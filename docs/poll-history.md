# Fase 7A.1 — arquivo de pesquisas presidenciais de 2026

O histórico é um arquivo de observações, não um agregador novo. A migration **V13__historical_polls.sql** cria apenas `historical_polls` e `historical_poll_results`. Nenhum registro é copiado para `polls`, `poll_results`, `candidates`, performance, snapshots, freeze, baseline ou projeção. `AggregateService`, elegibilidade, pesos, CALIBRATED e a integração TSE permanecem intactos.

## Modelo separado

`historical_polls`: UUID, electionKey, round (1/2), FK para instituto já cadastrado, registro TSE opcional, campo, publicação Instant, amostra/margem opcionais, metodologia, fonte HTTP(S), base TOTAL/VALID, modo de normalização, declaração de completude, chaves esperadas, notes, hash de conteúdo e timestamps.

`historical_poll_results`: UUID e FK exclusiva para pesquisa histórica, candidateKey estável, candidateName, displayName/party/ballotNumber opcionais, originalPercentage e normalizedPercentage anulável. **Não há FK para candidates**, nem criação/ativação de candidato. Nome e partido são metadados da observação. Campos desconhecidos devem permanecer nulos; não deduzir nomes, partidos, horário ou fontes.

candidateKey deve identificar a mesma pessoa consistentemente entre levantamentos/turnos, usando letras minúsculas, números e hífens, independentemente de estar ativa na disputa atual. Não reutilizar uma chave para pessoas diferentes.

## Endpoints

- `GET /api/poll-history`: somente arquivo, electionKey padrão `BR-PRESIDENT-2026`.
- `GET /api/poll-history?round=1` ou `?round=2`.
- `GET /api/poll-history?electionKey=BR-PRESIDENT-2026&round=1`: filtro explícito opcional.
- `POST /api/admin/poll-history/import?dryRun=true`: valida/prevê sem escrita.
- `POST /api/admin/poll-history/import?dryRun=false`: importa após revisão do relatório.

Default administrativo conservador: **dryRun=true**. O interceptor X-Admin-Key existente protege a nova rota; não houve mudança na segurança. Não há endpoints públicos de escrita.

O POST aceita array de registros ou `{ "polls": [...] }`, até 1000 por lote. Array vazio é no-op com contadores zero, adequado ao arquivo preparado sem evidências. Envelope ausente/inválido recebe 400. Cada registro tem transação própria; erro em uma linha não invalida linhas válidas. Resposta segue `ImportModels.Report`: dryRun, created, updated, unchanged, failed, details. Details incluem index, status, id, key, normalized (resultados com metadados) e message.

Chave natural: **electionKey + round + pollster + REG:registrationNumber**. Sem registro: **electionKey + round + pollster + DATE:fieldEnd + publishedAt**. Mesmo registro em turnos distintos é independente. Registro e chaves são case-sensitive; usar a identificação documentada pela fonte. Acrescentar um registro antes desconhecido muda a identidade: revisar possíveis duplicatas antes de uma carga real.

Conteúdo equivalente preserva UUID e timestamps. Resultados/chaves esperadas são ordenados e decimais canonicalizados antes do hash; mudanças apenas de ordem/escala não geram atualização. Instants usam precisão de microssegundos do PostgreSQL. Advisory lock transacional exclusivo deste importador e unique constraint protegem importações concorrentes. Dry-run simula duplicatas dentro do próprio lote sem gravar dados. GET lê metadados/resultados em snapshot transacional consistente.

## Schema por registro

| Campo | Regra |
|---|---|
| electionKey | Obrigatória, até 100 caracteres; chave 2026: `BR-PRESIDENT-2026` |
| round | 1 ou 2 |
| pollsterSlug | Slug existente, sem criar instituto automaticamente; inatividade atual não apaga histórico |
| registrationNumber | Registro TSE, se disponível, até 100 caracteres |
| fieldStart / fieldEnd | Datas ISO, início ≤ fim |
| publishedAt | Instant com offset/UTC verificável; não anterior ao fim do campo na data de Brasília |
| sampleSize / marginOfError | Opcionais, positivos; margem ≤100 p.p. e até 4 casas |
| methodology / notes | Opcionais, até 5000 caracteres |
| sourceUrl | Publicação verificável HTTP(S), obrigatória, até 2000 caracteres |
| voteBasis | TOTAL ou VALID conforme fonte |
| normalization | NONE (default) ou VALID_VOTES, por opção explícita |
| completeCandidateSet | Default false; só true com evidência de que todos os candidatos do cenário foram incluídos |
| expectedCandidateKeys | Lista completa de chaves esperadas quando completeCandidateSet=true |
| candidateResults | 1 a 50 resultados; candidateKey, candidateName, displayName, party, ballotNumber, originalPercentage |

