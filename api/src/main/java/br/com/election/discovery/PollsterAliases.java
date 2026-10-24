package br.com.election.discovery;

import java.util.Map;

/** Explicit, audited registry aliases; never fuzzy matching or institute creation. */
public final class PollsterAliases {
    private PollsterAliases(){}
    // Confirmed in the official 2026 CSV: BR-09709/2026 (also BR-03112/2026).
    private static final Map<String,String> ALIASES=Map.of(
        TsePollRegistryParser.normalize("DATAFOLHA INSTITUTO DE PESQUISAS LTDA."),"datafolha",
        TsePollRegistryParser.normalize("MDA-PESQUISA DE OPINIAO PUBLICA E CONSULT. ESTATIST. LTDA - EPP"),"cnt-mda",
        TsePollRegistryParser.normalize("QUAEST PESQUISAS, CONSULTORIA E PROJETOS LTDA."),"quaest");
    public static String slug(String normalizedName){return ALIASES.get(normalizedName);}
}
