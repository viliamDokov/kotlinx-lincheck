/*
 * Lincheck
 *
 * Copyright (C) 2019 - 2026 JetBrains s.r.o.
 *
 * This Source Code Form is subject to the terms of the
 * Mozilla Public License, v. 2.0. If a copy of the MPL was not distributed
 * with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.jetbrains.lincheck_test.datastructures.eventstructure

import org.jetbrains.kotlinx.lincheck.strategy.LincheckFailure

import org.jetbrains.kotlinx.lincheck.*
import org.jetbrains.kotlinx.lincheck.strategy.managed.eventstructure.consistency.Counter
import org.jetbrains.kotlinx.lincheck.strategy.managed.eventstructure.consistency.MemoryModel
import org.jetbrains.lincheck.datastructures.ModelCheckingOptions
import org.jetbrains.kotlinx.lincheck_test.util.*
import org.jetbrains.lincheck.datastructures.Options
import org.jetbrains.lincheck.datastructures.StressOptions
import org.junit.Ignore
import kotlin.reflect.*

abstract class AbstractEventStructureTest(
    private vararg val expectedFailures: KClass<out LincheckFailure>
) {
    open fun <O: Options<O, *>> O.customize() {}

    private fun <O : Options<O, *>> O.runInternalTest() {
        val failure: LincheckFailure? = checkImpl(this@AbstractEventStructureTest::class.java)
        if (failure === null) {
            assert(expectedFailures.isEmpty()) {
                "This test should fail, but no error has been occurred (see the logs for details)"
            }
        } else {
            checkFailureIsNotLincheckInternalBug(failure)
            checkTraceHasNoLincheckEvents(failure.toString())
            assert(expectedFailures.contains(failure::class)) {
                "This test has failed with an unexpected error: \n $failure"
            }
        }
    }

    fun _testWithEventStructureStrategyJAM() : Unit = ModelCheckingOptions().run {
        useExperimentalModelChecking()
        memoryModel(MemoryModel.JAM21)
        commonConfiguration()
        runInternalTest()
    }

    fun _testWithEventStructureStrategySC() : Unit = ModelCheckingOptions().run {
        useExperimentalModelChecking()
        memoryModel(MemoryModel.SequentialConsistency)
        commonConfiguration()
        runInternalTest()
    }

    fun _testWithStressStrategy() : Unit = StressOptions().run {
        commonConfiguration()
        runInternalTest()
    }

    fun _testWithModelCheckingStrategy() : Unit = ModelCheckingOptions().run {
        commonConfiguration()
        runInternalTest()
    }

    private fun <O : Options<O, *>> O.commonConfiguration(): Unit = run {
        invocationsPerIteration(7500)
        iterations(30)
        actorsBefore(2)
        threads(3)
        actorsPerThread(2)
        actorsAfter(2)
        minimizeFailedScenario(false)
        customize()
    }
}

const val TIMEOUT = 20 * 60_000L // 20 min
