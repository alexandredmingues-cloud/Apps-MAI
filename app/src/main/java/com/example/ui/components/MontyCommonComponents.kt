package com.example.ui.components

import android.content.Context
import android.content.Intent
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import com.example.data.SellerDocumentVault
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.data.MontyMarketData
import com.example.data.model.BadgeLevel
import com.example.data.model.ClientDossier
import com.example.data.model.CompatibilityBadge
import com.example.data.model.PropertyItem
import com.example.data.model.VisitRecord
import com.example.data.model.WorkspaceMode
import com.example.security.AppSecurityManager
import com.example.security.RateLimitChannel
import com.example.security.SecurityPosture
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
import kotlin.math.roundToInt

@DrawableRes
fun propertyImageRes(property: PropertyItem): Int {
    val key = "${property.id} ${property.sector} ${property.type} ${property.title}".lowercase()
    return when {
        key.contains("port marianne") || key.contains("antigone") || key.contains("dernier") ->
            R.drawable.img_prop_port_marianne_1795089337144
        key.contains("villa") || key.contains("baillargues") || key.contains("piscine") ->
            R.drawable.img_prop_villa_baillargues_1795089337422
        key.contains("ovalie") || key.contains("juvignac") ->
            R.drawable.img_prop_ovalie_terrace_1795089337394
        else ->
            R.drawable.img_montpellier_hero_1795052666541
    }
}

data class SectorMarketDelta(
    val sectorAvgM2: Int,
    val propertyM2: Int,
    val deltaPercent: Int,
    val label: String,
    val isGoodDeal: Boolean
)

fun computeSectorMarketDelta(property: PropertyItem): SectorMarketDelta {
    val propertyM2 = if (property.surface > 0L) (property.price / property.surface).toInt() else 0
    val sector = MontyMarketData.SECTEURS_MONTPELLIER.firstOrNull {
        it.name.contains(property.sector.substringBefore(" "), ignoreCase = true)
    }
    val isHouse = property.type.contains("Maison", ignoreCase = true) ||
        property.type.contains("Villa", ignoreCase = true)
    val avgM2 = if (isHouse && sector?.priceM2House != null) {
        sector.priceM2House
    } else {
        sector?.priceM2Apartment ?: 3800
    }
    val deltaPct = if (avgM2 > 0) (((propertyM2 - avgM2).toDouble() / avgM2) * 100.0).roundToInt() else 0
    val label = when {
        deltaPct <= -5 -> "Opportunité ${deltaPct}% vs secteur"
        deltaPct in -4..5 -> "Prix marché (${if (deltaPct >= 0) "+$deltaPct" else "$deltaPct"}%)"
        else -> "+${deltaPct}% (Prestations hautes)"
    }
    return SectorMarketDelta(
        sectorAvgM2 = avgM2,
        propertyM2 = propertyM2,
        deltaPercent = deltaPct,
        label = label,
        isGoodDeal = deltaPct <= 2
    )
}

fun sharePropertyWithPartner(
    context: Context,
    property: PropertyItem,
    score: Int,
    estimatedMonthlyEuro: Long
) {
    val priceM2 = if (property.surface > 0L) property.price / property.surface else 0L
    val delta = computeSectorMarketDelta(property)
    val shareText = buildString {
        appendLine("🏡 Coup de cœur repéré sur Mon Agent Immo !")
        appendLine()
        appendLine("✨ ${property.title}")
        appendLine("📍 ${property.sector}")
        appendLine("💶 ${MontyMarketData.formatEuro(property.price)} (${MontyMarketData.formatEuro(priceM2)}/m² • ${delta.label})")
        appendLine("📐 ${property.surface} m² • ${property.bedrooms} chambres • DPE ${property.dpe}")
        appendLine("🌿 ${property.exterior} • ${property.parking}")
        appendLine("🎯 Compatibilité avec nos critères : $score/100")
        appendLine("🏦 Mensualité estimée : ~${MontyMarketData.formatEuro(estimatedMonthlyEuro)}/mois")
        appendLine()
        append("Qu'en penses-tu ? On demande une visite ?")
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Bien repéré : ${property.title}")
        putExtra(Intent.EXTRA_TEXT, shareText)
    }
    context.startActivity(Intent.createChooser(intent, "Partager ce bien avec mes proches"))
}

