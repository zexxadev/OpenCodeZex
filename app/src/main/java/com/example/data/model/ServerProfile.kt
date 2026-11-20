package com.example.data.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class RuntimeMode {
    LOCAL_ANDROID,
    REMOTE_SERVER
}

@Serializable
enum class ProviderApiFormat(val label: String) {
    OPENAI_COMPATIBLE("OpenAI-Compatible"),
    ANTHROPIC("Anthropic"),
    GEMINI("Google Gemini")
}

@Serializable
data class SavedProviderAccount(
    val id: String,
    val name: String,
    val baseUrl: String = "",
    val apiKey: String = "",
    val defaultModelId: String = "",
    val apiFormat: ProviderApiFormat = ProviderApiFormat.OPENAI_COMPATIBLE,
    val envVarName: String = "",
    val isCustom: Boolean = false,
    val cachedModels: List<String> = emptyList()
)

@Serializable
data class LocalProviderConfig(
    val providerId: String = "opencode",
    val modelId: String = "big-pickle",
    val apiKey: String = "",
    val customBaseUrl: String = "",
    val apiFormat: ProviderApiFormat = ProviderApiFormat.OPENAI_COMPATIBLE
)

@Serializable
data class ServerProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val baseUrl: String,
    val username: String = "opencode",
    val password: String = "",
    val directory: String = "",
    val lastConnectedAt: Long? = null,
    val mode: RuntimeMode = RuntimeMode.REMOTE_SERVER
) {
    val isLocalAndroid: Boolean
        get() = mode == RuntimeMode.LOCAL_ANDROID || baseUrl.startsWith("local://")

    val normalizedUrl: String
        get() = if (isLocalAndroid) "local://android" else normalizeServerUrl(baseUrl)

    val hasAuth: Boolean
        get() = !isLocalAndroid && password.isNotBlank()

    companion object {
        fun normalizeServerUrl(raw: String): String {
            val trimmed = raw.trim().trimEnd('/')
            if (trimmed.isEmpty()) return ""
            if (trimmed.startsWith("local://")) return trimmed
            return if (trimmed.startsWith("http://", ignoreCase = true) ||
                trimmed.startsWith("https://", ignoreCase = true)
            ) {
                trimmed
            } else {
                "http://$trimmed"
            }
        }

        val defaultPresets: List<ServerProfile> = listOf(
            ServerProfile(
                id = "local-android",
                name = "Runtime Lokal Android (Built-in)",
                baseUrl = "local://android",
                mode = RuntimeMode.LOCAL_ANDROID
            ),
            ServerProfile(
                id = "preset-termux",
                name = "Lokal HP (Termux)",
                baseUrl = "http://127.0.0.1:4096",
                username = "opencode",
                mode = RuntimeMode.REMOTE_SERVER
            ),
            ServerProfile(
                id = "preset-emulator",
                name = "Emulator Localhost",
                baseUrl = "http://10.0.2.2:4096",
                username = "opencode",
                mode = RuntimeMode.REMOTE_SERVER
            ),
            ServerProfile(
                id = "preset-lan",
                name = "Workstation LAN",
                baseUrl = "http://192.168.1.100:4096",
                username = "opencode",
                mode = RuntimeMode.REMOTE_SERVER
            )
        )
    }
}
