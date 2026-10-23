package br.com.election.common;

import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Validate the effective URL (including ServiceConnection overrides) without logging it. */
@Configuration
@Profile("production")
public class ProductionDatabaseConfiguration {
    public ProductionDatabaseConfiguration(JdbcConnectionDetails connection) {
        validate(connection.getJdbcUrl());
    }

    static void validate(String jdbcUrl) {
        URI uri;
        List<String> modes = new ArrayList<>();
        try {
            if (jdbcUrl == null || !jdbcUrl.startsWith("jdbc:postgresql://")) throw new IllegalArgumentException();
            uri = URI.create(jdbcUrl.substring(5));
            if (uri.getHost() == null) throw new IllegalArgumentException();
            if (uri.getRawQuery() != null) for (String item : uri.getRawQuery().split("&")) {
                String[] pair = item.split("=", 2);
                if (URLDecoder.decode(pair[0], StandardCharsets.UTF_8).equals("sslmode")) {
                    if (!pair[0].equals("sslmode")) throw new IllegalArgumentException();
                    modes.add(pair.length == 2 ? URLDecoder.decode(pair[1], StandardCharsets.UTF_8) : "");
                }
            }
        } catch (IllegalArgumentException e) {
            // Never attach the original exception: URI parsing errors may contain credentials.
            throw new IllegalArgumentException("URL JDBC PostgreSQL de produção inválida.");
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        boolean neon = host.equals("neon.tech") || host.endsWith(".neon.tech");
        if (modes.size() > 1 || modes.stream().anyMatch(m -> !Set.of("require", "verify-ca", "verify-full").contains(m)) || (neon && modes.isEmpty()))
            throw new IllegalArgumentException("Produção exige sslmode=require, verify-ca ou verify-full explícito para Neon; modos inseguros ou duplicados não são aceitos.");
    }
}
