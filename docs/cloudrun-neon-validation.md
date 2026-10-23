# Validação da adaptação Cloud Run + Neon

Preparação concluída em 23/10/2026; nenhum deploy, recurso GCP/Neon, credencial real, import remoto, push, commit ou freeze. API e frontend mantêm os endpoints existentes.

## Configuração

- Cloud Run documentado: election-2026-api, southamerica-east1, 1 vCPU/1GiB, instance-based/CPU always allocated, min=1/max=1 no serviço, concurrency=40, timeout=60s, HTTP público e admin X-Admin-Key. Source deploy em api/ reutiliza exatamente o Dockerfile Java 21; Cloud Build e Artifact Registry, sem pipeline nova.
- PORT continua injetada pela plataforma, sem secret/variável customizada no deploy. Bind production 0.0.0.0. O teste Docker local simulou PORT=8080; o comando gcloud não define a porta.
- Neon com JDBC padrão e conexão pooled copiada do Console. Segredos separados via Secret Manager. Hikari 5 máximo/1 idle; timeouts internos intactos. Validação production bloqueia Neon sem sslmode seguro, modos fracos/duplicados e mensagens que poderiam expor a URL. Local não afetado.
- api/railway.json removido; README/deployment/runbook usam o novo target. Referência Railway permanece somente como aviso de transição e no relatório histórico da Fase 6, explicitamente marcado como histórico.
- Vercel continua com root web, NEXT_PUBLIC_API_URL para Cloud Run e ADMIN_UI_ENABLED=false. Nenhuma mudança de frontend, CORS, auth ou smoke script foi necessária; o smoke já era agnóstico ao provedor.
- Scheduler Java/Spring, polling TSE, caches, ETag/Last-Modified, backoff, dedup e recovery são exatamente os existentes. Nenhuma mudança nos modelos, fatores ou thresholds. Não há lock distribuído; sobreposição temporária em substituições ainda é uma limitação documentada, apesar de min/max=1.

## Verificações executadas

| Check | Resultado |
|---|---|
| api: .\mvnw.cmd test | 258 testes, zero falhas/erros/skips, BUILD SUCCESS |
| web: npm test | 3 aprovados |
| web: npm run lint | aprovado |
| web: npm run build | aprovado |
| docker build -t election-api:cloudrun ./api | aprovado, Dockerfile sem alterações |
| git diff --check | aprovado |

**261 testes automatizados no total.** Os 13 novos casos cobrem URLs Neon direct/pooled com TLS, modos require/verify-ca/verify-full, TLS ausente/fraco/duplicado/ambíguo, rejeição production para disable em outros hosts, compatibilidade local, erro sem credenciais e pool/bind production.

## Smoke com limite de recursos

A imagem foi executada em Docker local, usuário não-root election, **limite real de 1GiB e 1 vCPU**, sem swap extra, profile production e scheduler habilitado. PostgreSQL temporário separado e um stub TSE local respondendo 503 evitaram acesso remoto e gravação de resultados oficiais. O smoke só fez GETs.

- Health, candidates, pollsters, polls/latest, aggregates, source-status, president e projection: 200.
- Freeze ausente: 204. Admin data-quality sem chave: 401; com chave efêmera correta: 200.
- Memória observada: **290.6MiB / 1GiB**; OOMKilled=false. Isso valida startup e smoke normal, não uma carga completa de totalização; manter observação de memória durante a eleição.
- Scheduler executou 77 consultas ao stub, com último status 503; health permaneceu UP apesar da fonte indisponível. Nenhuma frequência foi alterada. CPU do Docker permanece disponível entre requests, análoga à exigência documentada de instance-based no Cloud Run.
- As 12 migrations existentes aplicaram no banco temporário; pesquisas/históricos/snapshots agregados/oficiais/projeção/freeze permaneceram em zero. Nenhuma migration nova ou seed real.
- Nenhum segredo efêmero apareceu nos logs. Containers, rede, stub e arquivos temporários de segredos foram limpos após a validação.

## Conservação e limites

A base operacional foi comparada integralmente antes/depois pelos endpoints: **18 pesquisas, 17 históricos, cinco snapshots agregados e nenhum freeze**, sem mudança nos registros. O diff confirma Dockerfile, data/, migrations, agregadores, projeção e integração TSE intocados. A API local voltou a responder após o restart normal do watcher de recompilação; nenhum processo local da aplicação foi substituído.

A CLI gcloud não está instalada aqui; flags foram conferidas na [referência oficial atual](https://docs.cloud.google.com/sdk/gcloud/reference/run/deploy), sem executar deploy ou instalar recursos. Nenhum Neon real/pooler remoto foi acessado. IAM, secrets reais, região Neon disponível, latência, migrations via pooler, backups e restore exigem validação no ambiente de destino. Não há bloqueio local conhecido. Avisos Maven 401 em repositórios opcionais de metadados foram não bloqueantes; testes e imagem passaram.

Procedimentos atuais: [deployment.md](deployment.md) e [production-runbook.md](production-runbook.md), incluindo backup direto, restore apenas documental, tamanho do banco, custos após a eleição e fluxo de carga preservado.

## Arquivos alterados/removidos

- `.gitignore`
- `README.md`
- `api/railway.json`
- `api/src/main/resources/application-production.yml`
- `docs/deployment.md`
- `docs/phase6-validation.md`
- `docs/production-runbook.md`

## Arquivos novos

- `api/.gcloudignore`
- `api/cloudrun.env.example.yaml`
- `api/src/main/java/br/com/election/common/ProductionDatabaseConfiguration.java`
- `api/src/test/java/br/com/election/common/ProductionDatabaseConfigurationTests.java`
- `docs/cloudrun-neon-validation.md`

## git diff --stat

```text
 .gitignore                                        |   3 +
 README.md                                         |   2 +-
 api/railway.json                                  |  11 --
 api/src/main/resources/application-production.yml |   5 +
 docs/deployment.md                                | 175 ++++++++++++++++------
 docs/phase6-validation.md                         |   2 +
 docs/production-runbook.md                        |  84 +++++++----
 7 files changed, 195 insertions(+), 87 deletions(-)
```

O stat padrão não inclui os 5 arquivos novos não rastreados acima. Nada foi staged ou commitado.
