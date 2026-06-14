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
import org.jetbrains.lincheck.util.Computable
import org.jetbrains.lincheck.util.Relation
import org.jetbrains.lincheck.util.RelationMatrix
import org.jetbrains.lincheck.util.ensure
import org.jetbrains.lincheck.util.union
import java.util.LinkedList
import kotlin.collections.*


abstract class SequentialConsistencyViolation : Inconsistency()

class SequentialConsistencyChecker(
    val checkReleaseAcquireConsistency: Boolean = true,
    val approximateSequentialConsistency: Boolean = true,
    val checkCoherence: Boolean = true,
) : ConsistencyChecker<AtomicThreadEvent, MutableExtendedExecution> {

    private val releaseAcquireChecker : ReleaseAcquireConsistencyChecker? =
        if (checkReleaseAcquireConsistency) ReleaseAcquireConsistencyChecker() else null

    private val coherenceChecker : CoherenceChecker? =
        if (checkCoherence) CoherenceChecker() else null

    override fun check(execution: MutableExtendedExecution): Inconsistency? {
        check(!execution.executionOrderComputable.computed)
        releaseAcquireChecker?.check(execution)
            ?.let { return it }
        coherenceChecker?.check(execution)
            ?.let { return it }
        check(execution.executionOrderComputable.computed)
        val executionOrder = execution.executionOrderComputable.value
            .ensure { it.isConsistent() }
        SequentialConsistencyReplayer().ensure {
            it.replay(executionOrder.ordering) != null
        }
        return null
    }

    /*
        // we will gradually approximate the total sequential execution order of events
        // by a partial order, starting with the partial causality order
        var executionOrderApproximation : Relation<AtomicThreadEvent> = causalityOrder
        // first try to check release/acquire consistency (it is cheaper) ---
        // release/acquire inconsistency will also imply violation of sequential consistency,
        if (releaseAcquireChecker != null) {
            when (val verdict = releaseAcquireChecker.check(execution)) {
                is ReleaseAcquireInconsistency -> return verdict
                is ConsistencyWitness -> {
                    // if execution is release/acquire consistent,
                    // the writes-before relation can be used
                    // to refine the execution ordering approximation
                    val rmwChainsStorage = verdict.witness.rmwChainsStorage
                    val writesBefore = verdict.witness.writesBefore
                    executionOrderApproximation = executionOrderApproximation union writesBefore
                    // TODO: combine SC approximation phase with coherence phase
                    if (computeCoherenceOrdering) {
                        return checkByCoherenceOrdering(execution, memoryAccessEventIndex, rmwChainsStorage, writesBefore)
                    }
                }
            }
        }
        // TODO: combine SC approximation phase with coherence phase (and remove this check)
        check(!computeCoherenceOrdering)
        if (approximateSequentialConsistency) {
            // TODO: embed the execution order approximation relation into the execution instance,
            //   so that this (and following stages) can be implemented as separate consistency check classes
            val executionIndex = MutableAtomicMemoryAccessEventIndex()
                .apply { index(execution) }
            val scApprox = SequentialConsistencyOrder(execution, executionIndex, executionOrderApproximation).apply {
                initialize()
                compute()
            }
            if (!scApprox.isConsistent()) {
                return SequentialConsistencyApproximationInconsistency()
            }
            executionOrderApproximation = scApprox
        }
        // get dependency covering to guide the search
        val covering = execution.buildExternalCovering(executionOrderApproximation)
        // aggregate atomic events before replaying
        val (aggregated, remapping) = execution.aggregate(ThreadAggregationAlgebra.aggregator())
        // check consistency by trying to replay execution using sequentially consistent abstract machine
        return checkByReplaying(aggregated, covering.aggregate(remapping))
    */


    // private fun checkByCoherenceOrdering(
    //     execution: Execution<AtomicThreadEvent>,
    //     executionIndex: AtomicMemoryAccessEventIndex,
    //     rmwChainsStorage: ReadModifyWriteOrder,
    //     wbRelation: WritesBeforeOrder,
    // ): ConsistencyVerdict<SequentialConsistencyWitness> {
    //     val writesOrder = causalityOrder union wbRelation
    //     val executionOrderComputable = computable {
    //         ExecutionOrder(execution, executionIndex, Relation.empty())
    //     }
    //     val coherence = CoherenceOrder(execution, executionIndex, rmwChainsStorage, writesOrder,
    //             executionOrder = executionOrderComputable
    //         )
    //         .apply { initialize(); compute() }
    //     if (!coherence.isConsistent())
    //         return SequentialConsistencyCoherenceViolation()
    //     val executionOrder = executionOrderComputable.value.ensure { it.isConsistent() }
    //     SequentialConsistencyReplayer(1 + execution.maxThreadID).ensure {
    //         it.replay(executionOrder.ordering) != null
    //     }
    //     return SequentialConsistencyWitness.create(executionOrder.ordering)
    // }

}

