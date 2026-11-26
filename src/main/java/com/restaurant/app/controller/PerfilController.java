package com.restaurant.app.controller;

import com.restaurant.app.model.Usuario;
import com.restaurant.app.services.CloudinaryStorageService;
import com.restaurant.app.services.PerfilService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/** RF029: perfil del usuario que inició sesión. */
@RestController
@RequestMapping("/api/perfil")
public class PerfilController {

    private final PerfilService perfilService;

    public PerfilController(PerfilService perfilService) {
        this.perfilService = perfilService;
    }

    @GetMapping
    public ResponseEntity<?> obtener(HttpSession session) {
        Usuario usuario = RespuestasApi.usuario(session);
        return RespuestasApi.ejecutar(usuario, () -> perfilService.obtener(usuario));
    }

    @PutMapping
    public ResponseEntity<?> actualizar(@RequestBody Map<String, Object> datos, HttpSession session) {
        Usuario usuario = RespuestasApi.usuario(session);
        return RespuestasApi.ejecutar(usuario,
                () -> refrescarSesion(session, perfilService.actualizar(usuario, datos)));
    }

    @PostMapping(value = "/foto", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> cambiarFoto(@RequestParam("archivo") MultipartFile archivo, HttpSession session) {
        Usuario usuario = RespuestasApi.usuario(session);
        try {
            return RespuestasApi.ejecutar(usuario,
                    () -> refrescarSesion(session, perfilService.cambiarFoto(usuario, archivo)));
        } catch (CloudinaryStorageService.CloudinaryNoConfiguradoException e) {
            return ResponseEntity.status(503).body(Map.of(
                    "error", "La subida de imágenes no está configurada en el servidor.",
                    "codigo", "NO_CONFIGURADO"));
        } catch (CloudinaryStorageService.CloudinaryStorageException e) {
            return ResponseEntity.status(502).body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/foto")
    public ResponseEntity<?> quitarFoto(HttpSession session) {
        Usuario usuario = RespuestasApi.usuario(session);
        return RespuestasApi.ejecutar(usuario,
                () -> refrescarSesion(session, perfilService.quitarFoto(usuario)));
    }

    /** La sesión guarda una copia del usuario: se reemplaza para que /auth/me refleje los cambios. */
    private Map<String, Object> refrescarSesion(HttpSession session, Usuario actualizado) {
        Object anterior = session.getAttribute("currentUser");
        if (anterior instanceof Usuario previo) {
            actualizado.setSucursalId(previo.getSucursalId());
        }
        session.setAttribute("currentUser", actualizado);
        return perfilService.aMapa(actualizado);
    }
}
