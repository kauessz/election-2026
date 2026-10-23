package br.com.election.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;
import java.net.URI;
import java.util.Arrays;

@Configuration
public class CorsConfiguration implements WebMvcConfigurer {
    private final String[] origins;
    public CorsConfiguration(@Value("${app.cors.allowed-origins:http://localhost:3000}") String csv) {
        origins = Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).distinct().toArray(String[]::new);
        for (String value : origins) {
            URI uri = URI.create(value);
            if (!("https".equals(uri.getScheme()) || "http".equals(uri.getScheme())) || uri.getHost() == null ||
                uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null || !uri.getPath().isEmpty())
                throw new IllegalArgumentException("CORS exige origens HTTP(S) exatas, sem wildcard, credenciais ou caminho.");
        }
    }
    @Override public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**").allowedOrigins(origins)
            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
            .allowedHeaders("Content-Type", "Accept", "X-Admin-Key").allowCredentials(false);
    }
}
