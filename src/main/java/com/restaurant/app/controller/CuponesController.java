package com.restaurant.app.controller;

import com.restaurant.app.services.CuponService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/cupones")
public class CuponesController {

    private final CuponService cuponService;

    public CuponesController(CuponService cuponService) {
        this.cuponService = cuponService;
    }

    @PostMapping("/validar")
    public ResponseEntity<?> validarCupon(@RequestParam String codigo,
                                          @RequestParam(required = false) BigDecimal subtotal) {
        try {
            return ResponseEntity.ok(cuponService.validar(codigo, subtotal));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("error", "No fue posible validar el cupón."));
        }
    }
}
