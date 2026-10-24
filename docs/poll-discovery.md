# Inbox semiautomática de pesquisas

Fluxo: **descoberta → staging/inbox → revisão humana → dry-run existente → aprovação → importação**. Nenhum sync ou adapter pode importar percentuais automaticamente. Os quatro algoritmos, histórico de desempenho, resultados oficiais TSE, projeção, freeze, arquivo histórico e mapa permanecem independentes.

## Fonte e contrato verificados

Fonte: [Pesquisas Eleitorais — 2026, Portal de Dados Abertos do TSE](https://dadosabertos.tse.jus.br/dataset/pesquisas-eleitorais-2026), sistema PesqEle, atualização diária. ZIP: [pesquisa_eleitoral_2026.zip](https://cdn.tse.jus.br/estatistica/sead/odsele/pesquisa_eleitoral/pesquisa_eleitoral_2026.zip).

Contrato inspecionado em 24/10/2026: CSVs por UF e agregado `pesquisa_eleitoral_2026_BRASIL.csv`, separador `;`, ISO-8859-1. O parser também suporta UTF-8/BOM, aspas escapadas e campos multilinha. Só lê registros do agregado BRASIL, evitando duplicação com arquivos estaduais. Metadados originais ficam no staging; não interpreta metodologia textual para inventar margem, resultados, URLs ou horário de publicação.

Campos usados: `AA_ELEICAO`, `SG_UF`, `SG_UE`, `NM_UE`, `DS_CARGO`, `NR_PROTOCOLO_REGISTRO`, `NM_EMPRESA`, `NM_EMPRESA_FANTASIA`, `DT_INICIO_PESQUISA`, `DT_FIM_PESQUISA`, `DT_DIVULGACAO`, `QT_ENTREVISTADO`, `DS_METODOLOGIA_PESQUISA`, `DS_PLANO_AMOSTRAL`, `DS_DADO_MUNICIPIO`. O protocolo `BR012342026` é representado como `BR-01234/2026`. Todas as demais colunas são retidas como evidência original, sem executar texto externo.

**Gate estruturado obrigatório:** `AA_ELEICAO=2026`, `DS_CARGO` presidencial exato, `SG_UE=BR`, `SG_UF=BR` e `NM_UE=BRASIL`. Esses campos identificam a unidade eleitoral presidencial, não garantem abrangência nacional da amostra: BR-02209/2026 (Bahia) e BR-03112/2026 (DF) têm os mesmos valores BR/BR/BRASIL de BR-09709/2026 (nacional).

Após o gate estruturado, exige-se evidência explícita de universo/cobertura nacional em `DS_DADO_MUNICIPIO` (abrangência da coleta) ou `DS_PLANO_AMOSTRAL` (universo amostral); `DS_METODOLOGIA_PESQUISA` é fallback conservador se os anteriores não fornecem evidência positiva. Declaração regional explícita em qualquer um desses campos veta o registro, mesmo com menção nacional em outro. Texto genérico, ausente ou inconclusivo não basta. Não se usa apenas a palavra Brasil solta, data ou instituto. Casos ambíguos podem ficar de fora por segurança.

**Limitação da fonte:** não há coluna explícita de turno, autorização judicial ou horário real da publicação. Campo deve começar após 04/10 e terminar até 25/10/2026; divulgação prevista após o campo e até 25/10, com `DT_DIVULGACAO >= polling.discovery.operational-from` (default 05/10/2026). Datas não substituem cargo/abrangência. Divulgações futuras dentro da disputa entram para planejamento. Primeiro turno permanece exclusivamente em `historical_polls`. As flags `secondRoundConfirmed` e `publicationAllowedConfirmed` ficam falsas até revisão humana; ambas são obrigatórias para READY/dry-run/import.

`DT_DIVULGACAO` é apenas a data permitida no cadastro, armazenada como `LocalDate`. Não é convertida para `publishedAt`. A revisão exige Instant real com offset/UTC, fonte verificável e data compatível com campo/permissão. Se só houver data sem horário confiável, o item permanece pendente: não usar meia-noite artificial.

## Configuração e limites

```yaml
polling:
  discovery:
    enabled: ${POLL_DISCOVERY_ENABLED:true}
    operational-from: ${POLL_DISCOVERY_OPERATIONAL_FROM:2026-10-05}
    tse-open-data-url: ${POLL_DISCOVERY_TSE_URL:https://cdn.tse.jus.br/estatistica/sead/odsele/pesquisa_eleitoral/pesquisa_eleitoral_2026.zip}
```

O endereço é configuração do servidor e validado por allowlist HTTPS/host/caminho exatos; não há upload de dataset nem URL no endpoint de sync. Redirecionamentos são recusados. Nenhuma fonte editorial é acessada. Limites: conexão 5s, request 30s, deadline total do download 35s, ZIP 32 MiB, expansão total 96 MiB, cada entrada 64 MiB, 64 entradas, 50.000 linhas, 100 campos e 30.000 caracteres por campo. Download/cabeçalho/ZIP/CSV inválido cancela o sync antes da persistência; conflitos do mesmo registro abortam o lote inteiro. Arquivos nunca são extraídos para disco.

Agendamento: cron `0 0 8,12,16 * * *`, `America/Sao_Paulo`. Datas, auditoria e referência usam o `Clock` da aplicação no mesmo fuso. Não executa sync no startup nem a cada minuto. `POLL_DISCOVERY_ENABLED=false` desliga agendamento e download manual. Testes desligam essa integração e usam ZIPs sintéticos locais. Manter max replicas=1 conforme o runbook; advisory lock protege o staging e a unicidade, mas não coordena downloads entre réplicas.

## Persistência e auditoria — V14

`poll_discoveries` contém os campos solicitados, `registration_number` único e um payload interno JSONB com estado completo, publicação real, confirmação humana, hash e relatório do dry-run. Campos relacionais permitem identificar e auditar os registros. `poll_discovery_events` preserva snapshots do staging em cada detecção, alteração relevante de cadastro, revisão, simulação, importação e ignore.

`tseMetadata` é a primeira evidência recebida; `latestTseMetadata` é a última versão materialmente distinta. Alterações de cadastro preservam a primeira evidência e o conteúdo revisado em eventos, invalidam a aprovação e exigem nova confirmação. Somente `DT_GERACAO`/`HH_GERACAO` são ignorados no hash do cadastro para evitar invalidar revisões sem mudança efetiva de metadados. Sync idempotente não altera timestamps ou gera eventos desnecessários.

Mapeamento automático usa correspondência exata após normalização de acentos, caixa e pontuação entre razão social/nome fantasia e nome/slug cadastrado. Depois aplica somente alias explícito: `DATAFOLHA INSTITUTO DE PESQUISAS LTDA.` → `datafolha`, confirmado no CSV real em BR-09709/2026, definido em `PollsterAliases.java`. O alvo precisa existir e estar ativo; ambiguidades não são resolvidas por alias. Sem similaridade aproximada, aliases presumidos ou criação de institutos. Correspondência ausente/ambígua gera `UNMAPPED_POLLSTER`; a revisão associa explicitamente um instituto existente e ativo. Para instituto novo: cadastrar via `POST /api/admin/pollsters` com nome/slug verificados e recarregar a inbox.

Registro já existente em `polls`, independentemente do instituto, é marcado `IMPORTED`, sem atualização ou novos snapshots. Ambiguidade de registro operacional bloqueia a operação. `IMPORTED` e `IGNORED` são terminais para edição humana e nunca são apagados pelo sync. O UUID importado é referência auditável, sem cascade, mesmo se a pesquisa operacional for posteriormente removida por outro fluxo administrativo.

## Endpoints administrativos

Todos sob a proteção X-Admin-Key existente, sem relaxar CORS ou segurança:

| Método | Caminho | Efeito |
|---|---|---|
| POST | `/api/admin/poll-discoveries/sync` | Obtém ZIP oficial e atualiza somente inbox |
| GET | `/api/admin/poll-discoveries?status=&expectedPublicationDate=&pollster=` | Itens ordenados e contadores globais; `date` permanece alias compatível; valores conflitantes são rejeitados |
| GET | `/api/admin/poll-discoveries/{id}` | Estado e evidências, relatório/hash da simulação |
| PUT | `/api/admin/poll-discoveries/{id}/results` | Salva revisão; nunca escreve em polls |
| POST | `/api/admin/poll-discoveries/{id}/dry-run` | Retorna `ImportModels.Report` existente |
| POST | `/api/admin/poll-discoveries/{id}/import` | Aprovação explícita, hash e importação transacional |
| POST | `/api/admin/poll-discoveries/{id}/ignore` | Mantém staging com motivo auditado |

Revisão (valores ilustrativos, não uma pesquisa real):

```json
{
  "pollsterSlug": "datafolha",
  "fieldStart": "2026-10-20",
  "fieldEnd": "2026-10-22",
  "publishedAt": "2026-10-23T12:00:00-03:00",
  "sampleSize": 2000,
  "marginOfError": 2,
  "methodology": "Transcrição confirmada da fonte",
  "sourceUrl": "https://example.org/publicacao-ilustrativa",
  "voteBasis": "VALID",
  "candidateResults": [{"ballotNumber":22,"percentage":50},{"ballotNumber":13,"percentage":50}],
  "notes": "Fonte e cenário conferidos pelo operador.",
  "secondRoundConfirmed": true,
  "publicationAllowedConfirmed": true
}
```

Todos os resultados da disputa ativa 13/22 são obrigatórios. Publicação futura, campo fora do segundo turno e instituto não cadastrado/ativo são bloqueados. A checagem de percentuais e normalização continua integralmente no `BatchImportService`/`PollService` existentes. `INVALID` após simulação falha exige corrigir/salvar revisão antes de tentar novamente.

Depois de um dry-run com `failed=0`, buscar o detalhe atualizado e enviar:

```json
{"payloadHash":"HASH-DO-DETALHE-ATUAL","approved":true}
```

O SHA-256 cobre o payload exato do importador, notas, horário de revisão e hash do cadastro original/revisado. Qualquer edição invalida o dry-run. Não basta enviar um booleano sem hash nem reutilizar uma prévia de outra revisão. Erros usam o contrato ProblemDetail existente. Ignore recebe `{"notes":"Motivo verificado pelo operador"}`.

## Importação e snapshots atômicos

O importador padrão mantém sua API/transações por linha. Um ponto de entrada interno `runInTransaction` reutiliza **a mesma** conversão, validação, normalização, deduplicação, lock e escrita do `BatchImportService`, juntando-se à transação da aprovação de um único item. Não há segundo importador, fórmula alternativa ou gravação direta de resultados pela descoberta.

Aprovação usa row lock do staging, lock lógico do importador e lock de escrita da tabela de snapshots para não retornar snapshot antigo numa corrida. Pesquisa, quatro snapshots via `AggregateSnapshotService` e evento `APPROVED_IMPORT` fazem commit juntos. Falha em qualquer etapa desfaz tudo. Repetição de registro já importado não grava nem gera snapshots.

O serviço de snapshots existente é idempotente por modelo/data/minuto. Para que cada importação **bem-sucedida** tenha quatro snapshots novos, a inbox recusa aprovação em minuto já ocupado. Repetir no minuto seguinte; o dry-run continua válido se nada mudou. Não são inventados horários, alteradas constraints antigas ou modificados os cálculos para contornar essa regra.

## Interface e operação

`/admin/pesquisas/inbox`, com `ADMIN_UI_ENABLED=true` no servidor. Chave administrativa digitada em campo password, somente em memória, enviada no header X-Admin-Key. Sem localStorage, cookies novos, credenciais em query string ou alteração da autorização existente.

Resumo, filtros de status/data/instituto, tabela e editor mostram evidências oficiais separadas dos percentuais fornecidos pelo operador. Edite apenas o que exigir confirmação, salve no staging, confira relatório de normalização, marque a aprovação e confirme a importação. Campos alterados desabilitam importação até nova simulação. Registros terminais são somente leitura. Dados externos são renderizados como texto, sem HTML arbitrário.

## Limpeza e preservação

Os dois arquivos incrementais `polls-history.backfill-datafolha-may-sep-2026.json` (5 registros) e `polls-history.backfill-poderdata-quaest-presep-2026.json` (8 registros) foram retirados após conferir suas chaves no arquivo canônico `data/polls-history.2026-round1.json` (24 registros). O canônico não foi editado; nenhum registro importado foi alterado.

O workflow GHCR agora escuta `master`, conserva a tag imutável por SHA e publica alias `master` somente nessa branch. Nenhum commit, push, deploy, import real, sync operacional, snapshot ou freeze foi executado como parte da implementação. Testes de persistência usam somente PostgreSQL descartável do Testcontainers.


## Arquivos desta entrega

Criados:

- `api/src/main/java/br/com/election/discovery/DiscoveryConfiguration.java`
- `api/src/main/java/br/com/election/discovery/DiscoveryModels.java`
- `api/src/main/java/br/com/election/discovery/PollDiscoveryController.java`
- `api/src/main/java/br/com/election/discovery/PollDiscoveryScheduler.java`
- `api/src/main/java/br/com/election/discovery/PollDiscoveryService.java`
- `api/src/main/java/br/com/election/discovery/PollDiscoveryStore.java`
- `api/src/main/java/br/com/election/discovery/PollPublicationAdapter.java`
- `api/src/main/java/br/com/election/discovery/TsePollRegistryClient.java`
- `api/src/main/java/br/com/election/discovery/TsePollRegistryParser.java`
- `api/src/main/resources/db/migration/V14__poll_discoveries.sql`
- `api/src/test/java/br/com/election/PollDiscoveryIntegrationTests.java`
- `api/src/test/java/br/com/election/discovery/TsePollRegistryParserTests.java`
- `docs/poll-discovery.md`
- `web/app/admin/pesquisas/inbox/inbox.css`
- `web/app/admin/pesquisas/inbox/page.tsx`
- `web/lib/poll-discovery.ts`
- `web/tests/poll-discovery.test.mjs`

Modificados:

- `.github/workflows/api-image.yml`
- `README.md`
- `api/src/main/java/br/com/election/readiness/BatchImportService.java`
- `api/src/main/resources/application.yaml`
- `api/src/test/java/br/com/election/PollApiTests.java`
- `api/src/test/java/br/com/election/ProductionApiTests.java`
- `api/src/test/resources/application.properties`
- `docs/deployment.md`
- `docs/production-runbook.md`
- `web/app/admin/importacao/page.tsx`
- `web/app/admin/pesquisas/page.tsx`

Removidos apenas os dois incrementais listados na seção de limpeza.

## Validação da entrega inicial da inbox

- Backend completo: **301 testes aprovados**, zero falhas/erros/skips (30 novos: 13 de parser/cliente e 17 de integração).
- Repetição dos 17 testes de integração após o relatório final de auditoria: aprovada, incluindo hash, rollback, concorrência e IDs dos quatro snapshots.
- Frontend completo: **67 testes aprovados**, incluindo dois novos de aprovação/status.
- ESLint, TypeScript/build Next.js e `git diff --check`: aprovados.
- Contrato do ZIP real: parser executado em memória, 114 registros na janela de segundo turno identificados; nenhum sync ou import operacional realizado.
- Fluxo da interface conferido com fixture em memória: edição bloqueia aprovação, exige salvar/novo dry-run e confirmação inline, e exibe o relatório de importação e quatro snapshots simulados.
- Arquivo histórico canônico e fontes dos agregadores, TSE/projeção permaneceram sem diff. Testes de regressão existentes dos quatro modelos passaram.

Total: **368 testes distintos**. Não houve criação de freeze, commit, push ou deploy.

## Correção do escopo operacional após o primeiro sync

`Sync.scanned` conta linhas brutas do CSV agregado antes dos filtros; os CSVs de UF não são contados novamente. `Sync.eligible` conta registros únicos que passaram cargo, abrangência e período. Created/updated/unchanged referem-se somente ao staging.

A UI separa **Descobertas adicionadas hoje** (`detectedAt` no dia atual de Brasília) de **Divulgação prevista hoje** (`expectedPublicationDate` igual ao dia atual de Brasília). Os contadores são globais, independentes dos filtros da tabela. Ordenação padrão: data prevista hoje, datas futuras crescentes, pendentes antigas por data decrescente e, por último, importadas/ignoradas antigas. As confirmações humanas permanecem obrigatórias.

Arquivos adicionais deste ajuste: `api/src/main/java/br/com/election/discovery/PollsterAliases.java` e `api/src/test/resources/discovery/tse-scope-observed.csv`. A fixture sanitizada mantém apenas colunas necessárias e trechos representativos de BR-02209/2026, BR-03112/2026 e BR-09709/2026, sem nomes de responsáveis ou dados pessoais. Os demais ajustes se restringem à configuração/modelos/parser/serviço/controller da inbox, UI/tipos, seus testes e esta documentação. Não há migration adicional nem nova alteração no BatchImportService.

O teste de integração usa PostgreSQL descartável: 18 pesquisas sintéticas antes/depois de dois syncs, conteúdo integral de polls idêntico, primeiro sync examina 3 e aceita 1 (já importada), segundo created=0 e nenhum snapshot criado. Não é execução contra o banco operacional local.

### SQL para limpar SOMENTE staging local

Não é migration e não deve rodar em produção. Conferir a conexão local antes de executar. O script bloqueia e apaga apenas as duas tabelas de staging; polls, snapshots e históricos não são alvo de comandos de escrita. Os contadores antes/depois devem ser iguais (18 na base descrita pelo operador).

```sql
SELECT current_database(), inet_server_addr(), inet_server_port();
BEGIN;
LOCK TABLE poll_discovery_events, poll_discoveries IN ACCESS EXCLUSIVE MODE;
SELECT count(*) AS polls_antes FROM polls;
DELETE FROM poll_discovery_events;
DELETE FROM poll_discoveries;
SELECT count(*) AS polls_depois FROM polls;
COMMIT;
```

Depois executar `POST /api/admin/poll-discoveries/sync` duas vezes com X-Admin-Key, verificar created=0 no segundo e ausência de BR-02209/2026 e BR-03112/2026 na inbox. Nenhuma chamada de importação é necessária. Este patch não executou limpeza ou sync no banco operacional local.

Validação do ZIP real da geração 23/10/2026, inspecionado em 24/10: **3.648 linhas brutas, 55 registros únicos elegíveis** com o filtro final. BR-02209/2026 e BR-03112/2026 foram excluídos; BR-09709/2026 foi incluído. Trata-se de parsing em memória, sem sync/persistência no banco local.

Validação final do ajuste: **310 testes backend + 67 frontend = 377 aprovados**, zero falhas/erros/skips. São 9 testes novos neste ajuste (5 de parser/aliases e 4 de integração), além da atualização do teste de publicação futura. ESLint, build Next.js/TypeScript e `git diff --check` aprovados. As 18 pesquisas do arquivo operacional continuam presentes entre os 55 registros elegíveis do ZIP. Nenhuma fórmula, dado operacional, histórico ou snapshot foi modificado; não houve commit, push ou deploy.
