package com.example.data.model

object ProviderCatalogDefaults {

    data class BuiltInProviderSpec(
        val id: String,
        val name: String,
        val baseUrl: String,
        val apiKeyUrl: String,
        val envVarName: String,
        val defaultModelId: String,
        val apiFormat: ProviderApiFormat,
        val models: List<String>,
        val supportsReasoningDefault: Boolean = true
    )

    val builtInProviders: List<BuiltInProviderSpec> = listOf(
        BuiltInProviderSpec(
            id = "opencode",
            name = "OpenCode (Zen)",
            baseUrl = "https://opencode.ai/zen/v1",
            apiKeyUrl = "https://opencode.ai/auth",
            envVarName = "OPENCODE_API_KEY",
            defaultModelId = "big-pickle",
            apiFormat = ProviderApiFormat.OPENAI_COMPATIBLE,
            models = listOf(
                "big-pickle",
                "grok-code",
                "kimi-k2",
                "kimi-k2.5",
                "glm-4.7-free",
                "glm-4.5-air",
                "qwen3-coder",
                "minimax-m2.1-free",
                "claude-sonnet-4-5",
                "claude-opus-4-1",
                "claude-3-5-haiku",
                "gemini-2.5-pro",
                "gemini-2.5-flash",
                "gpt-5-codex",
                "gpt-5"
            )
        ),
        BuiltInProviderSpec(
            id = "google",
            name = "Google Gemini",
            baseUrl = "https://generativelanguage.googleapis.com/v1beta",
            apiKeyUrl = "https://aistudio.google.com/apikey",
            envVarName = "GEMINI_API_KEY",
            defaultModelId = "gemini-2.5-flash",
            apiFormat = ProviderApiFormat.GEMINI,
            models = listOf(
                "gemini-2.5-flash",
                "gemini-2.5-pro",
                "gemini-2.5-flash-lite",
                "gemini-2.0-flash",
                "gemini-2.0-flash-lite",
                "gemini-1.5-pro",
                "gemini-1.5-flash"
            )
        ),
        BuiltInProviderSpec(
            id = "anthropic",
            name = "Anthropic (Claude)",
            baseUrl = "https://api.anthropic.com/v1",
            apiKeyUrl = "https://console.anthropic.com/settings/keys",
            envVarName = "ANTHROPIC_API_KEY",
            defaultModelId = "claude-sonnet-4-20250514",
            apiFormat = ProviderApiFormat.ANTHROPIC,
            models = listOf(
                "claude-sonnet-4-20250514",
                "claude-opus-4-20250514",
                "claude-3-7-sonnet-20250219",
                "claude-3-5-sonnet-20241022",
                "claude-3-5-haiku-20241022"
            )
        ),
        BuiltInProviderSpec(
            id = "openai",
            name = "OpenAI (GPT)",
            baseUrl = "https://api.openai.com/v1",
            apiKeyUrl = "https://platform.openai.com/api-keys",
            envVarName = "OPENAI_API_KEY",
            defaultModelId = "gpt-4o-mini",
            apiFormat = ProviderApiFormat.OPENAI_COMPATIBLE,
            models = listOf(
                "gpt-4o-mini",
                "gpt-4o",
                "gpt-4.1",
                "gpt-4.1-mini",
                "gpt-4.1-nano",
                "o3-mini",
                "o4-mini"
            )
        ),
        BuiltInProviderSpec(
            id = "openrouter",
            name = "OpenRouter",
            baseUrl = "https://openrouter.ai/api/v1",
            apiKeyUrl = "https://openrouter.ai/keys",
            envVarName = "OPENROUTER_API_KEY",
            defaultModelId = "anthropic/claude-sonnet-4",
            apiFormat = ProviderApiFormat.OPENAI_COMPATIBLE,
            models = listOf(
                "anthropic/claude-sonnet-4",
                "anthropic/claude-3.7-sonnet",
                "google/gemini-2.5-pro",
                "google/gemini-2.5-flash",
                "openai/gpt-4.1",
                "openai/gpt-4o",
                "deepseek/deepseek-r1",
                "deepseek/deepseek-chat-v3-0324",
                "qwen/qwen-2.5-coder-32b-instruct",
                "meta-llama/llama-3.3-70b-instruct"
            )
        ),
        BuiltInProviderSpec(
            id = "groq",
            name = "Groq",
            baseUrl = "https://api.groq.com/openai/v1",
            apiKeyUrl = "https://console.groq.com/keys",
            envVarName = "GROQ_API_KEY",
            defaultModelId = "llama-3.3-70b-versatile",
            apiFormat = ProviderApiFormat.OPENAI_COMPATIBLE,
            models = listOf(
                "llama-3.3-70b-versatile",
                "llama-3.1-8b-instant",
                "deepseek-r1-distill-llama-70b",
                "qwen-qwq-32b",
                "mixtral-8x7b-32768",
                "gemma2-9b-it"
            )
        ),
        BuiltInProviderSpec(
            id = "deepseek",
            name = "DeepSeek",
            baseUrl = "https://api.deepseek.com/v1",
            apiKeyUrl = "https://platform.deepseek.com/api_keys",
            envVarName = "DEEPSEEK_API_KEY",
            defaultModelId = "deepseek-chat",
            apiFormat = ProviderApiFormat.OPENAI_COMPATIBLE,
            models = listOf(
                "deepseek-chat",
                "deepseek-reasoner"
            )
        )
    )

