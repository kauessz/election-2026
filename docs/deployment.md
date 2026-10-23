# Deploy: Railway + Vercel

Esta fase prepara o deploy; não publica serviços nem transfere a base local. Fórmulas, STATE_SHRINKAGE_V1, thresholds e dados eleitorais permanecem intactos. Não criar PreElectionFreeze durante a configuração.

## Railway: API e PostgreSQL

1. Criar um projeto Railway e adicionar PostgreSQL como serviço separado chamado `Postgres`.
2. Criar serviço a partir do mesmo repositório GitHub. Root Directory: `/api`. Config File: `/api/railway.json` se a interface exigir caminho relativo ao repositório.
3. O Dockerfile dentro de `api` usa Maven/JDK 21 e JRE 21, tags versionadas, usuário não-root e somente o jar no runtime. O build ignora compilação/execução dos testes; a suíte deve passar localmente/CI antes de publicar. Comando local: `docker build -t election-api:phase6 ./api`.
4. Configurar as variáveis abaixo na API. Valores `${{...}}` são referências Railway ao serviço Postgres, não interpolação PowerShell. Não colar esse bloco em um shell.

```dotenv
SPRING_DATASOURCE_URL=jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}
SPRING_DATASOURCE_USERNAME=${{Postgres.PGUSER}}
SPRING_DATASOURCE_PASSWORD=${{Postgres.PGPASSWORD}}
SPRING_PROFILES_ACTIVE=production
ADMIN_API_KEY=<segredo-gerado-e-selado-no-Railway>
APP_CORS_ALLOWED_ORIGINS=https://<frontend-exato>.vercel.app
ELECTION_TSE_ENABLED=true
```

5. Manter o banco em private networking, na mesma região da API. Confirmar que PGHOST resolve o host privado. Não exigir `DATABASE_PUBLIC_URL`, não abrir domínio/TCP proxy público do Postgres para a API. A URL JDBC não deve conter usuário/senha: usar as variáveis separadas.
6. Railway fornece `PORT`; a API respeita esse valor, com default local 8081. Healthcheck Path: `/actuator/health`; timeout 300 segundos, configurado em railway.json. Flyway habilitado e Hibernate `ddl-auto=validate`. Sem banco/migrations válidos não há startup bem-sucedido; health inclui conexão DB e estado de prontidão, sem detalhes sensíveis.
7. Gerar domínio público HTTPS da API e executar o smoke test abaixo. Confirmar Flyway, `startup official_recovery_complete` e saúde UP nos logs.
8. **Uma única réplica e uma única região ativa.** railway.json solicita numReplicas=1; conferir também no painel que não há réplica extra/região adicional. O scheduler TSE é local; réplicas concorrentes podem duplicar polling/snapshots. Antes de replicas >1, implementar leader election/distributed lock. Não foi implementado lock distribuído nesta fase.

O profile production falha no startup se ADMIN_API_KEY estiver vazia/ausente. A chave não aparece nas respostas nem nos logs de autenticação. Não habilitar logging de headers HTTP, dump de Environment, Actuator env/configprops ou Hikari DEBUG. Logs estruturados em JSON (Logstash) em production; Flyway/Hikari ficam em WARN para evitar imprimir conexão. O evento de startup confirma recovery após migrations. Para investigar HTTP 200/304 e snapshot sem mudança, habilitar temporariamente DEBUG apenas em `br.com.election.tse.TseResultsClient`/`br.com.election.projection.ProjectionService`, sem logging global de requests.

## Vercel: frontend

1. Importar o mesmo repositório; Root Directory `/web`, framework Next.js, instalação padrão (`npm ci`) e build `npm run build`.
2. Configurar em Production e Preview:

```dotenv
NEXT_PUBLIC_API_URL=https://<dominio-publico-api-railway>
ADMIN_UI_ENABLED=false
```

3. Deploy e confirmar páginas públicas mesmo com API temporariamente indisponível (mensagem de erro/retry, sem quebra global).
4. Atualizar APP_CORS_ALLOWED_ORIGINS na API com o domínio Vercel exato e, depois, o domínio customizado. Aceita CSV; sem wildcard, caminhos ou credenciais. Redeploy da API se necessário. NEXT_PUBLIC_API_URL é incorporada ao build: alterou a URL, reconstruir Vercel.
5. `/admin` e todos os descendentes retornam HTTP 404 quando ADMIN_UI_ENABLED não for literalmente `true`. O bloqueio é no servidor (Proxy e layout), inclusive navegação RSC. O link administrativo fica oculto. Não existe chave administrativa no frontend, nem variável NEXT_PUBLIC_ADMIN_API_KEY. Não cadastrar ADMIN_API_KEY no projeto Vercel.

Local: copiar `web/.env.example` para `.env.local` (ignorado pelo Git), usando ADMIN_UI_ENABLED=true e a URL local da API. Sem chave configurada na API local, o fluxo administrativo anterior continua funcionando. Se configurar uma chave local, usar HTTP client com X-Admin-Key; a UI não recebe o segredo.

