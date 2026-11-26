package com.restaurant.app.controller;

import com.restaurant.app.model.Usuario;
import com.restaurant.app.services.ReembolsoService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

/** RF032: reembolsos a Nequi o tarjeta. Solo administradores. */
@RestController
@RequestMapping("/api")
public class ReembolsoController {

    private final ReembolsoService reembolsoService;

    public ReembolsoController(ReembolsoService reembolsoService) {
        this.reembolsoService = reembolsoService;
    }

    @GetMapping("/reembolsos")
    public ResponseEntity<?> listar(HttpSession session) {
        Usuario usuario = RespuestasApi.usuario(session);
        return RespuestasApi.ejecutar(usuario, () -> reembolsoService.listar(usuario));
    }

    @GetMapping("/reembolsos/pendientes")
    public ResponseEntity<?> pendientes(HttpSession session) {
        Usuario usuario = RespuestasApi.usuario(session);
        return RespuestasApi.ejecutar(usuario, () -> reembolsoService.pendientes(usuario));
    }

    @GetMapping("/pedidos/{pedidoId}/reembolsos")
    public ResponseEntity<?> listarDePedido(@PathVariable UUID pedidoId, HttpSession session) {
        Usuario usuario = RespuestasApi.usuario(session);
        return RespuestasApi.ejecutar(usuario, () -> reembolsoService.listarDePedido(usuario, pedidoId));
    }

    @PostMapping("/pedidos/{pedidoId}/reembolsos")
    public ResponseEntity<?> crear(@PathVariable UUID pedidoId,
                                   @RequestBody Map<String, Object> datos,
                                   HttpSession session) {
        Usuario usuario = RespuestasApi.usuario(session);
        return RespuestasApi.ejecutar(usuario, () -> reembolsoService.crear(usuario, pedidoId, datos));
    }

    @PutMapping("/reembolsos/{reembolsoId}/estado")
    public ResponseEntity<?> actualizarEstado(@PathVariable UUID reembolsoId,
                                              @RequestParam String estado,
                                              @RequestParam(required = false) String referencia,
                                              HttpSession session) {
        Usuario usuario = RespuestasApi.usuario(session);
        return RespuestasApi.ejecutar(usuario,
                () -> reembolsoService.actualizarEstado(usuario, reembolsoId, estado, referencia));
    }

    @PostMapping(value = "/reembolsos/{reembolsoId}/enviar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> enviarComprobante(@PathVariable UUID reembolsoId,
                                               @RequestParam String correo,
                                               @RequestParam("archivo") MultipartFile archivo,
                                               HttpSession session) {
        Usuario usuario = RespuestasApi.usuario(session);
        return RespuestasApi.ejecutar(usuario,
                () -> reembolsoService.enviarComprobante(usuario, reembolsoId, correo, archivo));
    }
}
