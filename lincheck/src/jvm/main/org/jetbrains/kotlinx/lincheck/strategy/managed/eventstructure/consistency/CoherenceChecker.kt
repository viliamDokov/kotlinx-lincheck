/*
 * Lincheck
 *
 * Copyright (C) 2019 - 2024 JetBrains s.r.o.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 *
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-3.0.html>
 */

package org.jetbrains.kotlinx.lincheck.strategy.managed.eventstructure.consistency

import org.jetbrains.kotlinx.lincheck.strategy.managed.*
import org.jetbrains.kotlinx.lincheck.strategy.managed.eventstructure.*
import org.jetbrains.lincheck.util.*
import org.jetbrains.lincheck.util.collections.*
import kotlin.math.roundToInt

typealias CoherenceList = List<AtomicThreadEvent>

class CoherenceChecker : ConsistencyChecker<AtomicThreadEvent, MutableExtendedExecution> {

    override fun check(execution: MutableExtendedExecution): Inconsistency? {
        FooTimer.measure(7) {
            execution.coherenceOrderComputable.apply {
                initialize()
                compute()
            }
            val coherenceOrder = execution.coherenceOrderComputable.value
            return if (!coherenceOrder.isConsistent())
                CoherenceViolation()
            else null
        }
    }

}

data class CoherenceEntry(
    val coherence: CoherenceList,
    val positions: List<Int>,
    val enumerator: Enumerator<AtomicThreadEvent>,
)

class CoherenceOrder(
    val execution: Execution<AtomicThreadEvent>,
    val memoryAccessEventIndex: AtomicMemoryAccessEventIndex,
    val rmwChainsStorage: ReadModifyWriteOrder,
    val writesOrder: Relation<AtomicThreadEvent>,
    var executionOrderNode: ComputableNode<ExecutionOrder>? = null,
) : Relation<AtomicThreadEvent>, Computable {

    private var consistent: Boolean = true

    var enumerator = execution.buildEnumerator()
    val causalGraph = CausalGraph(execution, enumerator)

    val extendedCoherenceOrder = ExtendedCoherenceOrder(execution, enumerator, memoryAccessEventIndex, causalityOrder union writesOrder)
    val executionOrder = ExecutionOrderFast(
        execution, enumerator, causalGraph, extendedCoherenceOrder
    )


    private val map = mutableMapOf<MemoryLocation, CoherenceEntry>()

    override fun invoke(x: AtomicThreadEvent, y: AtomicThreadEvent): Boolean {
        val location = getLocationForSameLocationWriteAccesses(x, y)
            ?: return false
        val (_, positions, enumerator) = map[location]
            ?: return writesOrder(x, y)
        return positions[enumerator[x]] < positions[enumerator[y]]
    }

    operator fun get(location: MemoryLocation): CoherenceList =
        map[location]?.coherence ?: emptyList()

    fun isConsistent(): Boolean =
        consistent

    override fun invalidate() {
        reset()
    }

    override fun reset() {
        map.clear()
        consistent = true
    }

    override fun compute() {
        check(map.isEmpty())

        enumerator = execution.buildEnumerator()
        causalGraph.initialize(execution, enumerator)
        causalGraph.buildCausalGraph(causalityOrder, true)

        CoherenceOrderOption.generate( memoryAccessEventIndex, rmwChainsStorage, writesOrder)
            .forEach { coherence ->

                extendedCoherenceOrder.execution = execution
                extendedCoherenceOrder.enumerator = enumerator
                extendedCoherenceOrder.writesOrder = causalityOrder union coherence

                extendedCoherenceOrder.apply {
                    reset()
                    initialize()
                    compute()
                }

                executionOrder.execution = execution
                executionOrder.enumerator = enumerator
                executionOrder.causalGraph = causalGraph
                executionOrder.eco = extendedCoherenceOrder
                val ordering = executionOrder.compute()

                if (ordering == null)
                    return@forEach

                this.map += coherence.map
                this.executionOrderNode?.setComputed(ordering)

                return
            }
        // if we reached this point, then none of the generated coherence orderings is consistent
        consistent = false
    }
}


