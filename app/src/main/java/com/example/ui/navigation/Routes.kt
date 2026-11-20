package com.example.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
data object SessionsRoute

@Serializable
data class WorkspaceRoute(val sessionId: String? = null)

@Serializable
data object FilesRoute

@Serializable
data object ServersRoute
