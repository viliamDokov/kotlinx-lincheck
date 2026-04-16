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
import org.jetbrains.kotlinx.lincheck.strategy.managed.eventstructure.Execution
import org.jetbrains.kotlinx.lincheck.strategy.managed.eventstructure.MutableExtendedExecution
import org.jetbrains.kotlinx.lincheck.strategy.managed.eventstructure.ThreadEvent
import org.jetbrains.kotlinx.lincheck.strategy.managed.eventstructure.ThreadFinishLabel
import org.jetbrains.kotlinx.lincheck.strategy.managed.eventstructure.ThreadForkLabel
import org.jetbrains.kotlinx.lincheck.strategy.managed.eventstructure.ThreadJoinLabel
import org.jetbrains.kotlinx.lincheck.strategy.managed.eventstructure.ThreadStartLabel
import org.jetbrains.kotlinx.lincheck.strategy.managed.eventstructure.asThreadForkLabel
import org.jetbrains.kotlinx.lincheck.strategy.managed.eventstructure.isAcquire
import org.jetbrains.kotlinx.lincheck.strategy.managed.eventstructure.isRelease
import org.jetbrains.kotlinx.lincheck.strategy.managed.eventstructure.isWrite
import org.jetbrains.kotlinx.lincheck.strategy.managed.eventstructure.locations
import org.jetbrains.kotlinx.lincheck.strategy.managed.eventstructure.readsFrom
import org.jetbrains.kotlinx.lincheck.strategy.managed.eventstructure.readsFromOpt
import org.jetbrains.kotlinx.lincheck.strategy.managed.eventstructure.sameLocation
import org.jetbrains.kotlinx.lincheck.util.ThreadId
import org.jetbrains.lincheck.util.Computable
import org.jetbrains.lincheck.util.Relation
import org.jetbrains.lincheck.util.RelationMatrix
import org.jetbrains.lincheck.util.toEnumerator

class JAM21Checker(
    val execution: Execution<AtomicThreadEvent>,
    val memoryAccessEventIndex: AtomicMemoryAccessEventIndex,
    val programOrder: Relation<ThreadEvent>
) : ConsistencyChecker<AtomicThreadEvent, MutableExtendedExecution> {

    class CycleIncosistency: Inconsistency() {}

    override fun check(execution: MutableExtendedExecution): Inconsistency? {

        val vo = VisibilityOrder(execution, memoryAccessEventIndex, programOrder)
        vo.compute()

        if(!vo.isIrreflexive) {
            return CycleIncosistency()
        } else {
            return null
        }
    }

}

