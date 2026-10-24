# Eleições 2026

Plataforma brasileira para acompanhar pesquisas do segundo turno presidencial. Monorepo simples com API, web e infraestrutura local. As Fases 1 e 2 entregam cadastro, normalização, quatro agregados transparentes e snapshots históricos. A Fase 3 acrescenta apuração oficial TSE por Brasil, UF e município. Não há previsão probabilística de vencedor.

## Stack

- **API:** Java 21, Spring Boot 4.1.1, Maven, PostgreSQL, Flyway, Spring Data JPA, Validation, Actuator e Testcontainers.
- **Web:** Next.js 16, TypeScript, App Router, Tailwind CSS, shadcn/ui, Recharts e Axios.
- **Infra:** PostgreSQL 17 em Docker Compose. Os testes usam PostgreSQL 17 real via Testcontainers, sem H2.

## Rodar localmente

Requisitos: Java 21, Node.js compatível com Next.js 16 e Docker Desktop em execução, com containers Linux.

Na raiz:

```sh
docker compose -f infra/docker-compose.yml up -d
```

Se um container `election-postgres` já foi criado com o nome de projeto Compose `infra`, reutilize esse projeto com `docker compose -p infra -f infra/docker-compose.yml up -d`. Um container com o mesmo nome criado por outro projeto causa conflito; não remova o volume para resolver esse caso.

API:

```sh
cd api
./mvnw spring-boot:run
```

No Windows:

```powershell
cd api
.\mvnw.cmd spring-boot:run
```

Web, em outro terminal:

```sh
cd web
npm install
npm run dev
```

- Web: http://localhost:3000
- API: http://localhost:8081
- Saúde: http://localhost:8081/actuator/health
- PostgreSQL: `localhost:5433`, banco/usuário/senha locais: `election`.

Variáveis opcionais da API: `DATABASE_URL` (JDBC), `DATABASE_USERNAME`, `DATABASE_PASSWORD`, `PORT`. Para o frontend, copie `web/.env.example` para `web/.env.local`; `NEXT_PUBLIC_API_URL` usa `http://localhost:8081` como fallback. Reinicie o Next.js após alterar as variáveis.

Flyway aplica V1–V11 automaticamente. Hibernate apenas valida o schema (`ddl-auto=validate`); `open-in-view=false`. CORS permite explicitamente `http://localhost:3000`.

## Uso

- `/`: quatro modelos agregados, comparação, composição por instituto, evolução por snapshots e últimas pesquisas.
- `/pesquisas`: histórico com valores originais e normalizados, fonte e metadados.
- `/admin/pesquisas`: cadastro, edição e exclusão de pesquisas. Os candidatos e institutos são carregados da API.
- `/admin/institutos`: cadastro/edição de desempenho histórico e botão para salvar snapshots dos quatro modelos.
- `/metodologia`: regras efetivamente implementadas.
- `/apuracao`: dados oficiais TSE, ritmo dos votos e projeção territorial própria em cards distintos, estados e busca municipal.

O admin e os endpoints de escrita não têm autenticação, conforme o escopo desta fase. O horário de cadastro usa o fuso do dispositivo e é enviado como um instante UTC; a publicação é exibida em `America/Sao_Paulo`. Datas de campo não sofrem conversão de fuso.

O seed configura Flávio Bolsonaro (PL, 22) e Lula (PT, 13) conforme o escopo solicitado. O seed é independente da fonte oficial; a Fase 3 associa os resultados TSE pelo número eleitoral, mantendo nomes e percentuais oficiais. Também cadastra os 14 institutos solicitados, sem pesquisas ou percentuais inventados.

## API REST

