# Runbook: Azure Container Apps + Neon + Vercel

Target operacional: Azure Container Apps; Cloud Run é somente alternativa histórica. Nenhum procedimento operacional abaixo foi executado nesta preparação. Não criar freeze, importar banco remoto, excluir snapshots ou alterar dados automaticamente.

## Antes da janela crítica

- Container App election-2026-api em Brazil South/brazilsouth: Consumption, 0.5 CPU/1Gi, min=1 e max=1, activeRevisionsMode=Single. Conferir ingress externo HTTPS, target port 8081, PORT=8081 e imagem GHCR pública fixada por SHA; não depender do alias master. Configuração em infra/azure/main.json.
- Budget e billing alerts ativos no projeto. Max=1 reduz risco de escala, mas não é um teto de custo nem garantia de que nunca haverá instâncias temporárias extras.
- Quatro Azure Container Apps Secrets: admin-api-key, db-url, db-username, db-password, mapeados às env por secretRef. Sem Key Vault obrigatório. Rotação exige reiniciar revisão ou publicar nova revisão; não basta editar o secret. Admin com X-Admin-Key; frontend sem segredo e ADMIN_UI_ENABLED=false. CORS deve ser origem Vercel exata.
- Neon dedicado, região disponível próxima da API, URL pooled fornecida pelo Console com sslmode=require, Hikari max=5/min-idle=1. Preparar endpoint direto para pg_dump e recuperação.
- Validar Flyway/validate no Neon antes de carregar dados reais. O pooler remoto não foi testado localmente. Se migrations exigirem sessão direta, o override padrão SPRING_FLYWAY_URL permite separá-las sem mudar o runtime pooled; ver deployment.md.
- Conferir janela de restore/retention e recursos de backup disponíveis no plano Neon da conta; não presumir retenção ou região. Fazer ensaio em branch/banco de teste antes de depender do restore.

## Backup lógico antes de qualquer freeze futuro

Usar a conexão **direta** copiada do Neon Console, com TLS. Não usar endpoint pooled para pg_dump/pg_restore. Configurar PGHOST/PGPORT/PGDATABASE/PGUSER a partir de um canal seguro. Senha em PGPASSFILE fora do Git, com permissões restritas; não colocá-la na URL ou linha de comando. Não imprimir variáveis nem gravar transcrição com segredos.

Exemplo documental, após o operador configurar o ambiente seguro:

```powershell
$env:PGSSLMODE = 'require'
pg_dump --format=custom --no-owner --no-acl --file='election-before-freeze.dump'
pg_restore --list 'election-before-freeze.dump'
```

Guardar cópia externa protegida e registrar horário, versão/schema, contagens e hash do arquivo. O dump pode conter todos os dados eleitorais operacionais: restringir acesso. Nenhum dump foi executado nesta tarefa e nenhum sistema de backup customizado foi adicionado à aplicação.

## Restore — somente procedimento

1. Registrar incidente, horário-alvo e estado atual. Suspender mudanças admin e parar temporariamente o serviço/scheduler no Console para evitar escritas durante recuperação; min=0 sozinho não garante parada imediata.
2. Quando possível, guardar um dump do estado atual antes de restaurar.
3. Escolher uma branch/banco de teste vazio no Neon com conexão direta. Confirmar o alvo e permissões; nunca ensaiar sobre a base operacional.
4. Restaurar o dump no alvo configurado por PGHOST/PGDATABASE/PGUSER/PGPASSFILE, mantendo PGSSLMODE=require. Exemplo documental: `pg_restore --dbname=$env:PGDATABASE --no-owner --no-acl --exit-on-error election-before-freeze.dump`. O comando escreve no alvo; não executar sem a conferência anterior.
5. Alternativamente, usar recursos de restore/branching disponíveis no Neon Console e plano contratado, verificando a janela de retenção. Não assumir que um deploy anterior do Container App restaura o banco.
6. Validar migrations/schema, contagens, pesquisas, históricos, snapshots e integridade. Se promover o alvo, atualizar os secrets de DB e reiniciar/criar revisão e apontar o serviço deliberadamente para ela, sem imprimir valores.
7. Subir uma instância, conferir recovery, health e smoke read-only, depois data-quality/aggregates/audit/history. Retomar escritas somente após conferência. Não recriar freeze ou snapshots automaticamente para esconder diferenças.

## Monitoramento de tamanho do banco

SQL somente de leitura:

