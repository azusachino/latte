package com.azusachino.latte.data.cache

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class BoundedFifoCacheTest {

    @Test(expected = IllegalArgumentException::class)
    fun zeroMaxSizeThrows() {
        BoundedFifoCache<String, String>(0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativeMaxSizeThrows() {
        BoundedFifoCache<String, String>(-5)
    }

    @Test
    fun fifoEvictionWhenCapacityExceeded() {
        val cache = BoundedFifoCache<String, Int>(maxSize = 3)
        cache["a"] = 1
        cache["b"] = 2
        cache["c"] = 3
        assertEquals(3, cache.size)

        // Adding "d" should evict "a" (oldest inserted)
        cache["d"] = 4
        assertEquals(3, cache.size)
        assertNull(cache["a"])
        assertEquals(2, cache["b"])
        assertEquals(3, cache["c"])
        assertEquals(4, cache["d"])

        // Adding "e" should evict "b"
        cache["e"] = 5
        assertEquals(3, cache.size)
        assertNull(cache["b"])
        assertEquals(3, cache["c"])
        assertEquals(4, cache["d"])
        assertEquals(5, cache["e"])
    }

    @Test
    fun updatingExistingKeyPreservesSize() {
        val cache = BoundedFifoCache<String, Int>(maxSize = 2)
        cache["a"] = 1
        cache["b"] = 2
        assertEquals(2, cache.size)

        // Update "a"
        cache["a"] = 10
        assertEquals(2, cache.size)
        assertEquals(10, cache["a"])
        assertEquals(2, cache["b"])

        // Eviction order is FIFO (by insertion), so inserting "c" evicts "a"
        cache["c"] = 3
        assertNull(cache["a"])
        assertEquals(2, cache["b"])
        assertEquals(3, cache["c"])
    }

    @Test
    fun removeAndClearWorkProperly() {
        val cache = BoundedFifoCache<String, String>(maxSize = 5)
        cache["1"] = "one"
        cache["2"] = "two"
        assertEquals(2, cache.size)
        assertTrue(cache.containsKey("1"))
        assertTrue("2" in cache)

        val removed = cache.remove("1")
        assertEquals("one", removed)
        assertEquals(1, cache.size)
        assertFalse(cache.containsKey("1"))

        cache.clear()
        assertEquals(0, cache.size)
        assertFalse(cache.containsKey("2"))
    }

    @Test
    fun threadSafetyUnderConcurrentWrites() {
        val cache = BoundedFifoCache<Int, Int>(maxSize = 50)
        val threads = 8
        val itemsPerThread = 200
        val latch = CountDownLatch(threads)
        val executor = Executors.newFixedThreadPool(threads)

        for (t in 0 until threads) {
            val startKey = t * 1000
            executor.execute {
                try {
                    for (i in 0 until itemsPerThread) {
                        cache[startKey + i] = i
                    }
                } finally {
                    latch.countDown()
                }
            }
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS))
        executor.shutdown()
        assertTrue(cache.size <= 50)
    }
}