fun shareBuyerPassport(
    context: Context,
    client: ClientDossier,
    readinessPercent: Int
) {
    val shareText = buildString {
        appendLine("🏅 PASSEPORT ACQUÉREUR CERTIFIÉ — MON AGENT IMMO")
        appendLine("Acquéreur : ${client.name}")
        appendLine("Indice Prêt à Signer : $readinessPercent / 100")
        appendLine("Enveloppe validée : ${MontyMarketData.formatEuro(client.budgetMax)} (Apport ${MontyMarketData.formatEuro(client.downPayment)})")
        appendLine("Financement : ${client.bankApproval}")
        appendLine("Recherche : ${client.propertyType} ≥ ${client.minSurface} m² (${client.minRooms} ch.) sur ${client.sectors.joinToString(", ")}")
        append("Délai cible : ${client.timelineDelai}")
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Passeport Acquéreur — ${client.name}")
        putExtra(Intent.EXTRA_TEXT, shareText)
    }
    context.startActivity(Intent.createChooser(intent, "Partager mon Passeport Acquéreur"))
}

@Composable
fun PropertyEditorialHeader(
    property: PropertyItem,
    score: Int,
    height: Dp = 185.dp,
    topTag: String = property.sector.uppercase()
) {
    val context = LocalContext.current
    val delta = computeSectorMarketDelta(property)
    val customBitmap = remember(property.id, property.photoVaultPaths) {
        property.photoVaultPaths.firstOrNull()?.let { relPath ->
            SellerDocumentVault.loadDownscaledPhotoBitmap(context, relPath, maxDimPx = 1024)
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
    ) {
        if (customBitmap != null) {
            Image(
                bitmap = customBitmap.asImageBitmap(),
                contentDescription = property.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Image(
                painter = painterResource(id = propertyImageRes(property)),
                contentDescription = property.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            ObsidianInk.copy(alpha = 0.25f),
                            ObsidianInk.copy(alpha = 0.15f),
                            EmeraldDeep.copy(alpha = 0.92f)
                        )
                    )
                )
        )
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = if (delta.isGoodDeal) SuccessGreen.copy(alpha = 0.92f) else ObsidianInk.copy(alpha = 0.78f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.border(
                        1.dp,
                        if (delta.isGoodDeal) Color(0xFF6EE7B7) else GoldAntique.copy(alpha = 0.5f),
                        RoundedCornerShape(10.dp)
                    )
                ) {
                    Text(
                        text = delta.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = IvoryWarm,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                    )
                }
                if (property.publisherType == "PARTICULIER") {
                    Surface(
                        color = GoldAntique.copy(alpha = 0.92f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "Particulier",
                            style = MaterialTheme.typography.labelSmall,
                            color = ObsidianInk,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                        )
                    }
                }
                if (property.photoCount > 0) {
                    Surface(
                        color = ObsidianInk.copy(alpha = 0.72f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "${property.photoCount} photos",
                            style = MaterialTheme.typography.labelSmall,
                            color = GoldChampagne,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                        )
                    }
                }
            }
            ScoreBadge(score = score)
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Text(
                text = topTag,
                style = MaterialTheme.typography.labelSmall,
                color = GoldChampagne
            )
            Text(
                text = property.title,
                style = MaterialTheme.typography.titleLarge,
                color = IvoryWarm,
                maxLines = 2
            )
            Text(
                text = "${property.exterior} • ${property.parking}",
                style = MaterialTheme.typography.bodySmall,
                color = IvoryWarm.copy(alpha = 0.85f),
                maxLines = 1
            )
        }
    }
}