```sql
SELECT pg_size_pretty(pg_database_size(current_database()));

SELECT relname, pg_size_pretty(pg_total_relation_size(relid))
FROM pg_catalog.pg_statio_user_tables
ORDER BY pg_total_relation_size(relid) DESC;
```

Faixas informativas: menos de 500 MB confortável; 500–750 MB acompanhar; acima de 750 MB avaliar upgrade/estratégia. Não são thresholds estatísticos nem rotinas automáticas. Não excluir snapshots automaticamente.

## Health probes

As três probes do template são GET /actuator/health, porta 8081. Startup: delay 30s, período 30s, timeout 5s, 10 falhas; readiness: delay 30s, período 10s, timeout 5s, 6 falhas; liveness: delay 60s, período 30s, timeout 5s, 6 falhas. Startup tolera a inicialização Spring/Flyway antes das demais probes. Não reduzir thresholds durante incidente sem diagnóstico.

Health depende do banco: indisponibilidade prolongada Neon pode provocar restart por liveness, sem resolver a causa. Conferir conectividade, TLS e credenciais; manter health e segurança intactos. O template não cria retenção persistente de logs (destination=none); console/log stream ficam disponíveis, retenção futura exige orçamento/configuração.

## Recovery e diagnóstico

O estado durável fica no PostgreSQL: não há dependência de disco local da réplica Azure para último resultado, pesquisas, históricos ou snapshots. Após migrations, o recovery existente carrega Brasil/UFs/exterior sem chamar o TSE durante essa etapa. Scheduler Java/Spring retoma; ETag/Last-Modified, limites de requisição, timeout, backoff e dedup continuam os existentes. Caches transitórios podem ser reconstruídos no restart; snapshots persistidos são a referência durável. Mais de uma instância exige coordenação distribuída futura, não implementada aqui.

- Health 200 só com DB e prontidão; 503/erro de startup exige verificar conectividade Neon/TLS, credenciais e migrations. Não enfraquecer o health e não usá-lo como ping para manter CPU ligada.
- Admin 401/403: conferir header/chave no HTTP client, nunca no browser. Sem chave, production não inicia. Não habilitar logs de headers, Environment, Hikari DEBUG ou URL com credenciais.
- TSE indisponível: conferir source-status, backoff e STALE; não encurtar intervalos. Ausência temporária do TSE não bloqueia startup. Sem snapshot anterior, UNAVAILABLE é esperado.
- Logs JSON: startup/recovery, requests TSE 200/304 em DEBUG específico, falhas/retry em WARN, snapshot persistido/skipped e status de projeção. Não imprimir payloads/segredos desnecessários.
- Se scheduler parar, conferir réplica Running, min=1, profile production e ELECTION_TSE_ENABLED=true. Max=1 sozinho não evita scale-to-zero. Não usar flags de CPU/billing de Cloud Run no Azure.
- Observar memória/RSS e latência Azure↔Neon. Manter 1GiB se não houver evidência de insuficiência. O smoke local com limite de 1GiB não substitui observação durante totalização completa.
- Sobreposição temporária em restart/deploy pode ocorrer mesmo com máximo 1. Evitar rollout na janela crítica e tráfego dividido; exclusividade estrita requer leader/lock distribuído antes de escalar.

## Depois da eleição

Mudança **manual**, após encerrar a necessidade do scheduler contínuo: no Portal Azure mudar min replicas para 0, manter max replicas=1 e plano Consumption e Neon com autosuspend/scale-to-zero quando disponível no plano. Conserva dados e backups. Não automatizar esse desligamento nem assumir que o scheduler continua rodando com min=0. Revisar budget/alertas, GHCR/Actions, logs Azure, Neon e Vercel; alertas não bloqueiam faturamento automaticamente.

## Referências

[Deploy e carga explícita](deployment.md), [Neon pooling / uso de conexão direta](https://neon.com/docs/connect/connection-pooling), [Azure Consumption](https://learn.microsoft.com/en-us/azure/container-apps/billing) e [Azure secrets](https://learn.microsoft.com/en-us/azure/container-apps/manage-secrets).


## Inbox de pesquisas

O scheduler de descoberta consulta somente o registro PesqEle às 08h/12h/16h de Brasília, com `POLL_DISCOVERY_ENABLED=true`; desligar com false. Não importa percentuais automaticamente. Operação, confirmação de autorização/cenário, revisão, hash e rollback transacional em [poll-discovery.md](poll-discovery.md). A carga real continua exigindo aprovação administrativa. Mantenha max replicas=1.
