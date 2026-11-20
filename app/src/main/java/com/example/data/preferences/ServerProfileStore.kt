package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.LocalProviderConfig
import com.example.data.model.ProviderCatalogDefaults
import com.example.data.model.SavedProviderAccount
import com.example.data.model.ServerProfile
import com.example.ui.theme.ThemePreference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "opencode_prefs")

class ServerProfileStore(
    private val context: Context,
    private val json: Json
) {
    private object Keys {
        val PROFILES_JSON = stringPreferencesKey("server_profiles_json")
        val ACTIVE_PROFILE_ID = stringPreferencesKey("active_profile_id")
        val THEME_PREFERENCE = stringPreferencesKey("theme_preference")
        val SELECTED_AGENT = stringPreferencesKey("selected_agent")
        val SELECTED_PROVIDER = stringPreferencesKey("selected_provider")
        val SELECTED_MODEL = stringPreferencesKey("selected_model")
        val HAS_COMPLETED_SETUP = booleanPreferencesKey("has_completed_setup")
        val LOCAL_PROVIDER_CONFIG = stringPreferencesKey("local_provider_config")
        val PROVIDER_ACCOUNTS_JSON = stringPreferencesKey("provider_accounts_json")
    }

    val profilesFlow: Flow<List<ServerProfile>> = context.dataStore.data.map { prefs ->
        val raw = prefs[Keys.PROFILES_JSON]
        if (raw.isNullOrBlank()) {
            ServerProfile.defaultPresets
        } else {
            runCatching { json.decodeFromString<List<ServerProfile>>(raw) }
                .getOrElse { ServerProfile.defaultPresets }
                .ifEmpty { ServerProfile.defaultPresets }
        }
    }

    val activeProfileIdFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[Keys.ACTIVE_PROFILE_ID]
    }

    val themePreferenceFlow: Flow<ThemePreference> = context.dataStore.data.map { prefs ->
        val raw = prefs[Keys.THEME_PREFERENCE]
        ThemePreference.entries.firstOrNull { it.name == raw } ?: ThemePreference.DARK
    }

    val selectedAgentFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.SELECTED_AGENT] ?: "build"
    }

    val selectedModelFlow: Flow<Pair<String?, String?>> = context.dataStore.data.map { prefs ->
        prefs[Keys.SELECTED_PROVIDER] to prefs[Keys.SELECTED_MODEL]
    }

    val hasCompletedSetupFlow: Flow<Boolean?> = context.dataStore.data.map { prefs ->
        prefs[Keys.HAS_COMPLETED_SETUP]
    }

    val localProviderConfigFlow: Flow<LocalProviderConfig> = context.dataStore.data.map { prefs ->
        val raw = prefs[Keys.LOCAL_PROVIDER_CONFIG]
        if (raw.isNullOrBlank()) {
            LocalProviderConfig()
        } else {
            runCatching { json.decodeFromString<LocalProviderConfig>(raw) }
                .getOrElse { LocalProviderConfig() }
        }
    }

    val providerAccountsFlow: Flow<List<SavedProviderAccount>> = context.dataStore.data.map { prefs ->
        val legacy = prefs[Keys.LOCAL_PROVIDER_CONFIG]?.takeIf { it.isNotBlank() }?.let { raw ->
            runCatching { json.decodeFromString<LocalProviderConfig>(raw) }.getOrNull()
        }
        val rawAccounts = prefs[Keys.PROVIDER_ACCOUNTS_JSON]
        val parsed = if (rawAccounts.isNullOrBlank()) {
            emptyList()
        } else {
            runCatching { json.decodeFromString<List<SavedProviderAccount>>(rawAccounts) }
                .getOrElse { emptyList() }
        }
        ProviderCatalogDefaults.mergeWithBuiltIns(parsed, legacy)
    }

    suspend fun saveProfiles(profiles: List<ServerProfile>) {
        context.dataStore.edit { prefs ->
            prefs[Keys.PROFILES_JSON] = json.encodeToString(profiles)
        }
    }

    suspend fun setActiveProfileId(id: String?) {
        context.dataStore.edit { prefs ->
            if (id == null) {
                prefs.remove(Keys.ACTIVE_PROFILE_ID)
            } else {
                prefs[Keys.ACTIVE_PROFILE_ID] = id
            }
        }
    }

    suspend fun setThemePreference(theme: ThemePreference) {
        context.dataStore.edit { prefs ->
            prefs[Keys.THEME_PREFERENCE] = theme.name
        }
    }

    suspend fun setSelectedAgent(agent: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SELECTED_AGENT] = agent
        }
    }

    suspend fun setSelectedModel(providerId: String?, modelId: String?) {
        context.dataStore.edit { prefs ->
            if (providerId == null || modelId == null) {
                prefs.remove(Keys.SELECTED_PROVIDER)
                prefs.remove(Keys.SELECTED_MODEL)
            } else {
                prefs[Keys.SELECTED_PROVIDER] = providerId
                prefs[Keys.SELECTED_MODEL] = modelId
            }
        }
    }

    suspend fun setHasCompletedSetup(completed: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.HAS_COMPLETED_SETUP] = completed
        }
    }

    suspend fun saveLocalProviderConfig(config: LocalProviderConfig) {
        context.dataStore.edit { prefs ->
            prefs[Keys.LOCAL_PROVIDER_CONFIG] = json.encodeToString(config)
        }
    }

    suspend fun saveProviderAccounts(accounts: List<SavedProviderAccount>) {
        context.dataStore.edit { prefs ->
            prefs[Keys.PROVIDER_ACCOUNTS_JSON] = json.encodeToString(accounts)
        }
    }

    data class LegacyProviderSnapshot(
        val selectedProviderId: String?,
        val selectedModelId: String?,
        val localConfig: LocalProviderConfig,
        val accounts: List<SavedProviderAccount>
    )

    suspend fun readLegacyProviderSnapshot(): LegacyProviderSnapshot? {
        val prefs = context.dataStore.data
            .catch { exception ->
                if (exception is IOException) emit(emptyPreferences()) else throw exception
            }
            .first()
        val rawConfig = prefs[Keys.LOCAL_PROVIDER_CONFIG]
        val rawAccounts = prefs[Keys.PROVIDER_ACCOUNTS_JSON]
        val selProv = prefs[Keys.SELECTED_PROVIDER]
        val selModel = prefs[Keys.SELECTED_MODEL]
        if (rawConfig.isNullOrBlank() && rawAccounts.isNullOrBlank() && selProv.isNullOrBlank()) {
            return null
        }
        val localConfig = rawConfig?.takeIf { it.isNotBlank() }?.let {
            runCatching { json.decodeFromString<LocalProviderConfig>(it) }.getOrNull()
        } ?: LocalProviderConfig()
        val accounts = rawAccounts?.takeIf { it.isNotBlank() }?.let {
            runCatching { json.decodeFromString<List<SavedProviderAccount>>(it) }.getOrNull()
        }.orEmpty()
        return LegacyProviderSnapshot(
            selectedProviderId = selProv,
            selectedModelId = selModel,
            localConfig = localConfig,
            accounts = accounts
        )
    }
}
