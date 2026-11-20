package com.example.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.LocalProviderConfig
import com.example.data.model.ProviderApiFormat
import com.example.data.model.ProviderCatalogDefaults
import com.example.data.model.SavedProviderAccount
import com.example.data.model.ServerProfile
import com.example.data.preferences.ServerProfileStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException

private val Context.providerConfigDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "provider_config_prefs"
)

/**
 * DataStore-backed repository responsible for persisting user API keys and
 * selected AI provider configurations across app restarts.
 */
interface ProviderConfigRepository {
    val providerConfigFlow: Flow<LocalProviderConfig>
    val selectedModelFlow: Flow<Pair<String?, String?>>
    val providerAccountsFlow: Flow<List<SavedProviderAccount>>

    suspend fun getProviderConfig(): LocalProviderConfig
    suspend fun getEffectiveConfigFor(providerId: String? = null, modelId: String? = null): LocalProviderConfig
    suspend fun getApiKey(providerId: String): String
    suspend fun saveSelectedProviderConfig(config: LocalProviderConfig)
    suspend fun saveApiKey(
        providerId: String,
        apiKey: String,
        modelId: String? = null,
        customBaseUrl: String? = null,
        apiFormat: ProviderApiFormat? = null,
        activate: Boolean = true
    )
    suspend fun setSelectedModel(providerId: String?, modelId: String?)
    suspend fun saveProviderAccount(account: SavedProviderAccount, activate: Boolean = true)
    suspend fun saveProviderAccounts(accounts: List<SavedProviderAccount>)
    suspend fun deleteCustomProviderAccount(providerId: String)
    suspend fun clearApiKey(providerId: String)
    suspend fun clearAll()
    suspend fun migrateFromLegacyIfNeeded(legacyStore: ServerProfileStore)
}

