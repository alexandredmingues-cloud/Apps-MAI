package com.example.ui.buyer

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.MontyMarketData
import com.example.data.SellerDocumentVault
import com.example.data.model.ClientDossier
import com.example.data.model.PropertyItem
import com.example.data.model.SellerDocumentCatalog
import com.example.data.model.SellerPropertyDossier
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.EmeraldDeep
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
fun BuyerPropertyDocsSectionCard(
    property: PropertyItem,
    dossier: SellerPropertyDossier?,
    client: ClientDossier,
    onOpenSummary: () -> Unit,
    onOpenCheckoutOrPack: () -> Unit
) {
    val applicableCount = dossier?.documents?.count { it.status != "Non applicable" } ?: 0
    val providedCount = dossier?.documents?.count { it.status != "Non applicable" && it.isProvided } ?: 0
    val isUnlocked = dossier?.isUnlockedForBuyer(client.id) == true
    val isPending = dossier?.hasPendingOrderForBuyer(client.id) == true
    val packPrice = dossier?.packPriceEuro ?: 29L

    Surface(
        color = IvoryWarm,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, GoldAntique.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "DOSSIER TECHNIQUE & LOI ALUR",
                        style = MaterialTheme.typography.labelSmall,
                        color = GoldAntique
                    )
                    Text(
                        text = if (dossier != null) {
                            "${dossier.propertyKind} • ${dossier.residenceType}"
                        } else {
                            "${property.type} • Dossier en préparation par le vendeur"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        color = EmeraldDeep,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (dossier != null) {
                    Surface(
                        color = if (isUnlocked) SuccessContainer else Color.White,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.border(1.dp, BorderSubtle, RoundedCornerShape(8.dp))
                    ) {
                        Text(
                            text = if (isUnlocked) "Pack débloqué" else "$providedCount/$applicableCount pièces",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isUnlocked) SuccessGreen else EmeraldDeep,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            if (dossier?.dvfValuation != null && dossier.dvfValuation.valuationScore > 0) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(color = EmeraldDeep, shape = RoundedCornerShape(8.dp)) {
                        Text(
                            text = "Indice DVF ${dossier.dvfValuation.valuationScore}/100 (${dossier.dvfValuation.count300m} ventes ≤300m)",
                            style = MaterialTheme.typography.labelSmall,
                            color = GoldChampagne,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            if (dossier == null) {
                Text(
                    text = "Les diagnostics DDT et pièces ALUR n'ont pas encore été publiés dans l'espace vendeur pour ce bien.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SlateMuted
                )
            } else {
                Text(
                    text = if (isUnlocked) {
                        "Vous avez accès à l'intégralité des documents fournis et au téléchargement ZIP."
                    } else if (isPending) {
                        "Règlement en attente de validation par le conseiller. Consultez le résumé gratuit en attendant."
                    } else {
                        "Consultez le résumé gratuit des diagnostics & charges ALUR ou réglez $packPrice € pour recevoir le dossier complet."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = ObsidianInk.copy(alpha = 0.85f)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onOpenSummary,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("buyer_free_summary_button")
                    ) {
                        Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text("Résumé gratuit", style = MaterialTheme.typography.labelSmall)
                    }
                    Button(
                        onClick = onOpenCheckoutOrPack,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isUnlocked) SuccessGreen else EmeraldDeep,
                            contentColor = if (isUnlocked) Color.White else GoldChampagne
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1.15f)
                            .testTag("buyer_pay_receive_docs_button")
                    ) {
                        Icon(
                            imageVector = if (isUnlocked) Icons.Default.FolderZip else Icons.Default.Payments,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = if (isUnlocked) "Ouvrir / ZIP" else "Payer $packPrice € & Recevoir",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BuyerDocumentSummaryDialog(
    dossier: SellerPropertyDossier,
    property: PropertyItem?,
    isUnlocked: Boolean,
    onDismiss: () -> Unit,
    onUpgradeToPaidPack: () -> Unit
) {
    val summaryText = remember(dossier, property) {
        dossier.executiveSummary.ifBlank {
            SellerDocumentCatalog.buildFreeBuyerSummary(dossier, property)
        }
    }

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
                            text = "Résumé Gratuit du Dossier",
                            style = MaterialTheme.typography.titleLarge,
                            color = IvoryWarm
                        )
                        Text(
                            text = "${dossier.propertyKind} • ${dossier.residenceType}",
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
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        color = IvoryWarm,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
                    ) {
                        Text(
                            text = summaryText,
                            style = MaterialTheme.typography.bodySmall,
                            color = ObsidianInk,
                            modifier = Modifier.padding(12.dp)
                        )
                    }

                    if (dossier.isAlurApplicable) {
                        Surface(
                            color = WarningContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Synthèse Loi ALUR (Résidence)", style = MaterialTheme.typography.labelLarge, color = WarningAmber)
                                Text(
                                    text = "Syndic : ${dossier.syndicName.ifBlank { "Non renseigné" }} • Charges : ${MontyMarketData.formatEuro(dossier.annualCoproCharges)}/an • Fonds travaux : ${MontyMarketData.formatEuro(dossier.alurWorkFundEuro)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ObsidianInk
                                )
                            }
                        }
                    }

                    Text(
                        text = "État des pièces du dossier (${dossier.documents.size})",
                        style = MaterialTheme.typography.labelLarge,
                        color = EmeraldDeep
                    )

                    dossier.documents.forEach { doc ->
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
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(doc.title, style = MaterialTheme.typography.labelLarge, color = EmeraldDeep)
                                    Text(
                                        text = "[${doc.category}] • ${doc.status}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (doc.isProvided) SuccessGreen else SlateMuted
                                    )
                                    if (doc.summaryText.isNotBlank()) {
                                        Text(doc.summaryText, style = MaterialTheme.typography.bodySmall, color = ObsidianInk)
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    imageVector = if (isUnlocked) Icons.Default.LockOpen else Icons.Default.Lock,
                                    contentDescription = if (isUnlocked) "Débloqué" else "Fichier complet réservé au pack payant",
                                    tint = if (isUnlocked) SuccessGreen else GoldAntique,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = BorderSubtle)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) { Text("Fermer") }
                    Button(
                        onClick = {
                            onDismiss()
                            onUpgradeToPaidPack()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldDeep, contentColor = GoldChampagne)
                    ) {
                        Text(
                            if (isUnlocked) "Ouvrir & Télécharger les pièces"
                            else "Payer ${dossier.packPriceEuro} € & Recevoir"
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BuyerDocumentCheckoutAndPackDialog(
    dossier: SellerPropertyDossier,
    property: PropertyItem?,
    client: ClientDossier,
    onDismiss: () -> Unit,
    onSubmitOrder: (buyerEmail: String, methodLabel: String, markPaidImmediately: Boolean) -> Unit,
    onShowToast: (String) -> Unit
) {
    val context = LocalContext.current
    val isUnlocked = dossier.isUnlockedForBuyer(client.id)
    val isPending = dossier.hasPendingOrderForBuyer(client.id)
    var buyerEmail by remember(client.id) { mutableStateOf(client.email) }

    val saveZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { targetUri ->
        if (targetUri != null) {
            runCatching {
                val bytes = SellerDocumentVault.buildBuyerPackZipBytes(context, dossier, property)
                context.contentResolver.openOutputStream(targetUri)?.use { out ->
                    out.write(bytes)
                } ?: error("Ouverture destination impossible.")
            }.onSuccess {
                onShowToast("Archive ZIP enregistrée dans vos fichiers !")
            }.onFailure {
                onShowToast(it.localizedMessage ?: "Échec de l'export ZIP.")
            }
        }
    }

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
                            text = if (isUnlocked) "Pack Documents Débloqué" else "Recevoir le Dossier Complet",
                            style = MaterialTheme.typography.titleLarge,
                            color = IvoryWarm
                        )
                        Text(
                            text = "${dossier.propertyTitle} • ${dossier.documents.count { it.isProvided }} pièces prêtes",
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
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (!isUnlocked) {
                        Surface(
                            color = IvoryWarm,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, GoldAntique.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "Pack Complet (${dossier.packPriceEuro} €)",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = EmeraldDeep,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Inclut les diagnostics DDT, les actes & pièces spécifiques (${dossier.propertyKind}), ainsi que l'ensemble des annexes Loi ALUR conservées pour ${dossier.residenceType}.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ObsidianInk
                                )
                            }
                        }

                        OutlinedTextField(
                            value = buyerEmail,
                            onValueChange = { buyerEmail = it },
                            label = { Text("E-mail de réception du pack") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (dossier.paymentLinkUrl.isNotBlank()) {
                            OutlinedButton(
                                onClick = {
                                    runCatching {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(dossier.paymentLinkUrl))
                                        context.startActivity(intent)
                                    }.onFailure {
                                        onShowToast("Lien de paiement invalide.")
                                    }
                                    onSubmitOrder(buyerEmail, "Lien paiement agence", false)
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Payer via le lien sécurisé agence (${dossier.packPriceEuro} €)")
                            }
                        }

                        if (isPending) {
                            Surface(
                                color = WarningContainer,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Paiement en attente de confirmation par le conseiller. En environnement de test/démo agence, vous pouvez aussi simuler la validation immédiate ci-dessous.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = WarningAmber,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }

                        Button(
                            onClick = {
                                onSubmitOrder(buyerEmail, "Règlement Démo Agence", true)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldDeep, contentColor = GoldChampagne),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("buyer_checkout_confirm_demo_button")
                        ) {
                            Icon(Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Régler ${dossier.packPriceEuro} € & Débloquer (Mode Démo)",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        Surface(
                            color = SuccessContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen)
                                Text(
                                    text = "Dossier complet débloqué ! Ouvrez les pièces ou téléchargez l'archive ZIP.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SuccessGreen,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    SellerDocumentVault.shareBuyerPackZip(context, dossier, property)
                                        .onFailure { onShowToast(it.localizedMessage ?: "Partage impossible.") }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldDeep, contentColor = GoldChampagne),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Partager ZIP", style = MaterialTheme.typography.labelSmall)
                            }
                            OutlinedButton(
                                onClick = {
                                    saveZipLauncher.launch("dossier_vendeur_${dossier.propertyId}.zip")
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Enregistrer ZIP", style = MaterialTheme.typography.labelSmall)
                            }
                        }

                        dossier.documents.filter { it.isProvided }.forEach { doc ->
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
                                        Text(doc.title, style = MaterialTheme.typography.labelLarge, color = EmeraldDeep)
                                        Text(
                                            text = if (doc.fileName.isNotBlank()) "${doc.fileName} • ${SellerDocumentVault.formatBytes(doc.sizeBytes)}" else doc.summaryText.ifBlank { doc.status },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = SlateMuted
                                        )
                                    }
                                    if (doc.localVaultPath.isNotBlank()) {
                                        TextButton(
                                            onClick = {
                                                SellerDocumentVault.openDocumentWithViewer(context, doc)
                                                    .onFailure { onShowToast(it.localizedMessage ?: "Ouverture impossible.") }
                                            }
                                        ) {
                                            Text("Ouvrir")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = BorderSubtle)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Fermer") }
                }
            }
        }
    }
}
