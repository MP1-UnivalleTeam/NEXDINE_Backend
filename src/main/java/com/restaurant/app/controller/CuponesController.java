package com.restaurant.app.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/cupones")
public class CuponesController {

    @PostMapping("/validar")
    public ResponseEntity<?> validarCupon(@RequestParam String codigo) {
        if (codigo == null || codigo.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "El código del cupón es obligatorio"));
        }

        return ResponseEntity.badRequest().body(Map.of("error", "Cupón inválido o expirado"));
    }
}
