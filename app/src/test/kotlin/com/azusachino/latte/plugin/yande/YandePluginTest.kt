package com.azusachino.latte.plugin.yande

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class YandePluginTest {

    @Test
    fun testYandePasswordHasher() {
        val hash = YandePasswordHasher.hash("password")
        assertNotNull(hash)
        assertEquals(40, hash.length) // SHA-1 is 40 hex chars
        // Verify deterministic output
        assertEquals(hash, YandePasswordHasher.hash("password"))
    }

    @Test
    fun testCsrfExtraction() {
        val html1 = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="csrf-param" content="authenticity_token" />
                <meta name="csrf-token" content="test_token_12345" />
            </head>
            </html>
        """.trimIndent()
        assertEquals("test_token_12345", YandePlugin.extractCsrfToken(html1))

        val html2 = """
            <head>
                <meta content="alt_token_67890" name="csrf-token" />
            </head>
        """.trimIndent()
        assertEquals("alt_token_67890", YandePlugin.extractCsrfToken(html2))

        val htmlEmpty = "<html><head></head></html>"
        assertNull(YandePlugin.extractCsrfToken(htmlEmpty))
    }
}
