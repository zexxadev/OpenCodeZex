package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.ProviderApiFormat
import com.example.data.model.ProviderCatalogDefaults
import com.example.data.model.SavedProviderAccount
import com.example.ui.theme.LocalOpenCodeColors

private enum class ProviderListFilter {
    ALL,
    READY,
    NEEDS_KEY
}

@Composable
fun ProviderAccountsSection(
    accounts: List<SavedProviderAccount>,
    activeProviderId: String?,
    activeModelId: String?,
    isRefreshingModels: Boolean,
    onOpenAddCustomProvider: () -> Unit,
    onEditProviderAccount: (SavedProviderAccount) -> Unit,
    onDeleteCustomProvider: (String) -> Unit,
    onSelectProviderModel: (String, String) -> Unit,
    onRefreshModels: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var filter by remember { mutableStateOf(ProviderListFilter.ALL) }

    val sortedAccounts = remember(accounts, activeProviderId) {
        ProviderCatalogDefaults.sortAccountsReadyFirst(
            accounts = accounts,
            activeProviderId = activeProviderId
        )
    }
    val readyCount = remember(sortedAccounts) {
        sortedAccounts.count { ProviderCatalogDefaults.isAccountReady(it) }
    }
    val needsKeyCount = (sortedAccounts.size - readyCount).coerceAtLeast(0)
    val visibleAccounts = remember(sortedAccounts, filter) {
        when (filter) {
            ProviderListFilter.ALL -> sortedAccounts
            ProviderListFilter.READY -> sortedAccounts.filter { ProviderCatalogDefaults.isAccountReady(it) }
            ProviderListFilter.NEEDS_KEY -> sortedAccounts.filter { !ProviderCatalogDefaults.isAccountReady(it) }
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Provider AI & Model (${sortedAccounts.size})",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Diurutkan status Siap terlebih dahulu · $readyCount siap, $needsKeyCount perlu key.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onOpenAddCustomProvider,
                modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
                    .testTag("add_custom_provider_button")
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Tambah Provider", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            OutlinedButton(
                onClick = { onRefreshModels(null) },
                enabled = !isRefreshingModels,
                modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
                    .testTag("refresh_all_models_button")
            ) {
                if (isRefreshingModels) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(6.dp))
                Text("Muat Model Live", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = filter == ProviderListFilter.ALL,
                onClick = { filter = ProviderListFilter.ALL },
                label = { Text("Semua (${sortedAccounts.size})") },
                modifier = Modifier.testTag("provider_filter_all")
            )
            FilterChip(
                selected = filter == ProviderListFilter.READY,
                onClick = { filter = ProviderListFilter.READY },
                label = { Text("Siap ($readyCount)") },
                modifier = Modifier.testTag("provider_filter_ready")
            )
            FilterChip(
                selected = filter == ProviderListFilter.NEEDS_KEY,
                onClick = { filter = ProviderListFilter.NEEDS_KEY },
                label = { Text("Perlu Key ($needsKeyCount)") },
                modifier = Modifier.testTag("provider_filter_needs_key")
            )
        }

        if (visibleAccounts.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Text(
                    text = if (filter == ProviderListFilter.READY) {
                        "Belum ada provider dengan API Key aktif. Ketuk \"Atur Key\" atau \"Buat Key\" pada daftar Semua untuk memulai."
                    } else {
                        "Semua provider sudah memiliki API Key atau endpoint siap pakai."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(14.dp)
                )
            }
        } else {
            visibleAccounts.forEach { account ->
                ProviderAccountCard(
                    account = account,
                    isActiveProvider = account.id.equals(activeProviderId, ignoreCase = true),
                    activeModelId = if (account.id.equals(activeProviderId, ignoreCase = true)) activeModelId else null,
                    isRefreshingModels = isRefreshingModels,
                    onEdit = { onEditProviderAccount(account) },
                    onDelete = { onDeleteCustomProvider(account.id) },
                    onRefreshModels = { onRefreshModels(account.id) },
                    onSelectModel = { modelId -> onSelectProviderModel(account.id, modelId) }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProviderAccountCard(
    account: SavedProviderAccount,
    isActiveProvider: Boolean,
    activeModelId: String?,
    isRefreshingModels: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onRefreshModels: () -> Unit,
    onSelectModel: (String) -> Unit
) {
    val semantic = LocalOpenCodeColors.current
    val context = LocalContext.current
    val hasKey = account.apiKey.isNotBlank()
    val isReady = ProviderCatalogDefaults.isAccountReady(account)
    val apiKeyUrl = remember(account.id, account.baseUrl) {
        ProviderCatalogDefaults.apiKeyUrlFor(account.id, account.baseUrl)
    }
    val allModels = remember(account, activeModelId) {
        val raw = ProviderCatalogDefaults.modelsForAccount(account)
        if (!activeModelId.isNullOrBlank() && activeModelId in raw) {
            listOf(activeModelId) + raw.filter { it != activeModelId }
        } else {
            raw
        }
    }
    var expanded by remember(account.id, isReady, isActiveProvider) {
        mutableStateOf(isActiveProvider || isReady)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("provider_card_${account.id}"),
        shape = RoundedCornerShape(14.dp),
        color = if (isActiveProvider) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            width = if (isActiveProvider) 1.5.dp else 1.dp,
            color = if (isActiveProvider) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = account.name.ifBlank { account.id },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (account.isCustom) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    text = "Custom",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = account.baseUrl.ifBlank { ProviderCatalogDefaults.defaultBaseUrlFor(account.id) },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (hasKey) {
                        semantic.successContainer
                    } else if (isReady) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (isReady) {
                            Icon(
                                Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = if (hasKey) semantic.success else MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                        Text(
                            text = when {
                                hasKey -> "Siap · Key Aktif"
                                account.isCustom -> "Siap · Endpoint"
                                else -> "Perlu Key"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = when {
                                hasKey -> semantic.success
                                account.isCustom -> MaterialTheme.colorScheme.onSecondaryContainer
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${allModels.size} model tersedia · ${account.apiFormat.label}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.testTag("toggle_models_${account.id}")
                ) {
                    Text(if (expanded) "Sembunyikan Grid" else "Lihat Semua (${allModels.size})")
                    Icon(
                        imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = if (expanded) "Ciutkan model" else "Tampilkan semua model",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            if (allModels.isNotEmpty()) {
                if (expanded) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        allModels.forEach { model ->
                            val isSelected = model == activeModelId
                            FilterChip(
                                selected = isSelected,
                                onClick = { onSelectModel(model) },
                                leadingIcon = if (isSelected) {
                                    {
                                        Icon(
                                            Icons.Rounded.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                } else null,
                                label = { Text(model, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        allModels.forEach { model ->
                            val isSelected = model == activeModelId
                            FilterChip(
                                selected = isSelected,
                                onClick = { onSelectModel(model) },
                                leadingIcon = if (isSelected) {
                                    {
                                        Icon(
                                            Icons.Rounded.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                } else null,
                                label = { Text(model, maxLines = 1) }
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onEdit,
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("edit_provider_${account.id}")
                ) {
                    Icon(
                        imageVector = if (hasKey) Icons.Rounded.Edit else Icons.Rounded.Key,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(if (hasKey) "Edit Key & URL" else "Atur Key", maxLines = 1)
                }
                if (!hasKey && apiKeyUrl.isNotBlank()) {
                    OutlinedButton(
                        onClick = { openProviderKeyPortal(context, apiKeyUrl) },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("provider_card_get_key_${account.id}")
                    ) {
                        Text("Buat Key", maxLines = 1)
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.AutoMirrored.Rounded.OpenInNew,
                            contentDescription = "Generate API key ${account.name}",
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
                OutlinedButton(
                    onClick = onRefreshModels,
                    enabled = !isRefreshingModels,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("refresh_provider_${account.id}")
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Muat model ${account.name}", modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Muat", maxLines = 1)
                }
                if (account.isCustom) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("delete_provider_${account.id}")
                    ) {
                        Icon(
                            Icons.Rounded.Delete,
                            contentDescription = "Hapus provider ${account.name}",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddOrEditProviderDialog(
    initialAccount: SavedProviderAccount?,
    isNewCustom: Boolean,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (SavedProviderAccount) -> Unit
) {
    var name by remember { mutableStateOf(initialAccount?.name.orEmpty()) }
    var customIdText by remember { mutableStateOf(initialAccount?.id.orEmpty()) }
    var baseUrl by remember {
        mutableStateOf(
            initialAccount?.baseUrl?.ifBlank {
                ProviderCatalogDefaults.defaultBaseUrlFor(initialAccount.id)
            }.orEmpty()
        )
    }
    var apiKey by remember { mutableStateOf(initialAccount?.apiKey.orEmpty()) }
    var defaultModelId by remember {
        mutableStateOf(
            initialAccount?.defaultModelId?.ifBlank {
                ProviderCatalogDefaults.defaultModelFor(initialAccount.id)
            } ?: "gpt-4o-mini"
        )
    }
    var apiFormat by remember {
        mutableStateOf(initialAccount?.apiFormat ?: ProviderApiFormat.OPENAI_COMPATIBLE)
    }
    var showKey by remember { mutableStateOf(false) }

    val availableModels = remember(initialAccount) {
        if (initialAccount != null) ProviderCatalogDefaults.modelsForAccount(initialAccount)
        else emptyList()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = when {
                    isNewCustom -> "Tambah Provider AI Baru"
                    initialAccount?.isCustom == true -> "Edit Provider Custom"
                    else -> "Atur ${initialAccount?.name ?: "Provider"}"
                }
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = if (isNewCustom) {
                        "Tambahkan endpoint LLM baru (OpenAI-compatible, Ollama, LiteLLM, Together, Anthropic, atau Gemini)."
                    } else {
                        "Atur URL endpoint, API Key, dan model default untuk provider ini."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (isNewCustom || initialAccount?.isCustom == true) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = {
                            name = it
                            if (isNewCustom) {
                                customIdText = it.trim().lowercase()
                                    .replace(Regex("[^a-z0-9]+"), "_")
                                    .trim('_')
                            }
                        },
                        label = { Text("Nama Provider") },
                        placeholder = { Text("Misal: Together AI / Ollama Lokal") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dialog_custom_provider_name")
                    )
                }

                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("URL Endpoint Base") },
                    placeholder = { Text("https://api.example.com/v1") },
                    leadingIcon = { Icon(Icons.Rounded.Link, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_custom_provider_url")
                )

                val resolvedPortalUrl = ProviderCatalogDefaults.apiKeyUrlFor(
                    providerId = if (isNewCustom) "custom" else initialAccount?.id.orEmpty(),
                    baseUrl = baseUrl
                )
                if (resolvedPortalUrl.isNotBlank()) {
                    ProviderApiKeyLinkBanner(
                        providerName = name.ifBlank { initialAccount?.name ?: "Provider" },
                        apiKeyUrl = resolvedPortalUrl,
                        modifier = Modifier.testTag("provider_dialog_get_api_key_link")
                    )
                }

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key${if (isNewCustom) " (opsional untuk lokal)" else ""}") },
                    placeholder = { Text("sk-... atau AIza...") },
                    singleLine = true,
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showKey = !showKey }) {
                            Icon(
                                if (showKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = "Toggle API Key"
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_custom_provider_key")
                )

                OutlinedTextField(
                    value = defaultModelId,
                    onValueChange = { defaultModelId = it },
                    label = { Text("Model ID Default") },
                    placeholder = { Text("Misal: qwen2.5-coder:32b atau gpt-4o") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_custom_provider_model")
                )

                if (availableModels.isNotEmpty()) {
                    Text("Pilih dari model tersedia:", style = MaterialTheme.typography.labelSmall)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        availableModels.forEach { model ->
                            FilterChip(
                                selected = defaultModelId == model,
                                onClick = { defaultModelId = model },
                                label = { Text(model, maxLines = 1) }
                            )
                        }
                    }
                }

                if (isNewCustom || initialAccount?.isCustom == true) {
                    Text("Format API", style = MaterialTheme.typography.labelMedium)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ProviderApiFormat.entries.forEach { fmt ->
                            FilterChip(
                                selected = apiFormat == fmt,
                                onClick = { apiFormat = fmt },
                                label = { Text(fmt.label) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            val isValid = baseUrl.isNotBlank() && (!isNewCustom || name.isNotBlank())
            Button(
                enabled = !isSaving && isValid,
                onClick = {
                    val resolvedId = if (isNewCustom) {
                        val slug = customIdText.ifBlank {
                            name.trim().lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
                        }.ifBlank { "custom_${System.currentTimeMillis()}" }
                        val reserved = ProviderCatalogDefaults.builtInSpec(slug) != null
                        if (reserved) "custom_$slug" else slug
                    } else {
                        initialAccount?.id ?: "custom_${System.currentTimeMillis()}"
                    }
                    val trimmedModel = defaultModelId.trim()
                    val existingModels = initialAccount?.cachedModels.orEmpty()
                    val nextModels = (listOfNotNull(trimmedModel.takeIf { it.isNotBlank() }) + existingModels)
                        .distinct()
                    onSave(
                        SavedProviderAccount(
                            id = resolvedId,
                            name = name.trim().ifBlank { initialAccount?.name ?: resolvedId },
                            baseUrl = baseUrl.trim().trimEnd('/'),
                            apiKey = apiKey.trim(),
                            defaultModelId = trimmedModel.ifBlank { "gpt-4o-mini" },
                            apiFormat = apiFormat,
                            envVarName = initialAccount?.envVarName?.ifBlank { null } ?: "CUSTOM_API_KEY",
                            isCustom = isNewCustom || (initialAccount?.isCustom == true),
                            cachedModels = nextModels
                        )
                    )
                },
                modifier = Modifier.testTag("confirm_save_custom_provider")
            ) {
                Text("Simpan Provider")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}
