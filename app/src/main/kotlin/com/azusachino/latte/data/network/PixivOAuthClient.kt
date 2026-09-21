package com.azusachino.latte.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Base64
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

data class PixivOAuthConfiguration(
    val clientId: String,
    val clientSecret: String,
    val tokenEndpoint: String = "https://oauth.secure.pixiv.net/auth/token",
)

data class PixivAuthorizationRequest(
    val url: String,
    val codeVerifier: String,
)

object PixivOAuthCallbackBus {
    private val callbackChannel = Channel<String>(Channel.BUFFERED)
    val callbacks = callbackChannel.receiveAsFlow()

    fun publish(uri: String) {
        callbackChannel.trySend(uri)
    }
}

class PixivOAuthClient(
    private val httpClient: OkHttpClient,
    private val configuration: PixivOAuthConfiguration,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    fun authorizationRequest(): PixivAuthorizationRequest? {
        if (configuration.clientId.isBlank() || configuration.clientSecret.isBlank()) return null
        val verifier = randomUrlToken(48)
        val challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
            MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()),
        )
        val url = "https://app-api.pixiv.net/web/v1/login".toHttpUrl().newBuilder()
            .addQueryParameter("code_challenge", challenge)
            .addQueryParameter("code_challenge_method", "S256")
            .addQueryParameter("client", "pixiv-android")
            .build()
            .toString()
        return PixivAuthorizationRequest(url, verifier)
    }

    suspend fun exchangeCode(code: String, codeVerifier: String): PixivSession? = withContext(Dispatchers.IO) {
        if (code.isBlank() || codeVerifier.isBlank()) return@withContext null
        val clientTime = OffsetDateTime.now(ZoneOffset.UTC).format(CLIENT_TIME_FORMAT)
        val request = Request.Builder()
            .url(configuration.tokenEndpoint)
            .header("Accept", "application/json")
            .header("User-Agent", "PixivAndroidApp/5.0.166 (Android; Latte)")
            .header("App-OS", "Android")
            .header("App-Version", "5.0.166")
            .header("X-Client-Time", clientTime)
            .header("X-Client-Hash", md5(clientTime + HASH_SALT))
            .post(
                FormBody.Builder()
                    .add("client_id", configuration.clientId)
                    .add("client_secret", configuration.clientSecret)
                    .add("grant_type", "authorization_code")
                    .add("code", code)
                    .add("code_verifier", codeVerifier)
                    .add("redirect_uri", REDIRECT_URI)
                    .add("include_policy", "true")
                    .build(),
            )
            .build()

        runCatching {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) return@use null
                val envelope = json.decodeFromString<PixivOAuthEnvelope>(body)
                val payload = envelope.response ?: envelope
                val accessToken = payload.accessToken?.takeIf(String::isNotBlank) ?: return@use null
                PixivSession(
                    accessToken = accessToken,
                    refreshToken = payload.refreshToken?.takeIf(String::isNotBlank),
                    userId = payload.user?.id,
                    username = payload.user?.name?.takeIf(String::isNotBlank),
                )
            }
        }.getOrNull()
    }

    suspend fun refresh(refreshToken: String): PixivSession? = withContext(Dispatchers.IO) {
        if (refreshToken.isBlank() || configuration.clientId.isBlank() || configuration.clientSecret.isBlank()) {
            return@withContext null
        }

        val clientTime = OffsetDateTime.now(ZoneOffset.UTC).format(CLIENT_TIME_FORMAT)
        val request = Request.Builder()
            .url(configuration.tokenEndpoint)
            .header("Accept", "application/json")
            .header("User-Agent", "PixivAndroidApp/5.0.166 (Android; Latte)")
            .header("App-OS", "Android")
            .header("App-Version", "5.0.166")
            .header("X-Client-Time", clientTime)
            .header("X-Client-Hash", md5(clientTime + HASH_SALT))
            .post(
                FormBody.Builder()
                    .add("client_id", configuration.clientId)
                    .add("client_secret", configuration.clientSecret)
                    .add("grant_type", "refresh_token")
                    .add("refresh_token", refreshToken)
                    .add("include_policy", "true")
                    .build(),
            )
            .build()

        runCatching {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) return@use null
                val envelope = json.decodeFromString<PixivOAuthEnvelope>(body)
                val payload = envelope.response ?: envelope
                val accessToken = payload.accessToken?.takeIf(String::isNotBlank) ?: return@use null
                PixivSession(
                    accessToken = accessToken,
                    refreshToken = payload.refreshToken?.takeIf(String::isNotBlank) ?: refreshToken,
                    userId = payload.user?.id,
                    username = payload.user?.name?.takeIf(String::isNotBlank),
                )
            }
        }.getOrNull()
    }

    private companion object {
        const val HASH_SALT = "28c1fdd170a5204386cb1313c7077b34f83e4aaf4aa829ce78c231e05b0bae2c"
        val CLIENT_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'+00:00'")
        const val REDIRECT_URI = "https://app-api.pixiv.net/web/v1/users/auth/pixiv/callback"

        fun md5(value: String): String = MessageDigest.getInstance("MD5")
            .digest(value.toByteArray())
            .joinToString("") { byte -> "%02x".format(byte) }

        fun randomUrlToken(byteCount: Int): String {
            val bytes = ByteArray(byteCount)
            SecureRandom().nextBytes(bytes)
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        }
    }
}

@Serializable
private data class PixivOAuthEnvelope(
    @SerialName("response") val response: PixivOAuthEnvelope? = null,
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("refresh_token") val refreshToken: String? = null,
    val user: PixivOAuthUser? = null,
)

@Serializable
private data class PixivOAuthUser(
    val id: Long? = null,
    val name: String? = null,
)
