package com.restaurant.app.controller;

import com.restaurant.app.model.Usuario;
import com.restaurant.app.services.HorarioService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** RF044: horarios de servicio por sucursal. */
@RestController
@RequestMapping("/api/horarios")
public class HorarioController {

    private final HorarioService horarioService;

    public HorarioController(HorarioService horarioService) {
        this.horarioService = horarioService;
    }

    /** Público: el menú lo consulta para avisar al cliente si la sucursal está cerrada. */
    @GetMapping("/sucursal/{sucursalId}")
    public ResponseEntity<?> consultar(@PathVariable UUID sucursalId) {
        return RespuestasApi.ejecutarSinSesion(() -> horarioService.consultar(sucursalId));
    }

    @PutMapping("/sucursal/{sucursalId}")
    public ResponseEntity<?> guardar(@PathVariable UUID sucursalId,
                                     @RequestBody List<Map<String, Object>> horarios,
                                     HttpSession session) {
        Usuario usuario = RespuestasApi.usuario(session);
        return RespuestasApi.ejecutar(usuario, () -> horarioService.guardar(usuario, sucursalId, horarios));
    }
}
