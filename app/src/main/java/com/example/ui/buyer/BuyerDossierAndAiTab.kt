package com.example.ui.buyer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import android.content.Intent
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import com.example.data.MontyMarketData
import com.example.data.model.BuyerChatMessage
import com.example.data.model.ClientDossier
import com.example.data.model.FinanceMetrics
import com.example.data.model.HcsfCapacityResult
import com.example.data.model.PropertyItem
import com.example.data.model.SellerPropertyDossier
import com.example.data.model.VisitRecord
import com.example.ui.AiGenState
import com.example.ui.SimulatorInput
import com.example.ui.components.shareBuyerPassport
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

private data class BuyerQuickTopic(
    val topicTag: String,
    val chipLabel: String,
    val question: String
)

private val BUYER_QUICK_TOPICS = listOf(
    BuyerQuickTopic(
        topicTag = "Financement",
        chipLabel = "Taux, apport & 35% HCSF",
        question = "Quelles sont les options de financement actuelles (taux, apport / ratio prêt-valeur et règle HCSF des 35% d'endettement) pour mon dossier ?"
    ),
    BuyerQuickTopic(
        topicTag = "Frais & Taxes",
        chipLabel = "Frais de notaire & Montpellier",
        question = "Combien prévoir en frais de notaire (ancien vs neuf) et quelles taxes locales faut-il anticiper à Montpellier ?"
    ),
    BuyerQuickTopic(
        topicTag = "Offre & Compromis",
        chipLabel = "Offre d'achat & Compromis",
        question = "Quelles sont les étapes exactes pour faire une offre d'achat puis signer le compromis de vente (délais, rétractation, conditions suspensives) ?"
    ),
    BuyerQuickTopic(
        topicTag = "DPE",
        chipLabel = "Comprendre le DPE (A à G)",
        question = "Comment lire le DPE (Diagnostic de Performance Énergétique) et quel est son impact sur mon achat à Montpellier ?"
    ),
    BuyerQuickTopic(
        topicTag = "Montpellier",
        chipLabel = "Conseils quartiers Montpellier",
        question = "Quels conseils pratiques me donnez-vous pour acheter à Montpellier selon mes secteurs cibles (tramway, copropriété, PPRI, valeur patrimoniale) ?"
    ),
    BuyerQuickTopic(
        topicTag = "Audit Négo",
        chipLabel = "Auditer mon Coup de Cœur (Prix & Négo)",
        question = "Analyse mes biens favoris par rapport aux prix au m² du marché montpelliérain et suggère-moi une stratégie d'offre d'achat et de négociation réaliste."
    )
)

