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

import org.jetbrains.kotlinx.lincheck.strategy.managed.eventstructure.consistency.MemoryModel
import org.jetbrains.lincheck.util.JdkVersion
import org.jetbrains.lincheck.util.jdkVersion
import org.junit.Assume.assumeFalse
import org.junit.Before
import java.util.concurrent.atomic.*
import org.junit.Test
import org.junit.Ignore
import java.lang.invoke.VarHandle
import kotlin.concurrent.thread

class JMTTests {

    @Before
    fun setUp() {
        // currently these tests lead to hangs on JDK-21, apparently due to
        // an unrelated bug with Kotlin stdlib arrays/collection util functions instrumentation,
        // see https://github.com/JetBrains/lincheck/issues/564 for details
        assumeFalse((jdkVersion == JdkVersion.JDK_21))
    }

    // ==================== sevcik ====================

    // RESULT: Never
    // forbidden per SA08 Section 4.1
    @Test
    fun test_SA08_irrelevant_read_introduction_source() {
        val forbiddenOutcomes: Set<Pair<Int, Int>> = setOf(1 to 1)
        litmusTest(assertNever(forbiddenOutcomes), MemoryModel.ReleaseAcquire) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            val z = AtomicInteger(0)
            var t0_r1 = 0
            var t1_r2 = 0
            val t0 = thread {
                t0_r1 = z.getPlain()
                if (t0_r1 == 0) {
                    var r3 = x.getPlain()
                    if (r3 == 1) {
                        y.setPlain(1)
                    }
                } else {
                    var r5 = 1
                    y.setPlain(t0_r1)
                }
            }
            val t1 = thread {
                x.setPlain(1)
                t1_r2 = y.getPlain()
                z.setPlain(t1_r2)
            }
            t0.join()
            t1.join()
            t0_r1 to t1_r2
        }
    }

    // IGNORED: test_SA08_irrelevant_read_introduction_target() {}

    // RESULT: Never
    // forbidden per SA08 Section 4.1
    @Test
    fun test_SA08_redundant_read_after_read_elimination_source() {
        val forbiddenOutcomes: Set<Int> = setOf(1)
        litmusTest(assertNever(forbiddenOutcomes), MemoryModel.ReleaseAcquire) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var t1_r2 = 0
            val t0 = thread {
                var r1 = x.getPlain()
                y.setPlain(r1)
            }
            val t1 = thread {
                t1_r2 = y.getPlain()
                if (t1_r2 == 1) {
                    var r3 = y.getPlain()
                    x.setPlain(r3)
                } else {
                    x.setPlain(1)
                }
            }
            t0.join()
            t1.join()
            t1_r2
        }
    }

    // IGNORED: test_SA08_redundant_read_after_read_elimination_target() {

    // RESULT: Never
    // forbidden per S08 Figure 1
    @Test
    fun test_Sevcik_2008_Tester_source() {
        val forbiddenOutcomes: Set<Int> = setOf(1)
        litmusTest(assertNever(forbiddenOutcomes), MemoryModel.ReleaseAcquire) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            val z = AtomicInteger(0)
            var t1_ans = 0
            val t0 = thread {
                var r1 = x.getPlain()
                y.setPlain(r1)
                var ans = r1
            }
            val t1 = thread {
                var r1 = z.get()
                var r2 = y.getPlain()
                if (r2 == 1) {
                    var r3 = y.getPlain()
                    x.setPlain(r3)
                } else {
                    x.setPlain(1)
                }
                t1_ans = r2
            }
            t0.join()
            t1.join()
            t1_ans
        }
    }

    // Ignored:  fun test_Sevcik_2008_Tester_target() {

    // ==================== jam ====================

    // IGNORED_RESULT: Never
    // forbidden per LBP21 Figure 8
    // TODO: review what is the correct outcome. Since this outcome is based on a flawed memory model
    @Ignore
    @Test
    fun test_LBP21_register_promotion_for_volatile_source() {
        val forbiddenOutcomes: Set<List<Int>> = setOf(listOf(1, 2, 1, 2))
        litmusTest(assertNever(forbiddenOutcomes), MemoryModel.ReleaseAcquire) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            val z = AtomicInteger(0)
            var t0_r1 = 0
            var t0_r2 = 0
            var t1_r3 = 0
            var t1_r4 = 0
            val t0 = thread {
                t0_r1 = x.getOpaque()
                t0_r2 = x.getOpaque()
            }
            val t1 = thread {
                t1_r3 = y.getOpaque()
                t1_r4 = y.getOpaque()
            }
            val t2 = thread {
                x.setOpaque(2)
                z.set(1)
                y.set(1)
            }
            val t3 = thread {
                y.set(2)
                x.set(1)
            }
            t0.join()
            t1.join()
            t2.join()
            t3.join()
            listOf(t0_r1, t0_r2, t1_r3, t1_r4)
        }
    }

    // IGNORED_RESULT: Sometimes
    // allowed per LBP21 Figure 9
    // TODO: review what is the correct outcome. Since this outcome is based on a flawed memory model
    @Ignore
    @Test
    fun test_LBP21_register_promotion_for_volatile_target() {
        val expectedOutcomes: Set<List<Int>> = setOf(listOf(1, 2, 1, 2))
        litmusTest(assertSometimes(expectedOutcomes), MemoryModel.ReleaseAcquire) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var t0_r1 = 0
            var t0_r2 = 0
            var t1_r3 = 0
            var t1_r4 = 0
            val t0 = thread {
                t0_r1 = x.getOpaque()
                t0_r2 = x.getOpaque()
            }
            val t1 = thread {
                t1_r3 = y.getOpaque()
                t1_r4 = y.getOpaque()
            }
            val t2 = thread {
                x.setOpaque(2)
                var z = 1
                y.set(1)
            }
            val t3 = thread {
                y.set(2)
                x.set(1)
            }
            t0.join()
            t1.join()
            t2.join()
            t3.join()
            listOf(t0_r1, t0_r2, t1_r3, t1_r4)
        }
    }

    // RESULT: Never
    // LBP21 Figure 1 / Listing L.1
    @Ignore // TODO: ADD SC
    @Test
    fun test_LBP21_volatile_non_sc_4() {
        val forbiddenOutcomes: Set<List<Int>> = setOf(listOf(0, 1, 1, 2))
        litmusTest(assertNever(forbiddenOutcomes), MemoryModel.ReleaseAcquire) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var t0_r1 = 0
            var t2_r2 = 0
            var t3_r3 = 0
            var t3_r4 = 0
            val t0 = thread {
                x.set(2)
                t0_r1 = y.get()
            }
            val t1 = thread {
                y.set(1)
            }
            val t2 = thread {
                t2_r2 = y.get()
                x.set(1)
            }
            val t3 = thread {
                t3_r3 = x.get()
                t3_r4 = x.get()
            }
            t0.join()
            t1.join()
            t2.join()
            t3.join()
            listOf(t0_r1, t2_r2, t3_r3, t3_r4)
        }
    }

    // RESULT: Never
    // LBP21 Listing L.2
    // TODO: review what is the correct outcome. Since this outcome is based on a flawed memory model
    @Ignore // TODO: ADD SC
    @Test
    fun test_LBP21_volatile_non_sc_5() {
        val forbiddenOutcomes: Set<List<Int>> = setOf(listOf(0, 1, 0, 1, 2))
        litmusTest(assertNever(forbiddenOutcomes), MemoryModel.ReleaseAcquire) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            val z = AtomicInteger(0)
            var t0_r1 = 0
            var t2_r2 = 0
            var t3_r3 = 0
            var t4_r4 = 0
            var t4_r5 = 0
            val t0 = thread {
                x.set(1)
                t0_r1 = y.get()
            }
            val t1 = thread {
                y.set(1)
            }
            val t2 = thread {
                t2_r2 = y.get()
                z.set(1)
            }
            val t3 = thread {
                z.set(2)
                t3_r3 = x.get()
            }
            val t4 = thread {
                t4_r4 = z.get()
                t4_r5 = z.get()
            }
            t0.join()
            t1.join()
            t2.join()
            t3.join()
            t4.join()
            listOf(t0_r1, t2_r2, t3_r3, t4_r4, t4_r5)
        }
    }

    // IGNORED_RESULT: Never
    // forbidden per LBP21 Figure 11
    // TODO: review what is the correct outcome. Since this outcome is based on a flawed memory model
    @Ignore
    @Test
    fun test_LBP21_vread_vread_merging_source() {
        val forbiddenOutcomes: Set<List<Int>> = setOf(listOf(1, 2, 1, 2, 2, 2))
        litmusTest(assertNever(forbiddenOutcomes), MemoryModel.ReleaseAcquire) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var t0_r1 = 0
            var t0_r2 = 0
            var t1_r3 = 0
            var t1_r4 = 0
            var t3_r5 = 0
            var t3_r6 = 0
            val t0 = thread {
                t0_r1 = x.getOpaque()
                t0_r2 = x.getOpaque()
            }
            val t1 = thread {
                t1_r3 = y.getOpaque()
                t1_r4 = y.getOpaque()
            }
            val t2 = thread {
                x.setOpaque(2)
            }
            val t3 = thread {
                t3_r5 = x.get()
                t3_r6 = x.get()
                y.setRelease(1)
            }
            val t4 = thread {
                y.set(2)
                x.set(1)
            }
            t0.join()
            t1.join()
            t2.join()
            t3.join()
            t4.join()
            listOf(t0_r1, t0_r2, t1_r3, t1_r4, t3_r5, t3_r6)
        }
    }

    // IGNORED_RESULT: Sometimes
    // allowed per LBP21 Figure 12
    // TODO: review what is the correct outcome. Since this outcome is based on a flawed memory model
    @Ignore
    @Test
    fun test_LBP21_vread_vread_merging_target() {
        val expectedOutcomes: Set<List<Int>> = setOf(listOf(1, 2, 1, 2, 2, 2))
        litmusTest(assertSometimes(expectedOutcomes), MemoryModel.ReleaseAcquire) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var t0_r1 = 0
            var t0_r2 = 0
            var t1_r3 = 0
            var t1_r4 = 0
            var t3_r5 = 0
            var t3_r6 = 0
            val t0 = thread {
                t0_r1 = x.getOpaque()
                t0_r2 = x.getOpaque()
            }
            val t1 = thread {
                t1_r3 = y.getOpaque()
                t1_r4 = y.getOpaque()
            }
            val t2 = thread {
                x.setOpaque(2)
            }
            val t3 = thread {
                t3_r5 = x.get()
                t3_r6 = t3_r5
                y.setRelease(1)
            }
            val t4 = thread {
                y.set(2)
                x.set(1)
            }
            t0.join()
            t1.join()
            t2.join()
            t3.join()
            t4.join()
            listOf(t0_r1, t0_r2, t1_r3, t1_r4, t3_r5, t3_r6)
        }
    }

    // IGNORED_RESULT: Never
    // forbidden per LBP21 Figure 13
    // TODO: review what is the correct outcome. Since this outcome is based on a flawed memory model
    @Ignore
    @Test
    fun test_LBP21_vwrite_vwrite_merging_source() {
        val forbiddenOutcomes: Set<List<Int>> = setOf(listOf(2, 3, 1, 2))
        litmusTest(assertNever(forbiddenOutcomes), MemoryModel.ReleaseAcquire) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            val z = AtomicInteger(0)
            var t0_r1 = 0
            var t0_r2 = 0
            var t1_r3 = 0
            var t1_r4 = 0
            val t0 = thread {
                t0_r1 = x.getOpaque()
                t0_r2 = x.getOpaque()
            }
            val t1 = thread {
                t1_r3 = y.getOpaque()
                t1_r4 = y.getOpaque()
            }
            val t2 = thread {
                y.setOpaque(2)
                x.set(1)
                x.set(2)
            }
            val t3 = thread {
                x.set(3)
                y.set(1)
            }
            t0.join()
            t1.join()
            t2.join()
            t3.join()
            listOf(t0_r1, t0_r2, t1_r3, t1_r4)
        }
    }

    // IGNORED_RESULT: Sometimes
    // allowed per LBP21 Figure 14
    // TODO: review what is the correct outcome. Since this outcome is based on a flawed memory model
    @Ignore
    @Test
    fun test_LBP21_vwrite_vwrite_merging_target() {
        val expectedOutcomes: Set<List<Int>> = setOf(listOf(2, 3, 1, 2))
        litmusTest(assertSometimes(expectedOutcomes), MemoryModel.ReleaseAcquire) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            val z = AtomicInteger(0)
            var t0_r1 = 0
            var t0_r2 = 0
            var t1_r3 = 0
            var t1_r4 = 0
            val t0 = thread {
                t0_r1 = x.getOpaque()
                t0_r2 = x.getOpaque()
            }
            val t1 = thread {
                t1_r3 = y.getOpaque()
                t1_r4 = y.getOpaque()
            }
            val t2 = thread {
                y.setOpaque(2)
                x.set(2)
            }
            val t3 = thread {
                x.set(3)
                y.set(1)
            }
            t0.join()
            t1.join()
            t2.join()
            t3.join()
            listOf(t0_r1, t0_r2, t1_r3, t1_r4)
        }
    }

    // IGNORED_RESULT: Never
    // forbidden per LBP21 Figure 15
    // TODO: review what is the correct outcome. Since this outcome is based on a flawed memory model
    @Ignore
    @Test
    fun test_LBP21_write_aread_merging_source() {
        val forbiddenOutcomes: Set<List<Int>> = setOf(listOf(1, 2, 1, 2, 0, 1, 2))
        litmusTest(assertNever(forbiddenOutcomes), MemoryModel.ReleaseAcquire) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            val z = AtomicInteger(0)
            var t0_r1 = 0
            var t0_r2 = 0
            var t1_r3 = 0
            var t1_r4 = 0
            var t3_r5 = 0
            var t3_r6 = 0
            var t3_r7 = 0
            val t0 = thread {
                t0_r1 = x.getOpaque()
                t0_r2 = x.getOpaque()
            }
            val t1 = thread {
                t1_r3 = y.getOpaque()
                t1_r4 = y.getOpaque()
            }
            val t2 = thread {
                y.setOpaque(1)
            }
            val t3 = thread {
                x.setRelease(2)
                t3_r7 = x.getAcquire()
                t3_r5 = z.get()
                t3_r6 = y.get()
            }
            val t4 = thread {
                y.set(2)
                x.set(1)
            }
            t0.join()
            t1.join()
            t2.join()
            t3.join()
            t4.join()
            listOf(t0_r1, t0_r2, t1_r3, t1_r4, t3_r5, t3_r6, t3_r7)
        }
    }

    // IGNORED_RESULT: Sometimes
    // allowed per LBP21 Figure 16
    // TODO: review what is the correct outcome. Since this outcome is based on a flawed memory model
    @Ignore
    @Test
    fun test_LBP21_write_aread_merging_target() {
        val expectedOutcomes: Set<List<Int>> = setOf(listOf(1, 2, 1, 2, 0, 1))
        litmusTest(assertSometimes(expectedOutcomes), MemoryModel.ReleaseAcquire) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            val z = AtomicInteger(0)
            var t0_r1 = 0
            var t0_r2 = 0
            var t1_r3 = 0
            var t1_r4 = 0
            var t3_r5 = 0
            var t3_r6 = 0
            val t0 = thread {
                t0_r1 = x.getOpaque()
                t0_r2 = x.getOpaque()
            }
            val t1 = thread {
                t1_r3 = y.getOpaque()
                t1_r4 = y.getOpaque()
            }
            val t2 = thread {
                y.setOpaque(1)
            }
            val t3 = thread {
                x.setRelease(2)
                var r7 = 2
                t3_r5 = z.get()
                t3_r6 = y.get()
            }
            val t4 = thread {
                y.set(2)
                x.set(1)
            }
            t0.join()
            t1.join()
            t2.join()
            t3.join()
            t4.join()
            listOf(t0_r1, t0_r2, t1_r3, t1_r4, t3_r5, t3_r6)
        }
    }

    // ==================== causality-test-cases ====================

    // INGORED: fun test_causality_test_case_01() {

    // IGNORED: fun test_causality_test_case_02() {

    // IGNORED: fun test_causality_test_case_03() {

    // RESULT: Never
    @Test
    fun test_causality_test_case_04() {
        val forbiddenOutcomes: Set<Pair<Int, Int>> = setOf(1 to 1)
        litmusTest(assertNever(forbiddenOutcomes), MemoryModel.ReleaseAcquire) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var t0_r1 = 0
            var t1_r2 = 0
            val t0 = thread {
                t0_r1 = x.getPlain()
                y.setPlain(t0_r1)
            }
            val t1 = thread {
                t1_r2 = y.getPlain()
                x.setPlain(t1_r2)
            }
            t0.join()
            t1.join()
            t0_r1 to t1_r2
        }
    }

    // RESULT: Never
    @Test
    fun test_causality_test_case_05() {
        val forbiddenOutcomes: Set<Triple<Int, Int, Int>> = setOf(Triple(1, 1, 0))
        litmusTest(assertNever(forbiddenOutcomes), MemoryModel.ReleaseAcquire) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            val z = AtomicInteger(0)
            var t0_r1 = 0
            var t1_r2 = 0
            var t3_r3 = 0
            val t0 = thread {
                t0_r1 = x.getPlain()
                y.setPlain(t0_r1)
            }
            val t1 = thread {
                t1_r2 = y.getPlain()
                x.setPlain(t1_r2)
            }
            val t2 = thread {
                z.setPlain(1)
            }
            val t3 = thread {
                t3_r3 = z.getPlain()
                x.setPlain(t3_r3)
            }
            t0.join()
            t1.join()
            t2.join()
            t3.join()
            Triple(t0_r1, t1_r2, t3_r3)
        }
    }

    // IGNORED: fun test_causality_test_case_06() {

    // IGNORED: fun test_causality_test_case_07() {

    // IGNORED: fun test_causality_test_case_08() {

    // IGNORED: fun test_causality_test_case_09() {

    // RESULT: Never
    @Test
    fun test_causality_test_case_10() {
        val forbiddenOutcomes: Set<Triple<Int, Int, Int>> = setOf(Triple(1, 1, 0))
        litmusTest(assertNever(forbiddenOutcomes), MemoryModel.ReleaseAcquire) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            val z = AtomicInteger(0)
            var t0_r1 = 0
            var t1_r2 = 0
            var t3_r3 = 0
            val t0 = thread {
                t0_r1 = x.getPlain()
                if (t0_r1 == 1) {
                    y.setPlain(1)
                }
            }
            val t1 = thread {
                t1_r2 = y.getPlain()
                if (t1_r2 == 1) {
                    x.setPlain(1)
                }
            }
            val t2 = thread {
                z.setPlain(1)
            }
            val t3 = thread {
                t3_r3 = z.getPlain()
                if (t3_r3 == 1) {
                    x.setPlain(1)
                }
            }
            t0.join()
            t1.join()
            t2.join()
            t3.join()
            Triple(t0_r1, t1_r2, t3_r3)
        }
    }

    // IGNORED: fun test_causality_test_case_11() {

    // RESULT: Never
    @Test
    fun test_causality_test_case_13() {
        val forbiddenOutcomes: Set<Pair<Int, Int>> = setOf(1 to 1)
        litmusTest(assertNever(forbiddenOutcomes), MemoryModel.ReleaseAcquire) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var t0_r1 = 0
            var t1_r2 = 0
            val t0 = thread {
                t0_r1 = x.getPlain()
                if (t0_r1 == 1) {
                    y.setPlain(1)
                }
            }
            val t1 = thread {
                t1_r2 = y.getPlain()
                if (t1_r2 == 1) {
                    x.setPlain(1)
                }
            }
            t0.join()
            t1.join()
            t0_r1 to t1_r2
        }
    }

    // IGNORED: fun test_causality_test_case_16() {

    // RESULT: Never
    // intended allowed per http://www.cs.umd.edu/~pugh/java/memoryModel/CausalityTestCases.html
    // actually disallowed per Aspinall and Sevcik "Formalising Java's Data Race Free Guarantee" Section 4
    @Test
    fun test_causality_test_case_17() {
        val forbiddenOutcomes: Set<Triple<Int, Int, Int>> = setOf(Triple(42, 42, 42))
        litmusTest(assertNever(forbiddenOutcomes), MemoryModel.ReleaseAcquire) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var t0_r1 = 0
            var t0_r3 = 0
            var t1_r2 = 0
            val t0 = thread {
                t0_r3 = x.getPlain()
                if (t0_r3 != 42) {
                    x.setPlain(42)
                }
                t0_r1 = x.getPlain()
                y.setPlain(t0_r1)
            }
            val t1 = thread {
                t1_r2 = y.getPlain()
                x.setPlain(t1_r2)
            }
            t0.join()
            t1.join()
            Triple(t0_r1, t1_r2, t0_r3)
        }
    }

    // RESULT: Never
    // intended allowed per http://www.cs.umd.edu/~pugh/java/memoryModel/CausalityTestCases.html
    // actually disallowed per Aspinall and Sevcik "Formalising Java's Data Race Free Guarantee" Section 4
    @Test
    fun test_causality_test_case_18() {
        val forbiddenOutcomes: Set<Triple<Int, Int, Int>> = setOf(Triple(42, 42, 42))
        litmusTest(assertNever(forbiddenOutcomes), MemoryModel.ReleaseAcquire) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var t0_r1 = 0
            var t0_r3 = 0
            var t1_r2 = 0
            val t0 = thread {
                t0_r3 = x.getPlain()
                if (t0_r3 == 0) {
                    x.setPlain(42)
                }
                t0_r1 = x.getPlain()
                y.setPlain(t0_r1)
            }
            val t1 = thread {
                t1_r2 = y.getPlain()
                x.setPlain(t1_r2)
            }
            t0.join()
            t1.join()
            Triple(t0_r1, t1_r2, t0_r3)
        }
    }

    // ==================== jmanson-thesis ====================

    // RESULT: Never
    @Test
    fun test_jmanson_thesis_fig_1_2() {
        val forbiddenOutcomes: Set<Pair<Int, Int>> = setOf(42 to 42)
        litmusTest(assertNever(forbiddenOutcomes), MemoryModel.ReleaseAcquire) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var t0_r1 = 0
            var t1_r2 = 0
            val t0 = thread {
                t0_r1 = x.getPlain()
                y.setPlain(t0_r1)
            }
            val t1 = thread {
                t1_r2 = y.getPlain()
                x.setPlain(t1_r2)
            }
            t0.join()
            t1.join()
            t0_r1 to t1_r2
        }
    }

    // IGNORED: fun test_jmanson_thesis_fig_1_3() {

    // IGNORED: fun test_jmanson_thesis_fig_2_1() {

    // RESULT: Sometimes
    @Test
    fun test_jmanson_thesis_fig_2_2() {
        val expectedOutcomes: Set<Pair<Int, Int>> = setOf(2 to 1)
        litmusTest(assertSometimes(expectedOutcomes), MemoryModel.ReleaseAcquire) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var t0_r2 = 0
            var t1_r1 = 0
            val t0 = thread {
                y.setPlain(1)
                t0_r2 = x.getPlain()
            }
            val t1 = thread {
                x.setPlain(2)
                t1_r1 = y.getPlain()
            }
            t0.join()
            t1.join()
            t0_r2 to t1_r1
        }
    }

    // RESULT: Sometimes
    @Test
    fun test_jmanson_thesis_fig_2_5() {
        val expectedOutcomes: Set<Triple<Int, Int, Int>> = setOf(Triple(1, 0, 1))
        litmusTest(assertSometimes(expectedOutcomes), MemoryModel.ReleaseAcquire) {
            val a = AtomicInteger(0)
            val b = AtomicInteger(0)
            var t1_r1 = 0
            var t1_r2 = 0
            var t1_r3 = 0
            val t0 = thread {
                a.setPlain(1)
                b.setPlain(1)
            }
            val t1 = thread {
                t1_r1 = b.getPlain()
                t1_r2 = a.getPlain()
                t1_r3 = a.getPlain()
            }
            t0.join()
            t1.join()
            Triple(t1_r1, t1_r2, t1_r3)
        }
    }

    // RESULT: Never
    @Test
    fun test_jmanson_thesis_fig_3_1() {
        // exists (0:r1!=0 \/ 1:r2!=0) -- disjunction with !=
        // Since x,y start at 0 and writes are conditional on non-zero reads,
        // the only possible outcome is (0,0). We forbid any (nonzero /\ nonzero).
        val forbiddenOutcomes: Set<Pair<Int, Int>> = setOf(1 to 1)
        litmusTest(assertNever(forbiddenOutcomes), MemoryModel.ReleaseAcquire) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var t0_r1 = 0
            var t1_r2 = 0
            val t0 = thread {
                t0_r1 = x.getPlain()
                if (t0_r1 != 0) {
                    y.setPlain(42)
                }
            }
            val t1 = thread {
                t1_r2 = y.getPlain()
                if (t1_r2 != 0) {
                    x.setPlain(42)
                }
            }
            t0.join()
            t1.join()
            t0_r1 to t1_r2
        }
    }

    // IGNORED: fun test_jmanson_thesis_fig_3_3a() {

    // RESULT: Sometimes
    @Test
    fun test_jmanson_thesis_fig_3_3b() {
        val expectedOutcomes: Set<Triple<Int, Int, Int>> = setOf(Triple(2, 2, 2))
        litmusTest(assertSometimes(expectedOutcomes), MemoryModel.ReleaseAcquire) {
            val a = AtomicInteger(0)
            val b = AtomicInteger(0)
            var t0_r1 = 0
            var t0_r2 = 0
            var t1_r3 = 0
            val t0 = thread {
                b.setPlain(2)
                t0_r1 = a.getPlain()
                t0_r2 = t0_r1
            }
            val t1 = thread {
                t1_r3 = b.getPlain()
                a.setPlain(t1_r3)
            }
            t0.join()
            t1.join()
            Triple(t0_r1, t0_r2, t1_r3)
        }
    }

    // IGNORED: fun test_jmanson_thesis_fig_3_5() {

    //IGNORED: fun test_jmanson_thesis_fig_3_6() {

    // RESULT: Never
    @Ignore // TODO: ADD SC
    @Test
    fun test_jmanson_thesis_fig_3_9() {
        val forbiddenOutcomes: Set<List<Int>> = setOf(listOf(1, 0, 2, 0))
        litmusTest(assertNever(forbiddenOutcomes), MemoryModel.ReleaseAcquire) {
            val v1 = AtomicInteger(0)
            val v2 = AtomicInteger(0)
            var t2_r1 = 0
            var t2_r2 = 0
            var t3_r3 = 0
            var t3_r4 = 0
            val t0 = thread {
                v1.set(1)
            }
            val t1 = thread {
                v2.set(2)
            }
            val t2 = thread {
                t2_r1 = v1.get()
                t2_r2 = v2.get()
            }
            val t3 = thread {
                t3_r3 = v2.get()
                t3_r4 = v1.get()
            }
            t0.join()
            t1.join()
            t2.join()
            t3.join()
            listOf(t2_r1, t2_r2, t3_r3, t3_r4)
        }
    }

    // RESULT: Never
    @Test
    fun test_jmanson_thesis_fig_3_10() {
        val forbiddenOutcomes: Set<Pair<Int, Int>> = setOf(1 to 1)
        litmusTest(assertNever(forbiddenOutcomes), MemoryModel.ReleaseAcquire) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            val v = AtomicInteger(0)
            var t0_r1 = 0
            var t1_r3 = 0
            val t0 = thread {
                t0_r1 = x.getPlain()
                v.set(0)
                var r2 = v.get()
                y.setPlain(1)
            }
            val t1 = thread {
                t1_r3 = y.getPlain()
                v.set(0)
                var r4 = v.get()
                x.setPlain(1)
            }
            t0.join()
            t1.join()
            t0_r1 to t1_r3
        }
    }

    // IGNORED: fun test_jmanson_thesis_fig_4_10() {

    // RESULT: Never
    @Test
    fun test_jmanson_thesis_fig_4_11() {
        val forbiddenOutcomes: Set<Triple<Int, Int, Int>> = setOf(Triple(1, 1, 1))
        litmusTest(assertNever(forbiddenOutcomes), MemoryModel.ReleaseAcquire) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var t0_r1 = 0
            var t1_r2 = 0
            var t2_r3 = 0
            val t0 = thread {
                t0_r1 = x.getPlain()
                if (t0_r1 == 0) {
                    x.setPlain(1)
                }
            }
            val t1 = thread {
                t1_r2 = x.getPlain()
                y.setPlain(t1_r2)
            }
            val t2 = thread {
                t2_r3 = y.getPlain()
                x.setPlain(t2_r3)
            }
            t0.join()
            t1.join()
            t2.join()
            Triple(t0_r1, t1_r2, t2_r3)
        }
    }

    //IGNORED: fun test_jmanson_thesis_fig_4_12() {

    // RESULT: Never
    // repeat of CausalityTestCases-Test05 modulo renaming and different constant.
    @Test
    fun test_jmanson_thesis_fig_4_5() {
        val forbiddenOutcomes: Set<Triple<Int, Int, Int>> = setOf(Triple(0, 42, 42))
        litmusTest(assertNever(forbiddenOutcomes), MemoryModel.ReleaseAcquire) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            val z = AtomicInteger(0)
            var t0_r1 = 0
            var t1_r2 = 0
            var t3_r0 = 0
            val t0 = thread {
                t0_r1 = x.getPlain()
                y.setPlain(t0_r1)
            }
            val t1 = thread {
                t1_r2 = y.getPlain()
                x.setPlain(t1_r2)
            }
            val t2 = thread {
                z.setPlain(42)
            }
            val t3 = thread {
                t3_r0 = z.getPlain()
                x.setPlain(t3_r0)
            }
            t0.join()
            t1.join()
            t2.join()
            t3.join()
            Triple(t3_r0, t0_r1, t1_r2)
        }
    }

    // RESULT: Never
    // repeat of CausalityTestCases-Test10 modulo renaming and different constant.
    @Test
    fun test_jmanson_thesis_fig_4_6() {
        val forbiddenOutcomes: Set<Triple<Int, Int, Int>> = setOf(Triple(0, 42, 42))
        litmusTest(assertNever(forbiddenOutcomes), MemoryModel.ReleaseAcquire) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            val z = AtomicInteger(0)
            var t0_r1 = 0
            var t1_r2 = 0
            var t3_r0 = 0
            val t0 = thread {
                t0_r1 = x.getPlain()
                if (t0_r1 == 1) {
                    y.setPlain(1)
                }
            }
            val t1 = thread {
                t1_r2 = y.getPlain()
                if (t1_r2 == 1) {
                    x.setPlain(1)
                }
            }
            val t2 = thread {
                z.setPlain(1)
            }
            val t3 = thread {
                t3_r0 = z.getPlain()
                if (t3_r0 == 1) {
                    x.setPlain(42)
                }
            }
            t0.join()
            t1.join()
            t2.join()
            t3.join()
            Triple(t3_r0, t0_r1, t1_r2)
        }
    }

    // IGNORED : fun test_jmanson_thesis_fig_4_7() {

    // RESULT: Never
    // repeat of CausalityTestCases-Test12, modulo replacing array by variables.
    @Test
    fun test_jmanson_thesis_fig_4_8() {
        // exists (0:aoobe!=1 /\ 0:r1=1 /\ 0:r2=1 /\ 1:r3=1)
        // aoobe is only set to 1 when r1 >= 2, so aoobe!=1 is implied by r1==1.
        // We approximate the forbidden outcome as (1, 1, 1) for (r1, r2, r3).
        val forbiddenOutcomes: Set<Triple<Int, Int, Int>> = setOf(Triple(1, 1, 1))
        litmusTest(assertNever(forbiddenOutcomes), MemoryModel.ReleaseAcquire) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            val a0 = AtomicInteger(1)
            val a1 = AtomicInteger(2)
            var t0_r1 = 0
            var t0_r2 = 0
            var t1_r3 = 0
            val t0 = thread {
                t0_r1 = x.getPlain()
                if (t0_r1 == 0) {
                    a0.setPlain(0)
                } else {
                    if (t0_r1 == 1) {
                        a1.setPlain(0)
                    } else {
                        var aoobe = 1
                    }
                }
                t0_r2 = a0.getPlain()
                y.setPlain(t0_r2)
            }
            val t1 = thread {
                t1_r3 = y.getPlain()
                x.setPlain(t1_r3)
            }
            t0.join()
            t1.join()
            Triple(t0_r1, t0_r2, t1_r3)
        }
    }

    // RESULT: Never
    @Test
    fun test_jmanson_thesis_fig_4_9() {
        val forbiddenOutcomes: Set<List<Int>> = setOf(listOf(1, 0, 1, 1))
        litmusTest(assertNever(forbiddenOutcomes), MemoryModel.ReleaseAcquire) {
            val a = AtomicInteger(0)
            val b = AtomicInteger(0)
            val c = AtomicInteger(0)
            val d = AtomicInteger(0)
            var t0_r1 = 0
            var t1_r2 = 0
            var t2_r3 = 0
            var t3_r4 = 0
            val t0 = thread {
                t0_r1 = a.getPlain()
                if (t0_r1 == 0) {
                    b.setPlain(1)
                }
            }
            val t1 = thread {
                t1_r2 = b.getPlain()
                if (t1_r2 == 1) {
                    c.setPlain(1)
                }
            }
            val t2 = thread {
                t2_r3 = c.getPlain()
                if (t2_r3 == 1) {
                    d.setPlain(1)
                }
            }
            val t3 = thread {
                t3_r4 = d.getPlain()
                if (t3_r4 == 1) {
                    c.setPlain(1)
                    a.setPlain(1)
                }
            }
            t0.join()
            t1.join()
            t2.join()
            t3.join()
            listOf(t0_r1, t1_r2, t2_r3, t3_r4)
        }
    }

    // IGNORED: fun test_jmanson_thesis_fig_5_1() {

    // IGNORED: fun test_jmanson_thesis_fig_8_1() {

    // IGNORED: fun test_jmanson_thesis_fig_8_2() {

    // IGNORED: fun test_jmanson_thesis_fig_8_3() {

    // ==================== jcstress-java5 ====================

    // RESULT: Sometimes
    @Test
    fun test_jcstress_advanced_15_volatiles_are_not_fences() {
        val expectedOutcomes: Set<Triple<Int, Int, Int>> = setOf(Triple(1, 0, 0))
        litmusTest(assertSometimes(expectedOutcomes), MemoryModel.ReleaseAcquire) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            val b = AtomicInteger(0)
            var t1_r1 = 0
            var t1_r2 = 0
            var t1_r3 = 0
            val t0 = thread {
                x.setPlain(1)
                b.set(1)
                y.setPlain(1)
            }
            val t1 = thread {
                t1_r1 = y.getPlain()
                t1_r2 = b.get()
                t1_r3 = x.getPlain()
            }
            t0.join()
            t1.join()
            Triple(t1_r1, t1_r2, t1_r3)
        }
    }

    // RESULT: Sometimes
    @Test
    fun test_jcstress_basics_05_coherence_same_read_plain_0_0() {
        val expectedOutcomes: Set<Pair<Int, Int>> = setOf(0 to 0)
        litmusTest(assertSometimes(expectedOutcomes), MemoryModel.ReleaseAcquire) {
            val a = AtomicInteger(0)
            var t1_r1 = 0
            var t1_r2 = 0
            val t0 = thread {
                a.setPlain(1)
            }
            val t1 = thread {
                t1_r1 = a.getPlain()
                t1_r2 = a.getPlain()
            }
            t0.join()
            t1.join()
            t1_r1 to t1_r2
        }
    }

    // RESULT: Sometimes
    @Test
    fun test_jcstress_basics_05_coherence_same_read_plain_0_1() {
        val expectedOutcomes: Set<Pair<Int, Int>> = setOf(0 to 1)
        litmusTest(assertSometimes(expectedOutcomes), MemoryModel.ReleaseAcquire) {
            val a = AtomicInteger(0)
            var t1_r1 = 0
            var t1_r2 = 0
            val t0 = thread {
                a.setPlain(1)
            }
            val t1 = thread {
                t1_r1 = a.getPlain()
                t1_r2 = a.getPlain()
            }
            t0.join()
            t1.join()
            t1_r1 to t1_r2
        }
    }

    // RESULT: Sometimes
    // TODO: In the future we need to ignore coherence for plain access!
    @Ignore // TODO: PLAIN COHERENCE
    @Test
    fun test_jcstress_basics_05_coherence_same_read_plain_1_0() {
        val expectedOutcomes: Set<Pair<Int, Int>> = setOf(1 to 0)
        litmusTest(assertSometimes(expectedOutcomes), MemoryModel.ReleaseAcquire) {
            val a = AtomicInteger(0)
            var t1_r1 = 0
            var t1_r2 = 0
            val t0 = thread {
                a.setPlain(1)
            }
            val t1 = thread {
                t1_r1 = a.getPlain()
                t1_r2 = a.getPlain()
            }
            t0.join()
            t1.join()
            t1_r1 to t1_r2
        }
    }

    // RESULT: Sometimes
    @Test
    fun test_jcstress_basics_05_coherence_same_read_plain_1_1() {
        val expectedOutcomes: Set<Pair<Int, Int>> = setOf(1 to 1)
        litmusTest(assertSometimes(expectedOutcomes), MemoryModel.ReleaseAcquire) {
            val a = AtomicInteger(0)
            var t1_r1 = 0
            var t1_r2 = 0
            val t0 = thread {
                a.setPlain(1)
            }
            val t1 = thread {
                t1_r1 = a.getPlain()
                t1_r2 = a.getPlain()
            }
            t0.join()
            t1.join()
            t1_r1 to t1_r2
        }
    }

    // RESULT: Sometimes
    @Test
    fun test_jcstress_basics_05_coherence_same_read_volatile_0_0() {
        val expectedOutcomes: Set<Pair<Int, Int>> = setOf(0 to 0)
        litmusTest(assertSometimes(expectedOutcomes), MemoryModel.ReleaseAcquire) {
            val a = AtomicInteger(0)
            var t1_r1 = 0
            var t1_r2 = 0
            val t0 = thread {
                a.set(1)
            }
            val t1 = thread {
                t1_r1 = a.get()
                t1_r2 = a.get()
            }
            t0.join()
            t1.join()
            t1_r1 to t1_r2
        }
    }

    // RESULT: Sometimes
    @Test
    fun test_jcstress_basics_05_coherence_same_read_volatile_0_1() {
        val expectedOutcomes: Set<Pair<Int, Int>> = setOf(0 to 1)
        litmusTest(assertSometimes(expectedOutcomes), MemoryModel.ReleaseAcquire) {
            val a = AtomicInteger(0)
            var t1_r1 = 0
            var t1_r2 = 0
            val t0 = thread {
                a.set(1)
            }
            val t1 = thread {
                t1_r1 = a.get()
                t1_r2 = a.get()
            }
            t0.join()
            t1.join()
            t1_r1 to t1_r2
        }
    }

    // RESULT: Never
    @Test
    fun test_jcstress_basics_05_coherence_same_read_volatile_1_0() {
        val forbiddenOutcomes: Set<Pair<Int, Int>> = setOf(1 to 0)
        litmusTest(assertNever(forbiddenOutcomes), MemoryModel.ReleaseAcquire) {
            val a = AtomicInteger(0)
            var t1_r1 = 0
            var t1_r2 = 0
            val t0 = thread {
                a.set(1)
            }
            val t1 = thread {
                t1_r1 = a.get()
                t1_r2 = a.get()
            }
            t0.join()
            t1.join()
            t1_r1 to t1_r2
        }
    }

    // RESULT: Sometimes
    @Test
    fun test_jcstress_basics_05_coherence_same_read_volatile_1_1() {
        val expectedOutcomes: Set<Pair<Int, Int>> = setOf(1 to 1)
        litmusTest(assertSometimes(expectedOutcomes), MemoryModel.ReleaseAcquire) {
            val a = AtomicInteger(0)
            var t1_r1 = 0
            var t1_r2 = 0
            val t0 = thread {
                a.set(1)
            }
            val t1 = thread {
                t1_r1 = a.get()
                t1_r2 = a.get()
            }
            t0.join()
            t1.join()
            t1_r1 to t1_r2
        }
    }

    // RESULT: Sometimes
    @Test
    fun test_jcstress_basics_06_causality_message_passing_plain_0_0() {
        val expectedOutcomes: Set<Pair<Int, Int>> = setOf(0 to 0)
        litmusTest(assertSometimes(expectedOutcomes), MemoryModel.ReleaseAcquire) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var t1_r1 = 0
            var t1_r2 = 0
            val t0 = thread {
                x.setPlain(1)
                y.setPlain(1)
            }
            val t1 = thread {
                t1_r1 = y.getPlain()
                t1_r2 = x.getPlain()
            }
            t0.join()
            t1.join()
            t1_r1 to t1_r2
        }
    }

    // RESULT: Sometimes
    @Test
    fun test_jcstress_basics_06_causality_message_passing_plain_0_1() {
        val expectedOutcomes: Set<Pair<Int, Int>> = setOf(0 to 1)
        litmusTest(assertSometimes(expectedOutcomes), MemoryModel.ReleaseAcquire) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var t1_r1 = 0
            var t1_r2 = 0
            val t0 = thread {
                x.setPlain(1)
                y.setPlain(1)
            }
            val t1 = thread {
                t1_r1 = y.getPlain()
                t1_r2 = x.getPlain()
            }
            t0.join()
            t1.join()
            t1_r1 to t1_r2
        }
    }

    // RESULT: Sometimes
    @Test
    fun test_jcstress_basics_06_causality_message_passing_plain_1_0() {
        val expectedOutcomes: Set<Pair<Int, Int>> = setOf(1 to 0)
        litmusTest(assertSometimes(expectedOutcomes), MemoryModel.ReleaseAcquire) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var t1_r1 = 0
            var t1_r2 = 0
            val t0 = thread {
                x.setPlain(1)
                y.setPlain(1)
            }
            val t1 = thread {
                t1_r1 = y.getPlain()
                t1_r2 = x.getPlain()
            }
            t0.join()
            t1.join()
            t1_r1 to t1_r2
        }
    }

    // RESULT: Sometimes
    @Test
    fun test_jcstress_basics_06_causality_message_passing_plain_1_1() {
        val expectedOutcomes: Set<Pair<Int, Int>> = setOf(1 to 1)
        litmusTest(assertSometimes(expectedOutcomes), MemoryModel.ReleaseAcquire) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var t1_r1 = 0
            var t1_r2 = 0
            val t0 = thread {
                x.setPlain(1)
                y.setPlain(1)
            }
            val t1 = thread {
                t1_r1 = y.getPlain()
                t1_r2 = x.getPlain()
            }
            t0.join()
            t1.join()
            t1_r1 to t1_r2
        }
    }

    // RESULT: Sometimes
    @Test
    fun test_jcstress_basics_06_causality_message_passing_volatile_0_0() {
        val expectedOutcomes: Set<Pair<Int, Int>> = setOf(0 to 0)
        litmusTest(assertSometimes(expectedOutcomes), MemoryModel.ReleaseAcquire) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var t1_r1 = 0
            var t1_r2 = 0
            val t0 = thread {
                x.set(1)
                y.set(1)
            }
            val t1 = thread {
                t1_r1 = y.get()
                t1_r2 = x.get()
            }
            t0.join()
            t1.join()
            t1_r1 to t1_r2
        }
    }

    // RESULT: Sometimes
    @Test
    fun test_jcstress_basics_06_causality_message_passing_volatile_0_1() {
        val expectedOutcomes: Set<Pair<Int, Int>> = setOf(0 to 1)
        litmusTest(assertSometimes(expectedOutcomes), MemoryModel.ReleaseAcquire) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var t1_r1 = 0
            var t1_r2 = 0
            val t0 = thread {
                x.set(1)
                y.set(1)
            }
            val t1 = thread {
                t1_r1 = y.get()
                t1_r2 = x.get()
            }
            t0.join()
            t1.join()
            t1_r1 to t1_r2
        }
    }

    // RESULT: Never
    @Test
    fun test_jcstress_basics_06_causality_message_passing_volatile_1_0() {
        val forbiddenOutcomes: Set<Pair<Int, Int>> = setOf(1 to 0)
        litmusTest(assertNever(forbiddenOutcomes), MemoryModel.ReleaseAcquire) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var t1_r1 = 0
            var t1_r2 = 0
            val t0 = thread {
                x.set(1)
                y.set(1)
            }
            val t1 = thread {
                t1_r1 = y.get()
                t1_r2 = x.get()
            }
            t0.join()
            t1.join()
            t1_r1 to t1_r2
        }
    }

    // RESULT: Sometimes
    @Test
    fun test_jcstress_basics_06_causality_message_passing_volatile_1_1() {
        val expectedOutcomes: Set<Pair<Int, Int>> = setOf(1 to 1)
        litmusTest(assertSometimes(expectedOutcomes), MemoryModel.ReleaseAcquire) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var t1_r1 = 0
            var t1_r2 = 0
            val t0 = thread {
                x.set(1)
                y.set(1)
            }
            val t1 = thread {
                t1_r1 = y.get()
                t1_r2 = x.get()
            }
            t0.join()
            t1.join()
            t1_r1 to t1_r2
        }
    }

    // RESULT: Sometimes
    @Test
    fun test_jcstress_basics_07_consensus_dekker_plain_0_0() {
        val expectedOutcomes: Set<Pair<Int, Int>> = setOf(0 to 0)
        litmusTest(assertSometimes(expectedOutcomes), MemoryModel.ReleaseAcquire) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var t0_r1 = 0
            var t1_r2 = 0
            val t0 = thread {
                x.setPlain(1)
                t0_r1 = y.getPlain()
            }
            val t1 = thread {
                y.setPlain(1)
                t1_r2 = x.getPlain()
            }
            t0.join()
            t1.join()
            t0_r1 to t1_r2
        }
    }

    // RESULT: Sometimes
    @Test
    fun test_jcstress_basics_07_consensus_dekker_plain_0_1() {
        val expectedOutcomes: Set<Pair<Int, Int>> = setOf(0 to 1)
        litmusTest(assertSometimes(expectedOutcomes), MemoryModel.ReleaseAcquire) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var t0_r1 = 0
            var t1_r2 = 0
            val t0 = thread {
                x.setPlain(1)
                t0_r1 = y.getPlain()
            }
            val t1 = thread {
                y.setPlain(1)
                t1_r2 = x.getPlain()
            }
            t0.join()
            t1.join()
            t0_r1 to t1_r2
        }
    }

    // RESULT: Sometimes
    @Test
    fun test_jcstress_basics_07_consensus_dekker_plain_1_0() {
        val expectedOutcomes: Set<Pair<Int, Int>> = setOf(1 to 0)
        litmusTest(assertSometimes(expectedOutcomes), MemoryModel.ReleaseAcquire) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var t0_r1 = 0
            var t1_r2 = 0
            val t0 = thread {
                x.setPlain(1)
                t0_r1 = y.getPlain()
            }
            val t1 = thread {
                y.setPlain(1)
                t1_r2 = x.getPlain()
            }
            t0.join()
            t1.join()
            t0_r1 to t1_r2
        }
    }

    // RESULT: Sometimes
    @Test
    fun test_jcstress_basics_07_consensus_dekker_plain_1_1() {
        val expectedOutcomes: Set<Pair<Int, Int>> = setOf(1 to 1)
        litmusTest(assertSometimes(expectedOutcomes), MemoryModel.ReleaseAcquire) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var t0_r1 = 0
            var t1_r2 = 0
            val t0 = thread {
                x.setPlain(1)
                t0_r1 = y.getPlain()
            }
            val t1 = thread {
                y.setPlain(1)
                t1_r2 = x.getPlain()
            }
            t0.join()
            t1.join()
            t0_r1 to t1_r2
        }
    }

    // RESULT: Never
    @Ignore // TODO: ADD SC
    @Test
    fun test_jcstress_basics_07_consensus_dekker_volatile_0_0() {
        val forbiddenOutcomes: Set<Pair<Int, Int>> = setOf(0 to 0)
        litmusTest(assertNever(forbiddenOutcomes), MemoryModel.ReleaseAcquire) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var t0_r1 = 0
            var t1_r2 = 0
            val t0 = thread {
                x.set(1)
                t0_r1 = y.get()
            }
            val t1 = thread {
                y.set(1)
                t1_r2 = x.get()
            }
            t0.join()
            t1.join()
            t0_r1 to t1_r2
        }
    }

    // RESULT: Sometimes
    @Test
    fun test_jcstress_basics_07_consensus_dekker_volatile_0_1() {
        val expectedOutcomes: Set<Pair<Int, Int>> = setOf(0 to 1)
        litmusTest(assertSometimes(expectedOutcomes), MemoryModel.ReleaseAcquire) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var t0_r1 = 0
            var t1_r2 = 0
            val t0 = thread {
                x.set(1)
                t0_r1 = y.get()
            }
            val t1 = thread {
                y.set(1)
                t1_r2 = x.get()
            }
            t0.join()
            t1.join()
            t0_r1 to t1_r2
        }
    }

    // RESULT: Sometimes
    @Test
    fun test_jcstress_basics_07_consensus_dekker_volatile_1_0() {
        val expectedOutcomes: Set<Pair<Int, Int>> = setOf(1 to 0)
        litmusTest(assertSometimes(expectedOutcomes), MemoryModel.ReleaseAcquire) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var t0_r1 = 0
            var t1_r2 = 0
            val t0 = thread {
                x.set(1)
                t0_r1 = y.get()
            }
            val t1 = thread {
                y.set(1)
                t1_r2 = x.get()
            }
            t0.join()
            t1.join()
            t0_r1 to t1_r2
        }
    }

    // RESULT: Sometimes
    @Test
    fun test_jcstress_basics_07_consensus_dekker_volatile_1_1() {
        val expectedOutcomes: Set<Pair<Int, Int>> = setOf(1 to 1)
        litmusTest(assertSometimes(expectedOutcomes), MemoryModel.ReleaseAcquire) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var t0_r1 = 0
            var t1_r2 = 0
            val t0 = thread {
                x.set(1)
                t0_r1 = y.get()
            }
            val t1 = thread {
                y.set(1)
                t1_r2 = x.get()
            }
            t0.join()
            t1.join()
            t0_r1 to t1_r2
        }
    }

    // RESULT: Sometimes
    @Test
    fun test_jcstress_rmw_09_gas_effects_1_cts_cts() {
        val expectedOutcomes: Set<Pair<Int, Int>> = setOf(0 to 0)
        litmusTest(assertSometimes(expectedOutcomes), MemoryModel.ReleaseAcquire) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var t0_r1 = 0
            var t1_r2 = 0
            val t0 = thread {
                x.setPlain(1)
                var t = y.get()
                if (t == 1) {
                    y.set(0)
                }
                t0_r1 = t
            }
            val t1 = thread {
                var t = y.get()
                if (t == 0) {
                    y.set(1)
                }
                t1_r2 = x.getPlain()
            }
            t0.join()
            t1.join()
            t0_r1 to t1_r2
        }
    }

    // RESULT: Sometimes
    @Test
    fun test_jcstress_UnobservedVolatileBarrierTest() {
        val expectedOutcomes: Set<Triple<Int, Int, Int>> = setOf(Triple(1, 0, 0))
        litmusTest(assertSometimes(expectedOutcomes), MemoryModel.ReleaseAcquire) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            val v = AtomicInteger(0)
            var t1_r1 = 0
            var t1_r2 = 0
            var t1_r3 = 0
            val t0 = thread {
                x.setPlain(1)
                v.set(1)
                y.setPlain(1)
            }
            val t1 = thread {
                t1_r1 = y.getPlain()
                t1_r2 = v.get()
                t1_r3 = x.getPlain()
            }
            t0.join()
            t1.join()
            Triple(t1_r1, t1_r2, t1_r3)
        }
    }

    // ==================== jcstress-varhandle ====================

    // RESULT: Sometimes
    // TODO: fences are not supported by the event structure strategy
    @Ignore
    @Test
    fun test_jcstress_advanced_02_multi_copy_atomic_fenced() {
        val expectedOutcomes: Set<List<Int>> = setOf(listOf(1, 0, 1, 0))
        litmusTest(assertSometimes(expectedOutcomes), MemoryModel.ReleaseAcquire) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var t2_r1 = 0
            var t2_r2 = 0
            var t3_r3 = 0
            var t3_r4 = 0
            val t0 = thread {
                VarHandle.fullFence()
                x.setPlain(1)
            }
            val t1 = thread {
                VarHandle.fullFence()
                y.setPlain(1)
            }
            val t2 = thread {
                t2_r1 = x.getPlain()
                VarHandle.acquireFence()
                t2_r2 = y.getPlain()
                VarHandle.acquireFence()
            }
            val t3 = thread {
                t3_r3 = y.getPlain()
                VarHandle.acquireFence()
                t3_r4 = x.getPlain()
                VarHandle.acquireFence()
            }
            t0.join()
            t1.join()
            t2.join()
            t3.join()
            listOf(t2_r1, t2_r2, t3_r3, t3_r4)
        }
    }

    // RESULT: Never
    // TODO: fences are not supported by the event structure strategy
    @Ignore
    @Test
    fun test_jcstress_advanced_02_multi_copy_atomic_fully_fenced() {
        val forbiddenOutcomes: Set<List<Int>> = setOf(listOf(1, 0, 1, 0))
        litmusTest(assertNever(forbiddenOutcomes), MemoryModel.ReleaseAcquire) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var t2_r1 = 0
            var t2_r2 = 0
            var t3_r3 = 0
            var t3_r4 = 0
            val t0 = thread {
                VarHandle.fullFence()
                x.setPlain(1)
            }
            val t1 = thread {
                VarHandle.fullFence()
                y.setPlain(1)
            }
            val t2 = thread {
                VarHandle.fullFence()
                t2_r1 = x.getPlain()
                VarHandle.fullFence()
                t2_r2 = y.getPlain()
                VarHandle.acquireFence()
            }
            val t3 = thread {
                VarHandle.fullFence()
                t3_r3 = y.getPlain()
                VarHandle.fullFence()
                t3_r4 = x.getPlain()
                VarHandle.acquireFence()
            }
            t0.join()
            t1.join()
            t2.join()
            t3.join()
            listOf(t2_r1, t2_r2, t3_r3, t3_r4)
        }
    }

    // RESULT: Sometimes
    @Test
    fun test_jcstress_advanced_02_multi_copy_atomic_opaque() {
        val expectedOutcomes: Set<List<Int>> = setOf(listOf(1, 0, 1, 0))
        litmusTest(assertSometimes(expectedOutcomes), MemoryModel.ReleaseAcquire) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var t2_r1 = 0
            var t2_r2 = 0
            var t3_r3 = 0
            var t3_r4 = 0
            val t0 = thread {
                x.setOpaque(1)
            }
            val t1 = thread {
                y.setOpaque(1)
            }
            val t2 = thread {
                t2_r1 = x.getOpaque()
                t2_r2 = y.getOpaque()
            }
            val t3 = thread {
                t3_r3 = y.getOpaque()
                t3_r4 = x.getOpaque()
            }
            t0.join()
            t1.join()
            t2.join()
            t3.join()
            listOf(t2_r1, t2_r2, t3_r3, t3_r4)
        }
    }

    // RESULT: Never
    @Test
    fun test_jcstress_advanced_03_non_mca_coherence_1221() {
        val forbiddenOutcomes: Set<List<Int>> = setOf(listOf(1, 2, 2, 1))
        litmusTest(assertNever(forbiddenOutcomes), MemoryModel.ReleaseAcquire) {
            val x = AtomicInteger(0)
            var t2_r1 = 0
            var t2_r2 = 0
            var t3_r3 = 0
            var t3_r4 = 0
            val t0 = thread {
                x.setOpaque(1)
            }
            val t1 = thread {
                x.setOpaque(2)
            }
            val t2 = thread {
                t2_r1 = x.getOpaque()
                t2_r2 = x.getOpaque()
            }
            val t3 = thread {
                t3_r3 = x.getOpaque()
                t3_r4 = x.getOpaque()
            }
            t0.join()
            t1.join()
            t2.join()
            t3.join()
            listOf(t2_r1, t2_r2, t3_r3, t3_r4)
        }
    }

    // RESULT: Never
    @Test
    fun test_jcstress_advanced_03_non_mca_coherence_2112() {
        val forbiddenOutcomes: Set<List<Int>> = setOf(listOf(2, 1, 1, 2))
        litmusTest(assertNever(forbiddenOutcomes), MemoryModel.ReleaseAcquire) {
            val x = AtomicInteger(0)
            var t2_r1 = 0
            var t2_r2 = 0
            var t3_r3 = 0
            var t3_r4 = 0
            val t0 = thread {
                x.setOpaque(1)
            }
            val t1 = thread {
                x.setOpaque(2)
            }
            val t2 = thread {
                t2_r1 = x.getOpaque()
                t2_r2 = x.getOpaque()
            }
            val t3 = thread {
                t3_r3 = x.getOpaque()
                t3_r4 = x.getOpaque()
            }
            t0.join()
            t1.join()
            t2.join()
            t3.join()
            listOf(t2_r1, t2_r2, t3_r3, t3_r4)
        }
    }

    // RESULT: Sometimes
    @Test
    fun test_jcstress_basics_05_coherence_same_read_opaque_0_0() {
        val expectedOutcomes: Set<Pair<Int, Int>> = setOf(0 to 0)
        litmusTest(assertSometimes(expectedOutcomes), MemoryModel.ReleaseAcquire) {
            val a = AtomicInteger(0)
            var t1_r1 = 0
            var t1_r2 = 0
            val t0 = thread {
                a.setOpaque(1)
            }
            val t1 = thread {
                t1_r1 = a.getOpaque()
                t1_r2 = a.getOpaque()
            }
            t0.join()
            t1.join()
            t1_r1 to t1_r2
        }
    }

    // RESULT: Sometimes
    @Test
    fun test_jcstress_basics_05_coherence_same_read_opaque_0_1() {
        val expectedOutcomes: Set<Pair<Int, Int>> = setOf(0 to 1)
        litmusTest(assertSometimes(expectedOutcomes), MemoryModel.ReleaseAcquire) {
            val a = AtomicInteger(0)
            var t1_r1 = 0
            var t1_r2 = 0
            val t0 = thread {
                a.setOpaque(1)
            }
            val t1 = thread {
                t1_r1 = a.getOpaque()
                t1_r2 = a.getOpaque()
            }
            t0.join()
            t1.join()
            t1_r1 to t1_r2
        }
    }

    // RESULT: Never
    @Test
    fun test_jcstress_basics_05_coherence_same_read_opaque_1_0() {
        val forbiddenOutcomes: Set<Pair<Int, Int>> = setOf(1 to 0)
        litmusTest(assertNever(forbiddenOutcomes), MemoryModel.ReleaseAcquire) {
            val a = AtomicInteger(0)
            var t1_r1 = 0
            var t1_r2 = 0
            val t0 = thread {
                a.setOpaque(1)
            }
            val t1 = thread {
                t1_r1 = a.getOpaque()
                t1_r2 = a.getOpaque()
            }
            t0.join()
            t1.join()
            t1_r1 to t1_r2
        }
    }

    // RESULT: Sometimes
    @Test
    fun test_jcstress_basics_05_coherence_same_read_opaque_1_1() {
        val expectedOutcomes: Set<Pair<Int, Int>> = setOf(1 to 1)
        litmusTest(assertSometimes(expectedOutcomes), MemoryModel.ReleaseAcquire) {
            val a = AtomicInteger(0)
            var t1_r1 = 0
            var t1_r2 = 0
            val t0 = thread {
                a.setOpaque(1)
            }
            val t1 = thread {
                t1_r1 = a.getOpaque()
                t1_r2 = a.getOpaque()
            }
            t0.join()
            t1.join()
            t1_r1 to t1_r2
        }
    }

    // RESULT: Sometimes
    @Test
    fun test_jcstress_basics_06_causality_message_passing_acqrel_0_0() {
        val expectedOutcomes: Set<Pair<Int, Int>> = setOf(0 to 0)
        litmusTest(assertSometimes(expectedOutcomes), MemoryModel.ReleaseAcquire) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var t1_r1 = 0
            var t1_r2 = 0
            val t0 = thread {
                x.setPlain(1)
                y.setRelease(1)
            }
            val t1 = thread {
                t1_r1 = y.getAcquire()
                t1_r2 = x.getPlain()
            }
            t0.join()
            t1.join()
            t1_r1 to t1_r2
        }
    }

    // RESULT: Sometimes
    @Test
    fun test_jcstress_basics_06_causality_message_passing_acqrel_0_1() {
        val expectedOutcomes: Set<Pair<Int, Int>> = setOf(0 to 1)
        litmusTest(assertSometimes(expectedOutcomes), MemoryModel.ReleaseAcquire) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var t1_r1 = 0
            var t1_r2 = 0
            val t0 = thread {
                x.setPlain(1)
                y.setRelease(1)
            }
            val t1 = thread {
                t1_r1 = y.getAcquire()
                t1_r2 = x.getPlain()
            }
            t0.join()
            t1.join()
            t1_r1 to t1_r2
        }
    }

    // RESULT: Never
    @Test
    fun test_jcstress_basics_06_causality_message_passing_acqrel_1_0() {
        val forbiddenOutcomes: Set<Pair<Int, Int>> = setOf(1 to 0)
        litmusTest(assertNever(forbiddenOutcomes), MemoryModel.ReleaseAcquire) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var t1_r1 = 0
            var t1_r2 = 0
            val t0 = thread {
                x.setPlain(1)
                y.setRelease(1)
            }
            val t1 = thread {
                t1_r1 = y.getAcquire()
                t1_r2 = x.getPlain()
            }
            t0.join()
            t1.join()
            t1_r1 to t1_r2
        }
    }

    // RESULT: Sometimes
    @Test
    fun test_jcstress_basics_06_causality_message_passing_acqrel_1_1() {
        val expectedOutcomes: Set<Pair<Int, Int>> = setOf(1 to 1)
        litmusTest(assertSometimes(expectedOutcomes), MemoryModel.ReleaseAcquire) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var t1_r1 = 0
            var t1_r2 = 0
            val t0 = thread {
                x.setPlain(1)
                y.setRelease(1)
            }
            val t1 = thread {
                t1_r1 = y.getAcquire()
                t1_r2 = x.getPlain()
            }
            t0.join()
            t1.join()
            t1_r1 to t1_r2
        }
    }

    // RESULT: Sometimes
    @Test
    fun test_jcstress_basics_06_causality_message_passing_opaque_0_0() {
        val expectedOutcomes: Set<Pair<Int, Int>> = setOf(0 to 0)
        litmusTest(assertSometimes(expectedOutcomes), MemoryModel.ReleaseAcquire) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var t1_r1 = 0
            var t1_r2 = 0
            val t0 = thread {
                x.setOpaque(1)
                y.setOpaque(1)
            }
            val t1 = thread {
                t1_r1 = y.getOpaque()
                t1_r2 = x.getOpaque()
            }
            t0.join()
            t1.join()
            t1_r1 to t1_r2
        }
    }

    // RESULT: Sometimes
    @Test
    fun test_jcstress_basics_06_causality_message_passing_opaque_0_1() {
        val expectedOutcomes: Set<Pair<Int, Int>> = setOf(0 to 1)
        litmusTest(assertSometimes(expectedOutcomes), MemoryModel.ReleaseAcquire) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var t1_r1 = 0
            var t1_r2 = 0
            val t0 = thread {
                x.setOpaque(1)
                y.setOpaque(1)
            }
            val t1 = thread {
                t1_r1 = y.getOpaque()
                t1_r2 = x.getOpaque()
            }
            t0.join()
            t1.join()
            t1_r1 to t1_r2
        }
    }

    // RESULT: Sometimes
    @Test
    fun test_jcstress_basics_06_causality_message_passing_opaque_1_0() {
        val expectedOutcomes: Set<Pair<Int, Int>> = setOf(1 to 0)
        litmusTest(assertSometimes(expectedOutcomes), MemoryModel.ReleaseAcquire) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var t1_r1 = 0
            var t1_r2 = 0
            val t0 = thread {
                x.setOpaque(1)
                y.setOpaque(1)
            }
            val t1 = thread {
                t1_r1 = y.getOpaque()
                t1_r2 = x.getOpaque()
            }
            t0.join()
            t1.join()
            t1_r1 to t1_r2
        }
    }

    // RESULT: Sometimes
    @Test
    fun test_jcstress_basics_06_causality_message_passing_opaque_1_1() {
        val expectedOutcomes: Set<Pair<Int, Int>> = setOf(1 to 1)
        litmusTest(assertSometimes(expectedOutcomes), MemoryModel.ReleaseAcquire) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var t1_r1 = 0
            var t1_r2 = 0
            val t0 = thread {
                x.setOpaque(1)
                y.setOpaque(1)
            }
            val t1 = thread {
                t1_r1 = y.getOpaque()
                t1_r2 = x.getOpaque()
            }
            t0.join()
            t1.join()
            t1_r1 to t1_r2
        }
    }

    // RESULT: Sometimes
    @Test
    fun test_jcstress_basics_07_consensus_dekker_acqrel_0_0() {
        val expectedOutcomes: Set<Pair<Int, Int>> = setOf(0 to 0)
        litmusTest(assertSometimes(expectedOutcomes), MemoryModel.ReleaseAcquire) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var t0_r1 = 0
            var t1_r2 = 0
            val t0 = thread {
                x.setRelease(1)
                t0_r1 = y.getAcquire()
            }
            val t1 = thread {
                y.setRelease(1)
                t1_r2 = x.getAcquire()
            }
            t0.join()
            t1.join()
            t0_r1 to t1_r2
        }
    }

    // RESULT: Sometimes
    @Test
    fun test_jcstress_basics_07_consensus_dekker_acqrel_0_1() {
        val expectedOutcomes: Set<Pair<Int, Int>> = setOf(0 to 1)
        litmusTest(assertSometimes(expectedOutcomes), MemoryModel.ReleaseAcquire) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var t0_r1 = 0
            var t1_r2 = 0
            val t0 = thread {
                x.setRelease(1)
                t0_r1 = y.getAcquire()
            }
            val t1 = thread {
                y.setRelease(1)
                t1_r2 = x.getAcquire()
            }
            t0.join()
            t1.join()
            t0_r1 to t1_r2
        }
    }

    // RESULT: Sometimes
    @Test
    fun test_jcstress_basics_07_consensus_dekker_acqrel_1_0() {
        val expectedOutcomes: Set<Pair<Int, Int>> = setOf(1 to 0)
        litmusTest(assertSometimes(expectedOutcomes), MemoryModel.ReleaseAcquire) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var t0_r1 = 0
            var t1_r2 = 0
            val t0 = thread {
                x.setRelease(1)
                t0_r1 = y.getAcquire()
            }
            val t1 = thread {
                y.setRelease(1)
                t1_r2 = x.getAcquire()
            }
            t0.join()
            t1.join()
            t0_r1 to t1_r2
        }
    }

    // RESULT: Sometimes
    @Test
    fun test_jcstress_basics_07_consensus_dekker_acqrel_1_1() {
        val expectedOutcomes: Set<Pair<Int, Int>> = setOf(1 to 1)
        litmusTest(assertSometimes(expectedOutcomes), MemoryModel.ReleaseAcquire) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var t0_r1 = 0
            var t1_r2 = 0
            val t0 = thread {
                x.setRelease(1)
                t0_r1 = y.getAcquire()
            }
            val t1 = thread {
                y.setRelease(1)
                t1_r2 = x.getAcquire()
            }
            t0.join()
            t1.join()
            t0_r1 to t1_r2
        }
    }

    // RESULT: Sometimes
    @Test
    fun test_jcstress_basics_07_consensus_dekker_opaque_0_0() {
        val expectedOutcomes: Set<Pair<Int, Int>> = setOf(0 to 0)
        litmusTest(assertSometimes(expectedOutcomes), MemoryModel.ReleaseAcquire) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var t0_r1 = 0
            var t1_r2 = 0
            val t0 = thread {
                x.setOpaque(1)
                t0_r1 = y.getOpaque()
            }
            val t1 = thread {
                y.setOpaque(1)
                t1_r2 = x.getOpaque()
            }
            t0.join()
            t1.join()
            t0_r1 to t1_r2
        }
    }

    // RESULT: Sometimes
    @Test
    fun test_jcstress_basics_07_consensus_dekker_opaque_0_1() {
        val expectedOutcomes: Set<Pair<Int, Int>> = setOf(0 to 1)
        litmusTest(assertSometimes(expectedOutcomes), MemoryModel.ReleaseAcquire) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var t0_r1 = 0
            var t1_r2 = 0
            val t0 = thread {
                x.setOpaque(1)
                t0_r1 = y.getOpaque()
            }
            val t1 = thread {
                y.setOpaque(1)
                t1_r2 = x.getOpaque()
            }
            t0.join()
            t1.join()
            t0_r1 to t1_r2
        }
    }

    // RESULT: Sometimes
    @Test
    fun test_jcstress_basics_07_consensus_dekker_opaque_1_0() {
        val expectedOutcomes: Set<Pair<Int, Int>> = setOf(1 to 0)
        litmusTest(assertSometimes(expectedOutcomes), MemoryModel.ReleaseAcquire) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var t0_r1 = 0
            var t1_r2 = 0
            val t0 = thread {
                x.setOpaque(1)
                t0_r1 = y.getOpaque()
            }
            val t1 = thread {
                y.setOpaque(1)
                t1_r2 = x.getOpaque()
            }
            t0.join()
            t1.join()
            t0_r1 to t1_r2
        }
    }

    // RESULT: Sometimes
    @Test
    fun test_jcstress_basics_07_consensus_dekker_opaque_1_1() {
        val expectedOutcomes: Set<Pair<Int, Int>> = setOf(1 to 1)
        litmusTest(assertSometimes(expectedOutcomes), MemoryModel.ReleaseAcquire) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var t0_r1 = 0
            var t1_r2 = 0
            val t0 = thread {
                x.setOpaque(1)
                t0_r1 = y.getOpaque()
            }
            val t1 = thread {
                y.setOpaque(1)
                t1_r2 = x.getOpaque()
            }
            t0.join()
            t1.join()
            t0_r1 to t1_r2
        }
    }

    // RESULT: Sometimes
    @Test
    fun test_jcstress_rmw_09_gas_effects_2_cas_cas() {
        val expectedOutcomes: Set<Pair<Int, Int>> = setOf(0 to 0)
        litmusTest(assertSometimes(expectedOutcomes), MemoryModel.ReleaseAcquire) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var t0_r1 = 0
            var t1_r2 = 0
            val t0 = thread {
                x.setPlain(1)
                t0_r1 = if (y.compareAndSet(1, 0)) 1 else 0
            }
            val t1 = thread {
                var r3 = y.compareAndSet(0, 1)
                t1_r2 = x.getPlain()
            }
            t0.join()
            t1.join()
            t0_r1 to t1_r2
        }
    }

    // RESULT: Sometimes
    @Test
    fun test_jcstress_rmw_09_gas_effects_3_gts_cas() {
        val expectedOutcomes: Set<Pair<Int, Int>> = setOf(0 to 0)
        litmusTest(assertSometimes(expectedOutcomes), MemoryModel.ReleaseAcquire) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var t0_r1 = 0
            var t1_r2 = 0
            val t0 = thread {
                x.setPlain(1)
                var t = y.get()
                y.set(0)
                t0_r1 = t
            }
            val t1 = thread {
                var r3 = y.compareAndSet(0, 1)
                t1_r2 = x.getPlain()
            }
            t0.join()
            t1.join()
            t0_r1 to t1_r2
        }
    }

    // RESULT: Never
    @Test
    fun test_jcstress_rmw_09_gas_effects_4_gas_cas() {
        val forbiddenOutcomes: Set<Pair<Int, Int>> = setOf(0 to 0)
        litmusTest(assertNever(forbiddenOutcomes), MemoryModel.ReleaseAcquire) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var t0_r1 = 0
            var t1_r2 = 0
            val t0 = thread {
                x.setPlain(1)
                t0_r1 = y.getAndSet(0)
            }
            val t1 = thread {
                var r3 = y.compareAndSet(0, 1)
                t1_r2 = x.getPlain()
            }
            t0.join()
            t1.join()
            t0_r1 to t1_r2
        }
    }

    // ==================== standard-variations ====================

    // RESULT: Sometimes
    @Test
    fun test_2plus2W_opq() {
        val expectedOutcomes: Set<Pair<Int, Int>> = setOf(1 to 1)
        litmusTest(assertSometimes(expectedOutcomes), MemoryModel.ReleaseAcquire) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var t0_r1 = 0
            var t1_r2 = 0
            val t0 = thread {
                x.setOpaque(2)
                y.setOpaque(1)
                t0_r1 = x.getOpaque()
            }
            val t1 = thread {
                y.setOpaque(2)
                x.setOpaque(1)
                t1_r2 = y.getOpaque()
            }
            t0.join()
            t1.join()
            t0_r1 to t1_r2
        }
    }

    // RESULT: Sometimes
    @Test
    fun test_iriw_acq_acq() {
        val expectedOutcomes: Set<List<Int>> = setOf(listOf(1, 0, 1, 0))
        litmusTest(assertSometimes(expectedOutcomes), MemoryModel.ReleaseAcquire) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var t2_r1 = 0
            var t2_r2 = 0
            var t3_r3 = 0
            var t3_r4 = 0
            val t0 = thread {
                x.set(1)
            }
            val t1 = thread {
                y.set(1)
            }
            val t2 = thread {
                t2_r1 = x.getAcquire()
                t2_r2 = y.getAcquire()
            }
            val t3 = thread {
                t3_r3 = y.getAcquire()
                t3_r4 = x.getAcquire()
            }
            t0.join()
            t1.join()
            t2.join()
            t3.join()
            listOf(t2_r1, t2_r2, t3_r3, t3_r4)
        }
    }

    // RESULT: Unknown
    @Test
    fun test_iriw_acq_vol() {
        val expectedOutcomes: Set<List<Int>> = setOf(listOf(1, 0, 1, 0))
        litmusTest(assertSometimes(expectedOutcomes), MemoryModel.ReleaseAcquire) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var t2_r1 = 0
            var t2_r2 = 0
            var t3_r3 = 0
            var t3_r4 = 0
            val t0 = thread {
                x.set(1)
            }
            val t1 = thread {
                y.set(1)
            }
            val t2 = thread {
                t2_r1 = x.getAcquire()
                t2_r2 = y.get()
            }
            val t3 = thread {
                t3_r3 = y.getAcquire()
                t3_r4 = x.get()
            }
            t0.join()
            t1.join()
            t2.join()
            t3.join()
            listOf(t2_r1, t2_r2, t3_r3, t3_r4)
        }
    }

    // IGNORED_RESULT: Never
    // TODO: fences are not supported by the event structure strategy
    @Ignore
    @Test
    fun test_iriw_sensibly_fenced() {
        val forbiddenOutcomes: Set<List<Int>> = setOf(listOf(1, 0, 1, 0))
        litmusTest(assertNever(forbiddenOutcomes), MemoryModel.ReleaseAcquire) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var t2_r1 = 0
            var t2_r2 = 0
            var t3_r3 = 0
            var t3_r4 = 0
            val t0 = thread {
                x.setPlain(1)
            }
            val t1 = thread {
                y.setPlain(1)
            }
            val t2 = thread {
                t2_r1 = x.getPlain()
                VarHandle.fullFence()
                t2_r2 = y.getPlain()
            }
            val t3 = thread {
                t3_r3 = y.getPlain()
                VarHandle.fullFence()
                t3_r4 = x.getPlain()
            }
            t0.join()
            t1.join()
            t2.join()
            t3.join()
            listOf(t2_r1, t2_r2, t3_r3, t3_r4)
        }
    }

    // RESULT: Never
    @Ignore // TODO: ADD SC
    @Test
    fun test_iriw_vol() {
        val forbiddenOutcomes: Set<List<Int>> = setOf(listOf(1, 0, 1, 0))
        litmusTest(assertNever(forbiddenOutcomes), MemoryModel.ReleaseAcquire) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var t2_r1 = 0
            var t2_r2 = 0
            var t3_r3 = 0
            var t3_r4 = 0
            val t0 = thread {
                x.set(1)
            }
            val t1 = thread {
                y.set(1)
            }
            val t2 = thread {
                t2_r1 = x.get()
                t2_r2 = y.get()
            }
            val t3 = thread {
                t3_r3 = y.get()
                t3_r4 = x.get()
            }
            t0.join()
            t1.join()
            t2.join()
            t3.join()
            listOf(t2_r1, t2_r2, t3_r3, t3_r4)
        }
    }

    // RESULT: Sometimes
    @Ignore // TODO: ADD SC
    @Test
    fun test_lb_fake_fence() {
        val expectedOutcomes: Set<Pair<Int, Int>> = setOf(1 to 1)
        litmusTest(assertSometimes(expectedOutcomes), MemoryModel.ReleaseAcquire) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            val v = AtomicInteger(0)
            var t0_r1 = 0
            var t1_r3 = 0
            val t0 = thread {
                t0_r1 = x.getPlain()
                var r2 = v.get()
                v.set(t0_r1)
                y.setPlain(1)
            }
            val t1 = thread {
                t1_r3 = y.getPlain()
                var r4 = v.get()
                v.set(r4)
                x.setPlain(1)
            }
            t0.join()
            t1.join()
            t0_r1 to t1_r3
        }
    }

    // RESULT: Never
    @Test
    fun test_lb_if() {
        val forbiddenOutcomes: Set<Pair<Int, Int>> = setOf(1 to 1)
        litmusTest(assertNever(forbiddenOutcomes), MemoryModel.ReleaseAcquire) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var t0_r1 = 0
            var t1_r2 = 0
            val t0 = thread {
                t0_r1 = x.getPlain()
                if (t0_r1 != 0) {
                    y.setPlain(1)
                }
            }
            val t1 = thread {
                t1_r2 = y.getPlain()
                if (t1_r2 != 0) {
                    x.setPlain(1)
                }
            }
            t0.join()
            t1.join()
            t0_r1 to t1_r2
        }
    }

    // You should not see LB for opaque? TODO: Investigate the origins of this test case
    @Test
    fun test_lb_opq() {
        val expectedOutcomes: Set<Pair<Int, Int>> = setOf(1 to 1)
        litmusTest(assertNever(expectedOutcomes), MemoryModel.ReleaseAcquire) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var t0_r1 = 0
            var t1_r2 = 0
            val t0 = thread {
                t0_r1 = y.getOpaque()
                x.setOpaque(1)
            }
            val t1 = thread {
                t1_r2 = x.getOpaque()
                y.setOpaque(1)
            }
            t0.join()
            t1.join()
            t0_r1 to t1_r2
        }
    }

    // IGNORED: fun test_lb_plain_constant_variable() {

    // RESULT: Never
    @Test
    fun test_lb_quasi_fence() {
        val forbiddenOutcomes: Set<Pair<Int, Int>> = setOf(1 to 1)
        litmusTest(assertNever(forbiddenOutcomes), MemoryModel.ReleaseAcquire) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            val v = AtomicInteger(0)
            var t0_r1 = 0
            var t1_r3 = 0
            val t0 = thread {
                t0_r1 = x.getPlain()
                v.set(0)
                var r2 = v.get()
                y.setPlain(1)
            }
            val t1 = thread {
                t1_r3 = y.getPlain()
                v.set(0)
                var r4 = v.get()
                x.setPlain(1)
            }
            t0.join()
            t1.join()
            t0_r1 to t1_r3
        }
    }

    // RESULT: Never
    @Test
    fun test_lb_ra() {
        val forbiddenOutcomes: Set<Pair<Int, Int>> = setOf(1 to 1)
        litmusTest(assertNever(forbiddenOutcomes), MemoryModel.ReleaseAcquire) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var t0_r1 = 0
            var t1_r2 = 0
            val t0 = thread {
                t0_r1 = y.getAcquire()
                x.setRelease(1)
            }
            val t1 = thread {
                t1_r2 = x.getAcquire()
                y.setRelease(1)
            }
            t0.join()
            t1.join()
            t0_r1 to t1_r2
        }
    }

    // RESULT: Sometimes
    @Test
    fun test_mp_2_access() {
        val expectedOutcomes: Set<List<Int>> = setOf(listOf(1, 1, 1, 0))
        litmusTest(assertSometimes(expectedOutcomes), MemoryModel.ReleaseAcquire) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            val z = AtomicInteger(0)
            var t1_a = 0
            var t1_b = 0
            var t2_c = 0
            var t2_d = 0
            val t0 = thread {
                x.setPlain(1)
                y.setRelease(1)
                z.setPlain(1)
            }
            val t1 = thread {
                t1_a = y.getAcquire()
                t1_b = x.getPlain()
            }
            val t2 = thread {
                t2_c = z.getAcquire()
                t2_d = x.getPlain()
            }
            t0.join()
            t1.join()
            t2.join()
            listOf(t1_a, t1_b, t2_c, t2_d)
        }
    }

    // RESULT: Never
    // TODO: fences are not supported by the event structure strategy
    @Ignore
    @Test
    fun test_mp_2_fence() {
        val forbiddenOutcomes: Set<List<Int>> = setOf(listOf(1, 1, 1, 0))
        litmusTest(assertNever(forbiddenOutcomes), MemoryModel.ReleaseAcquire) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            val z = AtomicInteger(0)
            var t1_a = 0
            var t1_b = 0
            var t2_c = 0
            var t2_d = 0
            val t0 = thread {
                x.setPlain(1)
                VarHandle.releaseFence()
                y.setPlain(1)
                z.setPlain(1)
            }
            val t1 = thread {
                t1_a = y.getAcquire()
                t1_b = x.getPlain()
            }
            val t2 = thread {
                t2_c = z.getAcquire()
                t2_d = x.getPlain()
            }
            t0.join()
            t1.join()
            t2.join()
            listOf(t1_a, t1_b, t2_c, t2_d)
        }
    }

    // IGNORED_RESULT: Sometimes
    @Ignore
    @Test
    fun test_mp_fadd_acq() {
        val expectedOutcomes: Set<List<Int>> = setOf(listOf(1, 2, 0))
        litmusTest(assertSometimes(expectedOutcomes), MemoryModel.ReleaseAcquire) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var t1_r1 = 0
            var t2_r2 = 0
            var t2_r3 = 0
            val t0 = thread {
                x.setPlain(42)
                y.setRelease(1)
            }
            val t1 = thread {
                t1_r1 = y.getAndAdd(1)
            }
            val t2 = thread {
                t2_r2 = y.getAcquire()
                t2_r3 = x.getPlain()
            }
            t0.join()
            t1.join()
            t2.join()
            listOf(t1_r1, t2_r2, t2_r3)
        }
    }

    // IGNORED_RESULT: Sometimes
    @Ignore
    @Test
    fun test_mp_fadd_rel() {
        val expectedOutcomes: Set<List<Int>> = setOf(listOf(1, 2, 0))
        litmusTest(assertSometimes(expectedOutcomes), MemoryModel.ReleaseAcquire) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var t1_r1 = 0
            var t2_r2 = 0
            var t2_r3 = 0
            val t0 = thread {
                x.setPlain(42)
                y.setRelease(1)
            }
            val t1 = thread {
                t1_r1 = y.getAndAdd(1)
            }
            val t2 = thread {
                t2_r2 = y.getAcquire()
                t2_r3 = x.getPlain()
            }
            t0.join()
            t1.join()
            t2.join()
            listOf(t1_r1, t2_r2, t2_r3)
        }
    }

    // RESULT: Never
    @Test
    fun test_mp_fadd_vol() {
        val forbiddenOutcomes: Set<List<Int>> = setOf(listOf(1, 2, 0))
        litmusTest(assertNever(forbiddenOutcomes), MemoryModel.ReleaseAcquire) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var t1_r1 = 0
            var t2_r2 = 0
            var t2_r3 = 0
            val t0 = thread {
                x.setPlain(42)
                y.setRelease(1)
            }
            val t1 = thread {
                t1_r1 = y.getAndAdd(1)
            }
            val t2 = thread {
                t2_r2 = y.getAcquire()
                t2_r3 = x.getPlain()
            }
            t0.join()
            t1.join()
            t2.join()
            listOf(t1_r1, t2_r2, t2_r3)
        }
    }

    // RESULT: Sometimes
    @Test
    fun test_mp_fake_fence() {
        val expectedOutcomes: Set<Pair<Int, Int>> = setOf(1 to 0)
        litmusTest(assertSometimes(expectedOutcomes), MemoryModel.ReleaseAcquire) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            val v = AtomicInteger(0)
            var t1_r2 = 0
            var t1_r4 = 0
            val t0 = thread {
                x.setPlain(1)
                var r1 = v.get()
                v.set(r1)
                y.setPlain(1)
            }
            val t1 = thread {
                t1_r2 = y.getPlain()
                var r3 = v.get()
                v.set(r3)
                t1_r4 = x.getPlain()
            }
            t0.join()
            t1.join()
            t1_r2 to t1_r4
        }
    }

    // TODO: fences are not supported by the event structure strategy
    @Ignore
    @Test
    fun test_mp_fence() {
        val expectedOutcomes: Set<Triple<Int, Int, Int>> = setOf(Triple(1, 0, 0))
        litmusTest(assertSometimes(expectedOutcomes), MemoryModel.ReleaseAcquire) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            val f = AtomicInteger(0)
            var t1_r1 = 0
            var t1_r2 = 0
            var t1_r3 = 0
            val t0 = thread {
                x.setPlain(1)
                y.setPlain(1)
                VarHandle.releaseFence()
                f.setPlain(1)
            }
            val t1 = thread {
                t1_r1 = f.getPlain()
                t1_r2 = x.getPlain()
                VarHandle.acquireFence()
                t1_r3 = y.getPlain()
            }
            t0.join()
            t1.join()
            Triple(t1_r1, t1_r2, t1_r3)
        }
    }

    // RESULT: Never
    @Test
    fun test_mp_large_transfer() {
        val forbiddenOutcomes: Set<Triple<Int, Int, Int>> = setOf(Triple(1, 1, 0))
        litmusTest(assertNever(forbiddenOutcomes), MemoryModel.ReleaseAcquire) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            val f = AtomicInteger(0)
            var t1_r1 = 0
            var t1_r2 = 0
            var t1_r3 = 0
            val t0 = thread {
                x.setPlain(1)
                y.setPlain(1)
                f.setRelease(1)
            }
            val t1 = thread {
                t1_r1 = f.getAcquire()
                t1_r2 = y.getPlain()
                t1_r3 = x.getPlain()
            }
            t0.join()
            t1.join()
            Triple(t1_r1, t1_r2, t1_r3)
        }
    }

    // RESULT: Sometimes
    @Test
    fun test_mp_once_two_unsync_writers() {
        val expectedOutcomes: Set<Triple<Int, Int, Int>> = setOf(Triple(0, 1, 2))
        litmusTest(assertSometimes(expectedOutcomes), MemoryModel.ReleaseAcquire) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var t0_r1 = 0
            var t2_r3 = 0
            var t2_r4 = 0
            val t0 = thread {
                t0_r1 = y.get()
                x.setPlain(1)
                y.set(1)
            }
            val t1 = thread {
                var r2 = y.get()
                x.setPlain(2)
                y.set(1)
            }
            val t2 = thread {
                t2_r3 = y.get()
                t2_r4 = x.getPlain()
            }
            t0.join()
            t1.join()
            t2.join()
            Triple(t0_r1, t2_r3, t2_r4)
        }
    }

    // RESULT: Never
    @Test
    fun test_mp_overwrite() {
        val forbiddenOutcomes: Set<Pair<Int, Int>> = setOf(1 to 1)
        litmusTest(assertNever(forbiddenOutcomes), MemoryModel.ReleaseAcquire) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var t1_r1 = 0
            var t1_r2 = 0
            val t0 = thread {
                x.setPlain(1)
                x.setPlain(2)
                y.setRelease(1)
            }
            val t1 = thread {
                t1_r1 = y.getAcquire()
                t1_r2 = x.getPlain()
            }
            t0.join()
            t1.join()
            t1_r1 to t1_r2
        }
    }

    // RESULT: Never
    @Ignore // TODO: ADD SC
    @Test
    fun test_mp_quasi_fence() {
        val forbiddenOutcomes: Set<Pair<Int, Int>> = setOf(1 to 0)
        litmusTest(assertNever(forbiddenOutcomes), MemoryModel.ReleaseAcquire) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            val v = AtomicInteger(0)
            var t1_r2 = 0
            var t1_r4 = 0
            val t0 = thread {
                x.setPlain(1)
                v.set(0)
                var r1 = v.get()
                y.setPlain(1)
            }
            val t1 = thread {
                t1_r2 = y.getPlain()
                v.set(0)
                var r3 = v.get()
                t1_r4 = x.getPlain()
            }
            t0.join()
            t1.join()
            t1_r2 to t1_r4
        }
    }

    // RESULT: Never
    @Test
    fun test_mp_trans() {
        val forbiddenOutcomes: Set<Triple<Int, Int, Int>> = setOf(Triple(2, 2, 1))
        litmusTest(assertNever(forbiddenOutcomes), MemoryModel.ReleaseAcquire) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var t1_r1 = 0
            var t2_r2 = 0
            var t2_r3 = 0
            val t0 = thread {
                x.setRelease(1)
                y.setRelease(2)
            }
            val t1 = thread {
                t1_r1 = y.getAcquire()
                x.setRelease(2)
            }
            val t2 = thread {
                t2_r2 = x.getAcquire()
                t2_r3 = x.getAcquire()
            }
            t0.join()
            t1.join()
            t2.join()
            Triple(t1_r1, t2_r2, t2_r3)
        }
    }

    // RESULT: Never
    @Test
    fun test_no_future_read_opq() {
        val forbiddenOutcomes: Set<Int> = setOf(1)
        litmusTest(assertNever(forbiddenOutcomes), MemoryModel.ReleaseAcquire) {
            val x = AtomicInteger(0)
            var t0_r1 = 0
            val t0 = thread {
                t0_r1 = x.getOpaque()
                x.setOpaque(1)
            }
            t0.join()
            t0_r1
        }
    }

    // RESULT: Never
    @Test
    fun test_no_future_read_pln() {
        val forbiddenOutcomes: Set<Int> = setOf(1)
        litmusTest(assertNever(forbiddenOutcomes), MemoryModel.ReleaseAcquire) {
            val x = AtomicInteger(0)
            var t0_r1 = 0
            val t0 = thread {
                t0_r1 = x.getPlain()
                x.setPlain(1)
            }
            t0.join()
            t0_r1
        }
    }

    // RESULT: Never
    @Test
    fun test_no_future_read_ra() {
        val forbiddenOutcomes: Set<Int> = setOf(1)
        litmusTest(assertNever(forbiddenOutcomes), MemoryModel.ReleaseAcquire) {
            val x = AtomicInteger(0)
            var t0_r1 = 0
            val t0 = thread {
                t0_r1 = x.getAcquire()
                x.setRelease(1)
            }
            t0.join()
            t0_r1
        }
    }

    // RESULT: Never
    @Test
    fun test_no_future_read_vol() {
        val forbiddenOutcomes: Set<Int> = setOf(1)
        litmusTest(assertNever(forbiddenOutcomes), MemoryModel.ReleaseAcquire) {
            val x = AtomicInteger(0)
            var t0_r1 = 0
            val t0 = thread {
                t0_r1 = x.get()
                x.set(1)
            }
            t0.join()
            t0_r1
        }
    }

    // IGNORED: fun test_out_of_thin_air_constant_conditional() {

    //IGNORED: fun test_out_of_thin_air_constant_conditional_variable_unconditional() {

    //IGNORED: fun test_out_of_thin_air_constant() {

    //IGNORED: fun test_out_of_thin_air_variable() {

    // RESULT: Sometimes
    @Test
    fun test_release_sequence_opq() {
        val expectedOutcomes: Set<Pair<Int, Int>> = setOf(2 to 0)
        litmusTest(assertSometimes(expectedOutcomes), MemoryModel.ReleaseAcquire) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var t1_r1 = 0
            var t1_r2 = 0
            val t0 = thread {
                x.setOpaque(1)
                y.setRelease(1)
                y.setOpaque(2)
            }
            val t1 = thread {
                t1_r1 = y.getAcquire()
                t1_r2 = x.getOpaque()
            }
            t0.join()
            t1.join()
            t1_r1 to t1_r2
        }
    }

    // RESULT: Sometimes
    @Test
    fun test_release_sequence_pln() {
        val expectedOutcomes: Set<Pair<Int, Int>> = setOf(2 to 0)
        litmusTest(assertSometimes(expectedOutcomes), MemoryModel.ReleaseAcquire) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var t1_r1 = 0
            var t1_r2 = 0
            val t0 = thread {
                x.setPlain(1)
                y.setRelease(1)
                y.setPlain(2)
            }
            val t1 = thread {
                t1_r1 = y.getAcquire()
                t1_r2 = x.getPlain()
            }
            t0.join()
            t1.join()
            t1_r1 to t1_r2
        }
    }

    // RESULT: Sometimes
    @Ignore // TODO: ADD SC
    @Test
    fun test_release_sequence_rmw() {
        val expectedOutcomes: Set<Triple<Int, Int, Int>> = setOf(Triple(1, 2, 0))
        litmusTest(assertSometimes(expectedOutcomes), MemoryModel.ReleaseAcquire) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var t0_r1 = 0
            var t1_r2 = 0
            var t1_r3 = 0
            val t0 = thread {
                x.setPlain(1)
                y.setRelease(1)
                t0_r1 = y.compareAndExchangeAcquire(1, 2)
            }
            val t1 = thread {
                t1_r2 = y.getAcquire()
                t1_r3 = x.getPlain()
            }
            t0.join()
            t1.join()
            Triple(t0_r1, t1_r2, t1_r3)
        }
    }

    // no RESULT annotation
    @Test
    fun test_rseq_weak() {
        val expectedOutcomes: Set<Pair<Int, Int>> = setOf(3 to 1)
        litmusTest(assertSometimes(expectedOutcomes), MemoryModel.ReleaseAcquire) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var t2_r1 = 0
            var t2_r2 = 0
            val t0 = thread {
                x.setOpaque(2)
            }
            val t1 = thread {
                y.setPlain(1)
                x.setRelease(1)
                x.setOpaque(3)
            }
            val t2 = thread {
                t2_r1 = x.getAcquire()
                if (t2_r1 == 3) {
                    t2_r2 = y.getPlain()
                }
            }
            t0.join()
            t1.join()
            t2.join()
            t2_r1 to t2_r2
        }
    }

    // RESULT: Sometimes
    @Test
    fun test_sb_fadd_volatile_acquire() {
        val expectedOutcomes: Set<Pair<Int, Int>> = setOf(0 to 0)
        litmusTest(assertSometimes(expectedOutcomes), MemoryModel.ReleaseAcquire) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var t0_r1 = 0
            var t1_r2 = 0
            val t0 = thread {
                var x0 = x.getAndAdd(1)
                t0_r1 = y.getAcquire()
            }
            val t1 = thread {
                var y0 = y.getAndAdd(1)
                t1_r2 = x.getAcquire()
            }
            t0.join()
            t1.join()
            t0_r1 to t1_r2
        }
    }

    // RESULT: Sometimes
    @Test
    fun test_sb_fake_fence() {
        val expectedOutcomes: Set<Pair<Int, Int>> = setOf(0 to 0)
        litmusTest(assertSometimes(expectedOutcomes), MemoryModel.ReleaseAcquire) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            val v = AtomicInteger(0)
            var t0_r2 = 0
            var t1_r4 = 0
            val t0 = thread {
                x.setPlain(1)
                var r1 = v.get()
                v.set(r1)
                t0_r2 = y.getPlain()
            }
            val t1 = thread {
                y.setPlain(1)
                var r3 = v.get()
                v.set(r3)
                t1_r4 = x.getPlain()
            }
            t0.join()
            t1.join()
            t0_r2 to t1_r4
        }
    }

    // RESULT: Sometimes
    @Test
    fun test_SB_opq_vol() {
        val expectedOutcomes: Set<Pair<Int, Int>> = setOf(0 to 0)
        litmusTest(assertSometimes(expectedOutcomes), MemoryModel.ReleaseAcquire) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var t0_r1 = 0
            var t1_r2 = 0
            val t0 = thread {
                x.setOpaque(1)
                t0_r1 = y.get()
            }
            val t1 = thread {
                y.setOpaque(1)
                t1_r2 = x.get()
            }
            t0.join()
            t1.join()
            t0_r1 to t1_r2
        }
    }

    // RESULT: Never
    @Ignore // TODO: ADD SC
    @Test
    fun test_sb_quasi_fence() {
        val forbiddenOutcomes: Set<Pair<Int, Int>> = setOf(0 to 0)
        litmusTest(assertNever(forbiddenOutcomes), MemoryModel.ReleaseAcquire) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            val v = AtomicInteger(0)
            var t0_r2 = 0
            var t1_r4 = 0
            val t0 = thread {
                x.setPlain(1)
                v.set(0)
                var r1 = v.get()
                t0_r2 = y.getPlain()
            }
            val t1 = thread {
                y.setPlain(1)
                v.set(0)
                var r3 = v.get()
                t1_r4 = x.getPlain()
            }
            t0.join()
            t1.join()
            t0_r2 to t1_r4
        }
    }

    // RESULT: Sometimes
    @Test
    fun test_sb_rfis() {
        val expectedOutcomes: Set<List<Int>> = setOf(listOf(1, 0, 1, 0))
        litmusTest(assertSometimes(expectedOutcomes), MemoryModel.ReleaseAcquire) {
            val x = AtomicInteger(0)
            val y = AtomicInteger(0)
            var t0_r1 = 0
            var t0_r2 = 0
            var t1_r3 = 0
            var t1_r4 = 0
            val t0 = thread {
                x.setPlain(1)
                t0_r1 = x.get()
                t0_r2 = y.get()
            }
            val t1 = thread {
                y.setPlain(1)
                t1_r3 = y.get()
                t1_r4 = x.get()
            }
            t0.join()
            t1.join()
            listOf(t0_r1, t0_r2, t1_r3, t1_r4)
        }
    }
}
