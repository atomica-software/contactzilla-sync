/*
 * Copyright © messageconcept software GmbH, Cologne, Germany.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package com.atomicasoftware.contactzillasync.ui.debug

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atomicasoftware.contactzillasync.ui.AppTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class DebugMdmActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            AppTheme {
                DebugMdmScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugMdmScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("debug_mdm", Context.MODE_PRIVATE) }
    
    var account1BaseUrl by remember { mutableStateOf(prefs.getString("account1_base_url", "https://dav.contactzilla.app/addressbooks/testuser1/spectralink") ?: "") }
    var account1Username by remember { mutableStateOf(prefs.getString("account1_username", "testuser1") ?: "") }
    var account1Password by remember { mutableStateOf(prefs.getString("account1_password", "testpassword1") ?: "") }
    var account1Name by remember { mutableStateOf(prefs.getString("account1_name", "Test Account 1") ?: "") }
    
    var account2BaseUrl by remember { mutableStateOf(prefs.getString("account2_base_url", "https://dav.contactzilla.app/addressbooks/testuser2/spectralink") ?: "") }
    var account2Username by remember { mutableStateOf(prefs.getString("account2_username", "testuser2") ?: "") }
    var account2Password by remember { mutableStateOf(prefs.getString("account2_password", "testpassword2") ?: "") }
    var account2Name by remember { mutableStateOf(prefs.getString("account2_name", "Test Account 2") ?: "") }
    
    var enableAccount2 by remember { mutableStateOf(prefs.getBoolean("enable_account2", false)) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Debug MDM Configuration",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        
        Text(
            text = "Configure test accounts for MDM simulation. Changes require app restart.",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Card {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Account 1 (Primary)",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium
                )
                
                OutlinedTextField(
                    value = account1BaseUrl,
                    onValueChange = { account1BaseUrl = it },
                    label = { Text("Base URL") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                OutlinedTextField(
                    value = account1Username,
                    onValueChange = { account1Username = it },
                    label = { Text("Username") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                OutlinedTextField(
                    value = account1Password,
                    onValueChange = { account1Password = it },
                    label = { Text("Password") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                OutlinedTextField(
                    value = account1Name,
                    onValueChange = { account1Name = it },
                    label = { Text("Account Name") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        
        Card {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Account 2 (Optional)",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    
                    Switch(
                        checked = enableAccount2,
                        onCheckedChange = { enableAccount2 = it }
                    )
                }
                
                if (enableAccount2) {
                    OutlinedTextField(
                        value = account2BaseUrl,
                        onValueChange = { account2BaseUrl = it },
                        label = { Text("Base URL") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    OutlinedTextField(
                        value = account2Username,
                        onValueChange = { account2Username = it },
                        label = { Text("Username") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    OutlinedTextField(
                        value = account2Password,
                        onValueChange = { account2Password = it },
                        label = { Text("Password") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    OutlinedTextField(
                        value = account2Name,
                        onValueChange = { account2Name = it },
                        label = { Text("Account Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(
                onClick = {
                    saveConfiguration(
                        prefs, account1BaseUrl, account1Username, account1Password, account1Name,
                        account2BaseUrl, account2Username, account2Password, account2Name, enableAccount2
                    )
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Save Configuration")
            }
            
            OutlinedButton(
                onClick = {
                    clearConfiguration(prefs)
                    // Reset form
                    account1BaseUrl = ""
                    account1Username = ""
                    account1Password = ""
                    account1Name = ""
                    account2BaseUrl = ""
                    account2Username = ""
                    account2Password = ""
                    account2Name = ""
                    enableAccount2 = false
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Clear All")
            }
        }
        
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Instructions:",
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "1. Configure your test accounts above\n" +
                          "2. Save the configuration\n" +
                          "3. Clear app data or uninstall/reinstall\n" +
                          "4. Launch the app to test auto-creation",
                    fontSize = 14.sp
                )
            }
        }
    }
}

private fun saveConfiguration(
    prefs: SharedPreferences,
    account1BaseUrl: String,
    account1Username: String,
    account1Password: String,
    account1Name: String,
    account2BaseUrl: String,
    account2Username: String,
    account2Password: String,
    account2Name: String,
    enableAccount2: Boolean
) {
    prefs.edit().apply {
        putString("account1_base_url", account1BaseUrl)
        putString("account1_username", account1Username)
        putString("account1_password", account1Password)
        putString("account1_name", account1Name)
        putString("account2_base_url", account2BaseUrl)
        putString("account2_username", account2Username)
        putString("account2_password", account2Password)
        putString("account2_name", account2Name)
        putBoolean("enable_account2", enableAccount2)
        apply()
    }
}

private fun clearConfiguration(prefs: SharedPreferences) {
    prefs.edit().clear().apply()
} 