/**
 * Onglet 'Mon Dossier' ('clientTab === "dossier"') du Portail Acquéreur.
 * Intègre le suivi de dossier, l'Assistant IA Acquéreur propulsé par Gemini,
 * les visites partagées et le simulateur de financement.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BuyerDossierAndAiTab(
    client: ClientDossier,
    favoriteProperties: List<PropertyItem>,
    allProperties: List<PropertyItem>,
    clientVisits: List<VisitRecord>,
    sellerDossiers: List<SellerPropertyDossier> = emptyList(),
    chatMessages: List<BuyerChatMessage>,
    buyerAiState: AiGenState,
    simulatorInput: SimulatorInput,
    financeMetrics: FinanceMetrics,
    hcsfCapacityResult: HcsfCapacityResult,
    onAskAssistant: (question: String, topic: String) -> Unit,
    onClearChat: () -> Unit,
    onUpdateSimulator: ((SimulatorInput) -> SimulatorInput) -> Unit,
    onOpenCapacityScreen: () -> Unit,
    onOpenPdfPreview: () -> Unit,
    onSubmitBuyerDocOrder: (SellerPropertyDossier, PropertyItem?, String, String, Boolean) -> Unit = { _, _, _, _, _ -> },
    onUpdateRgpdConsent: (Boolean) -> Unit = {},
    onBuildFullRgpdExportJson: () -> String? = { null },
    onEraseClientRgpdData: () -> Unit = {},
    onShowToast: (String) -> Unit = {}
) {
    var questionInput by remember { mutableStateOf("") }
    var summaryDossierTarget by remember { mutableStateOf<Pair<SellerPropertyDossier, PropertyItem?>?>(null) }
    var checkoutDossierTarget by remember { mutableStateOf<Pair<SellerPropertyDossier, PropertyItem?>?>(null) }
    var showEraseConfirmDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val readinessChecks = listOf(
        "Enveloppe & Apport" to (client.budgetMax > 0 && client.downPayment > 0),
        "Accord bancaire" to client.bankApproval.isNotBlank(),
        "Shortlist constituée" to favoriteProperties.isNotEmpty(),
        "Visite planifiée/faite" to clientVisits.isNotEmpty(),
        "Conseil IA consulté" to chatMessages.isNotEmpty()
    )
    val readinessPercent = (readinessChecks.count { it.second } * 100) / readinessChecks.size

    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        // 0. PASSEPORT ACQUÉREUR PRÊT À SIGNER (GAMIFICATION VIRALE)
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = EmeraldDeep),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Verified, contentDescription = null, tint = GoldChampagne, modifier = Modifier.size(18.dp))
                            Text(
                                text = "PASSEPORT ACQUÉREUR",
                                style = MaterialTheme.typography.labelSmall,
                                color = GoldChampagne
                            )
                        }
                        Text(
                            text = "Indice Prêt à Signer : $readinessPercent%",
                            style = MaterialTheme.typography.titleLarge,
                            color = IvoryWarm
                        )
                    }
                    Button(
                        onClick = { shareBuyerPassport(context, client, readinessPercent) },
                        colors = ButtonDefaults.buttonColors(containerColor = GoldAntique, contentColor = ObsidianInk),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("share_buyer_passport_button")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Partager Passeport", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Partager", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
                LinearProgressIndicator(
                    progress = { readinessPercent / 100f },
                    color = GoldChampagne,
                    trackColor = EmeraldMid,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    readinessChecks.forEach { (label, done) ->
                        Surface(
                            color = if (done) EmeraldMid else ObsidianInk.copy(alpha = 0.45f),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.border(
                                1.dp,
                                if (done) GoldAntique.copy(alpha = 0.7f) else IvoryWarm.copy(alpha = 0.15f),
                                RoundedCornerShape(14.dp)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (done) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF6EE7B7), modifier = Modifier.size(12.dp))
                                }
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (done) IvoryWarm else IvoryWarm.copy(alpha = 0.65f)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 1. SYNTHÈSE DU DOSSIER ACQUÉREUR & ÉTAPES
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderSubtle, RoundedCornerShape(20.dp))
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "MON DOSSIER ACQUÉREUR",
                            style = MaterialTheme.typography.labelSmall,
                            color = GoldAntique
                        )
                        Text(
                            text = client.name,
                            style = MaterialTheme.typography.headlineSmall,
                            color = EmeraldDeep
                        )
                        Text(
                            text = "Budget ${MontyMarketData.formatEuro(client.budgetMax)} • Apport ${MontyMarketData.formatEuro(client.downPayment)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = SlateMuted
                        )
                    }
                    OutlinedButton(
                        onClick = onOpenPdfPreview,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("dossier_pdf_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = "Dossier PDF",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Dossier PDF", style = MaterialTheme.typography.labelLarge)
                    }
                }

                Text(
                    text = "Avancement de votre projet",
                    style = MaterialTheme.typography.labelLarge,
                    color = EmeraldDeep
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MontyMarketData.ETAPES_PROJET.forEachIndexed { idx, label ->
                        val isCurrent = idx.toLong() == client.projectStage
                        val isDone = idx.toLong() < client.projectStage
                        Surface(
                            color = when {
                                isCurrent -> EmeraldDeep
                                isDone -> SuccessContainer
                                else -> IvoryWarm
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.border(
                                1.dp,
                                when {
                                    isCurrent -> GoldAntique
                                    isDone -> SuccessGreen.copy(alpha = 0.35f)
                                    else -> BorderSubtle
                                },
                                RoundedCornerShape(12.dp)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Étape ${idx + 1}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isCurrent) GoldChampagne else SlateMuted
                                )
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                    color = when {
                                        isCurrent -> IvoryWarm
                                        isDone -> SuccessGreen
                                        else -> SlateMuted
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // 1B. DOSSIERS TECHNIQUES & LOI ALUR DES BIENS (RÉSUMÉ GRATUIT OU PACK PAYANT)
        if (sellerDossiers.isNotEmpty()) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, GoldAntique.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Documents Vendeurs & Loi ALUR (${sellerDossiers.size})",
                        style = MaterialTheme.typography.titleMedium,
                        color = EmeraldDeep,
                        fontWeight = FontWeight.Bold
                    )
                    val prioritized = remember(sellerDossiers, favoriteProperties) {
                        val favIds = favoriteProperties.map { it.id }.toSet()
                        sellerDossiers.sortedByDescending { favIds.contains(it.propertyId) }
                    }
                    prioritized.take(3).forEach { dossier ->
                        val prop = allProperties.firstOrNull { it.id == dossier.propertyId }
                        if (prop != null) {
                            BuyerPropertyDocsSectionCard(
                                property = prop,
                                dossier = dossier,
                                client = client,
                                onOpenSummary = { summaryDossierTarget = dossier to prop },
                                onOpenCheckoutOrPack = { checkoutDossierTarget = dossier to prop }
                            )
                        }
                    }
                }
            }
        }

        // 2. ASSISTANT IA ACQUÉREUR (MONTY AI BUYER GUIDE)
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.5.dp, GoldAntique.copy(alpha = 0.55f), RoundedCornerShape(22.dp))
                .testTag("buyer_ai_assistant_card")
        ) {
            Column {
                // En-tête luxueux de l'assistant
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(EmeraldDeep)
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(EmeraldMid)
                                .border(1.dp, GoldAntique, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "Assistant IA Acquéreur",
                                tint = GoldChampagne,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Assistant Mon Agent Immo",
                                style = MaterialTheme.typography.titleLarge,
                                color = IvoryWarm
                            )
                            Text(
                                text = "Financement • Frais de notaire • Compromis • DPE • Montpellier",
                                style = MaterialTheme.typography.bodySmall,
                                color = GoldChampagne
                            )
                        }
                    }
                    if (chatMessages.isNotEmpty()) {
                        IconButton(
                            onClick = onClearChat,
                            modifier = Modifier.testTag("clear_buyer_chat_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Effacer la conversation",
                                tint = IvoryWarm.copy(alpha = 0.75f)
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Questions rapides sur votre achat à Montpellier :",
                        style = MaterialTheme.typography.labelLarge,
                        color = EmeraldDeep
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        BUYER_QUICK_TOPICS.forEach { item ->
                            Surface(
                                color = IvoryWarm,
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier
                                    .border(1.dp, GoldAntique.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                                    .clip(RoundedCornerShape(20.dp))
                                    .clickable(enabled = buyerAiState !is AiGenState.Loading && buyerAiState !is AiGenState.Streaming) {
                                        onAskAssistant(item.question, item.topicTag)
                                    }
                                    .testTag("buyer_topic_${item.topicTag.lowercase().replace(" ", "_")}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = GoldAntique,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = item.chipLabel,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = EmeraldDeep
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = BorderSubtle)

                    // Fil de conversation
                    if (chatMessages.isEmpty() && buyerAiState is AiGenState.Idle) {
                        Surface(
                            color = IvoryWarm,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Bonjour ${client.name.substringAfter("—").trim().ifBlank { client.name }} !",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = EmeraldDeep
                                )
                                Text(
                                    text = "Posez-moi vos questions sur les taux immobiliers et le ratio HCSF 35%, les frais de notaire et taxes à Montpellier, le déroulé d'une offre et du compromis de vente, la lecture du DPE, ou les spécificités des quartiers montpelliérains.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ObsidianInk.copy(alpha = 0.8f)
                                )
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            chatMessages.forEach { msg ->
                                val isUser = msg.role == "user"
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                                ) {
                                    Surface(
                                        color = if (isUser) EmeraldDeep else IvoryWarm,
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier
                                            .fillMaxWidth(0.92f)
                                            .then(
                                                if (!isUser) Modifier.border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
                                                else Modifier
                                            )
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text(
                                                text = if (isUser) "Vous" else "Assistant Mon Agent Immo",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (isUser) GoldChampagne else GoldAntique
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = msg.content,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (isUser) IvoryWarm else ObsidianInk
                                            )
                                        }
                                    }
                                }
                            }

                            when (buyerAiState) {
                                is AiGenState.Loading -> {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.padding(6.dp)
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            color = EmeraldDeep,
                                            strokeWidth = 2.dp
                                        )
                                        Text(
                                            text = "Analyse de votre dossier et rédaction de la réponse...",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = SlateMuted
                                        )
                                    }
                                }
                                is AiGenState.Streaming -> {
                                    Surface(
                                        color = IvoryWarm,
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier
                                            .fillMaxWidth(0.92f)
                                            .border(1.dp, GoldAntique, RoundedCornerShape(16.dp))
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text(
                                                text = "Assistant Mon Agent Immo (en direct...)",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = GoldAntique
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = buyerAiState.text,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = ObsidianInk
                                            )
                                        }
                                    }
                                }
                                is AiGenState.Error -> {
                                    Surface(
                                        color = ErrorContainer,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = buyerAiState.message,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = ErrorRed,
                                            modifier = Modifier.padding(12.dp)
                                        )
                                    }
                                }
                                else -> Unit
                            }
                        }
                    }

                    // Zone de saisie libre
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = questionInput,
                            onValueChange = { questionInput = it },
                            placeholder = {
                                Text(
                                    "Ex : Quel est le délai légal après un compromis ?",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            },
                            maxLines = 3,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("buyer_ai_input")
                        )
                        Button(
                            onClick = {
                                val q = questionInput.trim()
                                if (q.isNotEmpty()) {
                                    questionInput = ""
                                    onAskAssistant(q, "Question libre")
                                }
                            },
                            enabled = questionInput.isNotBlank() &&
                                buyerAiState !is AiGenState.Loading &&
                                buyerAiState !is AiGenState.Streaming,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = EmeraldDeep,
                                contentColor = GoldChampagne
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .height(52.dp)
                                .testTag("buyer_ai_send_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Envoyer la question"
                            )
                        }
                    }
                }
            }
        }

        // 3. VISITES & COMPTES RENDUS PARTAGÉS
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderSubtle, RoundedCornerShape(20.dp))
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = GoldAntique)
                    Text(
                        text = "Vos Visites & Comptes Rendus (${clientVisits.size})",
                        style = MaterialTheme.typography.titleLarge,
                        color = EmeraldDeep
                    )
                }

                if (clientVisits.isEmpty()) {
                    Text(
                        text = "Aucune visite planifiée pour le moment. Demandez une visite depuis l'onglet Explorer.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SlateMuted
                    )
                } else {
                    clientVisits.forEach { visit ->
                        val prop = allProperties.firstOrNull { it.id == visit.propertyId }
                        Surface(
                            color = IvoryWarm,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, BorderSubtle, RoundedCornerShape(14.dp))
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
                                        text = prop?.title ?: "Bien immobilier",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = EmeraldDeep,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Surface(
                                        color = if (visit.status == "Réalisée") SuccessContainer else WarningContainer,
                                        shape = RoundedCornerShape(16.dp)
                                    ) {
                                        Text(
                                            text = visit.status,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (visit.status == "Réalisée") SuccessGreen else WarningAmber,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Schedule,
                                        contentDescription = null,
                                        tint = GoldAntique,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "${visit.date} • ${visit.timeSlot}" +
                                            (visit.overallRating?.let { " • Note $it/10" } ?: ""),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = SlateMuted
                                    )
                                }
                                if (visit.clientImpressions.isNotBlank()) {
                                    Text(
                                        text = visit.clientImpressions,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = ObsidianInk
                                    )
                                }
                                if (visit.nextAction.isNotBlank()) {
                                    Text(
                                        text = "→ Prochaine étape : ${visit.nextAction}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = EmeraldDeep
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. SIMULATEUR DE FINANCEMENT CLIENT
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderSubtle, RoundedCornerShape(20.dp))
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Calculate, contentDescription = null, tint = GoldAntique)
                    Text(
                        text = "Simulation de Votre Enveloppe Bancaire",
                        style = MaterialTheme.typography.titleLarge,
                        color = EmeraldDeep
                    )
                }

                Button(
                    onClick = onOpenCapacityScreen,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EmeraldDeep,
                        contentColor = GoldChampagne
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("buyer_open_hcsf_capacity_button")
                ) {
                    Icon(Icons.Default.AccountBalance, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Simulateur Capacité HCSF 35% (${MontyMarketData.formatEuro(hcsfCapacityResult.maxPropertyPriceNet)})",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = simulatorInput.price.toString(),
                        onValueChange = { v ->
                            v.toLongOrNull()?.let { p -> onUpdateSimulator { it.copy(price = p) } }
                        },
                        label = { Text("Prix du bien (€)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = simulatorInput.downPayment.toString(),
                        onValueChange = { v ->
                            v.toLongOrNull()?.let { d -> onUpdateSimulator { it.copy(downPayment = d) } }
                        },
                        label = { Text("Apport (€)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = simulatorInput.durationYears.toString(),
                        onValueChange = { v ->
                            v.toIntOrNull()?.coerceIn(5, 30)?.let { y ->
                                onUpdateSimulator { it.copy(durationYears = y) }
                            }
                        },
                        label = { Text("Durée (ans)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = simulatorInput.interestRatePercent.toString(),
                        onValueChange = { v ->
                            v.replace(',', '.').toDoubleOrNull()?.let { r ->
                                onUpdateSimulator { it.copy(interestRatePercent = r) }
                            }
                        },
                        label = { Text("Taux nominal (%)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Surface(
                    color = EmeraldDeep,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
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
                            Text(
                                text = "Mensualité est. (assurance incl.)",
                                style = MaterialTheme.typography.bodySmall,
                                color = IvoryWarm.copy(alpha = 0.75f)
                            )
                            Text(
                                text = "${MontyMarketData.formatEuro(financeMetrics.monthlyInclInsurance)} / mois",
                                style = MaterialTheme.typography.headlineSmall,
                                color = GoldChampagne
                            )
                        }
                        HorizontalDivider(color = IvoryWarm.copy(alpha = 0.14f))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Frais de notaire est.", style = MaterialTheme.typography.bodySmall, color = IvoryWarm.copy(alpha = 0.65f))
                                Text(MontyMarketData.formatEuro(financeMetrics.notaryFees), style = MaterialTheme.typography.titleMedium, color = IvoryWarm)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Capital emprunté", style = MaterialTheme.typography.bodySmall, color = IvoryWarm.copy(alpha = 0.65f))
                                Text(MontyMarketData.formatEuro(financeMetrics.borrowedAmount), style = MaterialTheme.typography.titleMedium, color = IvoryWarm)
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Taux d'endettement HCSF", style = MaterialTheme.typography.bodySmall, color = IvoryWarm.copy(alpha = 0.65f))
                                Text(
                                    text = "${financeMetrics.debtRatioPercent} % " +
                                        if (financeMetrics.debtRatioPercent <= 35.0) "(Conforme)" else "(Vigilance >35%)",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (financeMetrics.debtRatioPercent <= 35.0) Color(0xFF6EE7B7) else Color(0xFFFBBF24)
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Reste à vivre mensuel", style = MaterialTheme.typography.bodySmall, color = IvoryWarm.copy(alpha = 0.65f))
                                Text(MontyMarketData.formatEuro(financeMetrics.remainderToLive), style = MaterialTheme.typography.titleMedium, color = IvoryWarm)
                            }
                        }
                    }
                }
            }
        }

        // 5. CONFIDENTIALITÉ & DROITS RGPD (ART. 6, 15, 17 & 20)
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderSubtle, RoundedCornerShape(20.dp))
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = EmeraldDeep)
                    Column {
                        Text("CONFIDENTIALITÉ & CONFORMITÉ RGPD", style = MaterialTheme.typography.labelSmall, color = GoldAntique)
                        Text("Vos droits sur vos données", style = MaterialTheme.typography.titleMedium, color = EmeraldDeep, fontWeight = FontWeight.Bold)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Consentement explicite au suivi projet", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = if (client.rgpdConsentGranted) {
                                "Accord enregistré${if (client.rgpdConsentDate.isNotBlank()) " le ${client.rgpdConsentDate}" else ""}"
                            } else {
                                "Activez pour autoriser le traitement de vos critères et alertes"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = SlateMuted
                        )
                    }
                    Switch(
                        checked = client.rgpdConsentGranted,
                        onCheckedChange = onUpdateRgpdConsent,
                        modifier = Modifier.testTag("buyer_rgpd_consent_switch")
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            val json = onBuildFullRgpdExportJson()
                            if (json.isNullOrBlank()) {
                                onShowToast("Export RGPD indisponible pour le moment.")
                            } else {
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/json"
                                    putExtra(Intent.EXTRA_SUBJECT, "Export RGPD Art. 15/20 — ${client.name}")
                                    putExtra(Intent.EXTRA_TEXT, json)
                                }
                                context.startActivity(Intent.createChooser(intent, "Exporter mes données RGPD (JSON)"))
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).testTag("buyer_rgpd_export_button")
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Export JSON", style = MaterialTheme.typography.labelSmall)
                    }
                    OutlinedButton(
                        onClick = { showEraseConfirmDialog = true },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).testTag("buyer_rgpd_erase_button")
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Effacer (Art. 17)", style = MaterialTheme.typography.labelSmall, color = ErrorRed)
                    }
                }
            }
        }
    }

    if (showEraseConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showEraseConfirmDialog = false },
            title = { Text("Droit à l'effacement (Art. 17 RGPD)", fontWeight = FontWeight.Bold, color = ErrorRed) },
            text = {
                Text("Confirmez-vous la suppression définitive de votre dossier acquéreur, de vos messages assistant et de vos visites/offres associées ?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showEraseConfirmDialog = false
                        onEraseClientRgpdData()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed, contentColor = Color.White)
                ) { Text("Effacer définitivement") }
            },
            dismissButton = {
                TextButton(onClick = { showEraseConfirmDialog = false }) { Text("Annuler") }
            }
        )
    }

    summaryDossierTarget?.let { (dossier, prop) ->
        BuyerDocumentSummaryDialog(
            dossier = dossier,
            property = prop,
            isUnlocked = dossier.isUnlockedForBuyer(client.id),
            onDismiss = { summaryDossierTarget = null },
            onUpgradeToPaidPack = { checkoutDossierTarget = dossier to prop }
        )
    }

    checkoutDossierTarget?.let { (dossier, prop) ->
        BuyerDocumentCheckoutAndPackDialog(
            dossier = dossier,
            property = prop,
            client = client,
            onDismiss = { checkoutDossierTarget = null },
            onSubmitOrder = { email, method, markPaid ->
                onSubmitBuyerDocOrder(dossier, prop, email, method, markPaid)
            },
            onShowToast = onShowToast
        )
    }
}
