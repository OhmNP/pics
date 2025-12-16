package com.photosync.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.photosync.android.ui.components.GlassCard
import com.photosync.android.ui.components.GradientBox
import com.photosync.android.ui.components.NeonText
import com.photosync.android.ui.theme.Error
import com.photosync.android.ui.theme.Primary
import com.photosync.android.ui.theme.PrimaryGlow
import com.photosync.android.ui.theme.Secondary
import com.photosync.android.ui.theme.Success
import com.photosync.android.ui.theme.SurfaceVariant
import com.photosync.android.ui.theme.TextPrimary
import com.photosync.android.ui.theme.TextSecondary
import com.photosync.android.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(
    onNavigateToPairing: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel()
) {
    val serverIp by viewModel.serverIp.collectAsStateWithLifecycle()
    val serverPort by viewModel.serverPort.collectAsStateWithLifecycle()
    val userName by viewModel.userName.collectAsStateWithLifecycle()
    
    // Gradient Background (matching Home)
    val focusManager = LocalFocusManager.current
    GradientBox(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onTap = {
                    focusManager.clearFocus()
                })
            }
    ) {
        // Ambient Background Blobs
        Box(
            modifier = Modifier
                .offset(x = (-100).dp, y = (-100).dp)
                .size(400.dp)
                .alpha(0.2f)
                .background(
                    brush = Brush.radialGradient(colors = listOf(Secondary, Color.Transparent)),
                    shape = CircleShape
                )
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 100.dp, y = 100.dp)
                .size(400.dp)
                .alpha(0.2f)
                .background(
                    brush = Brush.radialGradient(colors = listOf(Primary, Color.Transparent)),
                    shape = CircleShape
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // --- Profile Header ---
            ProfileHeaderSection(
                userName = userName,
                onUserNameChange = viewModel::updateUserName
            )

            // --- Server Configuration Card ---
            SettingsSectionTitle("Configuration")
            ServerConfigCard(
                serverIp = serverIp,
                serverPort = serverPort,
                onConfigureClick = onNavigateToPairing
            )

            // --- Data Management Card ---
            SettingsSectionTitle("Data Management")
            DataManagementCard(
                onResetHistory = { viewModel.resetSyncHistory() }
            )

            // --- About Card ---
            SettingsSectionTitle("Application")
            AboutCard()
            
            Spacer(modifier = Modifier.height(60.dp)) // Bottom padding
        }
    }
}

@Composable
private fun ProfileHeaderSection(
    userName: String,
    onUserNameChange: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Avatar with Glow
        Box(
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .blur(20.dp)
                    .background(Primary.copy(alpha = 0.5f), CircleShape)
            )
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .border(2.dp, Brush.linearGradient(listOf(Primary, Secondary)), CircleShape)
                    .background(SurfaceVariant, CircleShape)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Person,
                    contentDescription = "Profile",
                    tint = TextPrimary.copy(alpha = 0.8f),
                    modifier = Modifier.size(50.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Editable User Name or Display Text
        var isEditing by remember { mutableStateOf(userName.isBlank()) }
        val focusRequester = remember { FocusRequester() }
        
        if (isEditing) {
            var hasGainedFocus by remember { mutableStateOf(false) }
            
            LaunchedEffect(Unit) {
               focusRequester.requestFocus()
            }
            
            OutlinedTextField(
                value = userName,
                onValueChange = onUserNameChange,
                label = { Text("Display Name") },
                textStyle = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                ),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Primary,
                    unfocusedBorderColor = Color.Transparent,
                    focusedLabelColor = Primary,
                    unfocusedLabelColor = TextSecondary,
                    cursorColor = Primary,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent
                ),
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .focusRequester(focusRequester)
                    .onFocusChanged { focusState ->
                        if (focusState.isFocused) {
                            hasGainedFocus = true
                        }
                        if (!focusState.isFocused && hasGainedFocus && userName.isNotBlank() && isEditing) {
                            isEditing = false
                        }
                    },
                trailingIcon = {
                    IconButton(onClick = { if (userName.isNotBlank()) isEditing = false }) {
                        Icon(Icons.Rounded.Check, "Save", tint = Primary)
                    }
                }
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                NeonText(
                    text = userName,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary,
                    glowColor = PrimaryGlow.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = { isEditing = true }) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = "Edit Name",
                        tint = TextSecondary.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        
        Text(
            text = "PhotoSync Client",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
    }
}

@Composable
private fun SettingsSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = TextSecondary,
        modifier = Modifier.padding(start = 8.dp)
    )
}

@Composable
private fun ServerConfigCard(
    serverIp: String,
    serverPort: String,
    onConfigureClick: () -> Unit
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(Primary.copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Dns,
                        contentDescription = "Server",
                        tint = Primary
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = "Current Server",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = TextPrimary
                    )
                    Text(
                        text = if (serverIp.isNotBlank()) "$serverIp:$serverPort" else "Not Configured",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (serverIp.isNotBlank()) Success else TextSecondary
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = onConfigureClick,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Primary.copy(alpha = 0.2f),
                    contentColor = Primary
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Rounded.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("New Server Configuration")
            }
        }
    }
}

@Composable
private fun DataManagementCard(onResetHistory: () -> Unit) {
    var showDialog by remember { mutableStateOf(false) }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.clickable { showDialog = true }) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(Error.copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.History,
                        contentDescription = "Reset",
                        tint = Error
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Reset Sync History",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = TextPrimary
                    )
                    Text(
                        text = "Clear database tracking",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = TextSecondary
                )
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            containerColor = SurfaceVariant.copy(alpha = 0.95f),
            title = { Text("Reset Sync History?", color = TextPrimary) },
            text = { 
                Text(
                    "This will clear the local database of synced file records. All photos will be re-scanned and potentially re-synced.",
                    color = TextSecondary
                ) 
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onResetHistory()
                        showDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Error)
                ) {
                    Text("Reset")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = TextSecondary)
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun AboutCard() {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(Secondary.copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Info,
                    contentDescription = "About",
                    tint = Secondary
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = "PhotoSync",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = TextPrimary
                )
                Text(
                    text = "Version 1.0.0",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }
    }
}
