package com.azusachino.latte.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ToastQueueTest {

    @Test
    fun showsOneToastAtATimeInOrder() {
        val queue = ToastQueue()
        val first = queue.enqueue("first", ToastType.INFO)
        assertEquals("first", queue.current.value?.message)
        assertEquals(first, queue.current.value)

        assertNull(queue.enqueue("second", ToastType.INFO))
        assertNull(queue.enqueue("third", ToastType.SUCCESS))
        assertEquals("first", queue.current.value?.message)

        assertTrue(queue.dismiss(first!!.id))
        assertEquals("second", queue.current.value?.message)

        assertTrue(queue.dismiss(queue.current.value!!.id))
        assertEquals("third", queue.current.value?.message)

        assertTrue(queue.dismiss(queue.current.value!!.id))
        assertNull(queue.current.value)
    }

    @Test
    fun dismissingNonVisibleOrStaleIdIsIgnored() {
        val queue = ToastQueue()
        val first = queue.enqueue("first", ToastType.INFO)!!
        queue.enqueue("second", ToastType.INFO)

        assertFalse(queue.dismiss(999L))
        assertEquals("first", queue.current.value?.message)

        assertTrue(queue.dismiss(first.id))
        assertFalse("stale timer for an already dismissed toast must not dismiss the next one", queue.dismiss(first.id))
        assertEquals("second", queue.current.value?.message)
    }

    @Test
    fun duplicateOfVisibleToastRestartsItsTimerInsteadOfQueueing() {
        val queue = ToastQueue()
        val visible = queue.enqueue("saved", ToastType.SUCCESS)!!

        val restarted = queue.enqueue("saved", ToastType.SUCCESS)

        assertEquals(visible, restarted)
        assertEquals(visible, queue.current.value)
        // Nothing is queued behind it, so dismissing leaves the queue empty.
        assertTrue(queue.dismiss(visible.id))
        assertNull(queue.current.value)
    }

    @Test
    fun duplicateOfQueuedToastIsSuppressed() {
        val queue = ToastQueue()
        queue.enqueue("visible", ToastType.INFO)
        assertNull(queue.enqueue("queued", ToastType.WARNING))
        assertNull(queue.enqueue("queued", ToastType.WARNING))

        queue.dismiss(queue.current.value!!.id)
        assertEquals("queued", queue.current.value?.message)

        queue.dismiss(queue.current.value!!.id)
        assertNull("the duplicate must not appear a second time", queue.current.value)
    }

    @Test
    fun sameTextWithDifferentTypeIsNotADuplicate() {
        val queue = ToastQueue()
        queue.enqueue("Failed", ToastType.INFO)
        queue.enqueue("Failed", ToastType.ERROR)

        queue.dismiss(queue.current.value!!.id)
        assertEquals(ToastType.ERROR, queue.current.value?.type)
    }

    @Test
    fun sameMessageCanBeShownAgainAfterItWasDismissed() {
        val queue = ToastQueue()
        val first = queue.enqueue("again", ToastType.INFO)!!
        queue.dismiss(first.id)

        val second = queue.enqueue("again", ToastType.INFO)

        assertTrue(second!!.id != first.id)
        assertEquals("again", queue.current.value?.message)
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
    fun invokingActionRunsCallbackAndDismissesToast() {
        val queue = ToastQueue()
        var invocations = 0
        val undo = queue.enqueue("Removed", ToastType.INFO, actionLabel = "Undo") { invocations++ }!!
        queue.enqueue("Next", ToastType.INFO)

        assertTrue(queue.invokeAction(undo.id))

        assertEquals(1, invocations)
        assertEquals("Next", queue.current.value?.message)
    }

    @Test
    fun invokingActionOnStaleToastDoesNothing() {
        val queue = ToastQueue()
        var invocations = 0
        val stale = queue.enqueue("Removed", ToastType.INFO, actionLabel = "Undo") { invocations++ }!!
        queue.dismiss(stale.id)

        assertFalse(queue.invokeAction(stale.id))
        assertEquals(0, invocations)
    }

    @Test
    fun tappingBodyDismissesWithoutRunningAction() {
        val queue = ToastQueue()
        var invocations = 0
        val toast = queue.enqueue("Removed", ToastType.INFO, actionLabel = "Undo") { invocations++ }!!

        assertTrue(queue.dismiss(toast.id))

        assertEquals(0, invocations)
        assertNull(queue.current.value)
    }
}
