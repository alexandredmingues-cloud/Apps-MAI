from __future__ import annotations

from dataclasses import dataclass, field
from datetime import date, datetime, timezone
import html
from typing import Any


PRE_ETAT_DOC_VERSION = "v2.0-L721-2"


@dataclass(frozen=True)
class CoproLotInput:
    lot_number: str
    nature: str  # Ex: Appartement, Cave, Parking
    tantiemes_generaux: int
    confidence: float = 1.0  # Confiance d'extraction PDF (0..1)
    validated: bool = True


@dataclass(frozen=True)
class ExtractedFieldCheck:
    field_key: str
    label: str
    value_preview: str
    confidence: float
    is_doubtful: bool
    validated: bool


@dataclass(frozen=True)
class L7212ChecklistStatus:
    code: str
    label: str
    present: bool
    mandatory: bool = True


@dataclass(frozen=True)
class PreEtatDateInput:
    dossier_ref: str
    seller_name: str
    property_address: str
    syndic_name: str
    total_tantiemes_copro: int
    lots: list[CoproLotInput]
    annual_budget_copro_euro: float
    charges_n_minus_1_lot_euro: float
    charges_n_minus_2_lot_euro: float
    non_budget_works_voted_euro: float = 0.0
    seller_unpaid_charges_euro: float = 0.0
    works_fund_alur_balance_euro: float = 0.0
    current_quarter_called_euro: float = 0.0
    sale_date: date = field(default_factory=lambda: date(2026, 6, 15))
    quarter_start_date: date = field(default_factory=lambda: date(2026, 4, 1))
    quarter_end_date: date = field(default_factory=lambda: date(2026, 6, 30))
    custom_syndic_fee_euro: float = 420.0
    # Pièces obligatoires Art. L. 721-2 CCH
    has_fiche_synthetique: bool = True
    has_reglement_copro_edd: bool = True
    has_pv_ag_3_years: bool = True
    has_carnet_entretien: bool = True
    has_dtg_or_pppt: bool = True
    has_financial_annex: bool = True
    has_pending_procedures_statement: bool = True
    has_alur_works_fund_statement: bool = True
    # Confiance extraction PDF des champs financiers clés
    confidence_map: dict[str, float] = field(default_factory=dict)
    validated_fields: set[str] = field(default_factory=set)


@dataclass(frozen=True)
class PreEtatDateResult:
    dossier_ref: str
    document_version: str
    generated_at_iso: str
    total_lot_tantiemes: int
    tantiemes_share_percent: float
    estimated_annual_lot_charges_euro: float
    estimated_quarterly_lot_charges_euro: float
    prorata_days_seller: int
    prorata_days_total_quarter: int
    seller_quarter_share_euro: float
    buyer_quarter_reimbursement_euro: float
    seller_net_balance_due_euro: float
    works_fund_alur_attached_euro: float
    estimated_syndic_savings_euro: float
    savings_source_note: str
    calculation_steps: list[str]
    extracted_fields: list[ExtractedFieldCheck]
    l721_2_checklist: list[L7212ChecklistStatus]
    missing_mandatory_items: list[str]
    unvalidated_doubtful_fields: list[str]
    export_allowed: bool
    blocking_reasons: list[str]


