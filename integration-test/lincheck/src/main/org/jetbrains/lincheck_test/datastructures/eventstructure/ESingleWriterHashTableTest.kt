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
import org.jetbrains.kotlinx.lincheck_test.TIMEOUT
import org.jetbrains.lincheck.datastructures.*
import org.jetbrains.lincheck_test.datastructures.SingleWriterHashTable
import kotlin.reflect.KClass
import org.junit.Test

class SingleWriterHashTableTest() {
    val sequentialSpecification: KClass<*> = SequentialHashTableIntInt::class

    private val hashTable = SingleWriterHashTable<Int, Int>(initialCapacity = 30)

    @Operation
    fun put(key: Int, value: Int): Int? = hashTable.put(key, value)

    @Operation
    fun get(key: Int): Int? = hashTable.get(key)

    @Operation
    fun remove(key: Int): Int? = hashTable.remove(key)


    @Test(timeout = TIMEOUT, expected = AssertionError::class)
    fun testWithEventStructureStrategyJAM() = ModelCheckingOptions()
        .useExperimentalModelChecking()
        .memoryModel(MemoryModel.JAM21)
        .iterations(30)
        .invocationsPerIteration(7500)
        .checkObstructionFreedom(true)
        .sequentialSpecification(sequentialSpecification.java)
        .minimizeFailedScenario(false)
        .check(this::class.java)

    @Test(timeout = TIMEOUT, expected = AssertionError::class)
    fun testWithEventStructureStrategySC() = ModelCheckingOptions()
        .useExperimentalModelChecking()
        .memoryModel(MemoryModel.SequentialConsistency)
        .iterations(30)
        .invocationsPerIteration(7500)
        .checkObstructionFreedom(true)
        .sequentialSpecification(sequentialSpecification.java)
        .minimizeFailedScenario(false)
        .check(this::class.java)

    @Test(timeout = TIMEOUT, expected = AssertionError::class)
    fun testWithModelCheckingStrategy() = ModelCheckingOptions()
        .iterations(30)
        .invocationsPerIteration(7500)
        .checkObstructionFreedom(true)
        .sequentialSpecification(sequentialSpecification.java)
        .minimizeFailedScenario(false)
        .check(this::class.java)


    @Test(timeout = TIMEOUT, expected = AssertionError::class)
    fun testWithStressStrategy() = StressOptions()
        .iterations(30)
        .invocationsPerIteration(7500)
        .sequentialSpecification(sequentialSpecification.java)
        .minimizeFailedScenario(false)
        .check(this::class.java)

}

internal class SequentialHashTableIntInt {
    private val map = HashMap<Int, Int>()

    fun put(key: Int, value: Int): Int? = map.put(key, value)

    fun get(key: Int): Int? = map.get(key)

    fun remove(key: Int): Int? = map.remove(key)
}