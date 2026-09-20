package com.azusachino.latte.data.network

import com.azusachino.latte.data.model.PoolSummary
import com.azusachino.latte.data.model.PopularPeriod
import com.azusachino.latte.data.model.Post
import com.azusachino.latte.data.model.YandePoolDto
import com.azusachino.latte.data.model.YandePostDto
import com.azusachino.latte.data.model.toDomain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class YandeApi(private val baseUrl: String = "https://yande.re") {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    suspend fun getPosts(page: Int = 1, limit: Int = 100, tags: String? = null): List<Post> =
        withContext(Dispatchers.IO) {
            val urlBuilder = "$baseUrl/post.json".toHttpUrl().newBuilder()
                .addQueryParameter("page", page.toString())
                .addQueryParameter("limit", limit.toString())

            if (!tags.isNullOrBlank()) {
                urlBuilder.addQueryParameter("tags", tags)
            }

            executeGetPosts(urlBuilder.build().toString())
        }

    suspend fun getPopular(
        period: PopularPeriod,
        date: LocalDate,
        page: Int = 1,
        limit: Int = 100,
        safeMode: Boolean = false,
    ): List<Post> {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val baseTags = when (period) {
            PopularPeriod.DAY -> "order:score date:${date.format(formatter)}"
            PopularPeriod.WEEK -> {
                val start = date.minusDays((date.dayOfWeek.value - 1).toLong())
                val end = start.plusDays(6)
                "order:score date:${start.format(formatter)}..${end.format(formatter)}"
            }
            PopularPeriod.MONTH -> {
                val start = date.withDayOfMonth(1)
                val end = date.withDayOfMonth(date.lengthOfMonth())
                "order:score date:${start.format(formatter)}..${end.format(formatter)}"
            }
            PopularPeriod.YEAR -> {
                val start = date.withDayOfYear(1)
                val end = date.withDayOfYear(date.lengthOfYear())
                "order:score date:${start.format(formatter)}..${end.format(formatter)}"
            }
        }
        val tags = if (safeMode) "$baseTags rating:safe" else baseTags
        return getPosts(page = page, limit = limit, tags = tags)
    }

    suspend fun getPost(id: Long): Post? = withContext(Dispatchers.IO) {
        val posts = getPosts(page = 1, limit = 1, tags = "id:$id")
        posts.firstOrNull()
    }

    // Pool posts are reached through the regular post search via `pool:<id>`
    // (verified live: matches pool/show.json's post order exactly), so
    // viewing a pool's contents reuses the existing search feed/grid --
    // this only needs to list/search pools themselves.
    suspend fun getPools(query: String? = null, page: Int = 1): List<PoolSummary> =
        withContext(Dispatchers.IO) {
            val urlBuilder = "$baseUrl/pool.json".toHttpUrl().newBuilder()
                .addQueryParameter("page", page.toString())

            if (!query.isNullOrBlank()) {
                urlBuilder.addQueryParameter("query", query)
            }

            executeGetPools(urlBuilder.build().toString())
        }

    private fun executeGetPosts(urlString: String): List<Post> {
        val request = Request.Builder()
            .url(urlString)
            .header("Accept", "application/json")
            .build()

        val response = OkHttpProvider.client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw IOException("Unexpected HTTP response: ${response.code} ${response.message}")
        }

        val body = response.body?.string().orEmpty()
        val dtos = json.decodeFromString<List<YandePostDto>>(body)
        return dtos.map { it.toDomain() }
    }

    private fun executeGetPools(urlString: String): List<PoolSummary> {
        val request = Request.Builder()
            .url(urlString)
            .header("Accept", "application/json")
            .build()

        val response = OkHttpProvider.client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw IOException("Unexpected HTTP response: ${response.code} ${response.message}")
        }

        val body = response.body?.string().orEmpty()
        val dtos = json.decodeFromString<List<YandePoolDto>>(body)
        return dtos.map { it.toDomain() }
    }
}
