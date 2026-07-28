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

import org.jetbrains.kotlinx.lincheck.strategy.IncorrectResultsFailure
import org.jetbrains.kotlinx.lincheck.strategy.UnexpectedExceptionFailure
import org.jetbrains.kotlinx.lincheck.strategy.managed.eventstructure.consistency.MemoryModel
import org.jetbrains.kotlinx.lincheck_test.datastructures.*
import org.jetbrains.lincheck.datastructures.*
import org.junit.*

class EObstructionFreedomViolationTest  {
    private val q = MSQueueBlocking()

    @Operation
    fun enqueue(x: Int) = q.enqueue(x)

    @Operation
    fun dequeue(): Int? = q.dequeue()

    @Test(expected = AssertionError::class)
    fun testWithModelCheckingStrategy() = ModelCheckingOptions()
        .iterations(30)
        .invocationsPerIteration(7500)
        .checkObstructionFreedom(true)
        .check(this::class)

    @Test(expected = AssertionError::class)
    fun testWithEventStructureStrategyJAM() = ModelCheckingOptions()
        .iterations(30)
        .invocationsPerIteration(7500)
        .useExperimentalModelChecking()
        .memoryModel(MemoryModel.JAM21)
        .checkObstructionFreedom(true)
        .check(this::class)

    @Test(expected = AssertionError::class)
    fun testWithEventStructureStrategySC() = ModelCheckingOptions()
        .iterations(30)
        .invocationsPerIteration(7500)
        .useExperimentalModelChecking()
        .memoryModel(MemoryModel.SequentialConsistency)
        .checkObstructionFreedom(true)
        .check(this::class)


    @Test() // No obstruction-freedom checking for stress testing
    fun testWithStressStrategy() = StressOptions()
        .iterations(30)
        .invocationsPerIteration(7500)
        .check(this::class)
}