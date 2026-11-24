package com.example.ui.seller

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.HomeWork
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PhoneCallback
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Verified
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.DvfRingStats
import com.example.data.DvfValuationReport
import com.example.data.MontyMarketData
import com.example.data.SellerDocumentVault
import com.example.data.model.ClientDossier
import com.example.data.model.DocumentContributorRole
import com.example.data.model.DvfCriteriaOptions
import com.example.data.model.DvfValuationCriteria
import com.example.data.model.DvfValuationSnapshot
import com.example.data.model.PropertyItem
import com.example.data.model.PropertyKind
import com.example.data.model.ResidenceType
import com.example.data.model.SellerDocCatalogConstants
import com.example.data.model.SellerPropertyDossier
import com.example.data.model.SellerSaleDocument
import com.example.data.model.WorkspaceMode
import com.example.ui.DataUiState
import com.example.ui.components.MontyBrandHeader
import com.example.ui.components.WorkspaceSwitcherBar
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.EmeraldDeep
import com.example.ui.theme.EmeraldMid
import com.example.ui.theme.ErrorContainer
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.GoldAntique
import com.example.ui.theme.GoldChampagne
import com.example.ui.theme.IvoryWarm
import com.example.ui.theme.ObsidianInk
import com.example.ui.theme.SlateMuted
import com.example.ui.theme.SuccessContainer
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningAmber
import com.example.ui.theme.WarningContainer

