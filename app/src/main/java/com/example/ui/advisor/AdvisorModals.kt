package com.example.ui.advisor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.MontyMarketData
import com.example.data.model.ClientDossier
import com.example.data.model.PropertyDraftEnhancement
import com.example.data.model.PropertyItem
import com.example.ui.theme.EmeraldDeep
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.GoldAntique
import com.example.ui.theme.GoldChampagne
import com.example.ui.theme.IvoryWarm
import com.example.ui.theme.ObsidianInk
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewClientDialog(
    onDismiss: () -> Unit,
    onConfirm: (
        name: String,
        phone: String,
        email: String,
        situation: String,
        budgetMax: Long,
        downPayment: Long,
        monthlyIncome: Long,
        sector: String,
        propertyType: String,
        minSurface: Long,
        minRooms: Long,
        essentialsCsv: String,
        timelineDelai: String,
        privateNotes: String
    ) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var situation by remember { mutableStateOf("") }
    var budgetMax by remember { mutableStateOf("450000") }
    var downPayment by remember { mutableStateOf("90000") }
    var monthlyIncome by remember { mutableStateOf("6500") }
    var selectedSector by remember { mutableStateOf(MontyMarketData.SECTEURS_MONTPELLIER.first().name) }
    var propertyType by remember { mutableStateOf("Appartement") }
    var minSurface by remember { mutableStateOf("85") }
    var minRooms by remember { mutableStateOf("3") }
    var essentialsCsv by remember { mutableStateOf("Terrasse / Extérieur, Parking / Garage, DPE A, B ou C") }
    var timelineDelai by remember { mutableStateOf("Sous 3 mois") }
    var privateNotes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nouveau dossier acquéreur", style = MaterialTheme.typography.titleLarge, color = EmeraldDeep) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nom du foyer / Acquéreur *") },
                    placeholder = { Text("Ex : Durand — Camille & Julien") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("new_client_name_input")
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("Téléphone") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = situation,
                    onValueChange = { situation = it },
                    label = { Text("Situation familiale / pro") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = budgetMax,
                        onValueChange = { budgetMax = it.filter(Char::isDigit) },
                        label = { Text("Budget max (€)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = downPayment,
                        onValueChange = { downPayment = it.filter(Char::isDigit) },
                        label = { Text("Apport (€)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = monthlyIncome,
                        onValueChange = { monthlyIncome = it.filter(Char::isDigit) },
                        label = { Text("Revenus foyer (€/m)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = minSurface,
                        onValueChange = { minSurface = it.filter(Char::isDigit) },
                        label = { Text("Surf. min (m²)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = minRooms,
                        onValueChange = { minRooms = it.filter(Char::isDigit) },
                        label = { Text("Chambres") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(0.8f)
                    )
                }
                Text("Secteur prioritaire", style = MaterialTheme.typography.labelLarge, color = EmeraldDeep)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    MontyMarketData.SECTEURS_MONTPELLIER.take(6).forEach { sec ->
                        FilterChip(
                            selected = selectedSector == sec.name,
                            onClick = { selectedSector = sec.name },
                            label = { Text(sec.name.substringBefore(" ("), style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
                Text("Type de bien", style = MaterialTheme.typography.labelLarge, color = EmeraldDeep)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MontyMarketData.OPTIONS_TYPES_BIEN.forEach { t ->
                        FilterChip(
                            selected = propertyType == t,
                            onClick = { propertyType = t },
                            label = { Text(t, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
                OutlinedTextField(
                    value = essentialsCsv,
                    onValueChange = { essentialsCsv = it },
                    label = { Text("Critères essentiels (séparés par virgule)") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = timelineDelai,
                    onValueChange = { timelineDelai = it },
                    label = { Text("Délai souhaité") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = privateNotes,
                    onValueChange = { privateNotes = it },
                    label = { Text("Notes internes conseiller (masquées client)") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onConfirm(
                            name,
                            phone,
                            email,
                            situation,
                            budgetMax.toLongOrNull() ?: 450000L,
                            downPayment.toLongOrNull() ?: 90000L,
                            monthlyIncome.toLongOrNull() ?: 6500L,
                            selectedSector,
                            propertyType,
                            minSurface.toLongOrNull() ?: 85L,
                            minRooms.toLongOrNull() ?: 3L,
                            essentialsCsv,
                            timelineDelai,
                            privateNotes
                        )
                    }
                },
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldDeep, contentColor = GoldChampagne),
                modifier = Modifier.testTag("confirm_new_client_button")
            ) {
                Text("Créer le dossier")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewPropertyDialog(
    onDismiss: () -> Unit,
    onEnhanceDraft: (suspend (
        rawTitle: String,
        sector: String,
        type: String,
        price: Long,
        surface: Long,
        bedrooms: Long,
        dpe: String,
        exterior: String,
        parking: String,
        rawStrengths: String,
        rawWeaknesses: String
    ) -> Result<PropertyDraftEnhancement>)? = null,
    onConfirm: (
        title: String,
        sector: String,
        address: String,
        type: String,
        price: Long,
        surface: Long,
        rooms: Long,
        bedrooms: Long,
        dpe: String,
        monthlyCharges: Long,
        propertyTax: Long,
        estimatedWorks: Long,
        exterior: String,
        parking: String,
        strengthsCsv: String,
        weaknessesCsv: String
    ) -> Unit
) {
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf("") }
    var sector by remember { mutableStateOf(MontyMarketData.SECTEURS_MONTPELLIER.first().name) }
    var address by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("Appartement") }
    var price by remember { mutableStateOf("415000") }
    var surface by remember { mutableStateOf("92") }
    var rooms by remember { mutableStateOf("4") }
    var bedrooms by remember { mutableStateOf("3") }
    var dpe by remember { mutableStateOf("B") }
    var monthlyCharges by remember { mutableStateOf("150") }
    var propertyTax by remember { mutableStateOf("1750") }
    var estimatedWorks by remember { mutableStateOf("0") }
    var exterior by remember { mutableStateOf("Terrasse 18 m² plein Sud") }
    var parking by remember { mutableStateOf("Box fermé en sous-sol") }
    var strengthsCsv by remember { mutableStateOf("Lumineux, Proche Tramway, DPE performant") }
    var weaknessesCsv by remember { mutableStateOf("À valider en AG") }
    var isEnhancing by remember { mutableStateOf(false) }
    var aiRelevanceSummary by remember { mutableStateOf<String?>(null) }
    var enhanceError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ajouter un bien qualifié", style = MaterialTheme.typography.titleLarge, color = EmeraldDeep) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Titre du bien *") },
                    placeholder = { Text("Ex : T4 Terrasse — Parc Montcalm") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("new_property_title_input")
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Adresse / Rue") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Secteur Montpellier", style = MaterialTheme.typography.labelLarge, color = EmeraldDeep)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    MontyMarketData.SECTEURS_MONTPELLIER.take(6).forEach { sec ->
                        FilterChip(
                            selected = sector == sec.name,
                            onClick = { sector = sec.name },
                            label = { Text(sec.name.substringBefore(" ("), style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = price,
                        onValueChange = { price = it.filter(Char::isDigit) },
                        label = { Text("Prix FAI (€)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = surface,
                        onValueChange = { surface = it.filter(Char::isDigit) },
                        label = { Text("Surface (m²)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = rooms,
                        onValueChange = { rooms = it.filter(Char::isDigit) },
                        label = { Text("Pièces") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = bedrooms,
                        onValueChange = { bedrooms = it.filter(Char::isDigit) },
                        label = { Text("Chambres") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = dpe,
                        onValueChange = { dpe = it.uppercase().take(1) },
                        label = { Text("DPE") },
                        singleLine = true,
                        modifier = Modifier.weight(0.7f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = monthlyCharges,
                        onValueChange = { monthlyCharges = it.filter(Char::isDigit) },
                        label = { Text("Charges/m (€)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = propertyTax,
                        onValueChange = { propertyTax = it.filter(Char::isDigit) },
                        label = { Text("Taxe fonc. (€)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = estimatedWorks,
                        onValueChange = { estimatedWorks = it.filter(Char::isDigit) },
                        label = { Text("Travaux (€)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Text("Type de bien", style = MaterialTheme.typography.labelLarge, color = EmeraldDeep)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MontyMarketData.OPTIONS_TYPES_BIEN.forEach { t ->
                        FilterChip(
                            selected = type == t,
                            onClick = { type = t },
                            label = { Text(t, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
                OutlinedTextField(
                    value = exterior,
                    onValueChange = { exterior = it },
                    label = { Text("Extérieur") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = parking,
                    onValueChange = { parking = it },
                    label = { Text("Stationnement") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = strengthsCsv,
                    onValueChange = { strengthsCsv = it },
                    label = { Text("Points forts (virgules)") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = weaknessesCsv,
                    onValueChange = { weaknessesCsv = it },
                    label = { Text("Points de vigilance (virgules)") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                if (onEnhanceDraft != null) {
                    OutlinedButton(
                        onClick = {
                            isEnhancing = true
                            enhanceError = null
                            scope.launch {
                                onEnhanceDraft(
                                    title,
                                    sector,
                                    type,
                                    price.toLongOrNull() ?: 415000L,
                                    surface.toLongOrNull() ?: 92L,
                                    bedrooms.toLongOrNull() ?: 3L,
                                    dpe,
                                    exterior,
                                    parking,
                                    strengthsCsv,
                                    weaknessesCsv
                                ).onSuccess { res ->
                                    if (res.polishedTitle.isNotBlank()) title = res.polishedTitle
                                    if (res.strengthsCsv.isNotBlank()) strengthsCsv = res.strengthsCsv
                                    if (res.weaknessesCsv.isNotBlank()) weaknessesCsv = res.weaknessesCsv
                                    aiRelevanceSummary = res.relevanceSummary.takeIf { it.isNotBlank() }
                                }.onFailure { e ->
                                    enhanceError = e.localizedMessage ?: "Optimisation IA indisponible."
                                }
                                isEnhancing = false
                            }
                        },
                        enabled = !isEnhancing,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("enhance_property_ai_button")
                    ) {
                        if (isEnhancing) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = EmeraldDeep)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Optimisation IA en cours...")
                        } else {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = GoldAntique, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Améliorer par IA selon pertinence", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    aiRelevanceSummary?.let { summary ->
                        Surface(color = IvoryWarm, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "Ciblage IA : $summary",
                                style = MaterialTheme.typography.bodySmall,
                                color = EmeraldDeep,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                    enhanceError?.let { err ->
                        Text(err, style = MaterialTheme.typography.bodySmall, color = ErrorRed)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        onConfirm(
                            title,
                            sector,
                            address,
                            type,
                            price.toLongOrNull() ?: 415000L,
                            surface.toLongOrNull() ?: 92L,
                            rooms.toLongOrNull() ?: 4L,
                            bedrooms.toLongOrNull() ?: 3L,
                            dpe.ifBlank { "C" },
                            monthlyCharges.toLongOrNull() ?: 150L,
                            propertyTax.toLongOrNull() ?: 1750L,
                            estimatedWorks.toLongOrNull() ?: 0L,
                            exterior,
                            parking,
                            strengthsCsv,
                            weaknessesCsv
                        )
                    }
                },
                enabled = title.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldDeep, contentColor = GoldChampagne),
                modifier = Modifier.testTag("confirm_new_property_button")
            ) {
                Text("Ajouter au catalogue")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        }
    )
}

@Composable
fun NewVisitDialog(
    clients: List<ClientDossier>,
    properties: List<PropertyItem>,
    initialClientId: String,
    onDismiss: () -> Unit,
    onConfirm: (
        clientId: String,
        propertyId: String,
        date: String,
        timeSlot: String,
        status: String,
        rating: Double?,
        impressions: String,
        positives: String,
        negatives: String,
        technicalNotes: String,
        nextAction: String
    ) -> Unit
) {
    var selectedClientId by remember { mutableStateOf(initialClientId.ifBlank { clients.firstOrNull()?.id.orEmpty() }) }
    var selectedPropertyId by remember { mutableStateOf(properties.firstOrNull()?.id.orEmpty()) }
    var date by remember { mutableStateOf("2026-11-24") }
    var timeSlot by remember { mutableStateOf("14:30") }
    var status by remember { mutableStateOf("Programmée") }
    var rating by remember { mutableStateOf("8.5") }
    var impressions by remember { mutableStateOf("") }
    var positives by remember { mutableStateOf("") }
    var negatives by remember { mutableStateOf("") }
    var technicalNotes by remember { mutableStateOf("") }
    var nextAction by remember { mutableStateOf("Transmettre compte rendu et PV d'AG.") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Planifier / Saisir une visite", style = MaterialTheme.typography.titleLarge, color = EmeraldDeep) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Client", style = MaterialTheme.typography.labelLarge, color = EmeraldDeep)
                clients.forEach { cli ->
                    Surface(
                        color = if (selectedClientId == cli.id) EmeraldDeep else IvoryWarm,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedClientId = cli.id }
                    ) {
                        Text(
                            text = cli.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (selectedClientId == cli.id) GoldChampagne else ObsidianInk,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
                Text("Bien visité", style = MaterialTheme.typography.labelLarge, color = EmeraldDeep)
                properties.forEach { prop ->
                    Surface(
                        color = if (selectedPropertyId == prop.id) EmeraldDeep else IvoryWarm,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedPropertyId = prop.id }
                    ) {
                        Text(
                            text = "${prop.title} (${MontyMarketData.formatEuro(prop.price)})",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (selectedPropertyId == prop.id) GoldChampagne else ObsidianInk,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = date,
                        onValueChange = { date = it },
                        label = { Text("Date (AAAA-MM-JJ)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = timeSlot,
                        onValueChange = { timeSlot = it },
                        label = { Text("Heure") },
                        singleLine = true,
                        modifier = Modifier.weight(0.7f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Programmée", "Réalisée").forEach { st ->
                        FilterChip(
                            selected = status == st,
                            onClick = { status = st },
                            label = { Text(st, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
                if (status == "Réalisée") {
                    OutlinedTextField(
                        value = rating,
                        onValueChange = { rating = it },
                        label = { Text("Note globale /10") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = impressions,
                        onValueChange = { impressions = it },
                        label = { Text("Impressions client") },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = positives,
                        onValueChange = { positives = it },
                        label = { Text("Points positifs") },
                        maxLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = negatives,
                        onValueChange = { negatives = it },
                        label = { Text("Réserves / Points négatifs") },
                        maxLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                OutlinedTextField(
                    value = technicalNotes,
                    onValueChange = { technicalNotes = it },
                    label = { Text("Notes techniques") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = nextAction,
                    onValueChange = { nextAction = it },
                    label = { Text("Prochaine action") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (selectedClientId.isNotBlank() && selectedPropertyId.isNotBlank()) {
                        onConfirm(
                            selectedClientId,
                            selectedPropertyId,
                            date,
                            timeSlot,
                            status,
                            rating.replace(',', '.').toDoubleOrNull(),
                            impressions,
                            positives,
                            negatives,
                            technicalNotes,
                            nextAction
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldDeep, contentColor = GoldChampagne),
                modifier = Modifier.testTag("confirm_new_visit_button")
            ) {
                Text("Enregistrer la visite")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        }
    )
}

@Composable
fun NewOfferDialog(
    clients: List<ClientDossier>,
    properties: List<PropertyItem>,
    initialClientId: String,
    onDismiss: () -> Unit,
    onConfirm: (
        client: ClientDossier,
        property: PropertyItem,
        offeredPrice: Long,
        sellerContext: String,
        suspensiveConditions: String,
        mainArgument: String
    ) -> Unit
) {
    var selectedClientId by remember { mutableStateOf(initialClientId.ifBlank { clients.firstOrNull()?.id.orEmpty() }) }
    var selectedPropertyId by remember { mutableStateOf(properties.firstOrNull()?.id.orEmpty()) }
    val selectedProperty = properties.firstOrNull { it.id == selectedPropertyId }
    var offeredPrice by remember(selectedPropertyId) {
        mutableStateOf(((selectedProperty?.price ?: 400000L) * 95 / 100).toString())
    }
    var sellerContext by remember { mutableStateOf("Vendeur à l'écoute d'une offre sérieuse avec financement sécurisé.") }
    var suspensiveConditions by remember { mutableStateOf("Obtention de prêt bancaire sous 45 jours.") }
    var mainArgument by remember { mutableStateOf("Alignement sur les références DVF récentes du secteur et dossier bancaire validé.") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Préparer une offre d'achat", style = MaterialTheme.typography.titleLarge, color = EmeraldDeep) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Acquéreur", style = MaterialTheme.typography.labelLarge, color = EmeraldDeep)
                clients.forEach { cli ->
                    Surface(
                        color = if (selectedClientId == cli.id) EmeraldDeep else IvoryWarm,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedClientId = cli.id }
                    ) {
                        Text(
                            text = "${cli.name} (Max ${MontyMarketData.formatEuro(cli.budgetMax)})",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (selectedClientId == cli.id) GoldChampagne else ObsidianInk,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
                Text("Bien visé", style = MaterialTheme.typography.labelLarge, color = EmeraldDeep)
                properties.forEach { prop ->
                    Surface(
                        color = if (selectedPropertyId == prop.id) EmeraldDeep else IvoryWarm,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedPropertyId = prop.id }
                    ) {
                        Text(
                            text = "${prop.title} — ${MontyMarketData.formatEuro(prop.price)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (selectedPropertyId == prop.id) GoldChampagne else ObsidianInk,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
                OutlinedTextField(
                    value = offeredPrice,
                    onValueChange = { offeredPrice = it.filter(Char::isDigit) },
                    label = { Text("Prix proposé FAI (€)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("new_offer_price_input")
                )
                OutlinedTextField(
                    value = sellerContext,
                    onValueChange = { sellerContext = it },
                    label = { Text("Contexte vendeur") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = suspensiveConditions,
                    onValueChange = { suspensiveConditions = it },
                    label = { Text("Conditions suspensives") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = mainArgument,
                    onValueChange = { mainArgument = it },
                    label = { Text("Argument clé de négociation") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cli = clients.firstOrNull { it.id == selectedClientId }
                    val prop = properties.firstOrNull { it.id == selectedPropertyId }
                    if (cli != null && prop != null) {
                        onConfirm(
                            cli,
                            prop,
                            offeredPrice.toLongOrNull() ?: prop.price,
                            sellerContext,
                            suspensiveConditions,
                            mainArgument
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldDeep, contentColor = GoldChampagne),
                modifier = Modifier.testTag("confirm_new_offer_button")
            ) {
                Text("Enregistrer l'offre")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        }
    )
}
