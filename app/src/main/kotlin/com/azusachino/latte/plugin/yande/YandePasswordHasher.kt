package com.azusachino.latte.plugin.yande

import java.security.MessageDigest

object YandePasswordHasher {
    private const val SALT_PREFIX = "choujin-steiner--"
    private const val SALT_SUFFIX = "--"

    fun hash(password: String): String {
        val salted = "$SALT_PREFIX$password$SALT_SUFFIX"
        val md = MessageDigest.getInstance("SHA-1")
        val digest = md.digest(salted.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