Percentuais originais de 0 a 100, até 4 casas. Chaves e números de urna não podem repetir no mesmo levantamento. normalizedPercentage não pode ser fornecido pelo operador: é derivado apenas após validação.

## Normalização conservadora

1. **NONE:** preserva originais e deixa normalizedPercentage nulo, inclusive para recorte parcial em votos válidos. Não transforma um par do primeiro turno em uma disputa de dois candidatos.
2. **VALID_VOTES:** exige completeCandidateSet=true, expectedCandidateKeys sem duplicatas e igualdade exata com as chaves dos resultados. Primeiro turno exige pelo menos três candidatos; segundo, pelo menos dois. Um par do primeiro turno não passa nesse modo.
3. TOTAL completo: soma positiva e ≤100; a rotina aritmética pura existente calcula 100 × resultado / soma de **todos** os candidatos declarados e trata resíduo de arredondamento. Não acessa candidatos ativos nem agregadores.
4. VALID completo: soma aproximadamente 100 (tolerância 0,5 p.p. para arredondamento de publicação); mantém os valores declarados, sem reescalar uma base já válida.

A declaração de completude é uma obrigação de proveniência do operador. Contagem mínima não prova que o cenário real foi transcrito integralmente: conferir a publicação, todos os candidatos e a base antes do import. Brancos, nulos, indecisos e outras respostas não são candidatos; não inventar resultado residual nem cadastro para fechar soma. O backend verifica a lista declarada, não coleta/atesta a publicação remota.

## Frontend público

Página `/pesquisas/historico`, acessível em `/pesquisas`. Lê arquivo, pesquisas atuais e metadados atuais por GET. As pesquisas atuais são adaptadas **somente em memória para o painel do segundo turno**. Não são copiadas para a tabela histórica. Uma cópia histórica da mesma pesquisa do segundo turno não duplica o ponto; a observação atual prevalece apenas na visualização.

candidateKey continua sendo a identidade histórica. Para apresentar a mesma pessoa nos dois turnos, o adaptador só associa um candidato atual a uma chave histórica quando número e nome são compatíveis e a chave resultante é única. Dados ambíguos permanecem separados; não há alterações de cadastros/eligibilidade.

Controles 1º turno, 2º turno e visão geral; filtros locais por instituto, período de publicação (Brasília), TOTAL/VALID e seleção de candidatos. Limpar filtros restaura todas as observações. Candidatos ausentes/inativos na disputa atual continuam visíveis no arquivo.

**O eixo vertical dos pontos usa originalPercentage**, na base declarada, não combina normalizações parciais. Cada observação é um ponto, sem linha entre institutos ou turnos. Tabela técnica expõe também normalizedPercentage quando disponível. Visão geral usa dois painéis no mesmo domínio temporal, faixa de separação e marcos `04/10/2026 — 1º turno` e `25/10/2026 — 2º turno`, com nota de não equivalência.

Snapshots já existentes são uma opção em painel separado, identificados como **Agregado do modelo**, padrão CALIBRATED. Linhas tracejadas unem apenas snapshots do mesmo modelo/candidato, sem ajuste de tendência. Esse painel não recalcula modelos e só é habilitado para todos os institutos e bases que incluam VALID. O filtro de período usa a data de cálculo dos snapshots. Nenhum GET cria snapshot.

Gráficos/tabelas responsivos, candidatos identificados textualmente, pontos com foco e ação de teclado, resumo persistente e tooltip com instituto, publicação, campo, resultados, amostra, margem, base e registro. Tabelas acessíveis ficam em details; atualização é de 60s, sem requests sobrepostos e sem apagar a última resposta válida.

