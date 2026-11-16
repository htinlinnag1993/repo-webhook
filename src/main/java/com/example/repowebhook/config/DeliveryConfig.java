package com.example.repowebhook.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestClient;

@Configuration
public class DeliveryConfig {

    public static final String WEBHOOK_EXECUTOR = "webhookExecutor";

    /** Thread pool that performs outbound HTTP deliveries so API requests never block on subscribers. */
    @Bean(name = WEBHOOK_EXECUTOR)
    public ThreadPoolTaskExecutor webhookExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("webhook-");
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(1_000);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.initialize();
        return executor;
    }

    /**
     * HTTP client used for deliveries. SimpleClientHttpRequestFactory does not follow redirects for POST,
     * which keeps a subscriber from bouncing deliveries to another (possibly internal) host.
     */
    @Bean
    public RestClient webhookRestClient(RestClient.Builder builder, DeliveryProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());
        return builder.requestFactory(requestFactory).build();
    }
}
