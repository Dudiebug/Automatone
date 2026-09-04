/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package baritone.api.event.events;

import baritone.api.event.events.type.EventState;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TickEventSequenceRegressionTest {

    @Test
    public void publicClassMonitorDoesNotBlockProviderCreation() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch attempted = new CountDownLatch(1);
        Object publicClassMonitor = TickEvent.class;
        try {
            synchronized (publicClassMonitor) {
                Future<BiFunction<EventState, TickEvent.Type, TickEvent>> future = executor.submit(() -> {
                    attempted.countDown();
                    return TickEvent.createNextProvider();
                });

                assertTrue("provider creation did not start", attempted.await(5, TimeUnit.SECONDS));
                BiFunction<EventState, TickEvent.Type, TickEvent> provider = future.get(5, TimeUnit.SECONDS);
                int count = provider.apply(EventState.PRE, TickEvent.Type.IN).getCount();
                assertEquals(count, provider.apply(EventState.POST, TickEvent.Type.OUT).getCount());
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void concurrentProvidersReserveUniqueCounts() throws Exception {
        final int providerCount = 64;
        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>(providerCount);
        try {
            for (int i = 0; i < providerCount; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return TickEvent.createNextProvider().apply(EventState.PRE, TickEvent.Type.IN).getCount();
                }));
            }
            start.countDown();

            Set<Integer> counts = new HashSet<>();
            for (Future<Integer> future : futures) {
                counts.add(future.get());
            }
            assertEquals(providerCount, counts.size());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void providersCaptureCountsAndAdvanceExactlyOnce() {
        BiFunction<EventState, TickEvent.Type, TickEvent> first = TickEvent.createNextProvider();
        BiFunction<EventState, TickEvent.Type, TickEvent> second = TickEvent.createNextProvider();

        int firstCount = first.apply(EventState.PRE, TickEvent.Type.IN).getCount();
        int secondCount = second.apply(EventState.POST, TickEvent.Type.OUT).getCount();

        assertEquals(firstCount, first.apply(EventState.POST, TickEvent.Type.OUT).getCount());
        assertEquals(firstCount + 1, secondCount);
    }
}