| Método | Endpoint | Resposta |
| --- | --- | --- |
| GET | `/api/candidates` | Candidatos |
| POST | `/api/admin/candidates` | Candidato criado (201) |
| GET | `/api/pollsters` | Institutos |
| POST | `/api/admin/pollsters` | Instituto criado (201) |
| GET | `/api/polls` | Histórico em ordem decrescente de publicação |
| GET | `/api/polls/latest` | Uma pesquisa por instituto, mais recentes primeiro |
| GET | `/api/polls/{id}` | Pesquisa ou 404 |
| POST | `/api/admin/polls` | Pesquisa criada (201 e Location) |
| PUT | `/api/admin/polls/{id}` | Pesquisa atualizada, normalização recalculada |
| DELETE | `/api/admin/polls/{id}` | Exclusão dos resultados e da pesquisa (204) |
| GET | `/api/aggregates` | Quatro modelos calculados com insumos carregados uma única vez |
| GET | `/api/aggregates/simple` | Média simples |
| GET | `/api/aggregates/recency` | Recência pela meia-vida |
| GET | `/api/aggregates/weighted` | Recência × precisão, com cap |
| GET | `/api/aggregates/calibrated` | Recência × precisão × histórico, com cap |
| GET | `/api/aggregates/history?method=CALIBRATED` | Snapshots em ordem cronológica; filtro opcional |
| POST | `/api/admin/aggregates/snapshot` | Calcula/persiste os quatro modelos; idempotente por método/data/minuto |
| GET | `/api/admin/pollster-performances` | Histórico dos institutos |
| POST | `/api/admin/pollster-performances` | Cadastra desempenho com score derivado |
| PUT | `/api/admin/pollster-performances/{id}` | Edita desempenho e recalcula score |

Exemplo de corpo para criar/editar uma pesquisa (substitua `pollsterId` por um UUID retornado por `GET /api/pollsters`):

```json
{
  "pollsterId": "UUID-DO-INSTITUTO",
  "registrationNumber": "BR-00000/2026",
  "fieldStart": "2026-10-18",
  "fieldEnd": "2026-10-20",
  "publishedAt": "2026-10-22T15:00:00Z",
  "sampleSize": 2000,
  "marginOfError": 2,
  "methodology": "Entrevistas presenciais",
  "sourceUrl": "https://example.org/pesquisa",
  "voteBasis": "TOTAL",
  "results": [
    { "candidateId": "00000000-0000-0000-0000-000000000001", "originalPercentage": 47 },
    { "candidateId": "00000000-0000-0000-0000-000000000002", "originalPercentage": 45 }
  ]
}
```

O exemplo é ilustrativo, não representa uma pesquisa real. `normalizedPercentage` é calculado exclusivamente no backend. `registrationNumber` no DTO corresponde a `tse_registration_number` no banco. Amostra, margem, metodologia e registro são opcionais; URLs de fonte devem usar HTTP(S). Os campos de texto têm limites alinhados ao schema; percentuais aceitam até quatro casas decimais. O MVP aceita até 100 resultados por pesquisa.

Candidato: `name`, `displayName`, `party`, `ballotNumber` positivo, `active` opcional (padrão true). Instituto: `name`, `slug` único com letras minúsculas/números/hífens, `websiteUrl` HTTP(S) opcional e `active` opcional.

Erros usam `application/problem+json`: 400 para validação/request inválido, 404 para recursos/referências ausentes e 409 para conflitos de integridade. Erros de Bean Validation incluem `errors` com campo e mensagem. Criação, atualização e exclusão são transacionais; uma edição inválida preserva os resultados anteriores.

## Banco e arquitetura

Os pacotes em `br.com.election` são organizados por domínio: `candidate`, `pollster`, `poll`, `aggregate`, `common`. O agregador usa quatro calculadores separados e um alocador de pesos comum. `PollsterPerformance` pertence ao domínio `pollster`; seu `spreadError` determina apenas o fator de peso histórico.

Migrations em `api/src/main/resources/db/migration`:

1. `V1__create_candidates.sql`
2. `V2__create_pollsters.sql`
3. `V3__create_polls.sql`
4. `V4__create_poll_results.sql`
5. `V5__create_pollster_performance.sql`
6. `V6__seed_candidates.sql`
7. `V7__seed_pollsters.sql`
8. `V8__create_aggregate_snapshots.sql`
9. `V9__prepare_pollster_performance_sources.sql` — campos de procedência e índice; sem INSERT de números inventados.

UUIDs nativos, timestamps com fuso, percentuais `NUMERIC(7,4)`, FKs e constraints explícitas. Há unicidade de instituto por slug, resultado por pesquisa/candidato e desempenho por instituto/eleição/turno. Índices cobrem publicação, seleção por instituto e referências de candidatos. A seleção de latest usa `DISTINCT ON` no PostgreSQL, sem carregar o histórico em memória.

## Testes

Com Docker em execução, na pasta `api`:

```powershell
.\mvnw.cmd test
```

Linux/macOS: `./mvnw test`. O Testcontainers cria bancos isolados; não usa o banco local de desenvolvimento. A suíte cobre contexto, migrations/seeds, persistência, API, normalização, atualização/rollback, latest/desempates, unicidade, validação, delete, CORS, quatro algoritmos, cap/redistribuição, propriedades, histórico, snapshots/idempotência e ausência de N+1 com 14 institutos. Os repositories não são mockados.

