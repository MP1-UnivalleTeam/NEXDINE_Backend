package com.restaurant.app.controller;

import com.google.gson.JsonSyntaxException;
import com.restaurant.app.services.StripePaymentService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/stripe")
public class StripeWebhookController {

    private final StripePaymentService stripePaymentService;
    private final String webhookSecret;
    private final boolean stripeEnabled;

    public StripeWebhookController(StripePaymentService stripePaymentService,
                                   @Value("${stripe.webhook-secret:}") String webhookSecret,
                                   @Value("${stripe.enabled:false}") boolean stripeEnabled) {
        this.stripePaymentService = stripePaymentService;
        this.webhookSecret = webhookSecret == null ? "" : webhookSecret.trim();
        this.stripeEnabled = stripeEnabled;
    }

    @PostMapping("/webhook")
    public ResponseEntity<?> recibirWebhook(
            @RequestBody String payload,
            @RequestHeader(value = "Stripe-Signature", required = false) String signature) {
        if (!stripeEnabled || webhookSecret.isBlank()) {
            return ResponseEntity.status(503)
                    .body(Map.of("error", "Stripe no está habilitado o el webhook no está configurado."));
        }
        if (signature == null || signature.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Falta la firma del webhook de Stripe."));
        }

        final Event event;
        try {
            event = Webhook.constructEvent(payload, signature, webhookSecret);
        } catch (SignatureVerificationException | JsonSyntaxException | IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "La firma o el contenido del webhook de Stripe no es válido."));
        }

        try {
            stripePaymentService.procesarWebhook(event);
            return ResponseEntity.ok(Map.of("recibido", true));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
