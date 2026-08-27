package com.example.spredrop.data.firebase

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialException
import com.example.spredrop.data.local.SpreDropDatabase
import com.example.spredrop.model.AuthenticatedAccount
import com.example.spredrop.model.PrivacyMode
import com.example.spredrop.model.UserAccount
import com.example.spredrop.model.UserPresence
import com.example.spredrop.model.UserProfile
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest
import java.util.UUID

data class FirebaseConfig(
    val projectId: String = "spredrop",
    val apiKey: String = "AIzaSyBehu2ei4kWs3L89UJhwGlsq0wvmi-_lkg",
    val appId: String = "1:947368133167:android:7966e7ee9812d68f8fbcda",
    val isCustomConfigured: Boolean = true,
    val isOnlineSyncReady: Boolean = true
)

sealed interface AuthState {
    data object Idle : AuthState
    data object Loading : AuthState
    data class Authenticated(val user: AuthenticatedAccount) : AuthState
    data object SignedOut : AuthState
    data class Error(val message: String) : AuthState
}

class FirebaseAuthManager(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("spredrop_auth_prefs", Context.MODE_PRIVATE)
    private val db = SpreDropDatabase.getInstance(context)
    private val userDao = db.userDao()
    private val authScope = CoroutineScope(Dispatchers.IO)

    private val auth: FirebaseAuth? by lazy {
        try {
            FirebaseAuth.getInstance()
        } catch (e: Exception) {
            null
        }
    }
    private val credentialManager: CredentialManager by lazy { CredentialManager.create(context) }

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    val currentUser: AuthenticatedAccount?
        get() = (_authState.value as? AuthState.Authenticated)?.user

    init {
        restoreSession()
    }

    fun updateActiveSession(displayName: String, spreDropId: String) {
        val current = currentUser ?: return
        val updated = current.copy(displayName = displayName)
        saveSession(current.uid, current.email, displayName)
        _authState.value = AuthState.Authenticated(updated)
    }

    private fun isFirebaseConfigured(): Boolean {
        return try {
            val key = auth?.app?.options?.apiKey
            !key.isNullOrBlank() && !key.contains("Dummy", ignoreCase = true) && !key.contains("placeholder", ignoreCase = true)
        } catch (_: Exception) {
            false
        }
    }

    private fun restoreSession() {
        val savedUid = prefs.getString(PREF_ACTIVE_UID, null)
        val savedEmail = prefs.getString(PREF_ACTIVE_EMAIL, null)
        val savedDisplayName = prefs.getString(PREF_ACTIVE_NAME, null)

        if (!savedUid.isNullOrBlank()) {
            _authState.value = AuthState.Authenticated(
                AuthenticatedAccount(
                    uid = savedUid,
                    email = savedEmail,
                    displayName = savedDisplayName
                )
            )
        } else if (isFirebaseConfigured()) {
            val fbUser = try { auth?.currentUser } catch (_: Exception) { null }
            if (fbUser != null) {
                _authState.value = AuthState.Authenticated(
                    AuthenticatedAccount(
                        uid = fbUser.uid,
                        email = fbUser.email,
                        displayName = fbUser.displayName,
                        photoUrl = fbUser.photoUrl?.toString()
                    )
                )
                saveSession(fbUser.uid, fbUser.email, fbUser.displayName)
            } else {
                _authState.value = AuthState.SignedOut
            }
        } else {
            _authState.value = AuthState.SignedOut
        }
    }

    private fun saveSession(uid: String, email: String?, displayName: String?) {
        prefs.edit()
            .putString(PREF_ACTIVE_UID, uid)
            .putString(PREF_ACTIVE_EMAIL, email)
            .putString(PREF_ACTIVE_NAME, displayName)
            .apply()
    }

    private fun clearSession() {
        prefs.edit().clear().apply()
    }

    private fun hashPassword(password: String): String {
        val bytes = password.toByteArray()
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.fold("") { str, it -> str + "%02x".format(it) }
    }

    suspend fun signInWithEmail(email: String, pass: String): Result<AuthenticatedAccount> {
        val cleanEmail = email.trim().lowercase()
        if (cleanEmail.isBlank() || !cleanEmail.contains("@")) {
            val msg = "Please enter a valid email address."
            _authState.value = AuthState.Error(msg)
            return Result.failure(IllegalArgumentException(msg))
        }
        if (pass.length < 6) {
            val msg = "Password must be at least 6 characters."
            _authState.value = AuthState.Error(msg)
            return Result.failure(IllegalArgumentException(msg))
        }

        _authState.value = AuthState.Loading

        // 1. Check local account database
        val localAccount = userDao.getAccountByEmail(cleanEmail)
        val inputHash = hashPassword(pass)

        if (localAccount != null) {
            if (localAccount.passwordHash == inputHash) {
                val authenticated = AuthenticatedAccount(
                    uid = localAccount.userId,
                    email = localAccount.email,
                    displayName = localAccount.displayName
                )
                saveSession(authenticated.uid, authenticated.email, authenticated.displayName)
                _authState.value = AuthState.Authenticated(authenticated)

                // Update or create user profile
                val existingProfile = userDao.getUserProfileOnce()
                if (existingProfile == null || existingProfile.userId != localAccount.userId) {
                    val profile = UserProfile(
                        userId = localAccount.userId,
                        spreDropId = localAccount.spreDropId,
                        displayName = localAccount.displayName,
                        deviceModel = "${android.os.Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${android.os.Build.MODEL}",
                        createdAt = localAccount.createdAt,
                        lastSeen = System.currentTimeMillis()
                    )
                    userDao.insertOrUpdateProfile(profile)
                }

                // Attempt Firebase background sign in if configured
                if (isFirebaseConfigured()) {
                    try {
                        auth?.signInWithEmailAndPassword(cleanEmail, pass)?.await()
                    } catch (e: Exception) {
                        Log.d(TAG, "Firebase cloud sync optional note: ${e.message}")
                    }
                }

                return Result.success(authenticated)
            } else {
                val msg = "Incorrect password. Please try again."
                _authState.value = AuthState.Error(msg)
                return Result.failure(IllegalArgumentException(msg))
            }
        }

        // 2. Try Firebase Auth if local account wasn't registered yet and Firebase is configured
        val authInstance = auth
        if (authInstance != null && isFirebaseConfigured()) {
            try {
                val result = authInstance.signInWithEmailAndPassword(cleanEmail, pass).await()
                val user = result.user
                if (user != null) {
                    val handle = "@" + cleanEmail.substringBefore("@").replace(".", "_")
                    val authenticated = AuthenticatedAccount(
                        uid = user.uid,
                        email = user.email ?: cleanEmail,
                        displayName = user.displayName ?: handle,
                        photoUrl = user.photoUrl?.toString()
                    )
                    // Store account locally for offline resilience
                    val account = UserAccount(
                        email = cleanEmail,
                        passwordHash = inputHash,
                        userId = user.uid,
                        spreDropId = handle,
                        displayName = user.displayName ?: handle
                    )
                    userDao.insertAccount(account)
                    saveSession(authenticated.uid, authenticated.email, authenticated.displayName)
                    _authState.value = AuthState.Authenticated(authenticated)
                    return Result.success(authenticated)
                }
            } catch (e: Exception) {
                Log.d(TAG, "Firebase Auth direct login error: ${e.message}")
            }
        }

        val errorMsg = "No account found with this email. Please switch to the 'Sign Up' tab to create an account."
        _authState.value = AuthState.Error(errorMsg)
        return Result.failure(IllegalArgumentException(errorMsg))
    }

    suspend fun signUpWithEmail(
        email: String,
        pass: String,
        displayName: String,
        spreDropId: String
    ): Result<AuthenticatedAccount> {
        val cleanEmail = email.trim().lowercase()
        val cleanName = displayName.trim().ifBlank { "User" }
        val rawHandle = spreDropId.trim().lowercase().removePrefix("@")
        val cleanHandle = "@" + (if (rawHandle.isNotBlank()) rawHandle else cleanEmail.substringBefore("@").lowercase().replace(".", "_"))

        if (cleanEmail.isBlank() || !cleanEmail.contains("@")) {
            val msg = "Please enter a valid email address."
            _authState.value = AuthState.Error(msg)
            return Result.failure(IllegalArgumentException(msg))
        }
        if (pass.length < 6) {
            val msg = "Password must be at least 6 characters."
            _authState.value = AuthState.Error(msg)
            return Result.failure(IllegalArgumentException(msg))
        }

        _authState.value = AuthState.Loading

        val existingAccount = userDao.getAccountByEmail(cleanEmail)
        if (existingAccount != null) {
            val msg = "An account with this email already exists. Please switch to the 'Log In' tab."
            _authState.value = AuthState.Error(msg)
            return Result.failure(IllegalArgumentException(msg))
        }

        var uid = "usr_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        var photoUrl: String? = null

        // Try Firebase account creation only if configured
        val authInstance = auth
        if (authInstance != null && isFirebaseConfigured()) {
            try {
                val result = authInstance.createUserWithEmailAndPassword(cleanEmail, pass).await()
                val user = result.user
                if (user != null) {
                    uid = user.uid
                    photoUrl = user.photoUrl?.toString()
                    try {
                        val profileUpdates = UserProfileChangeRequest.Builder()
                            .setDisplayName(cleanName)
                            .build()
                        user.updateProfile(profileUpdates).await()
                    } catch (_: Exception) {}
                }
            } catch (e: Exception) {
                Log.d(TAG, "Firebase account registration notice: ${e.message}")
            }
        }

        // Save local registered account
        val passHash = hashPassword(pass)
        val newAccount = UserAccount(
            email = cleanEmail,
            passwordHash = passHash,
            userId = uid,
            spreDropId = cleanHandle,
            displayName = cleanName
        )
        userDao.insertAccount(newAccount)

        // Create initial user profile
        val profile = UserProfile(
            userId = uid,
            spreDropId = cleanHandle,
            displayName = cleanName,
            profilePhotoUri = photoUrl,
            avatarColorHex = "#00B4D8",
            visibility = PrivacyMode.VISIBLE,
            availability = UserPresence.AVAILABLE,
            deviceModel = "${android.os.Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${android.os.Build.MODEL}",
            createdAt = System.currentTimeMillis(),
            lastSeen = System.currentTimeMillis()
        )
        userDao.insertOrUpdateProfile(profile)

        val authenticated = AuthenticatedAccount(
            uid = uid,
            email = cleanEmail,
            displayName = cleanName,
            photoUrl = photoUrl
        )
        saveSession(authenticated.uid, authenticated.email, authenticated.displayName)
        _authState.value = AuthState.Authenticated(authenticated)

        return Result.success(authenticated)
    }

    suspend fun signInWithGoogle(webClientId: String? = null): Result<AuthenticatedAccount> {
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
                val email = googleIdTokenCredential.id
                val displayName = googleIdTokenCredential.displayName ?: email.substringBefore("@")
                val photoUrl = googleIdTokenCredential.profilePictureUri?.toString()
                val uid = "google_${UUID.nameUUIDFromBytes(email.toByteArray()).toString().take(12)}"
                val handle = "@" + email.substringBefore("@").replace(".", "_")

                // Save or update local account
                val account = UserAccount(
                    email = email.lowercase(),
                    passwordHash = hashPassword(UUID.randomUUID().toString()),
                    userId = uid,
                    spreDropId = handle,
                    displayName = displayName
                )
                userDao.insertAccount(account)

                val profile = UserProfile(
                    userId = uid,
                    spreDropId = handle,
                    displayName = displayName,
                    profilePhotoUri = photoUrl,
                    deviceModel = "${android.os.Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${android.os.Build.MODEL}",
                    createdAt = System.currentTimeMillis(),
                    lastSeen = System.currentTimeMillis()
                )
                userDao.insertOrUpdateProfile(profile)

                val authenticated = AuthenticatedAccount(
                    uid = uid,
                    email = email,
                    displayName = displayName,
                    photoUrl = photoUrl
                )
                saveSession(authenticated.uid, authenticated.email, authenticated.displayName)
                _authState.value = AuthState.Authenticated(authenticated)

                // Also link with Firebase if configured
                val authInstance = auth
                if (authInstance != null && isFirebaseConfigured()) {
                    try {
                        val authCredential = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
                        authInstance.signInWithCredential(authCredential).await()
                    } catch (_: Exception) {}
                }

                Result.success(authenticated)
            } else {
                throw IllegalStateException("Unexpected credential type")
            }
        } catch (e: GetCredentialException) {
            val errorDetails = e.localizedMessage ?: e.message ?: ""
            val userFriendlyMsg = if (errorDetails.contains("No credentials", ignoreCase = true) || errorDetails.contains("28433") || errorDetails.contains("type_no_credential", ignoreCase = true)) {
                "No Google accounts found on this device. Please create an account or sign in with Email & Password."
            } else if (errorDetails.contains("cancelled", ignoreCase = true) || errorDetails.contains("canceled", ignoreCase = true) || errorDetails.contains("28434")) {
                "Google sign-in was cancelled."
            } else {
                "Google Sign-In unavailable. Please sign in or register with Email & Password."
            }
            _authState.value = AuthState.Error(userFriendlyMsg)
            Result.failure(e)
        } catch (e: Exception) {
            val errorDetails = e.localizedMessage ?: e.message ?: ""
            val userFriendlyMsg = "Google Sign-In note: $errorDetails. Please use Email & Password."
            _authState.value = AuthState.Error(userFriendlyMsg)
            Result.failure(e)
        }
    }

    suspend fun sendPasswordReset(email: String): Result<Unit> {
        val cleanEmail = email.trim().lowercase()
        if (cleanEmail.isBlank() || !cleanEmail.contains("@")) {
            return Result.failure(IllegalArgumentException("Please enter a valid email address."))
        }

        val account = userDao.getAccountByEmail(cleanEmail)
        if (account != null) {
            // Account verified locally
            if (isFirebaseConfigured()) {
                try {
                    auth?.sendPasswordResetEmail(cleanEmail)?.await()
                } catch (_: Exception) {}
            }
            return Result.success(Unit)
        }

        val authInstance = auth
        if (authInstance != null && isFirebaseConfigured()) {
            return try {
                authInstance.sendPasswordResetEmail(cleanEmail).await()
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

        return Result.failure(IllegalArgumentException("No account registered with this email address."))
    }

    suspend fun resetPasswordWithNew(email: String, newPass: String): Result<Unit> {
        val cleanEmail = email.trim().lowercase()
        if (newPass.length < 6) {
            return Result.failure(IllegalArgumentException("Password must be at least 6 characters."))
        }
        val account = userDao.getAccountByEmail(cleanEmail)
            ?: return Result.failure(IllegalArgumentException("Account not found for $cleanEmail"))

        val newHash = hashPassword(newPass)
        userDao.updatePasswordHash(cleanEmail, newHash)
        return Result.success(Unit)
    }

    fun getFirebaseConfig(): FirebaseConfig {
        val savedProjectId = prefs.getString(PREF_CUSTOM_PROJECT_ID, "spredrop") ?: "spredrop"
        val savedApiKey = prefs.getString(PREF_CUSTOM_API_KEY, "AIzaSyBehu2ei4kWs3L89UJhwGlsq0wvmi-_lkg") ?: "AIzaSyBehu2ei4kWs3L89UJhwGlsq0wvmi-_lkg"
        val savedAppId = prefs.getString(PREF_CUSTOM_APP_ID, "1:947368133167:android:7966e7ee9812d68f8fbcda") ?: "1:947368133167:android:7966e7ee9812d68f8fbcda"
        val isCustom = savedApiKey.isNotBlank() && !savedApiKey.contains("Dummy", ignoreCase = true)
        return FirebaseConfig(
            projectId = savedProjectId,
            apiKey = savedApiKey,
            appId = savedAppId,
            isCustomConfigured = isCustom,
            isOnlineSyncReady = true
        )
    }

    suspend fun updateFirebaseConfig(projectId: String, apiKey: String, appId: String): Result<Unit> {
        return try {
            val cleanProjectId = projectId.trim().ifBlank { "spredrop" }
            val cleanApiKey = apiKey.trim()
            val cleanAppId = appId.trim().ifBlank { "1:947368133167:android:7966e7ee9812d68f8fbcda" }

            prefs.edit()
                .putString(PREF_CUSTOM_PROJECT_ID, cleanProjectId)
                .putString(PREF_CUSTOM_API_KEY, cleanApiKey)
                .putString(PREF_CUSTOM_APP_ID, cleanAppId)
                .apply()

            if (cleanApiKey.isNotBlank()) {
                val options = FirebaseOptions.Builder()
                    .setApiKey(cleanApiKey)
                    .setProjectId(cleanProjectId)
                    .setApplicationId(cleanAppId)
                    .setStorageBucket("$cleanProjectId.firebasestorage.app")
                    .build()

                try {
                    val app = FirebaseApp.getInstance()
                    app.delete()
                } catch (_: Exception) {}

                FirebaseApp.initializeApp(context, options)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update Firebase configuration", e)
            Result.failure(e)
        }
    }

    suspend fun signOut(): Result<Unit> {
        clearSession()
        try {
            auth?.signOut()
        } catch (_: Exception) {}
        try {
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
        } catch (_: Exception) {}
        _authState.value = AuthState.SignedOut
        return Result.success(Unit)
    }

    fun clearError() {
        if (_authState.value is AuthState.Error) {
            restoreSession()
        }
    }

    companion object {
        private const val TAG = "FirebaseAuthManager"
        private const val PREF_ACTIVE_UID = "active_uid"
        private const val PREF_ACTIVE_EMAIL = "active_email"
        private const val PREF_ACTIVE_NAME = "active_name"
        private const val PREF_CUSTOM_PROJECT_ID = "custom_firebase_project_id"
        private const val PREF_CUSTOM_API_KEY = "custom_firebase_api_key"
        private const val PREF_CUSTOM_APP_ID = "custom_firebase_app_id"
    }
}

