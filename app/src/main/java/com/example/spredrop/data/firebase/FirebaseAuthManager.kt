package com.example.spredrop.data.firebase

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialException
import com.example.spredrop.model.UserProfile
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest
import java.util.UUID

sealed interface AuthState {
    data object Idle : AuthState
    data object Loading : AuthState
    data class Authenticated(val user: FirebaseUser) : AuthState
    data object SignedOut : AuthState
    data class Error(val message: String) : AuthState
}

class FirebaseAuthManager(private val context: Context) {

    private val auth: FirebaseAuth? by lazy {
        try {
            FirebaseAuth.getInstance()
        } catch (e: Exception) {
            null
        }
    }
    private val credentialManager: CredentialManager by lazy { CredentialManager.create(context) }

    private val _authState = MutableStateFlow<AuthState>(
        try {
            val user = auth?.currentUser
            if (user != null) AuthState.Authenticated(user) else AuthState.SignedOut
        } catch (_: Exception) {
            AuthState.SignedOut
        }
    )
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    val currentUser: FirebaseUser?
        get() = try { auth?.currentUser } catch (_: Exception) { null }

    val userFlow: Flow<FirebaseUser?> = callbackFlow {
        val authInstance = auth
        if (authInstance == null) {
            trySend(null)
            awaitClose { }
            return@callbackFlow
        }
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            trySend(user)
            if (user != null) {
                _authState.value = AuthState.Authenticated(user)
            } else {
                _authState.value = AuthState.SignedOut
            }
        }
        authInstance.addAuthStateListener(listener)
        awaitClose {
            authInstance.removeAuthStateListener(listener)
        }
    }

    suspend fun signInWithEmail(email: String, pass: String): Result<FirebaseUser> {
        val authInstance = auth ?: return Result.failure(IllegalStateException("Firebase Auth not initialized"))
        _authState.value = AuthState.Loading
        return try {
            val result = authInstance.signInWithEmailAndPassword(email.trim(), pass).await()
            val user = result.user ?: throw IllegalStateException("User not found after sign in")
            _authState.value = AuthState.Authenticated(user)
            Result.success(user)
        } catch (e: Exception) {
            val errorMsg = e.localizedMessage ?: "Failed to sign in with email"
            _authState.value = AuthState.Error(errorMsg)
            Result.failure(e)
        }
    }

    suspend fun signUpWithEmail(
        email: String,
        pass: String,
        displayName: String,
        spreDropId: String
    ): Result<FirebaseUser> {
        val authInstance = auth ?: return Result.failure(IllegalStateException("Firebase Auth not initialized"))
        _authState.value = AuthState.Loading
        return try {
            val result = authInstance.createUserWithEmailAndPassword(email.trim(), pass).await()
            val user = result.user ?: throw IllegalStateException("User creation failed")

            // Update display name
            val profileUpdates = UserProfileChangeRequest.Builder()
                .setDisplayName(displayName.ifBlank { spreDropId })
                .build()
            user.updateProfile(profileUpdates).await()

            _authState.value = AuthState.Authenticated(user)
            Result.success(user)
        } catch (e: Exception) {
            val errorMsg = e.localizedMessage ?: "Failed to create account"
            _authState.value = AuthState.Error(errorMsg)
            Result.failure(e)
        }
    }

    suspend fun signInWithGoogle(webClientId: String? = null): Result<FirebaseUser> {
        val authInstance = auth ?: return Result.failure(IllegalStateException("Firebase Auth not initialized"))
        _authState.value = AuthState.Loading
        return try {
            val rawNonce = UUID.randomUUID().toString()
            val bytes = rawNonce.toByteArray()
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(bytes)
            val hashedNonce = digest.fold("") { str, it -> str + "%02x".format(it) }

            val clientId = webClientId ?: "842454263775.apps.googleusercontent.com"

            val googleIdOption: GetGoogleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(clientId)
                .setAutoSelectEnabled(false)
                .setNonce(hashedNonce)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val response: GetCredentialResponse = credentialManager.getCredential(
                request = request,
                context = context
            )

            val credential = response.credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val idToken = googleIdTokenCredential.idToken
                val authCredential = GoogleAuthProvider.getCredential(idToken, null)
                val authResult = authInstance.signInWithCredential(authCredential).await()
                val user = authResult.user ?: throw IllegalStateException("Firebase user was null")
                _authState.value = AuthState.Authenticated(user)
                Result.success(user)
            } else {
                throw IllegalStateException("Unexpected credential type: ${credential::class.java.name}")
            }
        } catch (e: GetCredentialException) {
            val errorDetails = e.localizedMessage ?: e.message ?: ""
            val userFriendlyMsg = if (errorDetails.contains("No credentials", ignoreCase = true) || errorDetails.contains("28433") || errorDetails.contains("type_no_credential", ignoreCase = true)) {
                "No Google accounts found on this device. Please sign in or register with Email & Password above, or add a Google Account in device Settings."
            } else if (errorDetails.contains("cancelled", ignoreCase = true) || errorDetails.contains("canceled", ignoreCase = true) || errorDetails.contains("28434")) {
                "Google sign-in was cancelled."
            } else {
                "Google Sign-In note: $errorDetails. You can sign in with Email & Password."
            }
            _authState.value = AuthState.Error(userFriendlyMsg)
            Result.failure(e)
        } catch (e: Exception) {
            val errorDetails = e.localizedMessage ?: e.message ?: ""
            val userFriendlyMsg = if (errorDetails.contains("No credentials", ignoreCase = true)) {
                "No Google credentials available on this device. Please sign up or sign in with Email & Password."
            } else {
                "Google Sign-In: $errorDetails"
            }
            _authState.value = AuthState.Error(userFriendlyMsg)
            Result.failure(e)
        }
    }

    suspend fun sendPasswordReset(email: String): Result<Unit> {
        val authInstance = auth ?: return Result.failure(IllegalStateException("Firebase Auth not initialized"))
        return try {
            authInstance.sendPasswordResetEmail(email.trim()).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signOut(): Result<Unit> {
        return try {
            auth?.signOut()
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (_: Exception) {}
            _authState.value = AuthState.SignedOut
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun clearError() {
        if (_authState.value is AuthState.Error) {
            _authState.value = try {
                val user = auth?.currentUser
                if (user != null) AuthState.Authenticated(user) else AuthState.SignedOut
            } catch (_: Exception) {
                AuthState.SignedOut
            }
        }
    }
}
