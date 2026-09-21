package com.azusachino.latte.data.network

import com.azusachino.latte.data.model.PixivIllustListResponse
import com.azusachino.latte.data.model.PixivIllustResponse
import com.azusachino.latte.data.model.Post
import com.azusachino.latte.data.model.toPost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.time.LocalDate

data class PixivSession(
    val accessToken: String,
    val refreshToken: String? = null,
    val userId: Long? = null,
    val username: String? = null,
)

class PixivApi(
    private val httpClient: OkHttpClient = OkHttpProvider.client,
    private val baseUrl: String = "https://app-api.pixiv.net",
    private val sessionProvider: () -> PixivSession? = { null },
) {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    suspend fun load(
        request: PixivFeedRequest,
        today: LocalDate = LocalDate.now(),
    ): PixivFeedResult = withContext(Dispatchers.IO) {
        val session = sessionProvider()
        val url = try {
            requestUrl(request, today, session)
        } catch (e: IllegalArgumentException) {
            return@withContext if (e.message == "Pixiv sign-in required") {
                PixivFeedResult.AuthRequired
            } else {
                PixivFeedResult.TransportFailure(e.message ?: "Invalid Pixiv request")
            }
        }

        when (val response = executeJson(url, "${request.kind.name.lowercase()} feed") {
            json.decodeFromString<PixivIllustListResponse>(it)
        }) {
            is ReadResult.Success -> response.value.toFeedResult(url)
            ReadResult.AuthRequired -> PixivFeedResult.AuthRequired
            is ReadResult.RateLimited -> PixivFeedResult.RateLimited(response.retryAfterSeconds)
            is ReadResult.UpstreamDrift -> PixivFeedResult.UpstreamDrift(response.operation)
            is ReadResult.TransportFailure -> PixivFeedResult.TransportFailure(response.message)
        }
    }

    suspend fun detail(workId: Long): PixivDetailResult = withContext(Dispatchers.IO) {
        if (workId <= 0) return@withContext PixivDetailResult.UpstreamDrift("illust detail")
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegments("v1/illust/detail")
            .addQueryParameter("filter", "for_android")
            .addQueryParameter("illust_id", workId.toString())
            .build()

        when (val response = executeJson(url, "illust detail") {
            json.decodeFromString<PixivIllustResponse>(it)
        }) {
            is ReadResult.Success -> response.value.illust?.let { illust ->
                runCatching { PixivDetailResult.Success(illust.toPost()) }
                    .getOrElse { PixivDetailResult.UpstreamDrift("illust detail") }
            } ?: PixivDetailResult.UpstreamDrift("illust detail")
            ReadResult.AuthRequired -> PixivDetailResult.AuthRequired
            is ReadResult.RateLimited -> PixivDetailResult.RateLimited(response.retryAfterSeconds)
            is ReadResult.UpstreamDrift -> PixivDetailResult.UpstreamDrift(response.operation)
            is ReadResult.TransportFailure -> PixivDetailResult.TransportFailure(response.message)
        }
    }

    suspend fun bookmark(workId: Long, bookmarked: Boolean): PixivBookmarkResult =
        withContext(Dispatchers.IO) {
            if (workId <= 0) return@withContext PixivBookmarkResult.UpstreamDrift("bookmark")
            if (sessionProvider()?.accessToken.isNullOrBlank()) {
                return@withContext PixivBookmarkResult.AuthRequired
            }

            val path = if (bookmarked) {
                "v2/illust/bookmark/add"
            } else {
                "v1/illust/bookmark/delete"
            }
            val bodyBuilder = FormBody.Builder().add("illust_id", workId.toString())
            if (bookmarked) bodyBuilder.add("restrict", "public")
            val request = request(baseUrl.toHttpUrl().newBuilder().addPathSegments(path).build())
                .post(bodyBuilder.build())
                .build()

            when (val response = execute(request, "bookmark") { Unit }) {
                is ReadResult.Success -> PixivBookmarkResult.Success
                ReadResult.AuthRequired -> PixivBookmarkResult.AuthRequired
                is ReadResult.RateLimited -> PixivBookmarkResult.RateLimited(response.retryAfterSeconds)
                is ReadResult.UpstreamDrift -> PixivBookmarkResult.UpstreamDrift(response.operation)
                is ReadResult.TransportFailure -> PixivBookmarkResult.TransportFailure(response.message)
            }
        }

    suspend fun autocomplete(query: String): PixivSupportResult = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext PixivSupportResult.Success(emptyList())
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegments("v2/search/autocomplete")
            .addQueryParameter("merge_plain_keyword_results", "true")
            .addQueryParameter("word", query.trim())
            .build()
        when (val response = executeJson(url, "autocomplete") {
            json.decodeFromString<PixivAutocompleteResponse>(it)
        }) {
            is ReadResult.Success -> PixivSupportResult.Success(response.value.tags.mapNotNull { it.name.takeIf(String::isNotBlank) })
            ReadResult.AuthRequired -> PixivSupportResult.AuthRequired
            is ReadResult.RateLimited -> PixivSupportResult.RateLimited(response.retryAfterSeconds)
            is ReadResult.UpstreamDrift -> PixivSupportResult.UpstreamDrift(response.operation)
            is ReadResult.TransportFailure -> PixivSupportResult.TransportFailure(response.message)
        }
    }

    suspend fun trendingTags(): PixivSupportResult = withContext(Dispatchers.IO) {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegments("v1/trending-tags/illust")
            .addQueryParameter("filter", "for_android")
            .build()
        when (val response = executeJson(url, "trending tags") {
            json.decodeFromString<PixivTrendingTagsResponse>(it)
        }) {
            is ReadResult.Success -> PixivSupportResult.Success(
                response.value.tags.mapNotNull { it.tag.takeIf(String::isNotBlank) },
            )
            ReadResult.AuthRequired -> PixivSupportResult.AuthRequired
            is ReadResult.RateLimited -> PixivSupportResult.RateLimited(response.retryAfterSeconds)
            is ReadResult.UpstreamDrift -> PixivSupportResult.UpstreamDrift(response.operation)
            is ReadResult.TransportFailure -> PixivSupportResult.TransportFailure(response.message)
        }
    }

    private fun requestUrl(
        request: PixivFeedRequest,
        today: LocalDate,
        session: PixivSession?,
    ): HttpUrl {
        request.cursor?.let { cursor ->
            val cursorUrl = cursor.toHttpUrl()
            require(isSameApiHost(cursorUrl)) { "Pixiv continuation host is not trusted" }
            return cursorUrl
        }

        val builder = baseUrl.toHttpUrl().newBuilder()
        when (request.kind) {
            PixivFeedKind.POPULAR -> builder
                .addPathSegments("v1/illust/ranking")
                .addQueryParameter("filter", "for_android")
                .addQueryParameter("mode", "day")
                .addQueryParameter("date", today.toString())
            PixivFeedKind.FOLLOWED_UPDATES -> {
                require(session?.accessToken?.isNotBlank() == true) { "Pixiv sign-in required" }
                builder.addPathSegments("v2/illust/follow")
                    .addQueryParameter("restrict", "public")
            }
            PixivFeedKind.FAVORITES -> {
                require(session?.accessToken?.isNotBlank() == true && (session.userId ?: 0) > 0) {
                    "Pixiv sign-in required"
                }
                builder.addPathSegments("v1/user/bookmarks/illust")
                    .addQueryParameter("user_id", session.userId.toString())
                    .addQueryParameter("restrict", "public")
            }
            PixivFeedKind.SEARCH -> {
                require(!request.query.isNullOrBlank()) { "Pixiv search query required" }
                builder.addPathSegments("v1/search/illust")
                    .addQueryParameter("filter", "for_android")
                    .addQueryParameter("merge_plain_keyword_results", "true")
                    .addQueryParameter("word", request.query.trim())
            }
        }
        return builder.build()
    }

    private fun PixivIllustListResponse.toFeedResult(requestUrl: HttpUrl): PixivFeedResult {
        if (illusts.isEmpty()) return PixivFeedResult.Empty
        val posts = illusts.mapNotNull { dto ->
            runCatching {
                require(dto.id > 0)
                dto.toPost()
            }.getOrNull()
        }
        if (posts.isEmpty()) return PixivFeedResult.UpstreamDrift("feed response")
        if (nextUrl != null && !isSameApiHost(nextUrl.toHttpUrl())) {
            return PixivFeedResult.UpstreamDrift("feed continuation")
        }
        return PixivFeedResult.Success(PixivFeedPage(posts, nextUrl))
    }

    private fun isSameApiHost(url: HttpUrl): Boolean {
        val base = baseUrl.toHttpUrl()
        return url.scheme == base.scheme && url.host == base.host
    }

    private fun request(url: HttpUrl): Request.Builder = Request.Builder()
        .url(url)
        .header("Accept", "application/json")
        .header("App-OS", "Android")
        .header("App-Version", "6.135.0")
        .apply {
            sessionProvider()?.accessToken?.takeIf(String::isNotBlank)?.let {
                header("Authorization", "Bearer $it")
            }
        }

    private fun <T> executeJson(
        url: HttpUrl,
        operation: String,
        decode: (String) -> T,
    ): ReadResult<T> = execute(request(url).build(), operation, decode)

    private fun <T> execute(
        request: Request,
        operation: String,
        decode: (String) -> T,
    ): ReadResult<T> {
        return try {
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                when {
                    response.code == 401 || response.code == 403 -> ReadResult.AuthRequired
                    response.code == 408 || response.code == 429 || response.code in 500..599 ->
                        ReadResult.RateLimited(response.header("Retry-After")?.toLongOrNull())
                    !response.isSuccessful -> ReadResult.TransportFailure(
                        "Pixiv $operation failed with HTTP ${response.code}",
                    )
                    body.isBlank() -> ReadResult.UpstreamDrift(operation)
                    else -> runCatching { ReadResult.Success(decode(body)) }
                        .getOrElse { ReadResult.UpstreamDrift(operation) }
                }
            }
        } catch (e: IOException) {
            ReadResult.TransportFailure(e.message ?: "Pixiv network request failed")
        } catch (e: IllegalArgumentException) {
            ReadResult.UpstreamDrift(operation)
        }
    }

    private sealed interface ReadResult<out T> {
        data class Success<T>(val value: T) : ReadResult<T>

        data object AuthRequired : ReadResult<Nothing>

        data class RateLimited(val retryAfterSeconds: Long?) : ReadResult<Nothing>

        data class UpstreamDrift(val operation: String) : ReadResult<Nothing>

        data class TransportFailure(val message: String) : ReadResult<Nothing>
    }
}

@Serializable
private data class PixivAutocompleteResponse(
    val tags: List<PixivAutocompleteTag> = emptyList(),
)

@Serializable
private data class PixivAutocompleteTag(
    val name: String = "",
)

@Serializable
private data class PixivTrendingTagsResponse(
    @SerialName("trend_tags") val tags: List<PixivTrendingTag> = emptyList(),
)

@Serializable
private data class PixivTrendingTag(
    val tag: String = "",
)
