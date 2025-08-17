# VirtueLoad - High-Performance API Load Testing Tool

A Java-based load testing tool designed for high-performance API testing using virtual threads. VirtueLoad provides comprehensive metrics, rate limiting, and configurable test scenarios for evaluating API performance under various load conditions.

## Features

- **Virtual Thread Architecture**: Leverages Java's virtual threads for maximum concurrency with minimal resource overhead
- **Rate Limiting**: Built-in rate limiter to control request frequency and prevent overwhelming target APIs
- **Comprehensive Metrics**: Real-time and final statistics including throughput, response times, and error rates
- **Configurable Testing**: Support for multiple API endpoints with individual configuration
- **Timeout Handling**: Sophisticated timeout detection and reporting
- **Real-time Monitoring**: Live status updates during test execution
- **Thread Pool Management**: Intelligent thread pool sizing with dynamic load balancing

## Prerequisites

- Java 21 or later (required for virtual threads)
- Maven 3.8+
- SLF4J logging framework
- Target APIs to test

## Installation

1. Clone the repository:
```bash
git clone https://github.com/amal-vjn/virtueload
cd virtueload
```

2. Build the project:
```bash
mvn clean compile
```

3. Configure your test scenarios in `src/main/resources/application.yml` (see Configuration section)

4. Run the application:

```bash
mvn exec:java -Dexec.mainClass="com.aml.virtueload.ApiLoadTest"
```

## Quick Start

1. Create your configuration file `src/main/resources/application.yml`:

```yaml
endpoints:
  - name: "MyAPI"
    url: "https://api.example.com/endpoint"
    client:
      requestsPerSecond: 100
      threadPoolSize: 1000
      totalRequests: 10000
      timeout: 5000
```

2. Run the application:

```bash
mvn exec:java -Dexec.mainClass="com.aml.virtueload.ApiLoadTest"
```

Or programmatically:

```java
public static void main(String[] args) throws Exception {
    AppConfig config = AppConfig.getInstance();
    
    // Process each configured API endpoint from application.yml
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
```

## Configuration

VirtueLoad uses YAML configuration files to define your API testing scenarios. Create an `application.yml` file in your project's resources directory to configure multiple API endpoints with their specific test parameters.

### Configuration File Structure

Create `src/main/resources/application.yml`:

```yaml
endpoints:
  - name: "UserAPI"
    url: "https://api.example.com/users"
    client:
      requestsPerSecond: 50
      threadPoolSize: 500
      totalRequests: 5000
      timeout: 3000
      
  - name: "ProductAPI"
    url: "https://api.example.com/products"
    client:
      requestsPerSecond: 100
      threadPoolSize: 1000
      totalRequests: 10000
      timeout: 5000
      
  - name: "OrderAPI"
    url: "https://api.example.com/orders"
    client:
      requestsPerSecond: 25
      threadPoolSize: 250
      totalRequests: 2500
      timeout: 2000
```

### Configuration Parameters

| Parameter | Description | Example | Range |
|-----------|-------------|---------|-------|
| `name` | Unique identifier for the API endpoint | "UserAPI" | Any string |
| `url` | Full URL of the API endpoint to test | "https://api.example.com/users" | Valid HTTP/HTTPS URL |
| `requestsPerSecond` | Rate limit for API calls | 50 | 1-1000+ |
| `threadPoolSize` | Maximum concurrent threads | 500 | 1-10000+ |
| `totalRequests` | Total number of requests to send | 5000 | 1+ |
| `timeout` | Request timeout in milliseconds | 3000 | 100-60000 |

### Multiple Environment Configurations

You can create different configuration files for different environments:

```yaml
# application-dev.yml
endpoints:
  - name: "DevAPI"
    url: "https://dev-api.example.com/users"
    client:
      requestsPerSecond: 10
      threadPoolSize: 100
      totalRequests: 1000
      timeout: 5000

# application-prod.yml  
endpoints:
  - name: "ProdAPI"
    url: "https://api.example.com/users"
    client:
      requestsPerSecond: 100
      threadPoolSize: 1000
      totalRequests: 10000
      timeout: 3000
```

## Metrics and Reporting

ThreadClients provides comprehensive metrics during and after test execution:

### Real-time Metrics (Every 5 seconds)
- **Completion Percentage**: Progress indicator
- **Running Threads**: Currently active requests
- **Request Counts**: Successful, failed, and timed-out requests
- **Throughput**: Current requests per second
- **Average Execution Times**: Broken down by request type

### Final Report
- **Total Execution Time**: Complete test duration
- **Success Rate**: Percentage of successful requests
- **Error Breakdown**: Detailed failure analysis
- **Performance Statistics**: Comprehensive timing analysis

