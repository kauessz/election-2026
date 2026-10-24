package br.com.election.discovery;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class PollsterAliasesTests {
    @Test void mapsExactMdaAlias(){
        assertThat(alias("MDA-PESQUISA DE OPINIAO PUBLICA E CONSULT. ESTATIST. LTDA - EPP")).isEqualTo("cnt-mda");
        assertThat(alias("MDA-PESQUISA DE OPINIAO PUBLICA E CONSULT. ESTATIST. NOVO LTDA - EPP")).isNull();
    }
    @Test void mapsExactQuaestAlias(){
        assertThat(alias("QUAEST PESQUISAS, CONSULTORIA E PROJETOS LTDA.")).isEqualTo("quaest");
        assertThat(alias("QUAEST PESQUISAS, CONSULTORIA E OUTROS PROJETOS LTDA.")).isNull();
    }
    @Test void otherObservedNamesHaveNoImplicitAliases(){
        for(String name:List.of("SIMPLEX CONSULTORIA ECONOMICA E EMPRESARIAL",
            "INSTITUTO FRANCA DE PESQUISA PESQUISA E ASSESSORIA",
            "COLECTTA INSTITUTO DE PESQUISA E ESTATISTICA LTDA","100 CIDADES"))assertThat(alias(name)).as(name).isNull();
    }
    private String alias(String name){return PollsterAliases.slug(TsePollRegistryParser.normalize(name));}
}
