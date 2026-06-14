/*
 * Lincheck
 *
 * Copyright (C) 2019 - 2026 JetBrains s.r.o.
 *
 * This Source Code Form is subject to the terms of the
 * Mozilla Public License, v. 2.0. If a copy of the MPL was not distributed
 * with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.jetbrains.lincheck.util

import org.jetbrains.lincheck.util.collections.*

fun interface Relation<in T> {
    operator fun invoke(x: T, y: T): Boolean

    companion object {
        fun<T> empty() = Relation<T> { _, _ -> false }
    }
}

infix fun<T> Relation<T>.union(relation: Relation<T>) = Relation<T> { x, y ->
    this(x, y) || relation(x, y)
}

infix fun<T> Relation<T>.intersection(relation: Relation<T>) = Relation<T> { x, y ->
    this(x, y) && relation(x, y)
}

fun<T> Relation<T>.orEqual(x: T, y: T): Boolean {
    return (x == y) || this(x, y)
}

fun<T> Relation<T>.unordered(x: T, y: T): Boolean {
    return (x != y) && !this(x, y) && !this(y, x)
}

fun<T> Relation<T>.maxOrNull(x: T, y: T): T? = when {
    x == y -> x
    this(x, y) -> y
    this(y, x) -> x
    else -> null
}

fun<T> Relation<T>.max(x: T, y: T): T =
    maxOrNull(x, y) ?: throw IncomparableArgumentsException("$x and $y are incomparable")

class IncomparableArgumentsException(message: String): Exception(message)

// covering for each element returns a list of elements on which it depends;
// in terms of a graph: for each node it returns source nodes of its incoming edges
fun interface Covering<T> {
    operator fun invoke(x: T): List<T>
}

interface Enumerator<T> {

    operator fun get(x: T): Int

    // TODO: change to `List` and use it to optimize iteration in `RelationMatrix`?
    operator fun get(i: Int): T

}

fun<T : Comparable<T>> SortedList<T>.toEnumerator(): Enumerator<T> = object : Enumerator<T> {
    private val list = this@toEnumerator
    override fun get(i: Int): T = list[i]
    override fun get(x: T): Int = list.indexOf(x)
}

class RelationMatrix<T>(
    // TODO: take nodes from the enumerator (?)
    val nodes: Collection<T>,
    val enumerator: Enumerator<T>,
) : Relation<T> {

    private val size = nodes.size

    private val matrix = Array(size) { BooleanArray(size) }

    private var version = 0

    constructor(nodes: Collection<T>, enumerator: Enumerator<T>, relation: Relation<T>) : this (nodes, enumerator) {
        add(relation)
    }

    override operator fun invoke(x: T, y: T): Boolean =
        get(x, y)

    private operator fun get(i: Int, j: Int): Boolean {
        return matrix[i][j]
    }

    operator fun get(x: T, y: T): Boolean =
        get(enumerator[x], enumerator[y])

    private operator fun set(i: Int, j: Int, value: Boolean) {
        version += (matrix[i][j] != value).toInt()
        matrix[i][j] = value
    }

    operator fun set(x: T, y: T, value: Boolean) =
        set(enumerator[x], enumerator[y], value)

    fun add(relation: Relation<T>) {
        for (i in 0 until size) {
            val x = enumerator[i]
            for (j in 0 until size) {
                this[i, j] = this[i, j] || relation(x, enumerator[j])
            }
        }
    }

    fun order(ordering: List<T>, strict: Boolean = true) {
        for (i in ordering.indices) {
            for (j in i until ordering.size) {
                if (strict && i == j)
                    continue
                this[ordering[i], ordering[j]] = true
            }
        }
    }

    fun remove(relation: Relation<T>) {
        for (i in 0 until size) {
            for (j in 0 until size) {
                this[i, j] = this[i, j] && !relation(enumerator[i], enumerator[j])
            }
        }
    }

    fun filter(relation: Relation<T>) {
        for (i in 0 until size) {
            for (j in 0 until size) {
                this[i, j] = this[i, j] && relation(enumerator[i], enumerator[j])
            }
        }
    }

    private fun swap(i: Int, j: Int) {
        val value  = this[i, j]
        this[i, j] = this[j, i]
        this[j, i] = value
    }

    fun transpose() {
        for (i in 1 until size) {
            for (j in 0 until i) {
                swap(i, j)
            }
        }
    }

    fun transitiveClosure() {
        // TODO: optimize -- skip the computation for already transitive relation;
        //  track this by saving relation version number at the last call to `transitiveClosure`
        kLoop@for (k in 0 until size) {
            iLoop@for (i in 0 until size) {
                if (!this[i, k])
                    continue@iLoop
                jLoop@for (j in 0 until size) {
                    this[i, j] = this[i, j] || this[k, j]
                }
            }
        }
    }

    fun transitiveReduction() {
        jLoop@for (j in 0 until size) {
            iLoop@for (i in 0 until size) {
                if (!this[i, j])
                    continue@iLoop
                kLoop@for (k in 0 until size) {
                    if (this[i, k] && this[j, k]) {
                        this[i, k] = false
                    }
                }
            }
        }
    }

    fun equivalenceClosure(equivClassMapping : (T) -> List<T>?) {
        for (i in 0 until size) {
            val x = enumerator[i]
            val xClass = equivClassMapping(x)
            for (j in 0 until size) {
                val y = enumerator[j]
                val yClass = equivClassMapping(y)
                if (this[x, y] && xClass !== yClass) {
                    xClass?.forEach { this[it, y] = true }
                    yClass?.forEach { this[x, it] = true }
                }
            }
        }
    }

    fun fixpoint(block: RelationMatrix<T>.() -> Unit) {
        do {
            val changed = trackChanges { block() }
        } while (changed)
    }

    fun trackChanges(block: RelationMatrix<T>.() -> Unit): Boolean {
        val version = this.version
        block(this)
        return (version != this.version)
    }

    fun isIrreflexive(): Boolean {
        for (i in 0 until size) {
            if (this[i, i])
                return false
        }
        return true
    }

}

class RelationAdjacencyList<T>(
    // TODO: take nodes from the enumerator (?)
    val nodes: Collection<T>,
) : Relation<T> {

    private val size = nodes.size

    private val adjacencyMap: MutableMap<T, MutableSet<T>> = mutableMapOf()

    private var version = 0

    constructor(nodes: Collection<T>, relation: Relation<T>) : this (nodes) {
        add(relation)
    }

    override operator fun invoke(x: T, y: T): Boolean =
        get(x, y)


    operator fun get(x: T, y: T): Boolean  {
        val ns = adjacencyMap[x] ?: return false
        return y in ns
    }

    fun adjacent(x: T) : Sequence<T> {
        return adjacencyMap[x]?.asSequence() ?: emptySequence()
    }

    operator fun set(x: T, y: T, value: Boolean) {
        adjacencyMap.update(x, mutableSetOf() ) {
            if (value) {
                it.add(y)
            } else {
                it.remove(y)
            }
            it
        }
        check(this[x, y] == value)
    }

    fun add(relation: Relation<T>) {
        TODO()
        for (node1 in nodes) {
            for (node2 in nodes) {
                if (relation(node1, node2)) {
                    this[node1, node2] = true
                }
            }
        }
    }

    fun order(ordering: List<T>, strict: Boolean = true) {
        TODO()
        for (i in ordering.indices) {
            for (j in i until ordering.size) {
                if (strict && i == j)
                    continue
                this[ordering[i], ordering[j]] = true
            }
        }
    }

    fun remove(relation: Relation<T>) {
        TODO()
        for (node1 in nodes) {
            for (node2 in nodes) {
                this[node1, node2] = this[node1, node2] && !relation(node1, node2)
            }
        }
    }

    fun filter(relation: Relation<T>) {
        TODO()
        for (node1 in nodes) {
            for (node2 in nodes) {
                this[node1, node2] = this[node1, node2] && relation(node1, node2)
            }
        }
    }

    private fun swap(node1: T, node2: T) {
        val value  = this[node1, node2]
        this[node1, node2] = this[node1, node2]
        this[node1, node2] = value
    }

    fun transpose() {
        TODO()
        for (node1 in nodes) {
            for (node2 in nodes) {
                swap(node1, node2)
            }
        }
    }

    fun transitiveClosure() {
        // TODO: optimize -- skip the computation for already transitive relation;
        //  track this by saving relation version number at the last call to `transitiveClosure`
        TODO()
        kLoop@for (nodeK in nodes) {
            iLoop@for (nodeI in nodes) {
                if (!this[nodeI, nodeK])
                    continue@iLoop
                jLoop@for (nodeJ in nodes) {
                    this[nodeI, nodeJ] = this[nodeI, nodeJ] || this[nodeK, nodeJ]
                }
            }
        }
    }

    fun transitiveReduction() {
        jLoop@for (nodeJ in nodes) {
            iLoop@for (nodeI in nodes) {
                if (!this[nodeI, nodeJ])
                    continue@iLoop
                kLoop@for (nodeK in nodes) {
                    if (this[nodeI, nodeK] && this[nodeJ, nodeK]) {
                        this[nodeI, nodeK] = false
                    }
                }
            }
        }
    }

    fun equivalenceClosure(equivClassMapping : (T) -> List<T>?) {
        for (i in nodes) {
            val xClass = equivClassMapping(i)
            for (j in nodes) {
                val yClass = equivClassMapping(j)
                if (this[i, j] && xClass !== yClass) {
                    xClass?.forEach { this[it, j] = true }
                    yClass?.forEach { this[i, it] = true }
                }
            }
        }
    }

    fun fixpoint(block: RelationAdjacencyList<T>.() -> Unit) {
        do {
            val changed = trackChanges { block() }
        } while (changed)
    }

    fun trackChanges(block: RelationAdjacencyList<T>.() -> Unit): Boolean {
        val version = this.version
        block(this)
        return (version != this.version)
    }

    fun isIrreflexive(): Boolean {
        for (i in nodes) {
            if (this[i, i])
                return false
        }
        return true
    }
}