Na pasta `web`:

```sh
npm run lint
npm run build
```

## Agregador — Fase 2

Todos os métodos usam exclusivamente `normalizedPercentage` da última pesquisa de cada instituto ativo. Campos futuros e resultados incompletos para os candidatos ativos são excluídos com motivo no response. A amostra total é descritiva, com contagem de pesquisas sem amostra.

- SIMPLE: pesos iguais `1/n`.
- RECENCY: peso bruto `2^(-idadeDoMidpoint / meiaVida)`.
- WEIGHTED: recência × `1/margem²`; sem margem declarada, estima `margem = 98/sqrt(amostra)` em pontos percentuais e aplica `1/margemEstimada²`. Sem margem e amostra válidas, fator 1.
- CALIBRATED: recência × precisão × fator histórico; no padrão, `clamp(1,15 − spreadError/20, 0,85, 1,15)`.

WEIGHTED e CALIBRATED redistribuem excessos até respeitar o cap de 15%. Com menos de sete institutos, usam pesos iguais e informam a inviabilidade. A normalização final dos candidatos soma exatamente 100%, sem alterar os percentuais individuais das pesquisas. Pesos usam 16 casas; percentuais, quatro; cálculos determinísticos usam BigDecimal. A fronteira limitada de `double` na exponencial está documentada.

Configuração em `api/src/main/resources/application.yaml`, validada por `@ConfigurationProperties`:

```yaml
polling:
  aggregation:
    half-life-days: 3
    max-institute-weight: 0.15
    historical-min-factor: 0.85
    historical-max-factor: 1.15
    historical-neutral-spread-error: 3
    historical-election-key: BR-PRESIDENT-2026
    historical-round: 1
```

Somente o histórico da eleição/turno configurados é usado. Sem dados, fator 1. Não há edição manual de `historicalScore`; o backend o deriva do erro. A tabela começa vazia porque não existem erros reais com fontes no repositório. O admin permite registrar `sourceUrl` e `sourceDescription`.

Os endpoints de cálculo e snapshot aceitam `?referenceDate=YYYY-MM-DD`; o padrão é a data atual em Brasília. Pesquisas publicadas após o fim da data de referência não entram. Isso usa os cadastros/configurações atuais, não reconstrói todo o estado conhecido no passado. O midpoint é calculado em dias nominais de 24 horas e a idade usa 00:00 da referência.

GETs nunca salvam snapshots. Use o botão do admin ou `POST /api/admin/aggregates/snapshot`. Uma chave única por método/data/minuto de cálculo evita duplicação; uma repetição retorna o snapshot original mesmo após mudanças de dados no mesmo minuto. O payload JSONB preserva composição completa, configuração e notas para auditoria. Sem pesquisas elegíveis, POST retorna 400. O histórico é lido sem recalcular, mesmo após exclusão da pesquisa de origem.

Veja o **exemplo completo com oito institutos**, fórmulas, critérios, tolerâncias e limitações em [docs/polling-model.md](docs/polling-model.md). O fallback de margem tem escala compatível com a margem declarada, mas é apenas uma aproximação por amostragem aleatória simples, 95% de confiança e máxima variância; não reconstrói a metodologia do instituto. A margem declarada válida sempre tem prioridade. Os fatores históricos padrão de 0,85 a 1,15 refletem a evidência histórica limitada e alteram somente os pesos.

## Escopo futuro

SSE, autenticação, mapas, scraping e projeção probabilística não foram implementados. Os próximos passos do modelo incluem revisão empírica dos fatores, sensibilidade e cadastro de desempenho real com fontes.


## Fase 3 — Apuração oficial TSE

Integração presidencial do 2º turno (eleição 6258, cargo 0001), separada do motor de pesquisas. `/apuracao` apresenta Brasil, UFs, busca municipal e páginas `/apuracao/{uf}/{codigoTse}`. Antes da contagem, exibe espera e percentuais `—`. Histórico somente oficial, sem projeção.

A migration **V10** adiciona snapshots oficiais JSONB e catálogo persistente de municípios. O cliente RestClient usa timeouts, User-Agent, ETag/Last-Modified, 304 sem snapshot, backoff e limite global padrão de 2 req/s. Nacional a cada 5s; uma UF por segundo (ciclo ~27s + latência); municípios sob demanda/cache de 30s. Último resultado válido sobrevive a falhas e reinícios. EA14/EA15 e coleta por zona permanecem como extensões documentadas.

