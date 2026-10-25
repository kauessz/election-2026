# Deploy: Azure Container Apps + Neon PostgreSQL + Vercel

Target operacional atual: **Azure Container Apps**. Apenas preparação: nenhum deploy, push, carga remota ou freeze foi executado. Cloud Run é alternativa histórica; seus exemplos em api/ e a [validação anterior](cloudrun-neon-validation.md) não orientam o target atual.

## Contrato operacional

| Item | Configuração |
|---|---|
| Região | Brazil South / brazilsouth |
| Plano e workload profile | Consumption |
| Recursos por réplica | CPU 0.5 / memória 1Gi |
| Scaling | min replicas=1 / max replicas=1 |
| Revisões | Single, sem divisão de tráfego |
| Ingress | externo HTTPS, allowInsecure=false, targetPort=8081 |
| Imagem | GHCR público, Linux amd64, tag SHA completo |
| Banco / frontend | Neon PostgreSQL / Vercel |

O Dockerfile existente de api/ permanece intacto: Java 21, build multi-stage, usuário não-root e exec-form para SIGTERM. A aplicação mantém `server.port=${PORT:8081}` e bind production 0.0.0.0. O template define PORT=8081 explicitamente como variável não secreta. Azure usa **target port 8081** no ingress; PORT não configura o ingress.

## 1. Imagem GHCR

