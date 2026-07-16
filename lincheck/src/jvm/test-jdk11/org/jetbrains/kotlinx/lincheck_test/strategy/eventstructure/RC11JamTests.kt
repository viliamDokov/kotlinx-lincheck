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

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine
import org.jetbrains.kotlinx.lincheck.Actor
import org.jetbrains.kotlinx.lincheck.execution.parallelResults
import org.jetbrains.kotlinx.lincheck.strategy.managed.eventstructure.consistency.MemoryModel
import org.jetbrains.kotlinx.lincheck.util.CancelledResult
import org.jetbrains.kotlinx.lincheck.util.SuspendedResult
import org.jetbrains.lincheck.datastructures.ModelCheckingOptions
import org.jetbrains.lincheck.datastructures.Operation
import org.jetbrains.lincheck.datastructures.scenario
import org.jetbrains.lincheck.util.JdkVersion
import org.jetbrains.lincheck.util.UnsafeHolder
import org.jetbrains.lincheck.util.isJdk8
import org.jetbrains.lincheck.util.jdkVersion
import org.junit.Assume.assumeFalse
import org.junit.Before
import java.util.concurrent.atomic.*
import org.junit.Test
import org.junit.Ignore
import org.junit.Rule
import org.junit.rules.TestName
import java.lang.invoke.MethodHandles
import java.lang.invoke.VarHandle
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ConcurrentSkipListMap
import java.util.concurrent.locks.LockSupport.park
import java.util.concurrent.locks.LockSupport.unpark
import kotlin.concurrent.thread
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.jvm.javaMethod

class RC11JamTests {


