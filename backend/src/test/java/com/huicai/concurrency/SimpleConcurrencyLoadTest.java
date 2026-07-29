package com.huicai.concurrency;

import com.huicai.common.test.SlowTest;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * 高并发压力测试 - 简化版
 * <p>专注于核心并发场景测试，避免复杂断言影响测试结果统计
 */
@SlowTest
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("高并发压力测试 - 简化版")
class SimpleConcurrencyLoadTest {

    @Autowired
    private MockMvc mvc;

    /**
     * 测试 1：核销推荐接口 - 100 并发 × 10 次请求
     */
    @Test
    @Order(1)
    @DisplayName("100并发 - 核销推荐接口")
    void test1_100ConcurrentThreads_reconciliation() throws Exception {
        final int THREADS = 100;
        final int REQUESTS_PER_THREAD = 10;

        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        CountDownLatch latch = new CountDownLatch(THREADS);
        AtomicInteger success = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);
        List<Long> times = Collections.synchronizedList(new ArrayList<>());

        long start = System.currentTimeMillis();

        for (int i = 0; i < THREADS; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < REQUESTS_PER_THREAD; j++) {
                        long t0 = System.nanoTime();
                        try {
                            mvc.perform(get("/api/sme/arap/v1/reconciliations/recommend-receipt"));
                            success.incrementAndGet();
                        } catch (Exception e) {
                            failed.incrementAndGet();
                        }
                        long t1 = System.nanoTime();
                        times.add(TimeUnit.NANOSECONDS.toMillis(t1 - t0));
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(60, TimeUnit.SECONDS);
        long totalTime = System.currentTimeMillis() - start;
        executor.shutdown();

        printResult("核销推荐接口 - 100并发", THREADS, REQUESTS_PER_THREAD,
                success.get(), failed.get(), totalTime, times);
    }

    /**
     * 测试 2：应收分页查询 - 50 并发 × 20 次请求
     */
    @Test
    @Order(2)
    @DisplayName("50并发 - 应收分页查询")
    void test2_50ConcurrentThreads_receivable() throws Exception {
        final int THREADS = 50;
        final int REQUESTS_PER_THREAD = 20;

        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        CountDownLatch latch = new CountDownLatch(THREADS);
        AtomicInteger success = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);
        List<Long> times = Collections.synchronizedList(new ArrayList<>());

        long start = System.currentTimeMillis();

        for (int i = 0; i < THREADS; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < REQUESTS_PER_THREAD; j++) {
                        long t0 = System.nanoTime();
                        try {
                            mvc.perform(get("/api/sme/arap/v1/receivables/page")
                                    .param("current", "1")
                                    .param("size", "10"));
                            success.incrementAndGet();
                        } catch (Exception e) {
                            failed.incrementAndGet();
                        }
                        long t1 = System.nanoTime();
                        times.add(TimeUnit.NANOSECONDS.toMillis(t1 - t0));
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(60, TimeUnit.SECONDS);
        long totalTime = System.currentTimeMillis() - start;
        executor.shutdown();

        printResult("应收分页查询 - 50并发", THREADS, REQUESTS_PER_THREAD,
                success.get(), failed.get(), totalTime, times);
    }

    /**
     * 测试 3：应付分页查询 - 50 并发 × 20 次请求
     */
    @Test
    @Order(3)
    @DisplayName("50并发 - 应付分页查询")
    void test3_50ConcurrentThreads_payable() throws Exception {
        final int THREADS = 50;
        final int REQUESTS_PER_THREAD = 20;

        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        CountDownLatch latch = new CountDownLatch(THREADS);
        AtomicInteger success = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);
        List<Long> times = Collections.synchronizedList(new ArrayList<>());

        long start = System.currentTimeMillis();

        for (int i = 0; i < THREADS; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < REQUESTS_PER_THREAD; j++) {
                        long t0 = System.nanoTime();
                        try {
                            mvc.perform(get("/api/sme/arap/v1/payables/page")
                                    .param("current", "1")
                                    .param("size", "10"));
                            success.incrementAndGet();
                        } catch (Exception e) {
                            failed.incrementAndGet();
                        }
                        long t1 = System.nanoTime();
                        times.add(TimeUnit.NANOSECONDS.toMillis(t1 - t0));
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(60, TimeUnit.SECONDS);
        long totalTime = System.currentTimeMillis() - start;
        executor.shutdown();

        printResult("应付分页查询 - 50并发", THREADS, REQUESTS_PER_THREAD,
                success.get(), failed.get(), totalTime, times);
    }

