package com.restaurant.app.controller;

import com.restaurant.app.model.Usuario;
import com.restaurant.app.services.ReporteService;
import jakarta.servlet.http.HttpSession;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

/** RF017, RF021, RF028, RF042: módulo de reportes. */
@RestController
@RequestMapping("/api/reportes")
public class ReporteController {

    private final ReporteService reporteService;

    public ReporteController(ReporteService reporteService) {
        this.reporteService = reporteService;
    }

    @GetMapping("/ventas")
    public ResponseEntity<?> ventas(@RequestParam String periodo,
                                    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                                    @RequestParam(required = false) UUID sucursalId,
                                    HttpSession session) {
        Usuario usuario = RespuestasApi.usuario(session);
        return RespuestasApi.ejecutar(usuario,
                () -> reporteService.reporteVentas(usuario, periodo, desde, hasta, sucursalId));
    }

    @GetMapping("/diario")
    public ResponseEntity<?> registroDiario(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                                            @RequestParam(required = false) UUID sucursalId,
                                            HttpSession session) {
        Usuario usuario = RespuestasApi.usuario(session);
        return RespuestasApi.ejecutar(usuario,
                () -> reporteService.registroDiario(usuario, desde, hasta, sucursalId));
    }
}
