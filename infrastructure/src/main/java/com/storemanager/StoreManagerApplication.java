package com.storemanager;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot entry point. Lives in the infrastructure module on purpose: the
 * application module (domain + ports + use cases) has zero Spring dependency,
 * so it cannot host the {@code @SpringBootApplication} class itself.
 *
 * <p>Lives directly under {@code com.storemanager} (not under {@code
 * com.storemanager.catalog.infrastructure}, where it originally lived) so that
 * its default component/entity/repository scan covers every bounded context
 * as a sibling package — {@code com.storemanager.catalog.*} and {@code
 * com.storemanager.sales.*} (and future contexts) alike. This also keeps it
 * discoverable as the {@code @SpringBootConfiguration} for slice tests (e.g.
 * {@code @DataJpaTest}, {@code @WebMvcTest}) under any bounded context's test
 * package, since that discovery walks up the package hierarchy from the test
 * class looking for exactly this annotation.
 */
@SpringBootApplication
public class StoreManagerApplication {

    public static void main(String[] args) {
        SpringApplication.run(StoreManagerApplication.class, args);
    }
}
