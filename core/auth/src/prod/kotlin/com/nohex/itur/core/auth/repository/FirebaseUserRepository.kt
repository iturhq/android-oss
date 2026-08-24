/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nohex.itur.core.auth.repository

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.exceptions.NoCredentialException
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.nohex.itur.core.auth.config.GoogleSignInConfig
import com.nohex.itur.core.auth.health.reportFirebaseAuthFailed
import com.nohex.itur.core.auth.health.reportFirebaseAuthSucceeded
import com.nohex.itur.core.data.health.BackendHealthReporter
import com.nohex.itur.core.data.repository.FirestoreCollections
import com.nohex.itur.core.data.repository.SignInFailureReason
import com.nohex.itur.core.data.repository.SignInResult
import com.nohex.itur.core.data.repository.UserRepository
import com.nohex.itur.core.domain.id.UserId
import com.nohex.itur.core.domain.model.User
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import kotlin.coroutines.resume

/**
 * A [com.nohex.itur.core.data.repository.UserRepository] that uses Firebase Authentication.
 */
class FirebaseUserRepository @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    @ApplicationContext private val context: Context,
    private val googleSignInConfig: GoogleSignInConfig,
    private val backendHealthReporter: Lazy<BackendHealthReporter>,
) : UserRepository {
    private var currentUser: User? = null

    override suspend fun getCurrentUser(): User {
        // Firebase restores a persisted session asynchronously during process startup. Reading
        // currentUser before its first auth-state callback can transiently return null and make a
        // signed-in organizer look anonymous for the rest of MapViewModel's one-shot restore.
        // The listener is guaranteed an initial callback, including for a genuinely signed-out
        // session, so wait for that authoritative snapshot before choosing the anonymous path.
        currentUser?.let { return it }
        val firebaseUser = awaitInitialAuthState()
        return firebaseUser?.let {
            // There is a Firebase Auth user, return this.
            Log.d("FirebaseUserRepo", "Registered user found: ${it.uid}")
            User.RegisteredUser(
                id = UserId(it.uid),
                name = it.displayName,
                email = it.email,
            )
        } ?: run {
            // There is no Firebase Auth user; retrieve or create an anonymous device ID.
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            val securePrefs = EncryptedSharedPreferences.create(
                context,
                "itur_secure_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
            val deviceId = securePrefs.getString("device_uuid", null)
                ?: run {
                    // Migrate from legacy plain SharedPreferences if a UUID is stored there.
                    val legacyPrefs =
                        context.getSharedPreferences("itur_prefs", Context.MODE_PRIVATE)
                    val id = legacyPrefs.getString("device_uuid", null)
                        ?: UUID.randomUUID().toString()
                    securePrefs.edit { putString("device_uuid", id) }
                    if (legacyPrefs.contains("device_uuid")) {
                        legacyPrefs.edit { remove("device_uuid") }
                    }
                    id
                }

            Log.d("FirebaseUserRepo", "Anonymous user found: $deviceId")
            User.AnonymousUser(id = UserId(deviceId))
        }
    }

    private suspend fun awaitInitialAuthState() = suspendCancellableCoroutine { continuation ->
        lateinit var listener: FirebaseAuth.AuthStateListener
        listener = FirebaseAuth.AuthStateListener { auth ->
            auth.removeAuthStateListener(listener)
            if (continuation.isActive) continuation.resume(auth.currentUser)
        }
        continuation.invokeOnCancellation {
            firebaseAuth.removeAuthStateListener(listener)
        }
        firebaseAuth.addAuthStateListener(listener)
    }

    override suspend fun signIn(context: Context): SignInResult {
        if (googleSignInConfig.webClientId.isBlank()) {
            backendHealthReporter.get().reportFirebaseAuthFailed(SignInFailureReason.NOT_CONFIGURED)
            return SignInResult.Failure(SignInFailureReason.NOT_CONFIGURED)
        }
        val credentialManager = CredentialManager.create(context)
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(googleSignInConfig.webClientId)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        return try {
            val result =
                credentialManager.getCredential(context = context, request = request)
            val credential = result.credential

            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val firebaseCredential =
                    GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
                val authResult = firebaseAuth.signInWithCredential(firebaseCredential).await()

                authResult.user?.let { firebaseUser ->
                    User.RegisteredUser(
                        id = UserId(firebaseUser.uid),
                        name = firebaseUser.displayName,
                        email = firebaseUser.email,
                    ).also { currentUser = it }
                }?.also {
                    backendHealthReporter.get().reportFirebaseAuthSucceeded("Google sign-in completed")
                }?.let(SignInResult::Success)
                    ?: SignInFailureReason.UNEXPECTED.asReportedFailure()
            } else {
                SignInFailureReason.UNEXPECTED.asReportedFailure()
            }
        } catch (_: GetCredentialCancellationException) {
            SignInResult.Cancelled
        } catch (_: NoCredentialException) {
            SignInResult.Failure(SignInFailureReason.NO_ACCOUNT)
        } catch (_: GetCredentialProviderConfigurationException) {
            SignInResult.Failure(SignInFailureReason.NOT_CONFIGURED)
        } catch (_: GetCredentialUnsupportedException) {
            SignInResult.Failure(SignInFailureReason.NOT_CONFIGURED)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: FirebaseNetworkException) {
            SignInFailureReason.SERVICE_UNAVAILABLE.asReportedFailure()
        } catch (failure: FirebaseAuthException) {
            failure.toSignInFailureReason().asReportedFailure()
        } catch (_: Exception) {
            SignInFailureReason.UNEXPECTED.asReportedFailure()
        }
    }

    override suspend fun signOut() {
        try {
            firebaseAuth.signOut()
            currentUser = null
            backendHealthReporter.get().reportFirebaseAuthSucceeded("Sign-out completed")
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            backendHealthReporter.get().reportFirebaseAuthFailed(SignInFailureReason.UNEXPECTED)
            throw failure
        }
    }

    override suspend fun getAll(ids: List<UserId>): List<User> {
        val db = FirebaseFirestore.getInstance()
        val snapshot = db.collection(FirestoreCollections.USERS)
            .whereIn(FieldPath.documentId(), ids.map { it.value })
            .get()
            .await()

        return snapshot.documents.mapNotNull { doc ->
            val id = UserId(doc.id)
            val name = doc.getString("name")
            val email = doc.getString("email")

            // All users from Firebase are registered.
            User.RegisteredUser(id, name, email)
        }
    }

    private fun SignInFailureReason.asReportedFailure(): SignInResult.Failure {
        backendHealthReporter.get().reportFirebaseAuthFailed(this)
        return SignInResult.Failure(this)
    }
}

private fun FirebaseAuthException.toSignInFailureReason(): SignInFailureReason = if (errorCode in CONFIGURATION_ERROR_CODES) {
    SignInFailureReason.NOT_CONFIGURED
} else {
    SignInFailureReason.UNEXPECTED
}

private val CONFIGURATION_ERROR_CODES = setOf(
    "ERROR_APP_NOT_AUTHORIZED",
    "ERROR_API_NOT_AVAILABLE",
    "ERROR_INVALID_CREDENTIAL",
)
