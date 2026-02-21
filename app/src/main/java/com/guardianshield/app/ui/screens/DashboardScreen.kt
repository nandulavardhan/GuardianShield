package com.guardianshield.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guardianshield.app.GuardianApp
import com.guardianshield.app.service.SosTriggerService
import com.guardianshield.app.ui.theme.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToDeadManSwitch: () -> Unit,
    onNavigateToContacts: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = GuardianApp.instance.preferencesManager
    val isSosActive by prefs.isSosActive.collectAsState(initial = false)
    val userName by prefs.userName.collectAsState(initial = "")

    // Pulse animation for SOS button
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isSosActive) 1.15f else 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (isSosActive) 600 else 1500,
                easing = EaseInOutCubic
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Guardian Shield",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                        if (userName.isNotBlank()) {
                            Text(
                                "Welcome, $userName",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
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
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            // Status Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(8.dp, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSosActive) RedDark.copy(alpha = 0.3f) else CardBackground
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(if (isSosActive) DangerRed else SafeGreen)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            if (isSosActive) "🚨 SOS ACTIVE" else "✅ Status: Safe",
                            fontWeight = FontWeight.Bold,
                            color = if (isSosActive) DangerRed else SafeGreen
                        )
                        Text(
                            if (isSosActive) "Emergency alerts sent. Location tracking active."
                            else "All systems ready.",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            // SOS Button
            Box(contentAlignment = Alignment.Center) {
                // Outer glow ring
                Box(
                    modifier = Modifier
                        .size(220.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    if (isSosActive) DangerRed.copy(alpha = pulseAlpha)
                                    else RedPrimary.copy(alpha = pulseAlpha * 0.5f),
                                    Color.Transparent
                                )
                            )
                        )
                )

                // Main SOS Button
                Button(
                    onClick = {
                        scope.launch {
                            if (isSosActive) {
                                SosTriggerService.cancelSos(context)
                            } else {
                                SosTriggerService.triggerSos(context, "manual")
                            }
                        }
                    },
                    modifier = Modifier
                        .size(160.dp)
                        .shadow(16.dp, CircleShape),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSosActive) DangerRed else RedPrimary
                    )
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            if (isSosActive) "STOP" else "SOS",
                            fontSize = 36.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        Text(
                            if (isSosActive) "Cancel Alert" else "Tap to Alert",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Quick Action Cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                QuickActionCard(
                    icon = Icons.Default.Shield,
                    title = "Dead Man\nSwitch",
                    color = AccentAmber,
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToDeadManSwitch
                )
                QuickActionCard(
                    icon = Icons.Default.People,
                    title = "Emergency\nContacts",
                    color = AccentCyan,
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToContacts
                )
                QuickActionCard(
                    icon = Icons.Default.Visibility,
                    title = "Ghost\nMode",
                    color = AccentGreen,
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToSettings
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Feature info cards
            FeatureInfoCard(
                icon = Icons.Default.LocationOn,
                title = "GPS Tracking",
                description = "Real-time location streaming activates on SOS trigger",
                color = AccentCyan
            )

            Spacer(modifier = Modifier.height(8.dp))

            FeatureInfoCard(
                icon = Icons.Default.CameraAlt,
                title = "Burst Capture",
                description = "6 photos (front + rear) captured silently on SOS",
                color = AccentAmber
            )

            Spacer(modifier = Modifier.height(8.dp))

            FeatureInfoCard(
                icon = Icons.Default.BatteryAlert,
                title = "Digital Breadcrumbs",
                description = "Auto-sends location & audio when battery < 5%",
                color = DangerRed
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun QuickActionCard(
    icon: ImageVector,
    title: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .height(110.dp)
            .shadow(4.dp, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                icon, contentDescription = title,
                tint = color,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                title,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary,
                textAlign = TextAlign.Center,
                lineHeight = 14.sp
            )
        }
    }
}

@Composable
fun FeatureInfoCard(
    icon: ImageVector,
    title: String,
    description: String,
    color: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = title, tint = color, modifier = Modifier.size(22.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextPrimary)
                Text(description, fontSize = 11.sp, color = TextSecondary)
            }
        }
    }
}

private val EaseInOutCubic = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)