    // TODO: actual failing test, that can be fixed with improvements to do the model checker
    @Test
    fun testSBOpaque() {
        val expectedOutcomes: Set<Pair<Int, Int>> = setOf((0 to 0), (0 to 1), (1 to 1), (1 to 0))
        litmusTest(assertSame(expectedOutcomes), MemoryModel.ReleaseAcquire) {
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
        val forbiddenOutcomes: Set<List<Int>> = setOf(listOf(0, 0, 0, 0))
        litmusTest(assertSometimes(forbiddenOutcomes), MemoryModel.ReleaseAcquire) {
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
        val expectedOutcomes: Set<List<Int>> = setOf(listOf(0, 0, 0, 0, 0, 0))
        litmusTest(assertSometimes(expectedOutcomes), MemoryModel.ReleaseAcquire) {
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
        litmusTest(assertNever(forbiddentOutcomes), MemoryModel.ReleaseAcquire) {
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
        litmusTest(assertNever(forbiddentOutcomes), MemoryModel.ReleaseAcquire) {
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
        litmusTest(assertNever(forbiddenOutcomes), MemoryModel.ReleaseAcquire) {
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
        litmusTest(assertSometimes(allowedOutcomes), MemoryModel.ReleaseAcquire) {
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
        litmusTest(assertNever(forbiddenOutcomes), MemoryModel.ReleaseAcquire) {
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
        litmusTest(assertNever(forbiddenOutcomes), MemoryModel.ReleaseAcquire) {
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
        litmusTest(assertNever(forbiddenOutcomes), MemoryModel.ReleaseAcquire) {
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
        litmusTest(assertNever(forbiddenOutcomes), MemoryModel.ReleaseAcquire) {
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
        val expectedOutcomes: Set<Triple<Int, Int, Int>> = setOf(Triple(1, 1, 1))
        litmusTest(assertSame(expectedOutcomes, UNKNOWN), MemoryModel.ReleaseAcquire) {
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
        val expectedOutcomes: Set<List<Int>> = setOf(listOf(1, 0, 1, 0))
        litmusTest(assertSometimes(expectedOutcomes), MemoryModel.ReleaseAcquire) {
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
        val expectedOutcomes: Set<List<Int>> = setOf(listOf(1, 0, 1, 0))
        litmusTest(assertSometimes(expectedOutcomes), MemoryModel.ReleaseAcquire) {
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
        litmusTest(assertSometimes(expectedOutcomes), MemoryModel.ReleaseAcquire) {
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
        val expectedOutcomes: Set<Triple<Int, Int, Int>> = setOf(Triple(0, 0, 0))
        litmusTest(assertSometimes(expectedOutcomes), MemoryModel.ReleaseAcquire) {
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
        litmusTest(assertNever(forbiddenOutcomes), MemoryModel.ReleaseAcquire) {
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
        val expectedOutcomes: Set<Triple<Int, Int, Int>> = setOf(Triple(0, 1, 0))
        litmusTest(assertSometimes(expectedOutcomes), MemoryModel.ReleaseAcquire) {
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
        val expectedOutcomes: Set<Triple<Int, Int, Int>> = setOf(Triple(2, 2, 0))
        litmusTest(assertSometimes(expectedOutcomes), MemoryModel.ReleaseAcquire) {
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
        litmusTest(assertSometimes(expectedOutcomes), MemoryModel.ReleaseAcquire) {
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
        val expectedOutcomes: Set<Pair<Int, Int>> = setOf((2 to 2))
        litmusTest(assertSometimes(expectedOutcomes), MemoryModel.ReleaseAcquire) {
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
        litmusTest(assertSometimes(expectedOutcomes), MemoryModel.ReleaseAcquire) {
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
        litmusTest(assertSometimes(expectedOutcomes), MemoryModel.ReleaseAcquire) {
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
        litmusTest(assertSometimes(expectedOutcomes), MemoryModel.ReleaseAcquire) {
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
        litmusTest(assertSometimes(expectedOutcomes), MemoryModel.ReleaseAcquire) {
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
        val expectedOutcomes: Set<List<Int>> = setOf(listOf(1, 0, 1, 0))
        litmusTest(assertSometimes(expectedOutcomes), MemoryModel.ReleaseAcquire) {
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
        val expectedOutcomes: Set<List<Int>> = setOf(listOf(1, 0, 1, 0))
        litmusTest(assertSometimes(expectedOutcomes), MemoryModel.ReleaseAcquire) {
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
        val expectedOutcomes: Set<List<Int>> = setOf(listOf(2, 1, 1, 1, 1))
        litmusTest(assertNever(expectedOutcomes), MemoryModel.ReleaseAcquire) {
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
        val expectedOutcomes: Set<List<Int>> = setOf(listOf(2, 1, 1, 1, 1))
        litmusTest(assertNever(expectedOutcomes), MemoryModel.ReleaseAcquire) {
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
        litmusTest(assertNever(expectedOutcomes), MemoryModel.ReleaseAcquire) {
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
        litmusTest(assertSometimes(expectedOutcomes), MemoryModel.ReleaseAcquire) {
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
        val expectedOutcomes: Set<List<Int>> = setOf(listOf(1, 1, 1, 1))
        litmusTest(assertNever(expectedOutcomes), MemoryModel.ReleaseAcquire) {
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
    fun testRoachmotel2() {
        val expectedOutcomes: Set<List<Int>> = setOf(listOf(1, 1, 1, 1))
        litmusTest(assertNever(expectedOutcomes), MemoryModel.ReleaseAcquire) {
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
        litmusTest(assertSometimes(expectedOutcomes), MemoryModel.ReleaseAcquire) {
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
        litmusTest(assertSame(expectedOutcomes, UNKNOWN), MemoryModel.ReleaseAcquire) {
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
        val expectedOutcomes: Set<Triple<Int, Int, Int>> = setOf(Triple(1, 1, 1))
        litmusTest(assertNever(expectedOutcomes), MemoryModel.ReleaseAcquire) {
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
        val expectedOutcomes: Set<List<Int>> = setOf(listOf(2, 2, 2, 0, 2, 0))
        litmusTest(assertSometimes(expectedOutcomes), MemoryModel.ReleaseAcquire) {
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

    @get:Rule
    val testName = TestName()

    class PlainPrimitiveVariable {
        private var variable: Int = 0

        fun write(value: Int) {
            variable = value
        }

        fun read(): Int {
            return variable
        }
    }

    @Test
    fun testPlainPrimitiveAccesses() {

        val write = PlainPrimitiveVariable::write
        val read = PlainPrimitiveVariable::read
        val testScenario = scenario {
            parallel {
                thread {
                    actor(write, 1)
                }
                thread {
                    actor(read)
                }
                thread {
                    actor(write, 2)
                }
            }
        }
        // TODO: when we will implement various access modes,
        //   we should probably report races on plain variables as errors (or warnings at least)
        val outcomes: Set<Int> = setOf(0, 1, 2)
        litmusTest(PlainPrimitiveVariable::class.java, testScenario, outcomes) { results ->
            getValue<Int>(results.parallelResults[1][0]!!)
        }
    }

    class PlainReferenceVariable {
        private var variable: String = ""

        fun write(value: String) {
            variable = value
        }

        fun read(): String {
            return variable
        }
    }

    @Test
    fun testPlainReferenceAccesses() {

        val write = PlainReferenceVariable::write
        val read = PlainReferenceVariable::read
        val testScenario = scenario {
            parallel {
                thread {
                    actor(write, "a")
                }
                thread {
                    actor(read)
                }
                thread {
                    actor(write, "b")
                }
            }
        }
        val outcomes: Set<String> = setOf("", "a", "b")
        litmusTest(PlainReferenceVariable::class.java, testScenario, outcomes) { results ->
            getValue<String>(results.parallelResults[1][0]!!)
        }
    }

    class PrimitiveArray {
        private val array = IntArray(8)

        fun write(index: Int, value: Int) {
            array[index] = value
        }

        fun read(index: Int): Int {
            return array[index]
        }
    }

    @Test
    fun testPrimitiveArrayAccesses() {

        val write = PrimitiveArray::write
        val read = PrimitiveArray::read
        val index = 2
        val testScenario = scenario {
            parallel {
                thread {
                    actor(write, index, 1)
                }
                thread {
                    actor(read, index)
                }
                thread {
                    actor(write, index, 2)
                }
            }
        }
        val outcomes: Set<Int> = setOf(0, 1, 2)
        litmusTest(PrimitiveArray::class.java, testScenario, outcomes) { results ->
            getValue<Int>(results.parallelResults[1][0]!!)
        }
    }

    class ReferenceArray {
        private val array = Array<String>(8) { "" }

        fun write(index: Int, value: String) {
            array[index] = value
        }

        fun read(index: Int): String {
            return array[index]
        }
    }

    @Test
    fun testReferenceArrayAccesses() {
        val write = ReferenceArray::write
        val read = ReferenceArray::read
        val index = 2
        val testScenario = scenario {
            parallel {
                thread {
                    actor(write, index, "a")
                }
                thread {
                    actor(read, index)
                }
                thread {
                    actor(write, index, "b")
                }
            }
        }
        val outcomes: Set<String> = setOf("", "a", "b")
        litmusTest(ReferenceArray::class.java, testScenario, outcomes) { results ->
            getValue<String>(results.parallelResults[1][0]!!)
        }
    }

    class AtomicVariable {
        // TODO: In the future we would likely want to switch to atomicfu primitives.
        //   However, atomicfu currently does not support various access modes that we intend to test here.
        private val variable = AtomicInteger()

        fun write(value: Int) {
            variable.set(value)
        }

        fun read(): Int {
            return variable.get()
        }

        fun compareAndSet(expected: Int, desired: Int): Boolean {
            return variable.compareAndSet(expected, desired)
        }

        fun addAndGet(delta: Int): Int {
            return variable.addAndGet(delta)
        }

        fun getAndAdd(delta: Int): Int {
            return variable.getAndAdd(delta)
        }
    }

    @Test
    fun testAtomicAccesses() {
        val read = AtomicVariable::read
        val write = AtomicVariable::write
        val testScenario = scenario {
            parallel {
                thread {
                    actor(write, 1)
                }
                thread {
                    actor(read)
                }
                thread {
                    actor(write, 2)
                }
            }
        }
        val outcomes: Set<Int> = setOf(0, 1, 2)
        litmusTest(AtomicVariable::class.java, testScenario, outcomes) { results ->
            getValue<Int>(results.parallelResults[1][0]!!)
        }
    }

    @Test
    fun testCompareAndSet() {
        val read = AtomicVariable::read
        val compareAndSet = AtomicVariable::compareAndSet
        val testScenario = scenario {
            parallel {
                thread {
                    actor(compareAndSet, 0, 1)
                }
                thread {
                    actor(compareAndSet, 0, 1)
                }
            }
            post {
                actor(read)
            }
        }
        val outcomes: Set<Triple<Boolean, Boolean, Int>> = setOf(
            Triple(true, false, 1),
            Triple(false, true, 1)
        )
        litmusTest(AtomicVariable::class.java, testScenario, outcomes) { results ->
            val r1 = getValue<Boolean>(results.parallelResults[0][0]!!)
            val r2 = getValue<Boolean>(results.parallelResults[1][0]!!)
            val r3 = getValue<Int>(results.postResults[0]!!)
            Triple(r1, r2, r3)
        }
    }

    @Test
    fun testGetAndAdd() {
        val read = AtomicVariable::read
        val getAndAdd = AtomicVariable::getAndAdd
        val testScenario = scenario {
            parallel {
                thread {
                    actor(getAndAdd, 1)
                }
                thread {
                    actor(getAndAdd, 1)
                }
            }
            post {
                actor(read)
            }
        }
        val outcomes: Set<Triple<Int, Int, Int>> = setOf(
            Triple(0, 1, 2),
            Triple(1, 0, 2)
        )
        litmusTest(AtomicVariable::class.java, testScenario, outcomes) { results ->
            val r1 = getValue<Int>(results.parallelResults[0][0]!!)
            val r2 = getValue<Int>(results.parallelResults[1][0]!!)
            val r3 = getValue<Int>(results.postResults[0]!!)
            Triple(r1, r2, r3)
        }
    }

    @Test
    fun testAddAndGet() {
        val read = AtomicVariable::read
        val addAndGet = AtomicVariable::addAndGet
        val testScenario = scenario {
            parallel {
                thread {
                    actor(addAndGet, 1)
                }
                thread {
                    actor(addAndGet, 1)
                }
            }
            post {
                actor(read)
            }
        }
        val outcomes: Set<Triple<Int, Int, Int>> = setOf(
            Triple(1, 2, 2),
            Triple(2, 1, 2)
        )
        litmusTest(AtomicVariable::class.java, testScenario, outcomes) { results ->
            val r1 = getValue<Int>(results.parallelResults[0][0]!!)
            val r2 = getValue<Int>(results.parallelResults[1][0]!!)
            val r3 = getValue<Int>(results.postResults[0]!!)
            Triple(r1, r2, r3)
        }
    }

    class GlobalAtomicVariable {

        companion object {
            // TODO: In the future we would likely want to switch to atomicfu primitives.
            //   However, atomicfu currently does not support various access modes that we intend to test here.
            private val globalVariable = AtomicInteger(0)
        }

        fun write(value: Int) {
            globalVariable.set(value)
        }

        fun read(): Int {
            return globalVariable.get()
        }

        fun compareAndSet(expected: Int, desired: Int): Boolean {
            return globalVariable.compareAndSet(expected, desired)
        }

        fun addAndGet(delta: Int): Int {
            return globalVariable.addAndGet(delta)
        }

        fun getAndAdd(delta: Int): Int {
            return globalVariable.getAndAdd(delta)
        }
    }

    @Test
    fun testGlobalAtomicAccesses() {
        val read = GlobalAtomicVariable::read
        val write = GlobalAtomicVariable::write
        val testScenario = scenario {
            parallel {
                thread {
                    actor(write, 1)
                }
                thread {
                    actor(read)
                }
                thread {
                    actor(write, 2)
                }
            }
        }
        val outcomes: Set<Int> = setOf(0, 1, 2)
        litmusTest(GlobalAtomicVariable::class.java, testScenario, outcomes) { results ->
            getValue<Int>(results.parallelResults[1][0]!!)
        }
    }

    // TODO: handle IntRef (var variables accessed from multiple threads)

    class VolatileReferenceVariable {
        @Volatile
        private var variable: String? = null

        companion object {
            private val updater =
                AtomicReferenceFieldUpdater.newUpdater(
                    VolatileReferenceVariable::class.java,
                    String::class.java,
                    "variable"
                )

            private val U = UnsafeHolder.UNSAFE

            @Suppress("DEPRECATION")
            private val offset = U.objectFieldOffset(VolatileReferenceVariable::class.java.getDeclaredField("variable"))

        }

        fun read(): String? {
            return variable
        }

        fun afuRead(): String? {
            return updater.get(this)
        }

        fun unsafeRead(): String? {
            return U.getObject(this, offset) as String?
        }

        fun write(value: String?) {
            variable = value
        }

        fun afuWrite(value: String?) {
            updater.set(this, value)
        }

        fun unsafeWrite(value: String?) {
            U.putObject(this, offset, value)
        }

        fun afuCompareAndSet(expected: String?, desired: String?): Boolean {
            return updater.compareAndSet(this, expected, desired)
        }

        fun unsafeCompareAndSet(expected: String?, desired: String?): Boolean {
            return U.compareAndSwapObject(this, offset, expected, desired)
        }

    }

    @Test
    fun testAtomicFieldUpdaterAccesses() {
        val read = VolatileReferenceVariable::afuRead
        val write = VolatileReferenceVariable::afuWrite
        val testScenario = scenario {
            parallel {
                thread {
                    actor(write, "a")
                }
                thread {
                    actor(read)
                }
                thread {
                    actor(write, "b")
                }
            }
        }
        val outcomes: Set<String?> = setOf(null, "a", "b")
        litmusTest(VolatileReferenceVariable::class.java, testScenario, outcomes) { results ->
            getValue(results.parallelResults[1][0]!!)
        }
    }

    @Test
    fun testUnsafeAccesses() {
        val read = VolatileReferenceVariable::unsafeRead
        val write = VolatileReferenceVariable::unsafeWrite
        val testScenario = scenario {
            parallel {
                thread {
                    actor(write, "a")
                }
                thread {
                    actor(read)
                }
                thread {
                    actor(write, "b")
                }
            }
        }
        val outcomes: Set<String?> = setOf(null, "a", "b")
        litmusTest(VolatileReferenceVariable::class.java, testScenario, outcomes) { results ->
            getValue(results.parallelResults[1][0]!!)
        }
    }

    @Test
    fun testAtomicFieldUpdaterCompareAndSet() {
        val read = VolatileReferenceVariable::afuRead
        val compareAndSet = VolatileReferenceVariable::afuCompareAndSet
        val testScenario = scenario {
            parallel {
                thread {
                    actor(compareAndSet, null, "a")
                }
                thread {
                    actor(compareAndSet, null, "a")
                }
            }
            post {
                actor(read)
            }
        }
        val outcomes: Set<Triple<Boolean, Boolean, String?>> = setOf(
            Triple(true, false, "a"),
            Triple(false, true, "a")
        )
        litmusTest(VolatileReferenceVariable::class.java, testScenario, outcomes) { results ->
            val r1 = getValue<Boolean>(results.parallelResults[0][0]!!)
            val r2 = getValue<Boolean>(results.parallelResults[1][0]!!)
            val r3 = getValue<String?>(results.postResults[0]!!)
            Triple(r1, r2, r3)
        }
    }

    @Test
    fun testUnsafeCompareAndSet() {
        val read = VolatileReferenceVariable::unsafeRead
        val compareAndSet = VolatileReferenceVariable::unsafeCompareAndSet
        val testScenario = scenario {
            parallel {
                thread {
                    actor(compareAndSet, null, "a")
                }
                thread {
                    actor(compareAndSet, null, "a")
                }
            }
            post {
                actor(read)
            }
        }
        val outcomes: Set<Triple<Boolean, Boolean, String?>> = setOf(
            Triple(true, false, "a"),
            Triple(false, true, "a")
        )
        litmusTest(VolatileReferenceVariable::class.java, testScenario, outcomes) { results ->
            val r1 = getValue<Boolean>(results.parallelResults[0][0]!!)
            val r2 = getValue<Boolean>(results.parallelResults[1][0]!!)
            val r3 = getValue<String?>(results.postResults[0]!!)
            Triple(r1, r2, r3)
        }
    }

    class UnsafeArrays {
        private var byteArray: ByteArray = ByteArray(8)
        private var shortArray: ShortArray = ShortArray(8)
        private var intArray: IntArray = IntArray(8)
        private var longArray: LongArray = LongArray(8)
        private var referenceArray: Array<String> = Array<String>(8) { "" }

        companion object {
            private val U = UnsafeHolder.UNSAFE

            private val byteArrayOffset = U.arrayBaseOffset(ByteArray::class.java)
            private val shortArrayOffset = U.arrayBaseOffset(ShortArray::class.java)
            private val intArrayOffset = U.arrayBaseOffset(IntArray::class.java)
            private val longArrayOffset = U.arrayBaseOffset(LongArray::class.java)
            private val referenceArrayOffset = U.arrayBaseOffset(Array<String>::class.java)

            private val byteIndexScale = U.arrayIndexScale(ByteArray::class.java)
            private val shortIndexScale = U.arrayIndexScale(ShortArray::class.java)
            private val intIndexScale = U.arrayIndexScale(IntArray::class.java)
            private val longIndexScale = U.arrayIndexScale(LongArray::class.java)
            private val referenceIndexScale = U.arrayIndexScale(Array<String>::class.java)

        }

        fun writeByte(index: Int, value: Byte) {
            U.putByte(byteArray, (index.toLong() * byteIndexScale) + byteArrayOffset, value)
        }

        fun writeShort(index: Int, value: Short) {
            U.putShort(shortArray, (index.toLong() * shortIndexScale) + shortArrayOffset, value)
        }

        fun writeInt(index: Int, value: Int) {
            U.putInt(intArray, (index.toLong() * intIndexScale) + intArrayOffset, value)
        }

        fun writeLong(index: Int, value: Long) {
            U.putLong(longArray, (index.toLong() * longIndexScale) + longArrayOffset, value)
        }

        fun writeReference(index: Int, value: String) {
            U.putObject(referenceArray, (index.toLong() * referenceIndexScale) + referenceArrayOffset, value)
        }

        fun readByte(index: Int): Byte {
            return U.getByte(byteArray, (index.toLong() * byteIndexScale) + byteArrayOffset)
        }

        fun readShort(index: Int): Short {
            return U.getShort(shortArray, (index.toLong() * shortIndexScale) + shortArrayOffset)
        }

        fun readInt(index: Int): Int {
            return U.getInt(intArray, (index.toLong() * intIndexScale) + intArrayOffset)
        }

        fun readLong(index: Int): Long {
            return U.getLong(longArray, (index.toLong() * longIndexScale) + longArrayOffset)
        }

        fun readReference(index: Int): String {
            return U.getObject(referenceArray, (index.toLong() * referenceIndexScale) + referenceArrayOffset) as String
        }

    }

    @Test
    fun testUnsafeByteArrayAccesses() {
        val read = UnsafeArrays::readByte
        val write = UnsafeArrays::writeByte
        val index = 2
        val testScenario = scenario {
            parallel {
                thread {
                    actor(write, index, 1.toByte())
                }
                thread {
                    actor(read, index)
                }
                thread {
                    actor(write, index, 2.toByte())
                }
            }
        }
        val outcomes: Set<Byte> = setOf(0, 1, 2)
        litmusTest(UnsafeArrays::class.java, testScenario, outcomes) { results ->
            getValue<Byte>(results.parallelResults[1][0]!!)
        }
    }

    @Test
    fun testUnsafeShortArrayAccesses() {
        val read = UnsafeArrays::readShort
        val write = UnsafeArrays::writeShort
        val index = 2
        val testScenario = scenario {
            parallel {
                thread {
                    actor(write, index, 1.toShort())
                }
                thread {
                    actor(read, index)
                }
                thread {
                    actor(write, index, 2.toShort())
                }
            }
        }
        val outcomes: Set<Short> = setOf(0, 1, 2)
        litmusTest(UnsafeArrays::class.java, testScenario, outcomes) { results ->
            getValue<Short>(results.parallelResults[1][0]!!)
        }
    }

    @Test
    fun testUnsafeIntArrayAccesses() {
        val read = UnsafeArrays::readInt
        val write = UnsafeArrays::writeInt
        val index = 2
        val testScenario = scenario {
            parallel {
                thread {
                    actor(write, index, 1)
                }
                thread {
                    actor(read, index)
                }
                thread {
                    actor(write, index, 2)
                }
            }
        }
        val outcomes: Set<Int> = setOf(0, 1, 2)
        litmusTest(UnsafeArrays::class.java, testScenario, outcomes) { results ->
            getValue<Int>(results.parallelResults[1][0]!!)
        }
    }

    @Test
    fun testUnsafeLongArrayAccesses() {
        val read = UnsafeArrays::readLong
        val write = UnsafeArrays::writeLong
        val index = 2
        val testScenario = scenario {
            parallel {
                thread {
                    actor(write, index, 1L)
                }
                thread {
                    actor(read, index)
                }
                thread {
                    actor(write, index, 2L)
                }
            }
        }
        val outcomes: Set<Long> = setOf(0, 1, 2)
        litmusTest(UnsafeArrays::class.java, testScenario, outcomes) { results ->
            getValue<Long>(results.parallelResults[1][0]!!)
        }
    }

    @Test
    fun testUnsafeReferenceArrayAccesses() {
        val read = UnsafeArrays::readReference
        val write = UnsafeArrays::writeReference
        val index = 2
        val testScenario = scenario {
            parallel {
                thread {
                    actor(write, index, "a")
                }
                thread {
                    actor(read, index)
                }
                thread {
                    actor(write, index, "b")
                }
            }
        }
        val outcomes: Set<String> = setOf("", "a", "b")
        litmusTest(UnsafeArrays::class.java, testScenario, outcomes) { results ->
            getValue<String>(results.parallelResults[1][0]!!)
        }
    }

    class SynchronizedVariable {

        private var variable: Int = 0

        @Synchronized
        fun write(value: Int) {
            variable = value
        }

        @Synchronized
        fun read(): Int {
            return variable
        }

        @Synchronized
        fun waitAndRead(): Int {
            // TODO: handle spurious wake-ups?
            (this as Object).wait()
            return variable
        }

        @Synchronized
        fun writeAndNotify(value: Int) {
            variable = value
            (this as Object).notify()
        }

        @Synchronized
        fun compareAndSet(expected: Int, desired: Int): Boolean {
            return if (variable == expected) {
                variable = desired
                true
            } else false
        }

        @Synchronized
        fun addAndGet(delta: Int): Int {
            variable += delta
            return variable
        }

        @Synchronized
        fun getAndAdd(delta: Int): Int {
            val value = variable
            variable += delta
            return value
        }

    }


    class ParkLatchedVariable {

        private var variable: Int = 0

        @Volatile
        private var parkedThread: Thread? = null

        @Volatile
        private var delivered: Boolean = false

        fun parkAndRead(): Int? {
            // TODO: handle spurious wake-ups?
            parkedThread = Thread.currentThread()
            return if (delivered) {
                park()
                variable
            } else null
        }

        fun writeAndUnpark(value: Int) {
            variable = value
            val thread = parkedThread
            if (thread != null)
                delivered = true
            unpark(thread)
        }

    }

    @Test
    fun testParking() {
        val writeAndUnpark = ParkLatchedVariable::writeAndUnpark
        val parkAndRead = ParkLatchedVariable::parkAndRead
        val testScenario = scenario {
            parallel {
                thread {
                    actor(writeAndUnpark, 1)
                }
                thread {
                    actor(parkAndRead)
                }
            }
        }
        val outcomes = setOf(null, 1)
        litmusTest(ParkLatchedVariable::class.java, testScenario, assertSame(outcomes, executionCount = 3)) { results ->
            getValue<Int?>(results.parallelResults[1][0]!!)
        }
    }

    class CoroutineWrapper {

        val continuation = AtomicReference<CancellableContinuation<Int>>()
        var resumedOrCancelled = AtomicBoolean(false)

        // TODO: Dubious fix was done here
        @Operation(promptCancellation = true)
        suspend fun suspend(): Int {
            return suspendCancellableCoroutine<Int> { continuation ->
                this.continuation.set(continuation)
            }
        }

        @InternalCoroutinesApi
        @Operation
        fun resume(value: Int): Boolean {
            this.continuation.get()?.let {
                if (resumedOrCancelled.compareAndSet(false, true)) {
                    val token = it.tryResume(value)
                    if (token != null)
                        it.completeResume(token)
                    return (token != null)
                }
            }
            return false
        }

        @Operation
        fun cancel(): Boolean {
            this.continuation.get()?.let {
                if (resumedOrCancelled.compareAndSet(false, true)) {
                    it.cancel(CancelledOperationException)
                    return true
                }
            }
            return false
        }
    }

    internal object CancelledOperationException : Exception()

    @Ignore
    @InternalCoroutinesApi
    @Test(timeout = TIMEOUT)
    fun testResume() {
        val suspend = CoroutineWrapper::suspend
        val resume = CoroutineWrapper::resume
        val testScenario = scenario {
            parallel {
                thread {
                    actor(suspend)
                }
                thread {
                    actor(resume, 1)
                }
            }
        }
        val outcomes = setOf(
            (SuspendedResult to false),
            (1 to true)
        )
        litmusTest(
            CoroutineWrapper::class.java,
            testScenario,
            assertSame(outcomes, executionCount = UNKNOWN)
        ) { results ->
            val r = getValueSuspended(results.parallelResults[0][0]!!)
            val b = getValue<Boolean>(results.parallelResults[1][0]!!)
            (r to b)
        }
    }

    @Ignore
    @InternalCoroutinesApi
    @Test(timeout = TIMEOUT)
    fun testCancel() {
        val suspendActor = Actor(
            method = CoroutineWrapper::suspend.javaMethod!!,
            arguments = listOf(),
            cancelOnSuspension = false
        )
        val cancel = CoroutineWrapper::cancel
        val testScenario = scenario {
            parallel {
                thread {
                    add(suspendActor)
                }
                thread {
                    actor(cancel)
                }
            }
        }
        val outcomes = setOf(
            (SuspendedResult to false),
            (CancelledOperationException to true)
        )
        litmusTest(
            CoroutineWrapper::class.java,
            testScenario,
            assertSame(outcomes, executionCount = UNKNOWN)
        ) { results ->
            val r = getValueSuspended(results.parallelResults[0][0]!!)
            val b = getValue<Boolean>(results.parallelResults[1][0]!!)
            (r to b)
        }
    }

    @Ignore
    @InternalCoroutinesApi
    @Test(timeout = TIMEOUT)
    fun testLincheckCancellation() {
        val suspendActor = Actor(
            method = CoroutineWrapper::suspend.javaMethod!!,
            arguments = listOf(),
            cancelOnSuspension = true
        )
        val resume = CoroutineWrapper::resume
        val testScenario = scenario {
            parallel {
                thread {
                    add(suspendActor)
                }
                thread {
                    actor(resume, 1)
                }
            }
        }
        val outcomes = setOf(
            (CancelledResult to false),
            (1 to true)
        )
        litmusTest(
            CoroutineWrapper::class.java,
            testScenario,
            assertSame(outcomes, executionCount = UNKNOWN)
        ) { results ->
            val r = getValueSuspended(results.parallelResults[0][0]!!)
            val b = getValue<Boolean>(results.parallelResults[1][0]!!)
            (r to b)
        }
    }

    @Ignore
    @InternalCoroutinesApi
    @Test(timeout = TIMEOUT)
    fun testLincheckPromptCancellation() {
        val suspendActor = Actor(
            method = CoroutineWrapper::suspend.javaMethod!!,
            arguments = listOf(),
            cancelOnSuspension = true,
            promptCancellation = true,
        )
        val resume = CoroutineWrapper::resume
        val testScenario = scenario {
            parallel {
                thread {
                    add(suspendActor)
                }
                thread {
                    actor(resume, 1)
                }
            }
        }
        val outcomes = setOf(
            (CancelledResult to false),
            (CancelledResult to true),
            // (1 to true),
        )
        litmusTest(
            CoroutineWrapper::class.java,
            testScenario,
            assertSame(outcomes, executionCount = UNKNOWN)
        ) { results ->
            val r = getValueSuspended(results.parallelResults[0][0]!!)
            val b = getValue<Boolean>(results.parallelResults[1][0]!!)
            (r to b)
        }
    }

    @Ignore
    @InternalCoroutinesApi
    @Test(timeout = TIMEOUT)
    fun testResumeCancel() {
        val suspendActor = Actor(
            method = CoroutineWrapper::suspend.javaMethod!!,
            arguments = listOf(),
            cancelOnSuspension = false
        )
        val resume = CoroutineWrapper::resume
        val cancel = CoroutineWrapper::cancel
        val testScenario = scenario {
            parallel {
                thread {
                    add(suspendActor)
                }
                thread {
                    actor(resume, 1)
                }
                thread {
                    actor(cancel)
                }
            }
        }
        val outcomes = setOf(
            Triple(SuspendedResult, false, false),
            Triple(1, true, false),
            Triple(CancelledOperationException, false, true)
        )
        litmusTest(
            CoroutineWrapper::class.java,
            testScenario,
            assertSame(outcomes, executionCount = UNKNOWN)
        ) { results ->
            val r = getValueSuspended(results.parallelResults[0][0]!!)
            val b1 = getValue<Boolean>(results.parallelResults[1][0]!!)
            val b2 = getValue<Boolean>(results.parallelResults[2][0]!!)
            Triple(r, b1, b2)
        }
    }

    @Ignore
    @InternalCoroutinesApi
    @Test(timeout = TIMEOUT)
    fun test1Resume2Suspend() {
        val suspend = CoroutineWrapper::suspend
        val resume = CoroutineWrapper::resume
        val testScenario = scenario {
            parallel {
                thread {
                    actor(suspend)
                }
                thread {
                    actor(suspend)
                }
                thread {
                    actor(resume, 1)
                }
            }
        }
        val outcomes = setOf(
            Triple(SuspendedResult, SuspendedResult, false),
            Triple(SuspendedResult, 1, true),
            Triple(1, SuspendedResult, true),
        )
        litmusTest(
            CoroutineWrapper::class.java,
            testScenario,
            assertSame(outcomes, executionCount = UNKNOWN)
        ) { results ->
            val r1 = getValueSuspended(results.parallelResults[0][0]!!)
            val r2 = getValueSuspended(results.parallelResults[1][0]!!)
            val b = getValue<Boolean>(results.parallelResults[2][0]!!)
            Triple(r1, r2, b)
        }
    }

    @Ignore
    @InternalCoroutinesApi
    @Test(timeout = TIMEOUT)
    fun test2Resume1Suspend() {
        val suspend = CoroutineWrapper::suspend
        val resume = CoroutineWrapper::resume
        val testScenario = scenario {
            parallel {
                thread {
                    actor(suspend)
                }
                thread {
                    actor(resume, 1)
                }
                thread {
                    actor(resume, 2)
                }
            }
        }
        val outcomes = setOf(
            Triple(SuspendedResult, false, false),
            Triple(1, true, false),
            Triple(2, false, true),
        )
        litmusTest(
            CoroutineWrapper::class.java,
            testScenario,
            assertSame(outcomes, executionCount = UNKNOWN)
        ) { results ->
            val r = getValueSuspended(results.parallelResults[0][0]!!)
            val b1 = getValue<Boolean>(results.parallelResults[1][0]!!)
            val b2 = getValue<Boolean>(results.parallelResults[2][0]!!)
            Triple(r, b1, b2)
        }
    }

    @Test
    fun testObjectIdsAreNotBrokenOnBackwardRevisit() {
        // This test is specifically made to make break during backward revisit of allocated objects
        // We had a bug in the object tracker where the replay order of the events affected the ObjectIDs of the replayed objects
        // In the initial invocation, we first assigned objectID 1 to Bar(1) and 2 to Bar(2)
        // In the next invocation we do a backward revisit from x.set(1) to x.get()
        // This means that we first replay Bar(2) and the x.set(1) and go to actually run x.get() and Bar(1).
        // During replay this meant that we wrongly gave Bar(2) the objectID of 1 (instead of the original 2 value)
        class Bar(a: Int) {
            val b = a
            override fun toString(): String {
                return "BAR:($b)"
            }
        }

        class Foo {
            var x = AtomicInteger(0)
            @Volatile
            var y = Bar(0)
            fun one() {
                x.get() == 0
                y = Bar(1)
                val x = y
            }

            fun two() {
                y = Bar(2)
                x.set(1)
            }
        }

        val testScenario = scenario {
            parallel {
                thread {
                    actor(Foo::one)
                }
                thread {
                    actor(Foo::two)
                }
            }
        }
        val outcomes: Set<Unit> = setOf(Unit)
        litmusTest(Foo::class.java, testScenario, assertSame(outcomes, UNKNOWN)) { results ->
            Unit
        }
    }

    @Test
    fun testStringsIdsAreNotBrokenOnBackwardRevisit() {
        // Same thing as the test above, but with strings, which work a bit differently
        class Foo {
            var x = AtomicInteger(0)
            @Volatile
            var y = ""

            fun one() {
                x.get() == 0
                y = "a"
            }

            fun two() {
                y = "b"
                x.set(1)
            }

        }

        val testScenario = scenario {
            parallel {
                thread {
                    actor(Foo::one)
                }
                thread {
                    actor(Foo::two)
                }
            }
        }
        val outcomes: Set<Unit> = setOf(Unit)
        litmusTest(Foo::class.java, testScenario, assertSame(outcomes, UNKNOWN)) { results ->
            Unit
        }
    }

    @Test
    fun testExternalObjectIdsAreNotBrokenOnGarbageCollection() {
        // This test tries to force the GC to collect the external Baz(0), which is left unused after each testInvocation
        // The ObjectTracker used to rely on a weak reference to the GC'd object, which would cause trouble
        class Baz(a: Int) {
            @Volatile
            var b = a
            override fun toString(): String {
                return "BAZ:($b)"
            }
        }

        class Foo {
            // The Baz(0) here is an external object
            // Since it is the intial value of a field of the test class
            @Volatile
            var y = Baz(0)
            fun one(): Int {
                val res = y.b
                return 0
            }

            fun two() {
                y.b = 0
                y.b = 1
                y.b = 2
            }
        }

        val testScenario = scenario {
            parallel {
                thread {
                    actor(Foo::one)
                }
                thread {
                    actor(Foo::two)
                }
            }
        }
        val outcomes: Set<Int> = setOf(0)
        litmusTest(Foo::class.java, testScenario, assertSame(outcomes, UNKNOWN)) { results ->
            val b1 = getValue<Int>(results.parallelResults[0][0]!!)
            System.gc() // Kindly suggest the GC to do its thing. Should make the test fail more consistently.
            return@litmusTest b1
        }
    }

    @Test
    fun testObjectIdsAreNotBrokenOnReplays() {
        // This test tries various nesting levels to see if we can break the replay order of the object tracker
        // Kind of a throw stuff at the wall and see what sticks approach
        class Bar(a: AtomicInteger) {
            @Volatile
            var b = a
            override fun toString(): String {
                return "BAR:($b)"
            }
        }

        class Baz(a: Bar) {
            @Volatile
            var b = a
            override fun toString(): String {
                return "BAZ:($b)"
            }
        }

        class Foo {
            @Volatile
            var y = Baz(Bar(AtomicInteger(0)))
            fun one(): Int {
                y.b = Bar(AtomicInteger(1))
                val res = y.b.b.get()
                return res
            }

            fun two() {
                y.b = Bar(AtomicInteger(2))
                y.b.b = AtomicInteger(3)
                y.b.b.set(4)
            }
        }

        val testScenario = scenario {
            parallel {
                thread {
                    actor(Foo::one)
                }
                thread {
                    actor(Foo::two)
                }
            }
        }
        val outcomes: Set<Int> = setOf(1, 2, 3, 4)
        litmusTest(Foo::class.java, testScenario, assertSame(outcomes, UNKNOWN)) { results ->
            val b1 = getValue<Int>(results.parallelResults[0][0]!!)
            return@litmusTest b1
        }
    }

    @Test
    fun testLambdaAllocationIsTracked() {
        // This test ensures that we properly keep track of the allocation of lambdas
        // The eventStructureObjectTracker needs to track lambda's as "NEW" object and not
        // as external ones, as that would cause the lambdas to have unstable object ids between invocations
        // or cause them to hold on to "stale" closures from previous invocations.
        // This particular test case detects improper tracking of lambdas by giving the lambda an unstable objectID
        // The test is weird because it is challenging to have get a lambda to capture stale values in this kind of test
        // and have the lambda be a non-thread local variable.
        class TestClass {
            val y = AtomicInteger(0)
            var lambda: (() -> Unit)? = null

            fun one(): Int? {
                val x = Object()
                // On subsequent replays this lambda will have different IdentityHashCode causing a new ObjectID.
                // This then breaks the replayer
                lambda = {
                    // We need some kind of capture to get a different identity hash code
                    val unused = x
                }
                lambda?.invoke()
                y.get()
                return 1
            }

            fun two() {
                y.set(1) // Trigger a "backward revisit"
            }
        }

        val testScenario = scenario {
            parallel {
                thread {
                    actor(TestClass::one)
                }
                thread {
                    actor(TestClass::two)
                }
            }
        }

        val outcomes: Set<Unit> = setOf(Unit)
        litmusTest(TestClass::class.java, testScenario, assertSame(outcomes, UNKNOWN)) { _ -> }
    }

    @Test
    fun testNonCapturingLambdaAllocationIsTracked() {
        // Sister test of `testLambdaAllocationIsTracked`, but for *non-capturing* lambdas.
        // Non-capturing lambdas are JVM-cached singletons: the same instance is returned
        // by every execution of the bootstrapped `invokedynamic` call site (same VM-local
        // identity hash code, no fresh allocation per call).
        //
        // The dedicated `afterInvokeDynamicObjectCreation` injection hook is idempotent
        // precisely so that hitting the same `invokedynamic` site repeatedly within an
        // invocation does not produce duplicate allocation events for the same singleton —
        // which would otherwise break replay determinism in the event-structure strategy.
        class TestClass {
            val y = AtomicInteger(0)
            var lambda: (() -> Unit)? = null

            fun one(): Int? {
                // Hit the same `invokedynamic` (linked to `LambdaMetafactory.metafactory`)
                // multiple times. Each execution returns the SAME JVM-cached singleton,
                // but the bytecode-injected `afterInvokeDynamicObjectCreation` fires every time.
                for (i in 0..2) {
                    lambda = { /* no captures - JVM caches the result */ }
                    lambda?.invoke()
                }
                y.get()
                return 1
            }

            fun two() {
                y.set(1) // Trigger a "backward revisit"
            }
        }

        val testScenario = scenario {
            parallel {
                thread {
                    actor(TestClass::one)
                }
                thread {
                    actor(TestClass::two)
                }
            }
        }

        val outcomes: Set<Unit> = setOf(Unit)
        litmusTest(TestClass::class.java, testScenario, assertSame(outcomes, UNKNOWN)) { _ -> }
    }

    @Test
    fun testStringConcatenationAllocationIsTracked() {
        // Sister test of `testLambdaAllocationIsTracked`, but for the other `invokedynamic`
        // bootstrap factory we instrument: `StringConcatFactory`.
        // On JVM 9+ targets, the Kotlin compiler lowers string templates and `+` between
        // strings to an `invokedynamic` linked to `StringConcatFactory.makeConcatWithConstants`
        //
        // Each execution produces a fresh `String` whose identity hash code is unstable
        // across replays — the same failure mode as for capturing lambdas. By treating
        // these strings as `NEW` allocations (just like `new String(...)`), we keep their
        // object IDs stable across invocations.
        class TestClass {
            val y = AtomicInteger(0)
            var s: String? = null

            fun one(): Int? {
                val a = "foo"
                val b = "bar"
                // Lowered to invokedynamic with `StringConcatFactory.makeConcatWithConstants`
                // on JVM 9+ targets.
                s = "$a-$b"
                y.get()
                return 1
            }

            fun two() {
                y.set(1) // Trigger a "backward revisit"
            }
        }

        val testScenario = scenario {
            parallel {
                thread {
                    actor(TestClass::one)
                }
                thread {
                    actor(TestClass::two)
                }
            }
        }

        val outcomes: Set<Unit> = setOf(Unit)
        litmusTest(TestClass::class.java, testScenario, assertSame(outcomes, UNKNOWN)) { _ -> }
    }

    @Test
    fun testNonCapturingLambdaAllocationDoesNotBreakBackwardRevisit() {
        // This aims to test the proper handling of non-capturing lambdas with the EventStructure model checking strategy.
        // The JVM stores non-capturing lambdas as singletons, so unlike other anonymous functions,
        // they need to be registered as 'external' objects.
        // Failure to do so could result in inconsistent assignments of objectIDs during different strategy invocation,
        // which then leads to issues during the replay ordering
        class TestClass {
            val y = AtomicInteger(0)

            private fun makeLambda(): () -> Int {
                // Lambdas are cached per call site, so code location matters
                val lambda = { 6 + 7 }
                return lambda
            }

            fun threadOne(): Int {
                val r0 = y.get()
                // This gets hit first in the first execution, so we have an allocation event for the lambda
                val lambda = makeLambda()
                return r0
            }

            fun threadTwo() {
                // The allocation is now cached in the first execution, so no allocation
                // In the second execution, this is hit first, so we have an allocation in a place where we did not expect
                val lambda = makeLambda()
                y.set(1) // Backwards revisit, so `y.get()` is blocked
            }
        }

        val testScenario = scenario {
            parallel {
                thread {
                    actor(TestClass::threadOne)
                }
                thread {
                    actor(TestClass::threadTwo)
                }
            }
        }

        val outcomes: Set<Int> = setOf(0, 1)
        litmusTest(TestClass::class.java, testScenario, assertSame(outcomes, UNKNOWN)) {
            getValue<Int>(it.parallelResults[0][0]!!)
        }
    }

    @Test
    fun testBoxedPrimitives() {
        class TestClass {
            var x: Any? = null;

            fun thread0(): Any? {
                x = 42_000_000
                return x
            }

            fun thread1() {
                x = "foo"
            }
        }

        val testScenario = scenario {
            parallel {
                thread {
                    actor(TestClass::thread0)
                }
                thread {
                    actor(TestClass::thread1)
                }
            }
        }

        val outcomes: Set<Any?> = setOf(42_000_000, "foo")
        litmusTest(TestClass::class.java, testScenario, assertSame(outcomes, UNKNOWN)) { results ->
            val b1 = getValue<Any?>(results.parallelResults[0][0]!!)
            return@litmusTest b1
        }
    }

    class SingleWriterHashTableTest() {
        val scenarios: Int = 300
        val threads: Int = 3
        val actorsBefore: Int = 1

        val checkObstructionFreedom: Boolean = true
        val sequentialSpecification: KClass<*> = SequentialHashTableIntInt::class

        private val hashTable = SingleWriterHashTable<Int, Int>(initialCapacity = 30)

        @Operation
        fun put(key: Int, value: Int): Int? = hashTable.put(key, value)

        @Operation
        fun get(key: Int): Int? = hashTable.get(key)

        @Operation
        fun remove(key: Int): Int? = hashTable.remove(key)

        @Test(timeout = TIMEOUT, expected = AssertionError::class)
        fun modelCheckingTest() = ModelCheckingOptions()
            .iterations(scenarios)
            .invocationsPerIteration(10_000)
            .actorsBefore(actorsBefore)
            .threads(threads)
            .actorsPerThread(2)
            .actorsAfter(0)
            .checkObstructionFreedom(checkObstructionFreedom)
            .sequentialSpecification(sequentialSpecification.java)
            .useExperimentalModelChecking()
            .check(this::class.java)
    }

    internal class SequentialHashTableIntInt {
        private val map = HashMap<Int, Int>()

        fun put(key: Int, value: Int): Int? = map.put(key, value)

        fun get(key: Int): Int? = map.get(key)

        fun remove(key: Int): Int? = map.remove(key)
    }

    @Test
    fun testConcurrentHashMap() {
        val executionScenario = scenario {
            parallel {
                thread {
                    actor(ConcurrentHashMap<Int, Int>::get, 4)
                    actor(ConcurrentHashMap<Int, Int>::put, 3, 4)
                }
                thread {
                    actor(ConcurrentHashMap<Int, Int>::put, 3, 4)
                    actor(ConcurrentHashMap<Int, Int>::get, 2)
                }
            }
        }
        litmusTest(ConcurrentHashMap::class.java, executionScenario, assertSame(setOf(1), UNKNOWN)) { results -> 1 }
    }

    @Test
    fun testConcurrentLinkedDeque() {
        val executionScenario = scenario {
            parallel {
                thread {
                    actor(ConcurrentLinkedQueue<Int>::offer, 0)
                }
                thread {
                    actor(ConcurrentLinkedQueue<Int>::offer, 1)
                }
            }
            post {
                actor(ConcurrentLinkedQueue<Int>::poll)
                actor(ConcurrentLinkedQueue<Int>::poll)
            }
        }
        val outcomes = setOf(1 to 0, 0 to 1)
        litmusTest(ConcurrentLinkedQueue::class.java, executionScenario, assertSame(outcomes, UNKNOWN)) { results ->
            val r1 = getValue<Int?>(results.postResults[0]!!)
            val r2 = getValue<Int?>(results.postResults[1]!!)
            r1 to r2
        }
    }

    @Test
    fun testConcurrentSkipListMap() {
        val put = ConcurrentSkipListMap<Int, Int>::put;
        val remove: (ConcurrentSkipListMap<Int, Int>, Int) -> Int? = ConcurrentSkipListMap<Int, Int>::remove
        val get: (ConcurrentSkipListMap<Int, Int>, Int) -> Int? = ConcurrentSkipListMap<Int, Int>::get
        val executionScenario = scenario {
            parallel {
                thread {
                    actor(put, 0, 0)
                    actor(ConcurrentSkipListMap<Int, Int>::get, 0)
                }
                thread {
                    actor(remove as KFunction<*>, 1)
                }
            }
        }
        val outcomes = setOf(Triple(null, null, 0), Triple(null, null, null))
        litmusTest(ConcurrentSkipListMap::class.java, executionScenario, assertSame(outcomes, UNKNOWN),
            MemoryModel.JAM21) { results ->
            val r1 = getValue<Int?>(results.parallelResults[0][0]!!)
            val r2 = getValue<Int?>(results.parallelResults[1][0]!!)
            val r3 = getValue<Int?>(results.parallelResults[0][1]!!)
            Triple(r1, r2, r3)
        }
    }

    @Test
    fun testMpFencesNotTransitive() {
        val expectedOutcomes: Set<Triple<Int, Int, Int>> = setOf(Triple(1, 1, 0))
        litmusTest(assertSometimes(expectedOutcomes), MemoryModel.JAM21) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            val z = AtomicInteger(0)

            var r0 = -1
            var r1 = -1
            var r2 = -1

            val t0 = thread {
                x.setPlain(1)
                VarHandle.releaseFence()
                y.setPlain(1)
            }

            val t1 = thread {
                r0 = y.getPlain()
                z.setPlain(1)
            }

            val t2 = thread {
                r1 = z.getPlain()
                VarHandle.acquireFence()
                r2 = x.getPlain()
            }

            t0.join()
            t1.join()
            t2.join()

            Triple(r0, r1, r2)
        }
    }

    @Test
    fun testMpReleaseWriteAcquireFence() {
        val expectedOutcomes: Set<Triple<Int, Int, Int>> = setOf(
            Triple(1, 1, 1),
            Triple(1, 0, 1),
            Triple(0, 1, 1),
            Triple(0, 1, 0),
            Triple(0, 0, 1),
            Triple(0, 0, 0)
        )
        litmusTest(assertSame(expectedOutcomes), MemoryModel.JAM21) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            val flag = AtomicInteger(0)

            var r0 = -1
            var r1 = -1
            var r2 = -1

            val t0 = thread {
                x.setPlain(1)
                y.setPlain(1)
                flag.setRelease(1)
            }

            val t1 = thread {
                r0 = flag.getPlain()
                r1 = x.getPlain()
                VarHandle.acquireFence()
                r2 = y.getPlain()
            }

            t0.join()
            t1.join()

            Triple(r0, r1, r2)
        }
    }

    @Test
    fun testMpReleaseFenceAcquireWrite() {
        val expectedOutcomes: Set<Triple<Int, Int, Int>> = setOf(
            Triple(1, 1, 1),
            Triple(1, 1, 0),
//            Triple(1, 0, 1),
//            Triple(1, 0, 0),
            Triple(0, 1, 1),
            Triple(0, 1, 0),
            Triple(0, 0, 1),
            Triple(0, 0, 0)
        )
        litmusTest(assertSame(expectedOutcomes), MemoryModel.JAM21) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            val flag = AtomicInteger(0)

            var r0 = -1
            var r1 = -1
            var r2 = -1

            val t0 = thread {
                x.setPlain(1)
                VarHandle.releaseFence()
                y.setPlain(1)
                flag.setPlain(1)
            }

            val t1 = thread {
                r0 = flag.getAcquire()
                r1 = x.getPlain()
                r2 = y.getPlain()
            }

            t0.join()
            t1.join()

            Triple(r0, r1, r2)
        }
    }


    @Test
    fun testManyRMW() {

        val outcomes = setOf<Triple<Int, Int, Int>>(
            Triple(0, 1, 2),
            Triple(0, 2, 1),
            Triple(1, 0, 2),
            Triple(1, 2, 0),
            Triple(2, 0, 1),
            Triple(2, 1, 0),
        )
        litmusTest(assertSame(outcomes)) {
            val x = AtomicInteger(0)

            val results = IntArray(3)

            val t1 = thread {
                results[0] = x.getAndIncrement()
            }
            val t2 = thread {
                results[1] = x.getAndIncrement()
            }
            val t3 = thread {
                results[2] = x.getAndIncrement()
            }

            t1.join()
            t2.join()
            t3.join()

            Triple(results[0], results[1], results[2])
        }
    }

    @Test
    fun testRMW4() {

        val outcomes = setOf<List<Int>>(
            listOf(0, 1, 2, 3),
            listOf(0, 1, 3, 2),
            listOf(0, 2, 1, 3),
            listOf(0, 2, 3, 1),
            listOf(0, 3, 1, 2),
            listOf(0, 3, 2, 1),

            listOf(1, 0, 2, 3),
            listOf(1, 0, 3, 2),
            listOf(1, 2, 0, 3),
            listOf(1, 2, 3, 0),
            listOf(1, 3, 0, 2),
            listOf(1, 3, 2, 0),

            listOf(2, 0, 1, 3),
            listOf(2, 0, 3, 1),
            listOf(2, 1, 0, 3),
            listOf(2, 1, 3, 0),
            listOf(2, 3, 0, 1),
            listOf(2, 3, 1, 0),

            listOf(3, 0, 1, 2),
            listOf(3, 0, 2, 1),
            listOf(3, 1, 0, 2),
            listOf(3, 1, 2, 0),
            listOf(3, 2, 0, 1),
            listOf(3, 2, 1, 0),
        )
        litmusTest(assertSame(outcomes), MemoryModel.JAM21) {
            val x = AtomicInteger(0)
            val results = IntArray(4)

            val t1 = thread {
                results[0] = x.getAndIncrement()
            }
            val t2 = thread {
                results[1] = x.getAndIncrement()
            }
            val t3 = thread {
                results[2] = x.getAndIncrement()
            }
            val t4 = thread {
                results[3] = x.getAndIncrement()
            }

            t1.join()
            t2.join()
            t3.join()
            t4.join()

            listOf(results[0], results[1], results[2], results[3])
        }
    }


    @Test
    fun testForcedRmwRR() {
        val outcomes = setOf<List<Int>>(
            listOf(0, 0, 0),
            listOf(0, 0, 1),
            listOf(0, 0, 42),
            listOf(0, 1, 1),
            listOf(0, 1, 42),
            listOf(0, 42, 42),
            // The 0,42,43 // should be impossible
            listOf(42, 0, 0),
            listOf(42, 0, 42),
            listOf(42, 0, 43),
            listOf(42, 42, 42),
            listOf(42, 42, 43),
            listOf(42, 43, 43),
        )
        litmusTest(assertSame(outcomes), MemoryModel.JAM21) {
            val x = AtomicInteger(0)
            val r = IntArray(3)

            val t1 = thread {
                r[0] = x.getAndIncrement()
            }

            val t2 = thread {
                x.setOpaque(42)
            }

            val t3 = thread {
                r[1] = x.getOpaque()
                r[2] = x.getOpaque()
            }

            t1.join()
            t2.join()
            t3.join()

            listOf(r[0], r[1], r[2])
        }
    }

    @Test
    fun testForcedRmwWW() {
        val outcomes = setOf<List<Int>>(
            listOf(42),
        )
        litmusTest(assertSame(outcomes), MemoryModel.JAM21) {
            val x = AtomicInteger(0)
            val r = IntArray(1)

            val t1 = thread {
                x.setOpaque(42)
                r[0] = x.getAndIncrement()
            }

            t1.join()

            listOf(r[0])
        }
    }

    @Test
    fun testForcedRmwWR() {
        val outcomes = setOf<List<Int>>(
            // (0, 1) outcome should not happen
            listOf(0, 42),
            listOf(42, 43),
            listOf(42, 42),
        )
        litmusTest(assertSame(outcomes), MemoryModel.JAM21) {
            val x = AtomicInteger(0)
            val r = IntArray(2)

            val t1 = thread {
                r[0] = x.getAndIncrement()
            }
            val t2 = thread {
                x.setOpaque(42)
                r[1] = x.getOpaque()
            }

            t1.join()
            t2.join()

            listOf(r[0], r[1])
        }
    }

    @Test
    fun testForcedRmwRW() {
        val outcomes = setOf<List<Int>>(
            listOf(0, 0),
            listOf(0, 42),
            listOf(42, 42),
            // (42, 0) should not happen
        )
        litmusTest(assertSame(outcomes), MemoryModel.JAM21) {
            val x = AtomicInteger(0)
            val r = IntArray(2)

            val t1 = thread {
                r[0] = x.getOpaque()
                r[1] = x.getAndIncrement()
            }
            val t2 = thread {
                x.setOpaque(42)
            }

            t1.join()
            t2.join()

            listOf(r[0], r[1])
        }
    }

    class MyAtomicInteger {

        @JvmField
        var value: Int

        constructor(initialValue: Int) {
            value = 0; // This is just to shut up the checker
            handle.setVolatile(this, initialValue)
        }

        companion object {
            private val handle = run {
                val lookup = MethodHandles.lookup()
                lookup.findVarHandle(MyAtomicInteger::class.java, "value", Int::class.javaPrimitiveType)
            }
        }

        fun getAndSetAcquire(value: Int): Int = handle.getAndSetAcquire(this, value) as Int
        fun getAndSet(value: Int): Int = handle.getAndSet(this, value) as Int
        fun get(): Int = handle.get(this) as Int
        fun getOpaque(): Int = handle.getOpaque(this) as Int
        fun getAcquire(): Int = handle.getAcquire(this) as Int
    }

    @Test
    fun testRMW2() {
        // TODO: fix this annoying aah test case
        val outcomes = setOf<List<Int>>(
            listOf(0,1,2,3,3),
            listOf(0,1,2,2,3),
            listOf(0,1,2,2,2),
            listOf(0,1,2,1,3),
            listOf(0,1,2,1,2),
            listOf(0,1,2,1,1),
            listOf(0,1,2,0,3),
            listOf(0,1,2,0,2),
            listOf(0,1,2,0,1),
            listOf(0,1,2,0,0),
            // 0,2,1
            listOf(0,3,1,3,3),
            listOf(0,3,1,3,2),
            listOf(0,3,1,2,2),
            listOf(0,3,1,1,3),
            listOf(0,3,1,1,2),
            listOf(0,3,1,1,1),
            listOf(0,3,1,0,3),
            listOf(0,3,1,0,2),
            listOf(0,3,1,0,1),
            listOf(0,3,1,0,0),
            //  1,0,2
            listOf(2,0,1,3,3),
            listOf(2,0,1,2,3),
            listOf(2,0,1,2,2),
            listOf(2,0,1,2,1),
            listOf(2,0,1,1,3),
            listOf(2,0,1,1,1),
            listOf(2,0,1,0,3),
            listOf(2,0,1,0,2),
            listOf(2,0,1,0,1),
            listOf(2,0,1,0,0),
            // 1,2,0
            listOf(3,1,0,3,3),
            listOf(3,1,0,3,2),
            listOf(3,1,0,3,1),
            listOf(3,1,0,2,2),
            listOf(3,1,0,1,2),
            listOf(3,1,0,1,1),
            listOf(3,1,0,0,3),
            listOf(3,1,0,0,2),
            listOf(3,1,0,0,1),
            listOf(3,1,0,0,0),
            // 2,0,1
            listOf(3,0,2,3,3),
            listOf(3,0,2,3,1),
            listOf(3,0,2,2,3),
            listOf(3,0,2,2,2),
            listOf(3,0,2,2,1),
            listOf(3,0,2,1,1),
            listOf(3,0,2,0,3),
            listOf(3,0,2,0,2),
            listOf(3,0,2,0,1),
            listOf(3,0,2,0,0),
            // 2,1,0
            listOf(2,3,0,3,3),
            listOf(2,3,0,3,2),
            listOf(2,3,0,3,1),
            listOf(2,3,0,2,2),
            listOf(2,3,0,2,1),
            listOf(2,3,0,1,1),
            listOf(2,3,0,0,3),
            listOf(2,3,0,0,2),
            listOf(2,3,0,0,1),
            listOf(2,3,0,0,0),
        )
        litmusTest(assertSame(outcomes), MemoryModel.JAM21) {
            val x = MyAtomicInteger(0)

            val r = IntArray(5)

            val t1 = thread {
                r[0] = x.getAndSetAcquire(1)
            }
            val t2 = thread {
                r[1] = x.getAndSetAcquire(2)
            }
            val t3 = thread {
                r[2] = x.getAndSetAcquire(3)
            }
            val t4 = thread {
                r[3] = x.getOpaque()
                r[4] = x.getOpaque()
            }

            t1.join()
            t2.join()
            t3.join()
            t4.join()

            listOf(r[0], r[1], r[2], r[3], r[4])
        }
    }

    @Test
    fun testRMW3_1() {
        val outcomes = setOf<List<Int>>(
            listOf(0,1,2,3),
            listOf(0,3,1,2),
            listOf(0,2,1,3),
            listOf(2,3,0,1),
            listOf(1,2,0,3),
            listOf(1,3,0,2),
        )
        litmusTest(assertSame(outcomes)) {
            val x = AtomicInteger(0)

            val r = IntArray(4)

            val t1 = thread {
                r[0] = x.getAndIncrement()
                r[1] = x.getAndIncrement()
            }
            val t2 = thread {
                r[2] = x.getAndIncrement()
                r[3] = x.getAndIncrement()
            }

            t1.join()
            t2.join()

            listOf(r[0], r[1], r[2], r[3])
        }
    }

    @Test
    fun testRMW3_2() {
        val outcomes = setOf<List<Int>>(
            listOf(0,1,2,3),
            listOf(0,3,1,2),
            listOf(0,2,1,3),
            listOf(2,3,0,1),
            listOf(1,2,0,3),
            listOf(1,3,0,2),
        )
        class TestClass {
            val x = AtomicInteger(0)
            fun one(): Pair<Int, Int> {
                val r1 = x.getAndIncrement()
                val r2 = x.getAndIncrement()
                return r1 to r2
            }

            fun two(): Pair<Int, Int> {
                val r3 = x.getAndIncrement()
                val r4 = x.getAndIncrement()
                return r3 to r4
            }
        }

        val scenario = scenario {
            parallel {
                thread{ actor(TestClass::one)}
                thread{ actor(TestClass::two)}
            }
        }
        litmusTest(TestClass::class.java, scenario, assertSame(outcomes)) { results ->
            val p1 = getValue<Pair<Int, Int>>(results.parallelResults[0][0]!!)
            val p2 = getValue<Pair<Int, Int>>(results.parallelResults[1][0]!!)

            listOf(p1.first, p1.second, p2.first, p2.second)
        }
    }


    @Test
    fun testCAS() {
        val outcomes = setOf<List<Int>>(
            listOf(1,0,0),
            listOf(0,1,0),
            listOf(0,0,1),
        )
        litmusTest(assertSame(outcomes), MemoryModel.JAM21) {
            val x = AtomicInteger(0)
            val r = IntArray(3)

            val t1 = thread {
                r[0] = if(x.compareAndSet(0, 1)) 1 else 0
            }
            val t2 = thread {
                r[1] = if(x.compareAndSet(0, 1)) 1 else 0
            }
            val t3 = thread {
                r[2] = if(x.compareAndSet(0, 1)) 1 else 0
            }

            t1.join()
            t2.join()
            t3.join()

            listOf(r[0], r[1], r[2])
        }
    }

    @Test
    fun testCAS2() {
        val outcomes = setOf<List<Int>>(
            listOf(1,0),
            listOf(0,1)
        )
        litmusTest(assertSame(outcomes)) {
            val x = AtomicInteger(0)
            val r = IntArray(2)

            val t1 = thread {
                r[0] = if(x.weakCompareAndSetPlain(0, 3)) 1 else 0
                if(r[0] == 0) r[1] = x.getAndIncrement()
            }

            val t2 = thread {
                x.setOpaque(1)
            }

            t1.join()
            t2.join()

            listOf(r[0], r[1])
        }
    }

    @Test
    fun testLastZero10Scenario() {
        class LastZero {
            val N = 10
            val array = IntArray(N+1) { 0 }

            constructor() {}

            fun reader() {
                var j = N
                while (array[j--] != 0) {}
            }

            fun writer(i: Int) {
                array[i] = array[i-1] + 1
            }
        }

        val reader = LastZero::reader
        val writer = LastZero::writer

        val testScenario = scenario {
            parallel {
                thread { actor(reader) }
                thread { actor(writer, 1) }
                thread { actor(writer, 2) }
                thread { actor(writer, 3) }
                thread { actor(writer, 4) }
                thread { actor(writer, 5) }
                thread { actor(writer, 6) }
                thread { actor(writer, 7) }
                thread { actor(writer, 8) }
                thread { actor(writer, 9) }
                thread { actor(writer, 10) }
            }
        }

        litmusTest(
            LastZero::class.java,
            testScenario,
            assertSame(setOf(1), 3328),
            MemoryModel.SequentialConsistency,
            10_000
        ) { 1 }
    }

}
