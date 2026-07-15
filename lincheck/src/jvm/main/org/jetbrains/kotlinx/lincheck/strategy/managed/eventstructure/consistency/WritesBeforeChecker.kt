/*
 * Lincheck
 *
 * Copyright (C) 2019 - 2026 JetBrains s.r.o.
 *
 * This Source Code Form is subject to the terms of the
 * Mozilla Public License, v. 2.0. If a copy of the MPL was not distributed
 * with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.jetbrains.kotlinx.lincheck.strategy.managed.eventstructure.consistency

import org.jetbrains.kotlinx.lincheck.strategy.managed.MemoryLocation
import org.jetbrains.kotlinx.lincheck.strategy.managed.eventstructure.AtomicMemoryAccessEventIndex
import org.jetbrains.kotlinx.lincheck.strategy.managed.eventstructure.AtomicThreadEvent
import org.jetbrains.kotlinx.lincheck.strategy.managed.eventstructure.ExtendedExecutionTracker
import org.jetbrains.kotlinx.lincheck.strategy.managed.eventstructure.Graph
import org.jetbrains.kotlinx.lincheck.strategy.managed.eventstructure.InitializationLabel
import org.jetbrains.kotlinx.lincheck.strategy.managed.eventstructure.MemoryAccessLabel
import org.jetbrains.kotlinx.lincheck.strategy.managed.eventstructure.MutableExtendedExecution
import org.jetbrains.kotlinx.lincheck.strategy.managed.eventstructure.ObjectAllocationLabel
import org.jetbrains.kotlinx.lincheck.strategy.managed.eventstructure.ReadAccessLabel
import org.jetbrains.kotlinx.lincheck.strategy.managed.eventstructure.WriteAccessLabel
import org.jetbrains.kotlinx.lincheck.strategy.managed.eventstructure.enumerationOrderSorted
import org.jetbrains.kotlinx.lincheck.strategy.managed.eventstructure.exclusiveReadPart
import org.jetbrains.kotlinx.lincheck.strategy.managed.eventstructure.getLocationForSameLocationAccesses
import org.jetbrains.kotlinx.lincheck.strategy.managed.eventstructure.getLocationForSameLocationWriteAccesses
import org.jetbrains.kotlinx.lincheck.strategy.managed.eventstructure.happensBeforeOrder
import org.jetbrains.kotlinx.lincheck.strategy.managed.eventstructure.happensBeforeSameLocationOrder
import org.jetbrains.kotlinx.lincheck.strategy.managed.eventstructure.isExclusiveWriteAccess
import org.jetbrains.kotlinx.lincheck.strategy.managed.eventstructure.isWriteAccessTo
import org.jetbrains.kotlinx.lincheck.strategy.managed.eventstructure.isWriteWriteRaceFree
import org.jetbrains.kotlinx.lincheck.strategy.managed.eventstructure.readsFrom
import org.jetbrains.kotlinx.lincheck.strategy.managed.eventstructure.toGraph
import org.jetbrains.kotlinx.lincheck.strategy.managed.eventstructure.topologicalSorting
import org.jetbrains.kotlinx.lincheck.strategy.managed.eventstructure.topologicalSortings
import org.jetbrains.kotlinx.lincheck.util.observes
import org.jetbrains.lincheck.util.Enumerator
import org.jetbrains.lincheck.util.MemoryOrdering
import org.jetbrains.lincheck.util.Relation
import org.jetbrains.lincheck.util.collections.cartesianProduct
import org.jetbrains.lincheck.util.unreachable

class WritesBeforeChecker(val memoryAccessEventIndex: AtomicMemoryAccessEventIndex, val memoryModel: MemoryModel): ExtendedExecutionTracker {

    private val volatileEventEnumerator = MutableEventEnumerator()
    val writesBeforeTracker = WritesBeforeTrackerReleationMatrix()
    val exclusiveWrites = mutableListOf<AtomicThreadEvent>()
    val exclusiveReadsFor = mutableMapOf<MemoryLocation, MutableMap<AtomicThreadEvent, AtomicThreadEvent>>()

    var stale: Boolean = true
    var consistencyResult : Inconsistency? = null


    val events = mutableListOf<AtomicThreadEvent>()
    val inconsistency : Inconsistency?
        get() = null

    override fun onAdd(event: AtomicThreadEvent) {
        val label = event.label as? MemoryAccessLabel ?: return
        // If not write or read response, then we skip
        if (!(label.isWrite || label.isResponse)) return
        //  Make sure that the cached result is not stale
        stale = true
        val location = label.location

        if(label.memoryOrdering == MemoryOrdering.VOLATILE) trackVolaitleEvent(event)
        if(label.isExclusive) trackRMWEvent(event)

        writesBeforeTracker.addEvent(event)

        // In the case of an exclusive write, we need to handle the
        if(label.isWrite && label.isExclusive) {
            val readsFrom = event.exclusiveReadPart.readsFrom
            writesBeforeTracker.setExclusiveWritesBefore(readsFrom, event)
        }

        // Assumes that we handle the allocation event explicitly
        for(write in memoryAccessEventIndex.getWrites(location)) {
            if(happensBeforeOrder(write, event)) {
                writesBeforeTracker.setWritesBefore(write, event)
            }
        }

        for(read in memoryAccessEventIndex.getReadResponses(location)) {
            if(happensBeforeOrder(read, event)) {
                writesBeforeTracker.setWritesBefore(read, event)
            }
        }
    }

    private fun trackRMWEvent(event: AtomicThreadEvent) {
        val label = event.label
        // Track the exclusive reads so we can get better candidates
        if(label is ReadAccessLabel && label.isResponse) {
            check(label.isExclusive)
            val write = event.readsFrom
            exclusiveReadsFor.getOrPut(label.location) { mutableMapOf() }[write] = event
            return
        }
        // Track the exclusive writes for consistency checking
        if(label is WriteAccessLabel)  {
            exclusiveWrites.add(event)
            return
        }
    }

    private fun trackVolaitleEvent(event: AtomicThreadEvent) { volatileEventEnumerator.add(event) }

    override fun onReset(execution: MutableExtendedExecution) {
        stale = true
        writesBeforeTracker.clear()
        volatileEventEnumerator.clear()
        exclusiveWrites.clear()
        exclusiveReadsFor.clear()
        execution.enumerationOrderSorted().forEach { onAdd(it) }
    }

    fun completeCheck() : Inconsistency? {
        if(!stale) return consistencyResult
        consistencyResult = _completeCheck()
        stale = false
        return consistencyResult
    }

    fun _completeCheck() : Inconsistency? {
        val cycle = writesBeforeTracker.hasCycle()
        if(cycle != null) return CoherenceViolation() // TODO: actually add a nicer class

        if(memoryModel == MemoryModel.SequentialConsistency) return doCheckWithTotalOrders()
        if(memoryModel == MemoryModel.JAM21) return doCheckWithTotalOrders()

        return null
    }

    private fun checkSequentialConsistency(): Inconsistency? {
        if (volatileEventEnumerator.list.isEmpty()) return null
        forEachCoherenceOrderSc().forEach { coherenceOrder ->
            // Check sequential Consistency
            val graph = SCBRelation(coherenceOrder).toGraph(volatileEventEnumerator.list, volatileEventEnumerator)
            val sorting = topologicalSorting(graph)
            if(sorting != null) return null
        }
        return CoherenceViolation()
    }

    private fun forEachCoherenceOrderSc(): Sequence<CoherenceRelation> {
        val sortings = writesBeforeTracker.writesBeforeGraphs.entries.filter{
            !it.value.isSingleton()  && !memoryAccessEventIndex.isWriteWriteRaceFree(it.key)
        }.map {
            topologicalSortings(it.value)
        }.toList()

        if(sortings.isEmpty()) {
            return emptySequence()
        }

        return sortings.cartesianProduct().map{
            CoherenceRelation(it)
        }
    }

    private fun doCheckWithTotalOrders() : Inconsistency? {
        // No need to compute the graph if there are no volatile events
        if (volatileEventEnumerator.list.isEmpty()) return null
        writesBeforeTracker.forEachCoherenceOrder().forEach { coherenceOrder ->
            // Check RMWs
            for (write in exclusiveWrites) {
                val writeLabel = write.label as WriteAccessLabel
                // Get the read event to the rmw
                val read = write.parent!!
                val readLabel = read.label as ReadAccessLabel
                check(readLabel.location == writeLabel.location)
                check(readLabel.isResponse)
                check(readLabel.isExclusive)

                val readsFromWrite = read.readsFrom
                // If the exclusive write is not directly after the write that the read reads from,
                // then we this order is bad
                if(!coherenceOrder.isDirectlyAfter(readsFromWrite, write)) return@forEach
            }

            // Check sequential Consistency
            val graph = SCBRelation(coherenceOrder).toGraph(volatileEventEnumerator.list, volatileEventEnumerator)
            val sorting = topologicalSorting(graph)
            if(sorting != null) return null
        }
        return CoherenceViolation()
    }

    fun getExclusiveRead(candidate: AtomicThreadEvent, location: MemoryLocation): AtomicThreadEvent? {
        return exclusiveReadsFor[location]?.get(candidate)
    }

}

class SCBRelation(val coherenceOrder: Relation<AtomicThreadEvent>) : Relation<AtomicThreadEvent> {

    val ecoRelation  = EcoRelation(coherenceOrder)

    override fun invoke(
        x: AtomicThreadEvent,
        y: AtomicThreadEvent
    ): Boolean {
        val writeX = getWrite(x)
        val writeY = getWrite(y)
        // For the CO and Rb part we actually need to make sure that the location is the same
        val location = getLocationForSameLocationAccesses(x, y)
        // CO and RB both at once
        if(location != null && !(x != writeX && writeY != y) && coherenceOrder(writeX, writeY)) return true // eco
        if (x.threadId == y.threadId && x.threadPosition < y.threadPosition) return true // po
        if (happensBeforeSameLocationOrder(x, y)) return true // hbloc

        // po; hb ; po
        val yParent = y.parent ?: return false
        return yParent.happensBeforeClock.observes(x.threadId, x.threadPosition+1)
    }
}

class SCBSimpleRelation(val coherenceOrder: Relation<AtomicThreadEvent>) : Relation<AtomicThreadEvent> {

    val ecoRelation  = EcoRelation(coherenceOrder)

    override fun invoke(
        x: AtomicThreadEvent,
        y: AtomicThreadEvent
    ): Boolean {
        val writeX = getWrite(x)
        val writeY = getWrite(y)
        // For the CO and Rb part we actually need to make sure that the location is the same
        val location = getLocationForSameLocationAccesses(x, y)
        // CO and RB both at once
        if(location != null && !(x != writeX && writeY != y) && coherenceOrder(writeX, writeY)) return true // eco
        if (happensBeforeOrder(x, y)) return true // hb
        return false
    }
}

interface WritesBeforeTracker: Relation<AtomicThreadEvent> {
    fun setWritesBefore(event1 : AtomicThreadEvent, event2: AtomicThreadEvent)
    fun maximalWrites(location: MemoryLocation): Sequence<AtomicThreadEvent>
    fun hasCycle(): Sequence<AtomicThreadEvent>? // Return the cycle if it exists, otherwise null
    fun clear()
}

fun WritesBeforeTracker(): WritesBeforeTracker = WritesBeforeTrackerReleationMatrix()

class WritesBeforeTrackerReleationMatrix() : WritesBeforeTracker {

    val maximalEvents: MutableMap<MemoryLocation, MutableSet<AtomicThreadEvent>> = mutableMapOf()
    val writesBeforeGraphs: MutableMap<MemoryLocation, WritesBeforeGraph> = mutableMapOf()

    override fun setWritesBefore(
        event1: AtomicThreadEvent,
        event2: AtomicThreadEvent
    ) {
        check(eventIsCorrect(event1))
        check(eventIsCorrect(event2))
        val write1 = getWrite(event1)
        val write2 = getWrite(event2)
        val location = getLocationForSameLocationAccesses(event1, event2)!!
        check(write1.label.isWriteAccessTo(location))
        check(write2.label.isWriteAccessTo(location))
        val maximalEventList = maximalEvents.getOrPut(location) { mutableSetOf() }
        maximalEventList.remove(write1)
        maximalEventList.add(write2)
        val graph = writesBeforeGraphs.getOrPut(location) { WritesBeforeGraph() }
        graph.setChild(write1, write2)
    }

    override fun maximalWrites(location: MemoryLocation): Sequence<AtomicThreadEvent> {
        return maximalEvents[location]?.let { return it.asSequence() } ?: emptySequence()
    }

    override fun hasCycle(): Sequence<AtomicThreadEvent>? {
        return writesBeforeGraphs.values.mapNotNull{ it.hasCycle() }.firstOrNull()
    }

    override fun clear() {
        writesBeforeGraphs.values.forEach { it.clear() }
        maximalEvents.values.forEach { it.clear() }
    }

    fun forEachCoherenceOrder(): Sequence<CoherenceRelation> {
        val sortings = writesBeforeGraphs.values.filter{
            !it.isSingleton()
        }.map {
            topologicalSortings(it)
        }.toList()

        if(sortings.isEmpty()) {
            return emptySequence()
        }

        return sortings.cartesianProduct().map{
            CoherenceRelation(it)
        }
    }

    override fun invoke(
        x: AtomicThreadEvent,
        y: AtomicThreadEvent
    ): Boolean {
        TODO("Not yet implemented")
    }

    fun addEvent(memoryAccessEvent: AtomicThreadEvent) {
        val label = memoryAccessEvent.label as MemoryAccessLabel
        val writeEvent = getWrite(memoryAccessEvent) // Get the corresponding write event
        val location = label.location

        // Add it as a maximal event as there are no other events
        val maximalEventList = maximalEvents.getOrPut(location) { mutableSetOf() }
        maximalEventList.add(writeEvent)

        // Add it to the graph
        val graph = writesBeforeGraphs.getOrPut(location) { WritesBeforeGraph() }
        graph.add(writeEvent)
    }

    fun setExclusiveWritesBefore(write1: AtomicThreadEvent, write2: AtomicThreadEvent) {
        return;
        check(isWriteEvent(write1))
        check(isWriteEvent(write2))
        val location = getLocationForSameLocationWriteAccesses(write1, write2)!!
        check(write1.label.isWriteAccessTo(location))
        check(write2.label.isWriteAccessTo(location))
        val maximalEventList = maximalEvents.getOrPut(location) { mutableSetOf() }
        maximalEventList.remove(write1)
        maximalEventList.add(write2)
        val graph = writesBeforeGraphs.getOrPut(location) { WritesBeforeGraph() }
        graph.setExclusiveChild(write1, write2)
    }
}

class WritesBeforeGraph: Graph<AtomicThreadEvent> {

    val children: MutableMap<AtomicThreadEvent, MutableSet<AtomicThreadEvent>> = mutableMapOf()
    var root: AtomicThreadEvent? = null
    val _nodes = mutableSetOf<AtomicThreadEvent>()
    override val nodes: Set<AtomicThreadEvent>
        get() = _nodes

    fun isSingleton() : Boolean {
        val isSingleton = nodes.size == 1
        if(isSingleton) check(root in _nodes) // Make sure that the root is the only event
        return isSingleton
    }

    // Because of static memory locations, we need to initialize the root events statically
    private fun initializeRoot(event: AtomicThreadEvent) {
        if(root != null) {
            // make sure that the root is already added
            check(root in nodes)
            return
        }

        val label = event.label
        // If the event is a regular write event, then we read the allocation event
        if (label is WriteAccessLabel) {
            root = event.allocation!!
        } else {
            // Otherwise the event has to be an allocation or initialization event
            check(label is ObjectAllocationLabel || label is InitializationLabel)
            root = event
        }
        // Final check, just in case
        check(root!!.label is ObjectAllocationLabel || root!!.label is InitializationLabel)

        // Finally add the event internally
        _add(root!!)
    }

    private fun _add(event: AtomicThreadEvent) {
        // Internal add funtions assumes that root handling is already done.
        // Function should be idempotent

        // Skip if already added
        if(event in _nodes) {
            // Double check that it is added already
            check(event in children)
            return
        }
        // Add it to structures
        _nodes.add(event)
        children[event] = mutableSetOf()
    }

    fun setChild(write1: AtomicThreadEvent, write2: AtomicThreadEvent) {
        check(isWriteEvent(write1))
        check(isWriteEvent(write2))
        check(write1 in nodes) { "Write event $write1 - $root is not in the graph" }
        check(write2 in nodes) { "Write event $write2 - $root is not in the graph" }

        if(write1 == write2) return

        children[write1]!!.add(write2)
    }

    fun clear() {
        root = null
        children.clear()
        _nodes.clear()
    }

    fun hasCycle(): Sequence<AtomicThreadEvent>? {
        val sorting = topologicalSorting(this) ?: return emptySequence()
        return null
    }

    override fun adjacent(node: AtomicThreadEvent): List<AtomicThreadEvent> {
        return children[node]?.toList() ?: listOf()
    }

    fun add(write: AtomicThreadEvent) {
        check(isWriteEvent(write))
        // If this is the first write event to be added event, then we also need to set the initial allocation/init event
        initializeRoot(write)
        // If the write is the root, then we are done
        if (write == root) return
        _add(write)
        // Make the new event a child of the root, if it different
        setChild(root!!, write)
    }

    fun setExclusiveChild(write1: AtomicThreadEvent, write2: AtomicThreadEvent) {
        return;
        check(isWriteEvent(write1))
        check(isWriteEvent(write2))
        check(write1 in nodes) { "Write event $write1 - $root is not in the graph" }
        check(write2 in nodes) { "Write event $write2 - $root is not in the graph" }
        check(write1 != write2) { "Exclusive write reads from itself!"}

        // For rmw events we need to make sure that write2 is also a child of any existing rmw

        children[write1]!!.add(write2)

        for(exclusiveChild in children[write1]!!) {
            if(!exclusiveChild.label.isExclusiveWriteAccess()) continue
            if(exclusiveChild == write2) continue
            children[exclusiveChild]!!.add(write2)
        }

        // And if write2 is exclusive then we need to make sure that all existing children are also its children
        if(write2.label.isExclusiveWriteAccess()) {
            for(child in children[write1]!!) {
                if(child == write2) continue
                children[write2]!!.add(child)
            }
        }
    }

}

class CoherenceRelation : Relation<AtomicThreadEvent> {
    val map: Map<AtomicThreadEvent, Int>

    constructor(coherenceLists: List<List<AtomicThreadEvent>>) {
        map = mutableMapOf()
        for (coherenceList in coherenceLists) {
            if(coherenceList.isEmpty()) continue
            check(coherenceList.getLocationForSameLocationWriteAccesses() != null)
            for((i,write) in coherenceList.withIndex()) {
                map[write] = i
            }
        }
    }

    override fun invoke(
        x: AtomicThreadEvent,
        y: AtomicThreadEvent
    ): Boolean {
        val location = getLocationForSameLocationAccesses(x, y) ?: return false
        val orderX = map[x]!!
        val orderY = map[y]!!
        return orderX < orderY
    }

    fun isDirectlyAfter(x: AtomicThreadEvent, y: AtomicThreadEvent) : Boolean {
        val location = getLocationForSameLocationAccesses(x, y) ?: return false
        val orderX = map[x]!!
        val orderY = map[y]!!
        return orderX + 1 == orderY
    }
}

class EcoRelation(val coherenceRelation: Relation<AtomicThreadEvent>) : Relation<AtomicThreadEvent> {
    override fun invoke(
        x: AtomicThreadEvent,
        y: AtomicThreadEvent
    ): Boolean {
        val location = getLocationForSameLocationAccesses(x, y) ?: return false
        val w1 = getWrite(x)
        val w2 = getWrite(y)

        // We need an explicit check here as we can events that are not included in the coherence relation, such as singletons
        if(w1 == w2) return false

        return coherenceRelation(w1, w2)
    }
}

private fun eventIsCorrect(event: AtomicThreadEvent) : Boolean {
    when(event.label) {
        is WriteAccessLabel, is ObjectAllocationLabel, is InitializationLabel -> return true
        is ReadAccessLabel -> return event.label.isResponse
        else -> return false
    }
}

private fun getWrite(event: AtomicThreadEvent): AtomicThreadEvent {
    check(eventIsCorrect(event))
    val label = event.label
    return when(label) {
        is WriteAccessLabel, is ObjectAllocationLabel, is InitializationLabel -> event
        is ReadAccessLabel -> event.readsFrom
        else -> unreachable()
    }
}

class MutableEventEnumerator: Enumerator<AtomicThreadEvent> {

    private val map = mutableMapOf<AtomicThreadEvent, Int>()
    private val _list = mutableListOf<AtomicThreadEvent>()
    val list: List<AtomicThreadEvent>
        get() = _list

    fun add(event: AtomicThreadEvent) {
        map[event] = _list.size
        _list.add(event)
    }

    fun clear() {
        map.clear()
        _list.clear()
    }

    override fun get(x: AtomicThreadEvent): Int {
        return map[x]!!
    }

    override fun get(i: Int): AtomicThreadEvent {
        return _list[i]
    }
}

private fun isWriteEvent(event: AtomicThreadEvent ) : Boolean {
    return event.label is WriteAccessLabel || event.label is ObjectAllocationLabel || event.label is InitializationLabel
}
