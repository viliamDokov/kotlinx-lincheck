/*
 * Lincheck
 *
 * Copyright (C) 2019 - 2022 JetBrains s.r.o.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 *
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-3.0.html>
 */
/*
 * Lincheck
 *
 * Copyright (C) 2019 - 2022 JetBrains s.r.o.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 *
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-3.0.html>
 */

package org.jetbrains.kotlinx.lincheck_test.strategy.eventstructure

import org.jetbrains.kotlinx.lincheck.execution.parallelResults
import org.jetbrains.kotlinx.lincheck.strategy.managed.eventstructure.consistency.MemoryModel
import org.jetbrains.lincheck.datastructures.scenario
import org.jetbrains.lincheck.util.JdkVersion
import org.jetbrains.lincheck.util.isJdk8
import org.jetbrains.lincheck.util.jdkVersion
import org.junit.Assume.assumeFalse
import org.junit.Before
import java.util.concurrent.atomic.*
import org.junit.Test
import org.junit.Ignore
import java.lang.invoke.VarHandle
import kotlin.concurrent.thread

class TrustTests {

    @Test
    fun testLastZero10() {
        class LastZero {
            val N = 10
            val buffer = AtomicIntegerArray(N + 1)

            fun reader() {
                var j = N
                while (buffer.get(j--) != 0) {}
            }

            fun writer(i: Int) {
                buffer.set(i, buffer.get(i-1) + 1)
            }
        }

        val testScenario = scenario {
            parallel {
                thread { actor(LastZero::reader) }
                thread { actor(LastZero::writer, 1) }
                thread { actor(LastZero::writer, 2) }
                thread { actor(LastZero::writer, 3) }
                thread { actor(LastZero::writer, 4) }
                thread { actor(LastZero::writer, 5) }
                thread { actor(LastZero::writer, 6) }
                thread { actor(LastZero::writer, 7) }
                thread { actor(LastZero::writer, 8) }
                thread { actor(LastZero::writer, 9) }
                thread { actor(LastZero::writer, 10) }
            }
        }

        litmusTest(LastZero::class.java, testScenario, assertSame(setOf(Unit), 3328), MemoryModel.SequentialConsistency, 100_000) { }
    }

    @Test
    fun testLastZero13() {
        class LastZero {
            val N = 13
            val buffer = AtomicIntegerArray(N + 1)

            fun reader() {
                var j = N
                while (buffer.get(j--) != 0) {}
            }

            fun writer(i: Int) {
                buffer.set(i, buffer.get(i-1) + 1)
            }
        }

        val testScenario = scenario {
            parallel {
                thread { actor(LastZero::reader) }
                thread { actor(LastZero::writer, 1) }
                thread { actor(LastZero::writer, 2) }
                thread { actor(LastZero::writer, 3) }
                thread { actor(LastZero::writer, 4) }
                thread { actor(LastZero::writer, 5) }
                thread { actor(LastZero::writer, 6) }
                thread { actor(LastZero::writer, 7) }
                thread { actor(LastZero::writer, 8) }
                thread { actor(LastZero::writer, 9) }
                thread { actor(LastZero::writer, 10) }
                thread { actor(LastZero::writer, 11) }
                thread { actor(LastZero::writer, 12) }
                thread { actor(LastZero::writer, 13) }
            }
        }

        litmusTest(LastZero::class.java, testScenario, assertSame(setOf(Unit), 32768), MemoryModel.SequentialConsistency, 100_000) {}
    }

    @Test
    fun testLastZero15() {
        class LastZero {
            val N = 15
            val buffer = AtomicIntegerArray(N + 1)

            fun reader() {
                var j = N
                while (buffer.get(j--) != 0) {}
            }

            fun writer(i: Int) {
                buffer.set(i, buffer.get(i-1) + 1)
            }
        }

        val testScenario = scenario {
            parallel {
                thread { actor(LastZero::reader) }
                thread { actor(LastZero::writer, 1) }
                thread { actor(LastZero::writer, 2) }
                thread { actor(LastZero::writer, 3) }
                thread { actor(LastZero::writer, 4) }
                thread { actor(LastZero::writer, 5) }
                thread { actor(LastZero::writer, 6) }
                thread { actor(LastZero::writer, 7) }
                thread { actor(LastZero::writer, 8) }
                thread { actor(LastZero::writer, 9) }
                thread { actor(LastZero::writer, 10) }
                thread { actor(LastZero::writer, 11) }
                thread { actor(LastZero::writer, 12) }
                thread { actor(LastZero::writer, 13) }
                thread { actor(LastZero::writer, 14) }
                thread { actor(LastZero::writer, 15) }
            }
        }

        litmusTest(LastZero::class.java, testScenario, assertSame(setOf(Unit), 147456), MemoryModel.SequentialConsistency, 200_000) {}
    }

    @Test
    fun testExpMem_7() {
        litmusTest(assertSame(setOf(1), 10_080), MemoryModel.SequentialConsistency, 20_000) {
            val x = AtomicInteger(0);
            val y = AtomicInteger(0);

            val t = thread {
                val r = x.getAndIncrement()
            }
            val t1 = thread {
                val r = y.getAndIncrement()
            }
            val t2 = thread {
                val r = y.getAndIncrement()
            }
            val t3 = thread {
                val r = y.getAndIncrement()
            }
            val t4 = thread {
                val r = y.getAndIncrement()
            }
            val t5 = thread {
                val r = y.getAndIncrement()
            }
            val t6 = thread {
                val r = y.getAndIncrement()
            }
            val t7 = thread {
                val r = y.getAndIncrement()
            }

            t1.join()
            t2.join()
            t3.join()
            t4.join()
            t5.join()
            t6.join()
            t7.join()

            val r = x.getAndIncrement()

            t.join()
            1
        }
    }