    fun builtInSpec(providerId: String): BuiltInProviderSpec? =
        builtInProviders.firstOrNull { it.id.equals(providerId, ignoreCase = true) }

    fun defaultModelFor(providerId: String): String =
        builtInSpec(providerId)?.defaultModelId ?: "gpt-4o-mini"

    fun defaultBaseUrlFor(providerId: String): String =
        builtInSpec(providerId)?.baseUrl ?: "https://api.openai.com/v1"

    fun apiKeyUrlFor(providerId: String, baseUrl: String = ""): String {
        builtInSpec(providerId)?.apiKeyUrl?.let { return it }
        val trimmed = baseUrl.trim()
        if (trimmed.startsWith("https://", ignoreCase = true)) {
            val host = trimmed.removePrefix("https://").substringBefore('/').substringBefore(':')
            if (host.isNotBlank() &&
                !host.equals("localhost", ignoreCase = true) &&
                !host.startsWith("127.") &&
                !host.startsWith("10.") &&
                !host.startsWith("192.168.")
            ) {
                return "https://$host"
            }
        }
        return ""
    }

    fun isAccountReady(
        account: SavedProviderAccount,
        activeConfig: LocalProviderConfig? = null
    ): Boolean {
        return account.apiKey.isNotBlank() ||
            (account.isCustom && account.baseUrl.isNotBlank()) ||
            (activeConfig != null &&
                account.id.equals(activeConfig.providerId, ignoreCase = true) &&
                activeConfig.apiKey.isNotBlank())
    }

    fun sortAccountsReadyFirst(
        accounts: List<SavedProviderAccount>,
        activeProviderId: String? = null,
        activeConfig: LocalProviderConfig? = null
    ): List<SavedProviderAccount> {
        return accounts.sortedWith(
            compareByDescending<SavedProviderAccount> { isAccountReady(it, activeConfig) }
                .thenByDescending {
                    !activeProviderId.isNullOrBlank() &&
                        it.id.equals(activeProviderId, ignoreCase = true)
                }
                .thenByDescending { it.id.equals("opencode", ignoreCase = true) }
        )
    }

    fun sortModelsReadyFirst(
        models: List<ModelOption>,
        activeProviderId: String? = null,
        activeModelId: String? = null
    ): List<ModelOption> {
        return models.sortedWith(
            compareByDescending<ModelOption> { it.isConnected }
                .thenByDescending {
                    !activeProviderId.isNullOrBlank() &&
                        !activeModelId.isNullOrBlank() &&
                        it.providerId.equals(activeProviderId, ignoreCase = true) &&
                        it.modelId == activeModelId
                }
                .thenByDescending {
                    !activeProviderId.isNullOrBlank() &&
                        it.providerId.equals(activeProviderId, ignoreCase = true)
                }
                .thenByDescending { it.providerId.equals("opencode", ignoreCase = true) }
                .thenBy { it.providerName.lowercase() }
                .thenBy { it.modelName.lowercase() }
        )
    }

