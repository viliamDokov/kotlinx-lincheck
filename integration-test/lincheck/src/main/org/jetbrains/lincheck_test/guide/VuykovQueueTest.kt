/*
 * Lincheck
 *
 * Copyright (C) 2019 - 2023 JetBrains s.r.o.
 *
 * This Source Code Form is subject to the terms of the
 * Mozilla Public License, v. 2.0. If a copy of the MPL was not distributed
 * with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.jetbrains.lincheck_test.guide

import org.jetbrains.lincheck.datastructures.ModelCheckingOptions
import org.jetbrains.lincheck.datastructures.Operation
import org.jetbrains.lincheck.datastructures.StressOptions
import org.jetbrains.lincheck_test.datastructures.MPSCQueue
import org.junit.Test

class VuykovQueueTest {
    private val queue = MPSCQueue<Int>()

    @Operation()
    public fun offer(x: Int) = queue.offer(x)

    @Operation(nonParallelGroup = "consumers")
    public fun poll(): Int? = queue.poll()

    @Test
    fun stressTest() = StressOptions().threads(3).check(this::class)

    @Test
    fun modelCheckingTest() = ModelCheckingOptions()
        .threads(3)
        .actorsBefore(2)
        .actorsAfter(2)
        .actorsPerThread(3)
        .check(this::class)

    // NOTE: We oom because we are stuck in a spin-loop
    @Test
    fun eventStructureModelCheckingTest() = ModelCheckingOptions()
        .threads(3)
        .actorsBefore(2)
        .actorsAfter(2)
        .actorsPerThread(3)
        .invocationsPerIteration(1_000)
        .useExperimentalModelChecking()
        .check(this::class)
}