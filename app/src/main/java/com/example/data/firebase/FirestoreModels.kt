package com.example.data.firebase

import androidx.annotation.Keep
import com.example.data.model.ProviderApiFormat
import com.example.data.model.RuntimeMode
import com.example.data.model.SavedProviderAccount
import com.example.data.model.ServerProfile
import com.example.ui.theme.ThemePreference
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class UserProfileDoc(
    @DocumentId val id: String = "",
    val uid: String = "",
    val email: String = "",
    val displayName: String = "",
    val photoUrl: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class AppSettingsDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val themePreference: String = ThemePreference.DARK.name,
    val activeProfileId: String = "",
    val selectedAgent: String = "build",
    val selectedProviderId: String = "opencode",
    val selectedModelId: String = "big-pickle",
    val setupCompleted: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toThemePreference(): ThemePreference {
        return ThemePreference.entries.firstOrNull { it.name == themePreference } ?: ThemePreference.DARK
    }
}

@Keep
@IgnoreExtraProperties
data class ServerProfileDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val baseUrl: String = "",
    val username: String = "opencode",
    val password: String = "",
    val directory: String = "",
    val mode: String = RuntimeMode.REMOTE_SERVER.name,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toServerProfile(): ServerProfile {
        val resolvedMode = RuntimeMode.entries.firstOrNull { it.name == mode } ?: RuntimeMode.REMOTE_SERVER
        return ServerProfile(
            id = id.ifBlank { "profile_${name.hashCode().toUInt()}" },
            name = name.ifBlank { "Server" },
            baseUrl = baseUrl,
            username = username,
            password = password,
            directory = directory,
            mode = resolvedMode
        )
    }
}

@Keep
@IgnoreExtraProperties
data class ProviderAccountDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val providerId: String = "",
    val name: String = "",
    val baseUrl: String = "",
    val apiKey: String = "",
    val defaultModelId: String = "",
    val apiFormat: String = ProviderApiFormat.OPENAI_COMPATIBLE.name,
    val envVarName: String = "",
    val customFlag: Boolean = false,
    val cachedModels: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toSavedProviderAccount(): SavedProviderAccount {
        val cleanId = providerId.ifBlank { id }
        val resolvedFormat = ProviderApiFormat.entries.firstOrNull { it.name == apiFormat }
            ?: ProviderApiFormat.OPENAI_COMPATIBLE
        return SavedProviderAccount(
            id = cleanId,
            name = name.ifBlank { cleanId },
            baseUrl = baseUrl,
            apiKey = apiKey,
            defaultModelId = defaultModelId,
            apiFormat = resolvedFormat,
            envVarName = envVarName,
            isCustom = customFlag,
            cachedModels = cachedModels
        )
    }
}

fun sanitizeFirestoreDocId(raw: String): String {
    val cleaned = raw.trim().replace(Regex("[^a-zA-Z0-9_-]"), "_")
    return cleaned.take(128).ifBlank { "default_id" }
}