### Sample Output
```
Status(45%) - Running: 234, Completed: 4500/10000 (Success: 4200, Timed Out: 150, Failed: 150), 
Rate: 89.23 req/sec, Avg Success Exec Time: 245.67 ms, Avg Timeout Exec Time: 5000.00 ms, 
Avg Unknown error Exec Time: 1234.56 ms

========================================
Execution completed in 112.34 seconds
Final statistics for API: UserAPI
  Total requests: 10000
  Successful requests: 9200, 92%
  Timed out requests: 500, 5%
  Failed requests: 300, 3%
  Average throughput: 89.02 requests/second
  Average execution time for Successful requests: 245.67 ms
  Average execution time for Timeout requests: 4987.23 ms
  Average execution time for Unknown failure requests: 1456.78 ms
========================================
```

## Architecture

### Core Components

1. **ApiLoadTest**: Main orchestrator class handling test execution and metrics collection
2. **RateLimiter**: Controls request frequency to prevent API overwhelming  
3. **ThreadMetrics**: Data structure for capturing individual request results (threadId, statusCode, durationMs)
4. **Virtual Thread Pool**: High-performance concurrency management
5. **AppConfig & ApiConfig**: YAML-based configuration management system

### Execution Flow

1. **Configuration Loading**: Load API endpoints and parameters from `application.yml`
2. **Initialization**: Configure rate limiter and metrics collectors for each endpoint
3. **Task Submission**: Submit requests to virtual thread executor with pool management
4. **Dynamic Management**: Monitor and adjust concurrent request load using intelligent batching
5. **Metrics Collection**: Aggregate real-time performance data with atomic counters
6. **Reporting**: Generate comprehensive test results with detailed statistics

## Error Handling

VirtueLoad categorizes errors into three types with comprehensive tracking:

- **Successful Requests (200)**: Normal successful API responses with execution time tracking
- **Timeout Errors (408)**: Requests exceeding configured timeout with actual timeout duration
- **Unknown Errors (500)**: Network issues, connection failures, or other exceptions with error categorization

Each error type is tracked separately with individual timing metrics and detailed logging for debugging purposes.

### Best Practices

#### Configuration Management
- Keep different `application.yml` files for different environments (dev, staging, prod)
- Use meaningful names for your API endpoints in the configuration
- Start with conservative values and gradually increase load parameters
- Document your configuration choices for team members

#### Performance Optimization
- Start with conservative thread pool sizes and gradually increase
- Monitor system resources during high-load tests
- Use appropriate timeout values based on expected API response times
- Consider network latency in your test configuration

### Configuration Validation

VirtueLoad validates your configuration on startup and will report any issues:

- **Missing required fields**: All parameters in the client section are mandatory
- **Invalid URLs**: URLs must be properly formatted with http:// or https://
- **Invalid ranges**: Parameters must be within acceptable ranges
- **Duplicate names**: Each endpoint must have a unique name

Example validation error:
```
ERROR: Invalid configuration for endpoint 'UserAPI':
  - requestsPerSecond must be greater than 0
  - timeout must be between 100 and 60000 milliseconds
  - URL must start with http:// or https://
```
### Rate Limiting
- Set `requestsPerSecond` in your YAML configuration below your API's rate limit
- Use rate limiting to simulate realistic user behavior
- Monitor target API's health during testing

### Monitoring
- Watch for memory usage during long-running tests
- Monitor both client and server resources
- Use logging levels appropriate for your environment

## Troubleshooting

### Common Issues

**High Memory Usage**
- Reduce `threadPoolSize` if experiencing memory pressure
- Ensure proper garbage collection settings for your JVM

**Connection Timeouts**
- Increase timeout values for slow APIs
- Check network connectivity and latency
- Verify target API capacity

**Rate Limiting Issues**
- Adjust `requestsPerSecond` if seeing 429 responses
- Implement exponential backoff for resilient testing

### Logging

Configure SLF4J logging levels:
- `INFO`: General execution progress and final statistics
- `WARN`: Timeout and recoverable error notifications
- `ERROR`: Serious failures requiring attention
- `DEBUG`: Detailed execution traces (performance impact)

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## Performance Considerations

- **Virtual Threads**: Designed to handle thousands of concurrent requests efficiently
- **Memory Usage**: Each virtual thread uses minimal memory compared to platform threads
- **Scalability**: Can scale to handle very high concurrent loads with proper configuration
- **Resource Management**: Automatic cleanup of completed requests prevents memory leaks

## Limitations

- Requires Java 21+ for virtual thread support
- HTTP client only (no WebSocket or other protocol support)
- Limited to GET requests in current implementation
- No built-in authentication mechanisms

## Package Structure

```
com.aml.virtueload/
├── ApiLoadTest.java          # Main load testing orchestrator
├── RateLimiter.java         # Request rate control
├── ThreadMetrics.java       # Request result data structure  
└── config/
    ├── AppConfig.java       # Application configuration loader
    └── ApiConfig.java       # API endpoint configuration
```


## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Version History

- **v1.0.0**: Initial release with virtual thread support and basic load testing

## Support

For issues, questions, or contributions:
- Create an issue in the GitHub repository
- Review existing documentation and examples
- Check troubleshooting section for common problems