    fun defaultAccounts(): List<SavedProviderAccount> = builtInProviders.map { spec ->
        SavedProviderAccount(
            id = spec.id,
            name = spec.name,
            baseUrl = spec.baseUrl,
            apiKey = "",
            defaultModelId = spec.defaultModelId,
            apiFormat = spec.apiFormat,
            envVarName = spec.envVarName,
            isCustom = false,
            cachedModels = spec.models
        )
    }

    fun mergeWithBuiltIns(
        saved: List<SavedProviderAccount>,
        legacyConfig: LocalProviderConfig? = null
    ): List<SavedProviderAccount> {
        val byId = saved.associateBy { it.id.lowercase() }.toMutableMap()

        if (legacyConfig != null && legacyConfig.providerId.isNotBlank() &&
            (legacyConfig.apiKey.isNotBlank() || legacyConfig.customBaseUrl.isNotBlank())
        ) {
            val legacyId = legacyConfig.providerId.lowercase()
            val existing = byId[legacyId]
            if (existing == null || existing.apiKey.isBlank()) {
                val spec = builtInSpec(legacyId)
                byId[legacyId] = SavedProviderAccount(
                    id = legacyId,
                    name = existing?.name ?: spec?.name ?: legacyConfig.providerId,
                    baseUrl = legacyConfig.customBaseUrl.ifBlank { existing?.baseUrl ?: spec?.baseUrl.orEmpty() },
                    apiKey = legacyConfig.apiKey.ifBlank { existing?.apiKey.orEmpty() },
                    defaultModelId = legacyConfig.modelId.ifBlank { existing?.defaultModelId ?: spec?.defaultModelId.orEmpty() },
                    apiFormat = existing?.apiFormat ?: spec?.apiFormat ?: legacyConfig.apiFormat,
                    envVarName = existing?.envVarName ?: spec?.envVarName ?: "CUSTOM_API_KEY",
                    isCustom = existing?.isCustom ?: (spec == null),
                    cachedModels = existing?.cachedModels ?: spec?.models.orEmpty()
                )
            }
        }

        val mergedBuiltIns = builtInProviders.map { spec ->
            val existing = byId.remove(spec.id)
            if (existing == null) {
                SavedProviderAccount(
                    id = spec.id,
                    name = spec.name,
                    baseUrl = spec.baseUrl,
                    apiKey = "",
                    defaultModelId = spec.defaultModelId,
                    apiFormat = spec.apiFormat,
                    envVarName = spec.envVarName,
                    isCustom = false,
                    cachedModels = spec.models
                )
            } else {
                val combinedModels = (existing.cachedModels + spec.models)
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .distinct()
                existing.copy(
                    name = existing.name.ifBlank { spec.name },
                    baseUrl = existing.baseUrl.ifBlank { spec.baseUrl },
                    defaultModelId = existing.defaultModelId.ifBlank { spec.defaultModelId },
                    envVarName = existing.envVarName.ifBlank { spec.envVarName },
                    isCustom = false,
                    cachedModels = combinedModels
                )
            }
        }

        val customOnes = byId.values.filter { it.id.isNotBlank() }.map { custom ->
            val models = (listOfNotNull(custom.defaultModelId.takeIf { it.isNotBlank() }) + custom.cachedModels)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
            custom.copy(isCustom = true, cachedModels = models)
        }

        return mergedBuiltIns + customOnes
    }

    fun modelsForAccount(account: SavedProviderAccount): List<String> {
        val curated = builtInSpec(account.id)?.models.orEmpty()
        return (listOfNotNull(account.defaultModelId.takeIf { it.isNotBlank() }) +
            account.cachedModels +
            curated)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    }
}
