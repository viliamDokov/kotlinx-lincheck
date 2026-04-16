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

import java.lang.invoke.VarHandle
import java.util.concurrent.atomic.*

import org.jetbrains.kotlinx.lincheck.strategy.managed.eventstructure.*
import org.junit.Ignore

import org.junit.Test
import kotlin.concurrent.thread

/**
 * These tests check that [EventStructureStrategy] adheres to the weak memory model.
 * It contains various litmus tests to check for specific weak behaviors.
 */
class MemoryModelTest {

    @Test
    fun testRRRW() {
        val expectedOutcomes: Set<Pair<Int, Int>> = setOf(
            (0 to 0),
            (0 to 1),
            (1 to 0),
            (1 to 1),
        )
        litmustTestv2(assertAlways(expectedOutcomes)) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var r1 = 0;
            var r2 = 0;
            val t1 = thread { r1 = x.get(); r2 = y.get() }
            val t2 = thread { y.set(1) }
            val t3 = thread { x.set(1) }
            t1.join()
            t2.join()
            t3.join()
            (r1 to r2)
        }
    }

    @Test
    fun testRRWOpaque() {
        val expectedOutcomes: Set<Pair<Int, Int>> = setOf(
            (0 to 0),
            (0 to 1),
            (1 to 0),
            (1 to 1),
        )
        litmustTestv2(assertAlways(expectedOutcomes)) {
            val x = AtomicInteger(0)
            var r1 = 0;
            var r2 = 0;
            val t1 = thread { r1 = x.getOpaque() }
            val t2 = thread { r2 = x.getOpaque() }
            val t3 = thread { x.setOpaque(1) }
            t1.join()
            t2.join()
            t3.join()
            (r1 to r2)
        }
    }


