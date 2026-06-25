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


/**
 * A simple multi-producer single consumer queue.
 * Note that theis implementation is not linearizable as it crashes for this scenario:
 *
 * --------------------------------------------
 * |  Thread 1    | Thread 2     |  Thread 3  |
 * |------------------------------------------|
 * | poll()      |  offer(1)    |  offer(2)   |
 * --------------------------------------------
 */
class MPSCQueue<T> {

    class Node<T>() {
        var value: T? = null
        var next: AtomicReference<Node<T>> = AtomicReference(null)
    }

    var head: AtomicReference<Node<T>>;
    var tail: Node<T>

    init {
        val node = Node<T>()
        head = AtomicReference(node)
        tail = node
    }

    fun offer(value: T) : Boolean  {
        val node =  Node<T>()
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


class MPSCQueueCorrect<T>() {

    class Node<T>() {
        var value: T? = null
        var next: AtomicReference<Node<T>> = AtomicReference(null)
    }

    var head: AtomicReference<Node<T>>;
    var tail: Node<T>

    init {
        val node = Node<T>()
        head = AtomicReference(node)
        tail = node
    }

    fun offer(value: T) : Boolean  {
        val node =  Node<T>()
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

    private fun consumeNode(next: Node<T>) : T? {
        val value = next.value
        next.value = null
        tail = next
        return value
    }

}