def compute_pre_etat_date(inp: PreEtatDateInput) -> PreEtatDateResult:
    if inp.total_tantiemes_copro <= 0:
        raise ValueError("Le total des tantièmes de la copropriété doit être > 0.")
    if not inp.lots:
        raise ValueError("Au moins un lot de copropriété est requis.")

    if any(lot.tantiemes_generaux <= 0 for lot in inp.lots):
        raise ValueError("Chaque lot doit avoir des tantièmes généraux strictement positifs.")

    total_lot_tantiemes = sum(lot.tantiemes_generaux for lot in inp.lots)
    if total_lot_tantiemes <= 0 or total_lot_tantiemes > inp.total_tantiemes_copro:
        raise ValueError("La somme des tantièmes des lots doit être > 0 et ≤ au total de la copropriété.")

    share_ratio = total_lot_tantiemes / float(inp.total_tantiemes_copro)
    share_pct = round(share_ratio * 100.0, 4)

    annual_lot_charges = round(inp.annual_budget_copro_euro * share_ratio, 2)
    quarterly_lot_charges = round(annual_lot_charges / 4.0, 2)

    q_total_days = max(1, (inp.quarter_end_date - inp.quarter_start_date).days + 1)
    clamped_sale = min(max(inp.sale_date, inp.quarter_start_date), inp.quarter_end_date)
    seller_days = max(0, min(q_total_days, (clamped_sale - inp.quarter_start_date).days))
    buyer_days = max(0, q_total_days - seller_days)

    quarter_basis = inp.current_quarter_called_euro if inp.current_quarter_called_euro > 0 else quarterly_lot_charges
    seller_q_share = round(quarter_basis * (seller_days / float(q_total_days)), 2)
    buyer_reimbursement = round(quarter_basis * (buyer_days / float(q_total_days)), 2)

    seller_net_due = round(
        max(0.0, inp.seller_unpaid_charges_euro) + max(0.0, inp.non_budget_works_voted_euro) - buyer_reimbursement,
        2,
    )

    savings_est = round(max(0.0, inp.custom_syndic_fee_euro), 2)
    savings_note = (
        f"Estimation indicative de frais syndic évités ({savings_est:.0f} € TTC) — "
        "référence moyenne constatée (étude tarifs syndics 2019 : 380 € à 600 € TTC), "
        "ajustable selon les honoraires réels de votre contrat de syndic."
    )

    steps = [
        f"1. Quote-part tantièmes : {total_lot_tantiemes} / {inp.total_tantiemes_copro} = {share_pct:.4f} %",
        f"2. Budget annuel imputable aux lots : {inp.annual_budget_copro_euro:.2f} € × {share_pct:.4f} % = {annual_lot_charges:.2f} €/an ({quarterly_lot_charges:.2f} €/trimestre)",
        f"3. Historique charges courantes lot : N-1 = {inp.charges_n_minus_1_lot_euro:.2f} € | N-2 = {inp.charges_n_minus_2_lot_euro:.2f} €",
        f"4. Prorata temporis trimestre (Art. 6-2 décret 1967) : {seller_days}/{q_total_days} j vendeur ({seller_q_share:.2f} €) • {buyer_days}/{q_total_days} j remboursement acquéreur ({buyer_reimbursement:.2f} €)",
        f"5. Fonds de travaux Loi ALUR (Art. 14-2) attaché aux lots (acquis au lot) : {inp.works_fund_alur_balance_euro:.2f} €",
        f"6. Solde net estimé vendeur (impayés {inp.seller_unpaid_charges_euro:.2f} € + travaux votés non appelés {inp.non_budget_works_voted_euro:.2f} € - prorata acquéreur {buyer_reimbursement:.2f} €) = {seller_net_due:.2f} €",
    ]

    # Contrôle confiance extraction PDF (< 0.80 => champ douteux à valider obligatoirement)
    field_specs = [
        ("total_tantiemes_copro", "Tantièmes totaux copropriété", str(inp.total_tantiemes_copro)),
        ("annual_budget_copro_euro", "Budget prévisionnel annuel", f"{inp.annual_budget_copro_euro:.2f} €"),
        ("charges_n_minus_1_lot_euro", "Charges courantes N-1", f"{inp.charges_n_minus_1_lot_euro:.2f} €"),
        ("seller_unpaid_charges_euro", "Sommes restant dues par le vendeur", f"{inp.seller_unpaid_charges_euro:.2f} €"),
        ("works_fund_alur_balance_euro", "Solde fonds de travaux ALUR", f"{inp.works_fund_alur_balance_euro:.2f} €"),
    ]
    extracted_fields: list[ExtractedFieldCheck] = []
    unvalidated_doubtful: list[str] = []

    for key, label, preview in field_specs:
        conf = float(inp.confidence_map.get(key, 0.95))
        is_doubtful = conf < 0.80
        is_validated = (not is_doubtful) or (key in inp.validated_fields)
        extracted_fields.append(
            ExtractedFieldCheck(
                field_key=key,
                label=label,
                value_preview=preview,
                confidence=round(conf, 2),
                is_doubtful=is_doubtful,
                validated=is_validated,
            )
        )
        if is_doubtful and not is_validated:
            unvalidated_doubtful.append(f"{label} (confiance {round(conf * 100)} %)")

    for lot in inp.lots:
        is_doubtful = lot.confidence < 0.80
        is_validated = (not is_doubtful) or lot.validated or (f"lot:{lot.lot_number}" in inp.validated_fields)
        extracted_fields.append(
            ExtractedFieldCheck(
                field_key=f"lot:{lot.lot_number}",
                label=f"Lot {lot.lot_number} ({lot.nature})",
                value_preview=f"{lot.tantiemes_generaux} tantièmes",
                confidence=round(lot.confidence, 2),
                is_doubtful=is_doubtful,
                validated=is_validated,
            )
        )
        if is_doubtful and not is_validated:
            unvalidated_doubtful.append(f"Lot {lot.lot_number} (confiance {round(lot.confidence * 100)} %)")

    # Checklist obligatoire Art. L. 721-2 Code de la Construction et de l'Habitation
    checklist = [
        L7212ChecklistStatus("FICHE_SYNTHETIQUE", "Fiche synthétique de la copropriété", inp.has_fiche_synthetique),
        L7212ChecklistStatus("RCP_EDD", "Règlement de copropriété & état descriptif de division", inp.has_reglement_copro_edd),
        L7212ChecklistStatus("PV_AG_3_ANS", "Procès-verbaux des 3 dernières assemblées générales", inp.has_pv_ag_3_years),
        L7212ChecklistStatus("CARNET_ENTRETIEN", "Carnet d'entretien de l'immeuble", inp.has_carnet_entretien),
        L7212ChecklistStatus("DTG_PPPT", "Diagnostic Technique Global (DTG) / PPPT le cas échéant", inp.has_dtg_or_pppt),
        L7212ChecklistStatus("ETAT_FINANCIER", "État des charges N-1/N-2, impayés & avances (L. 721-2 II)", inp.has_financial_annex),
        L7212ChecklistStatus("PROCEDURES", "Mention des procédures judiciaires en cours", inp.has_pending_procedures_statement),
        L7212ChecklistStatus("FONDS_ALUR", "Montant du fonds de travaux Art. 14-2 attaché au lot", inp.has_alur_works_fund_statement),
    ]
    missing_items = [item.label for item in checklist if item.mandatory and not item.present]

    blocking_reasons: list[str] = []
    if unvalidated_doubtful:
        blocking_reasons.append(
            "Validation humaine requise sur les champs douteux (< 80 % confiance) : " + ", ".join(unvalidated_doubtful)
        )
    if missing_items:
        blocking_reasons.append(
            "Mentions/pièces L. 721-2 CCH manquantes : " + ", ".join(missing_items)
        )

    return PreEtatDateResult(
        dossier_ref=inp.dossier_ref,
        document_version=PRE_ETAT_DOC_VERSION,
        generated_at_iso=datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M UTC"),
        total_lot_tantiemes=total_lot_tantiemes,
        tantiemes_share_percent=share_pct,
        estimated_annual_lot_charges_euro=annual_lot_charges,
        estimated_quarterly_lot_charges_euro=quarterly_lot_charges,
        prorata_days_seller=seller_days,
        prorata_days_total_quarter=q_total_days,
        seller_quarter_share_euro=seller_q_share,
        buyer_quarter_reimbursement_euro=buyer_reimbursement,
        seller_net_balance_due_euro=seller_net_due,
        works_fund_alur_attached_euro=round(inp.works_fund_alur_balance_euro, 2),
        estimated_syndic_savings_euro=savings_est,
        savings_source_note=savings_note,
        calculation_steps=steps,
        extracted_fields=extracted_fields,
        l721_2_checklist=checklist,
        missing_mandatory_items=missing_items,
        unvalidated_doubtful_fields=unvalidated_doubtful,
        export_allowed=len(blocking_reasons) == 0,
        blocking_reasons=blocking_reasons,
    )


