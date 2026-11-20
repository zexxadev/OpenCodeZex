package com.example

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import com.example.data.firebase.AppSettingsDoc
import com.example.data.firebase.ProviderAccountDoc
import com.example.data.firebase.ServerProfileDoc
import com.example.data.firebase.sanitizeFirestoreDocId
import com.example.data.model.ChatMessage
import com.example.data.model.LocalProviderConfig
import com.example.data.model.MessagePart
import com.example.data.model.ModelOption
import com.example.data.model.ProviderApiFormat
import com.example.data.model.SavedProviderAccount
import com.example.data.preferences.ServerProfileStore
import com.example.data.repository.DataStoreProviderConfigRepository
import com.example.ui.components.ThemeSelectorCard
import com.example.ui.components.ThemeToggleButton
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.ModelSelectorSheetContent
import com.example.ui.screens.ProviderApiKeyDialogContent
import com.example.ui.screens.ServersScreen
import com.example.ui.screens.SplashPhase
import com.example.ui.screens.SplashScreen
import com.example.ui.theme.OpenCodeTheme
import com.example.ui.theme.ThemePreference
import com.example.ui.viewmodel.CloudSyncStatus
import com.example.ui.viewmodel.ComposerMode
import com.example.ui.viewmodel.FailedPromptRequest
import com.example.ui.viewmodel.OpenCodeUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @get:Rule
    val composeTestRule = createComposeRule()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("OpenCodeZex", appName)
        assertEquals("com.opencodezex.app", context.packageName)
    }

    @Test
    fun `dataStore providerConfigRepository persists apiKey and selected provider across restarts`() = runBlocking {
        val prefsFile = tempFolder.newFolder("ds_restart").resolve("provider_config_prefs.preferences_pb")

        // App session 1: user enters OpenCode API key, adds a custom provider, and switches model
        val job1 = SupervisorJob()
        val dataStore1 = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + job1),
            produceFile = { prefsFile }
        )
        val repo1 = DataStoreProviderConfigRepository(dataStore1, json)

        repo1.saveApiKey(
            providerId = "opencode",
            apiKey = "oc-zen-secret-123",
            modelId = "qwen3-coder",
            customBaseUrl = "https://opencode.ai/zen/v1",
            activate = true
        )
        repo1.saveProviderAccount(
            account = SavedProviderAccount(
                id = "ollama_lan",
                name = "Ollama LAN",
                baseUrl = "http://192.168.1.50:11434/v1",
                apiKey = "ollama-token",
                defaultModelId = "qwen2.5-coder:32b",
                apiFormat = ProviderApiFormat.OPENAI_COMPATIBLE,
                isCustom = true,
                cachedModels = listOf("qwen2.5-coder:32b", "codellama")
            ),
            activate = false
        )
        repo1.setSelectedModel("opencode", "big-pickle")
        job1.cancelAndJoin()

        // App session 2 (simulated restart): fresh DataStore + Repository reading same disk file
        val job2 = SupervisorJob()
        val dataStore2 = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + job2),
            produceFile = { prefsFile }
        )
        val repo2 = DataStoreProviderConfigRepository(dataStore2, json)

        val restoredConfig = repo2.getProviderConfig()
        assertEquals("opencode", restoredConfig.providerId)
        assertEquals("big-pickle", restoredConfig.modelId)
        assertEquals("oc-zen-secret-123", restoredConfig.apiKey)
        assertEquals("https://opencode.ai/zen/v1", restoredConfig.customBaseUrl)

        val restoredSelection = repo2.selectedModelFlow.first()
        assertEquals("opencode" to "big-pickle", restoredSelection)

        assertEquals("oc-zen-secret-123", repo2.getApiKey("opencode"))
        assertEquals("ollama-token", repo2.getApiKey("ollama_lan"))

        val accounts = repo2.providerAccountsFlow.first()
        val customRestored = accounts.first { it.id == "ollama_lan" }
        assertTrue(customRestored.isCustom)
        assertEquals("http://192.168.1.50:11434/v1", customRestored.baseUrl)
        assertEquals(listOf("qwen2.5-coder:32b", "codellama"), customRestored.cachedModels)

        // Switch active provider to custom provider & verify effective config + clearApiKey
        repo2.setSelectedModel("ollama_lan", "codellama")
        val switched = repo2.getEffectiveConfigFor()
        assertEquals("ollama_lan", switched.providerId)
        assertEquals("codellama", switched.modelId)
        assertEquals("ollama-token", switched.apiKey)
        assertEquals("http://192.168.1.50:11434/v1", switched.customBaseUrl)

        repo2.clearApiKey("ollama_lan")
        assertEquals("", repo2.getApiKey("ollama_lan"))
        job2.cancelAndJoin()
    }

    @Test
    fun `dataStore providerConfigRepository migrates legacy ServerProfileStore configuration`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val legacyStore = ServerProfileStore(context, json)
        legacyStore.saveLocalProviderConfig(
            LocalProviderConfig(
                providerId = "google",
                modelId = "gemini-2.5-pro",
                apiKey = "AIzaLegacyKey999",
                customBaseUrl = "https://generativelanguage.googleapis.com/v1beta",
                apiFormat = ProviderApiFormat.GEMINI
            )
        )
        legacyStore.setSelectedModel("google", "gemini-2.5-pro")

        val migratedFile = tempFolder.newFolder("ds_migrate").resolve("provider_config_prefs.preferences_pb")
        val job = SupervisorJob()
        val ds = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + job),
            produceFile = { migratedFile }
        )
        val repo = DataStoreProviderConfigRepository(ds, json)
        repo.migrateFromLegacyIfNeeded(legacyStore)

        val migrated = repo.getProviderConfig()
        assertEquals("google", migrated.providerId)
        assertEquals("gemini-2.5-pro", migrated.modelId)
        assertEquals("AIzaLegacyKey999", migrated.apiKey)
        assertEquals(ProviderApiFormat.GEMINI, migrated.apiFormat)
        assertEquals("AIzaLegacyKey999", repo.getApiKey("google"))
        job.cancelAndJoin()
    }

    @Test
    fun `chatScreen renders copy to clipboard button on every AI code block and copies code`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        val markdownResponse = """
            Here is a Kotlin utility:
            ```kotlin
            fun greet(name: String) = "Hello, ${'$'}name"
            ```
            And run this command:
            ~~~bash
            gradle :app:testDebugUnitTest
            ~~~
        """.trimIndent()

        val message = ChatMessage(
            id = "msg_ai_1",
            sessionId = "ses_1",
            role = "assistant",
            createdAt = 1L,
            completedAt = 2L,
            agent = "build",
            modelId = "big-pickle",
            providerId = "opencode",
            errorText = null,
            parts = listOf(
                MessagePart.Text(
                    id = "part_1",
                    messageId = "msg_ai_1",
                    text = markdownResponse
                )
            )
        )

        composeTestRule.setContent {
            OpenCodeTheme {
                ChatScreen(
                    state = OpenCodeUiState(messages = listOf(message)),
                    onTextChange = {},
                    onSelectFileMention = {},
                    onSelectMode = {},
                    onOpenAgentMenu = {},
                    onOpenModelSheet = {},
                    onOpenCommandMenu = {},
                    onOpenDiffs = {},
                    onReconnectSse = {},
                    onSend = {},
                    onAbort = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        val copyButtons = composeTestRule.onAllNodesWithTag("copy_code_block_button")
        copyButtons.assertCountEquals(2)

        copyButtons[0].performClick()
        composeTestRule.waitForIdle()
        assertEquals(
            "fun greet(name: String) = \"Hello, \$name\"",
            clipboard.primaryClip?.getItemAt(0)?.text?.toString()
        )
        copyButtons[0].assertTextContains("Copied")

        copyButtons[1].performClick()
        composeTestRule.waitForIdle()
        assertEquals(
            "gradle :app:testDebugUnitTest",
            clipboard.primaryClip?.getItemAt(0)?.text?.toString()
        )
        copyButtons[1].assertTextContains("Copied")
    }

    @Test
    fun `chatScreen shows Ulangi Respon button on AI error card and failed prompt banner`() {
        val retriedIds = mutableListOf<String?>()
        var dismissed = false

        val failedAssistantMsg = ChatMessage(
            id = "msg_err_1",
            sessionId = "ses_err",
            role = "assistant",
            createdAt = 1L,
            completedAt = 2L,
            agent = "build",
            modelId = "big-pickle",
            providerId = "opencode",
            errorText = "Timeout: endpoint AI tidak merespons dalam 30 detik.",
            parts = emptyList()
        )
        val failedBanner = FailedPromptRequest(
            sessionId = "ses_err",
            input = "Buat fungsi sorting",
            mode = ComposerMode.PROMPT,
            agent = "build",
            model = null,
            errorTitle = "Gagal Terhubung ke Endpoint",
            errorDetail = "Server mengembalikan HTTP 503 setelah 3x percobaan ulang."
        )

        val uiState = androidx.compose.runtime.mutableStateOf(
            OpenCodeUiState(
                activeSessionId = "ses_err",
                messages = listOf(failedAssistantMsg.copy(errorText = null)),
                retryBannerMessage = "Gagal terhubung ke endpoint, mencoba ulang (2/3)…"
            )
        )

        composeTestRule.setContent {
            OpenCodeTheme {
                ChatScreen(
                    state = uiState.value,
                    onTextChange = {},
                    onSelectFileMention = {},
                    onSelectMode = {},
                    onOpenAgentMenu = {},
                    onOpenModelSheet = {},
                    onOpenCommandMenu = {},
                    onOpenDiffs = {},
                    onReconnectSse = {},
                    onSend = {},
                    onAbort = {},
                    onRetryResponse = { retriedIds.add(it) },
                    onDismissFailedPrompt = { dismissed = true }
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("retry_status_banner").assertIsDisplayed()

        // Transition to exhausted failure state (retry banner cleared, error card + failed prompt shown)
        composeTestRule.runOnUiThread {
            uiState.value = OpenCodeUiState(
                activeSessionId = "ses_err",
                messages = listOf(failedAssistantMsg),
                failedPromptRequest = failedBanner,
                retryBannerMessage = null
            )
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("retry_ai_response_button")
            .assertIsDisplayed()
            .assertTextContains("Ulangi Respon")
            .performClick()

        composeTestRule.onNodeWithTag("retry_failed_prompt_button")
            .assertIsDisplayed()
            .assertTextContains("Ulangi Respon")
            .performClick()

        composeTestRule.onNodeWithTag("dismiss_failed_prompt_button")
            .assertIsDisplayed()
            .performClick()

        assertEquals(listOf("msg_err_1", null), retriedIds)
        assertTrue(dismissed)
    }

    @Test
    @Config(sdk = [36], qualifiers = "w411dp-h891dp")
    fun `chatScreen formats rich markdown headings lists quotes tables and code blocks`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        val richMarkdown = """
            # Solusi Refactoring
            Berikut ringkasan perubahan pada `OpenCodeRepository`:
            - Gunakan **retry otomatis** saat timeout
            - Tangani `IOException` dengan aman

            > Catatan: Timeout disetel ke 30 detik.

            | Komponen | Status |
            | --- | --- |
            | Retry | Aktif |

            ```kotlin
            val timeoutMs = 30_000L
            ```
        """.trimIndent()

        val message = ChatMessage(
            id = "msg_md_1",
            sessionId = "ses_md",
            role = "assistant",
            createdAt = 1L,
            completedAt = 2L,
            agent = "build",
            modelId = "big-pickle",
            providerId = "opencode",
            errorText = null,
            parts = listOf(
                MessagePart.Text(
                    id = "part_md_1",
                    messageId = "msg_md_1",
                    text = richMarkdown
                )
            )
        )

        composeTestRule.setContent {
            OpenCodeTheme {
                ChatScreen(
                    state = OpenCodeUiState(
                        activeSessionId = "ses_md",
                        messages = listOf(message)
                    ),
                    onTextChange = {},
                    onSelectFileMention = {},
                    onSelectMode = {},
                    onOpenAgentMenu = {},
                    onOpenModelSheet = {},
                    onOpenCommandMenu = {},
                    onOpenDiffs = {},
                    onReconnectSse = {},
                    onSend = {},
                    onAbort = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("markdown_rendered_content").assertIsDisplayed()
        composeTestRule.onNodeWithText("Solusi Refactoring", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("Catatan: Timeout disetel ke 30 detik.", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("code_block_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("copy_code_block_button")
            .assertIsDisplayed()
            .performClick()
        composeTestRule.waitForIdle()
        assertEquals(
            "val timeoutMs = 30_000L",
            clipboard.primaryClip?.getItemAt(0)?.text?.toString()
        )
    }

    @Test
    @Config(sdk = [36], qualifiers = "w411dp-h891dp")
    fun `themeToggleAndSelector switch between Dark MaterialGlass and Miuix`() {
        val currentTheme = mutableStateOf(ThemePreference.DARK)

        composeTestRule.setContent {
            OpenCodeTheme(themePreference = currentTheme.value) {
                androidx.compose.foundation.layout.Column {
                    ThemeToggleButton(
                        currentTheme = currentTheme.value,
                        onSelectTheme = { currentTheme.value = it }
                    )
                    ThemeSelectorCard(
                        currentTheme = currentTheme.value,
                        onSelectTheme = { currentTheme.value = it }
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("theme_toggle_button").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("theme_option_material_glass").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        assertEquals(ThemePreference.MATERIAL_GLASS, currentTheme.value)

        composeTestRule.onNodeWithTag("theme_chip_miuix").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        assertEquals(ThemePreference.MIUIX, currentTheme.value)
        composeTestRule.onNodeWithText("Aktif: Miuix", substring = true).assertIsDisplayed()
    }

    @Test
    @Config(sdk = [36], qualifiers = "w411dp-h891dp")
    fun `modelSelectorSheet displays Siap models first and filters ready models`() {
        val unreadyFirstInput = listOf(
            ModelOption(
                providerId = "anthropic",
                providerName = "Anthropic (Claude)",
                modelId = "claude-sonnet-4-20250514",
                modelName = "claude-sonnet-4-20250514",
                isConnected = false,
                supportsReasoning = true
            ),
            ModelOption(
                providerId = "opencode",
                providerName = "OpenCode (Zen)",
                modelId = "big-pickle",
                modelName = "big-pickle",
                isConnected = true,
                supportsReasoning = true
            )
        )

        composeTestRule.setContent {
            OpenCodeTheme(themePreference = ThemePreference.MATERIAL_GLASS) {
                ModelSelectorSheetContent(
                    models = unreadyFirstInput,
                    selectedProviderId = null,
                    selectedModelId = null,
                    isRefreshingModels = false,
                    onRefreshModels = {},
                    onSelect = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        val readyBounds = composeTestRule.onNodeWithTag("model_option_opencode_big-pickle").getBoundsInRoot()
        val unreadyBounds = composeTestRule.onNodeWithTag("model_option_anthropic_claude-sonnet-4-20250514").getBoundsInRoot()
        assertTrue(readyBounds.top < unreadyBounds.top)

        composeTestRule.onNodeWithTag("model_filter_ready_chip").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("model_option_opencode_big-pickle").assertIsDisplayed()
        composeTestRule.onAllNodesWithTag("model_option_anthropic_claude-sonnet-4-20250514").assertCountEquals(0)
    }

    @Test
    @Config(sdk = [36], qualifiers = "w411dp-h891dp")
    fun `serversScreen organizes sections shows themes and displays API key generation link`() {
        val currentTheme = mutableStateOf(ThemePreference.MIUIX)
        val accounts = listOf(
            SavedProviderAccount(
                id = "opencode",
                name = "OpenCode (Zen)",
                baseUrl = "https://opencode.ai/zen/v1",
                apiKey = "",
                defaultModelId = "big-pickle"
            ),
            SavedProviderAccount(
                id = "google",
                name = "Google Gemini",
                baseUrl = "https://generativelanguage.googleapis.com/v1beta",
                apiKey = "AIza-ready",
                defaultModelId = "gemini-2.5-flash",
                apiFormat = ProviderApiFormat.GEMINI
            )
        )

        composeTestRule.setContent {
            OpenCodeTheme(themePreference = currentTheme.value) {
                ServersScreen(
                    state = OpenCodeUiState(
                        themePreference = currentTheme.value,
                        providerAccounts = accounts
                    ),
                    onTestConnection = {},
                    onActivateProfile = {},
                    onSaveProfile = {},
                    onDeleteProfile = {},
                    onThemeChange = { currentTheme.value = it },
                    onOpenSetupWizard = {},
                    onSaveProviderApiKey = { _, _, _, _ -> }
                )
            }
        }
        composeTestRule.waitForIdle()

        // Verify Material Glass and Miuix theme options are visible on ServersScreen
        composeTestRule.onNodeWithTag("theme_chip_material_glass").assertIsDisplayed()
        composeTestRule.onNodeWithTag("theme_chip_miuix").assertIsDisplayed()

        // Switch to Provider & Model tab filter to focus provider cards in viewport
        composeTestRule.onNodeWithTag("server_tab_filter_providers").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        // Ready provider ("google") renders above unready provider ("opencode")
        val readyCardTop = composeTestRule.onNodeWithTag("provider_card_google").getBoundsInRoot().top
        val unreadyCardTop = composeTestRule.onNodeWithTag("provider_card_opencode").getBoundsInRoot().top
        assertTrue(
            "Expected ready provider top ($readyCardTop) < unready provider top ($unreadyCardTop)",
            readyCardTop < unreadyCardTop
        )

        // Scroll down to verify direct API key creation CTA on unready card
        composeTestRule.onNodeWithTag("servers_lazy_column").performTouchInput { swipeUp() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("provider_card_get_key_opencode").assertIsDisplayed()
    }

    @Test
    @Config(sdk = [36], qualifiers = "w411dp-h891dp")
    fun `providerApiKeyDialogContent updates direct generation link when switching provider`() {
        val providerId = mutableStateOf("opencode")
        val modelId = mutableStateOf("big-pickle")
        val apiKey = mutableStateOf("")
        val baseUrl = mutableStateOf("https://opencode.ai/zen/v1")

        composeTestRule.setContent {
            OpenCodeTheme(themePreference = ThemePreference.MATERIAL_GLASS) {
                ProviderApiKeyDialogContent(
                    isLocalRuntime = true,
                    accounts = com.example.data.model.ProviderCatalogDefaults.defaultAccounts(),
                    providerId = providerId.value,
                    modelId = modelId.value,
                    apiKey = apiKey.value,
                    customBaseUrl = baseUrl.value,
                    onProviderIdChange = { providerId.value = it },
                    onSelectPreset = { preset ->
                        providerId.value = preset.id
                        modelId.value = preset.defaultModel
                        baseUrl.value = preset.defaultBaseUrl
                    },
                    onModelIdChange = { modelId.value = it },
                    onApiKeyChange = { apiKey.value = it },
                    onCustomBaseUrlChange = { baseUrl.value = it }
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("dialog_get_api_key_link").assertIsDisplayed()
        composeTestRule.onNodeWithText("https://opencode.ai/auth", substring = true).assertIsDisplayed()

        composeTestRule.onNodeWithTag("dialog_preset_google").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("https://aistudio.google.com/apikey", substring = true).assertIsDisplayed()
    }

    @Test
    fun `firestoreModels sanitize ids and convert POJOs accurately`() {
        assertEquals("custom_api_v1", sanitizeFirestoreDocId("custom.api/v1"))
        assertEquals("default_id", sanitizeFirestoreDocId("   "))

        val providerDoc = ProviderAccountDoc(
            id = "ollama_lan",
            userId = "uid123",
            providerId = "ollama_lan",
            name = "Ollama LAN",
            baseUrl = "http://192.168.1.50:11434/v1",
            apiKey = "tok",
            defaultModelId = "qwen3-coder",
            apiFormat = "OPENAI_COMPATIBLE",
            envVarName = "OLLAMA_API_KEY",
            customFlag = true,
            cachedModels = listOf("qwen3-coder")
        )
        val account = providerDoc.toSavedProviderAccount()
        assertTrue(account.isCustom)
        assertEquals("ollama_lan", account.id)
        assertEquals(ProviderApiFormat.OPENAI_COMPATIBLE, account.apiFormat)

        val settingsDoc = AppSettingsDoc(
            id = "preferences",
            userId = "uid123",
            themePreference = "MATERIAL_GLASS"
        )
        assertEquals(ThemePreference.MATERIAL_GLASS, settingsDoc.toThemePreference())

        val serverDoc = ServerProfileDoc(
            id = "local-android",
            userId = "uid123",
            name = "Lokal",
            baseUrl = "local://android",
            mode = "LOCAL_ANDROID"
        )
        assertTrue(serverDoc.toServerProfile().isLocalAndroid)
    }

    @Test
    @Config(sdk = [36], qualifiers = "w411dp-h891dp")
    fun `authScreen displays Google sign in CTA and triggers callback`() {
        var clicked = false
        composeTestRule.setContent {
            OpenCodeTheme(themePreference = ThemePreference.DARK) {
                AuthScreen(
                    isSigningIn = false,
                    errorMessage = "Percobaan error autentikasi",
                    themePreference = ThemePreference.DARK,
                    onThemeChange = {},
                    onGoogleSignInClick = { clicked = true }
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Percobaan error autentikasi").assertIsDisplayed()
        composeTestRule.onNodeWithTag("google_sign_in_button").assertIsDisplayed().performClick()
        assertTrue(clicked)
    }

    @Test
    @Config(sdk = [36], qualifiers = "w411dp-h891dp")
    fun `serversScreen renders CloudAccountSecurityCard with sync and sign out buttons`() {
        var syncClicked = false
        var signOutClicked = false
        composeTestRule.setContent {
            OpenCodeTheme(themePreference = ThemePreference.DARK) {
                ServersScreen(
                    state = OpenCodeUiState(
                        authenticatedUserId = "uid_alice",
                        authenticatedUserName = "Alice",
                        authenticatedUserEmail = "alice@example.com",
                        cloudSyncStatus = CloudSyncStatus.Synced()
                    ),
                    onTestConnection = {},
                    onActivateProfile = {},
                    onSaveProfile = {},
                    onDeleteProfile = {},
                    onThemeChange = {},
                    onOpenSetupWizard = {},
                    onSaveProviderApiKey = { _, _, _, _ -> },
                    onSyncCloud = { syncClicked = true },
                    onSignOut = { signOutClicked = true }
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Alice").assertIsDisplayed()
        composeTestRule.onNodeWithText("alice@example.com").assertIsDisplayed()
        composeTestRule.onNodeWithText("Tersinkronisasi ke Firebase Cloud").assertIsDisplayed()
        composeTestRule.onNodeWithTag("sync_cloud_button").assertIsDisplayed().performClick()
        composeTestRule.onNodeWithTag("sign_out_button").assertIsDisplayed().performClick()
        assertTrue(syncClicked)
        assertTrue(signOutClicked)
    }

    @Test
    @Config(sdk = [36], qualifiers = "w411dp-h891dp")
    fun `splashScreen displays OpenCodeZex branding logo and transitions initialization phases`() {
        val phase = mutableStateOf(SplashPhase.INITIALIZING_FIREBASE)
        composeTestRule.setContent {
            OpenCodeTheme(themePreference = ThemePreference.MATERIAL_GLASS) {
                SplashScreen(phase = phase.value)
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("splash_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("splash_logo_image").assertIsDisplayed()
        composeTestRule.onNodeWithTag("splash_brand_title").assertTextContains("OpenCodeZex")
        composeTestRule.onNodeWithTag("splash_status_label").assertIsDisplayed()
        composeTestRule.onNodeWithText("Menginisialisasi Firebase", substring = true).assertIsDisplayed()

        phase.value = SplashPhase.CHECKING_SESSION
        composeTestRule.mainClock.advanceTimeBy(300)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Memeriksa sesi akun", substring = true).assertIsDisplayed()

        phase.value = SplashPhase.SESSION_FOUND
        composeTestRule.mainClock.advanceTimeBy(300)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Membuka workspace", substring = true).assertIsDisplayed()
    }
}
