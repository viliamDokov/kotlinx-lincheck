/*
 * Lincheck
 *
 * Copyright (C) 2019 - 2024 JetBrains s.r.o.
 *
 * This Source Code Form is subject to the terms of the
 * Mozilla Public License, v. 2.0. If a copy of the MPL was not distributed
 * with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.jetbrains.kotlinx.lincheck.util

import org.jetbrains.lincheck.descriptors.Types
import org.jetbrains.lincheck.trace.TraceContext
import org.jetbrains.lincheck.trace.createAndRegisterMethodDescriptor
import org.jetbrains.lincheck.util.collections.resize
import kotlin.collections.Map.Entry

typealias ThreadId = Int
typealias ThreadMap<T> = Map<ThreadId, T>
typealias MutableThreadMap<T> = MutableMap<ThreadId, T>

fun <T> threadMapOf(): ThreadMap<T> =
    mutableThreadMapOf()

fun <T> threadMapOf(vararg pairs: Pair<ThreadId, T>): ThreadMap<T> =
    mutableThreadMapOf(*pairs)

fun <T> mutableThreadMapOf(): MutableThreadMap<T> =
    ArrayThreadMap()

fun <T> mutableThreadMapOf(vararg pairs: Pair<ThreadId, T>): MutableThreadMap<T> =
    ArrayThreadMap<T>().apply {  pairs.forEach { put(it.first, it.second) }}

internal fun TraceContext.getThreadRunMethodId(): Int =
    this.createAndRegisterMethodDescriptor(
        className = "java.lang.Thread",
        methodName = "run",
        methodType = Types.MethodType(Types.VOID_TYPE)
    ).id


class ArrayThreadMap<T> : MutableThreadMap<T> {

    class ArrayMapMutableEntry<T>(val map: ArrayThreadMap<T>, override var key: Int, override var value: T) : MutableMap.MutableEntry<Int, T> {
        override fun setValue(newValue: T): T {
            map[key+1] = newValue
            return value
        }
    }

    var array: ArrayList<T?> = arrayListOf()
    var used: BooleanArray = BooleanArray(0)

    override val keys: MutableSet<ThreadId>
        get() = KeySet()

    @Suppress("UNCHECKED_CAST")
    override val values: MutableCollection<T>
        get() = ValueSet()

    @Suppress("UNCHECKED_CAST")
    override val entries: MutableSet<MutableMap.MutableEntry<ThreadId, T>>
        get() = EntrySet()


    private inner class KeyIterator : MutableIterator<ThreadId> {
        private var index = 0
        private var done = 0

        override fun next(): ThreadId {
            while (index < array.size && !used[index]) {
                index++
            }
            index++
            done++
            return index - 2
        }

        override fun hasNext(): Boolean {
            return done < this@ArrayThreadMap.size
        }
        override fun remove() {
            TODO("Not yet implemented")
        }
    }

    private inner class ValueIterator : MutableIterator<T> {
        private val keyIterator = KeyIterator()

        override fun remove() {
            TODO("Not yet implemented")
        }

        @Suppress("UNCHECKED_CAST")
        override fun next(): T {
            val key = keyIterator.next()
            val value = array[key+1] as T
            return value
        }

        override fun hasNext(): Boolean {
            return keyIterator.hasNext()
        }
    }

    private inner class EntryIterator : MutableIterator<MutableMap.MutableEntry<ThreadId, T>> {
        private val keyIterator = KeyIterator()
        private var entry: ArrayMapMutableEntry<T>? = null

        override fun remove() {
            TODO("Not yet implemented")
        }

        @Suppress("UNCHECKED_CAST")
        override fun next(): MutableMap.MutableEntry<ThreadId, T> {
            val tid = keyIterator.next()
            val value = array[tid+1] as T

            if(entry == null) {
                entry = ArrayMapMutableEntry(this@ArrayThreadMap, tid, value)
            } else {
                entry!!.key = tid
                entry!!.value = value
            }

            return entry!!
        }

        override fun hasNext(): Boolean {
            return keyIterator.hasNext()
        }
    }

    private inner class KeySet : AbstractMutableSet<ThreadId>() {
        override fun add(element: ThreadId): Boolean {
            TODO("Not yet implemented")
        }

        override fun iterator(): MutableIterator<ThreadId> {
            return KeyIterator()
        }

        override val size: Int
            get() = this@ArrayThreadMap.size
    }

    private inner class EntrySet: AbstractMutableSet<MutableMap.MutableEntry<ThreadId, T>>() {
        override fun add(element: MutableMap.MutableEntry<ThreadId, T>): Boolean {
            TODO("Not yet implemented")
        }

        override fun iterator(): MutableIterator<MutableMap.MutableEntry<ThreadId, T>> {
            return EntryIterator()
        }

        override val size: Int
            get() = this@ArrayThreadMap.size
    }

    private inner class ValueSet: AbstractMutableCollection<T>() {

        override val size: Int
            get() = this@ArrayThreadMap.size

        override fun iterator(): MutableIterator<T> {
            return ValueIterator()
        }

        override fun add(element: T): Boolean {
            TODO("Not yet implemented")
        }
    }


    private fun resize(newSize: Int) {
        val newUsed = BooleanArray(newSize)
        used.copyInto(newUsed)
        used = newUsed
        array.resize(newSize,null)
    }

    override fun put(key: ThreadId, value: T): T? {
        val shiftedKey = key + 1
        check(shiftedKey >= 0)

        if(array.size <= shiftedKey) {
            resize(shiftedKey + 3)
        }

        if (used[shiftedKey]) {
            val old = array[shiftedKey]
            array[shiftedKey] = value
            return old
        }

        _size++;
        array[shiftedKey] = value
        used[shiftedKey] = true
        return null
    }

    override fun remove(key: ThreadId): T? {
        val shiftedKey = key + 1
        if (shiftedKey >= array.size || shiftedKey < 0) { return null }
        if (!used[shiftedKey]) { return null }
        _size--;
        used[shiftedKey] = false
        return array[shiftedKey]
    }

    override fun putAll(from: Map<out ThreadId, T>) {
        from.forEach { (key, value) -> put(key, value) }
    }

    override fun clear() {
        used.fill(false)
        _size = 0
    }

    var _size : Int = 0
    override val size: Int
        get() = _size

    override fun isEmpty(): Boolean {
        return size == 0
    }

    override fun containsKey(key: ThreadId): Boolean {
        val shiftedKey = key + 1
        if (shiftedKey >= array.size || shiftedKey < 0) { return false }
        return used[shiftedKey]
    }

    override fun containsValue(value: T): Boolean {
        return values.contains(value)
    }

    override fun get(key: ThreadId): T? {
        val shiftedKey = key + 1
        if (shiftedKey >= array.size || shiftedKey < 0) { return null }
        if (!used[shiftedKey]) { return null }
        return array[shiftedKey]
    }
}