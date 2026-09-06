package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.server.ServerManager
import com.example.ui.theme.VibrantBorder
import com.example.ui.theme.VibrantOnPrimaryContainer
import com.example.ui.theme.VibrantPrimary
import com.example.ui.theme.VibrantPrimaryContainer
import com.example.ui.theme.VibrantSecondaryContainer
import com.example.ui.theme.VibrantSurfaceCard
import com.example.ui.theme.VibrantSurfaceElevated
import com.example.ui.theme.VibrantTextPrimary
import com.example.ui.theme.VibrantTextSecondary
import com.example.utils.FileUtils
import com.example.viewmodel.LocalDropViewModel

@Composable
fun SettingsScreen(
    state: ServerManager.UiState,
    viewModel: LocalDropViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showPortDialog by remember { mutableStateOf(false) }
    var showCustomPinDialog by remember { mutableStateOf(false) }
    var hotSpotGuideExpanded by remember { mutableStateOf(false) }
    var routerGuideExpanded by remember { mutableStateOf(false) }

    if (showPortDialog) {
        var portInput by remember { mutableStateOf(state.port.toString()) }
        var portError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showPortDialog = false },
            title = { Text("Set Server Port", color = VibrantTextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Standard range 1024 - 65535. Default is 8080.",
                        style = MaterialTheme.typography.bodySmall,
                        color = VibrantTextSecondary
                    )
                    OutlinedTextField(
                        value = portInput,
                        onValueChange = {
                            portInput = it.filter { char -> char.isDigit() }
                            val num = portInput.toIntOrNull()
                            portError = if (num == null || num !in 1024..65535) "Port must be 1024 - 65535" else null
                        },
                        label = { Text("Port Number") },
                        isError = portError != null,
                        supportingText = { if (portError != null) Text(portError!!) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("port_input_field")
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(8080, 8081, 8888, 9000).forEach { p ->
                            FilterChip(
                                selected = portInput == p.toString(),
                                onClick = { portInput = p.toString(); portError = null },
                                label = { Text(p.toString()) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = VibrantPrimaryContainer,
                                    selectedLabelColor = VibrantOnPrimaryContainer
                                )
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val num = portInput.toIntOrNull()
                        if (num != null && num in 1024..65535) {
                            viewModel.setPort(num)
                            showPortDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VibrantPrimary,
                        contentColor = Color.White
                    ),
                    enabled = portError == null && portInput.isNotEmpty()
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showPortDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showCustomPinDialog) {
        var pinInput by remember { mutableStateOf(state.config.pinCode) }

        AlertDialog(
            onDismissRequest = { showCustomPinDialog = false },
            title = { Text("Set Access PIN", color = VibrantTextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Enter a 4 to 8 digit security PIN for PC client login:",
                        style = MaterialTheme.typography.bodySmall,
                        color = VibrantTextSecondary
                    )
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { pinInput = it.take(8) },
                        label = { Text("PIN Code") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.setPinCode(pinInput)
                        showCustomPinDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VibrantPrimary,
                        contentColor = Color.White
                    ),
                    enabled = pinInput.length in 4..8
                ) {
                    Text("Save PIN")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showCustomPinDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Server Configuration Section
        Text(
            text = "Server Configuration",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = VibrantTextPrimary
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = VibrantSurfaceCard),
            border = androidx.compose.foundation.BorderStroke(1.dp, VibrantBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Port Setting
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showPortDialog = true }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(VibrantSecondaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.NetworkCheck, contentDescription = null, tint = VibrantPrimary, modifier = Modifier.size(20.dp))
                        }
                        Column {
                            Text("Listening Port", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = VibrantTextPrimary)
                            Text("Default 8080", style = MaterialTheme.typography.labelSmall, color = VibrantTextSecondary)
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = state.port.toString(),
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = VibrantPrimary
                        )
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = VibrantTextSecondary)
                    }
                }

                // PIN Protection Setting
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(VibrantSecondaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = VibrantPrimary, modifier = Modifier.size(20.dp))
                        }
                        Column {
                            Text("PIN Authentication", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = VibrantTextPrimary)
                            Text("Require PIN on web browser", style = MaterialTheme.typography.labelSmall, color = VibrantTextSecondary)
                        }
                    }

                    Switch(
                        checked = state.config.pinRequired,
                        onCheckedChange = { viewModel.setPinRequired(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = VibrantPrimary,
                            checkedTrackColor = VibrantPrimaryContainer
                        )
                    )
                }

                // Custom PIN
                AnimatedVisibility(visible = state.config.pinRequired) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showCustomPinDialog = true }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(VibrantSecondaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = VibrantPrimary, modifier = Modifier.size(20.dp))
                            }
                            Column {
                                Text("Change PIN Code", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = VibrantTextPrimary)
                                Text("Current: ${state.config.pinCode}", style = MaterialTheme.typography.labelSmall, color = VibrantTextSecondary)
                            }
                        }

                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = VibrantTextSecondary)
                    }
                }
            }
        }

        // Connection Guides & Offline Mode
        Text(
            text = "Wi-Fi & Offline Guides",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = VibrantTextPrimary
        )

        // Hotspot guide
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = VibrantSurfaceCard),
            border = androidx.compose.foundation.BorderStroke(1.dp, VibrantBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { hotSpotGuideExpanded = !hotSpotGuideExpanded },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.WifiTethering, contentDescription = null, tint = VibrantPrimary)
                        Text(
                            text = "No Wi-Fi Router? Use Phone Hotspot",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = VibrantTextPrimary
                        )
                    }
                    Icon(
                        if (hotSpotGuideExpanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = VibrantTextSecondary
                    )
                }

                AnimatedVisibility(visible = hotSpotGuideExpanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "1. Turn on Portable Hotspot in Android Settings.",
                            style = MaterialTheme.typography.bodySmall,
                            color = VibrantTextSecondary
                        )
                        Text(
                            text = "2. Mobile Data can be OFF. No Internet is needed.",
                            style = MaterialTheme.typography.bodySmall,
                            color = VibrantTextSecondary
                        )
                        Text(
                            text = "3. Connect your PC / laptop to your phone's Hotspot Wi-Fi.",
                            style = MaterialTheme.typography.bodySmall,
                            color = VibrantTextSecondary
                        )
                        Text(
                            text = "4. Launch LocalDrop and open the shown IP address in browser.",
                            style = MaterialTheme.typography.bodySmall,
                            color = VibrantTextSecondary
                        )
                    }
                }
            }
        }

        // Router guide
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = VibrantSurfaceCard),
            border = androidx.compose.foundation.BorderStroke(1.dp, VibrantBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { routerGuideExpanded = !routerGuideExpanded },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Router, contentDescription = null, tint = VibrantPrimary)
                        Text(
                            text = "Same Wi-Fi Network Setup",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = VibrantTextPrimary
                        )
                    }
                    Icon(
                        if (routerGuideExpanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = VibrantTextSecondary
                    )
                }

                AnimatedVisibility(visible = routerGuideExpanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "1. Connect both this phone and PC to the same Wi-Fi router.",
                            style = MaterialTheme.typography.bodySmall,
                            color = VibrantTextSecondary
                        )
                        Text(
                            text = "2. The router does NOT need an Internet connection.",
                            style = MaterialTheme.typography.bodySmall,
                            color = VibrantTextSecondary
                        )
                        Text(
                            text = "3. Open the phone's LAN IP address on PC: e.g. http://192.168.1.x:8080",
                            style = MaterialTheme.typography.bodySmall,
                            color = VibrantTextSecondary
                        )
                        Text(
                            text = "4. Enjoy ultra-fast local network file transfers!",
                            style = MaterialTheme.typography.bodySmall,
                            color = VibrantTextSecondary
                        )
                    }
                }
            }
        }

        // Storage Directory Info
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = VibrantSurfaceCard),
            border = androidx.compose.foundation.BorderStroke(1.dp, VibrantBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(VibrantSecondaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Folder, contentDescription = null, tint = VibrantPrimary, modifier = Modifier.size(20.dp))
                }

                Column {
                    Text("LocalDrop Storage Folder", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = VibrantTextPrimary)
                    Text(
                        FileUtils.getStorageDir(context).absolutePath,
                        style = MaterialTheme.typography.labelSmall,
                        color = VibrantTextSecondary,
                        maxLines = 2
                    )
                }
            }
        }

        // About LocalDrop Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = VibrantSurfaceCard),
            border = androidx.compose.foundation.BorderStroke(1.dp, VibrantBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = VibrantPrimary, modifier = Modifier.size(20.dp))
                    Text(
                        text = "About LocalDrop v1.0",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = VibrantTextPrimary
                    )
                }

                Text(
                    text = "High-speed local network file transfer server for Android. Zero cloud telemetry, 100% offline, private and open local HTTP server.",
                    style = MaterialTheme.typography.bodySmall,
                    color = VibrantTextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
