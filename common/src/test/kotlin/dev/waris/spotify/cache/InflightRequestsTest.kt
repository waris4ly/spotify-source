package dev.waris.spotify.cache

import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class InflightRequestsTest {
    @Test
    fun identicalConcurrentRequests_shareSingleExecution() {
        val inflight = InflightRequests<String, String>()
        val callCount = AtomicInteger(0)
        val readyLatch = CountDownLatch(8)
        val releaseLatch = CountDownLatch(1)

        val executor = Executors.newFixedThreadPool(8)
        try {
            val tasks =
                List(8) {
                    Callable {
                        readyLatch.countDown()
                        readyLatch.await(5, TimeUnit.SECONDS)
                        inflight.execute("shared_key") {
                            callCount.incrementAndGet()
                            releaseLatch.await(5, TimeUnit.SECONDS)
                            "computed_result"
                        }
                    }
                }

            val futures = tasks.map { executor.submit(it) }
            Thread.sleep(100)
            releaseLatch.countDown()

            val results = futures.map { it.get(5, TimeUnit.SECONDS) }

            assertEquals(1, callCount.get())
            for (res in results) {
                assertEquals("computed_result", res)
            }
            assertEquals(0, inflight.activeCount)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun differentKeys_executeIndependently() {
        val inflight = InflightRequests<String, String>()
        val callCount = AtomicInteger(0)

        val r1 =
            inflight.execute("key1") {
                callCount.incrementAndGet()
                "val1"
            }
        val r2 =
            inflight.execute("key2") {
                callCount.incrementAndGet()
                "val2"
            }

        assertEquals("val1", r1)
        assertEquals("val2", r2)
        assertEquals(2, callCount.get())
        assertEquals(0, inflight.activeCount)
    }

    @Test
    fun failure_propagatesToAllWaitingCallers() {
        val inflight = InflightRequests<String, String>()
        val callCount = AtomicInteger(0)
        val readyLatch = CountDownLatch(4)
        val releaseLatch = CountDownLatch(1)

        val executor = Executors.newFixedThreadPool(4)
        try {
            val tasks =
                List(4) {
                    Callable {
                        readyLatch.countDown()
                        readyLatch.await(5, TimeUnit.SECONDS)
                        inflight.execute("fail_key") {
                            callCount.incrementAndGet()
                            releaseLatch.await(5, TimeUnit.SECONDS)
                            throw IllegalStateException("underlying compute error")
                        }
                    }
                }

            val futures = tasks.map { executor.submit(it) }
            Thread.sleep(100)
            releaseLatch.countDown()

            for (future in futures) {
                val ex =
                    assertThrows(Exception::class.java) {
                        future.get(5, TimeUnit.SECONDS)
                    }
                val cause = ex.cause
                assertEquals("underlying compute error", cause?.message)
            }

            assertEquals(1, callCount.get())
            assertEquals(0, inflight.activeCount)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun subsequentRequestAfterCompletion_executesAgain() {
        val inflight = InflightRequests<String, String>()
        val counter = AtomicInteger(0)

        val first =
            inflight.execute("key") {
                "first-${counter.incrementAndGet()}"
            }
        val second =
            inflight.execute("key") {
                "second-${counter.incrementAndGet()}"
            }

        assertEquals("first-1", first)
        assertEquals("second-2", second)
        assertEquals(2, counter.get())
    }
}
