package com.example.ui.advisor

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.AdvisorAiAction
import com.example.data.MontyMarketData
import com.example.data.model.ClientDossier
import com.example.data.model.FinanceMetrics
import com.example.data.model.HcsfCapacityResult
import com.example.data.model.PropertyItem
import com.example.data.model.VisitRecord
import com.example.ui.AiGenState
import com.example.ui.SimulatorInput
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
fun AdvisorFinanceTab(
    simulatorInput: SimulatorInput,
    financeMetrics: FinanceMetrics,
    hcsfCapacityResult: HcsfCapacityResult,
    onUpdateSimulator: ((SimulatorInput) -> SimulatorInput) -> Unit,
    onOpenCapacityScreen: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = EmeraldDeep),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, GoldAntique.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.AccountBalance, contentDescription = null, tint = GoldChampagne)
                    Text(
                        text = "Capacité d'emprunt HCSF 35%",
                        style = MaterialTheme.typography.titleMedium,
                        color = IvoryWarm,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "Enveloppe max estimée : ${MontyMarketData.formatEuro(hcsfCapacityResult.maxPropertyPriceNet)} net (prêt ${MontyMarketData.formatEuro(hcsfCapacityResult.maxBorrowedCapital)}).",
                    style = MaterialTheme.typography.bodySmall,
                    color = GoldChampagne
                )
                Button(
                    onClick = onOpenCapacityScreen,
                    colors = ButtonDefaults.buttonColors(containerColor = GoldAntique, contentColor = ObsidianInk),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("open_hcsf_capacity_button")
                ) {
                    Icon(Icons.Default.AccountBalance, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Ouvrir l'écran Simulation Bancaire HCSF",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderSubtle, RoundedCornerShape(20.dp))
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Calculate, contentDescription = null, tint = GoldAntique)
                    Text("Simulateur Bancaire HCSF 35%", style = MaterialTheme.typography.titleLarge, color = EmeraldDeep)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = simulatorInput.price.toString(),
                        onValueChange = { v -> v.toLongOrNull()?.let { p -> onUpdateSimulator { it.copy(price = p) } } },
                        label = { Text("Prix bien (€)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = simulatorInput.downPayment.toString(),
                        onValueChange = { v -> v.toLongOrNull()?.let { d -> onUpdateSimulator { it.copy(downPayment = d) } } },
                        label = { Text("Apport (€)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = simulatorInput.notaryRatePercent.toString(),
                        onValueChange = { v -> v.replace(',', '.').toDoubleOrNull()?.let { n -> onUpdateSimulator { it.copy(notaryRatePercent = n) } } },
                        label = { Text("Notaire (%)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = simulatorInput.durationYears.toString(),
                        onValueChange = { v -> v.toIntOrNull()?.coerceIn(5, 30)?.let { y -> onUpdateSimulator { it.copy(durationYears = y) } } },
                        label = { Text("Durée (ans)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = simulatorInput.interestRatePercent.toString(),
                        onValueChange = { v -> v.replace(',', '.').toDoubleOrNull()?.let { r -> onUpdateSimulator { it.copy(interestRatePercent = r) } } },
                        label = { Text("Taux (%)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = simulatorInput.insuranceRatePercent.toString(),
                        onValueChange = { v -> v.replace(',', '.').toDoubleOrNull()?.let { i -> onUpdateSimulator { it.copy(insuranceRatePercent = i) } } },
                        label = { Text("Assurance (%)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = simulatorInput.monthlyIncome.toString(),
                        onValueChange = { v -> v.toLongOrNull()?.let { m -> onUpdateSimulator { it.copy(monthlyIncome = m) } } },
                        label = { Text("Revenus (€/m)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = simulatorInput.existingCredits.toString(),
                        onValueChange = { v -> v.toLongOrNull()?.let { e -> onUpdateSimulator { it.copy(existingCredits = e) } } },
                        label = { Text("Crédits en cours (€/m)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = simulatorInput.monthlyCharges.toString(),
                        onValueChange = { v -> v.toLongOrNull()?.let { c -> onUpdateSimulator { it.copy(monthlyCharges = c) } } },
                        label = { Text("Charges + TF (€/m)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Résultats & Jauge HCSF
        val dtiOk = financeMetrics.debtRatioPercent <= 35.0
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = EmeraldDeep),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("SYNTHÈSE BANCAIRE", style = MaterialTheme.typography.labelSmall, color = GoldChampagne)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column {
                        Text("Mensualité globale (ass. incluse)", style = MaterialTheme.typography.bodySmall, color = IvoryWarm.copy(alpha = 0.7f))
                        Text(
                            "${MontyMarketData.formatEuro(financeMetrics.monthlyInclInsurance)} / mois",
                            style = MaterialTheme.typography.headlineMedium,
                            color = GoldChampagne,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Surface(
                        color = if (dtiOk) SuccessContainer else ErrorContainer,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "${financeMetrics.debtRatioPercent}% HCSF",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (dtiOk) SuccessGreen else ErrorRed,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
                LinearProgressIndicator(
                    progress = { (financeMetrics.debtRatioPercent / 50.0).toFloat().coerceIn(0f, 1f) },
                    color = if (dtiOk) Color(0xFF34D399) else Color(0xFFF87171),
                    trackColor = EmeraldMid,
                    modifier = Modifier.fillMaxWidth().height(8.dp)
                )
                HorizontalDivider(color = IvoryWarm.copy(alpha = 0.14f))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    MetricCol("Frais de notaire", MontyMarketData.formatEuro(financeMetrics.notaryFees))
                    MetricCol("Coût total projet", MontyMarketData.formatEuro(financeMetrics.totalProjectCost))
                    MetricCol("Montant emprunté", MontyMarketData.formatEuro(financeMetrics.borrowedAmount))
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    MetricCol("Coût total crédit", MontyMarketData.formatEuro(financeMetrics.totalCreditCost))
                    MetricCol("Reste à vivre", MontyMarketData.formatEuro(financeMetrics.remainderToLive))
                    MetricCol("Coût habitat réel", "${MontyMarketData.formatEuro(financeMetrics.realMonthlyHousingCost)}/m")
                }

                Surface(color = EmeraldMid, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "20 ans : ${MontyMarketData.formatEuro(financeMetrics.scenario20Years)}/m",
                            style = MaterialTheme.typography.labelLarge,
                            color = IvoryWarm
                        )
                        Text(
                            "25 ans : ${MontyMarketData.formatEuro(financeMetrics.scenario25Years)}/m",
                            style = MaterialTheme.typography.labelLarge,
                            color = GoldChampagne
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricCol(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = IvoryWarm.copy(alpha = 0.65f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = IvoryWarm, fontWeight = FontWeight.SemiBold)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdvisorAiTab(
    activeClient: ClientDossier?,
    properties: List<PropertyItem>,
    visits: List<VisitRecord>,
    aiState: AiGenState,
    onGenerate: (
        action: AdvisorAiAction,
        client: ClientDossier,
        property: PropertyItem,
        customInstruction: String
    ) -> Unit,
    onShowToast: (String) -> Unit
) {
    var selectedAction by remember { mutableStateOf(AdvisorAiAction.DOSSIER_SYNTHESIS) }
    var selectedPropertyId by remember(properties) { mutableStateOf(properties.firstOrNull()?.id.orEmpty()) }
    var customInstruction by remember { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current

    val selectedProperty = properties.firstOrNull { it.id == selectedPropertyId } ?: properties.firstOrNull()

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.5.dp, GoldAntique.copy(alpha = 0.55f), RoundedCornerShape(20.dp))
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = GoldAntique)
                    Text("Mon Agent Immo Copilot", style = MaterialTheme.typography.titleLarge, color = EmeraldDeep)
                }
                Text(
                    "Génération stratégique propulsée par Gemini (Firebase AI Logic) à partir du dossier actif, du bien cible et de l'observatoire Montpellier.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SlateMuted
                )

                Text("Action IA", style = MaterialTheme.typography.labelLarge, color = EmeraldDeep)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    AdvisorAiAction.entries.forEach { act ->
                        FilterChip(
                            selected = selectedAction == act,
                            onClick = { selectedAction = act },
                            label = { Text(act.label, style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = EmeraldDeep,
                                selectedLabelColor = GoldChampagne
                            )
                        )
                    }
                }

                Text("Bien de référence", style = MaterialTheme.typography.labelLarge, color = EmeraldDeep)
                properties.take(6).forEach { prop ->
                    val selected = prop.id == selectedProperty?.id
                    Surface(
                        color = if (selected) EmeraldDeep else IvoryWarm,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedPropertyId = prop.id }
                    ) {
                        Text(
                            text = "${prop.title} • ${MontyMarketData.formatEuro(prop.price)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (selected) GoldChampagne else ObsidianInk,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                OutlinedTextField(
                    value = customInstruction,
                    onValueChange = { customInstruction = it },
                    label = { Text("Consigne complémentaire (optionnel)") },
                    placeholder = { Text("Ex : Insister sur l'offre à 415k€ et la proximité Tram L2") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = {
                        if (activeClient != null && selectedProperty != null) {
                            onGenerate(selectedAction, activeClient, selectedProperty, customInstruction)
                        }
                    },
                    enabled = activeClient != null && selectedProperty != null &&
                        aiState !is AiGenState.Loading && aiState !is AiGenState.Streaming,
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldDeep, contentColor = GoldChampagne),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().height(50.dp).testTag("run_advisor_ai_button")
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Générer l'analyse Gemini", style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        when (aiState) {
            is AiGenState.Loading -> {
                Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), color = EmeraldDeep, strokeWidth = 2.5.dp)
                        Text("Synthèse patrimoniale en cours...", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            is AiGenState.Streaming, is AiGenState.Success -> {
                val outputText = when (aiState) {
                    is AiGenState.Streaming -> aiState.text
                    is AiGenState.Success -> aiState.text
                }
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, GoldAntique, RoundedCornerShape(20.dp))
                ) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (aiState is AiGenState.Streaming) "Génération en direct..." else "Analyse prête",
                                style = MaterialTheme.typography.labelLarge,
                                color = GoldAntique
                            )
                            OutlinedButton(onClick = {
                                clipboard.setText(AnnotatedString(outputText))
                                onShowToast("Texte copié dans le presse-papiers.")
                            }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copier", modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Copier", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        HorizontalDivider(color = BorderSubtle)
                        Text(outputText, style = MaterialTheme.typography.bodyMedium, color = ObsidianInk)
                    }
                }
            }
            is AiGenState.Error -> {
                Surface(color = ErrorContainer, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(aiState.message, color = ErrorRed, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(14.dp))
                }
            }
            is AiGenState.Idle -> Unit
        }
    }
}

@Composable
fun AdvisorMarketTab() {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = EmeraldDeep),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.AutoMirrored.Filled.TrendingUp, contentDescription = null, tint = GoldChampagne)
                    Text("Observatoire Montpellier & Métropole", style = MaterialTheme.typography.titleLarge, color = IvoryWarm)
                }
                Text(
                    "Référentiel prix/m², tension locative/résidentielle et performance DPE moyenne sur 9 secteurs clés.",
                    style = MaterialTheme.typography.bodySmall,
                    color = GoldChampagne
                )
            }
        }

        MontyMarketData.SECTEURS_MONTPELLIER.forEach { sec ->
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderSubtle, RoundedCornerShape(18.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(sec.name, style = MaterialTheme.typography.titleMedium, color = EmeraldDeep)
                        Surface(color = SuccessContainer, shape = RoundedCornerShape(16.dp)) {
                            Text(
                                sec.evolution,
                                style = MaterialTheme.typography.labelSmall,
                                color = SuccessGreen,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Appart : ${sec.priceM2Apartment} €/m²", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "Maison : ${sec.priceM2House?.let { "$it €/m²" } ?: "N/A"}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SlateMuted
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(color = WarningContainer, shape = RoundedCornerShape(8.dp)) {
                            Text("Tension : ${sec.tension}", style = MaterialTheme.typography.labelSmall, color = WarningAmber, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                        }
                        Surface(color = IvoryWarm, shape = RoundedCornerShape(8.dp)) {
                            Text("DPE moyen : ${sec.avgDpe}", style = MaterialTheme.typography.labelSmall, color = EmeraldDeep, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                        }
                    }
                    Text(sec.description, style = MaterialTheme.typography.bodySmall, color = SlateMuted)
                }
            }
        }
    }
}
