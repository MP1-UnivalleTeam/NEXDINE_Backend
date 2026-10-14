package com.restaurant.app.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class CloudinaryProperties {

    @Value("${CLOUDINARY_CLOUD_NAME:}")
    private String cloudName;

    @Value("${CLOUDINARY_API_KEY:}")
    private String apiKey;

    @Value("${CLOUDINARY_API_SECRET:}")
    private String apiSecret;

    @Value("${CLOUDINARY_FOLDER:nexdine/productos}")
    private String folder;

    public static final String VAR_CLOUD_NAME = "CLOUDINARY_CLOUD_NAME";
    public static final String VAR_API_KEY = "CLOUDINARY_API_KEY";
    public static final String VAR_API_SECRET = "CLOUDINARY_API_SECRET";

    public String getCloudName() {
        return cloudName == null ? "" : cloudName.trim();
    }

    public String getApiKey() {
        return apiKey == null ? "" : apiKey.trim();
    }

    public String getApiSecret() {
        return apiSecret == null ? "" : apiSecret.trim();
    }

    public String getFolder() {
        return (folder == null || folder.isBlank()) ? "nexdine/productos" : folder.trim();
    }

    public List<String> variablesFaltantes() {
        List<String> faltantes = new ArrayList<>();
        if (getCloudName().isEmpty()) {
            faltantes.add(VAR_CLOUD_NAME);
        }
        if (getApiKey().isEmpty()) {
            faltantes.add(VAR_API_KEY);
        }
        if (getApiSecret().isEmpty()) {
            faltantes.add(VAR_API_SECRET);
        }
        return faltantes;
    }

    public boolean estaConfigurado() {
        return variablesFaltantes().isEmpty();
    }

    @Override
    public String toString() {
        return "CloudinaryProperties{cloudName=" + getCloudName()
                + ", apiKey=" + (getApiKey().isEmpty() ? "ausente" : "presente")
                + ", apiSecret=" + (getApiSecret().isEmpty() ? "ausente" : "presente")
                + ", folder=" + getFolder()
                + ", configurado=" + estaConfigurado() + "}";
    }
}
