package com.example.ui.screens

import android.content.Context
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import com.example.ui.auth.signOutUser
import com.example.ui.theme.BorderLight
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.SurfaceLight
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarningOrange
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
  viewModel: SettingsViewModel,
  onSignOut: () -> Unit
) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()
  val snackbarHostState = remember { SnackbarHostState() }
  val settings by viewModel.settingsState.collectAsState()
  val isSaving by viewModel.isSaving.collectAsState()

  var businessName by remember(settings) { mutableStateOf(settings.businessName) }
  var address by remember(settings) { mutableStateOf(settings.address) }
  var phone by remember(settings) { mutableStateOf(settings.phone) }
  var email by remember(settings) { mutableStateOf(settings.email) }
  var invoicePrefix by remember(settings) { mutableStateOf(settings.invoicePrefix) }
  var terms by remember(settings) { mutableStateOf(settings.termsAndConditions) }

  val currentUser = Firebase.auth.currentUser

  Scaffold(
    snackbarHost = { SnackbarHost(snackbarHostState) }
  ) { paddingValues ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
        .padding(horizontal = 16.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      item {
        Text(
          text = "Settings & Shop Profile",
          style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
        )
        Text(
          text = "Configure store details, invoice prefix, and cloud connection",
          style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
        )
      }

      // Business Profile Card
      item {
        Card(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(14.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
          elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Text("Store Details", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))

            OutlinedTextField(
              value = businessName,
              onValueChange = { businessName = it },
              label = { Text("Shop Commercial Name *") },
              modifier = Modifier.fillMaxWidth(),
              singleLine = true
            )

            OutlinedTextField(
              value = address,
              onValueChange = { address = it },
              label = { Text("Store Address / Market") },
              modifier = Modifier.fillMaxWidth()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text("Contact Phone") },
                modifier = Modifier.weight(1f),
                singleLine = true
              )
              OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                modifier = Modifier.weight(1f),
                singleLine = true
              )
            }

            OutlinedTextField(
              value = invoicePrefix,
              onValueChange = { invoicePrefix = it },
              label = { Text("Invoice Prefix (e.g. BILL)") },
              modifier = Modifier.fillMaxWidth(),
              singleLine = true
            )

            OutlinedTextField(
              value = terms,
              onValueChange = { terms = it },
              label = { Text("Invoice Footer Terms") },
              modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(6.dp))

            Button(
              onClick = {
                viewModel.saveSettings(
                  businessName = businessName,
                  address = address,
                  phone = phone,
                  email = email,
                  invoicePrefix = invoicePrefix,
                  terms = terms,
                  onSuccess = {
                    coroutineScope.launch { snackbarHostState.showSnackbar("Shop profile updated successfully") }
                  },
                  onError = { msg ->
                    coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
                  }
                )
              },
              enabled = !isSaving,
              modifier = Modifier.fillMaxWidth().height(46.dp),
              shape = RoundedCornerShape(10.dp),
              colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
            ) {
              if (isSaving) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = SurfaceLight)
              } else {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Save Profile", fontWeight = FontWeight.SemiBold)
              }
            }
          }
        }
      }

      // Cloud Synchronization & Account Card
      item {
        Card(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(14.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
          elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
          Column(modifier = Modifier.padding(16.dp)) {
            Text("Cloud Synchronization", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
            Spacer(modifier = Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
              Box(
                modifier = Modifier
                  .size(36.dp)
                  .background(SuccessGreen.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
              ) {
                Icon(Icons.Default.CloudDone, contentDescription = null, tint = SuccessGreen)
              }
              Spacer(modifier = Modifier.width(12.dp))
              Column {
                Text("Firebase Cloud Backup Active", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(currentUser?.email ?: "Signed in via Google", fontSize = 12.sp, color = TextSecondary)
              }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
              text = "All bills, udhar dues, payments, and store settings are automatically backed up to this Google Account. If you uninstall or switch devices, simply sign in with this same Google Account to restore everything.",
              fontSize = 12.sp,
              color = TextSecondary,
              lineHeight = 17.sp
            )

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = BorderLight)
            Spacer(modifier = Modifier.height(14.dp))

            OutlinedButton(
              onClick = {
                val credentialManager = CredentialManager.create(context)
                signOutUser(
                  context = context,
                  credentialManager = credentialManager,
                  onSignOutComplete = onSignOut,
                  scope = coroutineScope
                )
              },
              modifier = Modifier.fillMaxWidth().height(46.dp),
              shape = RoundedCornerShape(10.dp)
            ) {
              Icon(Icons.Default.Logout, contentDescription = null, tint = WarningOrange, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Sign Out of Account", color = WarningOrange)
            }
          }
        }
      }

      item {
        Spacer(modifier = Modifier.height(40.dp))
      }
    }
  }
}
