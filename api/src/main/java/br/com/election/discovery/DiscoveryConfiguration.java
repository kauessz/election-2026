package br.com.election.discovery;

import org.springframework.context.annotation.*;
import org.springframework.boot.context.properties.*;
import java.time.*;
import java.net.URI;

@Configuration
@EnableConfigurationProperties(DiscoveryConfiguration.Properties.class)
public class DiscoveryConfiguration {
    public static final ZoneId ZONE=ZoneId.of("America/Sao_Paulo");
    @ConfigurationProperties("polling.discovery")
    public record Properties(boolean enabled, URI tseOpenDataUrl, LocalDate operationalFrom) {
        public Properties(boolean enabled,URI tseOpenDataUrl){this(enabled,tseOpenDataUrl,null);}
        @org.springframework.boot.context.properties.bind.ConstructorBinding
        public Properties {
            if(operationalFrom==null)operationalFrom=LocalDate.of(2026,10,5);
            if(tseOpenDataUrl==null) tseOpenDataUrl=URI.create("https://cdn.tse.jus.br/estatistica/sead/odsele/pesquisa_eleitoral/pesquisa_eleitoral_2026.zip");
            if(!"https".equals(tseOpenDataUrl.getScheme()) || !"cdn.tse.jus.br".equals(tseOpenDataUrl.getHost())
                || (tseOpenDataUrl.getPort()!=-1 && tseOpenDataUrl.getPort()!=443) || tseOpenDataUrl.getUserInfo()!=null
                || tseOpenDataUrl.getQuery()!=null || tseOpenDataUrl.getFragment()!=null
                || !tseOpenDataUrl.getPath().matches("/estatistica/sead/odsele/pesquisa_eleitoral/pesquisa_eleitoral_2026\\.zip"))
                throw new IllegalArgumentException("A descoberta aceita somente o ZIP oficial HTTPS do TSE de 2026.");
        }
    }
}