class VisibilityOrder(
    val execution: Execution<AtomicThreadEvent>,
    val memoryAccessEventIndex: AtomicMemoryAccessEventIndex,
    val programOrder: Relation<ThreadEvent>
): Computable {

    private val _events = events()
    var isIrreflexive: Boolean = false

    sealed class PoopLocation: Comparable<PoopLocation> {
        abstract val classNum: Int
        abstract val cmp: Int
        data class PoopMemoryLocation(val memoryLocation: MemoryLocation): PoopLocation() {
            override val classNum = 0
            override val cmp = memoryLocation.objID
        }
        data class PoopThreadId(val threadId: ThreadId): PoopLocation() {
            override val classNum = 1
            override val cmp = threadId
        }

        override fun compareTo(other: PoopLocation): Int {
            return compareBy<PoopLocation>( {it.classNum}, {it.cmp}).compare(this, other)
        }

    }

    data class PoopEvent(
        val location: PoopLocation,
        val event: AtomicThreadEvent,
    ): Comparable<PoopEvent> {
        override fun compareTo(other: PoopEvent): Int {
            return compareBy<PoopEvent>({it.event}, {it.location} ).compare(this, other)
        }

    }

    private fun events(): Iterable<PoopEvent> {
        val normalAccesses = memoryAccessEventIndex.locations.flatMap {
            memoryAccessEventIndex.getWrites(it).map{foo -> PoopEvent(PoopLocation.PoopMemoryLocation(it), foo)} +
            memoryAccessEventIndex.getReadResponses(it).map { foo -> PoopEvent(PoopLocation.PoopMemoryLocation(it), foo) }
        }

        val threadOperations = execution.mapNotNull { event ->
            when  {
                (event.label) is ThreadForkLabel -> PoopEvent(PoopLocation.PoopThreadId((event.label as ThreadForkLabel).forkThreadIds.first()), event)
                (event.label) is ThreadStartLabel && event.label.isResponse -> PoopEvent(PoopLocation.PoopThreadId((event.label as ThreadStartLabel).threadId), event)
                (event.label) is ThreadFinishLabel  -> PoopEvent(PoopLocation.PoopThreadId((event.label as ThreadFinishLabel).finishedThreadIds.first()), event)
                (event.label) is ThreadJoinLabel && event.label.isResponse && event.senders.size == 1 -> {
                    val threadFinishEvent = event.senders.first()
                    val threadId = threadFinishEvent.threadId
                    PoopEvent(PoopLocation.PoopThreadId(threadId), event)
                }
                else -> null
            }
        }

        return normalAccesses + threadOperations
    }



    override fun compute() {
        // let rfso = rf  //and some other stuff
        // let rel = W & (RA | V)
        // let acq = R & (RA | V)
        // let ra = po;[rel] | [acq];po | rfso
        // let vo = ra+ | po-loc

        //TODO: figure out allocation labels
        val rf = RelationMatrix(_events.toList(),_events.toEnumerator())
        for(event1 in rf.nodes) {
            for(event2 in rf.nodes) {
                // TODO: from which poop event do we really read from?
                rf[event1,event2] = false
                if(event2.event.readsFromOpt == event1.event && event1.location == event2.location) rf[event1, event2] = true
            }
        }
        val poloc = RelationMatrix(_events.toList(),_events.toEnumerator())
        for(event1 in poloc.nodes) {
            for(event2 in poloc.nodes) {
                poloc[event1,event2] = false
                if(programOrder(event1.event, event2.event) && event1.location == event2.location) poloc[event1, event2] = true
            }
        }

        val ra =  RelationMatrix(_events.toList(),_events.toEnumerator())
        for(event1 in ra.nodes) {
            for(event2 in ra.nodes) {
                ra[event1, event2] = false
                if(rf[event1, event2]) ra[event1, event2] = true
                if(programOrder(event1.event, event2.event) && event2.event.isRelease) ra[event1, event2] = true
                if(programOrder(event1.event, event2.event) && event1.event.isAcquire) ra[event1, event2] = true
            }
        }
        ra.transitiveClosure()
        val vo = ra.copy()
        for(event1 in vo.nodes) {
            for(event2 in vo.nodes) {
                if(poloc[event1, event2]) vo[event1, event2] = true
            }
        }
        // let coww = wwco(vo)
        // let cowr = wwco(vo;invrf)
        // let corw = wwco(vo;po-loc)
        // let corr = wwco(rf;po-loc;invrf)

        //TODO: VERY VERY SLOW AND BAD
        val invrf = rf.copy().also { it.transpose() }
        val voCinvrf = vo.compose(invrf)
        val voCpoloc = vo.compose(poloc)
        val rfCpolocCinvrf = rf.compose(poloc).compose(invrf)


        val co = RelationMatrix(_events.toList(),_events.toEnumerator())
        for(w1 in vo.nodes) {
            for(w2 in vo.nodes) {
                co[w1,w2] = false
                if(w1.event.isWrite && w2.event.isWrite && w1 != w2 && w1.location == w2.location) {
                    if(vo[w1, w2]) co[w1, w2] = true
                    if(voCinvrf[w1, w2]) co[w1, w2] = true
                    if(voCpoloc[w1, w2]) co[w1, w2] = true
                    if(rfCpolocCinvrf[w1, w2]) co[w1, w2] = true
                }
            }
        }

        co.transitiveClosure()
        for(w1 in vo.nodes) {
            for(w2 in vo.nodes) {
                if(co[w1, w2]) {
                    check(w1.event.isWrite)
                    check(w2.event.isWrite)
                }
            }
        }
        isIrreflexive = co.isIrreflexive()
    }

    override fun reset() {
        TODO("Not yet implemented")
    }

}
