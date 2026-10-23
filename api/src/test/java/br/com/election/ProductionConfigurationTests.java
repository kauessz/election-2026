package br.com.election;

import br.com.election.common.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;
import static org.assertj.core.api.Assertions.*;

class ProductionConfigurationTests {
    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(AdminAccessConfiguration.class);
    @Test void productionRejectsMissingAndWhitespaceKeyWithoutLeakingValue() {
        for (String key : new String[]{"", "   "}) runner.withPropertyValues("spring.profiles.active=production", "app.admin.api-key=" + key)
            .run(c -> assertThat(c).hasFailed().getFailure().hasRootCauseMessage("ADMIN_API_KEY é obrigatória no profile production."));
    }
    @Test void productionAcceptsKeyAndLocalDevelopmentAllowsEmptyKey() {
        runner.withPropertyValues("spring.profiles.active=production", "app.admin.api-key=test-only-key").run(c -> assertThat(c).hasNotFailed());
        runner.run(c -> assertThat(c).hasNotFailed());
    }
    @Test void rejectsWildcardAndNonOriginCorsValues() {
        for (var value : new String[]{"*", "https://*.example.org", "https://example.org/path", "https://user:secret@example.org"})
            assertThatThrownBy(() -> new CorsConfiguration(value)).isInstanceOf(IllegalArgumentException.class);
        assertThatCode(() -> new CorsConfiguration("https://one.example.org, https://two.example.org")).doesNotThrowAnyException();
    }
    @Test void portAndDatasourceRespectEnvironmentAndKeepLocalDefaults() throws Exception {
        var env = new MockEnvironment();
        for (var source : new YamlPropertySourceLoader().load("app", new ClassPathResource("application.yaml"))) env.getPropertySources().addLast(source);
        assertThat(env.getProperty("server.port")).isEqualTo("8081");
        assertThat(env.getProperty("spring.datasource.url")).isEqualTo("jdbc:postgresql://localhost:5433/election");
        env.setProperty("PORT", "9123");
        env.setProperty("SPRING_DATASOURCE_URL", "jdbc:postgresql://db.internal/election");
        env.setProperty("SPRING_DATASOURCE_USERNAME", "test-user");
        env.setProperty("SPRING_DATASOURCE_PASSWORD", "test-only-password");
        assertThat(env.getProperty("server.port")).isEqualTo("9123");
        assertThat(env.getProperty("spring.datasource.url")).isEqualTo("jdbc:postgresql://db.internal/election");
        assertThat(env.getProperty("spring.datasource.username")).isEqualTo("test-user");
        assertThat(env.getProperty("spring.datasource.password")).isEqualTo("test-only-password");
        assertThat(env.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
        assertThat(env.getProperty("spring.flyway.enabled")).isEqualTo("true");
    }
}
