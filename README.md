# Eleições 2026

Plataforma brasileira para acompanhar pesquisas do segundo turno presidencial. Monorepo simples com API, web e infraestrutura local. Esta fase entrega cadastro, histórico e normalização de pesquisas; não há agregado estatístico nem dados de apuração.

## Stack

- **API:** Java 21, Spring Boot 4.1.1, Maven, PostgreSQL, Flyway, Spring Data JPA, Validation, Actuator e Testcontainers.
- **Web:** Next.js 16, TypeScript, App Router, Tailwind CSS, shadcn/ui, Recharts (disponível para fases futuras) e Axios.
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

Flyway aplica V1–V7 automaticamente. Hibernate apenas valida o schema (`ddl-auto=validate`); `open-in-view=false`. CORS permite explicitamente `http://localhost:3000`.

## Uso

- `/`: panorama e última pesquisa de cada instituto.
- `/pesquisas`: histórico com valores originais e normalizados, fonte e metadados.
- `/admin/pesquisas`: cadastro, edição e exclusão de pesquisas. Os candidatos e institutos são carregados da API.
- `/metodologia`: regras efetivamente implementadas.
- `/apuracao`: placeholder sem dados fictícios.

O admin e os endpoints de escrita não têm autenticação, conforme o escopo desta fase. O horário de cadastro usa o fuso do dispositivo e é enviado como um instante UTC; a publicação é exibida em `America/Sao_Paulo`. Datas de campo não sofrem conversão de fuso.

O seed configura Flávio Bolsonaro (PL, 22) e Lula (PT, 13) conforme o escopo solicitado. Não é confirmação de candidatura ou integração oficial com o TSE. Também cadastra os 14 institutos solicitados, sem pesquisas ou percentuais inventados.

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

Os pacotes em `br.com.election` são organizados por domínio: `candidate`, `pollster`, `poll`, `common`. Não há serviço de agregado nesta fase. `PollsterPerformance` pertence ao domínio `pollster` e apenas prepara a persistência para calibração futura.

Migrations em `api/src/main/resources/db/migration`:

1. `V1__create_candidates.sql`
2. `V2__create_pollsters.sql`
3. `V3__create_polls.sql`
4. `V4__create_poll_results.sql`
5. `V5__create_pollster_performance.sql`
6. `V6__seed_candidates.sql`
7. `V7__seed_pollsters.sql`

UUIDs nativos, timestamps com fuso, percentuais `NUMERIC(7,4)`, FKs e constraints explícitas. Há unicidade de instituto por slug, resultado por pesquisa/candidato e desempenho por instituto/eleição/turno. Índices cobrem publicação, seleção por instituto e referências de candidatos. A seleção de latest usa `DISTINCT ON` no PostgreSQL, sem carregar o histórico em memória.

## Testes

Com Docker em execução, na pasta `api`:

```powershell
.\mvnw.cmd test
```

Linux/macOS: `./mvnw test`. O Testcontainers cria bancos isolados; não usa o banco local de desenvolvimento. A suíte cobre contexto, migrations/seeds, persistência, API, normalização, atualização/rollback, latest/desempates, unicidade, validação, delete e CORS. Os repositories não são mockados.

Na pasta `web`:

```sh
npm run lint
npm run build
```

## Escopo futuro

Consulte [docs/polling-model.md](docs/polling-model.md). Integração TSE, SSE, apuração, autenticação, mapas, projeção probabilística e ponderação estatística não foram implementados nesta fase.
