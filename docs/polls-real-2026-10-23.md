# Preparação operacional — revisão de 23/10/2026

**Somente dry run. 18 registros aceitos; nenhuma pendência de metadados. Não executar importação real.**

Foram cadastrados exclusivamente os cinco institutos autorizados. Nenhuma pesquisa, performance, snapshot ou freeze foi criado. O código e as fórmulas do agregador não foram alterados.

## Institutos cadastrados

| Nome | Slug exato |
|---|---|
| DataTrends | datatrends |
| Vox Brasil | vox-brasil |
| Instituto França | instituto-franca |
| Meio/Ideia | meio-ideia |
| Alfa Inteligência | alfa-inteligencia |

GET /api/pollsters confirma os cinco cadastros ativos. O arquivo `data/polls.real.2026-10-23.json` agora usa esses slugs, preservando todos os valores eleitorais, amostras, margens, bases e campos fornecidos pelo usuário. Continuam exatamente as chaves do schema do importador. Nesta revisão final foram preenchidos exclusivamente sourceUrl/publishedAt de DataTrends e Instituto França; os demais registros e valores foram preservados.

## Metadados resolvidos

### Alfa Inteligência — BR-08496/2026

Publicação pública correspondente: [Rádio Itatiaia](https://www.itatiaia.com.br/politica/eleicoes/pesquisas/alfa-inteligencia-flavio-bolsonaro-tem-52-dos-validos-lula-48-no-2o-turno/). `publishedAt = 2026-10-23T08:01:43-03:00`, extraído de JSON-LD datePublished e article:published_time, concordantes e com offset explícito. A página identifica o mesmo registro BR-08496/2026. Não foi usado o horário de atualização. A amostra fornecida pelo usuário (2.700) foi mantida; a divergência anterior com agendas cadastrais (1.200) não motivou alteração silenciosa dos valores.

### DataTrends — BR-04454/2026

Primeiro horário público verificável adotado: **19/10/2026 às 13:07**, conforme a [publicação do Blog do BG](https://www.blogdobg.com.br/pesquisa-datatrends-flavio-bolsonaro-tem-515-dos-votos-validos-contra-485-de-lula/), da mesma rodada BR-04454/2026, campo 15–17/10, n=2.000 e MOE 2,19. Usado exatamente `publishedAt = 2026-10-19T13:07:00-03:00`, fornecido pelo usuário e confirmado pelo horário público visível.

**Divergência preservada:** a [página atual do próprio Instituto DataTrends](https://institutodatatrends.com/pesquisas/62535/datatrends-flavio-bolsonaro-tem-51-5-dos-votos-validos-contra-48-5-de-lula-na-disputa-presidencial) exibe 20/10/2026. Essa data posterior não substitui a publicação pública verificável de 19/10 às 13:07. O elemento datetime incompatível de 2024 e o relógio corrente do cabeçalho não foram utilizados.

### Instituto França — BR-07896/2026

Publicação correspondente no [FanF1](https://fanf1.com.br/2026/10/20/instituto-franca-flavio-tem-508-e-lula-492-indecisos-sao-quase-9/) exibe **20/10/2026 às 07:09**. Usado exatamente `publishedAt = 2026-10-20T07:09:00-03:00`, conforme indicado pelo usuário e confirmado na página. Preservados os percentuais exatos 50,81% / 49,19%, também informados no corpo da matéria, sem substituí-los pelos arredondamentos do título. O [PDF anteriormente localizado](https://static.poder360.com.br/uploads/2026/10/Pesquisa_Presidente_2T_Brasil_Out2026_Instituto_Franca.pdf) permanece referência de auditoria; não se inferiu horário pela criação/modificação do PDF.

**Schema preservado:** publishedAt é Instant obrigatório. Ambos os horários agora têm evidência pública e offset explícito; nenhuma data isolada foi transformada arbitrariamente em meia-noite.

## Fontes e horários

publishedAt refere-se à publicação apontada ou confirmada na fonte, com offset explicitamente declarado; não representa necessariamente a primeira divulgação em toda a imprensa. Mantidos os metadados já resolvidos anteriormente. `methodology` permanece null, sem descrição inventada.

| Instituto | Slug | Registro | publishedAt | Fonte | Dry run |
|---|---|---|---|---|---|
| PoderData/Aya | `poderdata` | BR-07926/2026 | `2026-10-22T07:15:27-03:00` | [publicação](https://www.poder360.com.br/poderdata/flavio-tem-51-e-lula-tem-49-dos-votos-validos-diz-poderdata-aya/) | CREATED |
| Datafolha | `datafolha` | BR-09709/2026 | `2026-10-22T19:07:02-03:00` | [publicação](https://www.cnnbrasil.com.br/eleicoes/datafolha-flavio-e-lula-empatam-em-50-de-votos-validos-no-2o-turno/) | CREATED |
| Indexa/Broadcast | `indexa` | BR-02348/2026 | `2026-10-22T13:36:12-03:00` | [publicação](https://portaln10.com.br/politica/eleicoes/indexa-broadcast-mostra-flavio-com-506-e-lula-com-494-diferenca-cai-na-reta-final-do-2o-turno-355944/) | CREATED |
| CNT/MDA | `cnt-mda` | BR-00652/2026 | `2026-10-16T15:44:16.000Z` | [publicação](https://www.gazetadopovo.com.br/eleicoes/2026/pesquisa-eleitoral-2026/cnt-mda-presidente-outubro-2026-segundo-turno/) | CREATED |
| Quaest | `quaest` | BR-08535/2026 | `2026-10-21T10:56:45-03:00` | [publicação](https://www.poder360.com.br/poder-eleicoes-2026/flavio-tem-45-contra-44-de-lula-no-2o-turno-diz-quaest/) | CREATED |
| Palver | `palver` | BR-04612/2026 | `2026-10-21T09:41:14-03:00` | [publicação](https://www.jota.info/eleicoes/eleicoes-2026/flavio-bolsonaro-tem-51-dos-votos-validos-ante-49-de-lula-diz-pesquisa-palver) | CREATED |
| BTG/Nexus | `nexus` | BR-04499/2026 | `2026-10-19T11:45:25+00:00` | [publicação](https://www.nexus.fsb.com.br/estudos-divulgados/pesquisa-btg-nexus-de-intencao-de-votos-para-presidente-do-brasil-19-de-outubro-de-2026/) | CREATED |
| Gerp | `gerp` | BR-03835/2026 | `2026-10-21T18:00:56-03:00` | [publicação](https://www.cnnbrasil.com.br/eleicoes/gerp-flavio-tem-53-dos-votos-validos-no-2o-turno-lula-47-2/) | CREATED |
| Futura | `futura` | BR-06614/2026 | `2026-10-21T10:45:11-03:00` | [publicação](https://www.poder360.com.br/poder-pesquisas/flavio-tem-511-dos-votos-validos-contra-489-de-lula-diz-futura/) | CREATED |
| DataTrends | `datatrends` | BR-04454/2026 | `2026-10-19T13:07:00-03:00` | [publicação](https://www.blogdobg.com.br/pesquisa-datatrends-flavio-bolsonaro-tem-515-dos-votos-validos-contra-485-de-lula/) | CREATED |
| Ipespe/Abrapel | `ipespe` | BR-04350/2026 | `2026-10-23T10:00:54-03:00` | [publicação](https://www.cnnbrasil.com.br/eleicoes/ipespe-abrapel-flavio-tem-505-dos-votos-validos-lula-495/?utm_source=chatgpt.com) | CREATED |
| AtlasIntel | `atlasintel` | BR-02418/2026 | `2026-10-19T07:30:21-03:00` | [publicação](https://www.cnnbrasil.com.br/eleicoes/atlas-flavio-tem-511-dos-votos-validos-no-2o-turno-lula-489/) | CREATED |
| Vox Brasil | `vox-brasil` | BR-03259/2026 | `2026-10-23T00:00:35-03:00` | [publicação](https://www.cnnbrasil.com.br/eleicoes/vox-lula-tem-506-dos-votos-validos-no-2o-turno-flavio-494/) | CREATED |
| Real Time Big Data | `real-time-big-data` | BR-09670/2026 | `2026-10-23T07:00:21-03:00` | [publicação](https://www.cnnbrasil.com.br/eleicoes/real-time-flavio-tem-52-dos-votos-validos-no-2o-turno-lula-48/) | CREATED |
| Instituto França | `instituto-franca` | BR-07896/2026 | `2026-10-20T07:09:00-03:00` | [publicação](https://fanf1.com.br/2026/10/20/instituto-franca-flavio-tem-508-e-lula-492-indecisos-sao-quase-9/) | CREATED |
| Meio/Ideia | `meio-ideia` | BR-08251/2026 | `2026-10-21T07:00:15-03:00` | [publicação](https://www.cnnbrasil.com.br/eleicoes/meio-ideia-flavio-tem-511-dos-votos-validos-no-2o-turno-lula-489/) | CREATED |
| Veritá | `verita` | BR-06396/2026 | `2026-10-21T12:00:12-03:00` | [publicação](https://www.cnnbrasil.com.br/eleicoes/verita-flavio-tem-507-dos-votos-validos-no-2o-turno-lula-493/?utm_source=chatgpt.com) | CREATED |
| Alfa Inteligência | `alfa-inteligencia` | BR-08496/2026 | `2026-10-23T08:01:43-03:00` | [publicação](https://www.itatiaia.com.br/politica/eleicoes/pesquisas/alfa-inteligencia-flavio-bolsonaro-tem-52-dos-validos-lula-48-no-2o-turno/) | CREATED |

## Divergências de campo preservadas

### Gerp — BR-03835/2026

O usuário e a [publicação da CNN com o relatório](https://www.cnnbrasil.com.br/eleicoes/gerp-flavio-tem-53-dos-votos-validos-no-2o-turno-lula-47-2/) informam campo **19 a 21/10/2026**. O [espelho do cadastro do TSE no Pesquisa Eleitoral](https://www.pesquisaeleitoral.com.br/pesquisas/2026/eleicoes-2o-turno/presidente/brasil/gerp-mercadologia-br-03835) informa **17 a 20/10/2026**. Não são o mesmo período. O JSON preserva 19–21/10 conforme solicitado e mantém o registro BR-03835/2026; nenhuma reconciliação foi feita silenciosamente. A consulta ao espelho independente não substitui uma eventual conferência direta posterior no PesqEle.

### Palver — BR-04612/2026

Mantido o campo efetivamente realizado **18 a 20/10**, informado pelo usuário e pela [publicação do UOL](https://noticias.uol.com.br/eleicoes/2026/10/21/palver-pesquisa-presidencial-segundo-turno-21-de-outubro.ghtm). O [espelho do cadastro](https://www.pesquisaeleitoral.com.br/pesquisas/2026/eleicoes-2o-turno/presidente/brasil/palver-consultoria-e-desenvolvimento-tecnologico-br-04612) prevê 16 a 20/10. Mantida MOE 2,4 p.p. do relatório e da entrada fornecida, sem substituí-la pela previsão amostral cadastral.

## Dry run atual

Executado somente POST /api/admin/polls/import?dryRun=true, com os bytes do arquivo operacional. Relatório completo em `data/polls.real.2026-10-23.dry-run.json`.

```text
created: 18
updated: 0
unchanged: 0
failed: 0
```

Todos os 18 registros foram aceitos. CREATED é status de simulação: nenhuma pesquisa foi persistida. Nenhuma importação real foi autorizada. O relatório anterior 16/0/0/2 foi preservado em data/private/polls-before-final-metadata.dry-run.json.

| Normalização TOTAL pelo importador existente | Flávio | Lula |
|---|---:|---:|
| Quaest — originais 45 / 44 | 50,5618% | 49,4382% |
| Palver — originais 50 / 48 | 51,0204% | 48,9796% |

## Prévia matemática dos quatro modelos

Para responder aos valores solicitados sem gravar pesquisas, o **AggregateEngine existente** foi executado diretamente em memória com os 18 insumos fornecidos, a configuração efetiva lida de GET /api/aggregates e o histórico efetivo lido da API. O PollNormalizationService existente normalizou TOTAL; os percentuais VALID foram preservados. Não houve reimplementação das fórmulas.

Todos os 18 metadados obrigatórios estão preenchidos e foram aceitos no dry run. O motor existente foi novamente executado em memória com os metadados atualizados. Esta prévia fixa explicitamente o conjunto de 18 insumos e não executa o fluxo de persistência de AggregateService. Os pesos usam campo e margem; o preenchimento desses horários não alterou os valores matemáticos. Não é um agregado live nem freeze.

Data de referência: **2026-10-23**, Brasília. 18 institutos, 18 insumos, 59.442 entrevistados. IDs temporários das pesquisas na prévia existem somente em memória/arquivo e não foram inseridos no banco.

| Modelo — prévia em memória dos 18 insumos | Flávio | Lula |
|---|---:|---:|
| SIMPLE | 50.9551% | 49.0449% |
| RECENCY | 50.9383% | 49.0617% |
| WEIGHTED | 50.9630% | 49.0370% |
| CALIBRATED | 50.9630% | 49.0370% |

CALIBRATED coincide com WEIGHTED porque não há histórico de desempenho cadastrado para a eleição/turno configurados: fator histórico neutro 1. Half-life 3 dias, cap 0,15 e parâmetros históricos 0,85–1,15 permanecem iguais à configuração existente. O SIMPLE 50,9551% / 49,0449% confirma o sanity check aproximado 50,96% / 49,04%, sem diferença material.

Resultado completo e contribuição/peso por instituto: `data/polls.real.2026-10-23.preview.json`, marcado IN_MEMORY_MATHEMATICAL_PREVIEW_ONLY, persisted=false e allRecordsImportable=true.

## Data quality e conservação da base

GET /api/admin/data-quality retornou **WARNINGS**, ready=true, somente NO_POLLS, sem ERROR. O endpoint avalia a base persistida, que continua sem pesquisas; não constitui uma avaliação persistida do lote, cujas 18 linhas foram aceitas no dry run. A tela de freeze segue sem agregados operacionais calculáveis.

GET /api/polls: 0 antes e depois. GET /api/pre-election-freeze: ausente antes e depois. GET /api/pollsters confirma os cinco institutos criados. Nenhuma chamada de importação real ou criação de freeze foi feita. Nenhum commit.

SHA-256 do JSON enviado ao dry run: `002b29b39e42bdb032219a72dfba96e52eae7c762d22c95cb9d245e399726d89`.
