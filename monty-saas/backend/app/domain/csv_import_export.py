from __future__ import annotations

import csv
import io
from dataclasses import dataclass

from app.core.rbac import Permission, TenantContext
from app.core.rgpd import LEGAL_BASIS_CONSENT
from app.core.security import InputSanitizer
from app.domain.country_packs import CountryCode
from app.domain.crm_service import ContactRole, CrmContact, CrmService


@dataclass(frozen=True)
class CsvImportSummary:
    imported_count: int
    skipped_duplicates: int
    rejected_rows: int
    warnings: list[str]
    created_contact_ids: list[str]


def _normalize_header(raw: str) -> str:
    return raw.strip().lstrip("\ufeff").lower().replace(" ", "_")


def import_contacts_from_csv(
    ctx: TenantContext,
    crm: CrmService,
    *,
    csv_text: str,
    default_country: CountryCode = CountryCode.FR,
) -> CsvImportSummary:
    ctx.require_permission(Permission.CRM_WRITE)
    clean_text = csv_text.lstrip("\ufeff").strip()
    if not clean_text:
        return CsvImportSummary(0, 0, 0, ["Fichier CSV vide."], [])

    first_line = clean_text.splitlines()[0]
    delimiter = ";" if first_line.count(";") >= first_line.count(",") else ","
    reader = csv.DictReader(io.StringIO(clean_text), delimiter=delimiter)

    existing_emails = {
        c.email.lower()
        for c in crm.list_contacts(ctx)
        if c.email
    }

    imported = 0
    skipped = 0
    rejected = 0
    warnings: list[str] = []
    created_ids: list[str] = []

    for idx, row in enumerate(reader, start=2):
        norm = {_normalize_header(k): (v or "").strip() for k, v in row.items() if k is not None}
        name = norm.get("nom") or norm.get("full_name") or norm.get("name") or norm.get("nome") or ""
        email = (norm.get("email") or norm.get("mail") or "").lower()
        phone = norm.get("telephone") or norm.get("téléphone") or norm.get("phone") or norm.get("telemovel") or ""
        city = norm.get("ville") or norm.get("city") or norm.get("cidade") or ""
        raw_budget = norm.get("budget") or norm.get("budget_euro") or "0"
        raw_role = (norm.get("role") or norm.get("type") or "LEAD").upper()
        raw_country = (norm.get("pays") or norm.get("country") or norm.get("country_code") or default_country.value).upper()
        legal_basis = norm.get("base_legale") or norm.get("legal_basis") or LEGAL_BASIS_CONSENT

        if not name.strip():
            rejected += 1
            warnings.append(f"Ligne {idx} ignorée : nom manquant.")
            continue

        if email and email in existing_emails:
            skipped += 1
            continue

        try:
            role = ContactRole(raw_role)
        except ValueError:
            role = ContactRole.BUYER if "ACQ" in raw_role or "COMPR" in raw_role else (
                ContactRole.SELLER if "VEND" in raw_role else ContactRole.LEAD
            )

        try:
            country = CountryCode(raw_country)
        except ValueError:
            country = default_country

        digits = "".join(ch for ch in raw_budget if ch.isdigit())
        budget = int(digits) if digits else 0

        contact = crm.create_contact(
            ctx,
            full_name=name,
            email=email,
            phone=phone,
            role=role,
            country_code=country,
            city=city,
            budget_euro=budget,
            legal_basis=legal_basis,
            consent_granted=True,
        )
        if contact.email:
            existing_emails.add(contact.email)
        created_ids.append(contact.id)
        imported += 1

    return CsvImportSummary(
        imported_count=imported,
        skipped_duplicates=skipped,
        rejected_rows=rejected,
        warnings=warnings,
        created_contact_ids=created_ids,
    )


def export_contacts_to_csv(ctx: TenantContext, crm: CrmService) -> str:
    """Export CSV/Excel UTF-8 BOM avec neutralisation stricte des injections de formules (=, +, -, @)."""
    contacts: list[CrmContact] = crm.list_contacts(ctx)
    buf = io.StringIO()
    buf.write("\ufeff")
    writer = csv.writer(buf, delimiter=";", quoting=csv.QUOTE_MINIMAL)
    writer.writerow([
        "id",
        "country_code",
        "full_name",
        "email",
        "phone",
        "role",
        "city",
        "budget_euro",
        "legal_basis",
        "retention_until",
    ])
    for c in contacts:
        writer.writerow([
            InputSanitizer.sanitize_spreadsheet_cell(c.id),
            InputSanitizer.sanitize_spreadsheet_cell(c.country_code.value),
            InputSanitizer.sanitize_spreadsheet_cell(c.full_name),
            InputSanitizer.sanitize_spreadsheet_cell(c.email),
            InputSanitizer.sanitize_spreadsheet_cell(c.phone),
            InputSanitizer.sanitize_spreadsheet_cell(c.role.value),
            InputSanitizer.sanitize_spreadsheet_cell(c.city),
            c.budget_euro,
            InputSanitizer.sanitize_spreadsheet_cell(c.legal_basis),
            InputSanitizer.sanitize_spreadsheet_cell(c.retention_until),
        ])
    return buf.getvalue()
