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
import androidx.credentials.exceptions.NoCredentialException
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.nohex.itur.core.auth.BuildConfig
import com.nohex.itur.core.data.repository.UserRepository
import com.nohex.itur.core.domain.id.UserId
import com.nohex.itur.core.domain.model.User
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject

/**
 * A [com.nohex.itur.core.data.repository.UserRepository] that uses Firebase Authentication.
 */
class FirebaseUserRepository @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    @ApplicationContext private val context: Context,
) : UserRepository {
    private var currentUser: User? = null

    override suspend fun getCurrentUser(): User {
        // Return the current user.
        // If not set, check Firebase Auth.
        return currentUser ?: firebaseAuth.currentUser?.let {
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

    override suspend fun signIn(context: Context): User {
        val credentialManager = CredentialManager.create(context)
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        val result = try {
            credentialManager.getCredential(context = context, request = request)
        } catch (e: NoCredentialException) {
            throw Exception("Google sign-in is unavailable. Make sure a Google account is available in this device and try again.")
        }
        val credential = result.credential

        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
            val firebaseCredential =
                GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
            val authResult = firebaseAuth.signInWithCredential(firebaseCredential).await()

            return authResult.user?.let { firebaseUser ->
                User.RegisteredUser(
                    id = UserId(firebaseUser.uid),
                    name = firebaseUser.displayName,
                    email = firebaseUser.email,
                ).also { currentUser = it }
            } ?: throw IllegalStateException("Authentication succeeded but no user was returned")
        } else {
            throw IllegalStateException("Unexpected credential type: ${credential.type}")
        }
    }

    override suspend fun signOut() {
        firebaseAuth.signOut()
        currentUser = null
    }

    override suspend fun getAll(ids: List<UserId>): List<User> {
        val db = FirebaseFirestore.getInstance()
        val snapshot = db.collection("users")
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
}
