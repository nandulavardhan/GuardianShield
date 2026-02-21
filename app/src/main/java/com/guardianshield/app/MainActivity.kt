package com.guardianshield.app

import android.os.Bundle
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.guardianshield.app.service.SosTriggerService
import com.guardianshield.app.service.VolumeButtonSosService
import com.guardianshield.app.ui.screens.*
import com.guardianshield.app.ui.theme.DarkBackground
import com.guardianshield.app.ui.theme.GuardianShieldTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {

    // 3-finger swipe detection
    private var fingerStartY = floatArrayOf(0f, 0f, 0f)
    private val SWIPE_THRESHOLD = 300f
    private var gestureActive = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Determine start destination
        val prefs = GuardianApp.instance.preferencesManager
        val isSetup = runBlocking { prefs.isSetupComplete.first() }

        // Start Hardware Volume Trigger Monitor 
        if (isSetup) {
            VolumeButtonSosService.start(this)
        }

        setContent {
            GuardianShieldTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DarkBackground
                ) {
                    val navController = rememberNavController()

                    NavHost(
                        navController = navController,
                        startDestination = if (isSetup) "dashboard" else "login"
                    ) {
                        composable("login") {
                            LoginScreen(
                                onLoginSuccess = {
                                    val setupDone = runBlocking { prefs.isSetupComplete.first() }
                                    if (setupDone) {
                                        navController.navigate("dashboard") {
                                            popUpTo("login") { inclusive = true }
                                        }
                                    } else {
                                        navController.navigate("setup") {
                                            popUpTo("login") { inclusive = true }
                                        }
                                    }
                                }
                            )
                        }

                        composable("setup") {
                            SetupScreen(
                                onSetupComplete = {
                                    navController.navigate("dashboard") {
                                        popUpTo("setup") { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable("dashboard") {
                            DashboardScreen(
                                onNavigateToDeadManSwitch = {
                                    navController.navigate("dead_man_switch")
                                },
                                onNavigateToContacts = {
                                    navController.navigate("contacts")
                                },
                                onNavigateToSettings = {
                                    navController.navigate("settings")
                                }
                            )
                        }

                        composable("dead_man_switch") {
                            DeadManSwitchScreen(
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable("contacts") {
                            ContactsScreen(
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable("settings") {
                            SettingsScreen(
                                onBack = { navController.popBackStack() },
                                onLogout = {
                                    navController.navigate("login") {
                                        popUpTo(0) { inclusive = true }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * Detect 3-finger swipe down gesture for discreet SOS trigger.
     * Works across all screens.
     */
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount >= 3 && !gestureActive) {
                    gestureActive = true
                    for (i in 0 until minOf(3, event.pointerCount)) {
                        fingerStartY[i] = event.getY(i)
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (gestureActive && event.pointerCount >= 3) {
                    var allSwipedDown = true
                    for (i in 0 until minOf(3, event.pointerCount)) {
                        val deltaY = event.getY(i) - fingerStartY[i]
                        if (deltaY < SWIPE_THRESHOLD) {
                            allSwipedDown = false
                            break
                        }
                    }
                    if (allSwipedDown) {
                        gestureActive = false
                        SosTriggerService.triggerSos(this, "gesture_3finger")
                        return true
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                gestureActive = false
            }
        }
        return super.dispatchTouchEvent(event)
    }
}
