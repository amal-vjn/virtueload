package com.aml.virtueload.config;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.yaml.snakeyaml.Yaml;

/**
 * AppConfig is a singleton configuration class that loads API endpoint configurations from an application YAML file.
 * It provides methods to access the list of endpoints and retrieve specific endpoint configurations by name.
 */
public class AppConfig {
    private static AppConfig instance;
    private final List<ApiConfig> endpoints;

    private AppConfig() {
        endpoints = new ArrayList<>();
        Yaml yaml = new Yaml();
        try (InputStream inputStream = this.getClass()
                .getClassLoader()
                .getResourceAsStream("application.yml")) {
            Map<String, Object> config = yaml.load(inputStream);
            Map<String, Object> api = (Map<String, Object>) config.get("api");
            List<Map<String, Object>> endpointsList = (List<Map<String, Object>>) api.get("endpoints");
            
            for (Map<String, Object> endpoint : endpointsList) {
                ApiConfig apiConfig = new ApiConfig();
                apiConfig.setName((String) endpoint.get("name"));
                apiConfig.setUrl((String) endpoint.get("url"));
                
                Map<String, Object> clientMap = (Map<String, Object>) endpoint.get("client");
                ClientConfig clientConfig = new ClientConfig();
                clientConfig.setThreadPoolSize(((Number) clientMap.get("thread-pool-size")).intValue());
                clientConfig.setTotalRequests(((Number) clientMap.get("total-requests")).intValue());
                clientConfig.setRequestsPerSecond(((Number) clientMap.get("requests-per-second")).intValue());
                clientConfig.setTimeout(((Number) clientMap.get("timeout")).intValue());
                
                apiConfig.setClient(clientConfig);
                endpoints.add(apiConfig);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load configuration", e);
        }
    }

    public static synchronized AppConfig getInstance() {
        if (instance == null) {
            instance = new AppConfig();
        }
        return instance;
    }

    public List<ApiConfig> getEndpoints() {
        return endpoints;
    }

    public ApiConfig getEndpoint(String name) {
        return endpoints.stream()
            .filter(endpoint -> endpoint.getName().equals(name))
            .findFirst()
            .orElseThrow(() -> new RuntimeException("API endpoint not found: " + name));
    }
}
