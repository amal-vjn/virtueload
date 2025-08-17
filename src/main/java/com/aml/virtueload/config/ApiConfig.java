package com.aml.virtueload.config;

/**
 * ApiConfig is a configuration class for API endpoints used in load testing.
 * It contains the name, URL, and client configuration for the API.
 */
public class ApiConfig {
    private String name;
    private String url;
    private ClientConfig client;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public ClientConfig getClient() {
        return client;
    }

    public void setClient(ClientConfig client) {
        this.client = client;
    }
}
