# Deploy: Cloud Run + Neon PostgreSQL + Vercel

Somente preparação: nenhum comando de criação/deploy abaixo foi executado. Não usar credenciais reais nesta tarefa, fazer push, migrar dados automaticamente ou criar PreElectionFreeze. Railway deixou de ser o target; sua configuração foi removida. O relatório antigo da Fase 6 é histórico, não um procedimento atual.

## Contrato de produção

| Configuração | Valor inicial |
|---|---|
| API / região | election-2026-api / southamerica-east1 |
| CPU / memória | 1 vCPU / 1 GiB |
| Billing / CPU | instance-based / CPU sem throttling entre requests |
| Scaling do serviço | mínimo 1 **e** máximo 1 |
| HTTP concurrency / timeout | 40 / 60 segundos |
| Startup probe | GET /actuator/health |
| Acesso HTTP | público/unauthenticated; admin continua X-Admin-Key |
| Banco | Neon PostgreSQL, JDBC padrão, TLS obrigatório para Neon |
| Frontend | Vercel, root web, ADMIN_UI_ENABLED=false |

`PORT` é injetada pelo Cloud Run: não criar secret/variável customizada PORT nem passá-la em --set-env-vars. `server.port=${PORT:8081}` permanece; production escuta 0.0.0.0. O Dockerfile existente em api é reutilizado: multi-stage Maven/JDK 21, JRE 21 versionado, usuário election não-root e exec-form `java -jar` como processo principal, recebendo SIGTERM. Não há credenciais, arquivos locais de produção ou dependência de provedor na imagem. Não foi criada pipeline CI/CD.

## 1. Preparação GCP e IAM

1. Criar projeto GCP dedicado, habilitar billing e escolher southamerica-east1. Criar budget e billing alerts antes do uso. Alertas avisam; não são um teto financeiro automático.
2. Habilitar Cloud Run Admin API, Cloud Build API, Artifact Registry API e Secret Manager API. O deploy por source usa Cloud Build e Artifact Registry e encontra o Dockerfile no diretório api, sem trocar para buildpacks.
3. Criar service account de runtime dedicada, por exemplo `election-2026-api@PROJECT_ID.iam.gserviceaccount.com`. Não conceder Owner/Editor. Seu acesso GCP nesta fase limita-se a Secret Accessor nos quatro secrets específicos usados pela API.
4. Separar identidade de runtime da identidade de deploy/build. Seguir os papéis mínimos de source deploy e Cloud Run Builder na documentação oficial; o operador precisa poder usar a service account e autorizar acesso HTTP público. Não resolver erros concedendo Editor à conta de runtime. Políticas organizacionais podem impedir allUsers/unauthenticated; nesse caso o administrador precisa resolver a política antes de publicar.
5. Não implementar IAP, Cloud Scheduler, worker separado ou distributed lock nesta fase.

## 2. Neon e conexão JDBC

Criar projeto dedicado `election-2026-production`. Escolher no Neon Console uma região disponível para a conta geograficamente próxima do Cloud Run, priorizando latência entre os dois. Não presumir disponibilidade de uma região específica.

Copiar os connection details do Neon Console. Para runtime, preferir a opção pooled oferecida pelo Console (hostname normalmente contém -pooler); nunca fabricar esse hostname no código. Guardar usuário/senha separados da URL. Formato JDBC, com host e database copiados do Console:

```text
jdbc:postgresql://<HOST>:5432/<DATABASE>?sslmode=require
```

Não colocar valores reais nesses exemplos ou no Git. Direct e pooled são suportados pelo mesmo driver PostgreSQL; não há SDK Neon nem flags novas de PgBouncer. Para pg_dump/pg_restore e operações que precisam de sessão persistente, usar endpoint **direto** do Console.

Production configura Hikari maximum-pool-size=5 e minimum-idle=1; nenhum timeout foi modificado. Flyway continua habilitado e Hibernate ddl-auto=validate. A validação lê a URL efetiva usada pelo Spring: para neon.tech exige sslmode=require (ou verify-ca/verify-full, mais estritos); rejeita sslmode=disable/modos fracos e parâmetros duplicados. A regra não é aplicada ao profile local. Exceções não incluem a URL ou credenciais.

Os testes locais validam PostgreSQL, Flyway, Hibernate e Hikari; **Neon/pooler real não foi acessado**. A documentação Neon recomenda direct para migrations por possíveis limitações de sessões. Antes da carga real, validar Flyway no endpoint escolhido em banco de teste. Se houver incompatibilidade, usar URL direta exclusivamente em `SPRING_FLYWAY_URL`, injetada por um secret separado, mantendo o runtime pooled. O Spring já suporta esse override; ele não foi ativado e não foi adicionada arquitetura paralela de migrations.

## 3. Secret Manager

Criar via Console os secrets abaixo e adicionar valores/versões por interface segura. Não digitar valores em comandos versionados, histórico de shell, logs, arquivos .env do repositório ou frontend.

