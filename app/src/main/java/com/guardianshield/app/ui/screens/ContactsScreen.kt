package com.guardianshield.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guardianshield.app.data.SupabaseProvider
import com.guardianshield.app.data.models.EmergencyContact
import com.guardianshield.app.ui.theme.*
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var contacts by remember { mutableStateOf<List<EmergencyContact>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showAddDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var newPhone by remember { mutableStateOf("") }
    var newRelation by remember { mutableStateOf("") }

    // Load contacts
    LaunchedEffect(Unit) {
        try {
            val userId = SupabaseProvider.client.auth.currentUserOrNull()?.id ?: ""
            contacts = SupabaseProvider.client.postgrest["emergency_contacts"]
                .select {
                    filter { eq("user_id", userId) }
                }
                .decodeList<EmergencyContact>()
        } catch (_: Exception) {}
        isLoading = false
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            containerColor = DarkSurface,
            title = { Text("Add Emergency Contact", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(
                        value = newName, onValueChange = { newName = it },
                        label = { Text("Name") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = RedPrimary, cursorColor = RedPrimary)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newPhone, onValueChange = { newPhone = it },
                        label = { Text("Phone Number") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = RedPrimary, cursorColor = RedPrimary)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newRelation, onValueChange = { newRelation = it },
                        label = { Text("Relationship") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = RedPrimary, cursorColor = RedPrimary)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newName.isNotBlank() && newPhone.isNotBlank()) {
                            scope.launch {
                                try {
                                    val userId = SupabaseProvider.client.auth.currentUserOrNull()?.id ?: ""
                                    val contactInsert = com.guardianshield.app.data.models.EmergencyContactInsert(
                                        userId = userId,
                                        name = newName,
                                        phone = newPhone,
                                        relationship = newRelation
                                    )
                                    val insertedContact = SupabaseProvider.client.postgrest["emergency_contacts"]
                                        .insert(contactInsert) { select() }
                                        .decodeSingle<EmergencyContact>()
                                    contacts = contacts + insertedContact
                                    showAddDialog = false
                                    newName = ""; newPhone = ""; newRelation = ""
                                } catch (e: Exception) {
                                    android.util.Log.e("ContactsScreen", "Error adding contact", e)
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RedPrimary)
                ) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Emergency Contacts", fontWeight = FontWeight.Bold) },
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
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = RedPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Contact")
            }
        },
        containerColor = DarkBackground
    ) { padding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = RedPrimary)
            }
        } else if (contacts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📞", fontSize = 48.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("No emergency contacts yet", color = TextSecondary)
                    Text("Tap + to add your first contact", fontSize = 12.sp, color = TextMuted)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(contacts) { contact ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = CardBackground)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                tint = AccentCyan,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(contact.name, fontWeight = FontWeight.Bold, color = TextPrimary)
                                Text(contact.phone, fontSize = 13.sp, color = TextSecondary)
                                if (contact.relationship.isNotBlank()) {
                                    Text(contact.relationship, fontSize = 11.sp, color = TextMuted)
                                }
                            }
                            if (contact.isPcr == true) {
                                AssistChip(
                                    onClick = {},
                                    label = { Text("PCR", fontSize = 10.sp) },
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = DangerRed.copy(alpha = 0.2f),
                                        labelColor = DangerRed
                                    )
                                )
                            }
                            IconButton(onClick = {
                                scope.launch {
                                    try {
                                        SupabaseProvider.client.postgrest["emergency_contacts"]
                                            .delete {
                                                filter { eq("id", contact.id) }
                                            }
                                        contacts = contacts.filter { it.id != contact.id }
                                    } catch (_: Exception) {}
                                }
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = DangerRed)
                            }
                        }
                    }
                }
            }
        }
    }
}