def render_pre_etat_date_html(inp: PreEtatDateInput, res: PreEtatDateResult) -> str:
    if not res.export_allowed:
        raise ValueError("Export bloqué : " + " | ".join(res.blocking_reasons))

    lots_rows = "".join(
        f"<tr><td>{html.escape(lot.lot_number)}</td><td>{html.escape(lot.nature)}</td>"
        f"<td>{lot.tantiemes_generaux} / {inp.total_tantiemes_copro}</td></tr>"
        for lot in inp.lots
    )
    steps_li = "".join(f"<li>{html.escape(s)}</li>" for s in res.calculation_steps)
    checks_li = "".join(
        f"<li>✓ {html.escape(c.label)}</li>" for c in res.l721_2_checklist
    )

    return f"""<!DOCTYPE html>
<html lang="fr">
<head>
<meta charset="utf-8" />
<title>Pré-État Daté {html.escape(res.dossier_ref)}</title>
<style>
  @page {{ size: A4; margin: 18mm 14mm 20mm 14mm; }}
  body {{ font-family: Arial, sans-serif; color: #16221e; font-size: 12px; line-height: 1.45; }}
  h1 {{ color: #0b3b2e; font-size: 18px; margin-bottom: 4px; }}
  h2 {{ color: #0b3b2e; font-size: 13px; border-bottom: 1px solid #d5cfc2; padding-bottom: 3px; margin-top: 14px; }}
  table {{ width: 100%; border-collapse: collapse; margin-top: 6px; page-break-inside: avoid; }}
  th, td {{ border: 1px solid #d5cfc2; padding: 6px 8px; text-align: left; }}
  th {{ background: #f3efe6; }}
  .footer {{ margin-top: 24px; padding-top: 8px; border-top: 1px solid #d5cfc2; font-size: 10px; color: #52615b; display: flex; justify-content: space-between; }}
</style>
</head>
<body>
  <h1>Pré-État Daté Conforme Art. L. 721-2 CCH</h1>
  <p><strong>Dossier :</strong> {html.escape(res.dossier_ref)} • <strong>Vendeur :</strong> {html.escape(inp.seller_name)} • <strong>Bien :</strong> {html.escape(inp.property_address)} • <strong>Syndic :</strong> {html.escape(inp.syndic_name)}</p>

  <h2>1. Lots &amp; Quote-part de copropriété</h2>
  <table>
    <thead><tr><th>N° Lot</th><th>Nature</th><th>Tantièmes généraux</th></tr></thead>
    <tbody>{lots_rows}</tbody>
  </table>

  <h2>2. Synthèse Financière &amp; Détail des Calculs</h2>
  <ul>{steps_li}</ul>

  <h2>3. Checklist Réglementaire Loi ALUR (Art. L. 721-2 CCH)</h2>
  <ul>{checks_li}</ul>

  <p style="font-size:11px; color:#52615b;">{html.escape(res.savings_source_note)}</p>
  <div class="footer">
    <span>Monty Immobilier — Document préparatoire à l'avant-contrat</span>
    <span>Version {html.escape(res.document_version)} • Généré le {html.escape(res.generated_at_iso)}</span>
  </div>
</body>
</html>"""