@Composable
fun SellerWorkspaceScreen(
    properties: List<PropertyItem>,
    activeClient: ClientDossier?,
    dossiersState: DataUiState<List<SellerPropertyDossier>>,
    activeDossier: SellerPropertyDossier?,
    contributorRole: DocumentContributorRole,
    dvfUiState: DvfUiState,
    isSummarizingAi: Boolean,
    sellerViewModel: SellerViewModel,
    onUpdatePropertyMandateAndValuation: (String, Long, String, String, String, String, Double, String, Long, Long, Long, Int, String, String) -> Unit = { _, _, _, _, _, _, _, _, _, _, _, _, _, _ -> },
    onSwitchMode: (WorkspaceMode) -> Unit,
    onOpenPdfPreview: () -> Unit,
    onShowToast: (String) -> Unit,
    onSignOut: () -> Unit
) {
    BackHandler {
        onSwitchMode(WorkspaceMode.ADVISOR)
    }

    Scaffold(
        containerColor = IvoryWarm,
        topBar = {
            Column {
                WorkspaceSwitcherBar(
                    currentMode = WorkspaceMode.SELLER,
                    activeClient = activeClient,
                    onSwitchMode = onSwitchMode,
                    onOpenDossierPdf = onOpenPdfPreview,
                    onSignOut = onSignOut
                )
                Surface(color = Color.White, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MontyBrandHeader(light = false, subtitle = "Espace Vendeur • DVF & ALUR")
                        Surface(
                            color = EmeraldDeep,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.border(1.dp, GoldAntique, RoundedCornerShape(10.dp))
                        ) {
                            Text(
                                text = contributorRole.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = GoldChampagne,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                SellerWorkspaceContent(
                    properties = properties,
                    dossiersState = dossiersState,
                    activeDossier = activeDossier,
                    contributorRole = contributorRole,
                    dvfUiState = dvfUiState,
                    isSummarizingAi = isSummarizingAi,
                    sellerViewModel = sellerViewModel,
                    onUpdatePropertyMandateAndValuation = onUpdatePropertyMandateAndValuation,
                    onShowToast = onShowToast
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SellerWorkspaceContent(
    properties: List<PropertyItem>,
    dossiersState: DataUiState<List<SellerPropertyDossier>>,
    activeDossier: SellerPropertyDossier?,
    contributorRole: DocumentContributorRole,
    dvfUiState: DvfUiState,
    isSummarizingAi: Boolean,
    sellerViewModel: SellerViewModel,
    onUpdatePropertyMandateAndValuation: (String, Long, String, String, String, String, Double, String, Long, Long, Long, Int, String, String) -> Unit = { _, _, _, _, _, _, _, _, _, _, _, _, _, _ -> },
    onShowToast: (String) -> Unit
) {
    val context = LocalContext.current
    val isGeneratingListingAi by sellerViewModel.isGeneratingListingAi.collectAsStateWithLifecycle()
    val saleTimelineEstimate by sellerViewModel.saleTimelineEstimate.collectAsStateWithLifecycle()

    LaunchedEffect(properties.size, dossiersState) {
        if (activeDossier == null && properties.isNotEmpty() && dossiersState is DataUiState.Success) {
            sellerViewModel.ensureDossierForProperty(properties.first())
        }
    }

    var showConfigDialog by remember { mutableStateOf(false) }
    var editingDoc by remember { mutableStateOf<SellerSaleDocument?>(null) }
    var showAddCustomDocDialog by remember { mutableStateOf(false) }
    var showCallbackDialog by remember { mutableStateOf(false) }
    var pendingAttachSlot by remember { mutableStateOf<SellerSaleDocument?>(null) }
    var selectedCategory by remember { mutableStateOf("Tous") }
    var showComparablesList by remember { mutableStateOf(false) }

    val selectedProperty = remember(properties, activeDossier?.propertyId) {
        properties.firstOrNull { it.id == activeDossier?.propertyId } ?: properties.firstOrNull()
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        val slot = pendingAttachSlot
        val dossier = activeDossier
        pendingAttachSlot = null
        if (uri != null && slot != null && dossier != null) {
            val actorName = if (contributorRole == DocumentContributorRole.VENDEUR) {
                dossier.sellerName.ifBlank { "Propriétaire vendeur" }
            } else {
                "Agent Mon Agent Immo"
            }
            sellerViewModel.attachFileToDocumentSlot(
                context = context,
                dossier = dossier,
                property = selectedProperty,
                slot = slot,
                uri = uri,
                actorDisplayName = actorName
            )
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // 1. SÉLECTEUR DE BIEN & RÔLE CONTRIBUTEUR (AGENT / VENDEUR)
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = EmeraldDeep),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.HomeWork, contentDescription = null, tint = GoldChampagne)
                    Column {
                        Text(
                            text = "ESPACE VENDEUR COLLABORATIF",
                            style = MaterialTheme.typography.labelSmall,
                            color = GoldChampagne
                        )
                        Text(
                            text = "Documents essentiels, Loi ALUR & Avis de Valeur DVF",
                            style = MaterialTheme.typography.titleMedium,
                            color = IvoryWarm,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (properties.isEmpty()) {
                    Text(
                        text = "Ajoutez un bien dans l'onglet Biens pour ouvrir son espace vendeur.",
                        style = MaterialTheme.typography.bodySmall,
                        color = IvoryWarm.copy(alpha = 0.8f)
                    )
                } else {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        properties.forEach { prop ->
                            val isSelected = selectedProperty?.id == prop.id
                            Surface(
                                color = if (isSelected) GoldAntique else EmeraldMid,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .border(
                                        1.dp,
                                        if (isSelected) GoldChampagne else IvoryWarm.copy(alpha = 0.25f),
                                        RoundedCornerShape(12.dp)
                                    )
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { sellerViewModel.selectProperty(prop) }
                                    .testTag("seller_property_chip_${prop.id}")
                            ) {
                                Text(
                                    text = prop.title,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) ObsidianInk else IvoryWarm,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = IvoryWarm.copy(alpha = 0.16f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Vous déposez en tant que :",
                        style = MaterialTheme.typography.labelMedium,
                        color = IvoryWarm.copy(alpha = 0.85f)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        DocumentContributorRole.entries.forEach { role ->
                            val active = contributorRole == role
                            Surface(
                                color = if (active) GoldChampagne else EmeraldMid,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { sellerViewModel.setContributorRole(role) }
                                    .testTag("seller_role_${role.code.lowercase()}")
                            ) {
                                Text(
                                    text = if (role == DocumentContributorRole.AGENT) "Agent" else "Vendeur",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (active) ObsidianInk else IvoryWarm,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        if (activeDossier == null) {
            if (dossiersState is DataUiState.Loading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = EmeraldDeep)
                }
            } else if (selectedProperty != null) {
                Button(
                    onClick = { sellerViewModel.ensureDossierForProperty(selectedProperty) },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldDeep, contentColor = GoldChampagne),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Initialiser le dossier vendeur pour ${selectedProperty.title}")
                }
            }
            return
        }

        // 2. AVIS DE VALEUR DVF (50M / 100M / 300M) + SCORE /100 + RECONTACT
        DvfValuationSectionCard(
            dossier = activeDossier,
            property = selectedProperty,
            dvfUiState = dvfUiState,
            showComparablesList = showComparablesList,
            onToggleComparables = { showComparablesList = !showComparablesList },
            onRunDvf = { addr, surf, dpe, cond, criteria ->
                sellerViewModel.runDvfValuation(
                    dossier = activeDossier,
                    property = selectedProperty,
                    addressQuery = addr,
                    surfaceM2 = surf,
                    dpe = dpe,
                    conditionLabel = cond,
                    hasExteriorOrParking = !criteria.exteriorType.startsWith("Aucun") || !criteria.parkingType.startsWith("Aucun"),
                    criteria = criteria
                )
            },
            onRequestCallback = { showCallbackDialog = true },
            onUpdateCallbackStatus = { reqId, status ->
                sellerViewModel.updateCallbackStatus(activeDossier, selectedProperty, reqId, status)
            }
        )

        // 3. TYPOLOGIE BIEN & RÉGIME DE RÉSIDENCE (LOI ALUR)
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderSubtle, RoundedCornerShape(18.dp))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "TYPOLOGIE & RÉGIME DE RÉSIDENCE",
                            style = MaterialTheme.typography.labelSmall,
                            color = GoldAntique
                        )
                        Text(
                            text = "${activeDossier.propertyKind} • ${activeDossier.residenceType}",
                            style = MaterialTheme.typography.titleMedium,
                            color = EmeraldDeep,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    OutlinedButton(
                        onClick = { showConfigDialog = true },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("seller_open_config_button")
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Configurer", style = MaterialTheme.typography.labelSmall)
                    }
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ResidenceType.entries.forEach { res ->
                        FilterChip(
                            selected = activeDossier.resolvedResidence == res,
                            onClick = {
                                sellerViewModel.updatePropertyAndResidenceConfig(
                                    dossier = activeDossier,
                                    property = selectedProperty,
                                    newKind = activeDossier.resolvedKind,
                                    newResidence = res,
                                    sellerName = activeDossier.sellerName,
                                    sellerEmail = activeDossier.sellerEmail,
                                    sellerPhone = activeDossier.sellerPhone,
                                    mandateRef = activeDossier.mandateRef,
                                    syndicName = activeDossier.syndicName,
                                    annualCoproCharges = activeDossier.annualCoproCharges,
                                    alurWorkFundEuro = activeDossier.alurWorkFundEuro,
                                    packPriceEuro = activeDossier.packPriceEuro,
                                    paymentLinkUrl = activeDossier.paymentLinkUrl
                                )
                            },
                            label = { Text(res.label, style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = EmeraldDeep,
                                selectedLabelColor = GoldChampagne
                            )
                        )
                    }
                }

                Surface(
                    color = if (activeDossier.isAlurApplicable) WarningContainer else IvoryWarm,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (activeDossier.isAlurApplicable) {
                            "Régime Loi ALUR actif — les pièces copropriété/ASL sont conservées et suivies automatiquement (${activeDossier.syndicName.ifBlank { "syndic à renseigner" }})."
                        } else {
                            "Maison individuelle hors copropriété — les éventuelles pièces ALUR déjà déposées restent conservées en sécurité."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (activeDossier.isAlurApplicable) WarningAmber else SlateMuted,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        }

        // 4. COFFRE-FORT DOCUMENTS ESSENTIELS & LOI ALUR
        val applicableDocs = activeDossier.documents.filter { it.status != "Non applicable" }
        val providedDocs = applicableDocs.filter { it.isProvided }
        val alurDocs = applicableDocs.filter { it.category == "Loi ALUR" }
        val alurProvided = alurDocs.count { it.isProvided }
        val progress = if (applicableDocs.isEmpty()) 0f else providedDocs.size.toFloat() / applicableDocs.size.toFloat()

        val categories = remember(activeDossier.documents) {
            listOf("Tous") + activeDossier.documents.map { it.category }.distinct()
        }
        val displayedDocs = remember(activeDossier.documents, selectedCategory) {
            if (selectedCategory == "Tous") {
                activeDossier.documents
            } else {
                activeDossier.documents.filter { it.category == selectedCategory }
            }
        }

        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderSubtle, RoundedCornerShape(20.dp))
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
                            text = "PIÈCES ESSENTIELLES & LOI ALUR",
                            style = MaterialTheme.typography.labelSmall,
                            color = GoldAntique
                        )
                        Text(
                            text = "${providedDocs.size} / ${applicableDocs.size} documents prêts",
                            style = MaterialTheme.typography.titleLarge,
                            color = EmeraldDeep
                        )
                        if (alurDocs.isNotEmpty()) {
                            Text(
                                text = "Dont Loi ALUR : $alurProvided / ${alurDocs.size} pièces",
                                style = MaterialTheme.typography.bodySmall,
                                color = WarningAmber,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    Button(
                        onClick = { showAddCustomDocDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldDeep, contentColor = GoldChampagne),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("seller_add_custom_doc_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Ajouter", style = MaterialTheme.typography.labelSmall)
                    }
                }

                LinearProgressIndicator(
                    progress = { progress },
                    color = EmeraldDeep,
                    trackColor = IvoryWarm,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(8.dp))
                )

                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    categories.forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = { Text(cat, style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = EmeraldDeep,
                                selectedLabelColor = GoldChampagne
                            )
                        )
                    }
                }

                displayedDocs.forEach { doc ->
                    SellerDocumentSlotRow(
                        doc = doc,
                        onAttachFile = {
                            pendingAttachSlot = doc
                            openDocumentLauncher.launch(arrayOf("*/*"))
                        },
                        onOpenLocalFile = {
                            SellerDocumentVault.openDocumentWithViewer(context, doc)
                                .onFailure { onShowToast(it.localizedMessage ?: "Ouverture impossible.") }
                        },
                        onEditSlot = { editingDoc = doc }
                    )
                }
            }
        }

        // 5. RÉSUMÉ ACQUÉREUR & COMMANDES PAYANTES
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, GoldAntique.copy(alpha = 0.55f), RoundedCornerShape(20.dp))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "VITRINE ACQUÉREUR (RÉSUMÉ GRATUIT & PACK ${activeDossier.packPriceEuro} €)",
                            style = MaterialTheme.typography.labelSmall,
                            color = GoldAntique
                        )
                        Text(
                            text = "Synthèse & Commandes Documents",
                            style = MaterialTheme.typography.titleMedium,
                            color = EmeraldDeep,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Button(
                        onClick = { sellerViewModel.generateExecutiveSummary(activeDossier, selectedProperty) },
                        enabled = !isSummarizingAi,
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldDeep, contentColor = GoldChampagne),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("seller_generate_summary_button")
                    ) {
                        if (isSummarizingAi) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                color = GoldChampagne,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(15.dp))
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Résumer IA", style = MaterialTheme.typography.labelSmall)
                    }
                }

                Surface(
                    color = IvoryWarm,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
                ) {
                    Text(
                        text = activeDossier.executiveSummary.ifBlank {
                            "Aucun résumé publié. Cliquez sur « Résumer IA » pour générer une synthèse claire pour les acquéreurs."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = ObsidianInk,
                        modifier = Modifier.padding(12.dp)
                    )
                }

                if (activeDossier.buyerOrders.isNotEmpty()) {
                    HorizontalDivider(color = BorderSubtle)
                    Text(
                        text = "Demandes & achats acquéreurs (${activeDossier.buyerOrders.size})",
                        style = MaterialTheme.typography.labelLarge,
                        color = EmeraldDeep
                    )
                    activeDossier.buyerOrders.forEach { order ->
                        Surface(
                            color = if (order.isUnlocked) SuccessContainer else WarningContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${order.buyerName} (${order.amountEuro} €)",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = ObsidianInk
                                    )
                                    Text(
                                        text = "${order.status} • ${order.paymentMethodLabel} • ${order.createdAtLabel}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = SlateMuted
                                    )
                                }
                                if (!order.isUnlocked) {
                                    Button(
                                        onClick = {
                                            sellerViewModel.confirmBuyerOrderDelivery(
                                                activeDossier,
                                                selectedProperty,
                                                order.orderId
                                            )
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldDeep, contentColor = GoldChampagne),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("Valider & Livrer", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 6. PUBLICATION D'ANNONCE, PHOTOS, RÉDACTION IA, DÉLAI DE VENTE & MANDAT CONSEILLER
        SellerListingAndMandateSection(
            dossier = activeDossier,
            selectedProperty = selectedProperty,
            contributorRole = contributorRole,
            isGeneratingListingAi = isGeneratingListingAi,
            saleTimelineEstimate = saleTimelineEstimate,
            sellerViewModel = sellerViewModel,
            onUpdatePropertyMandateAndValuation = onUpdatePropertyMandateAndValuation,
            onShowToast = onShowToast
        )
    }

    activeDossier?.let { currentDossier ->
        if (showConfigDialog) {
            SellerConfigDialog(
                dossier = currentDossier,
                onDismiss = { showConfigDialog = false },
                onSave = { kind, res, sName, sMail, sPhone, mandate, syndic, charges, workFund, packPrice, payLink ->
                    sellerViewModel.updatePropertyAndResidenceConfig(
                        dossier = currentDossier,
                        property = selectedProperty,
                        newKind = kind,
                        newResidence = res,
                        sellerName = sName,
                        sellerEmail = sMail,
                        sellerPhone = sPhone,
                        mandateRef = mandate,
                        syndicName = syndic,
                        annualCoproCharges = charges,
                        alurWorkFundEuro = workFund,
                        packPriceEuro = packPrice,
                        paymentLinkUrl = payLink
                    )
                }
            )
        }

        editingDoc?.let { doc ->
            SellerEditDocumentDialog(
                doc = doc,
                onDismiss = { editingDoc = null },
                onSave = { status, summary ->
                    sellerViewModel.updateDocumentDetails(
                        dossier = currentDossier,
                        property = selectedProperty,
                        slot = doc,
                        newStatus = status,
                        newSummaryText = summary
                    )
                }
            )
        }

        if (showAddCustomDocDialog) {
            SellerAddCustomDocumentDialog(
                onDismiss = { showAddCustomDocDialog = false },
                onConfirm = { title, cat, summary, uri ->
                    val actorName = if (contributorRole == DocumentContributorRole.VENDEUR) {
                        currentDossier.sellerName.ifBlank { "Propriétaire vendeur" }
                    } else {
                        "Agent Mon Agent Immo"
                    }
                    sellerViewModel.addCustomDocument(
                        context = context,
                        dossier = currentDossier,
                        property = selectedProperty,
                        title = title,
                        category = cat,
                        summaryText = summary,
                        uri = uri,
                        actorDisplayName = actorName
                    )
                }
            )
        }

        if (showCallbackDialog) {
            SellerCallbackRequestDialog(
                dossier = currentDossier,
                onDismiss = { showCallbackDialog = false },
                onSubmit = { sName, sPhone, sEmail, slot, horizon, notes ->
                    sellerViewModel.submitCallbackRequest(
                        dossier = currentDossier,
                        property = selectedProperty,
                        sellerName = sName,
                        sellerPhone = sPhone,
                        sellerEmail = sEmail,
                        preferredSlot = slot,
                        projectHorizon = horizon,
                        notes = notes
                    )
                }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DvfValuationSectionCard(
    dossier: SellerPropertyDossier,
    property: PropertyItem?,
    dvfUiState: DvfUiState,
    showComparablesList: Boolean,
    onToggleComparables: () -> Unit,
    onRunDvf: (address: String, surfaceM2: Int, dpe: String, conditionLabel: String, criteria: DvfValuationCriteria) -> Unit,
    onRequestCallback: () -> Unit,
    onUpdateCallbackStatus: (requestId: String, status: String) -> Unit
) {
    val context = LocalContext.current
    val initialAddr = remember(dossier.propertyId, property?.address, property?.sector) {
        val raw = property?.address?.ifBlank { property.sector } ?: dossier.propertyTitle
        if (raw.contains("Montpellier", ignoreCase = true) || raw.contains("34")) raw else "$raw, Montpellier"
    }
    val isHouse = dossier.resolvedKind == PropertyKind.MAISON
    val floorOptions = if (isHouse) DvfCriteriaOptions.ELEVATOR_FLOOR_HOUSE else DvfCriteriaOptions.ELEVATOR_FLOOR_APPT

    var addressQuery by remember(dossier.propertyId) { mutableStateOf(initialAddr) }
    var surfaceText by remember(dossier.propertyId) { mutableStateOf((property?.surface ?: 75L).toString()) }
    var dpe by remember(dossier.propertyId) { mutableStateOf(property?.dpe ?: "C") }
    var condition by remember(dossier.propertyId) { mutableStateOf("Bon état") }

    var floorChoice by remember(dossier.propertyId, isHouse) {
        mutableStateOf(
            if (isHouse) {
                DvfCriteriaOptions.ELEVATOR_FLOOR_HOUSE.last().first
            } else {
                val title = property?.title.orEmpty()
                if (title.contains("Dernier", ignoreCase = true)) {
                    DvfCriteriaOptions.ELEVATOR_FLOOR_APPT.last().first
                } else {
                    DvfCriteriaOptions.ELEVATOR_FLOOR_APPT[3].first
                }
            }
        )
    }
    var viewChoice by remember(dossier.propertyId) {
        mutableStateOf(
            if (property?.description?.contains("vue", ignoreCase = true) == true) {
                DvfCriteriaOptions.VIEW_EXPOSURE.first().first
            } else {
                DvfCriteriaOptions.VIEW_EXPOSURE[1].first
            }
        )
    }
    var exteriorChoice by remember(dossier.propertyId) {
        val extRaw = property?.exterior.orEmpty()
        mutableStateOf(
            when {
                extRaw.contains("Jardin", ignoreCase = true) || extRaw.contains("Piscine", ignoreCase = true) ->
                    DvfCriteriaOptions.EXTERIOR_TYPES[3].first
                extRaw.contains("Terrasse", ignoreCase = true) ->
                    DvfCriteriaOptions.EXTERIOR_TYPES[2].first
                extRaw.contains("Balcon", ignoreCase = true) || extRaw.contains("Loggia", ignoreCase = true) ->
                    DvfCriteriaOptions.EXTERIOR_TYPES[1].first
                else -> DvfCriteriaOptions.EXTERIOR_TYPES[1].first
            }
        )
    }
    var parkingChoice by remember(dossier.propertyId) {
        val parkRaw = property?.parking.orEmpty()
        mutableStateOf(
            when {
                parkRaw.contains("Double", ignoreCase = true) -> DvfCriteriaOptions.PARKING_TYPES[3].first
                parkRaw.contains("Garage", ignoreCase = true) || parkRaw.contains("Box", ignoreCase = true) ->
                    DvfCriteriaOptions.PARKING_TYPES[2].first
                parkRaw.contains("Parking", ignoreCase = true) || parkRaw.contains("Place", ignoreCase = true) ->
                    DvfCriteriaOptions.PARKING_TYPES[1].first
                else -> DvfCriteriaOptions.PARKING_TYPES[1].first
            }
        )
    }
    var noiseChoice by remember(dossier.propertyId) { mutableStateOf(DvfCriteriaOptions.NOISE_ENVIRONMENT[0].first) }
    var hasCellar by remember(dossier.propertyId) { mutableStateOf(false) }
    var hasAcOrHeatPump by remember(dossier.propertyId) { mutableStateOf(false) }
    var hasGlazingOrFiber by remember(dossier.propertyId) { mutableStateOf(true) }
    var hasNoVisAVis by remember(dossier.propertyId) { mutableStateOf(false) }
    var hasMajorWorks by remember(dossier.propertyId) { mutableStateOf(false) }

    val conditions = listOf("Rénové / Neuf", "Bon état", "À rafraîchir", "À rénover")
    val dpeOptions = listOf("A", "B", "C", "D", "E", "F", "G")

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.5.dp, GoldAntique.copy(alpha = 0.65f), RoundedCornerShape(20.dp))
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Analytics, contentDescription = null, tint = GoldAntique)
                    Column {
                        Text(
                            text = "AVIS DE VALEUR DVF (50m • 100m • 300m)",
                            style = MaterialTheme.typography.labelSmall,
                            color = GoldAntique
                        )
                        Text(
                            text = "Estimation & Critères Justificatifs",
                            style = MaterialTheme.typography.titleMedium,
                            color = EmeraldDeep,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                OutlinedButton(
                    onClick = onRequestCallback,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("seller_request_callback_button")
                ) {
                    Icon(Icons.Default.PhoneCallback, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Être rappelé", style = MaterialTheme.typography.labelSmall)
                }
            }

            OutlinedTextField(
                value = addressQuery,
                onValueChange = { addressQuery = it },
                label = { Text("Adresse exacte du bien (géocodage BAN + DVF)") },
                placeholder = { Text("Ex. 12 Avenue Raymond Dugrand, Montpellier") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("dvf_address_input")
            )

            OutlinedTextField(
                value = surfaceText,
                onValueChange = { surfaceText = it.filter(Char::isDigit) },
                label = { Text("Surface habitable Carrez / utile (m²)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Text("DPE & État général", style = MaterialTheme.typography.labelLarge, color = EmeraldDeep)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                dpeOptions.forEach { d ->
                    FilterChip(
                        selected = dpe == d,
                        onClick = { dpe = d },
                        label = { Text("DPE $d", style = MaterialTheme.typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = EmeraldDeep,
                            selectedLabelColor = GoldChampagne
                        )
                    )
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                conditions.forEach { c ->
                    FilterChip(
                        selected = condition == c,
                        onClick = { condition = c },
                        label = { Text(c, style = MaterialTheme.typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = EmeraldDeep,
                            selectedLabelColor = GoldChampagne
                        )
                    )
                }
            }

            Surface(
                color = IvoryWarm,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderSubtle, RoundedCornerShape(14.dp))
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "CRITÈRES DÉTERMINANTS POUR JUSTIFIER LE PRIX",
                        style = MaterialTheme.typography.labelSmall,
                        color = GoldAntique,
                        fontWeight = FontWeight.Bold
                    )

                    DvfCriterionChipGroup(
                        title = if (isHouse) "Configuration du bâti" else "Étage & Ascenseur",
                        options = floorOptions.map { it.first },
                        selected = floorChoice,
                        onSelect = { floorChoice = it }
                    )
                    DvfCriterionChipGroup(
                        title = "Vue, exposition & luminosité",
                        options = DvfCriteriaOptions.VIEW_EXPOSURE.map { it.first },
                        selected = viewChoice,
                        onSelect = { viewChoice = it }
                    )
                    DvfCriterionChipGroup(
                        title = "Extérieur (balcon, terrasse, jardin)",
                        options = DvfCriteriaOptions.EXTERIOR_TYPES.map { it.first },
                        selected = exteriorChoice,
                        onSelect = { exteriorChoice = it }
                    )
                    DvfCriterionChipGroup(
                        title = "Stationnement & Garage",
                        options = DvfCriteriaOptions.PARKING_TYPES.map { it.first },
                        selected = parkingChoice,
                        onSelect = { parkingChoice = it }
                    )
                    DvfCriterionChipGroup(
                        title = "Environnement & Calme",
                        options = DvfCriteriaOptions.NOISE_ENVIRONMENT.map { it.first },
                        selected = noiseChoice,
                        onSelect = { noiseChoice = it }
                    )

                    Text(
                        text = "Équipements & points spécifiques",
                        style = MaterialTheme.typography.labelMedium,
                        color = EmeraldDeep,
                        fontWeight = FontWeight.SemiBold
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = hasCellar,
                            onClick = { hasCellar = !hasCellar },
                            label = { Text("Cave / Cellier (+1.5%)", style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = EmeraldDeep,
                                selectedLabelColor = GoldChampagne
                            )
                        )
                        FilterChip(
                            selected = hasAcOrHeatPump,
                            onClick = { hasAcOrHeatPump = !hasAcOrHeatPump },
                            label = { Text("Clim / PAC (+2%)", style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = EmeraldDeep,
                                selectedLabelColor = GoldChampagne
                            )
                        )
                        FilterChip(
                            selected = hasGlazingOrFiber,
                            onClick = { hasGlazingOrFiber = !hasGlazingOrFiber },
                            label = { Text("Double vitrage / Fibre (+1.5%)", style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = EmeraldDeep,
                                selectedLabelColor = GoldChampagne
                            )
                        )
                        FilterChip(
                            selected = hasNoVisAVis,
                            onClick = { hasNoVisAVis = !hasNoVisAVis },
                            label = { Text("Sans vis-à-vis (+2%)", style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = EmeraldDeep,
                                selectedLabelColor = GoldChampagne
                            )
                        )
                        FilterChip(
                            selected = hasMajorWorks,
                            onClick = { hasMajorWorks = !hasMajorWorks },
                            label = { Text("Travaux votés/à prévoir (-4%)", style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ErrorRed,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }

            Button(
                onClick = {
                    val criteria = DvfValuationCriteria(
                        floorAndElevator = floorChoice,
                        viewAndExposure = viewChoice,
                        exteriorType = exteriorChoice,
                        parkingType = parkingChoice,
                        environmentCalm = noiseChoice,
                        hasCellarOrStorage = hasCellar,
                        hasAcOrHeatPump = hasAcOrHeatPump,
                        hasRecentGlazingOrFiber = hasGlazingOrFiber,
                        hasNoVisAVis = hasNoVisAVis,
                        hasMajorWorksPending = hasMajorWorks
                    )
                    onRunDvf(
                        addressQuery,
                        (surfaceText.toIntOrNull() ?: 75).coerceIn(10, 1500),
                        dpe,
                        condition,
                        criteria
                    )
                },
                enabled = dvfUiState !is DvfUiState.Loading,
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldDeep, contentColor = GoldChampagne),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("run_dvf_valuation_button")
            ) {
                if (dvfUiState is DvfUiState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = GoldChampagne,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Interrogation Etalab / DVF Open Data...")
                } else {
                    Text("Calculer Avis de Valeur DVF (50m / 100m / 300m)", fontWeight = FontWeight.Bold)
                }
            }

            if (dvfUiState is DvfUiState.Error) {
                Surface(
                    color = ErrorContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = dvfUiState.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = ErrorRed,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            val liveReport = (dvfUiState as? DvfUiState.Ready)?.report
            val snapshot: DvfValuationSnapshot? = liveReport?.snapshot ?: dossier.dvfValuation

            if (snapshot != null) {
                DvfResultDisplay(
                    snapshot = snapshot,
                    report = liveReport,
                    showComparablesList = showComparablesList,
                    onToggleComparables = onToggleComparables,
                    onRequestCallback = onRequestCallback
                )
            }

            if (dossier.callbackRequests.isNotEmpty()) {
                HorizontalDivider(color = BorderSubtle)
                Text(
                    text = "Demandes de recontact approfondi (${dossier.callbackRequests.size})",
                    style = MaterialTheme.typography.labelLarge,
                    color = EmeraldDeep
                )
                dossier.callbackRequests.forEach { cb ->
                    Surface(
                        color = IvoryWarm,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${cb.sellerName} • ${cb.preferredSlot}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = EmeraldDeep
                                )
                                Surface(
                                    color = if (cb.status == "Traitée") SuccessContainer else WarningContainer,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = cb.status,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (cb.status == "Traitée") SuccessGreen else WarningAmber,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Tél: ${cb.sellerPhone.ifBlank { "—" }} • ${cb.projectHorizon}",
                                style = MaterialTheme.typography.bodySmall,
                                color = SlateMuted
                            )
                            if (cb.notes.isNotBlank()) {
                                Text(cb.notes, style = MaterialTheme.typography.bodySmall, color = ObsidianInk)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (cb.sellerPhone.isNotBlank()) {
                                    OutlinedButton(
                                        onClick = {
                                            val dial = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${cb.sellerPhone}"))
                                            context.startActivity(dial)
                                        },
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("Appeler", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                                SellerDocCatalogConstants.CALLBACK_STATUSES.forEach { st ->
                                    if (st != cb.status) {
                                        TextButton(onClick = { onUpdateCallbackStatus(cb.requestId, st) }) {
                                            Text(st, style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DvfResultDisplay(
    snapshot: DvfValuationSnapshot,
    report: DvfValuationReport?,
    showComparablesList: Boolean,
    onToggleComparables: () -> Unit,
    onRequestCallback: () -> Unit
) {
    Surface(
        color = EmeraldDeep,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = snapshot.resolvedAddress.ifBlank { "Secteur évalué" },
                        style = MaterialTheme.typography.labelSmall,
                        color = GoldChampagne,
                        maxLines = 2
                    )
                    Text(
                        text = if (snapshot.estimatedMidEuro > 0L) {
                            "${MontyMarketData.formatEuro(snapshot.estimatedLowEuro)} – ${MontyMarketData.formatEuro(snapshot.estimatedHighEuro)}"
                        } else {
                            "Estimation non chiffrée (0 vente DVF ≤ 300 m)"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        color = IvoryWarm,
                        fontWeight = FontWeight.Bold
                    )
                    if (snapshot.pricePerM2 > 0L) {
                        Text(
                            text = "Base centrale : ${MontyMarketData.formatEuro(snapshot.estimatedMidEuro)} (${MontyMarketData.formatEuro(snapshot.pricePerM2)}/m²)",
                            style = MaterialTheme.typography.bodySmall,
                            color = IvoryWarm.copy(alpha = 0.8f)
                        )
                    }
                }
                Surface(
                    color = ObsidianInk,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.border(1.dp, GoldAntique, RoundedCornerShape(12.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("SCORE DVF", style = MaterialTheme.typography.labelSmall, color = GoldChampagne)
                        Text(
                            text = "${snapshot.valuationScore}/100",
                            style = MaterialTheme.typography.titleLarge,
                            color = IvoryWarm,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = snapshot.scoreLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (snapshot.valuationScore >= 60) Color(0xFF6EE7B7) else Color(0xFFFBBF24)
                        )
                    }
                }
            }

            // 3 cercles concentriques 50m / 100m / 300m
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DvfPerimeterTile(
                    radius = "≤ 50 m",
                    count = snapshot.count50m,
                    avgM2 = snapshot.avgM2_50m,
                    modifier = Modifier.weight(1f)
                )
                DvfPerimeterTile(
                    radius = "≤ 100 m",
                    count = snapshot.count100m,
                    avgM2 = snapshot.avgM2_100m,
                    modifier = Modifier.weight(1f)
                )
                DvfPerimeterTile(
                    radius = "≤ 300 m",
                    count = snapshot.count300m,
                    avgM2 = snapshot.avgM2_300m,
                    modifier = Modifier.weight(1f)
                )
            }

            Text(
                text = "Source : ${snapshot.dataSource} • ${snapshot.computedAtLabel}",
                style = MaterialTheme.typography.labelSmall,
                color = IvoryWarm.copy(alpha = 0.65f)
            )
        }
    }

    // JUSTIFICATION DÉTAILLÉE DU PRIX (FACTEURS DÉTERMINANTS)
    val drivers = report?.adjustmentDrivers.orEmpty()
    if (drivers.isNotEmpty() || snapshot.justificationLines.isNotEmpty()) {
        Surface(
            color = IvoryWarm,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, GoldAntique.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "JUSTIFICATION DU PRIX ESTIMÉ",
                        style = MaterialTheme.typography.labelSmall,
                        color = GoldAntique,
                        fontWeight = FontWeight.Bold
                    )
                    val totalAdjSign = if (snapshot.totalAdjustmentPercent >= 0) "+" else ""
                    Surface(
                        color = if (snapshot.totalAdjustmentPercent >= 0) SuccessContainer else WarningContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "Ajustement net : $totalAdjSign${snapshot.totalAdjustmentPercent}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (snapshot.totalAdjustmentPercent >= 0) SuccessGreen else WarningAmber,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
                if (snapshot.baseMarketEuro > 0L) {
                    Text(
                        text = "Valeur brute secteur DVF (${MontyMarketData.formatEuro(snapshot.rawDvfPricePerM2)}/m²) : ${MontyMarketData.formatEuro(snapshot.baseMarketEuro)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = ObsidianInk,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                HorizontalDivider(color = BorderSubtle)

                if (drivers.isNotEmpty()) {
                    drivers.forEach { drv ->
                        val isPositive = drv.impactPercent > 0.0
                        val isNeutral = drv.impactPercent == 0.0
                        val pctSign = if (isPositive) "+" else ""
                        val pctLabel = if (drv.impactPercent % 1.0 == 0.0) {
                            "$pctSign${drv.impactPercent.toInt()}%"
                        } else {
                            "$pctSign${String.format(java.util.Locale.US, "%.1f", drv.impactPercent)}%"
                        }
                        val euroSign = if (drv.euroImpact > 0L) "+" else ""
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = drv.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = EmeraldDeep,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = drv.detail,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SlateMuted
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = pctLabel,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = when {
                                        isPositive -> SuccessGreen
                                        isNeutral -> SlateMuted
                                        else -> ErrorRed
                                    },
                                    fontWeight = FontWeight.Bold
                                )
                                if (drv.euroImpact != 0L) {
                                    Text(
                                        text = "$euroSign${MontyMarketData.formatEuro(drv.euroImpact)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isPositive) SuccessGreen else ErrorRed
                                    )
                                }
                            }
                        }
                    }
                } else {
                    snapshot.justificationLines.forEach { line ->
                        Text(
                            text = "• $line",
                            style = MaterialTheme.typography.bodySmall,
                            color = ObsidianInk
                        )
                    }
                }
            }
        }
    }

    if (report != null) {
        Surface(
            color = IvoryWarm,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = report.methodologyNote,
                    style = MaterialTheme.typography.bodySmall,
                    color = EmeraldDeep,
                    fontWeight = FontWeight.SemiBold
                )
                report.scoreBreakdown.forEach { (label, pts) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(label, style = MaterialTheme.typography.bodySmall, color = SlateMuted)
                        Text("+$pts pts", style = MaterialTheme.typography.labelSmall, color = EmeraldDeep, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (report.ring300m.comparables.isNotEmpty()) {
            OutlinedButton(
                onClick = onToggleComparables,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (showComparablesList) {
                        "Masquer les ventes notariales DVF (${report.ring300m.comparables.size})"
                    } else {
                        "Voir les ventes notariales DVF comparables (${report.ring300m.comparables.size})"
                    },
                    style = MaterialTheme.typography.labelLarge
                )
            }
            if (showComparablesList) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    report.ring300m.comparables.forEach { comp ->
                        Surface(
                            color = IvoryWarm,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, BorderSubtle, RoundedCornerShape(10.dp))
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${comp.propertyType} • ${comp.surfaceM2} m² • à ${comp.distanceMeters} m",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = EmeraldDeep
                                    )
                                    Text(
                                        text = "${comp.dateMutation} • ${comp.addressLabel}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = SlateMuted
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = MontyMarketData.formatEuro(comp.priceEuro),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = EmeraldDeep
                                    )
                                    Text(
                                        text = "${MontyMarketData.formatEuro(comp.pricePerM2)}/m²",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = GoldAntique
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    Button(
        onClick = onRequestCallback,
        colors = ButtonDefaults.buttonColors(containerColor = GoldAntique, contentColor = ObsidianInk),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Default.PhoneCallback, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text("Être recontacté pour approfondir cet avis de valeur", fontWeight = FontWeight.Bold)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DvfCriterionChipGroup(
    title: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = EmeraldDeep,
            fontWeight = FontWeight.SemiBold
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            options.forEach { opt ->
                FilterChip(
                    selected = selected == opt,
                    onClick = { onSelect(opt) },
                    label = { Text(opt, style = MaterialTheme.typography.labelSmall) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = EmeraldDeep,
                        selectedLabelColor = GoldChampagne
                    )
                )
            }
        }
    }
}

@Composable
private fun DvfPerimeterTile(
    radius: String,
    count: Int,
    avgM2: Long,
    modifier: Modifier = Modifier
) {
    Surface(
        color = EmeraldMid,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier.border(1.dp, GoldAntique.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(radius, style = MaterialTheme.typography.labelSmall, color = GoldChampagne, fontWeight = FontWeight.Bold)
            Text("$count ventes", style = MaterialTheme.typography.bodySmall, color = IvoryWarm)
            Text(
                text = if (avgM2 > 0L) "${MontyMarketData.formatEuro(avgM2)}/m²" else "—",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF6EE7B7)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SellerDocumentSlotRow(
    doc: SellerSaleDocument,
    onAttachFile: () -> Unit,
    onOpenLocalFile: () -> Unit,
    onEditSlot: () -> Unit
) {
    val isAlur = doc.category == "Loi ALUR"
    Surface(
        color = IvoryWarm,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                when {
                    doc.isProvided -> SuccessGreen.copy(alpha = 0.35f)
                    isAlur -> WarningAmber.copy(alpha = 0.45f)
                    else -> BorderSubtle
                },
                RoundedCornerShape(14.dp)
            )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = doc.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = EmeraldDeep,
                        fontWeight = FontWeight.Bold
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = doc.category,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isAlur) WarningAmber else GoldAntique,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (doc.required) {
                            Text("• Obligatoire", style = MaterialTheme.typography.labelSmall, color = SlateMuted)
                        }
                        if (doc.uploadedByRole.isNotBlank() && doc.isProvided) {
                            Text("• Par ${doc.uploadedByRole}", style = MaterialTheme.typography.labelSmall, color = EmeraldDeep)
                        }
                    }
                }
                Surface(
                    color = when (doc.status) {
                        "Vérifié", "Fourni" -> SuccessContainer
                        "Non applicable" -> Color.White
                        else -> WarningContainer
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = doc.status,
                        style = MaterialTheme.typography.labelSmall,
                        color = when (doc.status) {
                            "Vérifié", "Fourni" -> SuccessGreen
                            "Non applicable" -> SlateMuted
                            else -> WarningAmber
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            if (doc.summaryText.isNotBlank()) {
                Text(
                    text = doc.summaryText,
                    style = MaterialTheme.typography.bodySmall,
                    color = ObsidianInk.copy(alpha = 0.85f)
                )
            }

            if (doc.fileName.isNotBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(15.dp))
                    Text(
                        text = "${doc.fileName} (${SellerDocumentVault.formatBytes(doc.sizeBytes)})",
                        style = MaterialTheme.typography.labelSmall,
                        color = SuccessGreen
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onAttachFile,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (doc.fileName.isBlank()) "Déposer" else "Remplacer", style = MaterialTheme.typography.labelSmall)
                }
                if (doc.localVaultPath.isNotBlank()) {
                    OutlinedButton(
                        onClick = onOpenLocalFile,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(0.9f)
                    ) {
                        Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Ouvrir", style = MaterialTheme.typography.labelSmall)
                    }
                }
                OutlinedButton(
                    onClick = onEditSlot,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(0.9f)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Éditer", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
