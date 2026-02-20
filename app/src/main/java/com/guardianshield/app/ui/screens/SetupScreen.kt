package com.guardianshield.app.ui.screens

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guardianshield.app.GuardianApp
import com.guardianshield.app.data.SupabaseProvider
import com.guardianshield.app.data.models.EmergencyContact
import com.guardianshield.app.data.models.Profile
import com.guardianshield.app.ui.theme.*
import com.guardianshield.app.util.PermissionsHelper
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    onSetupComplete: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = GuardianApp.instance.preferencesManager

    var currentStep by remember { mutableIntStateOf(0) }
    var fullName by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var contactName by remember { mutableStateOf("") }
    var contactPhone by remember { mutableStateOf("") }
    var contactRelation by remember { mutableStateOf("") }
    var contacts by remember { mutableStateOf<List<EmergencyContact>>(emptyList()) }
    var pcrNumber by remember { mutableStateOf("112") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var permissionsGranted by remember { mutableStateOf(PermissionsHelper.hasAllPermissions(context)) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        permissionsGranted = results.values.all { it }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Setup — Step ${currentStep + 1}/4", fontWeight = FontWeight.Bold)
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
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Progress indicator
            LinearProgressIndicator(
                progress = { (currentStep + 1) / 4f },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                color = RedPrimary,
                trackColor = CardBackground,
            )

            when (currentStep) {
                // Step 1: Profile
                0 -> {
                    Text("👤 Your Profile", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Spacer(modifier = Modifier.height(24.dp))

                    OutlinedTextField(
                        value = fullName,
                        onValueChange = { fullName = it },
                        label = { Text("Full Name") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = RedPrimary, cursorColor = RedPrimary)
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("Phone Number") },
                        leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = RedPrimary, cursorColor = RedPrimary)
                    )
                }

                // Step 2: PIN
                1 -> {
                    Text("🔐 Set Your PIN", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("This PIN is used to cancel the Dead Man Switch.", fontSize = 13.sp, color = TextSecondary, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(24.dp))

                    OutlinedTextField(
                        value = pin,
                        onValueChange = { pin = it },
                        label = { Text("PIN (4-6 digits)") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = RedPrimary, cursorColor = RedPrimary)
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = confirmPin,
                        onValueChange = { confirmPin = it },
                        label = { Text("Confirm PIN") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = RedPrimary, cursorColor = RedPrimary)
                    )
                }

                // Step 3: Emergency Contacts
                2 -> {
                    Text("📞 Emergency Contacts", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Add people who will receive your SOS alerts.", fontSize = 13.sp, color = TextSecondary)
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = contactName,
                        onValueChange = { contactName = it },
                        label = { Text("Contact Name") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = RedPrimary, cursorColor = RedPrimary)
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = contactPhone,
                        onValueChange = { contactPhone = it },
                        label = { Text("Phone Number") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = RedPrimary, cursorColor = RedPrimary)
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = contactRelation,
                        onValueChange = { contactRelation = it },
                        label = { Text("Relationship (e.g. Mother, Friend)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = RedPrimary, cursorColor = RedPrimary)
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = pcrNumber,
                        onValueChange = { pcrNumber = it },
                        label = { Text("PCR / Police Number") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = RedPrimary, cursorColor = RedPrimary)
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            if (contactName.isNotBlank() && contactPhone.isNotBlank()) {
                                contacts = contacts + EmergencyContact(
                                    name = contactName,
                                    phone = contactPhone,
                                    relationship = contactRelation
                                )
                                contactName = ""; contactPhone = ""; contactRelation = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentCyan),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Contact")
                    }

                    contacts.forEachIndexed { idx, contact ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = CardBackground)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(contact.name, fontWeight = FontWeight.Bold, color = TextPrimary)
                                    Text("${contact.phone} • ${contact.relationship}", fontSize = 12.sp, color = TextSecondary)
                                }
                                IconButton(onClick = {
                                    contacts = contacts.toMutableList().also { it.removeAt(idx) }
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Remove", tint = DangerRed)
                                }
                            }
                        }
                    }
                }

                // Step 4: Permissions
                3 -> {
                    Text("📱 Permissions", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("These permissions are required for emergency features.", fontSize = 13.sp, color = TextSecondary, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(24.dp))

                    val perms = listOf(
                        "📍 Location" to "GPS tracking during emergencies",
                        "📸 Camera" to "Burst photos for evidence",
                        "📨 SMS" to "Send alerts to emergency contacts",
                        "🎙️ Microphone" to "Audio clip in digital breadcrumbs",
                        "🔔 Notifications" to "SOS status notifications"
                    )

                    perms.forEach { (name, desc) ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = CardBackground)
                        ) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(name, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                                    Text(desc, fontSize = 11.sp, color = TextSecondary)
                                }
                                Icon(
                                    if (permissionsGranted) Icons.Default.CheckCircle else Icons.Default.Error,
                                    contentDescription = null,
                                    tint = if (permissionsGranted) SafeGreen else WarningAmber
                                )
                            }
                        }
                    }

                    if (!permissionsGranted) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                permissionLauncher.launch(PermissionsHelper.getRequiredPermissions().toTypedArray())
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentAmber),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Grant Permissions", fontWeight = FontWeight.Bold, color = DarkBackground)
                        }
                    }
                }
            }

            // Error
            errorMessage?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(it, color = DangerRed, fontSize = 13.sp)
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Navigation buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (currentStep > 0) {
                    OutlinedButton(
                        onClick = { currentStep--; errorMessage = null },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Back")
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                Button(
                    onClick = {
                        errorMessage = null
                        when (currentStep) {
                            0 -> {
                                if (fullName.isBlank()) errorMessage = "Name is required"
                                else currentStep++
                            }
                            1 -> {
                                if (pin.length < 4) errorMessage = "PIN must be at least 4 digits"
                                else if (pin != confirmPin) errorMessage = "PINs do not match"
                                else currentStep++
                            }
                            2 -> {
                                if (contacts.isEmpty()) errorMessage = "Add at least one contact"
                                else currentStep++
                            }
                            3 -> {
                                // Complete setup
                                scope.launch {
                                    isLoading = true
                                    try {
                                        val pinHash = PermissionsHelper.hashPin(pin)
                                        prefs.setPinHash(pinHash)
                                        prefs.setUserName(fullName)
                                        prefs.setPcrNumber(pcrNumber)

                                        // Save profile to Supabase
                                        val userId = SupabaseProvider.client.auth.currentUserOrNull()?.id ?: ""
                                        try {
                                            SupabaseProvider.client.postgrest["profiles"].insert(
                                                Profile(
                                                    id = userId,
                                                    fullName = fullName,
                                                    phone = phone,
                                                    pinHash = pinHash
                                                )
                                            )

                                            // Save contacts to Supabase
                                            contacts.forEach { contact ->
                                                SupabaseProvider.client.postgrest["emergency_contacts"].insert(
                                                    EmergencyContact(
                                                        userId = userId,
                                                        name = contact.name,
                                                        phone = contact.phone,
                                                        relationship = contact.relationship
                                                    )
                                                )
                                            }
                                        } catch (e: Exception) {
                                            // Continue even if Supabase fails — prefs are saved locally
                                        }

                                        prefs.setSetupComplete(true)
                                        onSetupComplete()
                                    } catch (e: Exception) {
                                        errorMessage = e.message
                                    } finally {
                                        isLoading = false
                                    }
                                }
                            }
                        }
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RedPrimary),
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = TextPrimary, strokeWidth = 2.dp)
                    } else {
                        Text(if (currentStep < 3) "Next" else "Complete Setup", fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
