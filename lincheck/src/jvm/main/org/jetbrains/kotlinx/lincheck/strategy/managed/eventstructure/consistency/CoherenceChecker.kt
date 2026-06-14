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
    var extendedCoherenceOrder: ComputableNode<ExtendedCoherenceOrder>? = null,
    var executionOrder: ComputableNode<ExecutionOrder>? = null,
) : Relation<AtomicThreadEvent>, Computable {

    private var consistent: Boolean = true

    var enumerator = execution.buildEnumerator()
    val causalGraph = CausalGraph(execution, enumerator)
//    val executionOrder = ExecutionOrderFast()


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
        causalGraph.reset(execution, enumerator)
        causalGraph.buildCausalGraph(causalityOrder, true)

        CoherenceOrderOption.generate( memoryAccessEventIndex, rmwChainsStorage, writesOrder)
            .forEach { coherence ->
                val extendedCoherence = ExtendedCoherenceOrder(execution, memoryAccessEventIndex,
                    writesOrder = causalityOrder union coherence
                ).apply { initialize(); compute() }

                val executionOrder = ExecutionOrderFast(
                    execution, memoryAccessEventIndex,
                    causalGraph, extendedCoherence, extendedCoherence union causalityOrder
                ).apply { initialize(); compute() }

                if (!executionOrder.isConsistent())
                    return@forEach

                this.map += coherence.map
                this.extendedCoherenceOrder?.setComputed(extendedCoherence)
                this.executionOrder?.setComputed(executionOrder)


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
    val execution: Execution<AtomicThreadEvent>,
    val memoryAccessEventIndex: AtomicMemoryAccessEventIndex,
    val writesOrder: Relation<AtomicThreadEvent>,
): Relation<AtomicThreadEvent>, Computable {

//    private val relations: MutableMap<MemoryLocation, RelationMatrix<AtomicThreadEvent>> = mutableMapOf()
    val relations: MutableMap<MemoryLocation, RelationAdjacencyList<AtomicThreadEvent>> = mutableMapOf()

    override fun invoke(x: AtomicThreadEvent, y: AtomicThreadEvent): Boolean {
        val location = getLocationForSameLocationAccesses(x, y)
            ?: return false
//        if (!(isWriteOrReadResponse(x) && isWriteOrReadResponse(y)))
//            return false
        return relations[location]?.get(x, y) ?: false
    }

    fun adjacent(x: AtomicThreadEvent) : Sequence<AtomicThreadEvent> {
        val xloc = (x.label as? MemoryAccessLabel)?.location ?: return emptySequence()
        return relations[xloc]?.adjacent(x) ?: emptySequence()
    }

    inline fun adjacentForEach(x: AtomicThreadEvent, block: (AtomicThreadEvent) -> Unit) {
        val xloc = (x.label as? MemoryAccessLabel)?.location ?: return
        val neighbours = relations[xloc]?.adjacent(x)
        neighbours?.forEach { block(it) }
    }


    private fun isWriteOrReadResponse(x: AtomicThreadEvent): Boolean {
        return (x.label.isWriteAccess() || x.label is ReadAccessLabel && x.label.isResponse)
    }

    fun isIrreflexive(): Boolean =
        relations.all { (_, relation) -> relation.isIrreflexive() }

    override fun initialize() {
        for (location in memoryAccessEventIndex.locations) {
            val events = mutableListOf<AtomicThreadEvent>().apply {
                addAll(memoryAccessEventIndex.getWrites(location))
                addAll(memoryAccessEventIndex.getReadResponses(location))
            }
//            relations[location] = RelationMatrix(events, buildEnumerator(events))
            relations[location] = RelationAdjacencyList(events)
        }
    }

    override fun compute() {
        addCoherenceEdges()
        addReadsFromEdges()
        addReadsBeforeEdges()
        addCoherenceReadFromEdges()
        addReadsBeforeReadsFromEdges()
    }

    override fun reset() {
        relations.clear()
    }

    private fun addCoherenceEdges() {
        for (location in memoryAccessEventIndex.locations) {
            addCoherenceEdges(location)
        }
    }

    private fun addCoherenceEdges(location: MemoryLocation) {
        val relation = relations[location]!!
        for (write1 in memoryAccessEventIndex.getWrites(location)) {
            for (write2 in memoryAccessEventIndex.getWrites(location)) {
                if (write1 != write2 && writesOrder(write1, write2))
                    relation[write1, write2] = true
            }
        }
    }

    private fun addReadsFromEdges() {
        for (location in memoryAccessEventIndex.locations) {
            addReadsFromEdges(location)
        }
    }

    private fun addReadsFromEdges(location: MemoryLocation) {
        val relation = relations[location]!!
        for (read in memoryAccessEventIndex.getReadResponses(location)) {
            relation[read.readsFrom, read] = true
        }
    }

    private fun addReadsBeforeEdges() {
        for (location in memoryAccessEventIndex.locations) {
            addReadsBeforeEdges(location)
        }
    }

    private fun addReadsBeforeEdges(location: MemoryLocation) {
        val relation = relations[location]!!
        for (read in memoryAccessEventIndex.getReadResponses(location)) {
            for (write in memoryAccessEventIndex.getWrites(location)) {
                if (relation(read.readsFrom, write)) {
                    relation[read, write] = true
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
        val relation = relations[location]!!
        for (read in memoryAccessEventIndex.getReadResponses(location)) {
            for (write in memoryAccessEventIndex.getWrites(location)) {
                if (relation(write, read.readsFrom)) {
                    relation[write, read] = true
                }
            }
        }
    }

    private fun addReadsBeforeReadsFromEdges() {
        for (location in memoryAccessEventIndex.locations) {
            addReadsBeforeReadsFromEdges(location)
        }
    }

    private fun addReadsBeforeReadsFromEdges(location: MemoryLocation) {
        val relation = relations[location]!!
        for (read1 in memoryAccessEventIndex.getReadResponses(location)) {
            for (read2 in memoryAccessEventIndex.getReadResponses(location)) {
                if (relation(read1, read2.readsFrom)) {
                    relation[read1, read2] = true
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

    fun reset(newExecution: Execution<AtomicThreadEvent>, newEnumerator: Enumerator<AtomicThreadEvent>) {
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

    fun adjacent(event: AtomicThreadEvent): Sequence<AtomicThreadEvent> {
        val arr = map[enumerator[event]]
        return arr.map { if (it == EMPTY_VALUE ) null else enumerator[it] }.filterNotNull().asSequence()
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