class CoherenceViolation : SequentialConsistencyViolation() {
    override fun toString(): String {
        // TODO: what information should we display to help identify the cause of inconsistency?
        return "Sequential consistency coherence violation detected"
    }
}

class IncrementalSequentialConsistencyChecker(
    execution: MutableExtendedExecution,
    checkReleaseAcquireConsistency: Boolean = true,
    approximateSequentialConsistency: Boolean = true
) : AbstractPartialIncrementalConsistencyChecker<AtomicThreadEvent, MutableExtendedExecution>(
    execution = execution,
    checker = SequentialConsistencyChecker(
        checkReleaseAcquireConsistency,
        approximateSequentialConsistency,
    )
) {

    private val lockConsistencyChecker = LockConsistencyChecker()

    override fun doIncrementalCheck(event: AtomicThreadEvent): ConsistencyVerdict {
        check(state is ConsistencyVerdict.Consistent)
        check(execution.executionOrderComputable.computed)
        resetRelations()
        val executionOrder = execution.executionOrderComputable.value
        if (!executionOrder.isConsistentExtension(event)) {
            // if we end up in an unknown state, reset the execution order,
            // so it can be re-computed by the full consistency check
            execution.executionOrderComputable.reset()
            return ConsistencyVerdict.Unknown
        }
        executionOrder.add(event)
        return ConsistencyVerdict.Consistent
    }

    override fun doLightweightCheck(): ConsistencyVerdict {
        // TODO: extract into separate checker
        lockConsistencyChecker.check(execution)?.let { inconsistency ->
            return ConsistencyVerdict.Inconsistent(inconsistency)
        }
        // check by trying to replay execution order
        if (state == ConsistencyVerdict.Consistent) {
            check(execution.executionOrderComputable.computed)
            val replayer = SequentialConsistencyReplayer()
            val executionOrder = execution.executionOrderComputable.value
            if (replayer.replay(executionOrder.ordering) != null) {
                // if replay is successful, return "consistent" verdict
                return ConsistencyVerdict.Consistent
            }
        }
        // if we end up in an unknown state, reset the execution order,
        // so it can be re-computed by the full consistency check
        execution.executionOrderComputable.reset()
        return ConsistencyVerdict.Unknown
    }

    override fun doReset(): ConsistencyVerdict {
        resetRelations()
        execution.executionOrderComputable.apply {
            reset()
            // set state to `computed`,
            // so we can push the events into the execution order
            setComputed()
        }
        for (event in execution.enumerationOrderSorted()) {
            val verdict = doIncrementalCheck(event)
            if (verdict is ConsistencyVerdict.Unknown) {
                return ConsistencyVerdict.Unknown
            }
        }
        return ConsistencyVerdict.Consistent
    }

    private fun ExecutionOrder.isConsistentExtension(event: AtomicThreadEvent): Boolean {
        val last = ordering.lastOrNull()
        val label = event.label
        // TODO: for this check to be more robust,
        //   can we generalize it to work with the arbitrary aggregation algebra?
        return when {
            label is ReadAccessLabel && label.isResponse ->
                // TODO: also check that read reads-from some consistent write:
                //   e.g. the globally last write, or the last observed write
                event.isValidResponse(last!!)

            label is WriteAccessLabel && label.isExclusive ->
                event.isWritePartOfAtomicUpdate(last!!)

            else -> true
        }
    }

    // TODO: move to corresponding individual consistency checkers
    private fun resetRelations() {
        execution.writesBeforeOrderComputable.reset()
        execution.coherenceOrderComputable.reset()
        execution.extendedCoherenceComputable.reset()
    }

}