class CoherenceOrderOption(val writesOrder: Relation<AtomicThreadEvent>) : Relation<AtomicThreadEvent> {

    internal val map = mutableMapOf<MemoryLocation, CoherenceEntry>()

    override fun invoke(x: AtomicThreadEvent, y: AtomicThreadEvent): Boolean {
        val location = getLocationForSameLocationWriteAccesses(x, y)
            ?: return false
        val (_, positions, enumerator) = map[location]
            ?: return writesOrder(x, y)
        return positions[enumerator[x]] < positions[enumerator[y]]
    }


    operator fun get(location: MemoryLocation): CoherenceList =
        map[location]?.coherence ?: emptyList()
    companion object {
        internal fun generate(
            memoryAccessEventIndex: AtomicMemoryAccessEventIndex,
            rmwChainsStorage: ReadModifyWriteOrder,
            writesOrder: Relation<AtomicThreadEvent>
        ): Sequence<CoherenceOrderOption> {
            val coherenceOrderings = memoryAccessEventIndex.locations.mapNotNull { location ->
                if (memoryAccessEventIndex.isWriteWriteRaceFree(location))
                    return@mapNotNull null
                val writes = memoryAccessEventIndex.getWrites(location)
                    .takeIf { it.size > 1 } ?: return@mapNotNull null
                val enumerator = memoryAccessEventIndex.enumerator(AtomicMemoryAccessCategory.Write, location)!!
                topologicalSortings(writesOrder.toGraph(writes, enumerator)).filter {
                    rmwChainsStorage.respectful(it)
                }
            }
            if (coherenceOrderings.isEmpty()) {
                return sequenceOf(
                    CoherenceOrderOption(writesOrder)
                )
            }
            return coherenceOrderings.cartesianProduct().map { coherenceList ->
                val coherenceOrder = CoherenceOrderOption(writesOrder)
                for (coherence in coherenceList) {
                    val location = coherence.getLocationForSameLocationWriteAccesses()!!
                    val enumerator = memoryAccessEventIndex.enumerator(AtomicMemoryAccessCategory.Write, location)!!
                    val positions = MutableList(coherence.size) { 0 }
                    coherence.forEachIndexed { i, write ->
                        positions[enumerator[write]] = i
                    }
                    coherenceOrder.map[location] = CoherenceEntry(coherence, positions, enumerator)
                }
                return@map coherenceOrder
            }
        }

    }
}

class ExtendedCoherenceOrder(
    var execution: Execution<AtomicThreadEvent>,
    var enumerator: Enumerator<AtomicThreadEvent>,
    val memoryAccessEventIndex: AtomicMemoryAccessEventIndex,
    var writesOrder: Relation<AtomicThreadEvent>,
): Computable {

//    private val relations: MutableMap<MemoryLocation, RelationMatrix<AtomicThreadEvent>> = mutableMapOf()
    val map = CausalGraph(execution, enumerator)
//    val map = CausalGraph(execution, enumerator)

    inline fun adjacentForEach(x: AtomicThreadEvent, block: (AtomicThreadEvent) -> Unit) {
        map.adjacentForEach(x, block)
    }

    override fun initialize() {
        map.initialize(execution, enumerator)
        map.reset()
    }

    override fun compute() {
        addCoherenceEdges()
        addReadsFromEdges()
        addReadsBeforeEdges()
        addCoherenceReadFromEdges()
    }

    override fun reset() {
//        TODO()
    }

    private fun addCoherenceEdges() {
        for (location in memoryAccessEventIndex.locations) {
            addCoherenceEdges(location)
        }
    }

    private fun addCoherenceEdges(location: MemoryLocation) {
        for (write1 in memoryAccessEventIndex.getWrites(location)) {
            for (write2 in memoryAccessEventIndex.getWrites(location)) {
                if (write1 != write2 && writesOrder(write1, write2))
                    map.set(write1, write2)
            }
        }
    }

    private fun addReadsFromEdges() {
        for (location in memoryAccessEventIndex.locations) {
            addReadsFromEdges(location)
        }
    }

    private fun addReadsFromEdges(location: MemoryLocation) {
        for (read in memoryAccessEventIndex.getReadResponses(location)) {
            map.set(read.readsFrom, read)
        }
    }

    private fun addReadsBeforeEdges() {
        for (location in memoryAccessEventIndex.locations) {
            addReadsBeforeEdges(location)
        }
    }

    private fun addReadsBeforeEdges(location: MemoryLocation) {
        for (read in memoryAccessEventIndex.getReadResponses(location)) {
            for (write in memoryAccessEventIndex.getWrites(location)) {
                if (read.readsFrom != write && writesOrder(read.readsFrom, write)) {
                    map.set(read, write)
                }
            }
        }
    }

    private fun addCoherenceReadFromEdges() {
        for (location in memoryAccessEventIndex.locations) {
            addCoherenceReadFromEdges(location)
        }
    }

    private fun addCoherenceReadFromEdges(location: MemoryLocation) {
        for (read in memoryAccessEventIndex.getReadResponses(location)) {
            for (write in memoryAccessEventIndex.getWrites(location)) {
                if (write != read.readsFrom && writesOrder(write, read.readsFrom)) {
                    map.set(write, read)
                }
            }
        }
    }
}


