package com.aml.virtueload.config;

/**
 * ClientConfig is a configuration class for the client settings used in API load testing.
 * It contains parameters such as thread pool size, total requests, requests per second, and timeout.
 */
public class ClientConfig {
    private int threadPoolSize;
    private int totalRequests;
    private int requestsPerSecond;
    private int timeout; // Timeout in milliseconds

    public int getThreadPoolSize() {
        return threadPoolSize;
    }

    public void setThreadPoolSize(int threadPoolSize) {
        this.threadPoolSize = threadPoolSize;
    }

    public int getTotalRequests() {
        return totalRequests;
    }

    public void setTotalRequests(int totalRequests) {
        this.totalRequests = totalRequests;
    }

    public int getRequestsPerSecond() {
        return requestsPerSecond;
    }

    public void setRequestsPerSecond(int requestsPerSecond) {
        this.requestsPerSecond = requestsPerSecond;
    }

    public int getTimeout() {
        return timeout;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }
}
