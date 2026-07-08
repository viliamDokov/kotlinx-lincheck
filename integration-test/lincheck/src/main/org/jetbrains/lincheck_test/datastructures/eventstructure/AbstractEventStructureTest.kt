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
import org.junit.Test

import org.jetbrains.kotlinx.lincheck.*
import org.jetbrains.kotlinx.lincheck.strategy.*
import org.jetbrains.lincheck.datastructures.ModelCheckingOptions
import org.jetbrains.lincheck.datastructures.StressOptions
import org.jetbrains.kotlinx.lincheck_test.util.*
import org.jetbrains.lincheck.datastructures.Options
import org.junit.*
import org.junit.Assume.assumeTrue
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

    fun testWithEventStructureStrategy() : Unit = ModelCheckingOptions().run {
        invocationsPerIteration(1_000)
        useExperimentalModelChecking()
        commonConfiguration()
        runInternalTest()
    }

    private fun <O : Options<O, *>> O.commonConfiguration(): Unit = run {
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
