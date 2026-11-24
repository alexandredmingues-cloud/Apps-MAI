package com.example.ui.seller

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Publish
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.MontyMarketData
import com.example.data.SaleTimelineEstimate
import com.example.data.SaleTimelineStatus
import com.example.data.SellerDocumentVault
import com.example.data.model.DocumentContributorRole
import com.example.data.model.DvfValuationCriteria
import com.example.data.model.PropertyItem
import com.example.data.model.PropertyKind
import com.example.data.model.SellerDocCatalogConstants
import com.example.data.model.SellerPropertyDossier
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.EmeraldDeep
import com.example.ui.theme.EmeraldMid
import com.example.ui.theme.GoldAntique
import com.example.ui.theme.GoldChampagne
import com.example.ui.theme.IvoryWarm
import com.example.ui.theme.ObsidianInk
import com.example.ui.theme.SlateMuted
import com.example.ui.theme.SuccessContainer
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningAmber
import com.example.ui.theme.WarningContainer

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SellerListingAndMandateSection(
    dossier: SellerPropertyDossier,
    selectedProperty: PropertyItem?,
    contributorRole: DocumentContributorRole,
    isGeneratingListingAi: Boolean,
    saleTimelineEstimate: SaleTimelineEstimate?,
    sellerViewModel: SellerViewModel,
    onUpdatePropertyMandateAndValuation: (
        propertyId: String,
        price: Long,
        listingStatus: String,
        mandateRef: String,
        mandateType: String,
        mandateDate: String,
        agencyFeePercent: Double,
        listingUrl: String,
        dvfEstimatedEuro: Long,
        dvfEstimatedLowEuro: Long,
        dvfEstimatedHighEuro: Long,
        dvfScore: Int,
        dvfCriteriaSummary: String,
        advisorValuationNotes: String
    ) -> Unit,
    onShowToast: (String) -> Unit
) {
    val context = LocalContext.current

    var createNewListingMode by remember(dossier.propertyId) { mutableStateOf(selectedProperty == null) }
    val sourceProp = if (createNewListingMode) null else selectedProperty
    val resetKey = "${dossier.propertyId}_${createNewListingMode}_${sourceProp?.id.orEmpty()}"

    var title by remember(resetKey) { mutableStateOf(sourceProp?.title.orEmpty()) }
    var propertyType by remember(resetKey) {
        mutableStateOf(sourceProp?.type?.ifBlank { dossier.propertyKind } ?: dossier.propertyKind)
    }
    var sector by remember(resetKey) {
        mutableStateOf(sourceProp?.sector?.ifBlank { MontyMarketData.SECTEURS_MONTPELLIER.first().name } ?: MontyMarketData.SECTEURS_MONTPELLIER.first().name)
    }
    var address by remember(resetKey) { mutableStateOf(sourceProp?.address.orEmpty()) }
    var hideExactAddress by remember(resetKey) { mutableStateOf(sourceProp?.hideExactAddress ?: (contributorRole == DocumentContributorRole.VENDEUR)) }
    var priceText by remember(resetKey) { mutableStateOf((sourceProp?.price ?: dossier.dvfValuation?.estimatedMidEuro ?: 320000L).toString()) }
    var surfaceText by remember(resetKey) { mutableStateOf((sourceProp?.surface ?: 72L).toString()) }
    var roomsText by remember(resetKey) { mutableStateOf((sourceProp?.rooms ?: 3L).toString()) }
    var bedroomsText by remember(resetKey) { mutableStateOf((sourceProp?.bedrooms ?: 2L).toString()) }
    var dpe by remember(resetKey) { mutableStateOf(sourceProp?.dpe?.ifBlank { "C" } ?: "C") }
    var conditionLabel by remember(resetKey) { mutableStateOf(sourceProp?.generalCondition?.ifBlank { "Bon état" } ?: "Bon état") }
    var exterior by remember(resetKey) { mutableStateOf(sourceProp?.exterior.orEmpty()) }
    var parking by remember(resetKey) { mutableStateOf(sourceProp?.parking.orEmpty()) }
    var transports by remember(resetKey) { mutableStateOf(sourceProp?.transports.orEmpty()) }
    var environment by remember(resetKey) { mutableStateOf(sourceProp?.environment.orEmpty()) }
    var highlightsText by remember(resetKey) { mutableStateOf(sourceProp?.strengths?.joinToString("\n").orEmpty()) }
    var description by remember(resetKey) { mutableStateOf(sourceProp?.description.orEmpty()) }
    var photoPaths by remember(resetKey) { mutableStateOf(sourceProp?.photoVaultPaths.orEmpty()) }
    var aiPhotoObservations by remember(resetKey) { mutableStateOf("") }
    var aiVigilances by remember(resetKey) { mutableStateOf("") }
    var ownerConfirmedAccuracy by remember(resetKey) { mutableStateOf(sourceProp?.ownerConfirmedAccuracy ?: false) }
    var legalPublicationConsent by remember(resetKey) { mutableStateOf(sourceProp?.legalPublicationConsent ?: false) }

    val draftPropertyId = remember(resetKey) {
        sourceProp?.id?.takeIf { it.isNotBlank() } ?: "draft_${dossier.propertyId.ifBlank { "new" }}"
    }

    // Recalcul déterministe du délai de vente dès que les critères clés changent
    LaunchedEffect(
        dossier.propertyId,
        dossier.dvfValuation,
        dossier.mandateType,
        sector,
        propertyType,
        priceText,
        surfaceText,
        dpe,
        conditionLabel,
        photoPaths.size
    ) {
        sellerViewModel.recomputeSaleTimeline(
            dossier = dossier,
            sectorName = sector,
            propertyKind = PropertyKind.fromLabel(propertyType),
            askingPriceEuro = priceText.toLongOrNull() ?: 0L,
            surfaceM2 = surfaceText.toIntOrNull() ?: 0,
            dpe = dpe,
            conditionLabel = conditionLabel,
            criteria = DvfValuationCriteria(
                exteriorType = if (exterior.isNotBlank()) exterior else "Aucun extérieur (-4%)",
                parkingType = if (parking.isNotBlank()) parking else "Aucun stationnement (-3%)"
            ),
            photoCount = photoPaths.size
        )
    }

    // Zero-permission Photo Picker (Play Policy compliant)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(6)
    ) { uris ->
        if (uris.isNotEmpty()) {
            sellerViewModel.attachListingPhotos(
                context = context,
                propertyId = draftPropertyId,
                uris = uris,
                existingPaths = photoPaths,
                onUpdated = { updated -> photoPaths = updated }
            )
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // A. PUBLICATION D'ANNONCE VENDEUR / AGENCE + PHOTOS + RÉDACTION IA + DÉLAI DE VENTE
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.5.dp, EmeraldDeep.copy(alpha = 0.25f), RoundedCornerShape(20.dp))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (contributorRole == DocumentContributorRole.AGENT) "DIFFUSION ANNONCE AGENCE" else "PUBLIER MON ANNONCE PARTICULIER",
                            style = MaterialTheme.typography.labelSmall,
                            color = GoldAntique
                        )
                        Text(
                            text = if (createNewListingMode) "Créer une nouvelle annonce" else "Annonce & Aide à la rédaction IA",
                            style = MaterialTheme.typography.titleMedium,
                            color = EmeraldDeep,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (selectedProperty != null) {
                        TextButton(
                            onClick = { createNewListingMode = !createNewListingMode },
                            modifier = Modifier.testTag("seller_toggle_new_listing_button")
                        ) {
                            Text(if (createNewListingMode) "Éditer bien actuel" else "+ Nouvelle annonce")
                        }
                    }
                }

                // Badge statut publication actuel
                if (sourceProp != null) {
                    Surface(
                        color = when (sourceProp.publicationStatus) {
                            "PUBLIEE" -> SuccessContainer
                            "A_VERIFIER" -> WarningContainer
                            else -> IvoryWarm
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Diffuseur : ${sourceProp.publisherType} • Statut : ${sourceProp.publicationStatus} (${sourceProp.listingStatus})",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = EmeraldDeep,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                // Délai approximatif de vente (SaleTimelineEngine)
                saleTimelineEstimate?.let { est ->
                    Surface(
                        color = EmeraldDeep,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().testTag("sale_timeline_card")
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.Schedule, contentDescription = null, tint = GoldChampagne, modifier = Modifier.size(18.dp))
                                Text("DÉLAI APPROXIMATIF DE VENTE", style = MaterialTheme.typography.labelSmall, color = GoldChampagne)
                            }
                            Text(
                                text = est.displayRange,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = IvoryWarm
                            )
                            Text(
                                text = "${est.marketLiquidityLabel} • Confiance : ${est.confidenceLabel}",
                                style = MaterialTheme.typography.bodySmall,
                                color = GoldChampagne.copy(alpha = 0.9f)
                            )
                            if (est.status == SaleTimelineStatus.READY && est.factors.isNotEmpty()) {
                                HorizontalDivider(color = IvoryWarm.copy(alpha = 0.16f))
                                est.factors.take(4).forEach { f ->
                                    val sign = if (f.impactDays > 0) "+" else ""
                                    Text(
                                        text = "• ${f.label} ($sign${f.impactDays} j.) — ${f.detail}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = IvoryWarm.copy(alpha = 0.85f)
                                    )
                                }
                            }
                            est.warnings.take(2).forEach { w ->
                                Text("⚠ $w", style = MaterialTheme.typography.labelSmall, color = Color(0xFFFDE68A))
                            }
                        }
                    }
                }

                // Photos (Sélecteur Android sans permission de stockage)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Photos de l'annonce (${photoPaths.size}/6)",
                        style = MaterialTheme.typography.labelLarge,
                        color = EmeraldDeep,
                        fontWeight = FontWeight.Bold
                    )
                    OutlinedButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        enabled = photoPaths.size < 6,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("seller_pick_photos_button")
                    ) {
                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Ajouter photos", style = MaterialTheme.typography.labelSmall)
                    }
                }

                if (photoPaths.isNotEmpty()) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        photoPaths.forEach { relPath ->
                            val bmp = remember(relPath) {
                                SellerDocumentVault.loadDownscaledPhotoBitmap(context, relPath, maxDimPx = 320)
                            }
                            Box(
                                modifier = Modifier
                                    .size(92.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
                            ) {
                                if (bmp != null) {
                                    Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = "Photo du bien",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                Surface(
                                    color = ObsidianInk.copy(alpha = 0.72f),
                                    shape = CircleShape,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(4.dp)
                                        .size(26.dp)
                                        .clickable { photoPaths = photoPaths - relPath }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Close, contentDescription = "Retirer photo", tint = IvoryWarm, modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                // Formulaire bien
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Titre de l'annonce *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("seller_listing_title_input")
                )

                Text("Type de bien", style = MaterialTheme.typography.labelMedium, color = EmeraldDeep)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MontyMarketData.OPTIONS_TYPES_BIEN.forEach { t ->
                        FilterChip(
                            selected = propertyType == t,
                            onClick = { propertyType = t },
                            label = { Text(t, style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = EmeraldDeep,
                                selectedLabelColor = GoldChampagne
                            )
                        )
                    }
                }

                Text("Quartier / Secteur", style = MaterialTheme.typography.labelMedium, color = EmeraldDeep)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MontyMarketData.SECTEURS_MONTPELLIER.forEach { sec ->
                        FilterChip(
                            selected = sector == sec.name,
                            onClick = { sector = sec.name },
                            label = { Text(sec.name.substringBefore(" ("), style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = EmeraldDeep,
                                selectedLabelColor = GoldChampagne
                            )
                        )
                    }
                }

                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Adresse ou localisation (pour carte & distance)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Masquer l'adresse exacte côté acquéreur (quartier seul)",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(checked = hideExactAddress, onCheckedChange = { hideExactAddress = it })
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = priceText,
                        onValueChange = { priceText = it.filter(Char::isDigit) },
                        label = { Text("Prix souhaité (€)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = surfaceText,
                        onValueChange = { surfaceText = it.filter(Char::isDigit) },
                        label = { Text("Surface (m²)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = roomsText,
                        onValueChange = { roomsText = it.filter(Char::isDigit) },
                        label = { Text("Pièces") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = bedroomsText,
                        onValueChange = { bedroomsText = it.filter(Char::isDigit) },
                        label = { Text("Chambres") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = dpe,
                        onValueChange = { dpe = it.uppercase().take(2) },
                        label = { Text("DPE") },
                        singleLine = true,
                        modifier = Modifier.weight(0.7f)
                    )
                }

                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Neuf / Rénové", "Excellent état", "Bon état", "À rafraîchir", "À rénover").forEach { cond ->
                        FilterChip(
                            selected = conditionLabel == cond,
                            onClick = { conditionLabel = cond },
                            label = { Text(cond, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = exterior,
                        onValueChange = { exterior = it },
                        label = { Text("Extérieur (terrasse, jardin...)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = parking,
                        onValueChange = { parking = it },
                        label = { Text("Stationnement / Garage") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = transports,
                    onValueChange = { transports = it },
                    label = { Text("Transports & accès (tram, gare, axes)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = environment,
                    onValueChange = { environment = it },
                    label = { Text("Environnement & commerces") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = highlightsText,
                    onValueChange = { highlightsText = it },
                    label = { Text("Points forts (1 par ligne)") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                // Bouton d'aide à la rédaction IA multimodale (critères + photos + localisation)
                Button(
                    onClick = {
                        sellerViewModel.generateListingWithPhotosAndLocation(
                            context = context,
                            rawTitle = title,
                            publisherType = if (contributorRole == DocumentContributorRole.AGENT) "AGENCE" else "PARTICULIER",
                            propertyType = propertyType,
                            sector = sector,
                            addressHint = address,
                            hideExactAddress = hideExactAddress,
                            priceEuro = priceText.toLongOrNull() ?: 0L,
                            surfaceM2 = surfaceText.toLongOrNull() ?: 0L,
                            rooms = roomsText.toLongOrNull() ?: 1L,
                            bedrooms = bedroomsText.toLongOrNull() ?: 0L,
                            dpe = dpe,
                            conditionLabel = conditionLabel,
                            exterior = exterior,
                            parking = parking,
                            transports = transports,
                            environment = environment,
                            highlightsText = highlightsText,
                            photoPaths = photoPaths,
                            onResult = { draft ->
                                if (draft.polishedTitle.isNotBlank()) title = draft.polishedTitle
                                if (draft.description.isNotBlank()) description = draft.description
                                if (draft.highlightsCsv.isNotBlank()) {
                                    highlightsText = draft.highlightsCsv.split(",").joinToString("\n") { it.trim() }
                                }
                                aiPhotoObservations = draft.photoObservations
                                aiVigilances = draft.vigilancesCsv
                            }
                        )
                    },
                    enabled = !isGeneratingListingAi,
                    colors = ButtonDefaults.buttonColors(containerColor = GoldAntique, contentColor = ObsidianInk),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("seller_ai_draft_listing_button")
                ) {
                    if (isGeneratingListingAi) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = ObsidianInk, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (isGeneratingListingAi) "Rédaction IA en cours..." else "Rédiger l'annonce avec l'IA (critères, photos & lieu)",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                if (aiPhotoObservations.isNotBlank() || aiVigilances.isNotBlank()) {
                    Surface(color = IvoryWarm, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (aiPhotoObservations.isNotBlank()) {
                                Text("Analyse factuelle des photos : $aiPhotoObservations", style = MaterialTheme.typography.bodySmall, color = EmeraldDeep)
                            }
                            if (aiVigilances.isNotBlank()) {
                                Text("Points à vérifier avant publication : $aiVigilances", style = MaterialTheme.typography.labelSmall, color = WarningAmber)
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Texte de l'annonce *") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth().testTag("seller_listing_description_input")
                )

                // Confirmations légales obligatoires avant soumission/publication
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { ownerConfirmedAccuracy = !ownerConfirmedAccuracy }
                ) {
                    Checkbox(checked = ownerConfirmedAccuracy, onCheckedChange = { ownerConfirmedAccuracy = it })
                    Text("Je certifie l'exactitude des informations saisies sur le bien.", style = MaterialTheme.typography.bodySmall)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { legalPublicationConsent = !legalPublicationConsent }
                ) {
                    Checkbox(checked = legalPublicationConsent, onCheckedChange = { legalPublicationConsent = it })
                    Text("J'accepte la diffusion sur l'espace Acquéreur conformément au RGPD.", style = MaterialTheme.typography.bodySmall)
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            sellerViewModel.submitSellerListing(
                                baseProperty = sourceProp,
                                dossier = dossier,
                                title = title,
                                propertyType = propertyType,
                                sector = sector,
                                address = address,
                                hideExactAddress = hideExactAddress,
                                priceEuro = priceText.toLongOrNull() ?: 0L,
                                surfaceM2 = surfaceText.toLongOrNull() ?: 0L,
                                rooms = roomsText.toLongOrNull() ?: 1L,
                                bedrooms = bedroomsText.toLongOrNull() ?: 0L,
                                dpe = dpe,
                                conditionLabel = conditionLabel,
                                exterior = exterior,
                                parking = parking,
                                transports = transports,
                                environment = environment,
                                highlightsText = highlightsText,
                                description = description,
                                photoPaths = photoPaths,
                                ownerConfirmedAccuracy = ownerConfirmedAccuracy,
                                legalPublicationConsent = legalPublicationConsent,
                                targetPublicationStatus = "BROUILLON",
                                saleEstimate = saleTimelineEstimate,
                                onSavedPropertyId = { createNewListingMode = false }
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(0.9f).testTag("seller_save_draft_listing_button")
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Brouillon", style = MaterialTheme.typography.labelSmall)
                    }

                    val effectivePublisherType = sourceProp?.publisherType
                        ?.takeIf { it == "AGENCE" || it == "PARTICULIER" }
                        ?: if (contributorRole == DocumentContributorRole.AGENT) "AGENCE" else "PARTICULIER"
                    val targetStatus = if (effectivePublisherType == "AGENCE" && contributorRole == DocumentContributorRole.AGENT) "PUBLIEE" else "A_VERIFIER"
                    Button(
                        onClick = {
                            sellerViewModel.submitSellerListing(
                                baseProperty = sourceProp,
                                dossier = dossier,
                                title = title,
                                propertyType = propertyType,
                                sector = sector,
                                address = address,
                                hideExactAddress = hideExactAddress,
                                priceEuro = priceText.toLongOrNull() ?: 0L,
                                surfaceM2 = surfaceText.toLongOrNull() ?: 0L,
                                rooms = roomsText.toLongOrNull() ?: 1L,
                                bedrooms = bedroomsText.toLongOrNull() ?: 0L,
                                dpe = dpe,
                                conditionLabel = conditionLabel,
                                exterior = exterior,
                                parking = parking,
                                transports = transports,
                                environment = environment,
                                highlightsText = highlightsText,
                                description = description,
                                photoPaths = photoPaths,
                                ownerConfirmedAccuracy = ownerConfirmedAccuracy,
                                legalPublicationConsent = legalPublicationConsent,
                                targetPublicationStatus = targetStatus,
                                saleEstimate = saleTimelineEstimate,
                                onSavedPropertyId = { createNewListingMode = false }
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldDeep, contentColor = GoldChampagne),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1.3f).testTag("seller_publish_listing_button")
                    ) {
                        Icon(Icons.Default.Publish, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (targetStatus == "PUBLIEE") "Publier l'annonce" else "Soumettre l'annonce",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // B. MANDAT, LIEN VERS L'ANNONCE & AVIS DE VALEUR CONSEILLER / AGENCE
        MandateAndAdvisorValuationCard(
            dossier = dossier,
            selectedProperty = selectedProperty,
            onSaveMandateAndValuation = { mRef, mType, mDate, feePct, url, estEuro, advNotes ->
                sellerViewModel.updateDossierMandateAndValuation(
                    dossier = dossier,
                    property = selectedProperty,
                    mandateRef = mRef,
                    mandateType = mType,
                    mandateDate = mDate,
                    agencyFeePercent = feePct,
                    listingUrl = url,
                    advisorEstimatedEuro = estEuro,
                    advisorValuationNotes = advNotes,
                    dvfSnapshot = dossier.dvfValuation
                )
                if (selectedProperty != null) {
                    val dvf = dossier.dvfValuation
                    onUpdatePropertyMandateAndValuation(
                        selectedProperty.id,
                        if (estEuro > 0L) estEuro else selectedProperty.price,
                        selectedProperty.listingStatus,
                        mRef,
                        mType,
                        mDate,
                        feePct,
                        url,
                        dvf?.estimatedMidEuro ?: selectedProperty.dvfEstimatedEuro,
                        dvf?.estimatedLowEuro ?: selectedProperty.dvfEstimatedLowEuro,
                        dvf?.estimatedHighEuro ?: selectedProperty.dvfEstimatedHighEuro,
                        dvf?.valuationScore ?: selectedProperty.dvfScore,
                        dvf?.criteriaSummary ?: selectedProperty.dvfCriteriaSummary,
                        advNotes
                    )
                }
            },
            onOpenUrl = { rawUrl ->
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(rawUrl)))
                }.onFailure { onShowToast("Lien d'annonce invalide.") }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MandateAndAdvisorValuationCard(
    dossier: SellerPropertyDossier,
    selectedProperty: PropertyItem?,
    onSaveMandateAndValuation: (
        mandateRef: String,
        mandateType: String,
        mandateDate: String,
        agencyFeePercent: Double,
        listingUrl: String,
        advisorEstimatedEuro: Long,
        advisorValuationNotes: String
    ) -> Unit,
    onOpenUrl: (String) -> Unit
) {
    val key = "${dossier.id}_${dossier.mandateRef}_${dossier.listingUrl}_${dossier.advisorEstimatedEuro}"
    var mandateRef by remember(key) { mutableStateOf(dossier.mandateRef) }
    var mandateType by remember(key) { mutableStateOf(dossier.mandateType.ifBlank { SellerDocCatalogConstants.MANDATE_TYPES.first() }) }
    var mandateDate by remember(key) { mutableStateOf(dossier.mandateDate) }
    var agencyFeeText by remember(key) {
        mutableStateOf(if (dossier.agencyFeePercent > 0.0) dossier.agencyFeePercent.toString() else "4.5")
    }
    var listingUrl by remember(key) { mutableStateOf(dossier.listingUrl.ifBlank { selectedProperty?.listingUrl.orEmpty() }) }
    var advisorEstText by remember(key) {
        val est = dossier.advisorEstimatedEuro.takeIf { it > 0L } ?: dossier.dvfValuation?.estimatedMidEuro ?: 0L
        mutableStateOf(if (est > 0L) est.toString() else "")
    }
    var advisorNotes by remember(key) { mutableStateOf(dossier.advisorValuationNotes) }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BorderSubtle, RoundedCornerShape(20.dp))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = GoldAntique)
                Column {
                    Text("SUIVI CONSEILLER & AGENCE", style = MaterialTheme.typography.labelSmall, color = GoldAntique)
                    Text("Mandat, Lien Annonce & Estimation", style = MaterialTheme.typography.titleMedium, color = EmeraldDeep, fontWeight = FontWeight.Bold)
                }
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SellerDocCatalogConstants.MANDATE_TYPES.forEach { mType ->
                    FilterChip(
                        selected = mandateType == mType,
                        onClick = { mandateType = mType },
                        label = { Text(mType, style = MaterialTheme.typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = EmeraldDeep,
                            selectedLabelColor = GoldChampagne
                        )
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = mandateRef,
                    onValueChange = { mandateRef = it },
                    label = { Text("Réf. Mandat") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = mandateDate,
                    onValueChange = { mandateDate = it },
                    label = { Text("Date (AAAA-MM-JJ)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = advisorEstText,
                    onValueChange = { advisorEstText = it.filter(Char::isDigit) },
                    label = { Text("Avis de valeur retenu (€)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1.2f)
                )
                OutlinedTextField(
                    value = agencyFeeText,
                    onValueChange = { agencyFeeText = it },
                    label = { Text("Honoraires (%)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(0.8f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = listingUrl,
                    onValueChange = { listingUrl = it },
                    leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
                    label = { Text("Lien vers l'annonce (portail / site agence)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                if (listingUrl.startsWith("http", ignoreCase = true)) {
                    IconButton(onClick = { onOpenUrl(listingUrl) }) {
                        Icon(Icons.Default.OpenInNew, contentDescription = "Ouvrir l'annonce", tint = EmeraldDeep)
                    }
                }
            }

            OutlinedTextField(
                value = advisorNotes,
                onValueChange = { advisorNotes = it },
                label = { Text("Argumentaire estimation & stratégie de commercialisation") },
                maxLines = 4,
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    onSaveMandateAndValuation(
                        mandateRef,
                        mandateType,
                        mandateDate,
                        agencyFeeText.replace(',', '.').toDoubleOrNull() ?: 0.0,
                        listingUrl,
                        advisorEstText.toLongOrNull() ?: 0L,
                        advisorNotes
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldDeep, contentColor = GoldChampagne),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().testTag("seller_save_mandate_valuation_button")
            ) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Enregistrer mandat, estimation & lien", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
