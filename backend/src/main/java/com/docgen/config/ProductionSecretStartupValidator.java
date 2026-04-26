package com.docgen.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Fails application startup in production when placeholder or default secrets are still configured.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ProductionSecretStartupValidator implements ApplicationRunner {

    private final Environment environment;

    public ProductionSecretStartupValidator(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        ProductionSecretGuard.assertProductionSecrets(environment);
    }
}
