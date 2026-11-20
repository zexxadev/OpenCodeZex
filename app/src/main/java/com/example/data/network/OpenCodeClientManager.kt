package com.example.data.network

import com.example.data.model.ServerProfile
import kotlinx.serialization.json.Json
import okhttp3.Credentials
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

class OpenCodeClientManager {

    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        encodeDefaults = true
    }

    private var cachedKey: String? = null
    private var cachedApi: OpenCodeApiService? = null
    private var cachedSseApi: OpenCodeApiService? = null
    private var cachedSseClient: OkHttpClient? = null

    fun getApi(profile: ServerProfile): OpenCodeApiService {
        val key = cacheKey(profile)
        if (key == cachedKey && cachedApi != null) {
            return cachedApi!!
        }
        rebuildClients(profile)
        return cachedApi!!
    }

    fun getSseApi(profile: ServerProfile): OpenCodeApiService {
        val key = cacheKey(profile)
        if (key == cachedKey && cachedSseApi != null) {
            return cachedSseApi!!
        }
        rebuildClients(profile)
        return cachedSseApi!!
    }

    fun getSseHttpClient(profile: ServerProfile): OkHttpClient {
        val key = cacheKey(profile)
        if (key == cachedKey && cachedSseClient != null) {
            return cachedSseClient!!
        }
        rebuildClients(profile)
        return cachedSseClient!!
    }

    private fun rebuildClients(profile: ServerProfile) {
        val baseUrl = profile.normalizedUrl.let {
            if (it.endsWith("/")) it else "$it/"
        }

        val authAndDirInterceptor = Interceptor { chain ->
            val builder = chain.request().newBuilder()
            if (profile.hasAuth) {
                val user = profile.username.ifBlank { "opencode" }
                builder.header("Authorization", Credentials.basic(user, profile.password))
            }
            if (profile.directory.isNotBlank()) {
                builder.header("x-opencode-directory", profile.directory.trim())
            }
            chain.proceed(builder.build())
        }

        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val restHttpClient = OkHttpClient.Builder()
            .addInterceptor(authAndDirInterceptor)
            .addInterceptor(logging)
            .connectTimeout(AiRetryPolicy.ENDPOINT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .readTimeout(AiRetryPolicy.ENDPOINT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .writeTimeout(AiRetryPolicy.ENDPOINT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .build()

        val sseHttpClient = OkHttpClient.Builder()
            .addInterceptor(authAndDirInterceptor)
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS) // SSE long-lived stream
            .retryOnConnectionFailure(true)
            .build()

        val restRetrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(restHttpClient)
            .addConverterFactory(KotlinxJsonConverterFactory(json))
            .build()

        val sseRetrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(sseHttpClient)
            .addConverterFactory(KotlinxJsonConverterFactory(json))
            .build()

        cachedKey = cacheKey(profile)
        cachedApi = restRetrofit.create(OpenCodeApiService::class.java)
        cachedSseApi = sseRetrofit.create(OpenCodeApiService::class.java)
        cachedSseClient = sseHttpClient
    }

    private fun cacheKey(profile: ServerProfile): String =
        "${profile.normalizedUrl}|${profile.username}|${profile.password}|${profile.directory}"
}

data class DiagnosticError(
    val title: String,
    val detail: String,
    val hint: String
)

object ConnectionDiagnostics {
    fun classifyException(t: Throwable, url: String): DiagnosticError {
        return when (t) {
            is ConnectException -> DiagnosticError(
                title = "Gagal terhubung ke server",
                detail = "Tidak dapat menjangkau $url.",
                hint = "Pastikan OpenCode berjalan dengan: opencode serve --hostname 0.0.0.0 --port 4096"
            )
            is SocketTimeoutException -> DiagnosticError(
                title = "Koneksi timeout",
                detail = "Server di $url tidak merespons tepat waktu.",
                hint = "Periksa firewall atau pastikan perangkat berada di jaringan / Tailscale yang sama."
            )
            is UnknownHostException -> DiagnosticError(
                title = "Host tidak ditemukan",
                detail = "Alamat server '$url' tidak dapat di-resolve.",
                hint = "Periksa ulang IP atau domain server OpenCode Anda."
            )
            is SSLException -> DiagnosticError(
                title = "Kesalahan TLS/SSL",
                detail = t.localizedMessage ?: "Sertifikat HTTPS tidak valid.",
                hint = "Gunakan http:// untuk server lokal atau periksa sertifikat reverse proxy."
            )
            else -> DiagnosticError(
                title = "Terjadi kesalahan jaringan",
                detail = t.localizedMessage ?: t::class.java.simpleName,
                hint = "Cek log server OpenCode atau coba uji koneksi ulang."
            )
        }
    }

    fun classifyHttpCode(code: Int, message: String): DiagnosticError {
        return when (code) {
            401, 403 -> DiagnosticError(
                title = "Autentikasi ditolak ($code)",
                detail = "Server memerlukan Basic Auth yang valid.",
                hint = "Isi username (default: opencode) dan OPENCODE_SERVER_PASSWORD pada profil server."
            )
            404 -> DiagnosticError(
                title = "Endpoint tidak ditemukan (404)",
                detail = message.ifBlank { "Resource tidak tersedia di server." },
                hint = "Pastikan versi OpenCode Anda mendukung server HTTP API (opencode serve)."
            )
            else -> DiagnosticError(
                title = "Error HTTP $code",
                detail = message.ifBlank { "Server mengembalikan status $code." },
                hint = "Periksa output terminal opencode serve untuk detail lebih lanjut."
            )
        }
    }
}
