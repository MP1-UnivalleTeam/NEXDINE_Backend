package com.restaurant.app.controller;

import com.restaurant.app.model.Usuario;
import com.restaurant.app.services.CorreoService;
import com.restaurant.app.services.FacturacionService;
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
import java.util.function.Supplier;

/** RF030: facturación electrónica. */
@RestController
@RequestMapping("/api")
public class FacturacionController {

    private final FacturacionService facturacionService;

    public FacturacionController(FacturacionService facturacionService) {
        this.facturacionService = facturacionService;
    }

    @GetMapping("/facturacion/emisor")
    public ResponseEntity<?> obtenerEmisor(HttpSession session) {
        Usuario usuario = RespuestasApi.usuario(session);
        return RespuestasApi.ejecutar(usuario, () -> facturacionService.obtenerEmisor(usuario));
    }

    @PutMapping("/facturacion/emisor")
    public ResponseEntity<?> guardarEmisor(@RequestBody Map<String, Object> datos, HttpSession session) {
        Usuario usuario = RespuestasApi.usuario(session);
        return RespuestasApi.ejecutar(usuario, () -> facturacionService.guardarEmisor(usuario, datos));
    }

    @PostMapping("/pedidos/{pedidoId}/factura")
    public ResponseEntity<?> emitir(@PathVariable UUID pedidoId,
                                    @RequestBody(required = false) Map<String, Object> cliente,
                                    HttpSession session) {
        Usuario usuario = RespuestasApi.usuario(session);
        return RespuestasApi.ejecutar(usuario, () -> facturacionService.emitir(usuario, pedidoId, cliente));
    }

    @PostMapping(value = "/facturas/enviar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> enviar(@RequestParam UUID pedidoId,
                                    @RequestParam String numero,
                                    @RequestParam String correo,
                                    @RequestParam("archivo") MultipartFile archivo,
                                    HttpSession session) {
        Usuario usuario = RespuestasApi.usuario(session);
        Supplier<Object> accion = () -> facturacionService.enviar(usuario, pedidoId, numero, correo, archivo);
        return RespuestasApi.ejecutar(usuario, accion);
    }
}
