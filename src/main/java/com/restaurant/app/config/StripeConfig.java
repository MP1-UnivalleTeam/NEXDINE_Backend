package com.restaurant.app.config;

import com.stripe.StripeClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StripeConfig {

    @Bean
    @ConditionalOnProperty(name = "stripe.enabled", havingValue = "true")
    public StripeClient stripeClient(
            @Value("${stripe.secret-key}") String secretKey,
            @Value("${stripe.webhook-secret:}") String webhookSecret,
            @Value("${stripe.test-mode:true}") boolean testMode) {
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException("STRIPE_SECRET_KEY no puede estar vacío.");
        }
        if (webhookSecret == null || webhookSecret.isBlank()) {
            throw new IllegalStateException("STRIPE_WEBHOOK_SECRET no puede estar vacío.");
        }
        if (testMode && !secretKey.startsWith("sk_test_")) {
            throw new IllegalStateException(
                    "STRIPE_TEST_MODE=true requiere una clave secreta de prueba sk_test_ de Stripe.");
        }
        if (!testMode && !secretKey.startsWith("sk_live_")) {
            throw new IllegalStateException(
                    "STRIPE_TEST_MODE=false requiere una clave secreta de producción sk_live_ de Stripe.");
        }
        return new StripeClient(secretKey);
    }
}