## Dados e operação futura

`data/polls-history.2026-round1.json` contém **polls vazio**. Não há resultados simulados nesse arquivo, fonte/registro/horário inventado ou import histórico operacional nesta fase. Dados sintéticos aparecem exclusivamente nos testes, identificados como tais.

Quando houver publicações verificadas, preencher todos os metadados disponíveis, preservar a fonte e começar por dry-run. Se o horário não puder ser comprovado, não fabricar Instant; registrar pendência. Conferir normalized nos details e o conjunto completo antes de autorizar import real. Nenhuma pesquisa de primeiro turno deve ser enviada ao importador `/api/admin/polls/import` atual.

## Regressão de isolamento

Os testes em PostgreSQL descartável carregam as 18 pesquisas e 17 performances dos arquivos operacionais existentes e conferem, em 23/10/2026, antes/depois de importar pesquisas históricas de ambos os turnos:

| Modelo | Flávio | Lula |
|---|---:|---:|
| SIMPLE | 50.9551 | 49.0449 |
| RECENCY | 50.9383 | 49.0617 |
| WEIGHTED | 50.9630 | 49.0370 |
| CALIBRATED | 50.9549 | 49.0451 |

São verificadas igualdade dos DTOs dos agregados, pesquisas atuais, snapshots e freeze no banco descartável. O mesmo teste confirma 18 pesquisas nos endpoints atuais, enquanto `/api/poll-history` consulta apenas o arquivo. Testes de autenticação existentes foram estendidos à nova rota; as duas asserções legadas de contagem Flyway passam de 12 para 13, mantendo as verificações de seeds. Nenhuma fórmula, configuração de pesos ou regra do freeze foi alterada.

## Arquivos desta fase

Criados:

- `api/src/main/java/br/com/election/pollhistory/HistoricalPollModels.java`
- `api/src/main/java/br/com/election/pollhistory/HistoricalPollValidation.java`
- `api/src/main/java/br/com/election/pollhistory/HistoricalPollStore.java`
- `api/src/main/java/br/com/election/pollhistory/HistoricalPollImportService.java`
- `api/src/main/java/br/com/election/pollhistory/HistoricalPollController.java`
- `api/src/main/resources/db/migration/V13__historical_polls.sql`
- `api/src/test/java/br/com/election/HistoricalPollIntegrationTests.java`
- `data/polls-history.2026-round1.json`
- `docs/poll-history.md`
- `web/app/pesquisas/historico/page.tsx`
- `web/components/poll-history-timeline.tsx`
- `web/components/poll-history-timeline.css`
- `web/lib/poll-history.ts`
- `web/tests/poll-history.test.mjs`

Arquivos existentes ajustados: `README.md`, `web/README.md`, `web/app/pesquisas/page.tsx`, `web/components/aggregate-dashboard.tsx`, `web/tests/load-typescript.mjs`, `api/src/test/java/br/com/election/PollApiTests.java` e `api/src/test/java/br/com/election/ProductionApiTests.java`. Alterações anteriores da Fase 7A foram preservadas.

## Validação final — 23/10/2026

- Maven completo: **271 testes, zero falhas/erros/skips**, incluindo 13 novos testes de integração histórica.
- Frontend: **43 testes, zero falhas**, incluindo 14 novos testes do histórico.
- ESLint e build de produção Next.js: aprovados, incluindo `/pesquisas/historico`.
- Navegador: divisão de turnos, pontos navegáveis por teclado, filtro TOTAL preservando Quaest/Palver originais, painel e tabela de snapshots existentes; viewport de 390px sem overflow horizontal da página.
- Leitura da base operacional: **18 pesquisas e 5 snapshots preservados**, quatro agregados idênticos à referência acima, freeze ausente e arquivo histórico vazio. Não houve POST de carga histórica na base operacional.
- Pendência: reunir e auditar fontes completas do primeiro turno antes de preencher o arquivo e executar qualquer carga real.

Nenhuma pesquisa histórica participa de SIMPLE, RECENCY, WEIGHTED ou CALIBRATED atuais. Não houve deploy, commit, criação de snapshot ou freeze nesta fase.
