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

import org.jetbrains.kotlinx.lincheck.strategy.managed.eventstructure.AtomicMemoryAccessEventIndex
import org.jetbrains.kotlinx.lincheck.strategy.managed.eventstructure.AtomicThreadEvent
import org.jetbrains.kotlinx.lincheck.strategy.managed.eventstructure.Execution
import org.jetbrains.kotlinx.lincheck.strategy.managed.eventstructure.ReadAccessLabel
import org.jetbrains.kotlinx.lincheck.strategy.managed.eventstructure.ThreadEvent
import org.jetbrains.kotlinx.lincheck.strategy.managed.eventstructure.buildEnumerator
import org.jetbrains.kotlinx.lincheck.strategy.managed.eventstructure.readsFrom
import org.jetbrains.lincheck.util.Computable
import org.jetbrains.lincheck.util.Relation
import org.jetbrains.lincheck.util.RelationMatrix
import org.jetbrains.lincheck.util.refine

class VisibilityOrder(
    val execution: Execution<AtomicThreadEvent>,
    val memoryAccessEventIndex: AtomicMemoryAccessEventIndex,
    val programOrder: Relation<ThreadEvent>
): Relation<AtomicThreadEvent>, Computable {

    val relation: RelationMatrix<AtomicThreadEvent> = RelationMatrix(execution.toList(),execution.buildEnumerator())


    override fun invoke(
        x: AtomicThreadEvent,
        y: AtomicThreadEvent
    ): Boolean {
        return relation[x,y]
    }

    override fun compute() {
        // let rfso = rf  //and some other stuff
        // let rel = W & (RA | V)
        // let acq = R & (RA | V)
        // let ra = po;[rel] | [acq];po | rfso
        // let vo = ra+ | po-loc
        val rf = RelationMatrix(execution.toList(), execution.buildEnumerator())
        execution.forEach {  read ->
            if(read.label is ReadAccessLabel && read.label.isResponse)
            rf[read, read.readsFrom]  = true
        }

        programOrder
    }

    override fun reset() {
        TODO("Not yet implemented")
    }

}
