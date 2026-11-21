package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.audio.VoiceAudioManager
import com.example.auth.signOut
import com.example.crypto.VaultCryptoManager
import com.example.data.VaultRepository
import com.example.ui.AuthScreen
import com.example.ui.VaultHomeScreen
import com.example.ui.VaultViewModel
import com.example.ui.theme.NoteBankTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      var isDarkTheme by rememberSaveable { mutableStateOf(true) }
      NoteBankTheme(darkTheme = isDarkTheme) {
        NoteBankAppContent(
          isDarkTheme = isDarkTheme,
          onToggleTheme = { isDarkTheme = it },
        )
      }
    }
  }
}

@Composable
fun NoteBankAppContent(
  isDarkTheme: Boolean,
  onToggleTheme: (Boolean) -> Unit,
) {
  var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }

  DisposableEffect(Unit) {
    val listener = FirebaseAuth.AuthStateListener { auth -> currentUser = auth.currentUser }
    Firebase.auth.addAuthStateListener(listener)
    onDispose { Firebase.auth.removeAuthStateListener(listener) }
  }

  val user = currentUser
  if (user == null) {
    AuthScreen(onAuthSuccess = { currentUser = Firebase.auth.currentUser })
  } else {
    val uid = user.uid
    key(uid) {
      val context = LocalContext.current
      val scope = rememberCoroutineScope()
      val credentialManager = remember(context) { CredentialManager.create(context) }

      val viewModel: VaultViewModel =
        viewModel(
          key = uid,
          factory =
            viewModelFactory {
              initializer {
                val app =
                  checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                  }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                val repository = VaultRepository(db)
                val cryptoManager = VaultCryptoManager(app, uid)
                val audioManager = VoiceAudioManager(app)
                VaultViewModel(
                  repository = repository,
                  cryptoManager = cryptoManager,
                  audioManager = audioManager,
                  currentUserId = uid,
                )
              }
            },
        )

      VaultHomeScreen(
        viewModel = viewModel,
        userEmail = user.email,
        userName = user.displayName,
        isDarkTheme = isDarkTheme,
        onToggleTheme = onToggleTheme,
        onSignOut = {
          signOut(
            credentialManager = credentialManager,
            scope = scope,
            onSignOutComplete = { currentUser = null },
          )
        },
      )
    }
  }
}
