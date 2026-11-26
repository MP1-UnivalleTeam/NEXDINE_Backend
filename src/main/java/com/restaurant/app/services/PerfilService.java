package com.restaurant.app.services;

import com.restaurant.app.model.Usuario;
import com.restaurant.app.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * RF029: el usuario personaliza su perfil con foto, color de acento y tema
 * (claro, oscuro o el del sistema), además de sus datos de contacto.
 */
@Service
public class PerfilService {

    private static final Logger logger = LoggerFactory.getLogger(PerfilService.class);
    private static final Set<String> TEMAS = Set.of("CLARO", "OSCURO", "SISTEMA");

    private final UserRepository userRepository;
    private final CloudinaryStorageService cloudinaryStorageService;
    private final ImagenProductoValidador imagenValidador;

    public PerfilService(UserRepository userRepository,
                         CloudinaryStorageService cloudinaryStorageService,
                         ImagenProductoValidador imagenValidador) {
        this.userRepository = userRepository;
        this.cloudinaryStorageService = cloudinaryStorageService;
        this.imagenValidador = imagenValidador;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> obtener(Usuario actual) {
        return aMapa(recargar(actual));
    }

    @Transactional
    public Usuario actualizar(Usuario actual, Map<String, Object> datos) {
        Usuario usuario = recargar(actual);
        if (datos.containsKey("celular")) {
            String celular = FacturacionService.opcional(datos.get("celular"), 20);
            if (celular != null && !celular.matches("[0-9+ ]{7,20}")) {
                throw new IllegalArgumentException("El celular solo admite números (7 a 20).");
            }
            usuario.setCelular(celular);
        }
        if (datos.containsKey("direccion")) {
            usuario.setDireccion(FacturacionService.opcional(datos.get("direccion"), 200));
        }
        if (datos.containsKey("correo")) {
            String correo = CorreoService.normalizarCorreo(FacturacionService.opcional(datos.get("correo"), 150));
            if (correo != null && userRepository.existsByCorreoIgnoreCaseAndIdNot(correo, usuario.getId())) {
                throw new IllegalArgumentException("Ese correo ya está registrado en otra cuenta.");
            }
            usuario.setCorreo(correo);
        }
        if (datos.containsKey("colorAcento")) {
            String color = FacturacionService.opcional(datos.get("colorAcento"), 7);
            if (color != null && !color.matches("^#[0-9A-Fa-f]{6}$")) {
                throw new IllegalArgumentException("El color debe tener el formato #RRGGBB.");
            }
            usuario.setColorAcento(color == null ? null : color.toUpperCase());
        }
        if (datos.containsKey("tema")) {
            String tema = FacturacionService.opcional(datos.get("tema"), 10);
            if (tema != null && !TEMAS.contains(tema.toUpperCase())) {
                throw new IllegalArgumentException("El tema debe ser CLARO, OSCURO o SISTEMA.");
            }
            usuario.setTema(tema == null ? null : tema.toUpperCase());
        }
        return userRepository.save(usuario);
    }

    @Transactional
    public Usuario cambiarFoto(Usuario actual, MultipartFile archivo) {
        Usuario usuario = recargar(actual);
        try {
            imagenValidador.validar(archivo);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException(e.getMessage());
        }
        if (!cloudinaryStorageService.estaDisponible()) {
            throw new CloudinaryStorageService.CloudinaryNoConfiguradoException(
                    cloudinaryStorageService.variablesFaltantes());
        }
        CloudinaryStorageService.ResultadoSubida subida = cloudinaryStorageService.subirImagen(archivo);
        String anterior = usuario.getAvatarPublicId();
        usuario.setAvatarUrl(subida.url());
        usuario.setAvatarPublicId(subida.publicId());
        Usuario guardado = userRepository.save(usuario);
        eliminarSilencioso(anterior);
        return guardado;
    }

    @Transactional
    public Usuario quitarFoto(Usuario actual) {
        Usuario usuario = recargar(actual);
        String anterior = usuario.getAvatarPublicId();
        usuario.setAvatarUrl(null);
        usuario.setAvatarPublicId(null);
        Usuario guardado = userRepository.save(usuario);
        eliminarSilencioso(anterior);
        return guardado;
    }

    public Map<String, Object> aMapa(Usuario u) {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("id", u.getId());
        datos.put("nombre", u.getNombre());
        datos.put("rol", u.getRol());
        datos.put("correo", u.getCorreo() != null ? u.getCorreo() : "");
        datos.put("celular", u.getCelular() != null ? u.getCelular() : "");
        datos.put("direccion", u.getDireccion() != null ? u.getDireccion() : "");
        datos.put("avatarUrl", u.getAvatarUrl() != null ? u.getAvatarUrl() : "");
        datos.put("colorAcento", u.getColorAcento() != null ? u.getColorAcento() : "");
        datos.put("tema", u.getTema() != null ? u.getTema() : "SISTEMA");
        datos.put("subidaFotoDisponible", cloudinaryStorageService.estaDisponible());
        return datos;
    }

    private Usuario recargar(Usuario actual) {
        if (actual == null) {
            throw new IllegalStateException("Usuario no autenticado");
        }
        return userRepository.findById(actual.getId())
                .orElseThrow(() -> new IllegalStateException("La cuenta ya no existe."));
    }

    private void eliminarSilencioso(String publicId) {
        if (publicId == null || publicId.isBlank()) {
            return;
        }
        try {
            cloudinaryStorageService.eliminarImagen(publicId);
        } catch (RuntimeException e) {
            logger.warn("NEXDINE | No se pudo borrar la foto anterior del perfil: {}", e.getMessage());
        }
    }
}