class CausalGraph(var execution: Execution<AtomicThreadEvent>, var enumerator: Enumerator<AtomicThreadEvent>) {

    companion object {
        val EMPTY_VALUE: Int = -1
    }

    val nEvents : Int
        get() =  execution.size
    val nThreads : Int
        get() =  execution.maxThreadId + 1

    var capacityEvents: Int = 0
    var capacityThreads: Int = 0
        get() = nEvents
    var map : Array<IntArray> = allocateNewMap()


    fun allocateNewMap() : Array<IntArray> {
        capacityEvents = (nEvents * 1.5).roundToInt()
        capacityThreads = nThreads
        return Array(capacityEvents) { IntArray(capacityThreads) { EMPTY_VALUE } }
    }

    fun reset() {
        for (i in 0 until nEvents) {
            for (j in 0 until nThreads) {
                map[i][j] = EMPTY_VALUE
            }
        }
    }

    fun initialize(newExecution: Execution<AtomicThreadEvent>, newEnumerator: Enumerator<AtomicThreadEvent>) {
        val shouldResize = capacityEvents < newExecution.size || capacityThreads < (newExecution.maxThreadId + 1)
        this.execution = newExecution
        this.enumerator = newEnumerator
        if(shouldResize) {
            map = allocateNewMap()
        }
    }

    fun buildCausalGraph(relation: Relation<AtomicThreadEvent>, respectsProgramOrder: Boolean) {
        for (eventId in 0 until nEvents) {
            val event = enumerator[eventId]
            for (threadId in 0 until nThreads) { // NOTE: we can skip -1
                val threadEvents = execution.get(threadId) ?: continue
                val position = if (respectsProgramOrder) {
                    // TODO: this uses binary search from utils. Replace it with standard binary search function (I did not want to use my brain right now)
                    threadEvents.binarySearch { relation(event, it) }
                } else {
                    threadEvents.indexOfFirst { relation(event, it) }
                }

                val targetEvent = execution[threadId, position]
                var idx = -1
                if(targetEvent != null) idx = enumerator[targetEvent]
                map[eventId][threadId] = idx
            }
        }
    }

    fun set(event1: AtomicThreadEvent, event2: AtomicThreadEvent) {
        val idx1 = enumerator[event1]
        val idx2 = enumerator[event2]

        val arr = map[idx1]
        val idxExisting = arr[event2.threadId]
        if (idxExisting == EMPTY_VALUE) {
            arr[event2.threadId] = idx2
            return
        }

        val eventExisting = enumerator[idxExisting]
        check(eventExisting.threadId == event2.threadId)

        if(eventExisting.threadId > event2.threadId) {
            arr[event2.threadId] = idx2
        }
    }

