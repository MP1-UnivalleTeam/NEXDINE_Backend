package com.restaurant.app.config;

import com.cloudinary.Cloudinary;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CloudinaryConfig {

    private static final Logger log = LoggerFactory.getLogger(CloudinaryConfig.class);

    private final CloudinaryProperties properties;

    public CloudinaryConfig(CloudinaryProperties properties) {
        this.properties = properties;
    }

    @Bean
    public Cloudinary cloudinary() {
        if (!properties.estaConfigurado()) {
            log.warn("NEXDINE | Cloudinary deshabilitado. Variables de entorno faltantes: {}. "
                            + "La gestion de imagenes por archivo no estara disponible. "
                            + "El resto del sistema sigue operando con normalidad.",
                    properties.variablesFaltantes());
            return null;
        }

        com.cloudinary.Configuration configuration = new com.cloudinary.Configuration();
        configuration.cloudName = properties.getCloudName();
        configuration.apiKey = properties.getApiKey();
        configuration.apiSecret = properties.getApiSecret();

        configuration.secure = true;

        log.info("NEXDINE | Cloudinary habilitado. cloud={} folder={}",
                properties.getCloudName(), properties.getFolder());

        return new Cloudinary(configuration);
    }
}