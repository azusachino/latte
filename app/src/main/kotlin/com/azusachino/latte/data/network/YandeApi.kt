package com.azusachino.latte.data.network

import com.azusachino.latte.data.model.PopularPeriod
import com.azusachino.latte.data.model.Post
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

    suspend fun getPopular(period: PopularPeriod, date: LocalDate): List<Post> =
        withContext(Dispatchers.IO) {
            val endpoint = when (period) {
                PopularPeriod.DAY -> "popular_by_day.json"
                PopularPeriod.WEEK -> "popular_by_week.json"
                PopularPeriod.MONTH -> "popular_by_month.json"
                PopularPeriod.YEAR -> "popular_recent.json"
            }
            val urlBuilder = "$baseUrl/post/$endpoint".toHttpUrl().newBuilder()
                .addQueryParameter("year", date.year.toString())
                .addQueryParameter("month", date.monthValue.toString())
                .addQueryParameter("day", date.dayOfMonth.toString())

            executeGetPosts(urlBuilder.build().toString())
        }

    suspend fun getPost(id: Long): Post? = withContext(Dispatchers.IO) {
        val posts = getPosts(page = 1, limit = 1, tags = "id:$id")
        posts.firstOrNull()
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
}