| Secret | Environment no Cloud Run |
|---|---|
| election-admin-api-key | ADMIN_API_KEY |
| election-db-url | SPRING_DATASOURCE_URL |
| election-db-username | SPRING_DATASOURCE_USERNAME |
| election-db-password | SPRING_DATASOURCE_PASSWORD |

DB_URL também fica como secret por conter infraestrutura. Os nomes genéricos DB_USERNAME/DB_PASSWORD representam esses secrets, não novas propriedades da aplicação. Conceder `roles/secretmanager.secretAccessor` à conta de runtime **em cada um desses quatro secrets**, não no projeto inteiro. Exemplo seguro de binding, sem valores de segredo:

```powershell
$runtimeIdentity = 'election-2026-api@PROJECT_ID.iam.gserviceaccount.com'
gcloud secrets add-iam-policy-binding election-admin-api-key --member="serviceAccount:$runtimeIdentity" --role=roles/secretmanager.secretAccessor
# Repetir somente para os três secrets de banco listados acima.
```

Usar versões numéricas aprovadas nos bindings de env vars; os exemplos :1 abaixo são placeholders de versão, a conferir no Console. Rotacionar criando nova versão e atualizando a revisão, sem imprimir valores. Production sem ADMIN_API_KEY falha no startup.

Variáveis não sensíveis da API: SPRING_PROFILES_ACTIVE=production, APP_CORS_ALLOWED_ORIGINS com origem exata Vercel e ELECTION_TSE_ENABLED=true. O arquivo `api/cloudrun.env.example.yaml` é somente um exemplo não sensível. Não definir ADMIN_UI_ENABLED na API e não definir PORT manualmente.

## 4. Deploy por source — procedimento para execução posterior

A CLI gcloud não está instalada neste workspace. A sintaxe foi conferida na referência oficial atual em 23/10/2026, incluindo --min, --max, --no-cpu-throttling e --startup-probe. No ambiente do operador, conferir `gcloud version` e `gcloud run deploy --help`; atualizar a CLI se necessário. Alternativamente, configurar os mesmos valores no Console. Nenhuma flag foi executada nesta tarefa.

