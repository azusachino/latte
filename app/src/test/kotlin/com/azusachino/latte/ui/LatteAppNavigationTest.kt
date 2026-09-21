package com.azusachino.latte.ui

import com.azusachino.latte.ui.explore.dispatchExploreBack
import com.azusachino.latte.ui.explore.shouldHandleSearchBack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LatteAppNavigationTest {
    @Test
    fun authorWorksArrowPopsNavigationInsteadOfClearingSearch() {
        var popped = false
        var cleared = false

        dispatchExploreBack(onNestedBack = { popped = true }) { cleared = true }

        assertTrue(popped)
        assertFalse(cleared)
    }

    @Test
    fun authorWorksSearchDoesNotConsumeAppBack() {
        assertTrue(shouldHandleSearchBack(isRootScreen = true, isSearch = true))
        assertFalse(shouldHandleSearchBack(isRootScreen = false, isSearch = true))
    }

    @Test
    fun authorWorksBackReturnsToTheDetailScreen() {
        val detail = Screen.Detail(posts = emptyList(), initialIndex = 2)
        var navigation = ScreenStack()

        val authorWorks = Screen.AuthorWorks(authorId = 42L, authorName = "Artist")
        navigation = navigation.push(detail)
        navigation = navigation.push(authorWorks)

        assertEquals(authorWorks, navigation.current)
        navigation = navigation.pop()
        assertEquals(detail, navigation.current)
        navigation = navigation.pop()
        assertEquals(Screen.Explore, navigation.current)
    }

    @Test
    fun tagSearchBackReturnsToTheDetailScreen() {
        val detail = Screen.Detail(posts = emptyList(), initialIndex = 0)
        var navigation = ScreenStack()
            .push(detail)
            .push(Screen.TagSearch(query = "random-tag"))

        assertEquals(Screen.TagSearch(query = "random-tag"), navigation.current)
        navigation = navigation.pop()

        assertEquals(detail, navigation.current)
    }

    @Test
    fun deepNavigationStackPreservesFullHistoryThroughNestedDetails() {
        val detail1 = Screen.Detail(posts = emptyList(), initialIndex = 1)
        val authorWorks = Screen.AuthorWorks(authorId = 123L, authorName = "torino")
        val detail2 = Screen.Detail(posts = emptyList(), initialIndex = 2)
        val tagSearch = Screen.TagSearch(query = "citlali")
        val detail3 = Screen.Detail(posts = emptyList(), initialIndex = 3)

        var navigation = ScreenStack()
            .push(detail1)
            .push(authorWorks)
            .push(detail2)
            .push(tagSearch)
            .push(detail3)

        assertEquals(6, navigation.size)
        assertEquals(detail3, navigation.current)

        navigation = navigation.pop()
        assertEquals(5, navigation.size)
        assertEquals(tagSearch, navigation.current)

        navigation = navigation.pop()
        assertEquals(4, navigation.size)
        assertEquals(detail2, navigation.current)

        navigation = navigation.pop()
        assertEquals(3, navigation.size)
        assertEquals(authorWorks, navigation.current)

        navigation = navigation.pop()
        assertEquals(2, navigation.size)
        assertEquals(detail1, navigation.current)

        navigation = navigation.pop()
        assertEquals(1, navigation.size)
        assertEquals(Screen.Explore, navigation.current)
    }
}
