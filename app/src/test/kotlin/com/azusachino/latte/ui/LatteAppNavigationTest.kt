package com.azusachino.latte.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class LatteAppNavigationTest {
    @Test
    fun authorWorksBackReturnsToTheDetailScreen() {
        val detail = Screen.Detail(posts = emptyList(), initialIndex = 2)
        var navigation = ScreenStack()

        navigation = navigation.push(detail)
        navigation = navigation.push(Screen.AuthorWorks)

        assertEquals(Screen.AuthorWorks, navigation.current)
        navigation = navigation.pop()
        assertEquals(detail, navigation.current)
        navigation = navigation.pop()
        assertEquals(Screen.Explore, navigation.current)
    }
}