class SequentialConsistencyApproximationInconsistency : SequentialConsistencyViolation() {
    override fun toString(): String {
        // TODO: what information should we display to help identify the cause of inconsistency?
        return "Approximate sequential inconsistency detected"
    }
}

class SequentialConsistencyOrder(
    val execution: Execution<AtomicThreadEvent>,
    val memoryAccessEventIndex: AtomicMemoryAccessEventIndex,
    val memoryAccessOrder: Relation<AtomicThreadEvent>,
) : Relation<AtomicThreadEvent>, Computable {

    // TODO: make cached delegate?
    private var consistent = true

    private var relation: RelationMatrix<AtomicThreadEvent>? = null

    override fun invoke(x: AtomicThreadEvent, y: AtomicThreadEvent): Boolean =
        relation?.invoke(x, y) ?: false

    fun isConsistent(): Boolean {
        return consistent
    }

    override fun initialize() {
        // TODO: optimize -- build the relation only for write and read-response events
        relation = RelationMatrix(execution, execution.buildEnumerator())
    }

    override fun compute() {
        val relation = this.relation!!
        relation.add(memoryAccessOrder)
        relation.fixpoint {
            // TODO: maybe we can remove this check without affecting performance?
            if (!isIrreflexive()) {
                consistent = false
                return@fixpoint
            }
            coherenceClosure()
            transitiveClosure()
        }
    }

    override fun invalidate() {
        consistent = true
    }

    override fun reset() {
        invalidate()
        relation = null
    }

    private fun RelationMatrix<AtomicThreadEvent>.coherenceClosure() {
        for (location in memoryAccessEventIndex.locations) {
            coherenceClosure(location)
        }
    }

    private fun RelationMatrix<AtomicThreadEvent>.coherenceClosure(location: MemoryLocation) {
        val relation = this
        for (read in memoryAccessEventIndex.getReadResponses(location)) {
            for (write in memoryAccessEventIndex.getWrites(location)) {
                if (relation(write, read) && write != read.readsFrom) {
                    relation[write, read.readsFrom] = true
                }
                if (relation(read.readsFrom, write)) {
                    relation[read, write] = true
                }
            }
        }
    }

}

open class ExecutionOrder(
    open val execution: Execution<AtomicThreadEvent>,
    open val memoryAccessEventIndex: AtomicMemoryAccessEventIndex,
    open val approximation: Relation<AtomicThreadEvent>,
) : Relation<AtomicThreadEvent>, Computable {

    private var consistent = true

    private val _ordering = mutableListOf<AtomicThreadEvent>()

    open val ordering: List<AtomicThreadEvent>
        get() = _ordering

    private val constraints = Relation<AtomicThreadEvent> { x, y ->
        when {
            // put wait-request before notify event
            x.label.isRequest && x.label is WaitLabel ->
                (y == execution.getResponse(x)?.notifiedBy)

            else -> false
        }
    }

    override fun invoke(x: AtomicThreadEvent, y: AtomicThreadEvent): Boolean {
        TODO("Not yet implemented")
    }

    open fun isConsistent(): Boolean =
        // TODO: embed failure state into ComputableNode state machine?
        consistent

    open fun add(event: AtomicThreadEvent) {
        check(consistent)
        _ordering.add(event)
    }

    override fun compute() {
        check(_ordering.isEmpty())
        val relation = approximation union constraints

        // TODO: optimization --- we can build graph only for a subset of events, excluding:
        //  - non-blocking request events
        //  - events accessing race-free locations
        //  - what else?
        //  and then insert them back into the topologically sorted list
        // construct aggregated execution consisting of atomic events
        // to incorporate the atomicity constraints during the search for topological sorting

        val (aggregatedExecution, _) = execution.aggregate(ThreadAggregationAlgebra.aggregator())
        val aggregatedRelation = relation.existsLifting()
        val graph = aggregatedExecution.buildGraph(aggregatedRelation)
        val ordering = topologicalSorting(graph)

        if (ordering == null) {
            consistent = false
            return
        }

        this._ordering.addAll(ordering.flatMap { it.events })
    }

    override fun invalidate() {
        consistent = true
    }

    override fun reset() {
        _ordering.clear()
        invalidate()
    }

}

