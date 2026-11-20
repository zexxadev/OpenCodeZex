package com.example.data.firebase

import android.content.Context
import com.example.R
import com.example.data.model.SavedProviderAccount
import com.example.data.model.ServerProfile
import com.example.ui.theme.ThemePreference
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map

data class CloudAppSettings(
    val themePreference: ThemePreference,
    val activeProfileId: String,
    val selectedAgent: String,
    val selectedProviderId: String,
    val selectedModelId: String,
    val setupCompleted: Boolean
)

interface CloudConfigSyncRepository {
    fun observeAppSettings(userId: String): Flow<CloudAppSettings?>
    fun observeServerProfiles(userId: String): Flow<List<ServerProfile>>
    fun observeProviderAccounts(userId: String): Flow<List<SavedProviderAccount>>

    suspend fun upsertUserProfile(user: FirebaseUser): Result<Unit>
    suspend fun hasRemoteSettings(userId: String): Result<Boolean>
    suspend fun saveAppSettings(settings: CloudAppSettings): Result<Unit>
    suspend fun saveServerProfile(profile: ServerProfile): Result<Unit>
    suspend fun deleteServerProfile(profileId: String): Result<Unit>
    suspend fun saveProviderAccount(account: SavedProviderAccount): Result<Unit>
    suspend fun deleteProviderAccount(providerId: String): Result<Unit>
}

object NoOpCloudConfigSyncRepository : CloudConfigSyncRepository {
    override fun observeAppSettings(userId: String): Flow<CloudAppSettings?> = emptyFlow()
    override fun observeServerProfiles(userId: String): Flow<List<ServerProfile>> = emptyFlow()
    override fun observeProviderAccounts(userId: String): Flow<List<SavedProviderAccount>> = emptyFlow()
    override suspend fun upsertUserProfile(user: FirebaseUser): Result<Unit> = Result.success(Unit)
    override suspend fun hasRemoteSettings(userId: String): Result<Boolean> = Result.success(false)
    override suspend fun saveAppSettings(settings: CloudAppSettings): Result<Unit> = Result.success(Unit)
    override suspend fun saveServerProfile(profile: ServerProfile): Result<Unit> = Result.success(Unit)
    override suspend fun deleteServerProfile(profileId: String): Result<Unit> = Result.success(Unit)
    override suspend fun saveProviderAccount(account: SavedProviderAccount): Result<Unit> = Result.success(Unit)
    override suspend fun deleteProviderAccount(providerId: String): Result<Unit> = Result.success(Unit)
}

class FirestoreConfigRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth
) : CloudConfigSyncRepository {

    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(context.getString(R.string.firestore_database_id)),
        auth = Firebase.auth
    )

    companion object {
        private const val SETTINGS_DOC_ID = "preferences"
    }

    private fun requireUserId(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("Pengguna belum masuk ke akun Google")

    override fun observeAppSettings(userId: String): Flow<CloudAppSettings?> {
        val docPath = "users/$userId/app_settings/$SETTINGS_DOC_ID"
        return db.document(docPath)
            .snapshots(MetadataChanges.EXCLUDE)
            .map { snapshot ->
                try {
                    if (!snapshot.exists()) return@map null
                    val doc = snapshot.toObject(
                        AppSettingsDoc::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ) ?: return@map null
                    CloudAppSettings(
                        themePreference = doc.toThemePreference(),
                        activeProfileId = doc.activeProfileId,
                        selectedAgent = doc.selectedAgent.ifBlank { "build" },
                        selectedProviderId = doc.selectedProviderId,
                        selectedModelId = doc.selectedModelId,
                        setupCompleted = doc.setupCompleted
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    throw handleFirestoreError(e, OperationType.GET, docPath)
                }
            }
    }

    override fun observeServerProfiles(userId: String): Flow<List<ServerProfile>> {
        val colPath = "users/$userId/server_profiles"
        return db.collection(colPath)
            .snapshots(MetadataChanges.EXCLUDE)
            .map { querySnapshot ->
                try {
                    querySnapshot.documents.mapNotNull { docSnap ->
                        docSnap.toObject(
                            ServerProfileDoc::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )?.toServerProfile()
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    throw handleFirestoreError(e, OperationType.LIST, colPath)
                }
            }
    }

    override fun observeProviderAccounts(userId: String): Flow<List<SavedProviderAccount>> {
        val colPath = "users/$userId/provider_accounts"
        return db.collection(colPath)
            .snapshots(MetadataChanges.EXCLUDE)
            .map { querySnapshot ->
                try {
                    querySnapshot.documents.mapNotNull { docSnap ->
                        docSnap.toObject(
                            ProviderAccountDoc::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )?.toSavedProviderAccount()
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    throw handleFirestoreError(e, OperationType.LIST, colPath)
                }
            }
    }

    override suspend fun upsertUserProfile(user: FirebaseUser): Result<Unit> {
        val path = "users/${user.uid}"
        return try {
            val uid = requireUserId()
            if (uid != user.uid) {
                throw SecurityException("UID akun tidak cocok dengan pengguna aktif.")
            }
            val safeUid = sanitizeFirestoreDocId(uid)
            val docRef = db.collection("users").document(safeUid)
            val existing = docRef.get().awaitResult()
            val email = user.email?.trim()?.take(320).orEmpty()
            val validEmail = if (email.matches(Regex("^.+@.+\\..+$"))) email else ""
            val displayName = user.displayName?.trim()?.take(160).orEmpty()
            val photoUrl = user.photoUrl?.toString()?.trim()?.take(1024).orEmpty()

            if (existing.exists()) {
                val updates = hashMapOf<String, Any?>(
                    "email" to validEmail,
                    "displayName" to displayName,
                    "photoUrl" to photoUrl,
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                docRef.update(updates).awaitResult()
            } else {
                val payload = hashMapOf<String, Any?>(
                    "uid" to safeUid,
                    "email" to validEmail,
                    "displayName" to displayName,
                    "photoUrl" to photoUrl,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                docRef.set(payload).awaitResult()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    override suspend fun hasRemoteSettings(userId: String): Result<Boolean> {
        val path = "users/$userId/app_settings/$SETTINGS_DOC_ID"
        return try {
            val snap = db.document(path).get().awaitResult()
            Result.success(snap.exists())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            Result.failure(e)
        }
    }

    override suspend fun saveAppSettings(settings: CloudAppSettings): Result<Unit> {
        var targetPath = "users/unknown/app_settings/$SETTINGS_DOC_ID"
        return try {
            val uid = sanitizeFirestoreDocId(requireUserId())
            targetPath = "users/$uid/app_settings/$SETTINGS_DOC_ID"
            val docRef = db.document(targetPath)
            val existing = docRef.get().awaitResult()

            val commonFields = hashMapOf<String, Any?>(
                "themePreference" to settings.themePreference.name,
                "activeProfileId" to settings.activeProfileId.trim().take(128),
                "selectedAgent" to settings.selectedAgent.trim().ifBlank { "build" }.take(64),
                "selectedProviderId" to settings.selectedProviderId.trim().take(128),
                "selectedModelId" to settings.selectedModelId.trim().take(256),
                "setupCompleted" to settings.setupCompleted,
                "updatedAt" to FieldValue.serverTimestamp()
            )

            if (existing.exists()) {
                docRef.update(commonFields.filterValues { it != null }).awaitResult()
            } else {
                val createFields = HashMap(commonFields).apply {
                    put("userId", uid)
                    put("createdAt", FieldValue.serverTimestamp())
                }.filterValues { it != null }
                docRef.set(createFields).awaitResult()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, targetPath)
            Result.failure(e)
        }
    }

    override suspend fun saveServerProfile(profile: ServerProfile): Result<Unit> {
        var targetPath = "users/unknown/server_profiles"
        return try {
            val uid = sanitizeFirestoreDocId(requireUserId())
            val docId = sanitizeFirestoreDocId(profile.id)
            targetPath = "users/$uid/server_profiles/$docId"
            val docRef = db.document(targetPath)
            val existing = docRef.get().awaitResult()

            val commonFields = hashMapOf<String, Any?>(
                "name" to profile.name.trim().ifBlank { "Server" }.take(120),
                "baseUrl" to profile.normalizedUrl.ifBlank { "local://android" }.take(512),
                "username" to profile.username.trim().take(120),
                "password" to profile.password.take(512),
                "directory" to profile.directory.trim().take(512),
                "mode" to profile.mode.name,
                "updatedAt" to FieldValue.serverTimestamp()
            )

            if (existing.exists()) {
                docRef.update(commonFields.filterValues { it != null }).awaitResult()
            } else {
                val createFields = HashMap(commonFields).apply {
                    put("userId", uid)
                    put("createdAt", FieldValue.serverTimestamp())
                }.filterValues { it != null }
                docRef.set(createFields).awaitResult()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, targetPath)
            Result.failure(e)
        }
    }

    override suspend fun deleteServerProfile(profileId: String): Result<Unit> {
        var targetPath = "users/unknown/server_profiles"
        return try {
            val uid = sanitizeFirestoreDocId(requireUserId())
            val docId = sanitizeFirestoreDocId(profileId)
            targetPath = "users/$uid/server_profiles/$docId"
            db.document(targetPath).delete().awaitResult()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, targetPath)
            Result.failure(e)
        }
    }

    override suspend fun saveProviderAccount(account: SavedProviderAccount): Result<Unit> {
        var targetPath = "users/unknown/provider_accounts"
        return try {
            val uid = sanitizeFirestoreDocId(requireUserId())
            val docId = sanitizeFirestoreDocId(account.id)
            targetPath = "users/$uid/provider_accounts/$docId"
            val docRef = db.document(targetPath)
            val existing = docRef.get().awaitResult()

            val cleanProviderId = account.id.trim().lowercase().ifBlank { docId }.take(128)
            val cleanModels = account.cachedModels
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .take(200)

            val commonFields = hashMapOf<String, Any?>(
                "providerId" to cleanProviderId,
                "name" to account.name.trim().ifBlank { cleanProviderId }.take(120),
                "baseUrl" to account.baseUrl.trim().take(512),
                "apiKey" to account.apiKey.trim().take(512),
                "defaultModelId" to account.defaultModelId.trim().take(256),
                "apiFormat" to account.apiFormat.name,
                "envVarName" to account.envVarName.trim().take(128),
                "customFlag" to account.isCustom,
                "cachedModels" to cleanModels,
                "updatedAt" to FieldValue.serverTimestamp()
            )

            if (existing.exists()) {
                docRef.update(commonFields.filterValues { it != null }).awaitResult()
            } else {
                val createFields = HashMap(commonFields).apply {
                    put("userId", uid)
                    put("createdAt", FieldValue.serverTimestamp())
                }.filterValues { it != null }
                docRef.set(createFields).awaitResult()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, targetPath)
            Result.failure(e)
        }
    }

    override suspend fun deleteProviderAccount(providerId: String): Result<Unit> {
        var targetPath = "users/unknown/provider_accounts"
        return try {
            val uid = sanitizeFirestoreDocId(requireUserId())
            val docId = sanitizeFirestoreDocId(providerId)
            targetPath = "users/$uid/provider_accounts/$docId"
            db.document(targetPath).delete().awaitResult()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, targetPath)
            Result.failure(e)
        }
    }
}
