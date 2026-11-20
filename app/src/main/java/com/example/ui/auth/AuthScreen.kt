package com.example.ui.auth

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.R
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.SurfaceLight
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Composable
fun AuthScreen(
  onAuthSuccess: () -> Unit
) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()
  val credentialManager = remember { CredentialManager.create(context) }
  var isLoading by remember { mutableStateOf(false) }
  var errorMessage by remember { mutableStateOf<String?>(null) }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
      .padding(24.dp),
    contentAlignment = Alignment.Center
  ) {
    Card(
      modifier = Modifier
        .fillMaxWidth()
        .widthIn(max = 480.dp),
      shape = RoundedCornerShape(20.dp),
      colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surface
      ),
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
      elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
      ) {
        Box(
          modifier = Modifier
            .size(72.dp)
            .clip(RoundedCornerShape(18.dp)),
          contentAlignment = Alignment.Center
        ) {
          Image(
            painter = painterResource(id = R.drawable.meher_logo_badge),
            contentDescription = "Meher Logo",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
          )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
          text = "Meher",
          style = MaterialTheme.typography.headlineMedium.copy(
            fontWeight = FontWeight.Bold,
            color = TextPrimary
          ),
          textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
          text = "Retail Counter Billing & Udhar Ledger",
          style = MaterialTheme.typography.bodyMedium.copy(
            color = TextSecondary
          ),
          textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
          text = "Fast Bills • Item & Overall Discounts • Cloud Backup",
          style = MaterialTheme.typography.labelSmall.copy(
            color = PrimaryBlue,
            fontWeight = FontWeight.SemiBold
          ),
          textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
          onClick = {
            isLoading = true
            errorMessage = null
            onGoogleSignInClicked(
              context = context,
              credentialManager = credentialManager,
              onAuthSuccess = {
                isLoading = false
                onAuthSuccess()
              },
              onAuthError = { msg ->
                isLoading = false
                errorMessage = msg
              },
              onAuthCancelled = {
                isLoading = false
              },
              scope = coroutineScope
            )
          },
          enabled = !isLoading,
          modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .testTag("google_sign_in_button"),
          shape = RoundedCornerShape(12.dp),
          colors = ButtonDefaults.buttonColors(
            containerColor = PrimaryBlue,
            contentColor = SurfaceLight
          )
        ) {
          if (isLoading) {
            CircularProgressIndicator(
              modifier = Modifier.size(24.dp),
              color = SurfaceLight,
              strokeWidth = 2.5.dp
            )
          } else {
            Text(
              text = "Sign in with Google",
              style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.SemiBold
              )
            )
          }
        }

        if (errorMessage != null) {
          Spacer(modifier = Modifier.height(16.dp))
          Text(
            text = errorMessage ?: "",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center
          )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
          text = "Sign in with your Google Account to sync bills, udhar dues, and shop settings. Reinstall or switch phones anytime — sign in with the same Google Account to restore all data.",
          style = MaterialTheme.typography.labelSmall.copy(
            color = TextSecondary,
            lineHeight = 16.sp
          ),
          textAlign = TextAlign.Center
        )
      }
    }
  }
}

fun onGoogleSignInClicked(
  context: Context,
  credentialManager: CredentialManager,
  onAuthSuccess: () -> Unit,
  onAuthError: (String) -> Unit,
  onAuthCancelled: () -> Unit,
  scope: CoroutineScope
) {
  val clientId = try {
    context.getString(R.string.default_web_client_id)
  } catch (e: Exception) {
    onAuthError("Google Sign-In configuration missing: default_web_client_id not found")
    return
  }

  val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
  val request = GetCredentialRequest.Builder().addCredentialOption(signInOption).build()

  scope.launch {
    try {
      val result = credentialManager.getCredential(context as Activity, request)
      val credential = result.credential
      if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
        val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
        Firebase.auth.signInWithCredential(authCredential).await()
        onAuthSuccess()
      } else {
        onAuthError("Unexpected credential type received")
      }
    } catch (e: GetCredentialCancellationException) {
      Log.w("Auth", "Google Sign-In cancelled or dismissed: ${e.message}", e)
      onAuthCancelled()
    } catch (e: Exception) {
      Log.e("Auth", "Google Sign-In failed", e)
      onAuthError(e.localizedMessage ?: "Sign in failed. Please try again.")
    }
  }
}

fun attemptAutoSignIn(
  context: Context,
  credentialManager: CredentialManager,
  onAuthSuccess: () -> Unit,
  onUnauthenticated: () -> Unit,
  scope: CoroutineScope
) {
  if (Firebase.auth.currentUser != null) {
    onAuthSuccess()
    return
  }
  val clientId = try {
    context.getString(R.string.default_web_client_id)
  } catch (e: Exception) {
    onUnauthenticated()
    return
  }

  val googleIdOption = GetGoogleIdOption.Builder()
    .setFilterByAuthorizedAccounts(true)
    .setServerClientId(clientId)
    .setAutoSelectEnabled(true)
    .build()

  val request = GetCredentialRequest.Builder().addCredentialOption(googleIdOption).build()

  scope.launch {
    try {
      val result = credentialManager.getCredential(context, request)
      val credential = result.credential
      if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
        val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
        Firebase.auth.signInWithCredential(authCredential).await()
        onAuthSuccess()
      } else {
        onUnauthenticated()
      }
    } catch (e: Exception) {
      onUnauthenticated()
    }
  }
}

fun signOutUser(
  context: Context,
  credentialManager: CredentialManager,
  onSignOutComplete: () -> Unit,
  scope: CoroutineScope
) {
  Firebase.auth.signOut()
  scope.launch {
    try {
      credentialManager.clearCredentialState(ClearCredentialStateRequest())
    } catch (e: Exception) {
      Log.e("Auth", "Failed to clear credential state", e)
    } finally {
      onSignOutComplete()
    }
  }
}
