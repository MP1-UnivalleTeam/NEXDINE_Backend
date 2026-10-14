package com.restaurant.app.services;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import com.restaurant.app.config.CloudinaryProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class CloudinaryStorageService {

    private static final Logger log = LoggerFactory.getLogger(CloudinaryStorageService.class);

    private final CloudinaryProperties properties;
    private final ObjectProvider<Cloudinary> cloudinaryProvider;

    public CloudinaryStorageService(CloudinaryProperties properties,
                                   ObjectProvider<Cloudinary> cloudinaryProvider) {
        this.properties = properties;
        this.cloudinaryProvider = cloudinaryProvider;
    }

    public boolean estaDisponible() {
        return properties.estaConfigurado() && obtenerCloudinary() != null;
    }

    public java.util.List<String> variablesFaltantes() {
        return properties.variablesFaltantes();
    }

    public String getCarpeta() {
        return properties.getFolder();
    }

    public ResultadoSubida subirImagen(MultipartFile archivo) {
        Cloudinary cloudinary = obtenerCloudinary();

        if (cloudinary == null) {
            throw new CloudinaryNoConfiguradoException(properties.variablesFaltantes());
        }

        String publicId = "producto_" + UUID.randomUUID();

        Map<String, Object> opciones = new HashMap<>();
        opciones.put("folder", properties.getFolder());
        opciones.put("public_id", publicId);
        opciones.put("resource_type", "image");
        opciones.put("overwrite", false);
        opciones.put("use_filename", false);
        opciones.put("unique_filename", true);

        try {
            Uploader uploader = cloudinary.uploader();
            Map<?, ?> respuesta = uploader.upload(archivo.getBytes(), opciones);

            if (respuesta == null) {
                throw new CloudinaryCommunicationException(
                        "Cloudinary respondio vacio al subir la imagen.");
            }

            Object secureUrl = respuesta.get("secure_url");
            Object devueltoPublicId = respuesta.get("public_id");

            if (secureUrl == null || secureUrl.toString().isBlank()) {
                throw new CloudinaryCommunicationException(
                        "Cloudinary no devolvio una URL segura para la imagen.");
            }

            String url = secureUrl.toString();
            if (!url.startsWith("https://")) {
                url = url.replaceFirst("^http://", "https://");
            }

            String publicIdFinal = devueltoPublicId != null
                    ? devueltoPublicId.toString()
                    : properties.getFolder() + "/" + publicId;

            log.info("NEXDINE | Imagen subida a Cloudinary. public_id={} folder={}",
                    publicIdFinal, properties.getFolder());

            return new ResultadoSubida(url, publicIdFinal);

        } catch (IOException e) {
            throw new CloudinaryCommunicationException(
                    "No fue posible procesar el archivo de imagen recibido.");
        } catch (CloudinaryStorageException e) {
            throw e;
        } catch (RuntimeException e) {
            log.error("NEXDINE | Error comunicando con Cloudinary durante la subida: {}",
                    e.getClass().getSimpleName(), e);
            throw new CloudinaryCommunicationException(
                    "No fue posible subir la imagen a Cloudinary. Intente nuevamente.");
        }
    }

    public boolean eliminarImagen(String publicId) {
        if (publicId == null || publicId.isBlank()) {
            return false;
        }

        if (!esRecursoPropio(publicId)) {
            log.warn("NEXDINE | Se omite la eliminacion: el recurso no pertenece a la carpeta del sistema.");
            return false;
        }

        Cloudinary cloudinary = obtenerCloudinary();
        if (cloudinary == null) {
            log.warn("NEXDINE | No se pudo eliminar el recurso de Cloudinary: credenciales no configuradas.");
            return false;
        }

        try {
            Map<String, Object> opciones = new HashMap<>();
            opciones.put("invalidate", true);
            cloudinary.uploader().destroy(publicId, opciones);

            log.info("NEXDINE | Recurso eliminado de Cloudinary. public_id={}", publicId);
            return true;
        } catch (IOException | RuntimeException e) {
            log.error("NEXDINE | Error eliminando el recurso de Cloudinary {}: {}",
                    publicId, e.getClass().getSimpleName(), e);
            return false;
        }
    }

    public boolean esRecursoPropio(String publicId) {
        if (publicId == null || publicId.isBlank()) {
            return false;
        }
        String carpeta = properties.getFolder();
        return publicId.startsWith(carpeta + "/") || publicId.equals(carpeta);
    }

    private Cloudinary obtenerCloudinary() {
        try {
            return cloudinaryProvider.getIfAvailable();
        } catch (RuntimeException e) {
            log.error("NEXDINE | No se pudo obtener el cliente de Cloudinary: {}",
                    e.getClass().getSimpleName());
            return null;
        }
    }

    public record ResultadoSubida(String url, String publicId) {
    }

    public static class CloudinaryNoConfiguradoException extends RuntimeException {
        private final java.util.List<String> variablesFaltantes;

        public CloudinaryNoConfiguradoException(java.util.List<String> variablesFaltantes) {
            super("El servicio de imagenes no esta configurado. Configure las variables de entorno: "
                    + String.join(", ", variablesFaltantes) + ".");
            this.variablesFaltantes = java.util.List.copyOf(variablesFaltantes);
        }

        public java.util.List<String> getVariablesFaltantes() {
            return variablesFaltantes;
        }
    }

    public static class CloudinaryCommunicationException extends CloudinaryStorageException {
        public CloudinaryCommunicationException(String message) {
            super(message);
        }
    }

    public static class CloudinaryStorageException extends RuntimeException {
        public CloudinaryStorageException(String message) {
            super(message);
        }
    }
}
