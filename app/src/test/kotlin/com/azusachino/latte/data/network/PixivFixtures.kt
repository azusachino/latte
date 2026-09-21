package com.azusachino.latte.data.network

object PixivFixtures {
    fun read(name: String): String =
        requireNotNull(javaClass.getResourceAsStream("/pixiv/$name")) {
            "Missing Pixiv fixture: $name"
        }.bufferedReader().use { it.readText() }
}
