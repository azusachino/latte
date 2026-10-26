package com.azusachino.latte.ui.explore

import com.azusachino.latte.plugin.PluginFeedResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class FeedErrorMessageTest {

    private val offline = FeedError("You're offline", "Check your connection, then try again.")

    @Test
    fun unknownHostExceptionIsOffline() {
        assertEquals(offline, UnknownHostException("yande.re").toFeedError("Yande"))
    }

    @Test
    fun wrappedConnectExceptionIsOffline() {
        val wrapped = IOException("request failed", ConnectException("refused"))
        assertEquals(offline, wrapped.toFeedError("Yande"))
    }

    @Test
    fun socketTimeoutIsOffline() {
        assertEquals(offline, SocketTimeoutException("timeout").toFeedError("Yande"))
    }

    @Test
    fun transportFailureUnableToResolveHostIsOffline() {
        val failure = PluginFeedResult.TransportFailure(
            "Unable to resolve host \"yande.re\": No address associated with hostname",
        )
        assertEquals(offline, failure.toFeedError("Yande"))
    }

    @Test
    fun feedLoadFailureWrappingTransportFailureIsOffline() {
        val failure = FeedLoadFailure(PluginFeedResult.TransportFailure("Failed to connect to yande.re"))
        assertEquals(offline, failure.toFeedError("Yande"))
    }

    @Test
    fun rateLimitedWithRetryAfterMentionsSeconds() {
        assertEquals(
            FeedError("Too many requests", "Wait 30s, then try again."),
            PluginFeedResult.RateLimited(30).toFeedError("Pixiv"),
        )
    }

    @Test
    fun rateLimitedWithoutRetryAfterUsesGenericWait() {
        assertEquals(
            FeedError("Too many requests", "Wait a moment, then try again."),
            PluginFeedResult.RateLimited(null).toFeedError("Pixiv"),
        )
    }

    @Test
    fun upstreamDriftSaysSiteChangedFormat() {
        assertEquals(
            FeedError("This site changed its format", "Update Latte, or try again later."),
            PluginFeedResult.UpstreamDrift("feed response").toFeedError("Pixiv"),
        )
    }

    @Test
    fun serverErrorNamesThePlatform() {
        assertEquals(
            FeedError("Pixiv is having trouble", "Try again later."),
            PluginFeedResult.TransportFailure("Pixiv feed failed with HTTP 503").toFeedError("Pixiv"),
        )
    }

    @Test
    fun clientErrorFallsBackToGeneric() {
        assertEquals(GenericFeedError, PluginFeedResult.TransportFailure("Pixiv feed failed with HTTP 404").toFeedError("Pixiv"))
    }

    @Test
    fun unknownExceptionIsGenericAndLeaksNoRawText() {
        val mapped = IllegalStateException("secret internal detail").toFeedError("Yande")

        assertEquals(GenericFeedError, mapped)
        assertNoRawText(mapped)
    }

    @Test
    fun everyMappedFailureHidesRawClassNames() {
        val failures: List<Throwable> = listOf(
            UnknownHostException("yande.re"),
            IllegalStateException("boom"),
            FeedLoadFailure(PluginFeedResult.TransportFailure("Unable to resolve host \"x\"")),
            FeedLoadFailure(PluginFeedResult.TransportFailure("weird body")),
            FeedLoadFailure(PluginFeedResult.UpstreamDrift("feed response")),
            FeedLoadFailure(PluginFeedResult.RateLimited(5)),
        )
        failures.forEach { assertNoRawText(it.toFeedError("Yande")) }
    }

    @Test
    fun authRequiredHasSignInTitleWithoutRawText() {
        val mapped = PluginFeedResult.AuthRequired.toFeedError("Yande")
        assertEquals("Sign in required", mapped.title)
        assertNoRawText(mapped)
    }

    private fun assertNoRawText(error: FeedError) {
        val text = listOfNotNull(error.title, error.hint).joinToString(" ")
        assertFalse("raw class name leaked: $text", text.contains("Exception"))
        assertFalse("raw class name leaked: $text", text.contains("TransportFailure"))
        assertFalse("raw message leaked: $text", text.contains("secret"))
    }
}
