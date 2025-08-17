package com.aml.virtueload;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aml.virtueload.config.ApiConfig;
import com.aml.virtueload.config.AppConfig;

/**
 * ApiLoadTest is a class that performs load testing on a specified API endpoint using virtual threads.
 * It allows for concurrent requests to be sent to the API, tracks the success and failure rates,
 * and provides detailed metrics on request execution times.
 */
public class ApiLoadTest {

    private static final Logger logger = LoggerFactory.getLogger(ApiLoadTest.class);
    private static final HttpClient httpClient = HttpClient.newBuilder().build();
    private final AtomicInteger runningThreads = new AtomicInteger(0);
    private final AtomicInteger successfulRequests = new AtomicInteger(0);
    private final AtomicInteger failedRequests = new AtomicInteger(0);
    private final AtomicInteger timedOutRequests = new AtomicInteger(0);
    private final AtomicInteger successRequestTime = new AtomicInteger(0);
    private final AtomicInteger failedWithUnknownErrorCount = new AtomicInteger(0);
    private final AtomicLong totalExecTimeForfailedWithUnknown = new AtomicLong(0);
    private final AtomicLong totalTimeoutTime = new AtomicLong(0);
    private final RateLimiter rateLimiter;
    private final String apiName;

    /**
     * Constructs an ApiLoadTest instance for a specific API endpoint.
     *
     * @param apiName          the name of the API endpoint
     * @param requestsPerSecond the number of requests per second to be sent to the API
     */
    public ApiLoadTest(String apiName, int requestsPerSecond) {
        this.apiName = apiName;
        this.rateLimiter = new RateLimiter(requestsPerSecond);
    }

    public static void main(String[] args) throws Exception {
        AppConfig config = AppConfig.getInstance();
        // Process each configured API endpoint
        for (ApiConfig apiConfig : config.getEndpoints()) {
            ApiLoadTest client = new ApiLoadTest(
                    apiConfig.getName(),
                    apiConfig.getClient().getRequestsPerSecond()
            );

            client.invokeApiWithVirtualThreads(
                    apiConfig.getUrl(),
                    apiConfig.getClient().getThreadPoolSize(),
                    apiConfig.getClient().getTotalRequests(),
                    apiConfig.getClient().getTimeout()
            );
        }
    }

