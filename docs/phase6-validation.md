# Validação local — Fase 6

> Registro histórico: Railway deixou de ser o target atual. As instruções vigentes são [deployment.md](deployment.md) e [production-runbook.md](production-runbook.md), para Cloud Run + Neon + Vercel. Os resultados abaixo registram a validação anterior, sem orientar deploy atual.

Preparação Railway/Vercel concluída em 23/10/2026. Não houve deploy público, commit, freeze, carga de produção ou alteração das fórmulas/thresholds. Base operacional conferida integralmente antes/depois: 18 pesquisas, 17 históricos, cinco snapshots agregados e nenhum PreElectionFreeze, com os mesmos registros e resultados.

## Testes e builds

- API: `.\mvnw.cmd test` em api, BUILD SUCCESS; **245 testes, zero falhas/erros/skips**. Inclui 11 novos testes: 7 de API production (auth ausente/inválida/válida, rotas públicas, health sem detalhes, CORS/CSV/preflight e recovery STALE sem HTTP TSE) e 4 de configuração (chave obrigatória/vazia, compatibilidade local, origens inválidas, PORT/datasource/Flyway/validate).
- Web: `npm test`, **3 testes novos aprovados** para URL sem barras duplicadas, ausência de localhost em produção e erro recuperável de API indisponível. **248 testes automatizados no total**.
- Web: `npm run lint` e `npm run build` aprovados. Build production com ADMIN_UI_ENABLED=false; rotas admin dinâmicas, Proxy habilitado. Nenhuma referência ADMIN_API_KEY/NEXT_PUBLIC_ADMIN_API_KEY no bundle estático público.
- Docker: `docker build -t election-api:phase6 ./api` aprovado; Maven/JDK 21 no builder, JRE 21 e usuário election no runtime, jar único, testes não executados na imagem.
- `git diff --check` aprovado.

## Smoke HTTP e banco isolado

- `production-smoke.ps1` aprovado contra a imagem em profile production, em porta/rede/DB temporários separados: oito endpoints públicos 200, freeze ausente 204, data-quality sem chave 401 e com chave 200. Apenas GETs no smoke.
- DB disponível + migrations + prontidão: health 200/UP. Depois de parar somente o PostgreSQL temporário: health 503/DOWN, sem detalhes de conexão/senha.
- Banco recém-criado: 12 migrations aplicadas, zero pesquisas, zero históricos, zero snapshots agregados, zero freeze; nenhum seed eleitoral real adicionado.
- A imagem sem ADMIN_API_KEY, com banco temporário disponível, encerrou com exit 1 e mensagem genérica de chave obrigatória; nenhum valor de segredo registrado. A validação de chave vazia/ausente também passou nos testes de contexto production.
- `admin-ui-smoke.ps1`: quatro rotas conhecidas, HTML e RSC; oito checks 404 com UI desabilitada e oito checks 200 quando habilitada deliberadamente em servidor local separado. Páginas públicas retornaram 200. Servidores temporários, containers/rede e arquivos de segredos efêmeros foram removidos.

## Operação preparada

Admin restrito a /api/admin/** por X-Admin-Key (401/403, comparação constante de digests SHA-256). CORS por CSV com origens exatas; production sem allowlist não libera cross-origin. Health público, sem dados sensíveis, com DB e readiness. Profile production exige a chave e variáveis do banco; defaults locais mantidos. Flyway/validate preservados.

Railway: root /api, Postgres privado separado, PORT dinâmico, health /actuator/health, timeout 300 s, uma réplica. Vercel: root /web, NEXT_PUBLIC_API_URL e ADMIN_UI_ENABLED=false. Scheduler TSE, limites/cache/backoff/deduplicação preservados; recovery no ApplicationReadyEvent lê snapshots oficiais sem depender do TSE. Logs JSON em production e eventos de recovery/snapshot/projeção.

A configuração real das plataformas, domínio/CORS definitivo, scheduled backups e ensaio de restore devem ser executados no projeto Railway/Vercel pelo operador; não foram publicados/acionados nesta tarefa. Não há bloqueio local de código conhecido. Avisos não bloqueantes de metadados Maven 401 em repositórios opcionais ocorreram; build/testes completos passaram.

Procedimentos e comandos: [deploy](deployment.md) e [runbook](production-runbook.md).

## Arquivos alterados

- `README.md`
- `api/src/main/java/br/com/election/common/CorsConfiguration.java`
- `api/src/main/java/br/com/election/projection/ProjectionService.java`
- `api/src/main/java/br/com/election/tse/OfficialResultsService.java`
- `api/src/main/java/br/com/election/tse/TseResultsClient.java`
- `api/src/main/resources/application.yaml`
- `web/.env.example`
- `web/app/layout.tsx`
- `web/components/header.tsx`
- `web/lib/api.ts`
- `web/package.json`

## Arquivos novos

- `api/.dockerignore`
- `api/Dockerfile`
- `api/railway.json`
- `api/src/main/java/br/com/election/common/AdminAccessConfiguration.java`
- `api/src/main/resources/application-production.yml`
- `api/src/test/java/br/com/election/ProductionApiTests.java`
- `api/src/test/java/br/com/election/ProductionConfigurationTests.java`
- `docs/deployment.md`
- `docs/production-runbook.md`
- `scripts/admin-ui-smoke.ps1`
- `scripts/production-smoke.ps1`
- `web/app/admin/layout.tsx`
- `web/proxy.ts`
- `web/tests/api-config.test.mjs`
- `docs/phase6-validation.md`

## git diff --stat

```text
 README.md                                          |  4 ++++
 .../br/com/election/common/CorsConfiguration.java  | 20 +++++++++++++++--
 .../com/election/projection/ProjectionService.java |  3 ++-
 .../com/election/tse/OfficialResultsService.java   | 15 +++++++++++--
 .../java/br/com/election/tse/TseResultsClient.java |  2 +-
 api/src/main/resources/application.yaml            | 25 ++++++++++++++++++----
 web/.env.example                                   |  2 ++
 web/app/layout.tsx                                 |  2 +-
 web/components/header.tsx                          |  6 +++---
 web/lib/api.ts                                     |  2 +-
 web/package.json                                   |  3 ++-
 11 files changed, 68 insertions(+), 16 deletions(-)
```

O comando padrão acima não inclui os 15 arquivos novos ainda não rastreados, listados separadamente. Nenhum arquivo foi staged ou commitado.
