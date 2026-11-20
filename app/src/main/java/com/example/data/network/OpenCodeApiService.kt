package com.example.data.network

import com.example.data.model.AgentInfo
import com.example.data.model.AuthCredentialRequest
import com.example.data.model.CommandInfo
import com.example.data.model.CreateSessionRequest
import com.example.data.model.FileContentResponse
import com.example.data.model.FileDiff
import com.example.data.model.FileNode
import com.example.data.model.ForkSessionRequest
import com.example.data.model.HealthResponse
import com.example.data.model.OpenCodeSession
import com.example.data.model.PermissionReplyRequest
import com.example.data.model.ProviderCatalog
import com.example.data.model.SendCommandRequest
import com.example.data.model.SendPromptRequest
import com.example.data.model.SendShellRequest
import com.example.data.model.UpdateSessionRequest
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.serializer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import retrofit2.Converter
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming
import java.lang.reflect.Type

interface OpenCodeApiService {

    @GET("global/health")
    suspend fun getHealth(): Response<HealthResponse>

    @GET("session")
    suspend fun getSessions(): Response<List<OpenCodeSession>>

    @POST("session")
    suspend fun createSession(@Body request: CreateSessionRequest): Response<OpenCodeSession>

    @GET("session/status")
    suspend fun getSessionsStatus(): Response<JsonObject>

    @GET("session/{id}")
    suspend fun getSession(@Path("id") id: String): Response<OpenCodeSession>

    @PATCH("session/{id}")
    suspend fun updateSession(
        @Path("id") id: String,
        @Body request: UpdateSessionRequest
    ): Response<OpenCodeSession>

    @DELETE("session/{id}")
    suspend fun deleteSession(@Path("id") id: String): Response<ResponseBody>

    @POST("session/{id}/fork")
    suspend fun forkSession(
        @Path("id") id: String,
        @Body request: ForkSessionRequest = ForkSessionRequest()
    ): Response<OpenCodeSession>

    @POST("session/{id}/abort")
    suspend fun abortSession(@Path("id") id: String): Response<ResponseBody>

    @GET("session/{id}/diff")
    suspend fun getSessionDiff(
        @Path("id") id: String,
        @Query("messageID") messageId: String? = null
    ): Response<List<FileDiff>>

    @GET("session/{id}/message")
    suspend fun getMessages(
        @Path("id") id: String,
        @Query("limit") limit: Int? = 80
    ): Response<JsonArray>

    @POST("session/{id}/message")
    suspend fun sendMessageSync(
        @Path("id") id: String,
        @Body request: SendPromptRequest
    ): Response<JsonObject>

    @POST("session/{id}/prompt_async")
    suspend fun sendPromptAsync(
        @Path("id") id: String,
        @Body request: SendPromptRequest
    ): Response<ResponseBody>

    @POST("session/{id}/command")
    suspend fun sendCommand(
        @Path("id") id: String,
        @Body request: SendCommandRequest
    ): Response<ResponseBody>

    @POST("session/{id}/shell")
    suspend fun sendShell(
        @Path("id") id: String,
        @Body request: SendShellRequest
    ): Response<ResponseBody>

    @POST("session/{id}/permissions/{permissionID}")
    suspend fun replyPermission(
        @Path("id") sessionId: String,
        @Path("permissionID") permissionId: String,
        @Body request: PermissionReplyRequest
    ): Response<ResponseBody>

    @GET("provider")
    suspend fun getProviders(): Response<ProviderCatalog>

    @PUT("auth/{id}")
    suspend fun setProviderAuth(
        @Path("id") providerId: String,
        @Body request: AuthCredentialRequest
    ): Response<ResponseBody>

    @Streaming
    @GET("event")
    suspend fun streamEvents(
        @Header("Accept") accept: String = "text/event-stream",
        @Header("Cache-Control") cacheControl: String = "no-cache",
        @Header("Last-Event-ID") lastEventId: String? = null
    ): Response<ResponseBody>

    @Streaming
    @GET("global/event")
    suspend fun streamGlobalEvents(
        @Header("Accept") accept: String = "text/event-stream",
        @Header("Cache-Control") cacheControl: String = "no-cache",
        @Header("Last-Event-ID") lastEventId: String? = null
    ): Response<ResponseBody>

    @GET("agent")
    suspend fun getAgents(): Response<List<AgentInfo>>

    @GET("command")
    suspend fun getCommands(): Response<List<CommandInfo>>

    @GET("file")
    suspend fun listFiles(
        @Query("path") path: String = ""
    ): Response<List<FileNode>>

    @GET("file/content")
    suspend fun readFileContent(
        @Query("path") path: String
    ): Response<FileContentResponse>

    @GET("find")
    suspend fun findText(
        @Query("pattern") pattern: String
    ): Response<JsonArray>

    @GET("find/file")
    suspend fun findFiles(
        @Query("query") query: String
    ): Response<List<String>>
}

class KotlinxJsonConverterFactory(
    private val json: Json
) : Converter.Factory() {

    private val mediaType = "application/json; charset=UTF-8".toMediaType()

    override fun responseBodyConverter(
        type: Type,
        annotations: Array<out Annotation>,
        retrofit: Retrofit
    ): Converter<ResponseBody, *>? {
        if (type == ResponseBody::class.java || type == JsonElement::class.java) {
            if (type == ResponseBody::class.java) return null
        }
        val serializer: KSerializer<Any> = json.serializersModule.serializer(type)
        return Converter { body ->
            body.use {
                val raw = it.string()
                if (raw.isBlank() && type == Unit::class.java) {
                    Unit
                } else {
                    json.decodeFromString(serializer, raw)
                }
            }
        }
    }

    override fun requestBodyConverter(
        type: Type,
        parameterAnnotations: Array<out Annotation>,
        methodAnnotations: Array<out Annotation>,
        retrofit: Retrofit
    ): Converter<*, RequestBody>? {
        val serializer: KSerializer<Any> = json.serializersModule.serializer(type)
        return Converter<Any, RequestBody> { value ->
            val encoded = json.encodeToString(serializer, value)
            encoded.toRequestBody(mediaType)
        }
    }
}
