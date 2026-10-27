package com.restaurant.app.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SwaggerOpenApiSmokeTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void apiDocsGeneraEspecificacionValidaConLosEndpointsClave() {
        ResponseEntity<String> respuesta = rest.getForEntity("/v3/api-docs", String.class);

        assertEquals(200, respuesta.getStatusCode().value());
        String especificacion = respuesta.getBody();
        assertTrue(especificacion.contains("\"openapi\":\"3."));
        assertTrue(especificacion.contains("/api/pedidos/invitado"));
        assertTrue(especificacion.contains("/api/pedidos/{pedidoId}/resumen"));
        assertTrue(especificacion.contains("/api/pedidos/{pedidoId}/confirmar"));
        assertTrue(especificacion.contains("/api/pedidos/{pedidoId}/items"));
        assertTrue(especificacion.contains("/api/productos/{productoId}/opciones"));
        assertTrue(especificacion.contains("/api/sugerencias"));
        assertTrue(especificacion.contains("/api/cupones/validar"));
        assertTrue(especificacion.contains("/api/menu/{slug}"));
    }

    @Test
    void swaggerUiCargaConSuConfiguracion() {
        ResponseEntity<String> config = rest.getForEntity("/v3/api-docs/swagger-config", String.class);
        assertEquals(200, config.getStatusCode().value());
        assertTrue(config.getBody().contains("\"url\""));

        ResponseEntity<String> ui = rest.getForEntity("/swagger-ui/index.html", String.class);
        assertEquals(200, ui.getStatusCode().value());
        assertTrue(ui.getBody().contains("swagger-ui"));

        ResponseEntity<String> redirect = rest.getForEntity("/swagger-ui.html", String.class);
        assertEquals(302, redirect.getStatusCode().value());
        assertTrue(String.valueOf(redirect.getHeaders().getLocation())
                .contains("/swagger-ui/index.html"));
    }
}