[.github/workflows/api-image.yml](../.github/workflows/api-image.yml) publica via GitHub Actions, com push em master quando api/** ou o workflow mudarem, além de workflow_dispatch. Build de api/Dockerfile com contexto api/, linux/amd64:

- `ghcr.io/<github-owner-em-minúsculas>/election-2026-api:<git-sha-completo>`
- alias `master` somente quando a referência executada for master.

Usa GITHUB_TOKEN temporário, contents:read e packages:write. Sem credencial Azure, PAT fixo, ACR ou latest. Deploy/rollback usam o SHA aprovado, nunca dependem do alias master. A label OCI conecta o package ao repositório; packages preexistentes precisam conceder acesso de Actions ao repositório.

Após a primeira publicação, tornar o package **PUBLIC** em Package settings → Change visibility. Confirmar pull anônimo da tag SHA; repositório público não garante package público. O template assume imagem pública.

Alternativa private: configurar registry ghcr.io no Container App, usuário autorizado e PAT classic com read:packages (SSO se exigido pela organização). Guardar o PAT em secret adicional e usar configuration.registries[].passwordSecretRef. Não usar GITHUB_TOKEN temporário como credencial permanente no Azure. Não versionar PAT. [Referência GHCR](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-container-registry).

## 2. Resource group, Environment e Container App

[infra/azure/main.json](../infra/azure/main.json) é template ARM em escopo de resource group. Cria somente Environment Consumption e Container App, com dependência explícita entre eles. Não cria ACR, Key Vault, banco ou workspace de logs. Exportação persistente de logs está desativada (destination=none); usar console/log stream. Retenção futura via Azure Monitor exige configuração e orçamento próprios.

Procedimento para execução posterior pelo operador:

1. Conferir assinatura, permissões e quota Consumption em Brazil South; registrar os providers Microsoft.App/Microsoft.OperationalInsights se exigido pelo serviço.
2. Criar resource group, por exemplo election-2026-production, em Brazil South.
3. Azure Portal → Deploy a custom template → Build your own template → carregar infra/azure/main.json e selecionar esse resource group.
4. Preencher image com a tag SHA pública do GHCR já publicada.
5. Preencher os quatro parâmetros secretos no formulário seguro; são secureString obrigatórios, sem defaults e sem outputs. Não salvar valores no Git.
6. corsAllowedOrigins pode ficar vazio até existir o domínio Vercel. Revisar recursos e custo antes de submeter.

Secrets, env vars, ingress, scaling e probes entram na mesma criação do Container App, evitando primeira revisão sem credenciais. Também é possível aplicar esse ARM por CLI posteriormente com parâmetros seguros fora do Git; nunca usar valores literais no histórico do shell. Nenhum desses procedimentos foi executado nesta entrega.

## 3. Neon e Container Apps Secrets

Neon dedicado em região disponível próxima da API; medir latência, sem presumir que oferece Brazil South. Copiar host/database/credenciais do Neon Console. Preferir endpoint pooled fornecido pelo Console para runtime, sem fabricar hostname. JDBC: `jdbc:postgresql://<HOST>:5432/<DATABASE>?sslmode=require`. Usuário/senha separados da URL; backup/restore usa endpoint direto.

Hikari production permanece max=5/min-idle=1. Flyway habilitado, Hibernate validate e validação TLS existentes permanecem intactos. Neon/pooler remoto exige ensaio antes da carga. Se migrations exigirem conexão direta, avaliar o override padrão Spring SPRING_FLYWAY_URL via secret separado; ele não foi ativado nem alterado nesta fase.

| Parâmetro ARM seguro | Container Apps Secret | Env com secretRef |
|---|---|---|
| adminApiKey | admin-api-key | ADMIN_API_KEY |
| dbUrl | db-url | SPRING_DATASOURCE_URL |
| dbUsername | db-username | SPRING_DATASOURCE_USERNAME |
| dbPassword | db-password | SPRING_DATASOURCE_PASSWORD |

São Azure Container Apps Secrets, **sem exigir Key Vault**. Nenhum valor é versionado. Production sem chave continua falhando no startup; X-Admin-Key permanece obrigatório para administração.

Env não secretas: SPRING_PROFILES_ACTIVE=production, PORT=8081, ELECTION_TSE_ENABLED=true e APP_CORS_ALLOWED_ORIGINS com origem Vercel exata, inicialmente vazia. Não definir ADMIN_UI_ENABLED na API.

Rotação de secret não cria revisão automaticamente: reiniciar deliberadamente a revisão ativa ou criar nova revisão após alterar o secret. Em redeploy ARM, fornecer parâmetros seguros novamente, sem substituir valores por placeholders. Não imprimir headers, secrets ou environment completo em logs.

## 4. Ingress, scaling e probes

Conferir após criação: HTTPS externo, target port 8081, revisão Single, Consumption, CPU 0.5/1Gi e min/max=1. Não copiar flags de Cloud Run ou usar porta 8080.

Todas as probes usam **GET /actuator/health na porta 8081**, HTTP interno:

| Probe | initialDelay | period | timeout | failureThreshold |
|---|---:|---:|---:|---:|
| Startup | 30s | 30s | 5s | 10 |
| Readiness | 30s | 10s | 5s | 6 |
| Liveness | 60s | 30s | 5s | 6 |

Startup admite cerca de cinco minutos de tentativas após o delay para Spring/Flyway com 0.5 CPU; protege a inicialização antes das demais verificações. Health não foi enfraquecido: banco indisponível continua 503 e detalhes ocultos. TSE indisponível não bloqueia startup.

Como liveness também usa esse health, uma indisponibilidade prolongada do Neon pode reiniciar a réplica após falhas consecutivas. Thresholds conservadores evitam reinício imediato, mas não recuperam um banco indisponível: diagnosticar Neon/TLS. [Probes Azure](https://learn.microsoft.com/en-us/azure/container-apps/health-probes).

## 5. Smoke, scheduler e recovery

Após API+Neon reais, usar apiUrl retornado pelo template:

```powershell
.\scripts\production-smoke.ps1 -ApiBaseUrl 'https://<container-app-fqdn>' -AdminApiKey $env:ADMIN_API_KEY
```

O script existente só faz GET, aceita freeze ausente (204), verifica admin sem chave 401/com chave 200 e não altera dados.

Neon guarda o estado durável fora da réplica. Restart não perde pesquisas, históricos, snapshots ou último resultado oficial **persistido**. O recovery existente lê Brasil/UFs/exterior do banco no startup, sem nova chamada ao TSE; scheduler Spring reinicia normalmente. Caches/estado ainda não persistido não têm essa garantia.

Manter min=1 enquanto o scheduler estiver ativo, sem depender de tráfego HTTP para manter a réplica. Max=1 e revisão Single reduzem concorrência, mas substituições/rollouts ainda podem sobrepor processos. Evitar rollout na janela crítica e manter max=1 até implementar distributed lock. Intervalos, retry/backoff, ETag e persistência não mudam.

## Carga explícita após o deploy

O banco Neon de produção começa sem pesquisas reais, históricos, snapshots ou freeze. As 15 migrations mantêm os schemas e seeds estruturais versionados, incluindo histórico isolado (V13) e inbox (V14). Não há carga automática de percentuais nem cópia do banco local. A descoberta PesqEle apenas prepara staging; consulte [docs/poll-discovery.md](poll-discovery.md). Institutos extras da base local precisam ser cadastrados explicitamente por POST /api/admin/pollsters antes do dry run: DataTrends (`datatrends`), Vox Brasil (`vox-brasil`), Instituto França (`instituto-franca`), Meio/Ideia (`meio-ideia`) e Alfa Inteligência (`alfa-inteligencia`). Conferir primeiro GET /api/pollsters para evitar duplicatas. Não inventar registros nem slugs.

Comandos abaixo são um procedimento para execução posterior pelo operador. Não foram executados em produção por esta fase. Executar um passo por vez, inspecionando falhas, qualidade e auditoria antes da próxima escrita. A chave deve vir de secret/env, sem transcrição de sessão ou verbose de headers.

```powershell
$base = 'https://<container-app-fqdn>'
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


Resultados de referência para a carga de 18 pesquisas + 17 históricos, data de referência 23/10/2026:

| Modelo | Flávio | Lula |
|---|---:|---:|
| SIMPLE | 50.9551% | 49.0449% |
| RECENCY | 50.9383% | 49.0617% |
| WEIGHTED | 50.9630% | 49.0370% |
| CALIBRATED | 50.9549% | 49.0451% |

Conferir também pollCount=18, pollsterCount=18, totalSampleSize=59442, Ipespe neutro, pesos somando 1 e cap 15%. Não criar freeze nesta fase. Nenhum desses imports foi executado remotamente.


## 6. Vercel e CORS

Vercel: Root Directory web, Next.js, instalação/build padrão. Definir NEXT_PUBLIC_API_URL=https://<container-app-fqdn> e ADMIN_UI_ENABLED=false em Production/Preview. Nunca colocar ADMIN_API_KEY ou NEXT_PUBLIC_ADMIN_API_KEY no frontend. Mudança da URL pública exige novo build.

Depois de obter o domínio Vercel, preencher APP_CORS_ALLOWED_ORIGINS no Container App com origem HTTPS exata, sem caminho/wildcard. Domínio próprio futuro pode entrar via CSV. Atualizar também corsAllowedOrigins nos próximos deployments ARM para não apagar a configuração. Alteração de env cria revisão: conferir health e réplica única.

Reexecutar smoke API e `scripts/admin-ui-smoke.ps1 -WebBaseUrl 'https://<frontend>'`. Conferir navegação pública e /admin/** com 404. CORS não substitui X-Admin-Key.

## 7. Custos e referências

Consumption cobra recursos/uso conforme tarifa vigente; min=1 mantém réplica e impede scale-to-zero enquanto scheduler roda. Não prometer custo zero nem tratar max=1 como teto financeiro. Após eleição, encerrada a necessidade do scheduler contínuo, mudar **manualmente min replicas para 0** e manter max=1. Sem réplica não há scheduler; tráfego pode reativar a aplicação. Considerar Neon/Vercel/logs e configurar orçamento/alertas Azure.

- [ARM Container App](https://learn.microsoft.com/en-us/azure/templates/microsoft.app/2025-01-01/containerapps)
- [ARM Environment](https://learn.microsoft.com/en-us/azure/templates/microsoft.app/2025-01-01/managedenvironments)
- [GitHub Actions e GHCR](https://docs.github.com/en/actions/tutorials/publish-packages/publish-docker-images)
- [Azure Consumption e cobrança](https://learn.microsoft.com/en-us/azure/container-apps/billing)
- [Neon pooling](https://neon.com/docs/connect/connection-pooling)

Backup, recuperação e operação: [production-runbook.md](production-runbook.md).
