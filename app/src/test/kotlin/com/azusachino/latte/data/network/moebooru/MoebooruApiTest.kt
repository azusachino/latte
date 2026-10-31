package com.azusachino.latte.data.network.moebooru

import org.junit.Assert.assertEquals
import org.junit.Test

class MoebooruApiTest {

    @Test
    fun buildsSiteOwnedFavoriteAndPoolQueries() {
        assertEquals("vote:3:alice order:vote", MoebooruApi.favoriteTags(" alice "))
        assertEquals("pool:42", MoebooruApi.poolTags(42L))
    }

    @Test
    fun ownsSafeModeQueryPolicy() {
    }
}
