package com.azusachino.latte.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToastQueueTest {

    private fun ToastQueue.messages(): List<String> = visible.value.map { it.message }

    @Test
    fun newestToastIsFirstAndAllShownAtOnce() {
        val queue = ToastQueue()
        queue.enqueue("first", ToastType.INFO)
        queue.enqueue("second", ToastType.INFO)
        queue.enqueue("third", ToastType.SUCCESS)

        assertEquals(listOf("third", "second", "first"), queue.messages())
    }

    @Test
    fun fourthToastDropsOldestAndCapsAtThree() {
        val queue = ToastQueue()
        queue.enqueue("a", ToastType.INFO)
        queue.enqueue("b", ToastType.INFO)
        queue.enqueue("c", ToastType.INFO)
        queue.enqueue("d", ToastType.INFO)

        assertEquals(listOf("d", "c", "b"), queue.messages())
    }

    @Test
    fun duplicateOfVisibleToastMovesItToFrontWithoutCopying() {
        val queue = ToastQueue()
        val a = queue.enqueue("a", ToastType.INFO)
        queue.enqueue("b", ToastType.INFO)
        queue.enqueue("c", ToastType.INFO)

        val restarted = queue.enqueue("a", ToastType.INFO)

        assertEquals(a, restarted)
        assertEquals(listOf("a", "c", "b"), queue.messages())
    }

    @Test
    fun duplicateAtFrontKeepsStackUnchanged() {
        val queue = ToastQueue()
        queue.enqueue("a", ToastType.INFO)
        val b = queue.enqueue("b", ToastType.INFO)

        queue.enqueue("b", ToastType.INFO)

        assertEquals(listOf("b", "a"), queue.messages())
        assertEquals(b, queue.visible.value.first())
    }

    @Test
    fun sameTextWithDifferentTypeIsNotADuplicate() {
        val queue = ToastQueue()
        queue.enqueue("Failed", ToastType.INFO)
        queue.enqueue("Failed", ToastType.ERROR)

        assertEquals(2, queue.visible.value.size)
        assertEquals(ToastType.ERROR, queue.visible.value.first().type)
    }

    @Test
    fun dismissRemovesOnlyThatToast() {
        val queue = ToastQueue()
        queue.enqueue("a", ToastType.INFO)
        val b = queue.enqueue("b", ToastType.INFO)
        queue.enqueue("c", ToastType.INFO)

        assertTrue(queue.dismiss(b.id))

        assertEquals(listOf("c", "a"), queue.messages())
    }

    @Test
    fun staleTimerIdCannotDismissAnotherToast() {
        val queue = ToastQueue()
        val first = queue.enqueue("first", ToastType.INFO)
        assertTrue(queue.dismiss(first.id))

        // A newer toast gets a fresh id, so the old timer's id no longer matches anything.
        val second = queue.enqueue("second", ToastType.INFO)

        assertFalse("stale timer for an already dismissed toast must not dismiss the next one", queue.dismiss(first.id))
        assertEquals(listOf(second.id), queue.visible.value.map { it.id })
    }

    @Test
    fun dismissingUnknownIdIsIgnored() {
        val queue = ToastQueue()
        queue.enqueue("a", ToastType.INFO)

        assertFalse(queue.dismiss(999L))
        assertEquals(listOf("a"), queue.messages())
    }

    @Test
    fun sameMessageCanBeShownAgainAfterItWasDismissed() {
        val queue = ToastQueue()
        val first = queue.enqueue("again", ToastType.INFO)
        queue.dismiss(first.id)

        val second = queue.enqueue("again", ToastType.INFO)

        assertTrue(second.id != first.id)
        assertEquals(listOf("again"), queue.messages())
    }

    @Test
    fun errorDurationIsLongerThanInfo() {
        val info = ToastMessage(id = 1, message = "m", type = ToastType.INFO)
        val error = ToastMessage(id = 2, message = "m", type = ToastType.ERROR)

        assertEquals(2_500L, info.durationMs)
        assertEquals(5_000L, error.durationMs)
        assertTrue(error.durationMs > info.durationMs)
    }

    @Test
    fun warningDurationSitsBetweenInfoAndError() {
        val warning = ToastMessage(id = 1, message = "m", type = ToastType.WARNING)
        assertEquals(4_000L, warning.durationMs)
    }

    @Test
    fun toastWithActionStaysVisibleAtLeastFiveSeconds() {
        val info = ToastMessage(id = 1, message = "m", type = ToastType.INFO, actionLabel = "Undo")
        val warning = ToastMessage(id = 2, message = "m", type = ToastType.WARNING, actionLabel = "Undo")

        assertEquals(5_000L, info.durationMs)
        assertEquals(5_000L, warning.durationMs)
    }

    @Test
    fun invokingActionRunsCallbackAndRemovesOnlyThatToast() {
        val queue = ToastQueue()
        var invocations = 0
        val undo = queue.enqueue("Removed", ToastType.INFO, actionLabel = "Undo") { invocations++ }
        queue.enqueue("Next", ToastType.INFO)

        assertTrue(queue.invokeAction(undo.id))

        assertEquals(1, invocations)
        assertEquals(listOf("Next"), queue.messages())
    }

    @Test
    fun invokingActionOnStaleToastDoesNothing() {
        val queue = ToastQueue()
        var invocations = 0
        val stale = queue.enqueue("Removed", ToastType.INFO, actionLabel = "Undo") { invocations++ }
        queue.dismiss(stale.id)

        assertFalse(queue.invokeAction(stale.id))
        assertEquals(0, invocations)
    }

    @Test
    fun tappingBodyDismissesWithoutRunningAction() {
        val queue = ToastQueue()
        var invocations = 0
        val toast = queue.enqueue("Removed", ToastType.INFO, actionLabel = "Undo") { invocations++ }

        assertTrue(queue.dismiss(toast.id))

        assertEquals(0, invocations)
        assertTrue(queue.visible.value.isEmpty())
    }
}
