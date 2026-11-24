package com.example.ui.seller

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhoneCallback
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.PropertyKind
import com.example.data.model.ResidenceType
import com.example.data.model.SellerDocCatalogConstants
import com.example.data.model.SellerPropertyDossier
import com.example.data.model.SellerSaleDocument
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.EmeraldDeep
import com.example.ui.theme.GoldAntique
import com.example.ui.theme.GoldChampagne
import com.example.ui.theme.IvoryWarm
import com.example.ui.theme.ObsidianInk
import com.example.ui.theme.SlateMuted
import com.example.ui.theme.WarningAmber
import com.example.ui.theme.WarningContainer

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SellerConfigDialog(
    dossier: SellerPropertyDossier,
    onDismiss: () -> Unit,
    onSave: (
        kind: PropertyKind,
        residence: ResidenceType,
        sellerName: String,
        sellerEmail: String,
        sellerPhone: String,
        mandateRef: String,
        syndicName: String,
        annualCoproCharges: Long,
        alurWorkFundEuro: Long,
        packPriceEuro: Long,
        paymentLinkUrl: String
    ) -> Unit
) {
    var selectedKind by remember(dossier.id) { mutableStateOf(dossier.resolvedKind) }
    var selectedResidence by remember(dossier.id) { mutableStateOf(dossier.resolvedResidence) }
    var sellerName by remember(dossier.id) { mutableStateOf(dossier.sellerName) }
    var sellerPhone by remember(dossier.id) { mutableStateOf(dossier.sellerPhone) }
    var sellerEmail by remember(dossier.id) { mutableStateOf(dossier.sellerEmail) }
    var mandateRef by remember(dossier.id) { mutableStateOf(dossier.mandateRef) }
    var syndicName by remember(dossier.id) { mutableStateOf(dossier.syndicName) }
    var annualCharges by remember(dossier.id) { mutableStateOf(dossier.annualCoproCharges.toString()) }
    var workFund by remember(dossier.id) { mutableStateOf(dossier.alurWorkFundEuro.toString()) }
    var packPrice by remember(dossier.id) { mutableStateOf(dossier.packPriceEuro.toString()) }
    var paymentLink by remember(dossier.id) { mutableStateOf(dossier.paymentLinkUrl) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(vertical = 20.dp),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(EmeraldDeep)
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Bien, Résidence & Loi ALUR",
                            style = MaterialTheme.typography.titleLarge,
                            color = IvoryWarm
                        )
                        Text(
                            text = dossier.propertyTitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = GoldChampagne,
                            maxLines = 1
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Fermer", tint = IvoryWarm)
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Typologie du bien",
                        style = MaterialTheme.typography.labelLarge,
                        color = EmeraldDeep
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PropertyKind.entries.forEach { kind ->
                            FilterChip(
                                selected = selectedKind == kind,
                                onClick = {
                                    selectedKind = kind
                                    if (kind == PropertyKind.APPARTEMENT &&
                                        selectedResidence == ResidenceType.MAISON_INDIVIDUELLE
                                    ) {
                                        selectedResidence = ResidenceType.COPROPRIETE
                                    }
                                },
                                label = { Text(kind.label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = EmeraldDeep,
                                    selectedLabelColor = GoldChampagne
                                )
                            )
                        }
                    }

                    Text(
                        text = "Régime de résidence (documents Loi ALUR conservés automatiquement)",
                        style = MaterialTheme.typography.labelLarge,
                        color = EmeraldDeep
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ResidenceType.entries.forEach { res ->
                            FilterChip(
                                selected = selectedResidence == res,
                                onClick = { selectedResidence = res },
                                label = { Text(res.label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = EmeraldDeep,
                                    selectedLabelColor = GoldChampagne
                                )
                            )
                        }
                    }

                    Surface(
                        color = if (selectedResidence.alurRequired) WarningContainer else IvoryWarm,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
                    ) {
                        Text(
                            text = if (selectedResidence.alurRequired) {
                                "Loi ALUR activée : règlement, 3 PV d'AG, carnet d'entretien, pré-état daté et fonds de travaux sont intégrés et conservés."
                            } else {
                                "Maison individuelle hors copropriété : focus assainissement, urbanisme & bornage. Les pièces ALUR déjà déposées restent conservées."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (selectedResidence.alurRequired) WarningAmber else ObsidianInk,
                            modifier = Modifier.padding(10.dp)
                        )
                    }

                    HorizontalDivider(color = BorderSubtle)

                    Text("Propriétaire vendeur & Mandat", style = MaterialTheme.typography.labelLarge, color = EmeraldDeep)
                    OutlinedTextField(
                        value = sellerName,
                        onValueChange = { sellerName = it },
                        label = { Text("Nom du propriétaire vendeur") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = sellerPhone,
                            onValueChange = { sellerPhone = it },
                            label = { Text("Téléphone") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = mandateRef,
                            onValueChange = { mandateRef = it },
                            label = { Text("N° Mandat") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(
                        value = sellerEmail,
                        onValueChange = { sellerEmail = it },
                        label = { Text("E-mail vendeur") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (selectedResidence.alurRequired) {
                        HorizontalDivider(color = BorderSubtle)
                        Text("Données Résidence / Syndic (Loi ALUR)", style = MaterialTheme.typography.labelLarge, color = EmeraldDeep)
                        OutlinedTextField(
                            value = syndicName,
                            onValueChange = { syndicName = it },
                            label = { Text("Nom du syndic / ASL") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = annualCharges,
                                onValueChange = { annualCharges = it.filter(Char::isDigit) },
                                label = { Text("Charges annuelles (€)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = workFund,
                                onValueChange = { workFund = it.filter(Char::isDigit) },
                                label = { Text("Fonds travaux ALUR (€)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    HorizontalDivider(color = BorderSubtle)
                    Text("Accès payant acquéreur au dossier complet", style = MaterialTheme.typography.labelLarge, color = EmeraldDeep)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = packPrice,
                            onValueChange = { packPrice = it.filter(Char::isDigit) },
                            label = { Text("Tarif pack (€)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(0.42f)
                        )
                        OutlinedTextField(
                            value = paymentLink,
                            onValueChange = { paymentLink = it },
                            label = { Text("Lien paiement externe (optionnel)") },
                            placeholder = { Text("https://buy.stripe.com/...") },
                            singleLine = true,
                            modifier = Modifier.weight(0.58f)
                        )
                    }
                }

                HorizontalDivider(color = BorderSubtle)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Annuler")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onSave(
                                selectedKind,
                                selectedResidence,
                                sellerName,
                                sellerEmail,
                                sellerPhone,
                                mandateRef,
                                syndicName,
                                annualCharges.toLongOrNull() ?: 0L,
                                workFund.toLongOrNull() ?: 0L,
                                packPrice.toLongOrNull() ?: 29L,
                                paymentLink
                            )
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldDeep, contentColor = GoldChampagne),
                        modifier = Modifier.testTag("seller_config_save_button")
                    ) {
                        Text("Enregistrer")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SellerEditDocumentDialog(
    doc: SellerSaleDocument,
    onDismiss: () -> Unit,
    onSave: (newStatus: String, newSummary: String) -> Unit
) {
    var selectedStatus by remember(doc.id) { mutableStateOf(doc.status) }
    var summaryText by remember(doc.id) { mutableStateOf(doc.summaryText) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = doc.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = EmeraldDeep,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Catégorie : ${doc.category} • Renseignez le statut et le résumé visible gratuitement par les acquéreurs.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SlateMuted
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SellerDocCatalogConstants.STATUSES.forEach { st ->
                        FilterChip(
                            selected = selectedStatus == st,
                            onClick = { selectedStatus = st },
                            label = { Text(st, style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = EmeraldDeep,
                                selectedLabelColor = GoldChampagne
                            )
                        )
                    }
                }

                OutlinedTextField(
                    value = summaryText,
                    onValueChange = { summaryText = it },
                    label = { Text("Synthèse visible gratuitement par l'acquéreur") },
                    placeholder = { Text("Ex. Aucune procédure en cours, ravalement voté et payé...") },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Annuler") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onSave(selectedStatus, summaryText)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldDeep, contentColor = GoldChampagne)
                    ) {
                        Text("Enregistrer")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SellerAddCustomDocumentDialog(
    onDismiss: () -> Unit,
    onConfirm: (title: String, category: String, summaryText: String, fileUri: Uri?) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Loi ALUR") }
    var summary by remember { mutableStateOf("") }
    var pickedUri by remember { mutableStateOf<Uri?>(null) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) pickedUri = uri
    }

    val categories = listOf("Essentiel", "Diagnostic DDT", "Maison", "Appartement", "Loi ALUR")

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Ajouter une pièce au dossier",
                    style = MaterialTheme.typography.titleLarge,
                    color = EmeraldDeep
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Intitulé du document") },
                    placeholder = { Text("Ex. Facture pompe à chaleur / Attestation syndic") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    categories.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat, style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = EmeraldDeep,
                                selectedLabelColor = GoldChampagne
                            )
                        )
                    }
                }
                OutlinedTextField(
                    value = summary,
                    onValueChange = { summary = it },
                    label = { Text("Résumé acquéreur (optionnel)") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(
                    onClick = { filePicker.launch(arrayOf("*/*")) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (pickedUri != null) "Fichier sélectionné ✓" else "Joindre un fichier (PDF, image...)",
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Annuler") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onConfirm(title, category, summary, pickedUri)
                            onDismiss()
                        },
                        enabled = title.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldDeep, contentColor = GoldChampagne),
                        modifier = Modifier.testTag("seller_add_custom_doc_confirm")
                    ) {
                        Text("Ajouter")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SellerCallbackRequestDialog(
    dossier: SellerPropertyDossier,
    onDismiss: () -> Unit,
    onSubmit: (
        sellerName: String,
        sellerPhone: String,
        sellerEmail: String,
        preferredSlot: String,
        projectHorizon: String,
        notes: String
    ) -> Unit
) {
    var name by remember(dossier.id) { mutableStateOf(dossier.sellerName) }
    var phone by remember(dossier.id) { mutableStateOf(dossier.sellerPhone) }
    var email by remember(dossier.id) { mutableStateOf(dossier.sellerEmail) }
    var slot by remember { mutableStateOf(SellerDocCatalogConstants.CALLBACK_SLOTS.first()) }
    var horizon by remember { mutableStateOf(SellerDocCatalogConstants.PROJECT_HORIZONS.first()) }
    var notes by remember { mutableStateOf("") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(vertical = 20.dp),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(EmeraldDeep)
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.PhoneCallback, contentDescription = null, tint = GoldChampagne)
                        Column {
                            Text(
                                text = "Approfondir mon Avis de Valeur",
                                style = MaterialTheme.typography.titleLarge,
                                color = IvoryWarm
                            )
                            Text(
                                text = "Être recontacté par un conseiller pour une visite d'estimation",
                                style = MaterialTheme.typography.bodySmall,
                                color = GoldChampagne
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Fermer", tint = IvoryWarm)
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    dossier.dvfValuation?.let { snap ->
                        if (snap.estimatedMidEuro > 0L) {
                            Surface(
                                color = IvoryWarm,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, GoldAntique.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            ) {
                                Text(
                                    text = "Repère DVF joint à votre demande : ${snap.estimatedLowEuro} € – ${snap.estimatedHighEuro} € (Score DVF ${snap.valuationScore}/100)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = EmeraldDeep,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Nom & prénom") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("Téléphone de rappel *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("E-mail") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("Créneau de rappel préféré", style = MaterialTheme.typography.labelLarge, color = EmeraldDeep)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SellerDocCatalogConstants.CALLBACK_SLOTS.forEach { s ->
                            FilterChip(
                                selected = slot == s,
                                onClick = { slot = s },
                                label = { Text(s, style = MaterialTheme.typography.labelSmall) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = EmeraldDeep,
                                    selectedLabelColor = GoldChampagne
                                )
                            )
                        }
                    }

                    Text("Horizon de vente", style = MaterialTheme.typography.labelLarge, color = EmeraldDeep)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SellerDocCatalogConstants.PROJECT_HORIZONS.forEach { h ->
                            FilterChip(
                                selected = horizon == h,
                                onClick = { horizon = h },
                                label = { Text(h, style = MaterialTheme.typography.labelSmall) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = EmeraldDeep,
                                    selectedLabelColor = GoldChampagne
                                )
                            )
                        }
                    }

                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Précisions sur le bien (travaux, vue, étage, terrain...)") },
                        minLines = 3,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                HorizontalDivider(color = BorderSubtle)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Annuler") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onSubmit(name, phone, email, slot, horizon, notes)
                            onDismiss()
                        },
                        enabled = phone.isNotBlank() || email.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldDeep, contentColor = GoldChampagne),
                        modifier = Modifier.testTag("seller_callback_submit_button")
                    ) {
                        Text("Demander à être recontacté")
                    }
                }
            }
        }
    }
}