    fun get(event1: AtomicThreadEvent, event2: AtomicThreadEvent) : Boolean {
        val idx1 = enumerator[event1]
        val idx2 = enumerator[event2]

        val arr = map[idx1]
        val idxExisting = arr[event2.threadId]
        return idxExisting == idx2
    }

    inline fun adjacentForEach(event: AtomicThreadEvent, block: (AtomicThreadEvent) -> Unit) {
        val arr = map[enumerator[event]]
        arr.forEach {
            if (it != EMPTY_VALUE) {
                block(enumerator[it])
            }
        }
    }

}

class FooGraph(var execution: Execution<AtomicThreadEvent>, var enumerator: Enumerator<AtomicThreadEvent>) {
    companion object {
        val EMPTY_VALUE: Int = -1
    }

    val nEvents : Int
        get() =  execution.size
    val nThreads : Int
        get() =  execution.maxThreadId + 1

    var capacityEvents: Int = 0
    var capacityThreads: Int = 0
        get() = nEvents
    var map : Array<IntArray> = allocateNewMap()


    fun allocateNewMap() : Array<IntArray> {
        capacityEvents = (nEvents * 1.5).roundToInt()
        capacityThreads = nThreads
        return Array(capacityEvents) { IntArray(capacityThreads + 1) { 0 } }
    }

    fun reset() {
        for (i in 0 until nEvents) {
            map[i][0] = 0
        }
    }

    fun initialize(newExecution: Execution<AtomicThreadEvent>, newEnumerator: Enumerator<AtomicThreadEvent>) {
        val shouldResize = capacityEvents < newExecution.size || capacityThreads < (newExecution.maxThreadId + 1)
        execution = newExecution
        enumerator = newEnumerator
        if(shouldResize) {
            map = allocateNewMap()
        }
        reset()
    }

    fun set(event1: AtomicThreadEvent, event2: AtomicThreadEvent) {
        val idx1 = enumerator[event1]
        val idx2 = enumerator[event2]
        check(idx2 != -1)

        val arr = map[idx1]
        val size = arr[0]

        for (i in 1 until size+1) {
            val element = enumerator[arr[i]]
            if (element.threadId != event2.threadId) continue
            if (element.threadPosition > event2.threadPosition) {
                arr[i] = idx2
            }
            return
        }

        // Element for this thread has not yet been found so we return
        arr[size+1] = idx2
        arr[0] = size + 1
    }

    fun buildCausalGraph(relation: Relation<AtomicThreadEvent>, respectsProgramOrder: Boolean) {
        for (eventId in 0 until nEvents) {
            val event = enumerator[eventId]
            for (threadId in 0 until nThreads) { // NOTE: we can skip -1
                val threadEvents = execution.get(threadId) ?: continue
                val position = if (respectsProgramOrder) {
                    // TODO: this uses binary search from utils. Replace it with standard binary search function (I did not want to use my brain right now)
                    threadEvents.binarySearch { relation(event, it) }
                } else {
                    threadEvents.indexOfFirst { relation(event, it) }
                }

                val targetEvent = execution[threadId, position]
                if(targetEvent != null) {
                    set(event, targetEvent)
                }
            }
        }
    }

    fun get(event1: AtomicThreadEvent, event2: AtomicThreadEvent) : Boolean {
        val idx1 = enumerator[event1]
        val idx2 = enumerator[event2]

        val arr = map[idx1]
        val size = arr[0]
        for (i in 0 until size) {
            if (arr[i + 1] == idx2) {
                return true
            }
        }
        return false
    }

    inline fun adjacentForEach(event: AtomicThreadEvent, block: (AtomicThreadEvent) -> Unit) {
        val arr = map[enumerator[event]]
        for (i in 0 until arr[0]) {
            val it = arr[i+1]
            block(enumerator[it])
        }
    }
}