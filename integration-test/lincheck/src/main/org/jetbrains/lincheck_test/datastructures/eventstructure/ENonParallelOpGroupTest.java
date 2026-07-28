/*
 * Lincheck
 *
 * Copyright (C) 2019 - 2023 JetBrains s.r.o.
 *
 * This Source Code Form is subject to the terms of the
 * Mozilla Public License, v. 2.0. If a copy of the MPL was not distributed
 * with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.jetbrains.lincheck_test.datastructures.eventstructure;

import org.jctools.queues.atomic.SpscLinkedAtomicQueue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlinx.lincheck_test.AbstractLincheckTest;
import org.jetbrains.lincheck.datastructures.Operation;
import org.jetbrains.lincheck.datastructures.Options;
import org.junit.Test;

import static org.jetbrains.lincheck_test.datastructures.eventstructure.AbstractEventStructureTestKt.TIMEOUT;


public class ENonParallelOpGroupTest extends AbstractEventStructureTest {
    private final SpscLinkedAtomicQueue<Integer> queue = new SpscLinkedAtomicQueue<>();

    @Operation(nonParallelGroup = "producer")
    public void offer(Integer x) {
        queue.offer(x);
    }

    @Operation(nonParallelGroup = "consumer")
    public Integer poll() {
        return queue.poll();
    }

    @Test(timeout = TIMEOUT)
    public void testWithEventStructureStrategyJAM() {
        _testWithEventStructureStrategyJAM();
    }
    @Test(timeout = TIMEOUT)
    public void testWithEventStructureStrategySC() {
        _testWithEventStructureStrategySC();
    }
    @Test(timeout = TIMEOUT)
    public void testWithModelCheckingStrategy() {
        _testWithModelCheckingStrategy();
    }
    @Test(timeout = TIMEOUT)
    public void testWithStressStrategy() {
        _testWithStressStrategy();
    }

}
