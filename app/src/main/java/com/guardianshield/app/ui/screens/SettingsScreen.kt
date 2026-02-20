package com.guardianshield.app.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guardianshield.app.GuardianApp
import com.guardianshield.app.data.SupabaseProvider
import com.guardianshield.app.ghostmode.GhostMode
import com.guardianshield.app.ghostmode.GhostModeManager
import com.guardianshield.app.service.BatteryMonitorService
import com.guardianshield.app.ui.theme.*
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = GuardianApp.instance.preferencesManager
    val currentGhostMode by prefs.ghostMode.collectAsState(initial = "default")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground,
                    titleContentColor = TextPrimary
                )
            )
        },
        containerColor = DarkBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Ghost Mode Section
            Text(
                "👻 Ghost Mode",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Change the app's icon and name to disguise it in the launcher.",
                fontSize = 12.sp,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(16.dp))

            val modes = listOf(
                Triple(GhostMode.DEFAULT, "🛡️", "Guardian Shield"),
                Triple(GhostMode.CALCULATOR, "🧮", "Calculator"),
                Triple(GhostMode.NOTES, "📝", "My Notes")
            )

            modes.forEach { (mode, emoji, label) ->
                val isSelected = currentGhostMode == mode.name.lowercase()

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .then(
                            if (isSelected) Modifier.border(
                                2.dp, RedPrimary, RoundedCornerShape(12.dp)
                            ) else Modifier
                        )
                        .clickable {
                            scope.launch {
                                GhostModeManager.switchAppIdentity(context, mode)
                                prefs.setGhostMode(mode.name.lowercase())
                            }
                        },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) RedPrimary.copy(alpha = 0.1f) else CardBackground
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(emoji, fontSize = 28.sp)
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(label, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                            Text(
                                if (mode == GhostMode.DEFAULT) "Original app identity"
                                else "Disguised as $label",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                        if (isSelected) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = "Selected",
                                tint = SafeGreen
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Card(
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = AccentAmber.copy(alpha = 0.1f))
            ) {
                Text(
                    "💡 After switching Ghost Mode, the launcher icon updates within a few seconds. The app continues to work normally.",
                    modifier = Modifier.padding(12.dp),
                    fontSize = 11.sp,
                    color = AccentAmber,
                    lineHeight = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Background services
            Text("⚙️ Services", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(modifier = Modifier.height(12.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = CardBackground)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.BatteryAlert, contentDescription = null, tint = WarningAmber)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Battery Monitor", fontWeight = FontWeight.SemiBold, color = TextPrimary)
                            Text("Digital breadcrumbs when battery < 5%", fontSize = 11.sp, color = TextSecondary)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { BatteryMonitorService.start(context) },
                            colors = ButtonDefaults.buttonColors(containerColor = SafeGreen),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Start", fontSize = 12.sp, color = DarkBackground)
                        }
                        OutlinedButton(
                            onClick = { BatteryMonitorService.stop(context) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Stop", fontSize = 12.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Logout
            Button(
                onClick = {
                    scope.launch {
                        try {
                            SupabaseProvider.client.auth.signOut()
                        } catch (_: Exception) {}
                        onLogout()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DangerRed)
            ) {
                Icon(Icons.Default.Logout, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Logout", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(24.dp))

            // App info
            Text(
                "Guardian Shield v1.0 • Education & Hackathon Use",
                fontSize = 11.sp,
                color = TextMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
