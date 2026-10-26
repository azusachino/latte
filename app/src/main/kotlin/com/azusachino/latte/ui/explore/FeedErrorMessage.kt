package com.azusachino.latte.ui.explore

import com.azusachino.latte.data.model.Post
import com.azusachino.latte.plugin.PluginFeedResult
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** User-facing feed failure: a short title and a one-line hint. Never raw exception text. */
data class FeedError(val title: String, val hint: String? = null)

/** Carries a non-success [PluginFeedResult] through a throw so every loader maps it the same way. */
internal class FeedLoadFailure(val result: PluginFeedResult) : Exception(result.toString())

/** Success yields [filter] applied to its items; [PluginFeedResult.Empty] is an empty feed, not a failure. */
internal fun PluginFeedResult.feedPostsOrThrow(filter: (List<Post>) -> List<Post>): List<Post> = when (this) {
    is PluginFeedResult.Success -> filter(page.items)
    PluginFeedResult.Empty -> emptyList()
    else -> throw FeedLoadFailure(this)
}

internal val GenericFeedError = FeedError("Couldn't load posts", "Try again.")

private val OfflineError = FeedError("You're offline", "Check your connection, then try again.")
private val OfflineMarkers = listOf("unable to resolve host", "failed to connect", "timeout", "timed out")
private val ServerErrorPattern = Regex("""\bHTTP 5\d\d\b""")

/** Maps any feed failure to a message. [site] is the platform display name, e.g. "Yande". */
internal fun Throwable.toFeedError(site: String): FeedError {
    if (this is FeedLoadFailure) return result.toFeedError(site)
    val causes = generateSequence(this) { it.cause }.take(MAX_CAUSE_DEPTH)
    if (causes.any { it.isOfflineType() }) return OfflineError
    return messageError(message.orEmpty(), site)
}

internal fun PluginFeedResult.toFeedError(site: String): FeedError = when (this) {
    PluginFeedResult.AuthRequired -> FeedError("Sign in required", "Sign in, then try again.")
    is PluginFeedResult.RateLimited -> FeedError(
        title = "Too many requests",
        hint = retryAfterSeconds?.let { "Wait ${it}s, then try again." } ?: "Wait a moment, then try again.",
    )
    is PluginFeedResult.UpstreamDrift -> FeedError("This site changed its format", "Update Latte, or try again later.")
    is PluginFeedResult.TransportFailure -> messageError(message, site)
    PluginFeedResult.Empty, is PluginFeedResult.Success -> GenericFeedError
}

private const val MAX_CAUSE_DEPTH = 8

private fun Throwable.isOfflineType(): Boolean =
    this is UnknownHostException ||
        this is ConnectException ||
        this is SocketTimeoutException ||
        this is SSLException

private fun messageError(message: String, site: String): FeedError {
    val lower = message.lowercase()
    return when {
        OfflineMarkers.any { it in lower } -> OfflineError
        ServerErrorPattern.containsMatchIn(message) -> FeedError("$site is having trouble", "Try again later.")
        else -> GenericFeedError
    }
}