@Composable
fun MontyEmblemIcon(size: Dp = 36.dp) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        drawRoundRect(
            color = EmeraldDeep,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.22f, h * 0.22f)
        )
        drawRoundRect(
            color = GoldAntique,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.22f, h * 0.22f),
            style = Stroke(width = w * 0.04f)
        )
        val roof = Path().apply {
            moveTo(w * 0.24f, h * 0.42f)
            lineTo(w * 0.50f, h * 0.21f)
            lineTo(w * 0.76f, h * 0.42f)
        }
        drawPath(
            path = roof,
            color = GoldChampagne,
            style = Stroke(width = w * 0.045f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        val monogram = Path().apply {
            moveTo(w * 0.29f, h * 0.75f)
            lineTo(w * 0.29f, h * 0.45f)
            lineTo(w * 0.50f, h * 0.62f)
            lineTo(w * 0.71f, h * 0.45f)
            lineTo(w * 0.71f, h * 0.75f)
        }
        drawPath(
            path = monogram,
            color = IvoryWarm,
            style = Stroke(width = w * 0.055f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        drawCircle(
            color = GoldAntique,
            radius = w * 0.045f,
            center = Offset(w * 0.50f, h * 0.37f)
        )
    }
}

@Composable
fun MontyBrandHeader(
    light: Boolean = false,
    subtitle: String = "Montpellier • Advisory"
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        MontyEmblemIcon(size = 38.dp)
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "MON AGENT",
                    style = MaterialTheme.typography.titleLarge,
                    color = if (light) IvoryWarm else ObsidianInk,
                    maxLines = 1
                )
                Surface(
                    color = GoldAntique.copy(alpha = 0.22f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.border(1.dp, GoldAntique.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                ) {
                    Text(
                        text = "IMMO",
                        style = MaterialTheme.typography.labelSmall,
                        color = GoldAntique,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Text(
                text = subtitle.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = if (light) IvoryWarm.copy(alpha = 0.65f) else SlateMuted
            )
        }
    }
}

@Composable
fun WorkspaceSwitcherBar(
    currentMode: WorkspaceMode,
    activeClient: ClientDossier?,
    onSwitchMode: (WorkspaceMode) -> Unit,
    onOpenDossierPdf: () -> Unit,
    onSignOut: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var showSecurityDialog by remember { mutableStateOf(false) }

    Surface(
        color = ObsidianInk,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(
                color = EmeraldMid.copy(alpha = 0.45f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.border(1.dp, GoldAntique.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            ) {
                Row(
                    modifier = Modifier.padding(3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    val pills = listOf(
                        Triple(WorkspaceMode.BUYER, "Acquéreur", "workspace_pill_buyer"),
                        Triple(WorkspaceMode.SELLER, "Vendeur", "workspace_pill_seller"),
                        Triple(WorkspaceMode.ADVISOR, "Conseiller", "workspace_pill_advisor")
                    )
                    pills.forEach { (mode, label, tag) ->
                        val selected = currentMode == mode
                        Surface(
                            color = if (selected) GoldAntique else Color.Transparent,
                            shape = RoundedCornerShape(9.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(9.dp))
                                .clickable { if (!selected) onSwitchMode(mode) }
                                .testTag(tag)
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected) ObsidianInk else IvoryWarm.copy(alpha = 0.85f),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp)
                            )
                        }
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Surface(
                    color = SuccessGreen.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(9.dp),
                    modifier = Modifier
                        .border(1.dp, Color(0xFF34D399).copy(alpha = 0.45f), RoundedCornerShape(9.dp))
                        .clip(RoundedCornerShape(9.dp))
                        .clickable { showSecurityDialog = true }
                        .testTag("security_shield_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VerifiedUser,
                            contentDescription = "Bouclier Sécurité Mon Agent Immo",
                            tint = Color(0xFF6EE7B7),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Sécurisé",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF6EE7B7),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                IconButton(
                    onClick = onOpenDossierPdf,
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("open_pdf_preview_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = "Aperçu Dossier PDF",
                        tint = GoldChampagne,
                        modifier = Modifier.size(18.dp)
                    )
                }

                if (onSignOut != null) {
                    IconButton(
                        onClick = onSignOut,
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("sign_out_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Déconnexion",
                            tint = IvoryWarm.copy(alpha = 0.7f),
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }
        }
    }

    if (showSecurityDialog) {
        val posture = remember(showSecurityDialog) {
            AppSecurityManager.inspectRuntimeIntegrity(context)
        }
        SecurityShieldDialog(
            posture = posture,
            onDismiss = { showSecurityDialog = false },
            onSignOut = onSignOut
        )
    }
}

@Composable
fun SecurityShieldDialog(
    posture: SecurityPosture,
    onDismiss: () -> Unit,
    onSignOut: (() -> Unit)? = null
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 24.dp),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(EmeraldDeep)
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = Color(0xFF6EE7B7),
                            modifier = Modifier.size(24.dp)
                        )
                        Column {
                            Text(
                                text = "Bouclier Sécurité & Anti-Scraping",
                                style = MaterialTheme.typography.titleMedium,
                                color = IvoryWarm,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${posture.activeShieldCount}/6 défenses actives • Cert ${posture.signingCertSha256Short}",
                                style = MaterialTheme.typography.labelSmall,
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
                    SecurityCheckRow(
                        title = if (posture.isDebugBuild) {
                            "Anti-Capture Écran (FLAG_SECURE — Actif en Release)"
                        } else {
                            "Anti-Capture & Anti-Scraping Écran (FLAG_SECURE)"
                        },
                        detail = if (posture.isDebugBuild) {
                            "Relâché en aperçu Debug pour éviter l'écran noir sur l'émulateur streaming ; activé automatiquement en Release."
                        } else {
                            "Captures d'écran, enregistrements vidéo et aperçus multi-tâches bloqués au niveau système."
                        }
                    )
                    SecurityCheckRow(
                        title = "Rideau Privé & Anti-Tapjacking",
                        detail = "Masquage instantané hors focus et rejet automatique des clics sous fenêtre superposée."
                    )
                    SecurityCheckRow(
                        title = "Firestore Zero-Trust + App Check + Quotas",
                        detail = "Isolation par UID Google non-anonyme, validation stricte de schéma et plafond limit(100) anti-aspiration."
                    )
                    SecurityCheckRow(
                        title = "Anti-Reverse & TLS 1.3 Strict",
                        detail = "Cleartext HTTP interdit, certificats proxy tiers rejetés, R8 activé en release, sauvegardes ADB/Cloud désactivées."
                    )
                    SecurityCheckRow(
                        title = "Anti-Hook & Sanitisation IA",
                        detail = "Contrôle d'intégrité /proc/self/maps (Frida/Xposed), filtrage anti-injection et rate-limiter temps réel."
                    )

                    Surface(
                        color = IvoryWarm,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "QUOTAS ANTI-SCRAPING (FENÊTRE 60s)",
                                style = MaterialTheme.typography.labelSmall,
                                color = EmeraldDeep,
                                fontWeight = FontWeight.Bold
                            )
                            val aiUsed = AppSecurityManager.rateLimiter.recentCount(RateLimitChannel.AI_ADVISOR) +
                                AppSecurityManager.rateLimiter.recentCount(RateLimitChannel.AI_BUYER_CHAT) +
                                AppSecurityManager.rateLimiter.recentCount(RateLimitChannel.AI_AUTO_RELEVANCE)
                            val writeUsed = AppSecurityManager.rateLimiter.recentCount(RateLimitChannel.FIRESTORE_WRITE)
                            Text(
                                text = "Requêtes IA récentes : $aiUsed • Mutations cloud récentes : $writeUsed/30",
                                style = MaterialTheme.typography.bodySmall,
                                color = ObsidianInk
                            )
                            if (posture.isDebugBuild) {
                                Text(
                                    text = "Mode Aperçu Debug : garde-fous actifs sans bloquer l'émulateur de test.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SlateMuted
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onSignOut != null) {
                        TextButton(onClick = {
                            onDismiss()
                            onSignOut()
                        }) {
                            Text("Verrouiller & Déconnecter", color = ErrorRed)
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldDeep, contentColor = GoldChampagne)
                    ) {
                        Text("Fermer")
                    }
                }
            }
        }
    }
}

@Composable
private fun SecurityCheckRow(title: String, detail: String) {
    Surface(
        color = SuccessContainer.copy(alpha = 0.55f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, SuccessGreen.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
    ) {
        Row(
            modifier = Modifier.padding(11.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = SuccessGreen,
                modifier = Modifier.size(18.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = EmeraldDeep,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = ObsidianInk.copy(alpha = 0.85f)
                )
            }
        }
    }
}

@Composable
fun ScoreBadge(score: Int, modifier: Modifier = Modifier) {
    Surface(
        color = ObsidianInk.copy(alpha = 0.88f),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.border(1.dp, GoldAntique, RoundedCornerShape(12.dp))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "MATCH",
                style = MaterialTheme.typography.labelSmall,
                color = GoldChampagne
            )
            Text(
                text = "$score/100",
                style = MaterialTheme.typography.titleMedium,
                color = IvoryWarm,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun DpeBadge(dpe: String) {
    val isGood = dpe in listOf("A", "B", "C")
    Surface(
        color = if (isGood) SuccessContainer else WarningContainer,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.border(
            1.dp,
            if (isGood) SuccessGreen.copy(alpha = 0.35f) else WarningAmber.copy(alpha = 0.35f),
            RoundedCornerShape(8.dp)
        )
    ) {
        Text(
            text = "DPE $dpe",
            style = MaterialTheme.typography.labelSmall,
            color = if (isGood) SuccessGreen else WarningAmber,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CompatibilityBadgeRow(badges: List<CompatibilityBadge>) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        badges.forEach { badge ->
            val (bg, fg) = when (badge.level) {
                BadgeLevel.OK -> SuccessContainer to SuccessGreen
                BadgeLevel.WARN -> WarningContainer to WarningAmber
                BadgeLevel.ALERT -> ErrorContainer to ErrorRed
            }
            Surface(
                color = bg,
                shape = RoundedCornerShape(20.dp)
            ) {
                Text(
                    text = (if (badge.level == BadgeLevel.OK) "✓ " else "! ") + badge.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = fg,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
fun DossierPdfPreviewDialog(
    client: ClientDossier,
    properties: List<PropertyItem>,
    visits: List<VisitRecord>,
    onDismiss: () -> Unit,
    onShareReport: () -> Unit
) {
    val favoriteProps = properties.filter { client.selectedPropertyIds.contains(it.id) }
    val clientVisits = visits.filter { it.clientId == client.id && it.sharedWithClient }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(vertical = 24.dp),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(EmeraldDeep)
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Dossier Patrimonial Acquéreur",
                            style = MaterialTheme.typography.titleLarge,
                            color = IvoryWarm
                        )
                        Text(
                            text = "Notes confidentielles exclues • Prêt au partage",
                            style = MaterialTheme.typography.bodySmall,
                            color = GoldChampagne
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
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    MontyBrandHeader(subtitle = "Human Immobilier • Montpellier")
                    HorizontalDivider(color = BorderSubtle)

                    Surface(
                        color = IvoryWarm,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = client.name,
                                style = MaterialTheme.typography.titleLarge,
                                color = EmeraldDeep
                            )
                            Text(text = client.situation, style = MaterialTheme.typography.bodySmall, color = SlateMuted)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Enveloppe : ${MontyMarketData.formatEuro(client.budgetMax)} FAI (Apport ${MontyMarketData.formatEuro(client.downPayment)})",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Étape : ${MontyMarketData.ETAPES_PROJET.getOrElse(client.projectStage.toInt()) { "Découverte" }} • ${client.propertyType} ≥ ${client.minSurface} m²",
                                style = MaterialTheme.typography.bodySmall,
                                color = ObsidianInk
                            )
                        }
                    }

                    Text(
                        text = "SÉLECTION RETENUE (${favoriteProps.size})",
                        style = MaterialTheme.typography.labelSmall,
                        color = EmeraldDeep
                    )

                    if (favoriteProps.isEmpty()) {
                        Text(
                            text = "Aucun bien sélectionné pour le moment.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SlateMuted
                        )
                    } else {
                        favoriteProps.forEach { prop ->
                            val compat = MontyMarketData.computeCompatibility(prop, client)
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(prop.title, style = MaterialTheme.typography.titleMedium, color = EmeraldDeep)
                                        Text(
                                            "${prop.sector} • ${prop.surface} m² • ${prop.bedrooms} ch. • DPE ${prop.dpe}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = SlateMuted
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            MontyMarketData.formatEuro(prop.price),
                                            style = MaterialTheme.typography.titleMedium,
                                            color = EmeraldDeep
                                        )
                                        Text(
                                            "${compat.score}/100",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = SuccessGreen
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Text(
                        text = "VISITES & SUIVI (${clientVisits.size})",
                        style = MaterialTheme.typography.labelSmall,
                        color = EmeraldDeep
                    )
                    clientVisits.forEach { v ->
                        val p = properties.firstOrNull { it.id == v.propertyId }
                        Surface(
                            color = IvoryWarm,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    "${p?.title ?: "Bien"} — ${v.date} (${v.timeSlot})",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "Statut : ${v.status}" + (v.overallRating?.let { " • Note $it/10" } ?: ""),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SlateMuted
                                )
                                if (v.clientImpressions.isNotBlank()) {
                                    Text(v.clientImpressions, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Fermer")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onShareReport,
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldDeep, contentColor = GoldChampagne)
                    ) {
                        Text("Partager le dossier")
                    }
                }
            }
        }
    }
}
