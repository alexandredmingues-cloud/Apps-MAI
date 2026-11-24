package com.example.ui.saas

import android.content.Intent
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddBusiness
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.AgencyAuditLog
import com.example.data.model.AgencyMember
import com.example.data.model.AgencyOrg
import com.example.data.model.SaasPlan
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

@Composable
fun AgencyWorkspaceTab(
    agencies: List<AgencyOrg>,
    activeAgency: AgencyOrg?,
    isDirector: Boolean,
    members: List<AgencyMember>,
    auditLogs: List<AgencyAuditLog>,
    lastInviteCode: String?,
    onSelectAgency: (String) -> Unit,
    onCreateAgency: (String, SaasPlan, String) -> Unit,
    onUpdateAgencyPlan: (String, SaasPlan, String) -> Unit,
    onInviteCollaborator: (String, String) -> Unit,
    onJoinAgencyByCode: (String) -> Unit,
    onShowToast: (String) -> Unit
) {
    val context = LocalContext.current
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current

    var showCreateAgencyDialog by remember { mutableStateOf(false) }
    var showInviteDialog by remember { mutableStateOf(false) }
    var joinCodeInput by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // 1. Multi-Agency Organization Switcher
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = EmeraldDeep),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Espaces Multi-Agences & SaaS",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = IvoryWarm
                        )
                        Text(
                            text = "Cloisonnement strict par agence et par rôle",
                            style = MaterialTheme.typography.bodySmall,
                            color = GoldChampagne
                        )
                    }
                    Button(
                        onClick = { showCreateAgencyDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = GoldAntique, contentColor = ObsidianInk),
                        modifier = Modifier.testTag("saas_create_agency_button")
                    ) {
                        Icon(Icons.Default.AddBusiness, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Agence")
                    }
                }

                agencies.forEach { org ->
                    val selected = org.id == activeAgency?.id
                    Surface(
                        color = if (selected) EmeraldMid else Color(0xFF0E3024),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = if (selected) 1.5.dp else 1.dp,
                                color = if (selected) GoldChampagne else Color(0xFF285546),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelectAgency(org.id) }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Icon(Icons.Default.Business, contentDescription = null, tint = GoldChampagne)
                                Column {
                                    Text(org.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = IvoryWarm)
                                    Text(
                                        text = "${org.city} • ${org.saasPlan.label} • ${org.memberUids.size} membre(s)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = GoldChampagne
                                    )
                                }
                            }
                            if (selected) {
                                Icon(Icons.Default.CheckCircle, contentDescription = "Espace actif", tint = GoldChampagne)
                            }
                        }
                    }
                }
            }
        }

        // 2. Subscription Plan Switcher (Individual vs Agency)
        if (activeAgency != null) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Abonnement SaaS de l'espace actif",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = EmeraldDeep
                    )
                    SaasPlan.entries.forEach { plan ->
                        val isCurrent = activeAgency.saasPlan == plan
                        Surface(
                            color = if (isCurrent) SuccessContainer else IvoryWarm,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, if (isCurrent) SuccessGreen else BorderSubtle, RoundedCornerShape(12.dp))
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(enabled = isDirector && !isCurrent) {
                                    onUpdateAgencyPlan(activeAgency.name, plan, activeAgency.city)
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(plan.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = EmeraldDeep)
                                    Text(plan.subtitle, style = MaterialTheme.typography.bodySmall, color = SlateMuted)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(plan.priceMonthlyLabel, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = ObsidianInk)
                                    Text(
                                        text = if (isCurrent) "Actif" else if (isDirector) "Activer" else "Direction",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isCurrent) SuccessGreen else EmeraldDeep
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Collaborators & Invitations (RBAC)
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
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
                            text = "Équipe & Collaborateurs",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = EmeraldDeep
                        )
                        Text(
                            text = "Règle métier : un agent ne voit que ses contacts assignés.",
                            style = MaterialTheme.typography.labelSmall,
                            color = SlateMuted
                        )
                    }
                    if (isDirector) {
                        Button(
                            onClick = { showInviteDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldDeep, contentColor = GoldChampagne),
                            modifier = Modifier.testTag("saas_invite_collaborator_button")
                        ) {
                            Icon(Icons.Default.GroupAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Inviter")
                        }
                    }
                }

                if (!lastInviteCode.isNullOrBlank()) {
                    Surface(
                        color = WarningContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Dernier code généré", style = MaterialTheme.typography.labelSmall, color = WarningAmber)
                                Text(lastInviteCode, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = ObsidianInk)
                            }
                            Row {
                                IconButton(
                                    onClick = {
                                        clipboard.setText(AnnotatedString(lastInviteCode))
                                        onShowToast("Code $lastInviteCode copié.")
                                    }
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copier code", tint = EmeraldDeep)
                                }
                                IconButton(
                                    onClick = {
                                        val share = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, "Rejoins notre agence sur Mon Agent Immo avec le code : $lastInviteCode")
                                        }
                                        context.startActivity(Intent.createChooser(share, "Partager l'invitation"))
                                    }
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = "Partager invitation", tint = EmeraldDeep)
                                }
                            }
                        }
                    }
                }

                members.forEach { member ->
                    val isPending = member.status == "INVITED"
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, BorderSubtle, RoundedCornerShape(10.dp))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (member.role == "DIRECTOR") Icons.Default.VerifiedUser else Icons.Default.Person,
                                contentDescription = null,
                                tint = EmeraldDeep
                            )
                            Column {
                                Text(member.displayName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = "${member.agencyRole.labelFr} • ${member.email.ifBlank { "Compte Google" }}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SlateMuted
                                )
                            }
                        }
                        Surface(
                            color = if (isPending) WarningContainer else SuccessContainer,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = if (isPending) "Invité (${member.inviteCode})" else "Actif",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isPending) WarningAmber else SuccessGreen,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                HorizontalDivider(color = BorderSubtle)

                // Join existing agency via invite code
                Text(
                    text = "Rejoindre une autre agence avec un code",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = EmeraldDeep
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = joinCodeInput,
                        onValueChange = { joinCodeInput = it.uppercase() },
                        leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                        placeholder = { Text("Ex : MNT-7A9B2C") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("saas_join_code_input")
                    )
                    OutlinedButton(
                        onClick = {
                            if (joinCodeInput.isNotBlank()) {
                                onJoinAgencyByCode(joinCodeInput.trim())
                                joinCodeInput = ""
                            }
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("saas_join_code_button")
                    ) {
                        Text("Rejoindre")
                    }
                }
            }
        }

        // 4. Immutable Audit History
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.History, contentDescription = null, tint = EmeraldDeep)
                    Column {
                        Text(
                            text = "Journal d'audit immuable (${auditLogs.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = EmeraldDeep
                        )
                        Text(
                            text = "Toutes les actions critiques sont tracées sans modification possible",
                            style = MaterialTheme.typography.labelSmall,
                            color = SlateMuted
                        )
                    }
                }

                if (auditLogs.isEmpty()) {
                    Text("Aucune écriture d'audit enregistrée.", style = MaterialTheme.typography.bodySmall, color = SlateMuted)
                } else {
                    auditLogs.take(25).forEach { log ->
                        Surface(
                            color = IvoryWarm,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "${log.actionType} • ${log.targetLabel}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = EmeraldDeep
                                    )
                                }
                                Text(
                                    text = "${log.actorName} — ${log.detail}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ObsidianInk
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateAgencyDialog) {
        var orgName by remember { mutableStateOf("") }
        var orgCity by remember { mutableStateOf("Montpellier") }
        var selectedPlan by remember { mutableStateOf(SaasPlan.AGENCY) }

        AlertDialog(
            onDismissRequest = { showCreateAgencyDialog = false },
            title = { Text("Créer une agence / espace SaaS", fontWeight = FontWeight.Bold, color = EmeraldDeep) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = orgName, onValueChange = { orgName = it }, label = { Text("Nom de l'agence *") }, singleLine = true)
                    OutlinedTextField(value = orgCity, onValueChange = { orgCity = it }, label = { Text("Ville") }, singleLine = true)
                    SaasPlan.entries.forEach { plan ->
                        Surface(
                            color = if (selectedPlan == plan) EmeraldDeep else IvoryWarm,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedPlan = plan }
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(plan.label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = if (selectedPlan == plan) GoldChampagne else ObsidianInk)
                                Text(plan.priceMonthlyLabel, style = MaterialTheme.typography.labelSmall, color = if (selectedPlan == plan) IvoryWarm else SlateMuted)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (orgName.isNotBlank()) {
                            onCreateAgency(orgName, selectedPlan, orgCity)
                            showCreateAgencyDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldDeep, contentColor = GoldChampagne)
                ) { Text("Créer") }
            },
            dismissButton = { TextButton(onClick = { showCreateAgencyDialog = false }) { Text("Annuler") } }
        )
    }

    if (showInviteDialog) {
        var collabName by remember { mutableStateOf("") }
        var collabEmail by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showInviteDialog = false },
            title = { Text("Inviter un collaborateur", fontWeight = FontWeight.Bold, color = EmeraldDeep) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Un code d'invitation unique lié à son compte Google sera généré avec le rôle Conseiller / Agent.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SlateMuted
                    )
                    OutlinedTextField(value = collabName, onValueChange = { collabName = it }, label = { Text("Nom du collaborateur *") }, singleLine = true)
                    OutlinedTextField(value = collabEmail, onValueChange = { collabEmail = it }, label = { Text("E-mail Google *") }, singleLine = true)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (collabName.isNotBlank() && collabEmail.contains("@")) {
                            onInviteCollaborator(collabEmail, collabName)
                            showInviteDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldDeep, contentColor = GoldChampagne)
                ) { Text("Générer l'invitation") }
            },
            dismissButton = { TextButton(onClick = { showInviteDialog = false }) { Text("Annuler") } }
        )
    }
}
