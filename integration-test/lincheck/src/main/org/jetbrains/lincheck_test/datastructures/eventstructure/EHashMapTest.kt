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

import org.jetbrains.kotlinx.lincheck.strategy.IncorrectResultsFailure
import org.jetbrains.kotlinx.lincheck.strategy.UnexpectedExceptionFailure
import org.jetbrains.lincheck.datastructures.IntGen
import org.jetbrains.lincheck.datastructures.Operation
import org.jetbrains.lincheck.datastructures.Param
import org.junit.Test
import java.util.HashMap
import kotlin.collections.get

@Param(name = "key", gen = IntGen::class)
class EHashMapTest : AbstractEventStructureTest(IncorrectResultsFailure::class, UnexpectedExceptionFailure::class) {
    private val m = HashMap<Int, Int>()

    @Operation
    fun put(key: Int, @Param(name = "key") value: Int): Int? = m.put(key, value)

    @Operation
    operator fun get(@Param(name = "key") key: Int?): Int? = m[key]

    @Test(timeout = TIMEOUT)
    fun testWithEventStructureStrategyJAM() = _testWithEventStructureStrategyJAM()
    @Test(timeout = TIMEOUT)
    fun testWithEventStructureStrategySC() = _testWithEventStructureStrategySC()
    @Test(timeout = TIMEOUT)
    fun testWithModelCheckingStrategy() = _testWithModelCheckingStrategy()
    @Test(timeout = TIMEOUT)
    fun testWithStressStrategy() = _testWithStressStrategy()
}
