/*
 * Lincheck
 *
 * Copyright (C) 2019 - 2025 JetBrains s.r.o.
 *
 * This Source Code Form is subject to the terms of the
 * Mozilla Public License, v. 2.0. If a copy of the MPL was not distributed
 * with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.jetbrains.lincheck_test.datastructures.eventstructure

import org.jetbrains.lincheck.datastructures.IntGen
import org.jetbrains.lincheck.datastructures.Operation
import org.jetbrains.lincheck.datastructures.Options
import org.jetbrains.lincheck.datastructures.Param
import org.jetbrains.lincheck.datastructures.verifier.QuiescentConsistencyVerifier
import org.jetbrains.lincheck.datastructures.verifier.QuiescentConsistent
import org.jetbrains.lincheck_test.datastructures.LockFreeTaskQueue
import org.junit.Test

@Param(name = "value", gen = IntGen::class, conf = "1:3")
class ELockFreeTaskQueueTest : AbstractEventStructureTest() {
    private val q = LockFreeTaskQueue<Int>(true)

    @Operation
    fun addLast(@Param(name = "value") value: Int) = q.addLast(value)

    @QuiescentConsistent
    @Operation(nonParallelGroup = "consumer")
    fun removeFirstOrNull() = q.removeFirstOrNull()

    @Operation
    fun close() = q.close()

    override fun <O : Options<O, *>> O.customize() {
        verifier(QuiescentConsistencyVerifier::class.java)
    }

    @Test(timeout = TIMEOUT)
    fun testWithEventStructureStrategyJAM() = _testWithEventStructureStrategyJAM()
    @Test(timeout = TIMEOUT)
    fun testWithEventStructureStrategySC() = _testWithEventStructureStrategySC()
    @Test(timeout = TIMEOUT)
    fun testWithModelCheckingStrategy() = _testWithModelCheckingStrategy()
    @Test(timeout = TIMEOUT)
    fun testWithStressStrategy() = _testWithStressStrategy()
}