class DataStoreProviderConfigRepository(
    private val dataStore: DataStore<Preferences>,
    private val json: Json
) : ProviderConfigRepository {

    constructor(context: Context, json: Json) : this(
        dataStore = context.applicationContext.providerConfigDataStore,
        json = json
    )

    private object Keys {
        val SELECTED_PROVIDER_ID = stringPreferencesKey("selected_provider_id")
        val SELECTED_MODEL_ID = stringPreferencesKey("selected_model_id")
        val ACTIVE_API_KEY = stringPreferencesKey("active_api_key")
        val CUSTOM_BASE_URL = stringPreferencesKey("custom_base_url")
        val API_FORMAT = stringPreferencesKey("api_format")
        val LOCAL_PROVIDER_CONFIG_JSON = stringPreferencesKey("local_provider_config_json")
        val PROVIDER_ACCOUNTS_JSON = stringPreferencesKey("provider_accounts_json")
        val MIGRATED_FROM_LEGACY = booleanPreferencesKey("migrated_from_legacy")
    }

    private val safePreferences: Flow<Preferences> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }

    override val providerAccountsFlow: Flow<List<SavedProviderAccount>> = safePreferences.map { prefs ->
        decodeAccounts(prefs)
    }

    override val providerConfigFlow: Flow<LocalProviderConfig> = safePreferences.map { prefs ->
        decodeProviderConfig(prefs)
    }

    override val selectedModelFlow: Flow<Pair<String?, String?>> = safePreferences.map { prefs ->
        val provider = prefs[Keys.SELECTED_PROVIDER_ID]?.takeIf { it.isNotBlank() }
        val model = prefs[Keys.SELECTED_MODEL_ID]?.takeIf { it.isNotBlank() }
        if (provider != null && model != null) {
            provider to model
        } else {
            null to null
        }
    }

    override suspend fun getProviderConfig(): LocalProviderConfig = providerConfigFlow.first()

    override suspend fun getEffectiveConfigFor(
        providerId: String?,
        modelId: String?
    ): LocalProviderConfig {
        val prefs = safePreferences.first()
        val baseConfig = decodeProviderConfig(prefs)
        val accounts = decodeAccounts(prefs)
        val targetProviderId = providerId?.takeIf { it.isNotBlank() } ?: baseConfig.providerId
        val matched = accounts.firstOrNull { it.id.equals(targetProviderId, ignoreCase = true) }
        return if (matched != null) {
            baseConfig.copy(
                providerId = matched.id,
                modelId = modelId?.takeIf { it.isNotBlank() }
                    ?: baseConfig.modelId.takeIf { baseConfig.providerId.equals(matched.id, ignoreCase = true) }
                    ?: matched.defaultModelId,
                apiKey = matched.apiKey.ifBlank {
                    if (baseConfig.providerId.equals(matched.id, ignoreCase = true)) baseConfig.apiKey else ""
                },
                customBaseUrl = matched.baseUrl.ifBlank { baseConfig.customBaseUrl },
                apiFormat = matched.apiFormat
            )
        } else {
            baseConfig.copy(
                providerId = targetProviderId,
                modelId = modelId?.takeIf { it.isNotBlank() } ?: baseConfig.modelId
            )
        }
    }

    override suspend fun getApiKey(providerId: String): String {
        val cleanId = providerId.trim().lowercase()
        val prefs = safePreferences.first()
        val fromAccount = decodeAccounts(prefs)
            .firstOrNull { it.id.equals(cleanId, ignoreCase = true) }
            ?.apiKey
            .orEmpty()
        if (fromAccount.isNotBlank()) return fromAccount
        val active = decodeProviderConfig(prefs)
        return if (active.providerId.equals(cleanId, ignoreCase = true)) active.apiKey else ""
    }

    override suspend fun saveSelectedProviderConfig(config: LocalProviderConfig) {
        val cleanId = config.providerId.trim().lowercase().ifBlank { "opencode" }
        val builtIn = ProviderCatalogDefaults.builtInSpec(cleanId)
        val cleanModel = config.modelId.trim().ifBlank { ProviderCatalogDefaults.defaultModelFor(cleanId) }
        val cleanKey = config.apiKey.trim()
        val cleanUrl = config.customBaseUrl.trim().ifBlank { builtIn?.baseUrl.orEmpty() }
        val normalized = config.copy(
            providerId = cleanId,
            modelId = cleanModel,
            apiKey = cleanKey,
            customBaseUrl = cleanUrl
        )

        dataStore.edit { prefs ->
            val accounts = decodeAccounts(prefs).toMutableList()
            val idx = accounts.indexOfFirst { it.id.equals(cleanId, ignoreCase = true) }
            if (idx >= 0) {
                val existing = accounts[idx]
                accounts[idx] = existing.copy(
                    apiKey = cleanKey.ifBlank { existing.apiKey },
                    baseUrl = cleanUrl.ifBlank { existing.baseUrl },
                    defaultModelId = cleanModel.ifBlank { existing.defaultModelId },
                    apiFormat = normalized.apiFormat
                )
                prefs[Keys.PROVIDER_ACCOUNTS_JSON] = json.encodeToString(accounts)
            }
            writeActiveConfig(prefs, normalized)
            prefs[Keys.MIGRATED_FROM_LEGACY] = true
        }
    }

    override suspend fun saveApiKey(
        providerId: String,
        apiKey: String,
        modelId: String?,
        customBaseUrl: String?,
        apiFormat: ProviderApiFormat?,
        activate: Boolean
    ) {
        val cleanId = providerId.trim().lowercase().ifBlank { "opencode" }
        val cleanKey = apiKey.trim()
        val builtIn = ProviderCatalogDefaults.builtInSpec(cleanId)

        dataStore.edit { prefs ->
            val accounts = decodeAccounts(prefs).toMutableList()
            val existingIdx = accounts.indexOfFirst { it.id.equals(cleanId, ignoreCase = true) }
            val existing = accounts.getOrNull(existingIdx)

            val resolvedModel = modelId?.trim()?.takeIf { it.isNotBlank() }
                ?: existing?.defaultModelId?.takeIf { it.isNotBlank() }
                ?: ProviderCatalogDefaults.defaultModelFor(cleanId)
            val resolvedBaseUrl = customBaseUrl?.trim()?.takeIf { it.isNotBlank() }
                ?.let { ServerProfile.normalizeServerUrl(it) }
                ?: existing?.baseUrl?.takeIf { it.isNotBlank() }
                ?: builtIn?.baseUrl.orEmpty()
            val resolvedFormat = apiFormat
                ?: existing?.apiFormat
                ?: builtIn?.apiFormat
                ?: ProviderApiFormat.OPENAI_COMPATIBLE

            val updatedAccount = (existing ?: SavedProviderAccount(
                id = cleanId,
                name = builtIn?.name ?: providerId.trim(),
                baseUrl = resolvedBaseUrl,
                apiKey = cleanKey,
                defaultModelId = resolvedModel,
                apiFormat = resolvedFormat,
                envVarName = builtIn?.envVarName ?: "CUSTOM_API_KEY",
                isCustom = builtIn == null,
                cachedModels = (listOfNotNull(resolvedModel.takeIf { it.isNotBlank() }) + builtIn?.models.orEmpty()).distinct()
            )).copy(
                baseUrl = resolvedBaseUrl,
                apiKey = cleanKey,
                defaultModelId = resolvedModel,
                apiFormat = resolvedFormat
            )

            if (existingIdx >= 0) {
                accounts[existingIdx] = updatedAccount
            } else {
                accounts.add(updatedAccount)
            }
            prefs[Keys.PROVIDER_ACCOUNTS_JSON] = json.encodeToString(accounts)

            if (activate) {
                writeActiveConfig(
                    prefs,
                    LocalProviderConfig(
                        providerId = cleanId,
                        modelId = resolvedModel,
                        apiKey = cleanKey,
                        customBaseUrl = resolvedBaseUrl,
                        apiFormat = resolvedFormat
                    )
                )
            } else {
                val active = decodeProviderConfig(prefs)
                if (active.providerId.equals(cleanId, ignoreCase = true)) {
                    writeActiveConfig(
                        prefs,
                        active.copy(
                            apiKey = cleanKey,
                            customBaseUrl = resolvedBaseUrl,
                            apiFormat = resolvedFormat
                        )
                    )
                }
            }
            prefs[Keys.MIGRATED_FROM_LEGACY] = true
        }
    }

    override suspend fun setSelectedModel(providerId: String?, modelId: String?) {
        dataStore.edit { prefs ->
            if (providerId.isNullOrBlank() || modelId.isNullOrBlank()) {
                prefs.remove(Keys.SELECTED_PROVIDER_ID)
                prefs.remove(Keys.SELECTED_MODEL_ID)
            } else {
                val cleanProvider = providerId.trim().lowercase()
                val cleanModel = modelId.trim()
                val accounts = decodeAccounts(prefs)
                val matched = accounts.firstOrNull { it.id.equals(cleanProvider, ignoreCase = true) }
                val current = decodeProviderConfig(prefs)
                val resolvedKey = matched?.apiKey?.takeIf { it.isNotBlank() }
                    ?: if (current.providerId.equals(cleanProvider, ignoreCase = true)) current.apiKey else ""
                val resolvedBaseUrl = matched?.baseUrl?.takeIf { it.isNotBlank() }
                    ?: ProviderCatalogDefaults.builtInSpec(cleanProvider)?.baseUrl
                    ?: if (current.providerId.equals(cleanProvider, ignoreCase = true)) current.customBaseUrl else ""
                val resolvedFormat = matched?.apiFormat
                    ?: ProviderCatalogDefaults.builtInSpec(cleanProvider)?.apiFormat
                    ?: current.apiFormat

                writeActiveConfig(
                    prefs,
                    LocalProviderConfig(
                        providerId = cleanProvider,
                        modelId = cleanModel,
                        apiKey = resolvedKey,
                        customBaseUrl = resolvedBaseUrl,
                        apiFormat = resolvedFormat
                    )
                )
            }
            prefs[Keys.MIGRATED_FROM_LEGACY] = true
        }
    }

    override suspend fun saveProviderAccount(account: SavedProviderAccount, activate: Boolean) {
        val cleanId = account.id.trim().lowercase().ifBlank {
            "custom_${System.currentTimeMillis()}"
        }
        val cleanUrl = ServerProfile.normalizeServerUrl(account.baseUrl)
        val cleanModels = (listOfNotNull(account.defaultModelId.trim().takeIf { it.isNotBlank() }) + account.cachedModels)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()

        val normalized = account.copy(
            id = cleanId,
            name = account.name.trim().ifBlank { cleanId },
            baseUrl = cleanUrl,
            apiKey = account.apiKey.trim(),
            defaultModelId = account.defaultModelId.trim().ifBlank { cleanModels.firstOrNull().orEmpty() },
            cachedModels = cleanModels
        )

        dataStore.edit { prefs ->
            val currentList = decodeAccounts(prefs).toMutableList()
            val idx = currentList.indexOfFirst { it.id.equals(cleanId, ignoreCase = true) }
            if (idx >= 0) currentList[idx] = normalized else currentList.add(normalized)
            prefs[Keys.PROVIDER_ACCOUNTS_JSON] = json.encodeToString(currentList)

            if (activate) {
                val chosenModel = normalized.defaultModelId.ifBlank { cleanModels.firstOrNull() ?: "gpt-4o-mini" }
                writeActiveConfig(
                    prefs,
                    LocalProviderConfig(
                        providerId = normalized.id,
                        modelId = chosenModel,
                        apiKey = normalized.apiKey,
                        customBaseUrl = normalized.baseUrl,
                        apiFormat = normalized.apiFormat
                    )
                )
            } else {
                val active = decodeProviderConfig(prefs)
                if (active.providerId.equals(normalized.id, ignoreCase = true)) {
                    writeActiveConfig(
                        prefs,
                        active.copy(
                            apiKey = normalized.apiKey,
                            customBaseUrl = normalized.baseUrl,
                            apiFormat = normalized.apiFormat
                        )
                    )
                }
            }
            prefs[Keys.MIGRATED_FROM_LEGACY] = true
        }
    }

    override suspend fun saveProviderAccounts(accounts: List<SavedProviderAccount>) {
        dataStore.edit { prefs ->
            prefs[Keys.PROVIDER_ACCOUNTS_JSON] = json.encodeToString(accounts)
            prefs[Keys.MIGRATED_FROM_LEGACY] = true
        }
    }

    override suspend fun deleteCustomProviderAccount(providerId: String) {
        dataStore.edit { prefs ->
            val current = decodeAccounts(prefs)
            val updated = current.filterNot { it.id.equals(providerId, ignoreCase = true) && it.isCustom }
            prefs[Keys.PROVIDER_ACCOUNTS_JSON] = json.encodeToString(updated)

            val active = decodeProviderConfig(prefs)
            if (active.providerId.equals(providerId, ignoreCase = true)) {
                val fallback = updated.firstOrNull { it.apiKey.isNotBlank() } ?: updated.firstOrNull()
                if (fallback != null) {
                    writeActiveConfig(
                        prefs,
                        LocalProviderConfig(
                            providerId = fallback.id,
                            modelId = fallback.defaultModelId.ifBlank {
                                ProviderCatalogDefaults.defaultModelFor(fallback.id)
                            },
                            apiKey = fallback.apiKey,
                            customBaseUrl = fallback.baseUrl,
                            apiFormat = fallback.apiFormat
                        )
                    )
                }
            }
        }
    }

    override suspend fun clearApiKey(providerId: String) {
        val cleanId = providerId.trim().lowercase()
        dataStore.edit { prefs ->
            val accounts = decodeAccounts(prefs).map { acc ->
                if (acc.id.equals(cleanId, ignoreCase = true)) acc.copy(apiKey = "") else acc
            }
            prefs[Keys.PROVIDER_ACCOUNTS_JSON] = json.encodeToString(accounts)
            val active = decodeProviderConfig(prefs)
            if (active.providerId.equals(cleanId, ignoreCase = true)) {
                writeActiveConfig(prefs, active.copy(apiKey = ""))
            }
        }
    }

    override suspend fun clearAll() {
        dataStore.edit { it.clear() }
    }

    override suspend fun migrateFromLegacyIfNeeded(legacyStore: ServerProfileStore) {
        val prefs = safePreferences.first()
        if (prefs[Keys.MIGRATED_FROM_LEGACY] == true) return

        val legacySnapshot = legacyStore.readLegacyProviderSnapshot()
        dataStore.edit { dest ->
            if (dest[Keys.MIGRATED_FROM_LEGACY] == true) return@edit
            val hasDataInDest = !dest[Keys.LOCAL_PROVIDER_CONFIG_JSON].isNullOrBlank() ||
                !dest[Keys.PROVIDER_ACCOUNTS_JSON].isNullOrBlank()

            if (!hasDataInDest && legacySnapshot != null) {
                val mergedAccounts = ProviderCatalogDefaults.mergeWithBuiltIns(
                    saved = legacySnapshot.accounts,
                    legacyConfig = legacySnapshot.localConfig
                )
                dest[Keys.PROVIDER_ACCOUNTS_JSON] = json.encodeToString(mergedAccounts)

                val selectedProvider = legacySnapshot.selectedProviderId?.takeIf { it.isNotBlank() }
                    ?: legacySnapshot.localConfig.providerId.ifBlank { "opencode" }
                val matched = mergedAccounts.firstOrNull { it.id.equals(selectedProvider, ignoreCase = true) }
                val selectedModel = legacySnapshot.selectedModelId?.takeIf { it.isNotBlank() }
                    ?: legacySnapshot.localConfig.modelId.ifBlank {
                        matched?.defaultModelId ?: ProviderCatalogDefaults.defaultModelFor(selectedProvider)
                    }
                val resolvedConfig = LocalProviderConfig(
                    providerId = selectedProvider,
                    modelId = selectedModel,
                    apiKey = legacySnapshot.localConfig.apiKey.ifBlank { matched?.apiKey.orEmpty() },
                    customBaseUrl = legacySnapshot.localConfig.customBaseUrl.ifBlank { matched?.baseUrl.orEmpty() },
                    apiFormat = matched?.apiFormat ?: legacySnapshot.localConfig.apiFormat
                )
                writeActiveConfig(dest, resolvedConfig)
            }
            dest[Keys.MIGRATED_FROM_LEGACY] = true
        }
    }

    private fun writeActiveConfig(prefs: androidx.datastore.preferences.core.MutablePreferences, config: LocalProviderConfig) {
        prefs[Keys.SELECTED_PROVIDER_ID] = config.providerId
        prefs[Keys.SELECTED_MODEL_ID] = config.modelId
        prefs[Keys.ACTIVE_API_KEY] = config.apiKey
        prefs[Keys.CUSTOM_BASE_URL] = config.customBaseUrl
        prefs[Keys.API_FORMAT] = config.apiFormat.name
        prefs[Keys.LOCAL_PROVIDER_CONFIG_JSON] = json.encodeToString(config)
    }

    private fun decodeAccounts(prefs: Preferences): List<SavedProviderAccount> {
        val rawAccounts = prefs[Keys.PROVIDER_ACCOUNTS_JSON]
        val parsedAccounts = if (rawAccounts.isNullOrBlank()) {
            emptyList()
        } else {
            runCatching { json.decodeFromString<List<SavedProviderAccount>>(rawAccounts) }
                .getOrElse { emptyList() }
        }
        val activeConfig = decodeRawConfigOnly(prefs)
        return ProviderCatalogDefaults.mergeWithBuiltIns(parsedAccounts, activeConfig)
    }

    private fun decodeProviderConfig(prefs: Preferences): LocalProviderConfig {
        val rawConfig = decodeRawConfigOnly(prefs) ?: LocalProviderConfig()
        val rawAccounts = prefs[Keys.PROVIDER_ACCOUNTS_JSON]
        val parsedAccounts = if (rawAccounts.isNullOrBlank()) {
            emptyList()
        } else {
            runCatching { json.decodeFromString<List<SavedProviderAccount>>(rawAccounts) }
                .getOrElse { emptyList() }
        }
        val matched = parsedAccounts.firstOrNull {
            it.id.equals(rawConfig.providerId, ignoreCase = true)
        }
        return if (matched != null) {
            rawConfig.copy(
                apiKey = rawConfig.apiKey.ifBlank { matched.apiKey },
                customBaseUrl = rawConfig.customBaseUrl.ifBlank { matched.baseUrl },
                apiFormat = matched.apiFormat
            )
        } else {
            rawConfig
        }
    }

    private fun decodeRawConfigOnly(prefs: Preferences): LocalProviderConfig? {
        val rawJson = prefs[Keys.LOCAL_PROVIDER_CONFIG_JSON]
        if (!rawJson.isNullOrBlank()) {
            val decoded = runCatching { json.decodeFromString<LocalProviderConfig>(rawJson) }.getOrNull()
            if (decoded != null) return decoded
        }
        val provider = prefs[Keys.SELECTED_PROVIDER_ID] ?: return null
        val model = prefs[Keys.SELECTED_MODEL_ID] ?: ProviderCatalogDefaults.defaultModelFor(provider)
        val key = prefs[Keys.ACTIVE_API_KEY].orEmpty()
        val url = prefs[Keys.CUSTOM_BASE_URL] ?: ProviderCatalogDefaults.defaultBaseUrlFor(provider)
        val format = prefs[Keys.API_FORMAT]?.let { name ->
            ProviderApiFormat.entries.firstOrNull { it.name == name }
        } ?: ProviderCatalogDefaults.builtInSpec(provider)?.apiFormat ?: ProviderApiFormat.OPENAI_COMPATIBLE
        return LocalProviderConfig(
            providerId = provider,
            modelId = model,
            apiKey = key,
            customBaseUrl = url,
            apiFormat = format
        )
    }
}
