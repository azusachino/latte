package com.azusachino.latte.data.network

import org.junit.Assert.assertEquals
import org.junit.Test

class YandeApiTest {

    @Test
    fun buildsSiteOwnedFavoriteAndPoolQueries() {
        assertEquals("vote:3:alice", YandeApi.favoriteTags(" alice "))
        assertEquals("pool:42", YandeApi.poolTags(42L))
    }

    @Test
    fun ownsSafeModeQueryPolicy() {
        assertEquals("rating:safe", YandeApi.safeModeTags(null, enabled = true))
        assertEquals("artist rating:safe", YandeApi.safeModeTags("artist", enabled = true))
        assertEquals("rating:questionable", YandeApi.safeModeTags("rating:questionable", enabled = true))
        assertEquals("artist", YandeApi.safeModeTags("artist", enabled = false))
    }
}