    /**
     * Invokes the API using virtual threads and tracks the execution metrics.
     *
     * @param apiUrl          the URL of the API endpoint
     * @param poolSize        the size of the thread pool to use for concurrent requests
     * @param totalRequests   the total number of requests to send
     * @param timeout         the timeout for each request in milliseconds
     * @throws Exception if an error occurs during execution
     */
    public void invokeApiWithVirtualThreads(String apiUrl, int poolSize, int totalRequests, int timeout) throws Exception {
        logger.info("Starting client for API: {}", apiName);
        try (
                ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor(); ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor()) {
            List<Future<ThreadMetrics>> futures = new ArrayList<>();
            Instant startTime = Instant.now();

            // Start status reporting thread
            scheduler.scheduleAtFixedRate(() -> {
                int running = runningThreads.get();
                int successful = successfulRequests.get();
                int failed = failedRequests.get();
                int timedOut = timedOutRequests.get();
                int completed = successful + failed + timedOut;
                double durationSecs = Duration.between(startTime, Instant.now()).toMillis() / 1000.0;
                double requestsPerSec = completed / durationSecs;
                double avgSuccessExecutionTime = successfulRequests.get() > 0 ? successRequestTime.get() / (double) successfulRequests.get() : 0.0;
                double avgUnknownFailTime = failedWithUnknownErrorCount.get() > 0 ? totalExecTimeForfailedWithUnknown.get() / (double) failedWithUnknownErrorCount.get() : 0.0;
                double avgTimeoutTime = timedOut > 0 ? totalTimeoutTime.get() / (double) timedOut : 0.0;

                logger.info("Status({}%) - Running: {}, Completed: {}/{} (Success: {}, Timed Out: {}, Failed: {}), Rate: {} req/sec, Avg Success Exec Time: {} ms, Avg Timeout Exec Time: {} ms, Avg Unknown error Exec Time: {} ms",
                        completed*100/totalRequests, running, completed, totalRequests, successful, timedOut, failed,
                        String.format("%.2f", requestsPerSec),
                        String.format("%.2f", avgSuccessExecutionTime),
                        String.format("%.2f", avgTimeoutTime),
                        String.format("%.2f", avgUnknownFailTime));
            }, 1, 5, TimeUnit.SECONDS);

            // Submit tasks to the thread pool
            for (int i = 0; i < totalRequests; i++) {
                int requestId = i;
                futures.add(executor.submit(() -> {
                    runningThreads.incrementAndGet();
                    Instant start = Instant.now();
                    try {
                        return executeRequest(apiUrl, requestId, timeout);
                    } catch (Exception e) {
                        long durationMs = Duration.between(start, Instant.now()).toMillis();
                        if (e.getCause() instanceof HttpTimeoutException || e.getCause() instanceof TimeoutException) {
                            totalTimeoutTime.addAndGet(durationMs);
                            logger.warn("Request {} timed out after {} ms", requestId, durationMs);
                            return new ThreadMetrics(requestId, 408, durationMs); // 408 Request Timeout
                        } else {
                            totalExecTimeForfailedWithUnknown.addAndGet(durationMs);
                            failedWithUnknownErrorCount.incrementAndGet();
                            logger.error("Unknown Error during executing request {}: {}", requestId, e.getMessage());
                            return new ThreadMetrics(requestId, 500, durationMs); // 500 Internal Server Error
                        }
                    } finally {
                        runningThreads.decrementAndGet();
                    }
                }));

                // If we've submitted poolSize requests, wait for some to complete
                if (futures.size() >= poolSize) {
                    // Wait for some requests to complete before submitting more
                    int completedCount = 0;
                    while (completedCount < poolSize / 2) {
                        List<Future<ThreadMetrics>> completedFutures = futures.stream()
                                .filter(Future::isDone)
                                .collect(Collectors.toList());
                        if (completedFutures.isEmpty()) {
                            Thread.sleep(10); // Brief pause before checking again
                            continue;
                        }
                        for (Future<ThreadMetrics> future : completedFutures) {
                            futures.remove(future);
                            ThreadMetrics metrics = null;
                            try {
                                metrics = future.get(30, TimeUnit.SECONDS);
                            } catch (Exception e) {
                                if (e.getCause() instanceof HttpTimeoutException || e.getCause() instanceof TimeoutException) {
                                    logger.warn("Request timed out after {} ms", timeout);
                                    metrics = new ThreadMetrics(requestId, 408, timeout);
                                } else {
                                    logger.error("Error while waiting for request completion : {}, {}", e.getCause(), e.getMessage());
                                    metrics = new ThreadMetrics(requestId, 500, 0);
                                }
                            }
                            completedCount++;
                            updateMetrics(metrics);
                        }
                    }
                }
            }

            // Wait for all remaining tasks to complete
            for (Future<ThreadMetrics> future : futures) {
                ThreadMetrics metrics = null;
                try {
                    metrics = future.get(30, TimeUnit.SECONDS);
                } catch (Exception e) {
                    if (e.getCause() instanceof HttpTimeoutException || e.getCause() instanceof TimeoutException) {
                        logger.warn("Request timed out after {} ms", timeout);
                        metrics = new ThreadMetrics(0, 408, timeout);
                    } else {
                        logger.error("Error while waiting for final request completion: {}", e.getMessage());
                        metrics = new ThreadMetrics(0, 500, 0);
                    }
                }
                updateMetrics(metrics);
            }

            // Shutdown the scheduler
            scheduler.shutdown();
            scheduler.awaitTermination(1, TimeUnit.SECONDS);

            // Log final statistics
            logFinalStatistics(startTime);
        }
    }

