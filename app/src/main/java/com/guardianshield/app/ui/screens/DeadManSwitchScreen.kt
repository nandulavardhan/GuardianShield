package com.guardianshield.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guardianshield.app.GuardianApp
import com.guardianshield.app.service.SosTriggerService
import com.guardianshield.app.ui.theme.*
import com.guardianshield.app.util.PermissionsHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeadManSwitchScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = GuardianApp.instance.preferencesManager

    var isHolding by remember { mutableStateOf(false) }
    var countdownActive by remember { mutableStateOf(false) }
    var countdownSeconds by remember { mutableIntStateOf(10) }
    var showPinDialog by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf(false) }
    var sosFired by remember { mutableStateOf(false) }

    // Countdown timer
    LaunchedEffect(countdownActive) {
        if (countdownActive) {
            showPinDialog = true
            while (countdownSeconds > 0 && countdownActive) {
                delay(1000)
                countdownSeconds--
            }
            if (countdownSeconds <= 0 && countdownActive && !sosFired) {
                // Time's up — trigger SOS
                sosFired = true
                SosTriggerService.triggerSos(context, "dead_man_switch")
            }
        }
    }

    // Pulse animation
    val infiniteTransition = rememberInfiniteTransition(label = "dms_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = when {
            countdownActive -> 1.2f
            isHolding -> 1.03f
            else -> 1.06f
        },
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (countdownActive) 400 else 1200,
                easing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dms_scale"
    )

    // PIN Dialog
    if (showPinDialog) {
        AlertDialog(
            onDismissRequest = { /* Can't dismiss */ },
            containerColor = DarkSurface,
            title = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "⏱️ ${countdownSeconds}s",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = DangerRed,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Enter PIN to cancel SOS",
                        fontSize = 14.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            text = {
                Column {
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = {
                            pinInput = it
                            pinError = false
                        },
                        label = { Text("PIN") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        isError = pinError,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = RedPrimary,
                            cursorColor = RedPrimary
                        )
                    )
                    if (pinError) {
                        Text("Wrong PIN!", color = DangerRed, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            val storedHash = prefs.pinHash.first()
                            val inputHash = PermissionsHelper.hashPin(pinInput)
                            if (inputHash == storedHash) {
                                // Correct PIN — cancel
                                countdownActive = false
                                showPinDialog = false
                                pinInput = ""
                                countdownSeconds = 10
                            } else {
                                pinError = true
                                pinInput = ""
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SafeGreen)
                ) {
                    Text("Cancel SOS", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dead Man Switch", fontWeight = FontWeight.Bold) },
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
                .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (sosFired) {
                // SOS triggered screen
                Text(
                    "🚨 SOS TRIGGERED",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = DangerRed
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Emergency alerts have been sent.\nLocation tracking is active.",
                    fontSize = 14.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(32.dp))
                Button(
                    onClick = onBack,
                    colors = ButtonDefaults.buttonColors(containerColor = RedPrimary)
                ) {
                    Text("Return to Dashboard")
                }
            } else {
                // Instructions
                Text(
                    if (isHolding) "HOLDING — YOU'RE SAFE" else "HOLD THE BUTTON",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isHolding) SafeGreen else TextPrimary
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    "Release = 10 second countdown to SOS",
                    fontSize = 13.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(48.dp))

                // Hold Button
                Box(contentAlignment = Alignment.Center) {
                    // Glow
                    Box(
                        modifier = Modifier
                            .size(260.dp)
                            .scale(pulseScale)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        when {
                                            countdownActive -> DangerRed.copy(alpha = 0.5f)
                                            isHolding -> SafeGreen.copy(alpha = 0.3f)
                                            else -> AccentAmber.copy(alpha = 0.2f)
                                        },
                                        Color.Transparent
                                    )
                                )
                            )
                    )

                    // Button
                    Box(
                        modifier = Modifier
                            .size(200.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    countdownActive -> DangerRed
                                    isHolding -> SafeGreen
                                    else -> AccentAmber
                                }
                            )
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onPress = {
                                        isHolding = true
                                        countdownActive = false
                                        countdownSeconds = 10
                                        showPinDialog = false
                                        pinInput = ""

                                        // Wait for release
                                        val released = tryAwaitRelease()
                                        isHolding = false
                                        if (released && !sosFired) {
                                            countdownActive = true
                                        }
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                when {
                                    countdownActive -> "⚠️"
                                    isHolding -> "✓"
                                    else -> "🛡️"
                                },
                                fontSize = 40.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                when {
                                    countdownActive -> "RELEASING..."
                                    isHolding -> "SAFE"
                                    else -> "HOLD"
                                },
                                fontSize = 22.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(40.dp))

                // Info box
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBackground)
                ) {
                    Text(
                        "💡 If the phone is snatched and you can't enter your PIN within 10 seconds, SOS will trigger automatically with SMS and GPS.",
                        modifier = Modifier.padding(16.dp),
                        fontSize = 12.sp,
                        color = TextSecondary,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}
