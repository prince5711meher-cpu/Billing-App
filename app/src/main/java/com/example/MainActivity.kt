package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.attemptAutoSignIn
import com.example.ui.navigation.AppNavigation
import com.example.ui.theme.BillingAppTheme
import com.example.ui.update.MandatoryUpdateScreen
import com.example.ui.update.OptionalUpdateDialog
import com.example.ui.update.UpdateViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth

class MainActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    setContent {
      BillingAppTheme {
        Surface(
          modifier = Modifier.fillMaxSize(),
          color = MaterialTheme.colorScheme.background
        ) {
          AppRoot()
        }
      }
    }
  }
}

@Composable
fun AppRoot(
  updateViewModel: UpdateViewModel = viewModel()
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val coroutineScope = rememberCoroutineScope()
  val credentialManager = remember { CredentialManager.create(context) }

  val updateUiState by updateViewModel.uiState.collectAsStateWithLifecycle()
  var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }

  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        updateViewModel.refreshInstalledVersionAndPermission()
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose {
      lifecycleOwner.lifecycle.removeObserver(observer)
    }
  }

  DisposableEffect(Unit) {
    val authListener = FirebaseAuth.AuthStateListener { auth ->
      currentUser = auth.currentUser
    }
    Firebase.auth.addAuthStateListener(authListener)
    onDispose {
      Firebase.auth.removeAuthStateListener(authListener)
    }
  }

  // Attempt silent auto-sign-in on app start if not already signed in
  LaunchedEffect(Unit) {
    if (currentUser == null) {
      attemptAutoSignIn(
        context = context,
        credentialManager = credentialManager,
        onAuthSuccess = { currentUser = Firebase.auth.currentUser },
        onUnauthenticated = { /* Keep on AuthScreen */ },
        scope = coroutineScope
      )
    }
  }

  if (updateUiState.isMandatoryBlocked) {
    MandatoryUpdateScreen(
      uiState = updateUiState,
      onRetryCheck = { updateViewModel.checkForUpdatesManual() },
      onUpdateNow = { updateViewModel.startDownload(autoInstallWhenReady = true) },
      onCancelDownload = { updateViewModel.cancelDownload() },
      onInstallUpdate = { updateViewModel.installVerifiedUpdate() },
      onOpenInstallPermission = { updateViewModel.openUnknownSourcesSettings() }
    )
    return
  }

  if (currentUser == null) {
    AuthScreen(
      onAuthSuccess = {
        currentUser = Firebase.auth.currentUser
      }
    )
  } else {
    val uid = currentUser!!.uid
    androidx.compose.runtime.key(uid) {
      AppNavigation(
        onSignOut = {
          currentUser = null
        },
        updateViewModel = updateViewModel
      )
    }
  }

  if (updateUiState.showOptionalPrompt) {
    OptionalUpdateDialog(
      uiState = updateUiState,
      onDismissLater = { updateViewModel.dismissOptionalPrompt() },
      onUpdateNow = { updateViewModel.startDownload(autoInstallWhenReady = true) },
      onCancelDownload = { updateViewModel.cancelDownload() },
      onInstallUpdate = { updateViewModel.installVerifiedUpdate() },
      onOpenInstallPermission = { updateViewModel.openUnknownSourcesSettings() }
    )
  }
}
