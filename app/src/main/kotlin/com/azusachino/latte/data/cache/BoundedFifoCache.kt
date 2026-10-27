package com.azusachino.latte.data.cache

/**
 * Thread-safe bounded FIFO (First-In, First-Out) cache.
 * When [size] exceeds [maxSize], the oldest inserted entry is evicted.
 */
class BoundedFifoCache<K : Any, V : Any>(val maxSize: Int) {
    init {
        require(maxSize > 0) { "maxSize must be greater than 0, but was $maxSize" }
    }

    private val lock = Any()
    private val entries = object : LinkedHashMap<K, V>(maxSize, 0.75f, false) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean {
            return size > maxSize
        }
    }

    operator fun get(key: K): V? = synchronized(lock) {
        entries[key]
    }

    operator fun set(key: K, value: V) {
        synchronized(lock) {
            entries[key] = value
        }
    }

    fun getOrPut(key: K, defaultValue: () -> V): V = synchronized(lock) {
        val existing = entries[key]
        if (existing != null) {
            return existing
        }
        val newEntry = defaultValue()
        entries[key] = newEntry
        return newEntry
    }

    fun remove(key: K): V? = synchronized(lock) {
        entries.remove(key)
    }

    fun clear() = synchronized(lock) {
        entries.clear()
    }

    val size: Int
        get() = synchronized(lock) { entries.size }

    fun containsKey(key: K): Boolean = synchronized(lock) {
        entries.containsKey(key)
    }

    operator fun contains(key: K): Boolean = containsKey(key)

    fun snapshot(): Map<K, V> = synchronized(lock) {
        LinkedHashMap(entries)
    }
}