Configuração completa em `api/src/main/resources/application.yaml` (`tse.results`), com overrides `TSE_RESULTS_*`. Desligar coleta: `TSE_RESULTS_SCHEDULING_ENABLED=false`. Já está desligada por padrão nos testes. Operar uma única instância coletora nesta fase.

Endpoints GET em `/api/election`: `/president`, `/president/states`, `/president/states/{uf}`, `/president/municipalities`, `/president/states/{uf}/municipalities`, `/president/states/{uf}/municipalities/{municipalityCode}`, `/president/history?from=&to=&limit=`, `/source-status`.

[Contratos, campos oficiais, disponibilidade, configuração e estratégia territorial](docs/tse-results-integration.md). Fixtures reais versionadas e cenários sintéticos identificados em `api/src/test/resources/tse/`. Testes HTTP executam exclusivamente contra servidor local.


## Fase 4 — Ritmo e projeção territorial

Modelo próprio `STATE_SHRINKAGE_V1`, separado do TSE e do agregador de pesquisas. Combina o share entre os candidatos 13/22 no primeiro turno oficial (6257) com votos observados por UF no segundo turno, usando eleitorado das seções totalizadas como progresso. Estima votos válidos e soma as 27 UFs; não extrapola o percentual nacional parcial; soma apenas votos oficiais observados do exterior, sem projetar votos restantes. Em 100% estadual usa votos observados exatamente; no encerramento oficial nacional, converge ao resultado nacional do TSE, incluindo exterior.

V11 adiciona `election_baselines` e `projection_snapshots`. Baseline é carregado somente por `POST /api/admin/election-baselines/first-round/sync` (Brasil + 27 UFs; timeout recomendado 240s) e funciona offline depois. Não há sync recorrente. Consulte o resumo de sucessos/falhas e audite em `GET /api/admin/election-baselines/first-round`.

Novos GETs: `/api/election/president/trend`, `/remaining`, `/projection`, `/projection/history`, todos sob o prefixo `/api/election/president`. O ritmo usa deltas consecutivos válidos de 5/15/30min; pendências usam eleitorado, sem inventar votos futuros. Projeção é publicada apenas após 5% do eleitorado nacional representado, dez UFs com votos, quatro das cinco regiões, três dos seis estados-chave (SP, MG, RJ, BA, PR, RS) e cobertura territorial suficiente. Faltas de baseline/dados são informadas.

Configuração validada `election.projection` em `application.yaml`, com overrides `ELECTION_PROJECTION_*`. `ELECTION_PROJECTION_ENABLED=false` suspende publicação/snapshots e preserva os endpoints oficiais. O scheduler de análises não chama a rede; utiliza os snapshots já coletados e não roda nos testes por padrão.

[Modelo, fórmulas, convergência, replay sem look-ahead, endpoints e limitações](docs/election-projection-model.md). Não há probabilidade eleitoral, chamada de eleição ou declaração de vencedor. A ordem de entrada das seções dentro de uma UF pode enviesar a estimativa.

## Fase 5 — dados e freeze

Importação administrativa em lote com dry run, qualidade, auditoria e congelamento imutável dos quatro modelos. Use `/admin/importacao` e `/admin/freeze`. Nenhuma pesquisa real ou freeze é criado automaticamente. Formatos e operação: [docs/data-import.md](docs/data-import.md) e [docs/pre-election-freeze.md](docs/pre-election-freeze.md). Exemplos fictícios em `data/`; fontes reais privadas podem ficar em `data/private/`. Fórmulas estatísticas preservadas.

## Histórico das pesquisas — Fase 7A.1

Histórico público de pesquisas presidenciais de 2026 em `/pesquisas/historico`: arquivo separado, import idempotente com dry-run e observações individuais por turno. Modelo, normalização conservadora, endpoints e isolamento dos quatro agregados: [docs/poll-history.md](docs/poll-history.md). Nenhum dado histórico real é carregado automaticamente.

## Produção — Fase 6

Configuração Azure Container Apps (Brazil South, Consumption) + Neon PostgreSQL + Vercel, workflow GHCR, segurança, smoke tests e carga explícita em [docs/deployment.md](docs/deployment.md). Template em [infra/azure/main.json](infra/azure/main.json). Backup, restore, recuperação TSE e operação com uma réplica em [docs/production-runbook.md](docs/production-runbook.md). Nenhum dado local é migrado automaticamente. Cloud Run permanece somente como alternativa histórica.
