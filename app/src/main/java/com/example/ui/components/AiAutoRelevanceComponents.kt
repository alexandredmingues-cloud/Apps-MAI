package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TipsAndUpdates
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.AdvisorProactiveRecommendation
import com.example.data.model.CriteriaOptimizationSuggestion
import com.example.data.model.PropertyAiInsight
import com.example.ui.AutoAiUiState
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
fun BuyerAutoAiBanner(
    autoAiEnabled: Boolean,
    autoAiState: AutoAiUiState,
    onToggleAutoAi: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onApplyOptimization: (CriteriaOptimizationSuggestion) -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = EmeraldDeep),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, GoldAntique.copy(alpha = 0.6f), RoundedCornerShape(18.dp))
            .testTag("buyer_auto_ai_banner")
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "Amélioration IA",
                        tint = GoldChampagne,
                        modifier = Modifier.size(18.dp)
                    )
                    Column {
                        Text(
                            text = "AMÉLIORATION AUTO IA",
                            style = MaterialTheme.typography.labelSmall,
                            color = GoldChampagne
                        )
                        Text(
                            text = if (autoAiEnabled) "Tri & conseils classés selon pertinence" else "Mode manuel (critères bruts)",
                            style = MaterialTheme.typography.bodySmall,
                            color = IvoryWarm.copy(alpha = 0.8f)
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (autoAiEnabled) {
                        IconButton(
                            onClick = onRefresh,
                            enabled = autoAiState !is AutoAiUiState.Analyzing,
                            modifier = Modifier
                                .size(40.dp)
                                .testTag("refresh_auto_ai_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Actualiser l'analyse IA",
                                tint = GoldChampagne,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Switch(
                        checked = autoAiEnabled,
                        onCheckedChange = onToggleAutoAi,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = EmeraldDeep,
                            checkedTrackColor = GoldChampagne,
                            uncheckedThumbColor = IvoryWarm,
                            uncheckedTrackColor = EmeraldMid
                        ),
                        modifier = Modifier.testTag("auto_ai_toggle")
                    )
                }
            }

            AnimatedVisibility(visible = autoAiEnabled) {
                when (autoAiState) {
                    is AutoAiUiState.Analyzing -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(EmeraldMid, RoundedCornerShape(12.dp))
                                .padding(10.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = GoldChampagne,
                                strokeWidth = 2.dp
                            )
                            Text(
                                text = "Analyse automatique des biens selon votre dossier en cours...",
                                style = MaterialTheme.typography.bodySmall,
                                color = IvoryWarm
                            )
                        }
                    }

                    is AutoAiUiState.Ready -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(
                                color = EmeraldMid,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = autoAiState.result.buyerHeadline,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = IvoryWarm,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }

                            val opt = autoAiState.result.criteriaOptimization
                            if (opt != null && opt.shouldSuggestOptimization && opt.title.isNotBlank()) {
                                Surface(
                                    color = IvoryWarm,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.dp, GoldAntique, RoundedCornerShape(14.dp))
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.TipsAndUpdates,
                                                contentDescription = null,
                                                tint = GoldAntique,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = "Optimisation suggérée : ${opt.title}",
                                                style = MaterialTheme.typography.labelLarge,
                                                color = EmeraldDeep
                                            )
                                        }
                                        Text(
                                            text = opt.rationale,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = ObsidianInk
                                        )
                                        Button(
                                            onClick = { onApplyOptimization(opt) },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = EmeraldDeep,
                                                contentColor = GoldChampagne
                                            ),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("apply_ai_optimization_button")
                                        ) {
                                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(15.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Appliquer l'amélioration", style = MaterialTheme.typography.labelLarge)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    is AutoAiUiState.Error -> {
                        Surface(
                            color = ErrorContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = autoAiState.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ErrorRed,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = onRefresh) {
                                    Text("Réessayer", color = ErrorRed)
                                }
                            }
                        }
                    }

                    AutoAiUiState.Idle -> Unit
                }
            }
        }
    }
}

@Composable
fun AiRelevanceInsightStrip(insight: PropertyAiInsight) {
    Surface(
        color = EmeraldDeep.copy(alpha = 0.06f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, GoldAntique.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = GoldAntique,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "${insight.badgeLabel} • Pertinence IA ${insight.aiRelevanceScore}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = EmeraldDeep,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (insight.suggestedDiscountPercent > 0) {
                    Surface(
                        color = SuccessContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "Négo est. -${insight.suggestedDiscountPercent}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = SuccessGreen,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            if (insight.whyRelevant.isNotBlank()) {
                Text(
                    text = insight.whyRelevant,
                    style = MaterialTheme.typography.bodySmall,
                    color = ObsidianInk
                )
            }
        }
    }
}

@Composable
fun AdvisorProactiveAiCard(
    autoAiState: AutoAiUiState,
    onRefresh: () -> Unit,
    onActionProperty: (AdvisorProactiveRecommendation) -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.5.dp, GoldAntique.copy(alpha = 0.65f), RoundedCornerShape(20.dp))
            .testTag("advisor_proactive_ai_card")
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = GoldAntique)
                    Text(
                        text = "Copilote Proactif IA",
                        style = MaterialTheme.typography.titleMedium,
                        color = EmeraldDeep
                    )
                }
                IconButton(
                    onClick = onRefresh,
                    enabled = autoAiState !is AutoAiUiState.Analyzing,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Actualiser", tint = EmeraldDeep, modifier = Modifier.size(18.dp))
                }
            }

            when (autoAiState) {
                is AutoAiUiState.Analyzing -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = EmeraldDeep, strokeWidth = 2.dp)
                        Text(
                            text = "Évaluation automatique de la prochaine meilleure action...",
                            style = MaterialTheme.typography.bodySmall,
                            color = SlateMuted
                        )
                    }
                }

                is AutoAiUiState.Ready -> {
                    val rec = autoAiState.result.advisorRecommendation
                    if (rec != null && rec.headline.isNotBlank()) {
                        val (tagBg, tagFg) = when {
                            rec.priorityTag.contains("Urgent", ignoreCase = true) -> WarningContainer to WarningAmber
                            rec.priorityTag.contains("Opportunit", ignoreCase = true) -> SuccessContainer to SuccessGreen
                            else -> IvoryWarm to EmeraldDeep
                        }
                        Surface(color = tagBg, shape = RoundedCornerShape(8.dp)) {
                            Text(
                                text = rec.priorityTag.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = tagFg,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                        Text(
                            text = rec.headline,
                            style = MaterialTheme.typography.titleMedium,
                            color = EmeraldDeep
                        )
                        Text(
                            text = rec.rationale,
                            style = MaterialTheme.typography.bodySmall,
                            color = ObsidianInk.copy(alpha = 0.85f)
                        )
                        OutlinedButton(
                            onClick = { onActionProperty(rec) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = GoldAntique, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Ouvrir la stratégie IA sur ce dossier", style = MaterialTheme.typography.labelLarge)
                        }
                    } else {
                        Text(
                            text = autoAiState.result.buyerHeadline,
                            style = MaterialTheme.typography.bodySmall,
                            color = SlateMuted
                        )
                    }
                }

                is AutoAiUiState.Error -> {
                    Text(
                        text = autoAiState.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = ErrorRed
                    )
                }

                AutoAiUiState.Idle -> {
                    Text(
                        text = "Activez l'analyse automatique ou actualisez pour obtenir la recommandation prioritaire.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SlateMuted
                    )
                }
            }
        }
    }
}
