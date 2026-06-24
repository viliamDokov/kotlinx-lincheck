/*
 * Lincheck
 *
 * Copyright (C) 2019 - 2026 JetBrains s.r.o.
 *
 * This Source Code Form is subject to the terms of the
 * Mozilla Public License, v. 2.0. If a copy of the MPL was not distributed
 * with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.jetbrains.kotlinx.lincheck.util

import org.jetbrains.kotlinx.lincheck.execution.HBClock
import org.jetbrains.kotlinx.lincheck.execution.emptyClock
import kotlin.math.*

interface VectorClock {
    fun isEmpty(): Boolean

    fun maxThreadId(): Int

    operator fun get(tid: ThreadId): Int
}

interface MutableVectorClock : VectorClock {
    operator fun set(tid: ThreadId, timestamp: Int)

    fun increment(tid: ThreadId, n: Int) {
        set(tid, get(tid) + n)
    }

    fun merge(other: VectorClock)

    fun clear()
}

fun VectorClock.observes(tid: ThreadId, timestamp: Int): Boolean =
    timestamp <= get(tid)

fun MutableVectorClock.increment(tid: ThreadId) {
    increment(tid, 1)
}

operator fun VectorClock.plus(other: VectorClock): MutableVectorClock =
    copy().apply { merge(other) }

fun VectorClock(): VectorClock =
    IntArrayClock()

fun MutableVectorClock(defaultVal: Int = -1): MutableVectorClock =
    IntArrayClock(defaultVal)

fun VectorClock.copy(): MutableVectorClock {
    // TODO: make VectorClock sealed interface?
    when (this) {
        is ThreadMapClock -> return copy()
        is IntArrayClock -> return copy()
    }
    check(this is ThreadMapClock)
    return copy()
}

private class IntArrayClock(val defaultVal: Int = -1, capacity: Int = 0) : MutableVectorClock {

    var clock = emptyIntArrayClock(defaultVal, capacity)
    var _maxThreadId = -1

    val capacity: Int
        get() = clock.size

    override fun isEmpty(): Boolean =
        clock.all { it == -1 }

    // NOTE: potentially incorrect
    override fun maxThreadId(): Int = _maxThreadId

    override fun get(tid: ThreadId): Int  {
        val shiftedTid = tid + 1
        return if (shiftedTid < capacity) clock[shiftedTid] else -1
    }

    override fun set(tid: ThreadId, timestamp: Int) {
        if (tid > _maxThreadId) _maxThreadId = tid
        val shiftedTid = tid + 1
        expandIfNeeded(shiftedTid)
        clock[shiftedTid] = timestamp
    }

    override fun increment(tid: ThreadId, n: Int) {
        if (tid > _maxThreadId) _maxThreadId = tid
        val shiftedTid = tid + 1
        expandIfNeeded(shiftedTid)
        clock[shiftedTid] += n
    }

    override fun merge(other: VectorClock) {
        // TODO: make VectorClock sealed interface?
        check(other is IntArrayClock)
        if (capacity < other.capacity) {
            expand(other.capacity)
        }
        for (i in 0 until capacity) {
            clock[i] = max(clock[i], other[i-1])
        }
        _maxThreadId = max(_maxThreadId, other.maxThreadId())
    }

    override fun clear() {
        clock.fill(-1)
    }

    private fun expand(newCapacity: Int) {
        require(newCapacity > capacity)
        val newClock = emptyIntArrayClock(defaultVal, newCapacity)
        copyInto(newClock)
        clock = newClock
    }

    private fun expandIfNeeded(shiftedTid: ThreadId) {
        if (shiftedTid >= capacity) {
            expand(shiftedTid + 1)
        }
    }

    fun copy() : IntArrayClock {
        val newClock = IntArrayClock(defaultVal, capacity)
        copyInto(newClock.clock);
        newClock._maxThreadId = _maxThreadId
        return newClock
    }

    private fun copyInto(other: IntArray) {
        require(other.size >= capacity)
        // TODO: use arraycopy?
        //  System.arraycopy(old, 0, clock, 0, capacity)
        for (i in 0 until capacity) {
            other[i] = clock[i]
        }
    }

    override fun equals(other: Any?): Boolean =
        (other is IntArrayClock) && (clock.contentEquals(other.clock))

    override fun hashCode(): Int =
        clock.contentHashCode()

    override fun toString() =
        clock.joinToString(prefix = "[", separator = ",", postfix = "]")

    companion object {
        private fun emptyIntArrayClock(defaultVal: Int, capacity: Int) =
            IntArray(capacity) { defaultVal }
    }
}


private class ThreadMapClock(private val defaultVal: Int = -1) : MutableVectorClock {
    var clock = mutableThreadMapOf<Int>()

    override fun isEmpty(): Boolean =
        clock.isEmpty()

    override fun get(tid: ThreadId): Int =
        clock.getOrDefault(tid, defaultVal)

    override fun maxThreadId(): Int {
        return clock.keys.maxOrNull() ?: -1
    }

    override fun set(tid: ThreadId, timestamp: Int) {
        clock.set(tid, timestamp)
    }

    override fun increment(tid: ThreadId, n: Int) {
        // TODO: not sure what is the exact behaviour when tid is not already there
        // Note that the default value here is 0.
        clock.set(tid, get(tid) + n)
    }

    override fun merge(other: VectorClock) {
        // TODO: make VectorClock sealed interface?
        check(other is ThreadMapClock)
        for (i in other.clock.keys) {
            clock[i] = max(get(i), other[i])
        }
    }

    override fun clear() {
        clock.clear();
    }

    fun copy(): ThreadMapClock  {
        val newClock = ThreadMapClock()
        newClock.clock = clock.toMutableMap()
        return newClock
    }

    private fun copyFrom(other: ThreadMapClock) {
        clock = other.clock.toMutableMap();
    }

    override fun equals(other: Any?): Boolean =
        (other is ThreadMapClock) && (clock.equals(other.clock))

    override fun hashCode(): Int =
        clock.hashCode()

    override fun toString() =
        clock.toString()
}

fun VectorClock.toHBClock(capacity: Int, tid: ThreadId, aid: Int): HBClock {
    check(this is ThreadMapClock)
    val result = emptyClock(capacity)
    for (i in 0 until capacity) {
        if (i == tid) {
            result.clock[i] = get(i)
            continue
        }
        result.clock[i] = 1 + get(i)
    }
    return result
}