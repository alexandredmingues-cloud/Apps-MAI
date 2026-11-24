package com.example.ui.finance

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HomeWork
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.MontyMarketData
import com.example.data.model.ClientDossier
import com.example.data.model.HcsfCapacityInput
import com.example.data.model.HcsfCapacityResult
import com.example.data.model.PropertyItem
import com.example.security.AppSecurityManager
import com.example.security.RateLimitChannel
import com.example.security.RateLimitDecision
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.EmeraldDeep
import com.example.ui.theme.EmeraldMid
import com.example.ui.theme.EmeraldSurface
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
import kotlin.math.roundToLong

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HcsfCapacityScreen(
    input: HcsfCapacityInput,
    result: HcsfCapacityResult,
    activeClient: ClientDossier?,
    properties: List<PropertyItem>,
    onUpdateInput: ((HcsfCapacityInput) -> HcsfCapacityInput) -> Unit,
    onResetFromClient: () -> Unit,
    onApplyToSimulator: () -> Unit,
    onSaveBudgetToDossier: () -> Unit,
    onShowToast: (String) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current

    var incomeText by remember { mutableStateOf(input.monthlyNetIncome.toString()) }
    var creditsText by remember { mutableStateOf(input.existingMonthlyCredits.toString()) }
    var apportText by remember { mutableStateOf(input.downPayment.toString()) }
    var rateText by remember { mutableStateOf(input.interestRatePercent.toString()) }
    var insuranceText by remember { mutableStateOf(input.insuranceRatePercent.toString()) }

    LaunchedEffect(input.monthlyNetIncome) {
        if (incomeText.toLongOrNull() != input.monthlyNetIncome) incomeText = input.monthlyNetIncome.toString()
    }
    LaunchedEffect(input.existingMonthlyCredits) {
        if (creditsText.toLongOrNull() != input.existingMonthlyCredits) creditsText = input.existingMonthlyCredits.toString()
    }
    LaunchedEffect(input.downPayment) {
        if (apportText.toLongOrNull() != input.downPayment) apportText = input.downPayment.toString()
    }
    LaunchedEffect(input.interestRatePercent) {
        if (rateText.replace(',', '.').toDoubleOrNull() != input.interestRatePercent) {
            rateText = input.interestRatePercent.toString()
        }
    }
    LaunchedEffect(input.insuranceRatePercent) {
        if (insuranceText.replace(',', '.').toDoubleOrNull() != input.insuranceRatePercent) {
            insuranceText = input.insuranceRatePercent.toString()
        }
    }

    val eligibleProperties = remember(properties, result.maxPropertyPriceNet) {
        properties.filter { it.price <= result.maxPropertyPriceNet }.sortedByDescending { it.price }
    }
    val hcsfCompliant = result.isHcsfRatioCompliant && result.isHcsfDurationCompliant

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("hcsf_capacity_screen"),
        containerColor = IvoryWarm,
        topBar = {
            Surface(
                color = EmeraldDeep,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("hcsf_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Retour",
                                tint = GoldChampagne
                            )
                        }
                        Column {
                            Text(
                                text = "Simulation Bancaire HCSF",
                                style = MaterialTheme.typography.titleLarge,
                                color = IvoryWarm,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Capacité d'emprunt • Endettement 35% • Apport & Durée",
                                style = MaterialTheme.typography.labelSmall,
                                color = GoldChampagne
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                val decision = AppSecurityManager.rateLimiter.checkAndConsume(RateLimitChannel.SHARE_EXPORT)
                                if (decision is RateLimitDecision.Blocked) {
                                    onShowToast(decision.reason)
                                    return@IconButton
                                }
                                val summary = buildString {
                                    appendLine("MON AGENT IMMO — Simulation Bancaire HCSF")
                                    activeClient?.let { appendLine("Dossier : ${it.name}") }
                                    appendLine("Revenus nets mensuels : ${MontyMarketData.formatEuro(input.monthlyNetIncome)}/m")
                                    appendLine("Crédits en cours : ${MontyMarketData.formatEuro(input.existingMonthlyCredits)}/m")
                                    appendLine("Plafond HCSF retenu : ${input.maxDebtRatioPercent}% (effectif ${result.effectiveDebtRatioPercent}%)")
                                    appendLine("Durée : ${input.durationYears} ans • Taux ${input.interestRatePercent}% + Ass. ${input.insuranceRatePercent}%")
                                    appendLine("Apport personnel : ${MontyMarketData.formatEuro(input.downPayment)}")
                                    appendLine("Mensualité max prêt : ${MontyMarketData.formatEuro(result.maxMonthlyLoanPayment)}/m")
                                    appendLine("Capacité d'emprunt nette : ${MontyMarketData.formatEuro(result.maxBorrowedCapital)}")
                                    appendLine("Prix bien max (hors notaire) : ${MontyMarketData.formatEuro(result.maxPropertyPriceNet)}")
                                }
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "Attestation Simulation HCSF — Mon Agent Immo")
                                    putExtra(Intent.EXTRA_TEXT, summary)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Partager la simulation HCSF"))
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("hcsf_share_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Partager simulation HCSF",
                                tint = GoldChampagne
                            )
                        }

                        if (activeClient != null) {
                            OutlinedButton(
                                onClick = onResetFromClient,
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = GoldChampagne),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .padding(end = 6.dp)
                                    .testTag("hcsf_reset_client_button")
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "Synchroniser dossier", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Dossier", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp)
                    .navigationBarsPadding(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. HERO CAPACITÉ HCSF
                item {
                    HcsfHeroResultCard(
                        input = input,
                        result = result,
                        hcsfCompliant = hcsfCompliant
                    )
                }

                // 2. BANDEAU DOSSIER ACTIF
                if (activeClient != null) {
                    item {
                        Surface(
                            color = Color.White,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(EmeraldDeep),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.AccountBalance, contentDescription = null, tint = GoldChampagne, modifier = Modifier.size(19.dp))
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Dossier : ${activeClient.name}",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = EmeraldDeep,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = activeClient.bankApproval.ifBlank { "Budget dossier : ${MontyMarketData.formatEuro(activeClient.budgetMax)}" },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = SlateMuted
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. PARAMÈTRES HCSF (REVENUS, CRÉDITS, APPORT, TAUX D'ENDETTEMENT, DURÉE)
                item {
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
                            Text(
                                text = "Paramètres Bancaires & Normes HCSF",
                                style = MaterialTheme.typography.titleLarge,
                                color = EmeraldDeep
                            )

                            // Revenus nets mensuels
                            SliderFieldHeader(
                                label = "Revenus nets mensuels du foyer",
                                valueLabel = "${MontyMarketData.formatEuro(input.monthlyNetIncome)} / mois"
                            )
                            Slider(
                                value = input.monthlyNetIncome.toFloat().coerceIn(1500f, 25000f),
                                onValueChange = { raw ->
                                    val snapped = ((raw / 100f).roundToLong() * 100L).coerceAtLeast(0L)
                                    onUpdateInput { it.copy(monthlyNetIncome = snapped) }
                                },
                                valueRange = 1500f..25000f,
                                colors = SliderDefaults.colors(
                                    thumbColor = EmeraldDeep,
                                    activeTrackColor = GoldAntique
                                )
                            )
                            OutlinedTextField(
                                value = incomeText,
                                onValueChange = { raw ->
                                    incomeText = raw
                                    raw.toLongOrNull()?.let { v ->
                                        onUpdateInput { it.copy(monthlyNetIncome = v.coerceIn(0L, 1_000_000L)) }
                                    }
                                },
                                label = { Text("Revenus mensuels (€)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("hcsf_income_input")
                            )

                            // Crédits en cours
                            SliderFieldHeader(
                                label = "Crédits & charges mensuels en cours",
                                valueLabel = "${MontyMarketData.formatEuro(input.existingMonthlyCredits)} / mois"
                            )
                            Slider(
                                value = input.existingMonthlyCredits.toFloat().coerceIn(0f, 4000f),
                                onValueChange = { raw ->
                                    val snapped = ((raw / 50f).roundToLong() * 50L).coerceAtLeast(0L)
                                    onUpdateInput { it.copy(existingMonthlyCredits = snapped) }
                                },
                                valueRange = 0f..4000f,
                                colors = SliderDefaults.colors(
                                    thumbColor = EmeraldDeep,
                                    activeTrackColor = GoldAntique
                                )
                            )
                            OutlinedTextField(
                                value = creditsText,
                                onValueChange = { raw ->
                                    creditsText = raw
                                    if (raw.isBlank()) {
                                        onUpdateInput { it.copy(existingMonthlyCredits = 0L) }
                                    } else {
                                        raw.toLongOrNull()?.let { v ->
                                            onUpdateInput { it.copy(existingMonthlyCredits = v.coerceIn(0L, 500_000L)) }
                                        }
                                    }
                                },
                                label = { Text("Crédits en cours (€/mois)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("hcsf_credits_input")
                            )

                            HorizontalDivider(color = BorderSubtle)

                            // Apport personnel
                            SliderFieldHeader(
                                label = "Apport personnel mobilisable",
                                valueLabel = MontyMarketData.formatEuro(input.downPayment)
                            )
                            Slider(
                                value = input.downPayment.toFloat().coerceIn(0f, 400000f),
                                onValueChange = { raw ->
                                    val snapped = ((raw / 2500f).roundToLong() * 2500L).coerceAtLeast(0L)
                                    onUpdateInput { it.copy(downPayment = snapped) }
                                },
                                valueRange = 0f..400000f,
                                colors = SliderDefaults.colors(
                                    thumbColor = EmeraldDeep,
                                    activeTrackColor = GoldAntique
                                )
                            )
                            OutlinedTextField(
                                value = apportText,
                                onValueChange = { raw ->
                                    apportText = raw
                                    if (raw.isBlank()) {
                                        onUpdateInput { it.copy(downPayment = 0L) }
                                    } else {
                                        raw.toLongOrNull()?.let { v ->
                                            onUpdateInput { it.copy(downPayment = v.coerceIn(0L, 20_000_000L)) }
                                        }
                                    }
                                },
                                label = { Text("Apport personnel (€)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("hcsf_apport_input")
                            )

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(20000L, 50000L, 100000L, 150000L, 200000L).forEach { preset ->
                                    FilterChip(
                                        selected = input.downPayment == preset,
                                        onClick = { onUpdateInput { it.copy(downPayment = preset) } },
                                        label = { Text("${preset / 1000} k€") },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = EmeraldDeep,
                                            selectedLabelColor = GoldChampagne
                                        ),
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    )
                                }
                            }

                            HorizontalDivider(color = BorderSubtle)

                            // Taux d'endettement HCSF
                            Text(
                                text = "Plafond de taux d'endettement (Norme HCSF 35%)",
                                style = MaterialTheme.typography.labelLarge,
                                color = EmeraldDeep
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(
                                    30.0 to "30% Prudent",
                                    33.0 to "33% Bancaire",
                                    35.0 to "35% HCSF Max",
                                    37.0 to "37% Dérogation"
                                ).forEach { (ratio, label) ->
                                    FilterChip(
                                        selected = input.maxDebtRatioPercent == ratio,
                                        onClick = { onUpdateInput { it.copy(maxDebtRatioPercent = ratio) } },
                                        label = { Text(label) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = if (ratio <= 35.0) EmeraldDeep else WarningAmber,
                                            selectedLabelColor = Color.White
                                        ),
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("hcsf_dti_${ratio.toInt()}")
                                    )
                                }
                            }

                            AnimatedVisibility(visible = !result.isHcsfRatioCompliant) {
                                Surface(
                                    color = WarningContainer,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Warning, contentDescription = null, tint = WarningAmber, modifier = Modifier.size(18.dp))
                                        Text(
                                            text = "Au-delà de 35% : hors plafond strict HCSF (réservé au quota dérogatoire de 20% des banques pour résidence principale).",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = ObsidianInk
                                        )
                                    }
                                }
                            }

                            // Durée du prêt
                            Text(
                                text = "Durée d'emprunt (25 ans max HCSF, 27 ans VEFA)",
                                style = MaterialTheme.typography.labelLarge,
                                color = EmeraldDeep
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(10, 15, 20, 25, 27).forEach { y ->
                                    FilterChip(
                                        selected = input.durationYears == y,
                                        onClick = {
                                            onUpdateInput {
                                                it.copy(
                                                    durationYears = y,
                                                    isVefaNeuf = if (y == 27) true else it.isVefaNeuf,
                                                    notaryRatePercent = if (y == 27) 2.8 else it.notaryRatePercent
                                                )
                                            }
                                        },
                                        label = { Text(if (y == 27) "27 ans VEFA" else "$y ans") },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = EmeraldDeep,
                                            selectedLabelColor = GoldChampagne
                                        ),
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("hcsf_duration_$y")
                                    )
                                }
                            }

                            // Type d'achat (Notaire Ancien vs Neuf VEFA) & Taux
                            Text(
                                text = "Nature du bien & Conditions de crédit",
                                style = MaterialTheme.typography.labelLarge,
                                color = EmeraldDeep
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = !input.isVefaNeuf,
                                    onClick = {
                                        onUpdateInput {
                                            it.copy(
                                                isVefaNeuf = false,
                                                notaryRatePercent = 7.8,
                                                durationYears = if (it.durationYears > 25) 25 else it.durationYears
                                            )
                                        }
                                    },
                                    label = { Text("Ancien (~7,8% notaire)") },
                                    modifier = Modifier
                                        .weight(1f)
                                        .minimumInteractiveComponentSize()
                                        .testTag("hcsf_type_ancien")
                                )
                                FilterChip(
                                    selected = input.isVefaNeuf,
                                    onClick = {
                                        onUpdateInput { it.copy(isVefaNeuf = true, notaryRatePercent = 2.8) }
                                    },
                                    label = { Text("Neuf / VEFA (~2,8%)") },
                                    modifier = Modifier
                                        .weight(1f)
                                        .minimumInteractiveComponentSize()
                                        .testTag("hcsf_type_neuf")
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedTextField(
                                    value = rateText,
                                    onValueChange = { raw ->
                                        rateText = raw
                                        raw.replace(',', '.').toDoubleOrNull()?.let { r ->
                                            onUpdateInput { it.copy(interestRatePercent = r.coerceIn(0.0, 15.0)) }
                                        }
                                    },
                                    label = { Text("Taux crédit (%)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("hcsf_rate_input")
                                )
                                OutlinedTextField(
                                    value = insuranceText,
                                    onValueChange = { raw ->
                                        insuranceText = raw
                                        raw.replace(',', '.').toDoubleOrNull()?.let { ins ->
                                            onUpdateInput { it.copy(insuranceRatePercent = ins.coerceIn(0.0, 3.0)) }
                                        }
                                    },
                                    label = { Text("Assurance (%)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("hcsf_insurance_input")
                                )
                            }
                        }
                    }
                }

                // 4. DÉTAIL RÉGLEMENTAIRE HCSF & RESTE À VIVRE
                item {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, BorderSubtle, RoundedCornerShape(20.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Shield, contentDescription = null, tint = GoldAntique)
                                Text(
                                    text = "Audit HCSF & Reste à vivre",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = EmeraldDeep
                                )
                            }
                            DetailLine("Plafond d'endettement (${input.maxDebtRatioPercent}%)", "${MontyMarketData.formatEuro(result.maxMonthlyDebtTotal)} / mois")
                            DetailLine("Crédits déjà en cours déduits", "- ${MontyMarketData.formatEuro(input.existingMonthlyCredits)} / mois")
                            DetailLine("Mensualité prêt immo disponible (ass. incl.)", "${MontyMarketData.formatEuro(result.maxMonthlyLoanPayment)} / mois", highlight = true)
                            DetailLine("Reste à vivre mensuel foyer", "${MontyMarketData.formatEuro(result.remainderToLive)} / mois")
                            DetailLine("Frais de notaire est. (${input.notaryRatePercent}%)", MontyMarketData.formatEuro(result.estimatedNotaryFees))
                            DetailLine("Coût total intérêts + assurance", MontyMarketData.formatEuro(result.totalCreditCost))

                            Surface(
                                color = IvoryWarm,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Info, contentDescription = null, tint = EmeraldDeep, modifier = Modifier.size(16.dp))
                                    Text(
                                        text = "Règles HCSF obligatoires depuis 2022 : endettement max 35% assurance emprunteur comprise et durée max 25 ans (27 ans en VEFA).",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = SlateMuted
                                    )
                                }
                            }
                        }
                    }
                }

                // 5. COMPARATEUR RAPIDE DES DURÉES HCSF (15 / 20 / 25 ANS)
                item {
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
                                Icon(Icons.Default.Savings, contentDescription = null, tint = GoldAntique)
                                Text(
                                    text = "Comparatif Capacité selon la Durée",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = EmeraldDeep
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                result.durationScenarios.forEach { sc ->
                                    val selected = sc.durationYears == input.durationYears
                                    Surface(
                                        color = if (selected) EmeraldDeep else IvoryWarm,
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .border(
                                                1.5.dp,
                                                if (selected) GoldAntique else BorderSubtle,
                                                RoundedCornerShape(14.dp)
                                            )
                                            .clip(RoundedCornerShape(14.dp))
                                            .clickable {
                                                onUpdateInput { it.copy(durationYears = sc.durationYears) }
                                            }
                                            .minimumInteractiveComponentSize()
                                            .testTag("hcsf_scenario_card_${sc.durationYears}")
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(10.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = "${sc.durationYears} ans",
                                                style = MaterialTheme.typography.labelLarge,
                                                color = if (selected) GoldChampagne else EmeraldDeep,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "Bien max",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (selected) IvoryWarm.copy(alpha = 0.7f) else SlateMuted
                                            )
                                            Text(
                                                text = MontyMarketData.formatEuro(sc.maxPropertyPriceNet),
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Bold,
                                                color = if (selected) IvoryWarm else ObsidianInk
                                            )
                                            Text(
                                                text = "Prêt ${sc.maxBorrowedCapital / 1000} k€",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (selected) GoldChampagne else SlateMuted
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 6. BIENS ÉLIGIBLES + CTA SYNCHRONISATION
                item {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, GoldAntique.copy(alpha = 0.55f), RoundedCornerShape(20.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.HomeWork, contentDescription = null, tint = GoldAntique)
                                Text(
                                    text = "${eligibleProperties.size} bien(s) accessible(s) sur ${properties.size}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = EmeraldDeep
                                )
                            }
                            if (eligibleProperties.isEmpty()) {
                                Text(
                                    text = "Aucun bien du catalogue ne correspond encore à ce plafond net. Ajustez l'apport, la durée (25 ans) ou activez une recherche personnalisée.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SlateMuted
                                )
                            } else {
                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    eligibleProperties.forEach { prop ->
                                        Surface(
                                            color = IvoryWarm,
                                            shape = RoundedCornerShape(14.dp),
                                            modifier = Modifier
                                                .width(195.dp)
                                                .border(1.dp, BorderSubtle, RoundedCornerShape(14.dp))
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(12.dp),
                                                verticalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Text(
                                                    text = prop.sector.substringBefore(" ("),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = GoldAntique
                                                )
                                                Text(
                                                    text = prop.title,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    color = EmeraldDeep,
                                                    maxLines = 2
                                                )
                                                Text(
                                                    text = MontyMarketData.formatEuro(prop.price),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = SuccessGreen
                                                )
                                                Text(
                                                    text = "${prop.surface} m² • ${prop.bedrooms} ch. • DPE ${prop.dpe}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = SlateMuted
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Button(
                                onClick = onApplyToSimulator,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = EmeraldDeep,
                                    contentColor = GoldChampagne
                                ),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("hcsf_apply_button")
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Appliquer cette capacité au simulateur",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            if (activeClient != null) {
                                OutlinedButton(
                                    onClick = onSaveBudgetToDossier,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                        .testTag("hcsf_save_budget_button")
                                ) {
                                    Text(
                                        text = "Enregistrer comme budget max de mon dossier (${MontyMarketData.formatEuro(result.maxPropertyPriceNet)})",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = EmeraldDeep,
                                        fontWeight = FontWeight.SemiBold
                                    )
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
private fun HcsfHeroResultCard(
    input: HcsfCapacityInput,
    result: HcsfCapacityResult,
    hcsfCompliant: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(EmeraldDeep, EmeraldSurface)
                )
            )
            .border(1.dp, GoldAntique.copy(alpha = 0.55f), RoundedCornerShape(24.dp))
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "CAPACITÉ D'ACHAT HCSF",
                    style = MaterialTheme.typography.labelSmall,
                    color = GoldChampagne
                )
                Surface(
                    color = if (hcsfCompliant) SuccessContainer else ErrorContainer,
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (hcsfCompliant) Icons.Default.Verified else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (hcsfCompliant) SuccessGreen else ErrorRed,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (hcsfCompliant) "Conforme HCSF (${result.effectiveDebtRatioPercent}%)"
                            else "Hors norme HCSF",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (hcsfCompliant) SuccessGreen else ErrorRed
                        )
                    }
                }
            }

            Column {
                Text(
                    text = "Prix du bien achetable (hors notaire)",
                    style = MaterialTheme.typography.bodySmall,
                    color = IvoryWarm.copy(alpha = 0.78f)
                )
                Text(
                    text = MontyMarketData.formatEuro(result.maxPropertyPriceNet),
                    style = MaterialTheme.typography.headlineLarge,
                    color = GoldChampagne,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.testTag("hcsf_max_property_price_text")
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                HeroStatCol("Capacité d'emprunt", MontyMarketData.formatEuro(result.maxBorrowedCapital))
                HeroStatCol("Enveloppe totale", MontyMarketData.formatEuro(result.maxTotalProjectBudget))
                HeroStatCol("Mensualité max", "${MontyMarketData.formatEuro(result.maxMonthlyLoanPayment)}/m")
            }

            HorizontalDivider(color = IvoryWarm.copy(alpha = 0.16f))

            // Jauge visuelle Canvas : Emprunt vs Apport vs Notaire
            Text(
                text = "Décomposition de votre enveloppe (${input.durationYears} ans à ${input.interestRatePercent}%)",
                style = MaterialTheme.typography.labelSmall,
                color = IvoryWarm.copy(alpha = 0.8f)
            )
            EnvelopeBreakdownBar(
                borrowed = result.maxBorrowedCapital,
                downPayment = input.downPayment,
                notaryFees = result.estimatedNotaryFees
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                LegendChip(color = GoldChampagne, label = "Prêt ${result.maxBorrowedCapital / 1000} k€")
                LegendChip(color = Color(0xFF34D399), label = "Apport ${input.downPayment / 1000} k€")
                LegendChip(color = Color(0xFF94A3B8), label = "Notaire ~${result.estimatedNotaryFees / 1000} k€")
            }
        }
    }
}

@Composable
private fun EnvelopeBreakdownBar(
    borrowed: Long,
    downPayment: Long,
    notaryFees: Long
) {
    val total = (borrowed + downPayment).coerceAtLeast(1L).toFloat()
    val netRatio = ((borrowed + downPayment - notaryFees).coerceAtLeast(0L) / total).coerceIn(0f, 1f)
    val borrowedRatio = (borrowed / total).coerceIn(0f, netRatio)
    val apportRatio = (netRatio - borrowedRatio).coerceAtLeast(0f)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(14.dp)
            .clip(RoundedCornerShape(8.dp))
    ) {
        val w = size.width
        val h = size.height
        val borrowedW = w * borrowedRatio
        val apportW = w * apportRatio
        val notaryW = (w - borrowedW - apportW).coerceAtLeast(0f)

        drawRoundRect(
            color = EmeraldMid,
            size = Size(w, h),
            cornerRadius = CornerRadius(12f, 12f)
        )
        if (borrowedW > 0f) {
            drawRect(
                color = GoldChampagne,
                topLeft = Offset.Zero,
                size = Size(borrowedW, h)
            )
        }
        if (apportW > 0f) {
            drawRect(
                color = Color(0xFF34D399),
                topLeft = Offset(borrowedW, 0f),
                size = Size(apportW, h)
            )
        }
        if (notaryW > 0f) {
            drawRect(
                color = Color(0xFF94A3B8),
                topLeft = Offset(borrowedW + apportW, 0f),
                size = Size(notaryW, h)
            )
        }
    }
}

@Composable
private fun LegendChip(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = IvoryWarm
        )
    }
}

@Composable
private fun HeroStatCol(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = IvoryWarm.copy(alpha = 0.68f))
        Text(value, style = MaterialTheme.typography.titleSmall, color = IvoryWarm, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SliderFieldHeader(label: String, valueLabel: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.labelLarge, color = EmeraldDeep)
        Text(text = valueLabel, style = MaterialTheme.typography.labelLarge, color = GoldAntique, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DetailLine(label: String, value: String, highlight: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = if (highlight) EmeraldDeep else SlateMuted,
            fontWeight = if (highlight) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = if (highlight) SuccessGreen else ObsidianInk,
            fontWeight = if (highlight) FontWeight.Bold else FontWeight.SemiBold
        )
    }
}
