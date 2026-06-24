/*
 * Lincheck
 *
 * Copyright (C) 2019 - 2025 JetBrains s.r.o.
 *
 * This Source Code Form is subject to the terms of the
 * Mozilla Public License, v. 2.0. If a copy of the MPL was not distributed
 * with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.jetbrains.lincheck_test.datastructures

import java.util.concurrent.atomic.AtomicReference


class VuykovNode<T>() {
    var value: T? = null
    var next: AtomicReference<VuykovNode<T>> = AtomicReference(null)
}

class VuykovQueue<T> {
    var head: AtomicReference<VuykovNode<T>>;
    var tail: VuykovNode<T>

    init {
        val node = VuykovNode<T>()
        head = AtomicReference(node)
        tail = node
    }

    fun offer(value: T) : Boolean  {
        val node =  VuykovNode<T>()
        node.value = value

        val prev = head.getAndSet(node)
        prev.next.set(node)
        return true
    }

    fun poll() : T? {
        val tailNode = tail
        val next = tailNode.next.get()

        if(next != null) {
            val value = next.value
            next.value = null
            tail = next

            return value
        }
        return null
    }
}


class VuykovQueueCorrect<T>() {

    var head: AtomicReference<VuykovNode<T>>;
    var tail: VuykovNode<T>

    init {
        val node = VuykovNode<T>()
        head = AtomicReference(node)
        tail = node
    }

    fun offer(value: T) : Boolean  {
        val node =  VuykovNode<T>()
        node.value = value

        val prev = head.getAndSet(node)
        prev.next.set(node)
        return true
    }

    fun poll() : T? {
        val tailNode = tail
        var next = tailNode.next.get()

        if (next != null) {
            return consumeNode(next)
        } else if (tailNode != head.get()) {
            do {
                next = tailNode.next.get()
            } while (next == null)
            return consumeNode(next)
        }

        return null
    }

    private fun consumeNode(next: VuykovNode<T>) : T? {
        val value = next.value
        next.value = null
        tail = next
        return value
    }

}
