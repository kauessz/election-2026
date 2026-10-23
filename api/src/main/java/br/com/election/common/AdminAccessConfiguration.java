package br.com.election.common;

import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Collections;

@Configuration
public class AdminAccessConfiguration implements WebMvcConfigurer {
    private final byte[] expectedDigest;

    public AdminAccessConfiguration(@Value("${app.admin.api-key:}") String key, Environment environment) {
        if (key.isBlank() && environment.acceptsProfiles(Profiles.of("production")))
            throw new IllegalStateException("ADMIN_API_KEY é obrigatória no profile production.");
        expectedDigest = key.isBlank() ? null : digest(key);
    }

    private static byte[] digest(String value) {
        try { return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 indisponível."); }
    }

    @Override public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
                // Preflight carries header names, never the secret; MVC still validates its origin.
                if (expectedDigest == null || CorsUtils.isPreFlightRequest(request)) return true;
                var headers = Collections.list(request.getHeaders("X-Admin-Key"));
                int status = headers.isEmpty() || headers.getFirst().isBlank() ? 401 :
                    headers.size() == 1 && MessageDigest.isEqual(expectedDigest, digest(headers.getFirst())) ? 200 : 403;
                if (status == 200) return true;
                response.setStatus(status);
                response.setContentType("application/problem+json");
                response.getWriter().write("{\"status\":" + status + ",\"title\":\"Acesso administrativo negado\"}");
                return false;
            }
        }).addPathPatterns("/api/admin", "/api/admin/**");
    }
}
