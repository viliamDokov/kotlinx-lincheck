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

import org.jetbrains.kotlinx.lincheck.strategy.managed.eventstructure.consistency.MemoryModel
import org.jetbrains.kotlinx.lincheck_test.AbstractLincheckTest
import org.jetbrains.lincheck.datastructures.IntGen
import org.jetbrains.lincheck.datastructures.ModelCheckingOptions
import org.jetbrains.lincheck.datastructures.Operation
import org.jetbrains.lincheck.datastructures.Options
import org.jetbrains.lincheck.datastructures.Param
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap

@Param(name = "key", gen = IntGen::class, conf = "1:5")
class EConcurrentHashMapTest : AbstractEventStructureTest() {
    private val map = ConcurrentHashMap<Int, Int>()

    @Operation
    fun put(@Param(name = "key") key: Int, value: Int) = map.put(key, value)

    @Operation
    operator fun get(@Param(name = "key") key: Int) = map[key]

    @Operation
    fun remove(@Param(name = "key") key: Int) = map.remove(key)

    override fun <O : Options<O, *>> O.customize() {
        invocationsPerIteration(10000)
        if (this is ModelCheckingOptions) {

//        Generated execution8:
//        | ---------------------------------- |
//        | Thread 1  |  Thread 2  | Thread 3  |
//        | ---------------------------------- |
//        | get(4)    |            |           |
//        | remove(2) |            |           |
//        | ---------------------------------- |
//        | remove(4) | get(5)     | get(1)    |
//        | get(1)    | put(1, -7) | put(5, 4) |
//        | ---------------------------------- |
//        | remove(4) |            |           |
//        | get(5)    |            |           |
//        | ---------------------------------- |
            addCustomScenario {
                initial {
                    actor(EConcurrentHashMapTest::get, 4)
                    actor(EConcurrentHashMapTest::remove, 2)
                }

                parallel {
                    thread {
                        actor(EConcurrentHashMapTest::remove, 4)
                        actor(EConcurrentHashMapTest::get, 1)
                    }
                    thread {
                        actor(EConcurrentHashMapTest::get, 5)
                        actor(EConcurrentHashMapTest::put, 1, 7)
                    }
                    thread {
                        actor(EConcurrentHashMapTest::get, 1)
                        actor(EConcurrentHashMapTest::put, 5, 4)
                    }
                }

                post {
                    actor(EConcurrentHashMapTest::remove, 4)
                    actor(EConcurrentHashMapTest::get, 5)
                }
            }
            iterations(0)
            memoryModel(MemoryModel.SequentialConsistency)
            analyzeStdLib(true)
        }
    }

    @Test(timeout = TIMEOUT)
    fun test() = testWithEventStructureStrategy()
}