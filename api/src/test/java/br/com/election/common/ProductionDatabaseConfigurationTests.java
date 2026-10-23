package br.com.election.common;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProductionDatabaseConfigurationTests {
    private JdbcConnectionDetails connection(String url) {
        var connection = mock(JdbcConnectionDetails.class);
        when(connection.getJdbcUrl()).thenReturn(url);
        return connection;
    }
    @ParameterizedTest @ValueSource(strings={
        "jdbc:postgresql://ep-test.neon.tech:5432/test?sslmode=require",
        "jdbc:postgresql://ep-test-pooler.neon.tech/test?sslmode=require",
        "jdbc:postgresql://ep-test.neon.tech/test?sslmode=verify-ca",
        "jdbc:postgresql://ep-test.NEON.TECH/test?sslmode=verify-full"})
    void acceptsSecureDirectAndPooledEndpointsWithoutConnecting(String url) {
        var details = connection(url);
        assertThatCode(() -> new ProductionDatabaseConfiguration(details)).doesNotThrowAnyException();
        verify(details).getJdbcUrl();verifyNoMoreInteractions(details);
    }
    @ParameterizedTest @ValueSource(strings={
        "jdbc:postgresql://ep-test.neon.tech/test",
        "jdbc:postgresql://ep-test.neon.tech/test?sslmode=disable",
        "jdbc:postgresql://ep-test.neon.tech/test?sslmode=prefer",
        "jdbc:postgresql://ep-test.neon.tech/test?sslmode=disable&sslmode=require",
        "jdbc:postgresql://ep-test.neon.tech/test?%73slmode=require"})
    void rejectsMissingWeakDuplicateOrAmbiguousTls(String url) {
        assertThatThrownBy(() -> new ProductionDatabaseConfiguration(connection(url))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void localProfileIsUnaffected() {
        new ApplicationContextRunner().withUserConfiguration(ProductionDatabaseConfiguration.class)
            .withBean(JdbcConnectionDetails.class, () -> connection("jdbc:postgresql://localhost/test?sslmode=disable"))
            .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(ProductionDatabaseConfiguration.class));
    }
    @Test void productionContextRejectsExplicitDisableForAnyHost() {
        new ApplicationContextRunner().withPropertyValues("spring.profiles.active=production")
            .withUserConfiguration(ProductionDatabaseConfiguration.class)
            .withBean(JdbcConnectionDetails.class, () -> connection("jdbc:postgresql://localhost/test?sslmode=disable"))
            .run(context -> assertThat(context).hasFailed());
    }
    @Test void invalidUrlNeverLeaksCredentialsThroughExceptionOrCause() {
        assertThatThrownBy(() -> new ProductionDatabaseConfiguration(connection("jdbc:postgresql://user:fake-sensitive@bad host/test")))
            .hasMessage("URL JDBC PostgreSQL de produção inválida.").hasNoCause();
    }
    @Test void productionPoolAndBindAddressAreConservative() throws Exception {
        var env = new MockEnvironment();
        for (var source : new YamlPropertySourceLoader().load("production", new ClassPathResource("application-production.yml"))) env.getPropertySources().addLast(source);
        assertThat(env.getProperty("spring.datasource.hikari.maximum-pool-size",Integer.class)).isEqualTo(5);
        assertThat(env.getProperty("spring.datasource.hikari.minimum-idle",Integer.class)).isEqualTo(1);
        assertThat(env.getProperty("server.address")).isEqualTo("0.0.0.0");
    }
}
