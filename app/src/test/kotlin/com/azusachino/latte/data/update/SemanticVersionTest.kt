package com.azusachino.latte.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SemanticVersionTest {

    @Test
    fun parseStandardVersions() {
        val v1 = SemanticVersion.parse("0.1.2")
        assertNotNull(v1)
        assertEquals(0, v1!!.major)
        assertEquals(1, v1.minor)
        assertEquals(2, v1.patch)
        assertNull(v1.prerelease)

        val v2 = SemanticVersion.parse("v1.20.3-beta.1")
        assertNotNull(v2)
        assertEquals(1, v2!!.major)
        assertEquals(20, v2.minor)
        assertEquals(3, v2.patch)
        assertEquals("beta.1", v2.prerelease)
    }

    @Test
    fun parseInvalidVersions() {
        assertNull(SemanticVersion.parse(""))
        assertNull(SemanticVersion.parse("abc"))
    }

    @Test
    fun compareVersionsCorrectly() {
        val v012 = SemanticVersion.parse("0.1.2")!!
        val v013 = SemanticVersion.parse("v0.1.3")!!
        val v020 = SemanticVersion.parse("0.2.0")!!
        val v100 = SemanticVersion.parse("1.0.0")!!

        assertTrue(v013 > v012)
        assertTrue(v020 > v013)
        assertTrue(v100 > v020)
        assertFalse(v012 > v012)
        assertEquals(0, v012.compareTo(SemanticVersion.parse("0.1.2")!!))
    }

    @Test
    fun comparePrereleaseVsFinal() {
        val beta = SemanticVersion.parse("1.0.0-beta")!!
        val final = SemanticVersion.parse("1.0.0")!!
        assertTrue(final > beta)
    }
}