Após IAM, secrets e projeto preparados, executar **a partir de api/**. O Dockerfile atual será usado pelo source build. Estes comandos publicam recursos: são documentação, não autorização para executá-los nesta preparação.

```powershell
gcloud config set project PROJECT_ID
gcloud config set run/region southamerica-east1
# Executar a partir de api/:
gcloud run deploy election-2026-api `
  --source . `
  --region southamerica-east1 `
  --allow-unauthenticated `
  --service-account election-2026-api@PROJECT_ID.iam.gserviceaccount.com `
  --cpu 1 `
  --memory 1Gi `
  --no-cpu-throttling `
  --min 1 `
  --max 1 `
  --concurrency 40 `
  --timeout 60 `
  --startup-probe="httpGet.path=/actuator/health,periodSeconds=10,timeoutSeconds=5,failureThreshold=24" `
  --set-env-vars="SPRING_PROFILES_ACTIVE=production,APP_CORS_ALLOWED_ORIGINS=https://frontend.example.org,ELECTION_TSE_ENABLED=true" `
  --set-secrets="ADMIN_API_KEY=election-admin-api-key:1,SPRING_DATASOURCE_URL=election-db-url:1,SPRING_DATASOURCE_USERNAME=election-db-username:1,SPRING_DATASOURCE_PASSWORD=election-db-password:1"
```

Substituir PROJECT_ID, domínio exato e versões pelos valores aprovados. Os comandos não contêm credenciais reais. O domínio example.org não é o domínio de produção. Não adicionar --port nem uma variável PORT: usar a porta padrão injetada pela plataforma. O mesmo Dockerfile foi validado com PORT=8080 apenas no teste Docker local, não em um deploy.

`--set-env-vars`/`--set-secrets` substituem listas existentes: revisar antes de um redeploy; para mudanças pontuais usar o Console ou as variantes --update-* da CLI. Para CORS CSV com vários domínios, usar o Console ou arquivo local de env fora do Git, evitando confundir a vírgula da lista CORS com o delimitador da CLI.

Após deploy, conferir no Console/descrição do serviço: região, uma revisão recebendo tráfego, min=1, max=1, instance-based billing, 1 CPU/1GiB, concurrency 40, timeout 60s, secrets e startup probe. A opção --no-cpu-throttling habilita a CPU entre requests, necessária para as threads Spring/Java do scheduler. Não confiar somente em max=1: min=1 evita scale-to-zero na janela crítica. Não criar health ping externo para manter a instância viva.

O health atual serve como startup probe: depende de DB e prontidão; retorna 503 se banco indisponível e não expõe detalhes. Orçamento de startup de até 240s (24 × 10s), dentro do limite da plataforma. Não usar health que consulta banco como liveness agressiva: falha transitória do Neon não deve criar ciclo de restarts. A disponibilidade do TSE não entra no health.

## 5. Scheduler e substituição de instâncias

Nada mudou no polling: nacional aproximadamente 5s, ciclo UF existente, municípios sob demanda/cache, ETag/Last-Modified, timeouts, rate limiter, retry/backoff e deduplicação. Uma CPU alocada entre requests permite que as threads Java continuem trabalhando. Request timeout 60s não altera os timeouts HTTP internos TSE.

Min/max=1 reduzem concorrência operacional, mas não constituem exclusão distribuída: a plataforma pode substituir instâncias e ter sobreposição temporária, especialmente em deploy/revisões. Evitar tráfego dividido, revisões extras com minimum instances e deploys na janela crítica. Uma garantia estrita de scheduler único em qualquer rollout precisará de leader/distributed lock futuro, que não foi implementado.

Estado importante fica no PostgreSQL. O startup recovery existente recarrega os últimos snapshots oficiais; o scheduler retoma e indisponibilidade temporária do TSE não impede startup. O frontend mostra o último estado com STALE quando aplicável. Sem estado anterior, UNAVAILABLE é esperado. Monitorar memória e contagem real de instâncias, sem elevar preventivamente para 2GiB.

## 6. Saúde, smoke e carga

Após API + Neon reais, validar health e executar o smoke read-only a partir da raiz:

```powershell
.\scripts\production-smoke.ps1 -ApiBaseUrl 'https://<cloud-run-url>' -AdminApiKey $env:ADMIN_API_KEY
```

O script é agnóstico ao provedor; só faz GET. Freeze ausente (204) é válido. Confere admin sem chave 401 e com chave 200. Não importa nada nem cria snapshots.

## Carga explícita após o deploy

O banco Neon de produção começa sem pesquisas reais, históricos, snapshots ou freeze. As 12 migrations existentes só mantêm os schemas/seeds estruturais já versionados. Não há nova migration nem cópia automática do banco local. Institutos extras da base local precisam ser cadastrados explicitamente por POST /api/admin/pollsters antes do dry run: DataTrends (`datatrends`), Vox Brasil (`vox-brasil`), Instituto França (`instituto-franca`), Meio/Ideia (`meio-ideia`) e Alfa Inteligência (`alfa-inteligencia`). Conferir primeiro GET /api/pollsters para evitar duplicatas. Não inventar registros nem slugs.

Comandos abaixo são um procedimento para execução posterior pelo operador. Não foram executados em produção por esta fase. Executar um passo por vez, inspecionando falhas, qualidade e auditoria antes da próxima escrita. A chave deve vir de secret/env, sem transcrição de sessão ou verbose de headers.

```powershell
$base = 'https://<cloud-run-url>'
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

## 7. Vercel e CORS

Frontend permanece em Vercel, Root Directory web, framework Next.js, instalação/build padrão. Definir NEXT_PUBLIC_API_URL=https://<cloud-run-url> e ADMIN_UI_ENABLED=false em Production/Preview. Nunca definir ADMIN_API_KEY ou NEXT_PUBLIC_ADMIN_API_KEY no frontend. Alterou NEXT_PUBLIC_API_URL, reconstruir o frontend.

Depois do deploy Vercel, configurar APP_CORS_ALLOWED_ORIGINS no Cloud Run com a origem exata, por exemplo https://election-2026.vercel.app; domínio próprio futuro pode ser adicionado via CSV. Os exemplos são ilustrativos, não domínios confirmados do projeto. Sem wildcard, caminhos ou credentials. Admin continua protegido por X-Admin-Key independentemente de CORS; /admin/** continua 404 no frontend.

Executar novamente o smoke da API e `scripts/admin-ui-smoke.ps1 -WebBaseUrl 'https://<frontend>'`. Confirmar navegação pública, tratamento de API indisponível e apuração com STALE quando a fonte estiver atrasada. Operações admin de produção são feitas por HTTP client, sem painel público.

## Referências oficiais

- [gcloud run deploy: flags atuais](https://docs.cloud.google.com/sdk/gcloud/reference/run/deploy)
- [Cloud Run source deploy / Dockerfile / IAM de build](https://docs.cloud.google.com/run/docs/deploying-source-code)
- [Instance-based billing](https://docs.cloud.google.com/run/docs/configuring/billing-settings)
- [Secret Manager no Cloud Run](https://docs.cloud.google.com/run/docs/configuring/services/secrets)
- [Startup probes](https://docs.cloud.google.com/run/docs/configuring/healthchecks)
- [Neon: pooling e limitações de sessão](https://neon.com/docs/connect/connection-pooling)
- [Neon: conexão padrão](https://neon.com/docs/connect/connect-from-any-app)

Backup, restore, custo após a eleição e monitoramento: [production-runbook.md](production-runbook.md).
