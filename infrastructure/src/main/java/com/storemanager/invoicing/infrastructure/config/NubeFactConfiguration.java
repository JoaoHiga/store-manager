package com.storemanager.invoicing.infrastructure.config;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Registers the {@link RestClient} used by {@code NubeFactInvoicingAdapter} (design.md's
 * Architecture Decisions: Spring {@code RestClient}, not {@code RestTemplate}/{@code WebClient}).
 * Configured with an explicit connect/read timeout so an unreachable/slow NubeFacT never blocks a
 * request thread indefinitely (design.md's Threat Matrix: "Outbound call hangs / NubeFacT
 * outage").
 */
@Configuration
public class NubeFactConfiguration {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

    @Bean
    public RestClient nubeFactRestClient(@Value("${nubefact.base-url}") String baseUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) CONNECT_TIMEOUT.toMillis());
        requestFactory.setReadTimeout((int) READ_TIMEOUT.toMillis());
        return RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
    }
}
