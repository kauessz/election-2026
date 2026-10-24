package br.com.election.discovery;

import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.net.URI;
import java.util.*;
import java.util.zip.*;
import java.util.concurrent.Flow;
import static org.assertj.core.api.Assertions.*;

class TsePollRegistryParserTests {
    final TsePollRegistryParser parser=new TsePollRegistryParser(new DiscoveryConfiguration.Properties(false,null));
    static final String HEADER="AA_ELEICAO;SG_UE;SG_UF;DS_CARGO;NR_PROTOCOLO_REGISTRO;NM_EMPRESA;NM_EMPRESA_FANTASIA;DT_INICIO_PESQUISA;DT_FIM_PESQUISA;DT_DIVULGACAO;QT_ENTREVISTADO;DS_METODOLOGIA_PESQUISA;NM_UE;DS_PLANO_AMOSTRAL;DS_DADO_MUNICIPIO\n";
    static final String ROW="2026;BR;BR;Presidente;BR012342026;Empresa sintética;Datafolha;2026-10-19 00:00:00;2026-10-21 00:00:00;2026-10-22 00:00:00;1500;\"SINTÉTICO; teste \"\"citado\"\"\nsegunda linha\";BRASIL;Universo: eleitorado brasileiro;A abrangencia da coleta e nacional.\n";
    byte[] zip(String filename,String csv,Charset charset){try(var bytes=new ByteArrayOutputStream();var zip=new ZipOutputStream(bytes)){
        zip.putNextEntry(new ZipEntry(filename));zip.write(csv.getBytes(charset));zip.closeEntry();zip.finish();return bytes.toByteArray();
    }catch(IOException e){throw new RuntimeException(e);}}
    List<TsePollRegistryParser.Entry> parse(String rows){return parser.parse(zip("pesquisa_eleitoral_2026_BRASIL.csv",HEADER+rows,StandardCharsets.ISO_8859_1));}
    @Test void parsesOfficialLayoutLatin1ZipQuotesAndMultiline(){var rows=parse(ROW);assertThat(rows).hasSize(1);var row=rows.getFirst();
        assertThat(row.registrationNumber()).isEqualTo("BR-01234/2026");assertThat(row.sampleSize()).isEqualTo(1500);
        assertThat(row.methodology()).isEqualTo("SINTÉTICO; teste \"citado\"\nsegunda linha");assertThat(row.metadata()).containsEntry("NR_PROTOCOLO_REGISTRO","BR012342026");}
    @Test void supportsUtf8Bom(){assertThat(parser.parse(zip("pesquisa_eleitoral_2026_BRASIL.csv","\uFEFF"+HEADER+ROW,StandardCharsets.UTF_8))).hasSize(1);}
    @Test void palver01794PassesWithObservedRepresentativeText(){
        String plan="O público-alvo do levantamento será composto por brasileiros a partir dos 16 anos. "
            +"O voto no 2º turno da eleição presidencial de 2022 será calibrado por região, com base nos resultados oficiais do TSE, correspondendo no total nacional a Lula e Jair Bolsonaro.";
        String methodology="A metodologia utilizada é survey quantitativo online por questionário estruturado com amostragem não-probabilística, "
            +"com os dados submetidos a ajuste de pesos iterativo ancorado em dados oficiais do IBGE e do TSE para refletir o perfil da população brasileira.";
        assertThat(TsePollRegistryParser.nationalScope(Map.of("NM_UE","BRASIL","DS_PLANO_AMOSTRAL",plan,"DS_METODOLOGIA_PESQUISA",methodology))).isTrue();
        String row=ROW.replace("BR012342026","BR017942026").replace("Empresa sintética","PALVER CONSULTORIA E DESENVOLVIMENTO TECNOLOGICO LTDA.")
            .replace("Datafolha","Palver").replace("2026-10-19","2026-10-22").replace("2026-10-21","2026-10-24")
            .replace("2026-10-22 00:00:00;1500","2026-10-24 00:00:00;1500")
            .replace("Universo: eleitorado brasileiro",plan).replace("A abrangencia da coleta e nacional.","#NULO#");
        assertThat(parse(row)).extracting(TsePollRegistryParser.Entry::registrationNumber).containsExactly("BR-01794/2026");
    }
    @Test void explicitNationalExpressionsAreRecognizedIndependentlyWithBoundedExactMatching(){
        for(String expression:List.of("O público-alvo do levantamento será composto por brasileiros a partir dos 16 anos",
            "Público alvo: brasileiros", "perfil da população brasileira", "correspondendo no total nacional a", "uma amostra nacional",
            "amostra representativa de todo o Brasil")){
            assertThat(TsePollRegistryParser.nationalScope(Map.of("NM_UE","BRASIL","DS_PLANO_AMOSTRAL",expression))).as(expression).isTrue();
        }
        for(String expression:List.of("público-alvo estrangeiros","população brasileirax","total nacionalizado","amostra nacionalizada",
            "representativa do Brasiliense","público-alvo "+"x".repeat(66)+" brasileiros", "representativa "+"x".repeat(66)+" Brasil"))
            assertThat(TsePollRegistryParser.nationalScope(Map.of("NM_UE","BRASIL","DS_PLANO_AMOSTRAL",expression))).as(expression).isFalse();
    }
    @Test void seta08499RegionalVetoWinsOverEveryNewNationalExpression(){
        String methodology="Pesquisa com metodologia quantitativa, com a realização de entrevistas pessoais domiciliares utilizando questionário estruturado junto a uma amostra representativa do eleitorado do estado do RN.";
        for(String expression:List.of("público-alvo brasileiros","população brasileira","total nacional","amostra nacional","representativa do Brasil")){
            assertThat(TsePollRegistryParser.nationalScope(Map.of("NM_UE","BRASIL","DS_METODOLOGIA_PESQUISA",methodology,"DS_PLANO_AMOSTRAL",expression))).isFalse();
            String row=ROW.replace("BR012342026","BR084992026").replace("Datafolha","Seta")
                .replace("Universo: eleitorado brasileiro",methodology).replace("A abrangencia da coleta e nacional.",expression);
            assertThat(parse(row)).as(expression).isEmpty();
            for(String region:List.of("Estado da Bahia","Distrito Federal"))
                assertThat(TsePollRegistryParser.nationalScope(Map.of("NM_UE","BRASIL","DS_DADO_MUNICIPIO","A abrangência da coleta corresponde ao "+region,"DS_PLANO_AMOSTRAL",expression))).isFalse();
        }
    }
    @Test void newNationalEvidenceNeverBypassesStructuredElectionOfficeOrUnitGates(){
        String row=ROW.replace("Universo: eleitorado brasileiro","público-alvo brasileiros").replace("A abrangencia da coleta e nacional.","#NULO#");
        assertThat(parse(row)).hasSize(1);
        for(String excluded:List.of(row.replace("2026;BR;BR","2024;BR;BR"),row.replace("2026;BR;BR","2026;SP;BR"),
            row.replace("2026;BR;BR","2026;BR;DF"),row.replace(";BRASIL;",";BAHIA;"),row.replace("Presidente","Governador")))assertThat(parse(excluded)).isEmpty();
    }
    @Test void observedBahiaAndDfAreExcludedDespiteBrElectoralUnit() throws Exception {
        String csv=new String(getClass().getResourceAsStream("/discovery/tse-scope-observed.csv").readAllBytes(),StandardCharsets.UTF_8);
        var result=parser.inspect(zip("pesquisa_eleitoral_2026_BRASIL.csv",csv,StandardCharsets.UTF_8));
        assertThat(result.scanned()).isEqualTo(3);
        assertThat(result.entries()).extracting(TsePollRegistryParser.Entry::registrationNumber).containsExactly("BR-09709/2026");
        assertThat(result.entries().getFirst().legalName()).isEqualTo("DATAFOLHA INSTITUTO DE PESQUISAS LTDA.");
    }
    @Test void rejectsUnknownOrContradictoryCoverageAndRegionalStructuredScope(){
        assertThat(parse(ROW.replace("Universo: eleitorado brasileiro","Não informado").replace("A abrangencia da coleta e nacional.","Em anexo"))).isEmpty();
        assertThat(parse(ROW.replace("A abrangencia da coleta e nacional.","A abrangencia da coleta corresponde ao Estado da Bahia."))).isEmpty();
        assertThat(parse(ROW.replace(";BRASIL;",";BAHIA;"))).isEmpty();
        assertThat(parse(ROW.replace("Presidente","Senador"))).isEmpty();
        assertThat(parse(ROW.replace("Universo: eleitorado brasileiro","Universo: eleitorado de Brasil").replace("A abrangencia da coleta e nacional.","Em anexo"))).hasSize(1);
    }
    @Test void methodologyIsOnlyConservativeFallbackAndCannotOverrideRegionalMetadata(){
        String fallback=ROW.replace("Universo: eleitorado brasileiro","Em anexo").replace("A abrangencia da coleta e nacional.","Em anexo")
            .replace("segunda linha","O universo da pesquisa e o eleitorado brasileiro");
        assertThat(parse(fallback)).hasSize(1);
        assertThat(parse(fallback.replace(";Em anexo;Em anexo",";Em anexo;A abrangencia da coleta corresponde ao Distrito Federal"))).isEmpty();
    }
    @Test void operationalCutoffUsesPublicationDateAndIsConfigurable(){
        var later=new TsePollRegistryParser(new DiscoveryConfiguration.Properties(false,null,java.time.LocalDate.parse("2026-10-23")));
        assertThat(later.parse(zip("pesquisa_eleitoral_2026_BRASIL.csv",HEADER+ROW,StandardCharsets.UTF_8))).isEmpty();
        assertThat(later.parse(zip("pesquisa_eleitoral_2026_BRASIL.csv",HEADER+ROW.replace("2026-10-22","2026-10-23"),StandardCharsets.UTF_8))).hasSize(1);
        assertThat(new DiscoveryConfiguration.Properties(false,null).operationalFrom()).isEqualTo(java.time.LocalDate.parse("2026-10-05"));
    }
    @Test void aliasesAreExplicitAndNeverFuzzy(){
        assertThat(PollsterAliases.slug(TsePollRegistryParser.normalize("DATAFOLHA INSTITUTO DE PESQUISAS LTDA."))).isEqualTo("datafolha");
        assertThat(PollsterAliases.slug(TsePollRegistryParser.normalize("DATAFOLHA INSTITUTO DE PESQUISAS NOVO LTDA."))).isNull();
    }
    @Test void excludesStatesOtherYearsOfficesAndFirstRound(){
        for(String row:List.of(ROW.replace("2026;BR;BR","2024;BR;BR"),ROW.replace("2026;BR;BR","2026;SP;SP"),ROW.replace("Presidente","Governador"),
            ROW.replace("2026-10-19","2026-10-01"),ROW.replace("2026-10-21","2026-10-26")))assertThat(parse(row)).isEmpty();}
    @Test void exactPresidentialOfficeAndNationalScopeRequired(){assertThat(parse(ROW.replace("Presidente","Presidente da República"))).hasSize(1);
        assertThat(parse(ROW.replace("Presidente","Presidente, Governador"))).isEmpty();assertThat(parse(ROW.replace("2026;BR;BR","2026;SP;BR"))).isEmpty();}
    @Test void deduplicatesIdenticalRegistryRows(){assertThat(parse(ROW+ROW)).hasSize(1);}
    @Test void conflictingRegistryAbortsWholeParse(){assertThatThrownBy(()->parse(ROW+ROW.replace("1500","2000"))).hasMessageContaining("conflitante");}
    @Test void missingHeaderOrWrongZipMemberFailsClosed(){assertThatThrownBy(()->parser.parse(zip("pesquisa_eleitoral_2026_BRASIL.csv",ROW,StandardCharsets.UTF_8))).hasMessageContaining("Cabeçalho");
        assertThatThrownBy(()->parser.parse(zip("untrusted.csv",HEADER+ROW,StandardCharsets.UTF_8))).hasMessageContaining("não encontrado");}
    @Test void rejectsMalformedCsvDatesAndProtocols(){assertThatThrownBy(()->parse(ROW.replace("segunda linha\"","segunda linha"))).hasMessageContaining("aspas");
        assertThatThrownBy(()->parse(ROW.replace("2026-10-19","invalid-date"))).hasMessageContaining("Data");
        assertThatThrownBy(()->parse(ROW.replace("BR012342026","SP012342026"))).hasMessageContaining("Registro");}
    @Test void rejectsLargeCellAndCompressedInput(){assertThatThrownBy(()->parse(ROW.replace("Datafolha","A".repeat(30001)))).hasMessageContaining("limite");
        assertThatThrownBy(()->parser.parse(new byte[TsePollRegistryClient.MAX_ZIP+1])).hasMessageContaining("limite");}
    @Test void rejectsTooManyZipEntries(){try(var bytes=new ByteArrayOutputStream();var z=new ZipOutputStream(bytes)){
        for(int i=0;i<65;i++){z.putNextEntry(new ZipEntry("entry"+i));z.closeEntry();}z.finish();assertThatThrownBy(()->parser.parse(bytes.toByteArray())).hasMessageContaining("entradas");
    }catch(IOException e){throw new RuntimeException(e);}}
    @Test void acceptsOnlyOfficialFixedHttpsUrl(){
        for(String uri:List.of("http://cdn.tse.jus.br/file.zip","https://attacker.example/file.zip","https://localhost/file.zip",
            "https://cdn.tse.jus.br:444/estatistica/sead/odsele/pesquisa_eleitoral/pesquisa_eleitoral_2026.zip",
            "https://cdn.tse.jus.br/estatistica/sead/odsele/pesquisa_eleitoral/pesquisa_eleitoral_2026.zip?redirect=1"))
            assertThatThrownBy(()->new DiscoveryConfiguration.Properties(true,URI.create(uri))).isInstanceOf(IllegalArgumentException.class);
        assertThat(new DiscoveryConfiguration.Properties(true,null).tseOpenDataUrl().getHost()).isEqualTo("cdn.tse.jus.br");}
    @Test void boundedBodyCancelsAsSoonAsLimitIsExceeded(){var body=new TsePollRegistryClient.LimitedBody(4);boolean[] cancelled={false};
        body.onSubscribe(new Flow.Subscription(){public void request(long n){}public void cancel(){cancelled[0]=true;}});
        body.onNext(List.of(ByteBuffer.wrap(new byte[5])));assertThat(cancelled[0]).isTrue();assertThat(body.getBody().toCompletableFuture()).isCompletedExceptionally();}
    @Test void disabledClientNeverAccessesNetwork(){assertThatThrownBy(()->new TsePollRegistryClient(new DiscoveryConfiguration.Properties(false,null)).download()).hasMessageContaining("desabilitada");}
}