    @Test
    fun testExpMem_8() {
        litmusTest(assertSame(setOf(1), 80_640), MemoryModel.SequentialConsistency, 100_000) {
            val x = AtomicInteger(0);
            val y = AtomicInteger(0);

            val t = thread {
                val r = x.getAndIncrement()
            }
            val t1 = thread {
                val r = y.getAndIncrement()
            }
            val t2 = thread {
                val r = y.getAndIncrement()
            }
            val t3 = thread {
                val r = y.getAndIncrement()
            }
            val t4 = thread {
                val r = y.getAndIncrement()
            }
            val t5 = thread {
                val r = y.getAndIncrement()
            }
            val t6 = thread {
                val r = y.getAndIncrement()
            }
            val t7 = thread {
                val r = y.getAndIncrement()
            }
            val t8 = thread {
                val r = y.getAndIncrement()
            }

            t1.join()
            t2.join()
            t3.join()
            t4.join()
            t5.join()
            t6.join()
            t7.join()
            t8.join()

            val r = x.getAndIncrement()

            t.join()
            1
        }
    }

    @Test
    fun testExpMem_9() {
        litmusTest(assertSame(setOf(1), 725_760), MemoryModel.SequentialConsistency, 1_000_000) {
            val x = AtomicInteger(0);
            val y = AtomicInteger(0);

            val t = thread {
                val r = x.getAndIncrement()
            }
            val t1 = thread {
                val r = y.getAndIncrement()
            }
            val t2 = thread {
                val r = y.getAndIncrement()
            }
            val t3 = thread {
                val r = y.getAndIncrement()
            }
            val t4 = thread {
                val r = y.getAndIncrement()
            }
            val t5 = thread {
                val r = y.getAndIncrement()
            }
            val t6 = thread {
                val r = y.getAndIncrement()
            }
            val t7 = thread {
                val r = y.getAndIncrement()
            }
            val t8 = thread {
                val r = y.getAndIncrement()
            }
            val t9 = thread {
                val r = y.getAndIncrement()
            }

            t1.join()
            t2.join()
            t3.join()
            t4.join()
            t5.join()
            t6.join()
            t7.join()
            t8.join()
            t9.join()

            val r = x.getAndIncrement()

            t.join()
            1
        }
    }

    @Test
    fun testExpMem2_4() {
        litmusTest(assertSame(setOf(1), 3637), MemoryModel.SequentialConsistency, 1_000_000) {
            val x = AtomicInteger(0);

            val t1 = thread {
                val r = x.get()
                x.set(r + 1)
            }
            val t2 = thread {
                val r = x.get()
                x.set(r + 1)
            }
            val t3 = thread {
                val r = x.get()
                x.set(r + 1)
            }
            val t4 = thread {
                val r = x.get()
                x.set(r + 1)
            }
            val t5 = thread {
                val r = x.get()
                x.set(r + 1)
            }

            t2.join()
            t3.join()
            t4.join()
            t5.join()

            val r = x.get()
            x.set(r + 1)

            t1.join()
            1
        }
    }

    @Test
    fun testExpMem2_5() {
        litmusTest(assertSame(setOf(1), 52906), MemoryModel.SequentialConsistency, 1_000_000) {
            val x = AtomicInteger(0);

            val t1 = thread {
                val r = x.get()
                x.set(r + 1)
            }
            val t2 = thread {
                val r = x.get()
                x.set(r + 1)
            }
            val t3 = thread {
                val r = x.get()
                x.set(r + 1)
            }
            val t4 = thread {
                val r = x.get()
                x.set(r + 1)
            }
            val t5 = thread {
                val r = x.get()
                x.set(r + 1)
            }
            val t6 = thread {
                val r = x.get()
                x.set(r + 1)
            }

            t2.join()
            t3.join()
            t4.join()
            t5.join()
            t6.join()

            val r = x.get()
            x.set(r + 1)

            t1.join()
            1
        }
    }
    @Test
    fun testExpMem2_6() {
        litmusTest(assertSame(setOf(1), 916855), MemoryModel.SequentialConsistency, 1_000_000) {
            val x = AtomicInteger(0);

            val t1 = thread {
                val r = x.get()
                x.set(r + 1)
            }
            val t2 = thread {
                val r = x.get()
                x.set(r + 1)
            }
            val t3 = thread {
                val r = x.get()
                x.set(r + 1)
            }
            val t4 = thread {
                val r = x.get()
                x.set(r + 1)
            }
            val t5 = thread {
                val r = x.get()
                x.set(r + 1)
            }
            val t6 = thread {
                val r = x.get()
                x.set(r + 1)
            }
            val t7 = thread {
                val r = x.get()
                x.set(r + 1)
            }

            t2.join()
            t3.join()
            t4.join()
            t5.join()
            t6.join()
            t7.join()

            val r = x.get()
            x.set(r + 1)

            t1.join()
            1
        }
    }

}