class ExecutionOrderFast(
    override val execution: Execution<AtomicThreadEvent>,
    override val memoryAccessEventIndex: AtomicMemoryAccessEventIndex,
    val causalGraph: CausalGraph,
    val eco: ExtendedCoherenceOrder,
    override val approximation: Relation<AtomicThreadEvent>,
) : ExecutionOrder(execution, memoryAccessEventIndex, approximation) {

    private var consistent = true

    private val _ordering = mutableListOf<AtomicThreadEvent>()

    override val ordering: List<AtomicThreadEvent>
        get() = _ordering

    fun adjacent(node: AtomicThreadEvent) : Sequence<AtomicThreadEvent> {
        val seq = mutableListOf<AtomicThreadEvent>()
        eco.adjacentForEach(node) { n -> seq.add(n) }
        causalGraph.adjacentForEach(node) { n -> seq.add(n) }
        if ( node.label.isRequest && node.label is WaitLabel ) {
            val resp = execution.getResponse(node)?.notifiedBy
            if (resp != null) seq.add(resp)
        }
        return seq.asSequence()
    }

    inline fun forEachAdjacent(node: AtomicThreadEvent, block : (AtomicThreadEvent) -> Unit) {
        eco.adjacentForEach(node) { block(it) }
        causalGraph.adjacentForEach(node) { block(it) }
        if (node.label.isRequest && node.label is WaitLabel ) {
            val resp = execution.getResponse(node)?.notifiedBy
            if (resp != null) block(resp)
        }
    }

    override fun invoke(x: AtomicThreadEvent, y: AtomicThreadEvent): Boolean {
        TODO("Not yet implemented")
    }

    override fun isConsistent(): Boolean =
        // TODO: embed failure state into ComputableNode state machine?
        consistent

    override fun add(event: AtomicThreadEvent) {
        check(consistent)
        _ordering.add(event)
    }

    override fun compute() {
        check(_ordering.isEmpty())

        // TODO: optimization --- we can build graph only for a subset of events, excluding:
        //  - non-blocking request events
        //  - events accessing race-free locations
        //  - what else?
        //  and then insert them back into the topologically sorted list

        val ordering = topologicalSorting()

        if (ordering == null) {
            consistent = false
            return
        }

        this._ordering.addAll(ordering )
//        this._ordering.addAll(ordering.flatMap { it.events })
    }

    fun topologicalSorting() : List<AtomicThreadEvent>? {
        val queue = LinkedList<Pair<Boolean, AtomicThreadEvent>>()
        // The only event without any deps is the INIT event
        queue.add(Pair(false, execution[-1,0]!!))
        check(execution.first().label is InitializationLabel)

        val marked = mutableSetOf<ThreadEvent>()
        val done = mutableSetOf<ThreadEvent>()
        val result = mutableListOf<AtomicThreadEvent>()

        while (queue.isNotEmpty()) {
            val (isDone, node) = queue.removeLast()
            if (node in done) continue
            if (isDone) {
                done.add(node)
                result.add(node)
                continue
            }
            // We have a cycle
            if (node in marked) {
                return null
            }
            marked.add(node)

            // Add the action marking that the queue is over
            queue.add(Pair(true, node))

            val child = execution[node.threadId, node.threadPosition+1]
            if (child != null) {
                check(child.parent == node) { "Child event ${child} has wrong parent ${child.parent}, expected ${node}!" }
                queue.add(Pair(false, child))
            }

            forEachAdjacent(node) { neighbour ->
                if (neighbour == child) return@forEachAdjacent // TODO: maybe add assert that the child is always a neighbour?

                // To handle atomic events, we add the start of the execution
                var start: AtomicThreadEvent = neighbour;
                while(start.parent != null && ThreadAggregationAlgebra.synchronizable(start.parent!!.label, start.label)) {
                    start = start.parent!!
                }

                if (start == node) return@forEachAdjacent
                queue.add(Pair(false, start))
            }
        }

        check(result.size == execution.size)
        return result.reversed()
    }


    override fun invalidate() {
        consistent = true
    }

    override fun reset() {
        _ordering.clear()
        invalidate()
    }

}