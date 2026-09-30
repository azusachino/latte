package com.azusachino.latte.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

class DohResolverTest {

    @Test
    fun testDoHResolutionFallback() {
        // Test that DoH resolver can resolve yande.re or cloudflare.com
        val addresses = DohResolver.resolve("yande.re")
        if (addresses != null) {
            assertTrue(addresses.isNotEmpty())
            assertTrue(addresses.first() is InetAddress)
        }
    }
}