/* ======== Store Buffering ======== */


    @Test
    fun testSB() {
        val outcomes: Set<Pair<Int, Int>> = setOf(
            (0 to 0),
            (0 to 1),
            (1 to 0),
            (1 to 1)
        )
        litmustTestv2(assertAlways(outcomes)) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var r1 = 0;
            var r2 = 0;
            val t1 = thread { x.set(1); r1 = y.get() }
            val t2 = thread { y.set(1); r2 = x.get() }
            t1.join()
            t2.join()
            (r1 to r2)
        }
    }

    @Test
    fun testSBList() {
        val outcomes: Set<List<Int>> = setOf(
            listOf(0, 0),
            listOf(0, 1),
            listOf(1, 0),
            listOf(1, 1)
        )
        litmustTestv2(assertAlways(outcomes)) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var r1 = 0;
            var r2 = 0;
            val t1 = thread { x.set(1); r1 = y.get() }
            val t2 = thread { y.set(1); r2 = x.get() }
            t1.join()
            t2.join()
            listOf(r1, r2)
        }
    }

    @Test
    fun testSBOpaque() {
        val expectedOutcomes: Set<Pair<Int, Int>> = setOf((0 to 0), (0 to 1), (1 to 1), (1 to 0))
        litmustTestv2(assertAlways(expectedOutcomes)) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var r1 = 0;
            var r2 = 0;
            val t1 = thread { x.setOpaque(1); r1 = y.getOpaque() }
            val t2 = thread { y.setOpaque(1); r2 = x.getOpaque() }
            t1.join()
            t2.join()
            (r1 to r2)
        }
    }

    @Test
    fun test4SB() {
        val forbiddenOutcomes: Set<List<Int>> = setOf(listOf(0, 0, 0, 0))
        litmustTestv2(assertSometimes(forbiddenOutcomes)) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            val z = AtomicInteger(0)
            val a = AtomicInteger(0)
            var r0 = 0; var r1 = 0; var r2 = 0; var r3 = 0
            val t0 = thread { x.setOpaque(1); r0 = y.getOpaque() }
            val t1 = thread { y.setOpaque(1); r1 = z.getOpaque() }
            val t2 = thread { z.setOpaque(1); r2 = a.getOpaque() }
            val t3 = thread { a.setOpaque(1); r3 = x.getOpaque() }
            t0.join(); t1.join(); t2.join(); t3.join()
            listOf(r0, r1, r2, r3)
        }
    }


    @Test
    fun test6SB() {
        val expectedOutcomes: Set<List<Int>> = setOf(listOf(0, 0, 0, 0, 0, 0))
        litmustTestv2(assertSometimes(expectedOutcomes)) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            val z = AtomicInteger(0)
            val a = AtomicInteger(0)
            val b = AtomicInteger(0)
            val c = AtomicInteger(0)
            var r0 = 0; var r1 = 0; var r2 = 0; var r3 = 0; var r4 = 0; var r5 = 0
            val t0 = thread { x.setOpaque(1); r0 = y.getOpaque() }
            val t1 = thread { y.setOpaque(1); r1 = z.getOpaque() }
            val t2 = thread { z.setOpaque(1); r2 = a.getOpaque() }
            val t3 = thread { a.setOpaque(1); r3 = b.getOpaque() }
            val t4 = thread { b.setOpaque(1); r4 = c.getOpaque() }
            val t5 = thread { c.setOpaque(1); r5 = x.getOpaque() }
            t0.join(); t1.join(); t2.join(); t3.join(); t4.join(); t5.join()
            listOf(r0, r1, r2, r3, r4, r5)
        }
    }

    @Test
    fun testArfna() {
        val forbiddenOutcomes: Set<Pair<Int, Int>> = setOf((1 to 1))
        litmustTestv2(assertNever(forbiddenOutcomes)) {
            val a = AtomicInteger(0)
            val b = AtomicInteger(0)
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var r0 = 0; var r1 = 0
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
            t0.join(); t1.join()
            (x.get() to y.get())
        }
    }

    @Test
    fun testArfnaTransformed() {
        val forbiddenOutcomes: Set<Pair<Int, Int>> = setOf((1 to 1))
        litmustTestv2(assertNever(forbiddenOutcomes)) {
            val a = AtomicInteger(0)
            val b = AtomicInteger(0)
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var r0 = 0; var r1 = 0
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
            t0.join(); t1.join()
            (x.get() to y.get())
        }
    }

    @Test
    fun testB() {
        //NOTE: This is just load buffering, I am not sure why the name is like that.
        val forbiddenOutcomes: Set<Pair<Int, Int>> = setOf((1 to 1))
        litmustTestv2(assertNever(forbiddenOutcomes)) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var r0 = 0; var r1 = 0
            val t0 = thread { r0 = x.getOpaque(); y.setOpaque(1) }
            val t1 = thread { r1 = y.getOpaque(); x.setOpaque(1) }
            t0.join(); t1.join()
            (r0 to r1)
        }
    }

    @Test
    fun testBReorder() {
        val allowedOutcomes: Set<Pair<Int, Int>> = setOf((1 to 1))
        litmustTestv2(assertSometimes(allowedOutcomes)) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var r0 = 0; var r1 = 0
            val t0 = thread { y.setOpaque(1); r0 = x.getOpaque() }
            val t1 = thread { r1 = y.getOpaque(); x.setOpaque(1) }
            t0.join(); t1.join()
            (r0 to r1)
        }
    }

    @Test
    fun testC() {
        val forbiddenOutcomes: Set<Pair<Int, Int>> = setOf((1 to 1))
        litmustTestv2(assertNever(forbiddenOutcomes)) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            val p = AtomicInteger(0)
            val q = AtomicInteger(0)
            var r0 = 0; var r1 = 0
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
            t0.join(); t1.join()
            (p.get() to q.get())
        }
    }

    @Test
    fun testCReorder() {
        val forbiddenOutcomes: Set<Pair<Int, Int>> = setOf((1 to 1))
        litmustTestv2(assertNever(forbiddenOutcomes)) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            val p = AtomicInteger(0)
            val q = AtomicInteger(0)
            var r0 = 0; var r1 = 0
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
            t0.join(); t1.join()
            (p.get() to q.get())
        }
    }

    @Test
    fun testCoRWR() {
        val forbiddenOutcomes: Set<Pair<Int, Int>> = setOf((1 to 0))
        litmustTestv2(assertNever(forbiddenOutcomes)) {
            val x = AtomicInteger(0)
            var eax = 0; var ebx = 0
            val t0 = thread {
                eax = x.getOpaque()
                x.setOpaque(1)
                ebx = x.getOpaque()
            }
            t0.join()
            (eax to ebx)
        }
    }


    @Test
    fun testCyc() {
        val forbiddenOutcomes: Set<Pair<Int, Int>> = setOf((1 to 1))
        litmustTestv2(assertNever(forbiddenOutcomes)) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var r0 = 0; var r1 = 0
            val t0 = thread {
                r0 = x.getOpaque()
                if (r0 != 0) {
                    y.setOpaque(1)
                }
            }
            val t1 = thread {
                r1 = y.getOpaque()
                if (r1 != 0) {
                    x.setOpaque(1)
                }
            }
            t0.join(); t1.join()
            (r0 to r1)
        }
    }

    //PORF acyclic?
    @Ignore
    @Test
    fun testCycNa() {
        val expectedOutcomes: Set<Pair<Int, Int>> = setOf((1 to 1))
        litmustTestv2(assertSometimes(expectedOutcomes)) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var r0 = 0; var r1 = 0
            val t0 = thread {
                r0 = x.getPlain()
                if (r0 != 0) {
                    y.setPlain(1)
                }
            }
            val t1 = thread {
                r1 = y.getPlain()
                if (r1 != 0) {
                    x.setPlain(1)
                }
            }
            t0.join(); t1.join()
            (r0 to r1)
        }
    }

    @Test
    fun testFinalWrite() {
        val expectedOutcomes: Set<Int> = setOf(1)
        litmustTestv2(assertAlways(expectedOutcomes)) {
            val a = AtomicInteger(0)
            val t0 = thread {
                a.setOpaque(1)
            }
            t0.join();
            a.getOpaque()
        }
    }

    @Test
    fun testListOf() {
        val expectedOutcomes: Set<List<Int>> = setOf(listOf(1))
        litmustTestv2(assertAlways(expectedOutcomes)) {
            val a = AtomicInteger(0)
            val t0 = thread {
                a.setOpaque(1)
            }
            t0.join();
            listOf(a.getOpaque())
        }
    }


    @Test
    fun testFig1() {
        val expectedOutcomes: Set<Triple<Int, Int, Int>> = setOf(Triple(1, 1, 1))
        litmustTestv2(assertAlways(expectedOutcomes, UNKNOWN)) {
            val a = AtomicInteger(0)
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var r0 = 0; var r1 = 0; var r2 = 0
            val t0 = thread {
                a.setPlain(1)
                r0 = x.getOpaque()
                r1 = a.getPlain()
                y.setOpaque(1)
            }
            val t1 = thread {
                r2 = y.getOpaque()
                x.setOpaque(1)
            }
            t0.join(); t1.join()
            Triple(a.get(), x.get(), y.get())
        }
    }

    @Test
    fun testIriwInternal() {
        val expectedOutcomes: Set<List<Int>> = setOf(listOf(1, 0, 1, 0))
        litmustTestv2(assertSometimes(expectedOutcomes)) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var eax0 = 0; var ebx0 = 0; var eax1 = 0; var ebx1 = 0
            val t0 = thread {
                x.setOpaque(1)
                eax0 = x.getOpaque()
                ebx0 = y.getOpaque()
            }
            val t1 = thread {
                y.setOpaque(1)
                eax1 = y.getOpaque()
                ebx1 = x.getOpaque()
            }
            t0.join(); t1.join()
            listOf(eax0, ebx0, eax1, ebx1)
        }
    }

    @Test
    fun testIRIW() {
        val expectedOutcomes: Set<List<Int>> = setOf(listOf(1, 0, 1, 0))
        litmustTestv2(assertSometimes(expectedOutcomes)) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var eax0 = 0; var ebx0 = 0; var eax2 = 0; var ebx2 = 0
            val t0 = thread {
                eax0 = y.getOpaque()
                ebx0 = x.getOpaque()
            }
            val t1 = thread { x.setOpaque(1) }
            val t2 = thread {
                eax2 = x.getOpaque()
                ebx2 = y.getOpaque()
            }
            val t3 = thread { y.setOpaque(1) }
            t0.join(); t1.join(); t2.join(); t3.join()
            listOf(eax0, ebx0, eax2, ebx2)
        }
    }

    @Test
    fun testMpRelaxed() {
        val expectedOutcomes: Set<Pair<Int, Int>> = setOf((1 to 0))
        litmustTestv2(assertSometimes(expectedOutcomes)) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var r0 = 0; var r1 = -1
            val t0 = thread {
                x.setPlain(1)
                y.setOpaque(1)
            }
            val t1 = thread {
                r0 = y.getOpaque()
                if (r0 != 0) {
                    r1 = x.getPlain()
                }
            }
            t0.join(); t1.join()
            (r0 to r1)
        }
    }


    @Test
    fun testPodrw001() {
        // NOTE: this is just Store Buffering with 3 reads
        val expectedOutcomes: Set<Triple<Int, Int, Int>> = setOf(Triple(0, 0, 0))
        litmustTestv2(assertSometimes(expectedOutcomes)) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            val z = AtomicInteger(0)
            var r0 = 0; var r1 = 0; var r2 = 0
            val t0 = thread { z.setOpaque(1); r0 = x.getOpaque() }
            val t1 = thread { x.setOpaque(1); r1 = y.getOpaque() }
            val t2 = thread { y.setOpaque(1); r2 = z.getOpaque() }
            t0.join(); t1.join(); t2.join()
            Triple(r0, r1, r2)
        }
    }

    //TODO: This is the full fence test. Not doing that right now
    @Ignore
    @Test
    fun testRWCSyncs() {
        val forbiddenOutcomes: Set<Triple<Int, Int, Int>> = setOf(Triple(1, 0, 0))
        litmustTestv2(assertNever(forbiddenOutcomes)) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var r1 = 0; var r2 = 0; var r3 = 0
            val t0 = thread { x.setOpaque(1) }
            val t1 = thread {
                r1 = x.getOpaque()
                VarHandle.fullFence()
                r2 = y.getOpaque()
            }
            val t2 = thread {
                y.setOpaque(1)
                VarHandle.fullFence()
                r3 = x.getOpaque()
            }
            t0.join(); t1.join(); t2.join()
            Triple(r1, r2, r3)
        }
    }

    @Test
    fun testWRR() {
        val forbiddenOutcomes: Set<Pair<Int, Int>> = setOf((1 to 0))
        litmustTestv2(assertNever(forbiddenOutcomes)) {
            val x = AtomicInteger(0)
            var x2 = 0; var x3 = 0
            val t0 = thread { x.setOpaque(1) }
            val t1 = thread {
                x2 = x.getOpaque()
                x3 = x.getOpaque()
            }
            t0.join(); t1.join()
            (x2 to x3)
        }
    }

    @Test
    fun testX001() {
        val expectedOutcomes: Set<Triple<Int, Int, Int>> = setOf(Triple(0, 1, 0))
        litmustTestv2(assertSometimes(expectedOutcomes)) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var r0 = 0; var eax = 0; var ebx = 0
            val t0 = thread { x.setOpaque(1); r0 = y.getOpaque() }
            val t1 = thread {
                y.setOpaque(1)
                eax = y.getOpaque()
                ebx = x.getOpaque()
            }
            t0.join(); t1.join()
            Triple(r0, eax, ebx)
        }
    }


    @Test
    fun testX003() {
        val expectedOutcomes: Set<Triple<Int, Int, Int>> = setOf(Triple(2, 2, 0))
        litmustTestv2(assertSometimes(expectedOutcomes)) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var eax = 0; var ebx = 0
            val t0 = thread {
                x.setOpaque(1)
                y.setOpaque(1)
            }
            val t1 = thread {
                y.setOpaque(2)
                eax = y.getOpaque()
                ebx = x.getOpaque()
            }
            t0.join(); t1.join()
            Triple(y.get(), eax, ebx)
        }
    }

    @Test
    fun testX006() {
        val expectedOutcomes: Set<Pair<Int, Int>> = setOf((2 to 0))
        litmustTestv2(assertSometimes(expectedOutcomes)) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var r0 = 0
            val t0 = thread {
                x.setOpaque(1)
                y.setOpaque(1)
            }
            val t1 = thread {
                y.setOpaque(2)
                r0 = x.getOpaque()
            }
            t0.join(); t1.join()
            (y.get() to r0)
        }
    }

    @Test
    fun testX86_2plus2W() {
        val expectedOutcomes: Set<Pair<Int, Int>> = setOf((2 to 2))
        litmustTestv2(assertSometimes(expectedOutcomes)) {
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
            t0.join(); t1.join()
            (x.get() to y.get())
        }
    }


    @Test
    fun testA1() {
        val expectedOutcomes: Set<Pair<Int, Int>> = setOf((1 to 1))
        litmustTestv2(assertSometimes(expectedOutcomes)) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var r0 = 0; var r1 = 0
            val t0 = thread {
                r0 = y.getOpaque()
                x.setRelease(1)
            }
            val t1 = thread {
                r1 = x.getAcquire()
                if (r1 != 0) {
                    y.setPlain(1)
                }
            }
            t1.join(); t0.join()
            (x.get() to y.get())
        }
    }

    @Test
    fun testA1Reorder() {
        val expectedOutcomes: Set<Pair<Int, Int>> = setOf((1 to 1))
        litmustTestv2(assertSometimes(expectedOutcomes)) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var r0 = 0; var r1 = 0
            val t0 = thread {
                x.setRelease(1)
                r0 = y.getOpaque()
            }
            val t1 = thread {
                r1 = x.getAcquire()
                if (r1 != 0) {
                    y.setPlain(1)
                }
            }
            t0.join(); t1.join()
            (x.get() to y.get())
        }
    }

    @Test
    fun testA3() {
        val expectedOutcomes: Set<Int> = setOf(1)
        litmustTestv2(assertSometimes(expectedOutcomes)) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var r1 = 0
            val t0 = thread {
                y.setPlain(1)
                x.setRelease(1)
            }
            val t1 = thread {
                r1 = x.getAcquire()
                var r2 = 0
                if (r1 != 0) {
                    r2 = y.getOpaque()
                }
            }
            t0.join(); t1.join()
            r1
        }
    }

    @Test
    fun testA3Reorder() {
        val expectedOutcomes: Set<Int> = setOf(1)
        litmustTestv2(assertSometimes(expectedOutcomes)) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var r1 = 0
            val t0 = thread {
                y.setPlain(1)
                x.setRelease(1)
            }
            val t1 = thread {
                val r2 = y.getOpaque()
                r1 = x.getAcquire()
            }
            t0.join(); t1.join()
            r1
        }
    }

    @Test
    fun testIRIWPoaasLL() {
        val expectedOutcomes: Set<List<Int>> = setOf(listOf(1, 0, 1, 0))
        litmustTestv2(assertSometimes(expectedOutcomes)) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var r1 = 0; var r2 = 0; var r3 = 0; var r4 = 0
            val t0 = thread { x.setRelease(1) }
            val t1 = thread { y.setRelease(1) }
            val t2 = thread {
                r1 = x.getAcquire()
                r2 = y.getAcquire()
            }
            val t3 = thread {
                r3 = y.getAcquire()
                r4 = x.getAcquire()
            }
            t0.join(); t1.join(); t2.join(); t3.join()
            listOf(r1, r2, r3, r4)
        }
    }

    @Test
    fun testIRIWPoapsLL() {
        val expectedOutcomes: Set<List<Int>> = setOf(listOf(1, 0, 1, 0))
        litmustTestv2(assertSometimes(expectedOutcomes)) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var r1 = 0; var r2 = 0; var r3 = 0; var r4 = 0
            val t0 = thread { x.setRelease(1) }
            val t1 = thread { y.setRelease(1) }
            val t2 = thread {
                r1 = x.getAcquire()
                r2 = y.getOpaque()
            }
            val t3 = thread {
                r3 = y.getAcquire()
                r4 = x.getOpaque()
            }
            t0.join(); t1.join(); t2.join(); t3.join()
            listOf(r1, r2, r3, r4)
        }
    }


    @Test
    fun testLinearisation() {
        val expectedOutcomes: Set<List<Int>> = setOf(listOf(2, 1, 1, 1, 1))
        litmustTestv2(assertNever(expectedOutcomes)) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            val w = AtomicInteger(0)
            val z = AtomicInteger(0)
            var r0 = 0; var r1 = 0; var r2 = 0
            val t0 = thread {
                r0 = x.getAcquire() + y.getPlain()
                if (r0 == 2) {
                    w.setRelease(1)
                }
            }
            val t1 = thread {
                r1 = w.getOpaque()
                if (r1 != 0) {
                    z.setOpaque(1)
                }
            }
            val t2 = thread {
                r2 = z.getOpaque()
                if (r2 != 0) {
                    y.setPlain(1)
                    x.setRelease(1)
                }
            }
            t0.join(); t1.join(); t2.join()
            listOf(r0, w.get(), x.get(), y.get(), z.get())
        }
    }

    @Test
    fun testLinearisation2() {
        val expectedOutcomes: Set<List<Int>> = setOf(listOf(2, 1, 1, 1, 1))
        litmustTestv2(assertNever(expectedOutcomes)) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            val w = AtomicInteger(0)
            val z = AtomicInteger(0)
            var r0 = 0; var r1 = 0; var r2 = 0
            val t0 = thread {
                var t = x.getAcquire()
                t = t + y.getPlain()
                r0 = t
                if (t == 2) {
                    w.setRelease(1)
                }
            }
            val t1 = thread {
                r1 = w.getOpaque()
                if (r1 != 0) {
                    z.setOpaque(1)
                }
            }
            val t2 = thread {
                r2 = z.getOpaque()
                if (r2 != 0) {
                    y.setPlain(1)
                    x.setRelease(1)
                }
            }
            t0.join(); t1.join(); t2.join()
            listOf(r0, w.get(), x.get(), y.get(), z.get())
        }
    }

    @Test
    fun testMpRelacq() {
        val expectedOutcomes: Set<Pair<Int, Int>> = setOf((1 to 0))
        litmustTestv2(assertNever(expectedOutcomes)) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var r0 = 0; var r1 = -1
            val t0 = thread {
                x.setPlain(1)
                y.setRelease(1)
            }
            val t1 = thread {
                r0 = y.getAcquire()
                if (r0 == 1) {
                    r1 = x.getPlain()
                }
            }
            t0.join(); t1.join()
            (r0 to r1)
        }
    }

    @Test
    fun testMpRelacqRs() {
        val expectedOutcomes: Set<Pair<Int, Int>> = setOf((2 to 0))
        litmustTestv2(assertSometimes(expectedOutcomes)) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var r0 = 0; var r1 = -1
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
            t0.join(); t1.join()
            (r0 to r1)
        }
    }

    @Test
    fun testRoachmotel() {
        val expectedOutcomes: Set<List<Int>> = setOf(listOf(1, 1, 1, 1))
        litmustTestv2(assertNever(expectedOutcomes)) {
            val a = AtomicInteger(0)
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            val z = AtomicInteger(0)
            var r0 = 0; var r1 = 0; var r2 = 0; var r3 = 0
            val t0 = thread {
                z.setRelease(1)
                a.setPlain(1)
            }
            val t1 = thread {
                r0 = x.getOpaque()
                if (r0 != 0) {
                    r1 = z.getAcquire()
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
            t0.join(); t1.join(); t2.join()
            listOf(a.get(), z.get(), x.get(), y.get())
        }
    }

    @Test
    fun testRoachmotel2() {
        val expectedOutcomes: Set<List<Int>> = setOf(listOf(1, 1, 1, 1))
        litmustTestv2(assertNever(expectedOutcomes)) {
            val a = AtomicInteger(0)
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            val z = AtomicInteger(0)
            var r0 = 0; var r1 = 0; var r2 = 0; var r3 = 0
            val t0 = thread {
                a.setPlain(1)
                z.setRelease(1)
            }
            val t1 = thread {
                r0 = x.getOpaque()
                if (r0 != 0) {
                    r1 = z.getAcquire()
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
            t0.join(); t1.join(); t2.join()
            listOf(a.get(), z.get(), x.get(), y.get())
        }
    }

    @Test
    fun testRseqWeak() {
        val expectedOutcomes: Set<Pair<Int, Int>> = setOf((3 to 1))
        litmustTestv2(assertSometimes(expectedOutcomes)) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var r0 = 0; var r1 = 0
            val t0 = thread { x.setOpaque(2) }
            val t1 = thread {
                y.setPlain(1)
                x.setRelease(1)
                x.setOpaque(3)
            }
            val t2 = thread {
                r0 = x.getAcquire()
                if (r0 == 3) {
                    r1 = y.getPlain()
                }
            }
            t0.join(); t1.join(); t2.join()
            (x.get() to y.get())
        }
    }

    // TODO: To fix this test, we need to make thread joins as release acquire events
//    @Ignore
    @Test
    fun testRseqWeak2() {
        val expectedOutcomes: Set<Pair<Int, Int>> = setOf((3 to 1))
        litmustTestv2(assertAlways(expectedOutcomes, UNKNOWN)) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var r0 = 0; var r1 = 0
            val t0 = thread {
                y.setPlain(1)
                x.setRelease(1)
                x.setOpaque(3)
            }
            val t1 = thread {
                r0 = x.getAcquire()
                if (r0 == 3) {
                    r1 = y.getPlain()
                }
            }
            t0.join(); t1.join()
            (x.get() to y.get())
        }
    }

    @Test
    fun testTotalco() {
        val expectedOutcomes: Set<Triple<Int, Int, Int>> = setOf(Triple(1, 1, 1))
        litmustTestv2(assertNever(expectedOutcomes)) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var r0 = 0; var r1 = 0; var r2 = 0
            val t0 = thread {
                r0 = x.getOpaque()
                x.setOpaque(1)
            }
            val t1 = thread {
                r1 = y.getAcquire()
                x.setOpaque(2)
            }
            val t2 = thread {
                r2 = x.getAcquire()
                y.setOpaque(1)
            }
            t0.join(); t1.join(); t2.join()
            Triple(r0, r1, r2)
        }
    }

    @Test
    fun testWWRRWWRRWsilpPoaaWsilpPoaa() {
        val expectedOutcomes: Set<List<Int>> = setOf(
            listOf(2, 2, 2, 0, 2, 0)
        )
        litmustTestv2(assertSometimes(expectedOutcomes)) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var r10 = 0; var r12 = 0; var r30 = 0; var r32 = 0
            val t0 = thread {
                x.setRelease(1)
                x.setRelease(2)
            }
            val t1 = thread {
                r10 = x.getAcquire()
                r12 = y.getAcquire()
            }
            val t2 = thread {
                y.setRelease(1)
                y.setRelease(2)
            }
            val t3 = thread {
                r30 = y.getAcquire()
                r32 = x.getAcquire()
            }
            t0.join(); t1.join(); t2.join(); t3.join()
            listOf(x.get(), y.get(), r10, r12, r30, r32)
        }
    }

}

internal class SharedMemory(size: Int = 16) {
    // TODO: use AtomicIntegerArray once it is fixed
    // TODO: In the future we would likely want to switch to atomicfu primitives.
    //   However, atomicfu currently does not support various access modes that we intend to test here.
    private val memory = Array(size) { AtomicInteger() }

    val size: Int
        get() = memory.size

    fun write(location: Int, value: Int) {
        memory[location].set(value)
    }

    fun read(location: Int): Int {
        return memory[location].get()
    }

    // TODO: use `compareAndExchange` once Java 9 is available?
    fun compareAndSet(location: Int, expected: Int, desired: Int): Boolean {
        return memory[location].compareAndSet(expected, desired)
    }

    fun fetchAndAdd(location: Int, delta: Int): Int {
        return memory[location].getAndAdd(delta)
    }
}