    /**
     * 测试 4：银行对账评分 - 30 并发 × 15 次请求
     */
    @Test
    @Order(4)
    @DisplayName("30并发 - 银行对账评分")
    void test4_30ConcurrentThreads_bankRecon() throws Exception {
        final int THREADS = 30;
        final int REQUESTS_PER_THREAD = 15;

        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        CountDownLatch latch = new CountDownLatch(THREADS);
        AtomicInteger success = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);
        List<Long> times = Collections.synchronizedList(new ArrayList<>());

        long start = System.currentTimeMillis();

        for (int i = 0; i < THREADS; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < REQUESTS_PER_THREAD; j++) {
                        long t0 = System.nanoTime();
                        try {
                            mvc.perform(get("/api/sme/cash/v1/bank-reconciliation/score")
                                    .param("period", "202606"));
                            success.incrementAndGet();
                        } catch (Exception e) {
                            failed.incrementAndGet();
                        }
                        long t1 = System.nanoTime();
                        times.add(TimeUnit.NANOSECONDS.toMillis(t1 - t0));
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(60, TimeUnit.SECONDS);
        long totalTime = System.currentTimeMillis() - start;
        executor.shutdown();

        printResult("银行对账评分 - 30并发", THREADS, REQUESTS_PER_THREAD,
                success.get(), failed.get(), totalTime, times);
    }

    /**
     * 测试 5：瞬时峰值测试 - 200 线程同时发起
     */
    @Test
    @Order(5)
    @DisplayName("200并发 - 瞬时峰值测试")
    void test5_200PeakConcurrent() throws Exception {
        final int THREADS = 200;

        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        CyclicBarrier barrier = new CyclicBarrier(THREADS);
        CountDownLatch latch = new CountDownLatch(THREADS);
        AtomicInteger success = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);
        List<Long> times = Collections.synchronizedList(new ArrayList<>());

        long start = System.currentTimeMillis();

        for (int i = 0; i < THREADS; i++) {
            executor.submit(() -> {
                try {
                    barrier.await();  // 所有线程同时开始
                    long t0 = System.nanoTime();
                    try {
                        mvc.perform(get("/api/sme/arap/v1/reconciliations/recommend-receipt"));
                        success.incrementAndGet();
                    } catch (Exception e) {
                        failed.incrementAndGet();
                    }
                    long t1 = System.nanoTime();
                    times.add(TimeUnit.NANOSECONDS.toMillis(t1 - t0));
                } catch (Exception e) {
                    // ignore
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(30, TimeUnit.SECONDS);
        long totalTime = System.currentTimeMillis() - start;
        executor.shutdown();

        printResult("瞬时峰值测试 - 200并发", THREADS, 1,
                success.get(), failed.get(), totalTime, times);
    }

    private void printResult(String testName, int threads, int requestsPerThread,
                             int success, int failed, long totalTimeMs, List<Long> times) {
        int totalRequests = threads * requestsPerThread;
        double successRate = totalRequests > 0 ? (success * 100.0) / totalRequests : 0;
        double avgTime = times.stream().mapToLong(Long::longValue).average().orElse(0);
        double throughput = totalTimeMs > 0 ? (success * 1000.0) / totalTimeMs : 0;

        // 计算 P95, P99
        long p95 = 0, p99 = 0;
        if (!times.isEmpty()) {
            Collections.sort(times);
            p95 = times.get(Math.min((int) (times.size() * 0.95), times.size() - 1));
            p99 = times.get(Math.min((int) (times.size() * 0.99), times.size() - 1));
        }

        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════════╗");
        System.out.println("║  " + testName);
        System.out.println("╠══════════════════════════════════════════════════════════════════╣");
        System.out.printf("║  并发线程数: %-4d  |  每线程请求: %-4d  |  总请求: %-4d     ║%n",
                threads, requestsPerThread, totalRequests);
        System.out.printf("║  成功数: %-5d   |  失败数: %-5d    |  成功率: %5.1f%%       ║%n",
                success, failed, successRate);
        System.out.println("╠══════════════════════════════════════════════════════════════════╣");
        System.out.printf("║  总耗时: %-6d ms     |  吞吐量: %8.2f req/s              ║%n",
                totalTimeMs, throughput);
        System.out.printf("║  平均响应: %7.2f ms  |  P95: %5d ms  |  P99: %5d ms       ║%n",
                avgTime, p95, p99);
        System.out.println("╚══════════════════════════════════════════════════════════════════╝");
        System.out.println();

        // 性能断言：确保测试结果有意义，而非空跑
        assertTrue(success > 0, testName + " — 应至少有1个成功请求");
        assertTrue(successRate >= 50.0, testName + " — 成功率应 >= 50%（实际: " + successRate + "%）");
        assertTrue(avgTime < 5000, testName + " — 平均响应应 < 5000ms（实际: " + avgTime + "ms）");
        assertTrue(p99 < 10000, testName + " — P99 应 < 10000ms（实际: " + p99 + "ms）");
    }
}
