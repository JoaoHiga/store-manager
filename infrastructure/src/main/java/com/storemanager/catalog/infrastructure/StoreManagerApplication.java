package com.storemanager.catalog.infrastructure;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot entry point. Lives in the infrastructure module on purpose: the
 * application module (domain + ports + use cases) has zero Spring dependency,
 * so it cannot host the {@code @SpringBootApplication} class itself.
 */
@SpringBootApplication
public class StoreManagerApplication {

    public static void main(String[] args) {
        SpringApplication.run(StoreManagerApplication.class, args);
    }
}
