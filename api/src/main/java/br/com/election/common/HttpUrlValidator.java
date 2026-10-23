package br.com.election.common;
import jakarta.validation.*;
import java.net.URI;
public class HttpUrlValidator implements ConstraintValidator<HttpUrl, String> {
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) return true;
        try { URI uri = URI.create(value); return ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme())) && uri.getHost() != null; }
        catch (IllegalArgumentException e) { return false; }
    }
}