    //Updates the metrics based on the response from the executed request
    private void updateMetrics(ThreadMetrics metrics) {
        if (metrics.statusCode() == 200) {
            successRequestTime.addAndGet((int) metrics.durationMs());
            successfulRequests.incrementAndGet();
        } else if (metrics.statusCode() == 408) {
            totalTimeoutTime.addAndGet(metrics.durationMs());
            timedOutRequests.incrementAndGet();
        } else {
            failedRequests.incrementAndGet();
        }
    }

    //Logs the final statistics after all requests have been processed
    private void logFinalStatistics(Instant startTime) {
        double totalDurationSecs = Duration.between(startTime, Instant.now()).toMillis() / 1000.0;
        int totalCompleted = successfulRequests.get() + failedRequests.get() + timedOutRequests.get();
        double avgRequestsPerSec = totalCompleted / totalDurationSecs;
        double avgSuccessExecutionTime = totalCompleted > 0 ? successRequestTime.get() / (double) successfulRequests.get() : 0.0;
        double avgUnknownFailTime = failedWithUnknownErrorCount.get() > 0
                ? totalExecTimeForfailedWithUnknown.get() / (double) failedWithUnknownErrorCount.get() : 0.0;
        double avgTimeoutTime = timedOutRequests.get() > 0
                ? totalTimeoutTime.get() / (double) timedOutRequests.get() : 0.0;

        logger.info("========================================");
        logger.info("Execution completed in {} seconds", String.format("%.2f", totalDurationSecs));
        logger.info("Final statistics for API: {}", apiName);
        logger.info("  Total requests: {}", totalCompleted);
        logger.info("  Successful requests: {}, {}%", successfulRequests.get(), totalCompleted > 0 ? (successfulRequests.get() * 100 / totalCompleted) : 0);
        logger.info("  Timed out requests: {}, {}%",timedOutRequests.get(),totalCompleted > 0 ? (timedOutRequests.get() * 100 / totalCompleted) : 0);
        logger.info("  Failed requests: {}, {}%", failedRequests.get(), totalCompleted > 0 ? (failedRequests.get() * 100 / totalCompleted) : 0);
        logger.info("  Average throughput: {} requests/second", String.format("%.2f", avgRequestsPerSec));
        logger.info("  Average execution time for Successful requests: {} ms", String.format("%.2f", avgSuccessExecutionTime));
        logger.info("  Average execution time for Timeout requests: {} ms", String.format("%.2f", avgTimeoutTime));
        logger.info("  Average execution time for Unknown failure requests: {} ms", String.format("%.2f", avgUnknownFailTime));
        logger.info("========================================");
    }

    // Executes a single request to the API and returns the metrics for that request
    private ThreadMetrics executeRequest(String apiUrl, int threadId, int timeout) throws IOException, InterruptedException {
        rateLimiter.acquire();
        Instant start = Instant.now();

        HttpResponse<String> response = null;
        try {
            response = httpClient.send(
                    HttpRequest.newBuilder()
                            .uri(URI.create(apiUrl))
                            .GET()
                            .timeout(Duration.ofMillis(timeout))
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );
        } catch (HttpTimeoutException e) {
            long durationMs = Duration.between(start, Instant.now()).toMillis();
            return new ThreadMetrics(threadId, 408, durationMs);
        } catch (IOException e) {
            throw new IOException("Failed to execute request", e);
        } catch (Exception e) {
            throw new IOException("Unexpected error during execution. ", e);
        }

        long durationMs = Duration.between(start, Instant.now()).toMillis();
        return new ThreadMetrics(threadId, response.statusCode(), durationMs);
    }
}

record ThreadMetrics(int threadId, int statusCode, long durationMs) {

}