## Segurança e CORS

Somente `/api/admin/**` exige X-Admin-Key. Ausente/vazio: 401; inválido: 403; correto: prossegue. Comparação constante de digests SHA-256 com tamanho fixo. Endpoints públicos e health permanecem anônimos. Preflight não contém segredo e passa pela validação CORS; a requisição administrativa seguinte exige chave. CORS não substitui autenticação.

Origens locais: http://localhost:3000. Em production, sem APP_CORS_ALLOWED_ORIGINS nenhuma origem cross-origin é liberada. Lista exata, CSV, sem credentials e sem `*`. API base remove barras finais; nenhum localhost fica hardcoded no cliente. A URL local fica somente no arquivo de ambiente de desenvolvimento. Sem URL configurada, chamadas são relativas e falhas recebem a mensagem de conexão; configurar NEXT_PUBLIC_API_URL antes do build de produção.

## Smoke test sem escrita

Na raiz, em PowerShell (a chave vem de secret/env, nunca de arquivo versionado):

```powershell
.\scripts\production-smoke.ps1 -ApiBaseUrl 'https://<api>' -AdminApiKey $env:ADMIN_API_KEY
.\scripts\admin-ui-smoke.ps1 -WebBaseUrl 'https://<frontend>'
```

O primeiro script faz somente GET: health, candidates, pollsters, polls/latest, aggregates, source-status, president, projection, freeze e data-quality sem/com chave. Freeze ausente (204) é válido. Sem AdminApiKey, apenas o check autenticado é marcado SKIP; os demais continuam. O segundo confere 404 em quatro rotas admin por HTML e RSC; `-Enabled` serve apenas para teste local deliberado da UI habilitada. Não executam import, snapshot ou freeze.

## Carga explícita após o deploy

O banco de produção começa sem pesquisas reais, históricos, snapshots ou freeze. As 12 migrations existentes só mantêm os schemas/seeds estruturais já versionados. Não há nova migration nem cópia automática do banco local. Institutos extras da base local precisam ser cadastrados explicitamente por POST /api/admin/pollsters antes do dry run: DataTrends (`datatrends`), Vox Brasil (`vox-brasil`), Instituto França (`instituto-franca`), Meio/Ideia (`meio-ideia`) e Alfa Inteligência (`alfa-inteligencia`). Conferir primeiro GET /api/pollsters para evitar duplicatas. Não inventar registros nem slugs.

Comandos abaixo são um procedimento para execução posterior pelo operador. Não foram executados em produção por esta fase. Executar um passo por vez, inspecionando falhas, qualidade e auditoria antes da próxima escrita. A chave deve vir de secret/env, sem transcrição de sessão ou verbose de headers.

```powershell
$base = 'https://<api>'
$headers = @{ 'X-Admin-Key' = $env:ADMIN_API_KEY }
$polls = [IO.File]::ReadAllBytes((Resolve-Path 'data/polls.real.2026-10-23.json'))
$history = [IO.File]::ReadAllBytes((Resolve-Path 'data/pollster-performance.real.2026-first-round.json'))
Invoke-RestMethod -Method Post -Uri "$base/api/admin/polls/import?dryRun=true" -Headers $headers -ContentType 'application/json' -Body $polls
# Após revisão do dry run:
Invoke-RestMethod -Method Post -Uri "$base/api/admin/polls/import" -Headers $headers -ContentType 'application/json' -Body $polls
Invoke-RestMethod -Method Post -Uri "$base/api/admin/pollster-performances/import?dryRun=true" -Headers $headers -ContentType 'application/json' -Body $history
# Após revisão do dry run:
Invoke-RestMethod -Method Post -Uri "$base/api/admin/pollster-performances/import" -Headers $headers -ContentType 'application/json' -Body $history
Invoke-RestMethod -Uri "$base/api/admin/data-quality" -Headers $headers
Invoke-RestMethod -Uri "$base/api/aggregates"
Invoke-RestMethod -Uri "$base/api/aggregates/audit"
# Somente após validar qualidade, quatro modelos, pesos e neutralidade do Ipespe:
Invoke-RestMethod -Method Post -Uri "$base/api/admin/aggregates/snapshot" -Headers $headers
Invoke-RestMethod -Uri "$base/api/aggregates/history"
```

Consultar [runbook](production-runbook.md) para backup, restore e incidentes.

## Referências oficiais consultadas

- [Railway: Dockerfile](https://docs.railway.com/builds/dockerfiles)
- [Railway: config as code](https://docs.railway.com/config-as-code/reference)
- [Railway: healthchecks](https://docs.railway.com/deployments/healthchecks)
- [Vercel: Root Directory e build](https://vercel.com/docs/builds/configure-a-build)
- [Next.js: variáveis de ambiente](https://nextjs.org/docs/app/guides/environment-variables)
