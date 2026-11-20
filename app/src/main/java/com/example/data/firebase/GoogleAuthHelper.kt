package com.example.data.firebase

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.ClearCredentialException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext

fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        trySend(firebaseAuth.currentUser)
    }
    addAuthStateListener(listener)
    trySend(currentUser)
    awaitClose { removeAuthStateListener(listener) }
}

object GoogleAuthHelper {
    private const val TAG = "Auth"

    suspend fun attemptAutoSignIn(context: Context): Result<FirebaseUser?> {
        val credentialManager = CredentialManager.create(context)
        val autoOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(true)
            .setServerClientId(context.getString(R.string.default_web_client_id))
            .setAutoSelectEnabled(true)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(autoOption)
            .build()

        return try {
            val response = credentialManager.getCredential(context, request)
            val credential = response.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleIdCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val firebaseCredential = GoogleAuthProvider.getCredential(googleIdCredential.idToken, null)
                val result = Firebase.auth.signInWithCredential(firebaseCredential).awaitResult()
                Result.success(result.user)
            } else {
                Result.success(null)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: NoCredentialException) {
            Result.success(null)
        } catch (e: GetCredentialCancellationException) {
            Log.w(TAG, "Auto Google sign-in canceled by user", e)
            Result.success(null)
        } catch (e: GetCredentialException) {
            Log.w(TAG, "Auto Google sign-in unavailable: ${e.type}", e)
            Result.success(null)
        } catch (e: GoogleIdTokenParsingException) {
            Log.e(TAG, "Failed to parse Google ID token on auto sign-in", e)
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "Auto Google sign-in failed", e)
            Result.failure(e)
        }
    }

    suspend fun signInWithGoogle(context: Context): Result<FirebaseUser> {
        val credentialManager = CredentialManager.create(context)
        val signInOption = GetSignInWithGoogleOption.Builder(
            context.getString(R.string.default_web_client_id)
        ).build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInOption)
            .build()

        return try {
            val response = credentialManager.getCredential(context, request)
            val credential = response.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleIdCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val firebaseCredential = GoogleAuthProvider.getCredential(googleIdCredential.idToken, null)
                val authResult = Firebase.auth.signInWithCredential(firebaseCredential).awaitResult()
                val user = authResult.user
                    ?: return Result.failure(IllegalStateException("Akun Firebase tidak ditemukan setelah login."))
                Result.success(user)
            } else {
                Result.failure(IllegalStateException("Tipe kredensial tidak valid untuk Google Sign-In."))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: GetCredentialCancellationException) {
            Log.w(TAG, "Google sign-in canceled by user", e)
            Result.failure(e)
        } catch (e: GetCredentialException) {
            Log.e(TAG, "Google sign-in credential error: ${e.type}", e)
            Result.failure(e)
        } catch (e: GoogleIdTokenParsingException) {
            Log.e(TAG, "Failed to parse Google ID token", e)
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "Google sign-in failed", e)
            Result.failure(e)
        }
    }

    suspend fun signOut(context: Context) {
        withContext(NonCancellable) {
            try {
                CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
            } catch (e: ClearCredentialException) {
                Log.w(TAG, "Failed to clear credential state on sign-out", e)
            } catch (e: Exception) {
                Log.w(TAG, "Unexpected error clearing credential state", e)
            } finally {
                Firebase.auth.signOut()
            }
        }
    }
}
