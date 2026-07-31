/*
 * Lincheck
 *
 * Copyright (C) 2019 - 2023 JetBrains s.r.o.
 *
 * This Source Code Form is subject to the terms of the
 * Mozilla Public License, v. 2.0. If a copy of the MPL was not distributed
 * with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.jetbrains.lincheck_test.datastructures.eventstructure

import org.jetbrains.kotlinx.lincheck.strategy.managed.eventstructure.consistency.MemoryModel
import org.jetbrains.kotlinx.lincheck_test.AbstractLincheckTest
import org.jetbrains.lincheck.datastructures.*
import java.util.concurrent.*
import org.junit.*

class EConcurrentLinkedDequeTest  {
    private val deque = ConcurrentLinkedDeque<Int>()

    @Operation
    fun addFirst(e: Int) = deque.addFirst(e)

    @Operation
    fun addLast(e: Int) = deque.addLast(e)

    @Operation
    fun pollFirst() = deque.pollFirst()

    @Operation
    fun pollLast() = deque.pollLast()

    @Operation
    fun peekFirst() = deque.peekFirst()

    @Operation
    fun peekLast() = deque.peekLast()

    @Test(expected = AssertionError::class)
    fun testWithEventStructureStrategyJAM() = ModelCheckingOptions()
        .useExperimentalModelChecking()
        .memoryModel(MemoryModel.JAM21)
        .iterations(30)
        .invocationsPerIteration(1000)
        .check(this::class)

    @Test(expected = AssertionError::class)
    fun testWithEventStructureStrategySC() = ModelCheckingOptions()
        .useExperimentalModelChecking()
        .memoryModel(MemoryModel.SequentialConsistency)
        .iterations(30)
        .invocationsPerIteration(1000)
        .check(this::class)

    @Test(expected = AssertionError::class)
    fun testWithModelCheckingStrategy() = ModelCheckingOptions()
        .iterations(30)
        .invocationsPerIteration(1000)
        .check(this::class)

    @Test
    fun testWithStressStrategy() = StressOptions()
        .iterations(30)
        .invocationsPerIteration(1000)
        .check(this::class)
}
