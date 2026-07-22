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

class JamMemoryModelTests {

    @Before
    fun setUp() {
        // currently these tests lead to hangs on JDK-21, apparently due to
        // an unrelated bug with Kotlin stdlib arrays/collection util functions instrumentation,
        // see https://github.com/JetBrains/lincheck/issues/564 for details
        assumeFalse((jdkVersion == JdkVersion.JDK_21))
    }

    // TODO: actual failing test, that can be fixed with improvements to do the model checker
    @Test
    fun testSBOpaque() {
        val expectedOutcomes: Set<Pair<Int, Int>> = setOf((0 to 0), (0 to 1), (1 to 1), (1 to 0))
        litmusTest(assertSame(expectedOutcomes), MemoryModel.JAM21) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var r0 = 0;
            var r1 = 0;
            val t0 = thread {
                x.setRelease(1)
                r0 = y.getOpaque()
            }
            val t1 = thread {
                y.setOpaque(1)
                r1 = x.getOpaque()
            }
            t0.join()
            t1.join()
            r0 to r1
        }
    }

    @Test
    fun test4SB() {
        val forbiddenOutcomes: Set<List<Int>> = setOf(listOf(0,0,0,0))
        litmusTest(assertSometimes(forbiddenOutcomes), MemoryModel.JAM21) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            val z = AtomicInteger(0)
            val a = AtomicInteger(0)
            var r0 = 0;
            var r1 = 0;
            var r2 = 0;
            var r3 = 0;
            val t0 = thread {
                x.setOpaque(1)
                r0 = y.getOpaque()
            }
            val t1 = thread {
                y.setOpaque(1)
                r1 = z.getOpaque()
            }
            val t2 = thread {
                z.setOpaque(1)
                r2 = a.getOpaque()
            }
            val t3 = thread {
                a.setOpaque(1)
                r3 = x.getOpaque()
            }
            t0.join()
            t1.join()
            t2.join()
            t3.join()
            listOf(r0, r1, r2, r3)
        }
    }


    // TODO: actual failing test, that can be fixed with improvements to do the model checker
    @Test
    fun test6SB() {
        val expectedOutcomes: Set<List<Int>> = setOf(listOf(0,0,0,0,0,0))
        litmusTest(assertSometimes(expectedOutcomes), MemoryModel.JAM21) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            val z = AtomicInteger(0)
            val a = AtomicInteger(0)
            val b = AtomicInteger(0)
            val c = AtomicInteger(0)
            var r0 = 0;
            var r1 = 0;
            var r2 = 0;
            var r3 = 0;
            var r4 = 0;
            var r5 = 0;
            val t0 = thread {
                x.setOpaque(1)
                r0 = y.getOpaque()
            }
            val t1 = thread {
                y.setOpaque(1)
                r1 = z.getOpaque()
            }
            val t2 = thread {
                z.setOpaque(1)
                r2 = a.getOpaque()
            }
            val t3 = thread {
                a.setOpaque(1)
                r3 = b.getOpaque()
            }
            val t4 = thread {
                b.setOpaque(1)
                r4 = c.getOpaque()
            }
            val t5 = thread {
                c.setOpaque(1)
                r5 = x.getOpaque()
            }
            t0.join()
            t1.join()
            t2.join()
            t3.join()
            t4.join()
            t5.join()
            listOf(r0, r1, r2, r3, r4, r5)
        }
    }

    @Test
    fun testArfna() {
        // x=1 /\ y=1, should never happen. TODO: support getting final values?
        val forbiddentOutcomes: Set<Pair<Int, Int>> = setOf((1 to 1))
        litmusTest(assertNever(forbiddentOutcomes), MemoryModel.JAM21) {
            val a = AtomicInteger(0)
            val b = AtomicInteger(0)
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var r0 = 0;
            var r1 = 0;
            val t0 = thread {
                r0 = x.getOpaque()
                if (r0 != 0) {
                    val t = a.getPlain()
                    b.setPlain(1)
                    if (t != 0) {
                        y.setOpaque(1)
                    }
                }
            }
            val t1 = thread {
                r1 = y.getOpaque()
                if (r1 != 0) {
                    if (b.getPlain() != 0) {
                        a.setPlain(1)
                        x.setOpaque(1)
                    }
                }
            }
            t0.join()
            t1.join()
            r0 to r1
        }
    }

    @Test
    fun testArfnaTransformed() {
        // x=1 /\ y=1, should never happen. TODO: support getting final values?
        val forbiddentOutcomes: Set<Pair<Int, Int>> = setOf((1 to 1))
        litmusTest(assertNever(forbiddentOutcomes), MemoryModel.JAM21) {
            val a = AtomicInteger(0)
            val b = AtomicInteger(0)
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var r0 = 0;
            var r1 = 0;
            val t0 = thread {
                r0 = x.getOpaque()
                if (r0 != 0) {
                    b.setPlain(1)
                    val t = a.getPlain()
                    if (t != 0) {
                        y.setOpaque(1)
                    }
                }
            }
            val t1 = thread {
                r1 = y.getOpaque()
                if (r1 != 0) {
                    if (b.getPlain() != 0) {
                        a.setPlain(1)
                        x.setOpaque(1)
                    }
                }
            }
            t0.join()
            t1.join()
            r0 to r1
        }
    }

    @Test
    fun testB() {
        //NOTE: This is just load buffering, I am not sure why the name is like that.
        val forbiddenOutcomes: Set<Pair<Int, Int>> = setOf((1 to 1))
        litmusTest(assertNever(forbiddenOutcomes), MemoryModel.JAM21) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var r0 = 0;
            var r1 = 0;
            val t0 = thread {
                r0 = x.getOpaque()
                y.setOpaque(1)
            }
            val t1 = thread {
                r1 = y.getOpaque()
                x.setOpaque(1)
            }
            t0.join()
            t1.join()
            r0 to r1
        }
    }

    @Test
    fun testBReorder() {
        val allowedOutcomes: Set<Pair<Int, Int>> = setOf((1 to 1))
        litmusTest(assertSometimes(allowedOutcomes), MemoryModel.JAM21) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var r0 = 0;
            var r1 = 0;
            val t0 = thread {
                y.setOpaque(1)
                r0 = x.getOpaque()
            }
            val t1 = thread {
                r1 = y.getOpaque()
                x.setOpaque(1)
            }
            t0.join()
            t1.join()
            r0 to r1
        }
    }

    // TODO: fix model checker
    @Test
    fun testC() {
        val forbiddenOutcomes: Set<Pair<Int, Int>> = setOf((1 to 1))
        litmusTest(assertNever(forbiddenOutcomes), MemoryModel.JAM21) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            val p = AtomicInteger(0)
            val q = AtomicInteger(0)
            var r0 = 0;
            var r1 = 0;
            val t0 = thread {
                r0 = x.getOpaque()
                if (r0 != 0) {
                    val t = p.getPlain()
                    q.setPlain(1)
                    if (t != 0) {
                        y.setOpaque(1)
                    }
                }
            }
            val t1 = thread {
                r1 = y.getOpaque()
                if (r1 != 0) {
                    val r2 = q.getPlain()
                    if (r2 != 0) {
                        p.setPlain(1)
                        x.setOpaque(1)
                    }
                }
            }
            t0.join()
            t1.join()
            p.get() to q.get()
        }
    }

    // TODO: fix model checker
    @Test
    fun testCReorder() {
        val forbiddenOutcomes: Set<Pair<Int, Int>> = setOf((1 to 1))
        litmusTest(assertNever(forbiddenOutcomes), MemoryModel.JAM21) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            val p = AtomicInteger(0)
            val q = AtomicInteger(0)
            var r0 = 0;
            var r1 = 0;
            val t0 = thread {
                r0 = x.getOpaque()
                if (r0 != 0) {
                    q.setPlain(1)
                    val t = p.getPlain()
                    if (t != 0) {
                        y.setOpaque(1)
                    }
                }
            }
            val t1 = thread {
                r1 = y.getOpaque()
                if (r1 != 0) {
                    val r2 = q.getPlain()
                    if (r2 != 0) {
                        p.setPlain(1)
                        x.setOpaque(1)
                    }
                }
            }
            t0.join()
            t1.join()
            p.get() to q.get()
        }
    }

    @Test
    fun testCoRWR() {
        val forbiddenOutcomes: Set<Pair<Int, Int>> = setOf((1 to 0))
        litmusTest(assertNever(forbiddenOutcomes), MemoryModel.JAM21) {
            val x = AtomicInteger(0)
            val eax = x.getOpaque()
            x.setOpaque(1)
            val ebx = x.getOpaque()
            eax to ebx
        }
    }


    @Test
    fun testCyc() {
        val forbiddenOutcomes: Set<Pair<Int, Int>> = setOf((1 to 1))
        litmusTest(assertNever(forbiddenOutcomes), MemoryModel.JAM21) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var r0 = 0;
            var r1 = 0;
            val t0 = thread {
                r0 = x.getOpaque()
                if (r0 != 0) y.setOpaque(1)
            }
            val t1 = thread {
                r1 = y.getOpaque()
                if (r1 != 0) x.setOpaque(1)
            }
            t0.join()
            t1.join()
            r0 to r1
        }
    }

    // TODO: actual failing test, that can be fixed with improvements to do the model checker
    @Test
    fun testFig1() {
        val expectedOutcomes: Set<Triple<Int, Int, Int>> = setOf(Triple(1,1,1))
        litmusTest(assertSame(expectedOutcomes, UNKNOWN), MemoryModel.JAM21) {
            val a = AtomicInteger(0)
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            val t0 = thread {
                a.setPlain(1)
                x.getOpaque()
                a.getPlain()
                y.setOpaque(1)
            }
            val t1 = thread {
                y.getOpaque()
                x.setOpaque(1)
            }
            t0.join()
            t1.join()
            Triple(a.get(), x.get(), y.get())
        }
    }

    // TODO: actual failing test, that can be fixed with improvements to do the model checker
    @Test
    fun testIriwInternal() {
        val expectedOutcomes: Set<List<Int>> = setOf(listOf(1,0,1,0))
        litmusTest(assertSometimes(expectedOutcomes), MemoryModel.JAM21) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var t0a = 0;
            var t0b = 0;
            var t1a = 0;
            var t1b = 0;
            val t0 = thread {
                x.setOpaque(1)
                t0a = x.getOpaque()
                t0b = y.getOpaque()
            }
            val t1 = thread {
                y.setOpaque(1)
                t1a = y.getOpaque()
                t1b = x.getOpaque()
            }
            t0.join()
            t1.join()
            listOf(t0a, t0b, t1a, t1b)
        }
    }

    // TODO: actual failing test, that can be fixed with improvements to do the model checker
    @Test
    fun testIRIW() {
        val expectedOutcomes: Set<List<Int>> = setOf(listOf(1,0,1,0))
        litmusTest(assertSometimes(expectedOutcomes), MemoryModel.JAM21) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var t0a = 0;
            var t0b = 0;
            var t2a = 0;
            var t2b = 0;
            val t0 = thread {
                t0a = y.getOpaque()
                t0b = x.getOpaque()
            }
            val t1 = thread {
                x.setOpaque(1)
            }
            val t2 = thread {
                t2a = x.getOpaque()
                t2b = y.getOpaque()
            }
            val t3 = thread {
                y.setOpaque(1)
            }
            t0.join()
            t1.join()
            t2.join()
            t3.join()
            listOf(t0a, t0b, t2a, t2b)
        }
    }

    // TODO: actual failing test, that can be fixed with improvements to do the model checker
    @Test
    fun testMpRelaxed() {
        val expectedOutcomes: Set<Pair<Int, Int>> = setOf((1 to 0))
        litmusTest(assertSometimes(expectedOutcomes), MemoryModel.JAM21) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var r0 = 0;
            var r1 = -1;
            val t0 = thread {
                x.setPlain(1)
                y.setOpaque(1)
            }
            val t1 = thread {
                r0 = y.getOpaque()
                if (r0 != 0) r1 = x.getPlain()
            }
            t0.join()
            t1.join()
            r0 to r1
        }
    }


    // TODO: actual failing test, that can be fixed with improvements to do the model checker
    @Test
    fun testPodrw001() {
        // NOTE: this is just Store Buffering with 3 reads
        val expectedOutcomes: Set<Triple<Int, Int, Int>> = setOf(Triple(0,0,0))
        litmusTest(assertSometimes(expectedOutcomes), MemoryModel.JAM21) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            val z = AtomicInteger(0)
            var r0 = 0;
            var r1 = 0;
            var r2 = 0;
            val t0 = thread {
                z.setOpaque(1)
                r0 = x.getOpaque()
            }
            val t1 = thread {
                x.setOpaque(1)
                r1 = y.getOpaque()
            }
            val t2 = thread {
                y.setOpaque(1)
                r2 = z.getOpaque()
            }
            t0.join()
            t1.join()
            t2.join()
            Triple(r0, r1, r2)
        }
    }

    @Test
    fun testWRR() {
        val forbiddenOutcomes: Set<Pair<Int, Int>> = setOf((1 to 0))
        litmusTest(assertNever(forbiddenOutcomes), MemoryModel.JAM21) {
            val x = AtomicInteger(0)
            var x2 = 0;
            var x3 = 0;
            val t0 = thread {
                x.setOpaque(1)
            }
            val t1 = thread {
                x2 = x.getOpaque()
                x3 = x.getOpaque()
            }
            t0.join()
            t1.join()
            x2 to x3
        }
    }

    // TODO: actual failing test, that can be fixed with improvements to do the model checker
    @Test
    fun testX001() {
        val expectedOutcomes: Set<Triple<Int, Int, Int>> = setOf(Triple(0,1,0))
        litmusTest(assertSometimes(expectedOutcomes), MemoryModel.JAM21) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var r0 = 0;
            var eax = 0;
            var ebx = 0;
            val t0 = thread {
                x.setOpaque(1)
                r0 = y.getOpaque()
            }
            val t1 = thread {
                y.setOpaque(1)
                eax = y.getOpaque()
                ebx = x.getOpaque()
            }
            t0.join()
            t1.join()
            Triple(r0, eax, ebx)
        }
    }


    // TODO: actual failing test, that can be fixed with improvements to do the model checker
    @Test
    fun testX003() {
        val expectedOutcomes: Set<Triple<Int, Int, Int>> = setOf(Triple(2,2,0))
        litmusTest(assertSometimes(expectedOutcomes), MemoryModel.JAM21) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var eax = 0;
            var ebx = 0;
            val t0 = thread {
                x.setOpaque(1)
                y.setOpaque(1)
            }
            val t1 = thread {
                y.setOpaque(2)
                eax = y.getOpaque()
                ebx = x.getOpaque()
            }
            t0.join()
            t1.join()
            Triple(y.get(), eax, ebx)
        }
    }

    // TODO: actual failing test, that can be fixed with improvements to do the model checker
    @Test
    fun testX006() {
        val expectedOutcomes: Set<Pair<Int, Int>> = setOf((2 to 0))
        litmusTest(assertSometimes(expectedOutcomes), MemoryModel.JAM21) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var r0 = 0;
            val t0 = thread {
                x.setOpaque(1)
                y.setOpaque(1)
            }
            val t1 = thread {
                y.setOpaque(2)
                r0 = x.getOpaque()
            }
            t0.join()
            t1.join()
            y.get() to r0
        }
    }

    // TODO: actual failing test, that can be fixed with improvements to do the model checker
    @Test
    fun testX86_2plus2W() {
        val expectedOutcomes: Set<Pair<Int,Int>> = setOf((2 to 2))
        litmusTest(assertSometimes(expectedOutcomes), MemoryModel.JAM21) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            val t0 = thread {
                x.setOpaque(2)
                y.setOpaque(1)
            }
            val t1 = thread {
                y.setOpaque(2)
                x.setOpaque(1)
            }
            t0.join()
            t1.join()
            x.get() to y.get()
        }
    }


    // TODO: actual failing test, that can be fixed with improvements to do the model checker
    @Test
    fun testA1() {
        val expectedOutcomes: Set<Pair<Int, Int>> = setOf((1 to 1))
        litmusTest(assertSometimes(expectedOutcomes), MemoryModel.JAM21) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var r1 = 0;
            val t0 = thread {
                y.getOpaque()
                x.setRelease(1)
            }
            val t1 = thread {
                r1 = x.getAcquire()
                if (r1 != 0) {
                    y.setPlain(1)
                }
            }
            t0.join()
            t1.join()
            x.get() to y.get()
        }
    }

    // TODO: actual failing test, that can be fixed with improvements to do the model checker
    @Test
    fun testA1Reorder() {
        val expectedOutcomes: Set<Pair<Int, Int>> = setOf((1 to 1))
        litmusTest(assertSometimes(expectedOutcomes), MemoryModel.JAM21) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var r1 = 0;
            val t0 = thread {
                x.setRelease(1)
                y.getOpaque()
            }
            val t1 = thread {
                r1 = x.getAcquire()
                if (r1 != 0) {
                    y.setPlain(1)
                }
            }
            t0.join()
            t1.join()
            x.get() to y.get()
        }
    }

    @Test
    fun testA3() {
        val expectedOutcomes: Set<Int> = setOf(1)
        litmusTest(assertSometimes(expectedOutcomes), MemoryModel.JAM21) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var r1 = 0
            val t0 = thread {
                y.setPlain(1)
                x.setRelease(1)
            }
            val t1 = thread {
                r1 = x.getAcquire()
                if (r1 != 0) {
                    y.getOpaque()
                }
            }
            t0.join()
            t1.join()
            r1
        }
    }

    @Test
    fun testA3Reorder() {
        val expectedOutcomes: Set<Int> = setOf(1)
        litmusTest(assertSometimes(expectedOutcomes), MemoryModel.JAM21) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var r1 = 0
            val t0 = thread {
                y.setPlain(1)
                x.setRelease(1)
            }
            val t1 = thread {
                y.getOpaque()
                r1 = x.getAcquire()
            }
            t0.join()
            t1.join()
            r1
        }
    }

    // TODO: actual failing test, that can be fixed with improvements to do the model checker
    @Test
    fun testIRIWPoaasLL() {
        val expectedOutcomes: Set<List<Int>> = setOf(listOf(1,0,1,0))
        litmusTest(assertSometimes(expectedOutcomes), MemoryModel.JAM21) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var r1 = 0;
            var r2 = 0;
            var r3 = 0;
            var r4 = 0;
            val t0 = thread {
                x.setRelease(1)
            }
            val t1 = thread {
                y.setRelease(1)
            }
            val t2 = thread {
                r1 = x.getAcquire()
                r2 = y.getAcquire()
            }
            val t3 = thread {
                r3 = y.getAcquire()
                r4 = x.getAcquire()
            }
            t0.join()
            t1.join()
            t2.join()
            t3.join()
            listOf(r1, r2, r3, r4)
        }
    }

    // TODO: actual failing test, that can be fixed with improvements to do the model checker
    @Test
    fun testIRIWPoapsLL() {
        val expectedOutcomes: Set<List<Int>> = setOf(listOf(1,0,1,0))
        litmusTest(assertSometimes(expectedOutcomes), MemoryModel.JAM21) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var r1 = 0;
            var r2 = 0;
            var r3 = 0;
            var r4 = 0;
            val t0 = thread {
                x.setRelease(1)
            }
            val t1 = thread {
                y.setRelease(1)
            }
            val t2 = thread {
                r1 = x.getAcquire()
                r2 = y.getOpaque()
            }
            val t3 = thread {
                r3 = y.getAcquire()
                r4 = x.getOpaque()
            }
            t0.join()
            t1.join()
            t2.join()
            t3.join()
            listOf(r1, r2, r3, r4)
        }
    }


    // TODO: actual failing test, that can be fixed with improvements to do the model checker
    @Test
    fun testLinearisation() {
        val expectedOutcomes: Set<List<Int>> = setOf(listOf(2,1,1,1,1))
        litmusTest(assertNever(expectedOutcomes), MemoryModel.JAM21) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            val w = AtomicInteger(0)
            val z = AtomicInteger(0)
            var t = 0;
            var r0 = 0;
            var r1 = 0;
            val t0 = thread {
                t = x.getAcquire() + y.getPlain()
                if (t == 2) {
                    w.setRelease(1)
                }
            }
            val t1 = thread {
                r0 = w.getOpaque()
                if (r0 != 0) {
                    z.setOpaque(1)
                }
            }
            val t2 = thread {
                r1 = z.getOpaque()
                if (r1 != 0) {
                    y.setPlain(1)
                    x.setRelease(1)
                }
            }
            t0.join()
            t1.join()
            t2.join()
            listOf(t, w.get(), x.get(), y.get(), z.get())
        }
    }

    // TODO: actual failing test, that can be fixed with improvements to do the model checker
    @Test
    fun testLinearisation2() {
        val expectedOutcomes: Set<List<Int>> = setOf(listOf(2,1,1,1,1))
        litmusTest(assertNever(expectedOutcomes), MemoryModel.JAM21) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            val w = AtomicInteger(0)
            val z = AtomicInteger(0)
            var r0 = 0;
            var r1 = 0;
            val t0 = thread {
                val tt = x.getAcquire()
                r0 = tt + y.getPlain()
                if (r0 == 2) {
                    w.setRelease(1)
                }
            }
            val t1 = thread {
                r0 = w.getOpaque()
                if (r0 != 0) {
                    z.setOpaque(1)
                }
            }
            val t2 = thread {
                r1 = z.getOpaque()
                if (r1 != 0) {
                    y.setPlain(1)
                    x.setRelease(1)
                }
            }
            t0.join()
            t1.join()
            t2.join()
            listOf(r0, w.get(), x.get(), y.get(), z.get())
        }
    }

    @Test
    fun testMpRelacq() {
        val expectedOutcomes: Set<Pair<Int, Int>> = setOf((1 to 0))
        litmusTest(assertNever(expectedOutcomes), MemoryModel.JAM21) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var r0 = 0;
            var r1 = -1;
            val t0 = thread {
                x.setPlain(1)
                y.setRelease(1)
            }
            val t1 = thread {
                r0 = y.getAcquire()
                if (r0 == 1) r1 = x.getPlain()
            }
            t0.join()
            t1.join()
            r0 to r1
        }
    }

    // NOTE: this test is interesting because C11 forbids this behavior but JAM allows it
    @Test
    fun testMpRelacqRs() {
        val expectedOutcomes: Set<Pair<Int, Int>> = setOf((2 to 0))
        litmusTest(assertSometimes(expectedOutcomes), MemoryModel.JAM21) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var r0 = 0;
            var r1 = -1;
            val t0 = thread {
                x.setPlain(1)
                y.setRelease(1)
                y.setOpaque(2)
            }
            val t1 = thread {
                r0 = y.getAcquire()
                if (r0 == 2) {
                    r1 = x.getPlain()
                }
            }
            t0.join()
            t1.join()
            r0 to r1
        }
    }

    // TODO: actual failing test, that can be fixed with improvements to do the model checker
    @Test
    fun testRoachmotel() {
        val expectedOutcomes: Set<List<Int>> = setOf(listOf(1,1,1,1))
        litmusTest(assertNever(expectedOutcomes), MemoryModel.JAM21) {
            val a = AtomicInteger(0)
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            val z = AtomicInteger(0)
            var r0 = 0;
            var r2 = 0;
            var r3 = 0;
            val t0 = thread {
                z.setRelease(1)
                a.setPlain(1)
            }
            val t1 = thread {
                r0 = x.getOpaque()
                if (r0 != 0) {
                    z.getAcquire()
                    r2 = a.getPlain()
                    if (r2 != 0) {
                        y.setOpaque(1)
                    }
                }
            }
            val t2 = thread {
                r3 = y.getOpaque()
                if (r3 != 0)  {
                    x.setOpaque(1)
                }
            }
            t0.join()
            t1.join()
            t2.join()
            listOf(a.get(), z.get(), x.get(), y.get())
        }
    }

    // TODO: actual failing test, that can be fixed with improvements to do the model checker
    @Test
    fun testRoachmotel2() {
        val expectedOutcomes: Set<List<Int>> = setOf(listOf(1,1,1,1))
        litmusTest(assertNever(expectedOutcomes), MemoryModel.JAM21) {
            val a = AtomicInteger(0)
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            val z = AtomicInteger(0)
            var r0 = 0;
            var r2 = 0;
            var r3 = 0;
            val t0 = thread {
                a.setPlain(1)
                z.setRelease(1)
            }
            val t1 = thread {
                r0 = x.getOpaque()
                if (r0 != 0) {
                    z.getAcquire()
                    r2 = a.getPlain()
                    if (r2 != 0) {
                        y.setOpaque(1)
                    }
                }
            }
            val t2 = thread {
                r3 = y.getOpaque()
                if (r3 != 0) {
                    x.setOpaque(1)
                }
            }
            t0.join()
            t1.join()
            t2.join()
            listOf(a.get(), z.get(), x.get(), y.get())
        }
    }

    // TODO: actual failing test, that can be fixed with improvements to do the model checker
    @Test
    fun testRseqWeak() {
        val expectedOutcomes: Set<Pair<Int, Int>> = setOf((3 to 1))
        litmusTest(assertSometimes(expectedOutcomes), MemoryModel.JAM21) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var r0 = 0;
            val t0 = thread {
                x.setOpaque(2)
            }
            val t1 = thread {
                y.setPlain(1)
                x.setRelease(1)
                x.setOpaque(3)
            }
            val t2 = thread {
                r0 = x.getAcquire()
                if (r0 == 3) {
                    y.getPlain()
                }
            }
            t0.join()
            t1.join()
            t2.join()
            x.get() to y.get()
        }
    }

    // TODO: actual failing test, that can be fixed with improvements to do the model checker
    @Test
    fun testRseqWeak2() {
        val expectedOutcomes: Set<Pair<Int, Int>> = setOf((3 to 1))
        litmusTest(assertSame(expectedOutcomes, UNKNOWN), MemoryModel.JAM21) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var r0 = 0;
            val t0 = thread {
                y.setPlain(1)
                x.setRelease(1)
                x.setOpaque(3)
            }
            val t1 = thread {
                r0 = x.getAcquire()
                if (r0 == 3) {
                    y.getPlain()
                }
            }
            t0.join()
            t1.join()
            x.get() to y.get()
        }
    }

    @Test
    fun testTotalco() {
        val expectedOutcomes: Set<Triple<Int, Int, Int>> = setOf(Triple(1,1,1))
        litmusTest(assertNever(expectedOutcomes), MemoryModel.JAM21) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var t0r = 0;
            var t1r = 0;
            var t2r = 0;
            val t0 = thread {
                t0r = x.getOpaque()
                x.setOpaque(1)
            }
            val t1 = thread {
                t1r = y.getAcquire()
                x.setOpaque(2)
            }
            val t2 = thread {
                t2r = x.getAcquire()
                y.setOpaque(1)
            }
            t0.join()
            t1.join()
            t2.join()
            Triple(t0r, t1r, t2r)
        }
    }

    // TODO: actual failing test, that can be fixed with improvements to do the model checker
    @Test
    fun testWWRRWWRRWsilpPoaaWsilpPoaa() {
        val expectedOutcomes: Set<List<Int>> = setOf(listOf(2,2,2,0,2,0))
        litmusTest(assertSometimes(expectedOutcomes), MemoryModel.JAM21) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var t1a = 0;
            var t1b = 0;
            var t3a = 0;
            var t3b = 0;
            val t0 = thread {
                x.setRelease(1)
                x.setRelease(2)
            }
            val t1 = thread {
                t1a = x.getAcquire()
                t1b = y.getAcquire()
            }
            val t2 = thread {
                y.setRelease(1)
                y.setRelease(2)
            }
            val t3 = thread {
                t3a = y.getAcquire()
                t3b = x.getAcquire()
            }
            t0.join()
            t1.join()
            t2.join()
            t3.join()
            listOf(x.get(), y.get(), t1a, t1b, t3a, t3b)
        }
    }


    // We probably need more tests for fences
    //TODO: fix the test style
    @Test
    fun testMpFences() {
        val expectedOutcomes: Set<Pair<Int, Int>> = setOf((1 to 0))
        litmusTest(assertNever(expectedOutcomes), MemoryModel.JAM21) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)

            var r0 = -1
            var r1 = -1

            val t0 = thread {
                x.setPlain(1)
                VarHandle.releaseFence()
                y.setOpaque(1)
            }

            val t1 = thread {
                r0 = y.getOpaque()
                VarHandle.acquireFence()
                if (r0 == 1) {
                    r1 = x.getPlain()
                }
            }

            t0.join()
            t1.join()
            r0 to r1
        }
    }

    //TODO: for all the tests below, make them lambda style
    @Test
    fun testA4() {
        class TestA4Volatile {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            fun thread0(): Int {
                x.set(1)
                return y.get()
            }
            fun thread1(): Int {
                y.set(1)
                return x.get()
            }
        }
        val testScenario = scenario {
            parallel {
                thread { actor(TestA4Volatile::thread0) }
                thread { actor(TestA4Volatile::thread1) }
            }
        }
        val expectedOutcomes: Set<Pair<Int, Int>> = setOf((0 to 0))
        litmusTest(TestA4Volatile::class.java, testScenario, assertNever(expectedOutcomes), MemoryModel.JAM21) { results ->
            val r1 = getValue<Int>(results.parallelResults[0][0]!!)
            val r2 = getValue<Int>(results.parallelResults[1][0]!!)
            r1 to r2
        }
    }

    @Test
    fun testA4Reorder() {
        class TestA4VolatileReorder {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            fun thread0(): Int {
                val r1 = y.get()
                x.set(1)
                return r1
            }
            fun thread1(): Int {
                y.set(1)
                return x.get()
            }
        }
        val testScenario = scenario {
            parallel {
                thread { actor(TestA4VolatileReorder::thread0) }
                thread { actor(TestA4VolatileReorder::thread1) }
            }
        }
        val expectedOutcomes: Set<Pair<Int, Int>> = setOf((0 to 0))
        litmusTest(TestA4VolatileReorder::class.java, testScenario, assertSometimes(expectedOutcomes), MemoryModel.JAM21) { results ->
            val r1 = getValue<Int>(results.parallelResults[0][0]!!)
            val r2 = getValue<Int>(results.parallelResults[1][0]!!)
            r1 to r2
        }
    }

    @Test
    fun test2Plus2W() {
        class Test2Plus2WVolatile {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            fun thread0(): Int {
                x.set(1)
                y.set(2)
                return y.getOpaque()
            }
            fun thread1(): Int {
                y.set(1)
                x.set(2)
                return x.getOpaque()
            }
        }
        val testScenario = scenario {
            parallel {
                thread { actor(Test2Plus2WVolatile::thread0) }
                thread { actor(Test2Plus2WVolatile::thread1) }
            }
        }
        val expectedOutcomes: Set<Pair<Int, Int>> = setOf((1 to 1))
        litmusTest(Test2Plus2WVolatile::class.java, testScenario, assertNever(expectedOutcomes), MemoryModel.JAM21) { results ->
            val r1_0 = getValue<Int>(results.parallelResults[0][0]!!)
            val r1_1 = getValue<Int>(results.parallelResults[1][0]!!)
            r1_0 to r1_1
        }
    }

    @Test
    fun testCppMemIriwRelacq() {
        class TestCppMemIriwRelacq {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            fun thread0() {
                x.set(1)
            }
            fun thread1() {
                y.set(1)
            }
            fun thread2(): Pair<Int, Int> {
                val r1 = x.getAcquire()
                val r2 = y.getAcquire()
                return r1 to r2
            }
            fun thread3(): Pair<Int, Int> {
                val r3 = y.getAcquire()
                val r4 = x.getAcquire()
                return r3 to r4
            }
        }
        val testScenario = scenario {
            parallel {
                thread { actor(TestCppMemIriwRelacq::thread0) }
                thread { actor(TestCppMemIriwRelacq::thread1) }
                thread { actor(TestCppMemIriwRelacq::thread2) }
                thread { actor(TestCppMemIriwRelacq::thread3) }
            }
        }
        val expectedOutcomes: Set<List<Int>> = setOf(listOf(1, 0, 1, 0))
        litmusTest(TestCppMemIriwRelacq::class.java, testScenario, assertSometimes(expectedOutcomes), MemoryModel.JAM21) { results ->
            val t2 = getValue<Pair<Int, Int>>(results.parallelResults[2][0]!!)
            val t3 = getValue<Pair<Int, Int>>(results.parallelResults[3][0]!!)
            listOf(t2.first, t2.second, t3.first, t3.second)
        }
    }

    //TODO: There are is no possible write of value 2 to y. I assume this is some thing air behaviour.
    @Test
    fun testCppMemScAtomics() {
        class TestCppMemScAtomics {
            val x = AtomicInteger(2)
            val y = AtomicInteger(0)
            fun thread0() {
                x.set(3)
            }
            fun thread1() {
                if (x.get() == 3) {
                    y.setPlain(1)
                }
            }
            fun post(): Int {
                return y.get()
            }
        }
        val testScenario = scenario {
            parallel {
                thread { actor(TestCppMemScAtomics::thread0) }
                thread { actor(TestCppMemScAtomics::thread1) }
            }
            post { actor(TestCppMemScAtomics::post) }
        }
        val expectedOutcomes: Set<Int> = setOf(2)
        litmusTest(TestCppMemScAtomics::class.java, testScenario, assertNever(expectedOutcomes), MemoryModel.JAM21) { results ->
            getValue<Int>(results.postResults[0]!!)
        }
    }

    @Test
    fun testFig6() {
        class TestFig6 {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            fun thread0() {
                x.setOpaque(1)
                x.set(2)
                y.set(1)
            }
            fun thread1() {
                x.setOpaque(3)
                y.set(2)
            }
            fun thread2(): Int {
                y.set(3)
                return x.get()
            }
            fun thread3(): List<Int> {
                val s1 = x.getOpaque()
                val s2 = x.getOpaque()
                val s3 = x.getOpaque()
                val t1 = y.getOpaque()
                val t2 = y.getOpaque()
                val t3 = y.getOpaque()
                return listOf(s1, s2, s3, t1, t2, t3)
            }
        }
        val testScenario = scenario {
            parallel {
                thread { actor(TestFig6::thread0) }
                thread { actor(TestFig6::thread1) }
                thread { actor(TestFig6::thread2) }
                thread { actor(TestFig6::thread3) }
            }
        }
        // RC11 allows this, but C11 does not
        val expectedOutcomes: Set<List<Int>> = setOf(listOf(1, 1, 1, 2, 2, 3, 3))
        litmusTest(TestFig6::class.java, testScenario, assertSometimes(expectedOutcomes), MemoryModel.JAM21, 10_000) { results ->
            val r = getValue<Int>(results.parallelResults[2][0]!!)
            val t3 = getValue<List<Int>>(results.parallelResults[3][0]!!)
            listOf(r, t3[0], t3[1], t3[2], t3[3], t3[4], t3[5])
        }
    }

    @Test
    fun testFig6Translated() {
        class TestFig6Translated {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            fun thread0() {
                x.setOpaque(1)
                x.set(2)
                y.set(1)
            }
            fun thread1() {
                x.set(3)
                y.set(2)
            }
            fun thread2(): Int {
                y.set(3)
                return x.get()
            }
            fun thread3(): List<Int> {
                val s1 = x.getOpaque()
                val s2 = x.getOpaque()
                val s3 = x.getOpaque()
                val t1 = y.getOpaque()
                val t2 = y.getOpaque()
                val t3 = y.getOpaque()
                return listOf(s1, s2, s3, t1, t2, t3)
            }
        }
        val testScenario = scenario {
            parallel {
                thread { actor(TestFig6Translated::thread0) }
                thread { actor(TestFig6Translated::thread1) }
                thread { actor(TestFig6Translated::thread2) }
                thread { actor(TestFig6Translated::thread3) }
            }
        }
        val expectedOutcomes: Set<List<Int>> = setOf(listOf(1, 1, 1, 2, 2, 3, 3))
        litmusTest(TestFig6Translated::class.java, testScenario, assertSometimes(expectedOutcomes), MemoryModel.JAM21, 10_000) { results ->
            val r = getValue<Int>(results.parallelResults[2][0]!!)
            val t3 = getValue<List<Int>>(results.parallelResults[3][0]!!)
            listOf(r, t3[0], t3[1], t3[2], t3[3], t3[4], t3[5])
        }
    }

    @Test
    fun testIriwAcqSc() {
        class TestIriwAcqSc {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            fun thread0() {
                x.set(1)
            }
            fun thread1() {
                y.set(1)
            }
            fun thread2(): Pair<Int, Int> {
                val r1 = x.getAcquire()
                val r2 = y.get()
                return r1 to r2
            }
            fun thread3(): Pair<Int, Int> {
                val r3 = y.getAcquire()
                val r4 = x.get()
                return r3 to r4
            }
        }
        val testScenario = scenario {
            parallel {
                thread { actor(TestIriwAcqSc::thread0) }
                thread { actor(TestIriwAcqSc::thread1) }
                thread { actor(TestIriwAcqSc::thread2) }
                thread { actor(TestIriwAcqSc::thread3) }
            }
        }
        val expectedOutcomes: Set<List<Int>> = setOf(listOf(1, 0, 1, 0))
        litmusTest(TestIriwAcqSc::class.java, testScenario, assertSometimes(expectedOutcomes), MemoryModel.JAM21) { results ->
            val t2 = getValue<Pair<Int, Int>>(results.parallelResults[2][0]!!)
            val t3 = getValue<Pair<Int, Int>>(results.parallelResults[3][0]!!)
            listOf(t2.first, t2.second, t3.first, t3.second)
        }
    }

    @Test
    fun testIriwScRlxAcq() {
        class TestIriwScRlxAcq {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            fun thread0() {
                x.set(1)
                x.setOpaque(2)
            }
            fun thread1() {
                y.set(1)
                y.setOpaque(2)
            }
            fun thread2(): Pair<Int, Int> {
                val r1 = x.getAcquire()
                val r2 = y.getAcquire()
                return r1 to r2
            }
            fun thread3(): Pair<Int, Int> {
                val r3 = y.getAcquire()
                val r4 = x.getAcquire()
                return r3 to r4
            }
        }
        val testScenario = scenario {
            parallel {
                thread { actor(TestIriwScRlxAcq::thread0) }
                thread { actor(TestIriwScRlxAcq::thread1) }
                thread { actor(TestIriwScRlxAcq::thread2) }
                thread { actor(TestIriwScRlxAcq::thread3) }
            }
        }
        val expectedOutcomes: Set<List<Int>> = setOf(listOf(2, 0, 2, 0))
        litmusTest(TestIriwScRlxAcq::class.java, testScenario, assertSometimes(expectedOutcomes), MemoryModel.JAM21) { results ->
            val t2 = getValue<Pair<Int, Int>>(results.parallelResults[2][0]!!)
            val t3 = getValue<Pair<Int, Int>>(results.parallelResults[3][0]!!)
            listOf(t2.first, t2.second, t3.first, t3.second)
        }
    }

    @Test
    fun testIriwSc() {
        class TestIriwSc {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            fun thread0() {
                x.set(1)
            }
            fun thread1() {
                y.set(1)
            }
            fun thread2(): Pair<Int, Int> {
                val r1 = x.get()
                val r2 = y.get()
                return r1 to r2
            }
            fun thread3(): Pair<Int, Int> {
                val r3 = y.get()
                val r4 = x.get()
                return r3 to r4
            }
        }
        val testScenario = scenario {
            parallel {
                thread { actor(TestIriwSc::thread0) }
                thread { actor(TestIriwSc::thread1) }
                thread { actor(TestIriwSc::thread2) }
                thread { actor(TestIriwSc::thread3) }
            }
        }
        val expectedOutcomes: Set<List<Int>> = setOf(listOf(1, 0, 1, 0))
        litmusTest(TestIriwSc::class.java, testScenario, assertNever(expectedOutcomes), MemoryModel.JAM21) { results ->
            val t2 = getValue<Pair<Int, Int>>(results.parallelResults[2][0]!!)
            val t3 = getValue<Pair<Int, Int>>(results.parallelResults[3][0]!!)
            listOf(t2.first, t2.second, t3.first, t3.second)
        }
    }

    @Test
    fun testMpSc() {
        class TestMpSc {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            fun thread0() {
                x.setPlain(1)
                y.set(1)
            }
            fun thread1(): Pair<Int, Int> {
                val r0 = y.get()
                var r1 = -1
                if (r0 == 1) {
                    r1 = x.getPlain()
                }
                return r0 to r1
            }
        }
        val testScenario = scenario {
            parallel {
                thread { actor(TestMpSc::thread0) }
                thread { actor(TestMpSc::thread1) }
            }
        }
        val expectedOutcomes: Set<Pair<Int, Int>> = setOf((1 to 0))
        litmusTest(TestMpSc::class.java, testScenario, assertNever(expectedOutcomes), MemoryModel.JAM21) { results ->
            getValue<Pair<Int, Int>>(results.parallelResults[1][0]!!)
        }
    }

    @Test
    fun testVolatileNonSc4() {
        class TestRaNonLocal {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            fun thread0(): Int {
                y.set(2)
                return x.get()
            }
            fun thread1() {
                x.set(1)
            }
            fun thread2(): Int {
                val r0 = x.get()
                y.set(1)
                return r0
            }
            fun thread3(): Pair<Int, Int> {
                val r0 = y.get()
                val r1 = y.get()
                return r0 to r1
            }
        }
        val testScenario = scenario {
            parallel {
                thread { actor(TestRaNonLocal::thread0) }
                thread { actor(TestRaNonLocal::thread1) }
                thread { actor(TestRaNonLocal::thread2) }
                thread { actor(TestRaNonLocal::thread3) }
            }
        }
        val expectedOutcomes: Set<List<Int>> = setOf(listOf(0, 1, 1, 2))
        litmusTest(TestRaNonLocal::class.java, testScenario, assertNever(expectedOutcomes), MemoryModel.JAM21) { results ->
            val t0 = getValue<Int>(results.parallelResults[0][0]!!)
            val t2 = getValue<Int>(results.parallelResults[2][0]!!)
            val t3 = getValue<Pair<Int, Int>>(results.parallelResults[3][0]!!)
            listOf(t0, t2, t3.first, t3.second)
        }
    }

    @Test
    fun testVolatileNonSc5() {
        class TestReadWriteSc {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            val z = AtomicInteger(0)
            fun thread0(): Int {
                x.set(1)
                return y.get()
            }
            fun thread1() {
                y.set(1)
            }
            fun thread2(): Int {
                val r1 = y.get()
                z.set(1)
                return r1
            }
            fun thread3(): Int {
                z.set(2)
                return x.get()
            }
            fun thread4(): Pair<Int, Int> {
                val r1 = z.get()
                val r2 = z.get()
                return r1 to r2
            }
        }
        val testScenario = scenario {
            parallel {
                thread { actor(TestReadWriteSc::thread0) }
                thread { actor(TestReadWriteSc::thread1) }
                thread { actor(TestReadWriteSc::thread2) }
                thread { actor(TestReadWriteSc::thread3) }
                thread { actor(TestReadWriteSc::thread4) }
            }
        }
        val expectedOutcomes: Set<List<Int>> = setOf(listOf(0, 1, 0, 1, 2))
        litmusTest(TestReadWriteSc::class.java, testScenario, assertNever(expectedOutcomes), MemoryModel.JAM21) { results ->
            val t0 = getValue<Int>(results.parallelResults[0][0]!!)
            val t2 = getValue<Int>(results.parallelResults[2][0]!!)
            val t3 = getValue<Int>(results.parallelResults[3][0]!!)
            val t4 = getValue<Pair<Int, Int>>(results.parallelResults[4][0]!!)
            listOf(t0, t2, t3, t4.first, t4.second)
        }
    }

    @Test
    fun testZ6U() {
        class TestZ6U {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            fun thread0() {
                x.set(1)
                y.setRelease(1)
            }
            fun thread1(): Pair<Int, Int> {
                val r1 = y.getAndAdd(1)
                val r2 = y.getOpaque()
                return r1 to r2
            }
            fun thread2(): Int {
                y.set(3)
                return x.get()
            }
        }
        val testScenario = scenario {
            parallel {
                thread { actor(TestZ6U::thread0) }
                thread { actor(TestZ6U::thread1) }
                thread { actor(TestZ6U::thread2) }
            }
        }
        val expectedOutcomes: Set<List<Int>> = setOf(listOf(1, 3, 0))
        litmusTest(TestZ6U::class.java, testScenario, assertNever(expectedOutcomes), MemoryModel.JAM21) { results ->
            val t1 = getValue<Pair<Int, Int>>(results.parallelResults[1][0]!!)
            val t2 = getValue<Int>(results.parallelResults[2][0]!!)
            listOf(t1.first, t1.second, t2)
        }
    }

    @Test
    fun testA3v2() {
        class TestA3v2 {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            fun thread0() {
                y.setPlain(1)
                x.setRelease(1)
            }
            fun thread1(): Int {
                val r0 = x.compareAndExchangeAcquire(1, 2)
                var r1 = -1
                if (r0 == 1) {
                    r1 = y.getPlain()
                }
                return r1
            }
        }
        val testScenario = scenario {
            parallel {
                thread { actor(TestA3v2::thread0) }
                thread { actor(TestA3v2::thread1) }
            }
        }
        val expectedOutcomes: Set<Int> = setOf(1)
        litmusTest(TestA3v2::class.java, testScenario, assertSometimes(expectedOutcomes), MemoryModel.JAM21) { results ->
            getValue<Int>(results.parallelResults[1][0]!!)
        }
    }

    @Test
    fun testCp() {
        class TestCp {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            val p = AtomicInteger(0)
            val q = AtomicInteger(0)
            fun thread0(): Int {
                val r0 = x.getOpaque()
                if (r0 != 0) {
                    val t = p.compareAndExchangeAcquire(1, 2)
                    q.setPlain(1)
                    if (t == 1) {
                        y.setOpaque(1)
                    }
                }
                return r0
            }
            fun thread1(): Int {
                val r1 = y.getOpaque()
                if (r1 != 0) {
                    val r2 = q.getPlain()
                    if (r2 != 0) {
                        p.setPlain(1)
                        x.setOpaque(1)
                    }
                }
                return r1
            }
            fun post(): Pair<Int, Int> {
                return p.get() to q.get()
            }
        }
        val testScenario = scenario {
            parallel {
                thread { actor(TestCp::thread0) }
                thread { actor(TestCp::thread1) }
            }
            post { actor(TestCp::post) }
        }
        val expectedOutcomes: Set<Pair<Int, Int>> = setOf((1 to 1))
        litmusTest(TestCp::class.java, testScenario, assertNever(expectedOutcomes), MemoryModel.JAM21) { results ->
            getValue<Pair<Int, Int>>(results.postResults[0]!!)
        }
    }

    @Test
    fun testCpReorder() {
        class TestCpReorder {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            val p = AtomicInteger(0)
            val q = AtomicInteger(0)
            fun thread0(): Int {
                val r0 = x.getOpaque()
                if (r0 != 0) {
                    q.setPlain(1)
                    val t = p.compareAndExchangeAcquire(1, 2)
                    if (t == 1) {
                        y.setOpaque(1)
                    }
                }
                return r0
            }
            fun thread1(): Int {
                val r1 = y.getOpaque()
                if (r1 != 0) {
                    val r2 = q.getPlain()
                    if (r2 != 0) {
                        p.setPlain(1)
                        x.setOpaque(1)
                    }
                }
                return r1
            }
            fun post(): Pair<Int, Int> {
                return p.get() to q.get()
            }
        }
        val testScenario = scenario {
            parallel {
                thread { actor(TestCpReorder::thread0) }
                thread { actor(TestCpReorder::thread1) }
            }
            post { actor(TestCpReorder::post) }
        }
        val expectedOutcomes: Set<Pair<Int, Int>> = setOf((1 to 1))
        litmusTest(TestCpReorder::class.java, testScenario, assertNever(expectedOutcomes), MemoryModel.JAM21) { results ->
            getValue<Pair<Int, Int>>(results.postResults[0]!!)
        }
    }


    @Test
    fun testCpq() {
        class TestCpq {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            val p = AtomicInteger(0)
            val q = AtomicInteger(0)
            fun thread0(): Int {
                val r0 = x.getOpaque()
                if (r0 != 0) {
                    val t = p.compareAndExchangeAcquire(1, 2)
                    val u = q.compareAndExchangeAcquire(0, 1)
                    if (t == 1) {
                        y.setOpaque(1)
                    }
                }
                return r0
            }
            fun thread1(): Int {
                val r1 = y.getOpaque()
                if (r1 != 0) {
                    val r2 = q.getPlain()
                    if (r2 != 0) {
                        p.setPlain(1)
                        x.setOpaque(1)
                    }
                }
                return r1
            }
            fun post(): Pair<Int, Int> {
                return p.get() to q.get()
            }
        }
        val testScenario = scenario {
            parallel {
                thread { actor(TestCpq::thread0) }
                thread { actor(TestCpq::thread1) }
            }
            post { actor(TestCpq::post) }
        }
        val expectedOutcomes: Set<Pair<Int, Int>> = setOf((1 to 1))
        litmusTest(TestCpq::class.java, testScenario, assertNever(expectedOutcomes), MemoryModel.JAM21) { results ->
            getValue<Pair<Int, Int>>(results.postResults[0]!!)
        }
    }

    @Test
    fun testCpqReorder() {
        class TestCpqReorder {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            val p = AtomicInteger(0)
            val q = AtomicInteger(0)
            fun thread0(): Int {
                val r0 = x.getOpaque()
                if (r0 != 0) {
                    val u = q.compareAndExchangeAcquire(0, 1)
                    val t = p.compareAndExchangeAcquire(1, 2)
                    if (t == 1) {
                        y.setOpaque(1)
                    }
                }
                return r0
            }
            fun thread1(): Int {
                val r1 = y.getOpaque()
                if (r1 != 0) {
                    val r2 = q.getPlain()
                    if (r2 != 0) {
                        p.setPlain(1)
                        x.setOpaque(1)
                    }
                }
                return r1
            }
            fun post(): Pair<Int, Int> {
                return p.get() to q.get()
            }
        }
        val testScenario = scenario {
            parallel {
                thread { actor(TestCpqReorder::thread0) }
                thread { actor(TestCpqReorder::thread1) }
            }
            post { actor(TestCpqReorder::post) }
        }
        val expectedOutcomes: Set<Pair<Int, Int>> = setOf((1 to 1))
        litmusTest(TestCpqReorder::class.java, testScenario, assertNever(expectedOutcomes), MemoryModel.JAM21) { results ->
            getValue<Pair<Int, Int>>(results.postResults[0]!!)
        }
    }

    @Test
    fun testCq() {
        class TestCq {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            val p = AtomicInteger(0)
            val q = AtomicInteger(0)
            fun thread0(): Int {
                val r0 = x.getOpaque()
                if (r0 != 0) {
                    val t = p.getPlain()
                    val u = q.compareAndExchangeAcquire(0, 1)
                    if (t != 0) {
                        y.setOpaque(1)
                    }
                }
                return r0
            }
            fun thread1(): Int {
                val r1 = y.getOpaque()
                if (r1 != 0) {
                    val r2 = q.getPlain()
                    if (r2 != 0) {
                        p.setPlain(1)
                        x.setOpaque(1)
                    }
                }
                return r1
            }
            fun post(): Pair<Int, Int> {
                return p.get() to q.get()
            }
        }
        val testScenario = scenario {
            parallel {
                thread { actor(TestCq::thread0) }
                thread { actor(TestCq::thread1) }
            }
            post { actor(TestCq::post) }
        }
        val expectedOutcomes: Set<Pair<Int, Int>> = setOf((1 to 1))
        litmusTest(TestCq::class.java, testScenario, assertNever(expectedOutcomes), MemoryModel.JAM21) { results ->
            getValue<Pair<Int, Int>>(results.postResults[0]!!)
        }
    }

    @Test
    fun testCqReorder() {
        class TestCqReorder {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            val p = AtomicInteger(0)
            val q = AtomicInteger(0)
            fun thread0(): Int {
                val r0 = x.getOpaque()
                if (r0 != 0) {
                    val u = q.compareAndExchangeAcquire(0, 1)
                    val t = p.getPlain()
                    if (t != 0) {
                        y.setOpaque(1)
                    }
                }
                return r0
            }
            fun thread1(): Int {
                val r1 = y.getOpaque()
                if (r1 != 0) {
                    val r2 = q.getPlain()
                    if (r2 != 0) {
                        p.setPlain(1)
                        x.setOpaque(1)
                    }
                }
                return r1
            }
            fun post(): Pair<Int, Int> {
                return p.get() to q.get()
            }
        }
        val testScenario = scenario {
            parallel {
                thread { actor(TestCqReorder::thread0) }
                thread { actor(TestCqReorder::thread1) }
            }
            post { actor(TestCqReorder::post) }
        }
        val expectedOutcomes: Set<Pair<Int, Int>> = setOf((1 to 1))
        litmusTest(TestCqReorder::class.java, testScenario, assertNever(expectedOutcomes), MemoryModel.JAM21) { results ->
            getValue<Pair<Int, Int>>(results.postResults[0]!!)
        }
    }

    @Ignore
    @Test
    fun testPPOCA() {
        class TestPPOCA {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            val z = AtomicInteger(0)
            fun thread0() {
                x.setOpaque(1)
                VarHandle.fullFence()
                y.setOpaque(1)
            }
            fun thread1(): Triple<Int, Int, Int> {
                val x0 = y.getOpaque()
                var x4 = 0
                var x6 = 0
                if (x0 == 0) {
                    z.setOpaque(1)
                    x4 = z.getOpaque()
                    x6 = x.getOpaque()
                }
                return Triple(x0, x4, x6)
            }
        }
        val testScenario = scenario {
            parallel {
                thread { actor(TestPPOCA::thread0) }
                thread { actor(TestPPOCA::thread1) }
            }
        }
        val expectedOutcomes: Set<Triple<Int, Int, Int>> = setOf(Triple(1, 1, 0))
        litmusTest(TestPPOCA::class.java, testScenario, assertSometimes(expectedOutcomes), MemoryModel.JAM21) { results ->
            getValue<Triple<Int, Int, Int>>(results.parallelResults[1][0]!!)
        }
    }

    @Ignore
    @Test
    fun testSBMfence() {
        class TestSBMfence {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            fun thread0(): Int {
                x.setOpaque(1)
                VarHandle.fullFence()
                return y.getOpaque()
            }
            fun thread1(): Int {
                y.setOpaque(1)
                VarHandle.fullFence()
                return x.getOpaque()
            }
        }
        val testScenario = scenario {
            parallel {
                thread { actor(TestSBMfence::thread0) }
                thread { actor(TestSBMfence::thread1) }
            }
        }
        val expectedOutcomes: Set<Pair<Int, Int>> = setOf((0 to 0))
        litmusTest(TestSBMfence::class.java, testScenario, assertNever(expectedOutcomes), MemoryModel.JAM21) { results ->
            val eax0 = getValue<Int>(results.parallelResults[0][0]!!)
            val eax1 = getValue<Int>(results.parallelResults[1][0]!!)
            eax0 to eax1
        }
    }

    @Ignore
    @Test
    fun testWRWC() {
        class TestWRWC {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            val z = AtomicInteger(0)
            fun thread0() {
                x.setOpaque(1)
                z.setRelease(1)
            }
            fun thread1(): Pair<Int, Int> {
                val r1 = z.getAcquire()
                VarHandle.fullFence()
                val r2 = y.getOpaque()
                return r1 to r2
            }
            fun thread2(): Int {
                y.setOpaque(1)
                VarHandle.fullFence()
                return x.getOpaque()
            }
        }
        val testScenario = scenario {
            parallel {
                thread { actor(TestWRWC::thread0) }
                thread { actor(TestWRWC::thread1) }
                thread { actor(TestWRWC::thread2) }
            }
        }
        val expectedOutcomes: Set<Triple<Int, Int, Int>> = setOf(Triple(1, 0, 0))
        litmusTest(TestWRWC::class.java, testScenario, assertNever(expectedOutcomes), MemoryModel.JAM21) { results ->
            val t1 = getValue<Pair<Int, Int>>(results.parallelResults[1][0]!!)
            val t2 = getValue<Int>(results.parallelResults[2][0]!!)
            Triple(t1.first, t1.second, t2)
        }
    }

    @Ignore
    @Test
    fun testRWCSyncs() {
        class TestRWCSyncs {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            fun thread0() {
                x.setOpaque(1)
            }
            fun thread1(): Pair<Int, Int> {
                val r1 = x.getOpaque()
                VarHandle.fullFence() // TODO: full fence here.
                val r2 = y.getOpaque()
                return r1 to r2
            }
            fun thread2(): Int {
                y.setOpaque(1)
                VarHandle.fullFence()
                return x.getOpaque()
            }
        }
        val testScenario = scenario {
            parallel {
                thread { actor(TestRWCSyncs::thread0) }
                thread { actor(TestRWCSyncs::thread1) }
                thread { actor(TestRWCSyncs::thread2) }
            }
        }
        val forbiddenOutcomes: Set<Triple<Int, Int, Int>> = setOf(Triple(1,0,0))
        litmusTest(TestRWCSyncs::class.java, testScenario, assertNever(forbiddenOutcomes), MemoryModel.JAM21) { results ->
            val t1 = getValue<Pair<Int, Int>>(results.parallelResults[1][0]!!)
            val r1 = getValue<Int>(results.parallelResults[2][0]!!)
            Triple(t1.first, t1.second, r1)
        }
    }

    @Test
    fun testX002() {
        class TestX002 {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            fun thread0(): Int {
                x.setOpaque(1)
                VarHandle.fullFence()
                return y.getOpaque()
            }
            fun thread1(): Pair<Int, Int> {
                y.setOpaque(1)
                val eax = y.getOpaque()
                val ebx = x.getOpaque()
                return eax to ebx
            }
        }
        val testScenario = scenario {
            parallel {
                thread { actor(TestX002::thread0) }
                thread { actor(TestX002::thread1) }
            }
        }
        val expectedOutcomes: Set<List<Int>> = setOf(listOf(0, 1, 0))
        litmusTest(TestX002::class.java, testScenario, assertSometimes(expectedOutcomes), MemoryModel.JAM21) { results ->
            val eax0 = getValue<Int>(results.parallelResults[0][0]!!)
            val t1 = getValue<Pair<Int, Int>>(results.parallelResults[1][0]!!)
            listOf(eax0, t1.first, t1.second)
        }
    }

    @Test
    fun testX005() {
        class TestX005 {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            fun thread0(): Int {
                x.setOpaque(1)
                VarHandle.fullFence()
                return y.getOpaque()
            }
            fun thread1(): Int {
                y.setOpaque(1)
                return x.getOpaque()
            }
        }
        val testScenario = scenario {
            parallel {
                thread { actor(TestX005::thread0) }
                thread { actor(TestX005::thread1) }
            }
        }
        val expectedOutcomes: Set<Pair<Int, Int>> = setOf((0 to 0))
        litmusTest(TestX005::class.java, testScenario, assertSometimes(expectedOutcomes), MemoryModel.JAM21) { results ->
            val eax0 = getValue<Int>(results.parallelResults[0][0]!!)
            val eax1 = getValue<Int>(results.parallelResults[1][0]!!)
            eax0 to eax1
        }
    }


    @Test
    fun testSbPlusRfis() {
        class TestClass {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            fun thread0(): Pair<Int, Int> {
                x.setPlain(1)
                val r0 = x.get()
                val r1 = y.get()
                return r0 to r1
            }
            fun thread1(): Pair<Int, Int> {
                y.setPlain(1)
                val r0 = y.get()
                val r1 = x.get()
                return r0 to r1
            }
        }
        val testScenario = scenario {
            parallel {
                thread { actor(TestClass::thread0) }
                thread { actor(TestClass::thread1) }
            }
        }
        val expectedOutcomes: Set<List<Int>> = setOf(listOf(1,0,1,0))
        //NOTE: this behaviour is forbidden by JAM21, but actually happens in practice as mentioned in the JMT paper
        // https://arxiv.org/pdf/2604.15978, section 7, scenario 3
        litmusTest(TestClass::class.java, testScenario, assertSometimes(expectedOutcomes), MemoryModel.JAM21) { results ->
            val t0 = getValue<Pair<Int,Int>>(results.parallelResults[0][0]!!)
            val t1 = getValue<Pair<Int,Int>>(results.parallelResults[1][0]!!)
            listOf(t0.first, t0.second, t1.first, t1.second)
        }
    }
}
