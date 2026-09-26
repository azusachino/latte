package com.azusachino.latte.data.network.moebooru

import org.junit.Assert.assertEquals
import org.junit.Test

class MoebooruApiTest {

    @Test
    fun buildsSiteOwnedFavoriteAndPoolQueries() {
        assertEquals("vote:3:alice", MoebooruApi.favoriteTags(" alice "))
        assertEquals("pool:42", MoebooruApi.poolTags(42L))
    }

    @Test
    fun ownsSafeModeQueryPolicy() {
        assertEquals("rating:safe", MoebooruApi.safeModeTags(null, enabled = true))
        assertEquals("artist rating:safe", MoebooruApi.safeModeTags("artist", enabled = true))
        assertEquals("rating:questionable", MoebooruApi.safeModeTags("rating:questionable", enabled = true))
        assertEquals("artist", MoebooruApi.safeModeTags("artist", enabled = false))
    }
}