def serialize_pre_etat_result(res: PreEtatDateResult) -> dict[str, Any]:
    return {
        "dossier_ref": res.dossier_ref,
        "document_version": res.document_version,
        "generated_at_iso": res.generated_at_iso,
        "total_lot_tantiemes": res.total_lot_tantiemes,
        "tantiemes_share_percent": res.tantiemes_share_percent,
        "estimated_annual_lot_charges_euro": res.estimated_annual_lot_charges_euro,
        "estimated_quarterly_lot_charges_euro": res.estimated_quarterly_lot_charges_euro,
        "prorata_days_seller": res.prorata_days_seller,
        "prorata_days_total_quarter": res.prorata_days_total_quarter,
        "seller_quarter_share_euro": res.seller_quarter_share_euro,
        "buyer_quarter_reimbursement_euro": res.buyer_quarter_reimbursement_euro,
        "seller_net_balance_due_euro": res.seller_net_balance_due_euro,
        "works_fund_alur_attached_euro": res.works_fund_alur_attached_euro,
        "estimated_syndic_savings_euro": res.estimated_syndic_savings_euro,
        "savings_source_note": res.savings_source_note,
        "calculation_steps": res.calculation_steps,
        "extracted_fields": [f.__dict__ for f in res.extracted_fields],
        "l721_2_checklist": [c.__dict__ for c in res.l721_2_checklist],
        "missing_mandatory_items": res.missing_mandatory_items,
        "unvalidated_doubtful_fields": res.unvalidated_doubtful_fields,
        "export_allowed": res.export_allowed,
        "blocking_reasons": res.blocking_reasons,
    }
