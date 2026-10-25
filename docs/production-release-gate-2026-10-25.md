# Gate final da release — 25/10/2026

## PRE-DEPLOY

**Estado: GO CONTROLADO, condicionado à criação e confirmação de snapshot manual da branch production imediatamente antes da V15.** O snapshot ainda não existe. Esta classificação encerra os bloqueios de identificação e janela/fuso; não autoriza commit, push, deploy, migration ou Restore. Este runbook reúne as consultas anteriores e o restore já realizado em PostgreSQL local isolado; nesta atualização foram alterados somente registros documentais.

Decisão aprovada pelo usuário: **manter o DEFAULT de origin nesta release**. O writer anterior insere somente as sete colunas antigas de projection_snapshots; remover o DEFAULT mantendo NOT NULL quebra sua publicação após rollback. V15 permanece com origin NOT NULL DEFAULT 'LEGACY_UNKNOWN'. O writer novo informa origin explicitamente; os registros anteriores e as escritas do binário anterior continuam LEGACY_UNKNOWN, nunca LIVE artificialmente. Não há alteração de fórmula. Remover o default fica para uma release com janela de rollback compatível.

Evidências iniciais coletadas aproximadamente 02:00–02:10 e complementadas em 25/10/2026, aproximadamente 02:12–02:20 de Brasília. Reconsultar antes de executar, pois produção pode mudar depois desta captura.

| Item | Evidência / situação |
|---|---|
| Repositório API e web | https://github.com/kauessz/election-2026 — monorepo api/ e web/ |
| Branch / HEAD local | master / 87b01855d28eedcef0de273b6b72b4db8935c651 |
| Git | 18 arquivos tracked modificados; readiness e relatórios novos ainda untracked; nada staged. backups/ também untracked, excluir do commit. |
| API efetivamente usada pela web publicada | https://election-2026-api.mangotree-d5e22651.brazilsouth.azurecontainerapps.io — encontrado no bundle servido pela Vercel |
| Saúde API | /actuator/health HTTP 200, UP; /actuator/info retorna {}, sem SHA/revisão |
| Oficial / fonte | /api/election/president HTTP 200, NOT_STARTED; source-status com último sucesso 2026-10-25T05:07:18.148473448Z, HTTP 304, zero falhas consecutivas |
| Nova API | /api/election/president/context ainda 404 em produção, coerente com release não publicada |
| API saudável / rollback desta release | Azure CLI autenticada: `election-2026-api--0000003`, active=true, Healthy, RunningAtMaxScale, 1 réplica, 100% de tráfego. Criada em 2026-10-25T03:02:34Z. **Esta é a revisão a restaurar se a API nova falhar.** |
| API imagem configurada atual | `ghcr.io/kauessz/election-2026-api:87b01855d28eedcef0de273b6b72b4db8935c651`. A tag foi confirmada na configuração remota e está acessível no GHCR. |
| Digest GHCR da tag atual | `sha256:95d5de2a4fb993622d9e8db898982c54c3b8e8ac6e8388b298135be832091946`, obtido com docker manifest inspect. A API de réplica não retornou imageDigest: este é o digest atual do registro, não uma medição do digest já baixado pela réplica. Preservar a revisão e a tag; não sobrescrever tags SHA. |
| Azure identidade | Subscription `966a84fd-8f8c-421e-b744-fe7c58807758` (Azure subscription 1); resource group `election-2026-rg`; Container App `election-2026-api`; região Brazil South. |
| Rollout configurado | `Single`; min=1/max=1; ingress com latestRevision=true e weight=100, resolvendo agora para `election-2026-api--0000003`. maxInactiveRevisions=100. Outras cinco revisões retidas estão inativas, zero réplicas/zero tráfego. |
| Réplica atual | `election-2026-api--0000003-5fc85d79c7-5z2nc`, Running, container ready=true, restartCount=0. |
| Frontend saudável | https://election-2026-khaki.vercel.app/ HTTP 200 |
| Deployment Vercel associado ao HEAD | https://vercel.com/kauesszs-projects/election-2026/65yhbXxsSUeN95iotfjLkD7z6dqU — status GitHub Vercel success, atualizado 2026-10-24T18:57:50Z, SHA 87b01855d28eedcef0de273b6b72b4db8935c651 |
| Alias atual / rollback web | **Confirmado pelo usuário no painel:** projeto election-2026, Environment=Production, Ready, Current, source=master, commit `fix: improve national poll scope detection`, SHA abreviado 87b0185… e ID visível 65yhbXxsS, correspondentes ao SHA e link completos acima; domínio election-2026-khaki.vercel.app e mais dois domínios Production. **Este deployment atual é o rollback primário da próxima release.** Deployment anterior não informado, sem constituir bloqueio adicional. |
| Neon projeto/branch do Console | **Correlação confirmada pelo usuário no painel:** projeto election-2026-production, branch production, id br-solitary-poetry-b6tc25si, endpoint ep-purple-fire-b6adwo6u. Projeto id damp-heart-34208907 já identificado pelo URL do Console. |
| Banco efetivamente usado pela API | URL obtida em memória do secretref db-url, sem gravar/imprimir credenciais: endpoint `ep-purple-fire-b6adwo6u.c-2.sa-east-1.aws.neon.tech`, database `neondb`, PostgreSQL `18.6 (fd34222)`. |
| Schema / Flyway remoto | Consulta psql com default_transaction_read_only=on: 14 registros V1–V14, todos success=true; V14 `poll discoveries`, instalada em 2026-10-24T18:01:05.945274 (timestamp do banco). Nenhum registro V15 e coluna origin ausente. **V15 ainda não aplicada.** Não foi executado Flyway migrate/repair/validate contra produção. |

Revisões anteriores disponíveis, via `az containerapp revision list --all`:

| Revisão | Imagem SHA | Estado observado | Tráfego / réplicas |
|---|---|---|---|
| election-2026-api--0000002 | e2911448b23ddd2901594d2ce56bafc8863cee9c | Healthy, inativa, Stopped | 0% / 0 |
| election-2026-api--history-v13 | 3d3514299c7107c7bf698e4067346e605a857aea | Healthy, inativa, Stopped | 0% / 0 |
| election-2026-api--0000001 | 7b4c7e1e574b003d00ea0cc268fd0d6d7d94660c | Healthy, inativa, Stopped | 0% / 0 |
| election-2026-api--prod-config | 7b4c7e1e574b003d00ea0cc268fd0d6d7d94660c | Healthy, inativa, Stopped | 0% / 0 |
| election-2026-api--b255w63 | 7b4c7e1e574b003d00ea0cc268fd0d6d7d94660c | Healthy, inativa, Stopped | 0% / 0 |

Todas as imagens usam o prefixo `ghcr.io/kauessz/election-2026-api:`. Healthy em revisão parada é o estado armazenado pelo provedor, não um smoke novo daquela imagem. A revisão imediatamente anterior é 0000002; o alvo de rollback da próxima release é **0000003**, atualmente saudável, e não 0000002 por padrão.

Tabela final de identidade para o rollout:

| Item | Atual | Novo deploy | Rollback |
|---|---|---|---|
| API revision | election-2026-api--0000003 | Registrar nome retornado ao criar revisão; ainda não existe | election-2026-api--0000003 |
| API image/digest | ghcr.io/kauessz/election-2026-api:87b01855d28eedcef0de273b6b72b4db8935c651; digest do registro 95d5de2a… (completo acima) | Tag do novo commit e digest do build aprovado; ainda não existem | Mesma imagem configurada na revisão 0000003; digest do registro completo acima |
| Web deployment | 65yhbXxsSUeN95iotfjLkD7z6dqU; Current/Ready/Production confirmado pelo usuário, ID visível 65yhbXxsS | Deployment automático do novo SHA no PUSH; pode preceder API, com fallback temporário validado; smoke antes e depois da API | 65yhbXxsSUeN95iotfjLkD7z6dqU, atual saudável antes da nova release |
| Web SHA | 87b01855d28eedcef0de273b6b72b4db8935c651; abreviação 87b0185… confirmada no painel e correlacionada ao status GitHub | Novo commit aprovado | 87b01855d28eedcef0de273b6b72b4db8935c651 |
| DB branch | production / br-solitary-poetry-b6tc25si; endpoint ep-purple-fire-b6adwo6u / neondb; correlação confirmada pelo usuário | Mesma, sem trocar endpoint/branch | Mesma |
| DB schema | **V14 confirmado por SELECT remoto** | V15, após autorização futura | V15 permanece |
| Backup/PITR | PITR últimas 6 horas, America/Sao_Paulo (GMT-03:00), confirmado pelo usuário; backup CUSTOM restaurado localmente; snapshot manual disponível, nenhum criado | Criar e confirmar snapshot manual pré-V15 de production; registrar ID, branch, instante capturado e expiração/retensão, se exibida | Aplicação volta à revisão anterior mantendo V15; snapshot/PITR somente em incidente de dados separado e autorizado |

**GO CONTROLADO:** alvos de rollback API/web, comportamento automático Vercel, ausência de Railway, schema atual, branch Neon e janela PITR de seis horas com fuso foram identificados. A sequência de exclusividade está definida e não foi executada. **Condição obrigatória antes da V15: snapshot manual pré-migration criado e confirmado na branch production.** Se não puder ser confirmado, voltar a NO-GO para aplicação da migration. Deployment web anterior ao atual é registro complementar, não bloqueio: o atual é o rollback primário da próxima release. **Ensaio local classificou a release como A — BACKWARD_COMPATIBLE:** o push em master pode publicar a web antes da promoção da API durante uma janela curta; contenção Vercel deixou de ser pré-condição desta release. Zero coletores antigos antes do startup novo continua obrigatório. Nenhuma ação externa foi autorizada apenas por esta classificação.

Atualização por confirmação humana nesta conversa, após as consultas técnicas: o usuário verificou o deployment Current/Ready/Production no Vercel e depois confirmou GitHub conectado a kauessz/election-2026, deployments automáticos por commits, Branch Tracking de Production=master, domínios associados e ausência de Deploy Hooks. Determinou tratar push em master como criação automática de deployment Production capaz de atualizar os domínios servidos; nenhuma configuração staged/manual foi identificada. Confirmou que Railway não participa e confirmou endpoint/branch Neon. Na confirmação mais recente, informou PITR disponível por seis horas em America/Sao_Paulo (GMT-03:00), branch production (Default), snapshot manual disponível e nenhum snapshot criado. Isso substitui os placeholders anteriores de janela/fuso. Não houve nova consulta autenticada aos painéis pelo agente.

Gatilhos comprovados e limites da evidência:

- `.github/workflows/api-image.yml`: push em master com mudanças em api/** ou no próprio workflow, e workflow_dispatch, geram imagem GHCR com tag SHA e alias master. Publicação apenas; nenhum passo de deploy. Último run success: https://github.com/kauessz/election-2026/actions/runs/40123079568.
- O Dockerfile pula testes (`-Dmaven.test.skip=true`). O único workflow remoto é o build/publish; master não tem proteção habilitada. Não existe gate de testes obrigatório comprovado antes do deploy.
- **Vercel confirmado pelo usuário no painel:** projeto election-2026 conectado ao GitHub kauessz/election-2026; commits geram deployments automaticamente; Production acompanha master e possui election-2026-khaki.vercel.app e mais dois domínios. Não há Deploy Hooks. **Tratar push em master como ação de produção:** cria deployment Production e, sem staged/manual identificado, pode atualizar automaticamente o frontend servido. Não tratar esse push como simples preview ou publicação de artefatos. A ausência de Deploy Hooks não desativa a integração Git.
- Azure CLI encontrada fora do PATH em `C:/Program Files/Microsoft SDKs/Azure/CLI2/wbin/az.cmd`, autenticada na assinatura acima. `containerapp github-action show` retornou LinkedRepoNotFound: não há repositório ligado por esse mecanismo do Container App. O único workflow GitHub é GHCR publish. Não há evidência de deploy Azure automático por esse caminho; automação externa não é descartada por inferência.
- **Conclusão Railway: não participa da produção atual deste projeto**, conforme confirmação direta do usuário em resposta à verificação de serviços conectados/ativos. Não existe gatilho Railway deste projeto a preparar neste rollout. A conclusão se baseia nessa confirmação, não na ausência de workflow/hooks.
- O controlador de abas falhou; o fallback de interface encerrou o controle porque não conseguiu determinar a URL atual com segurança. Nenhum novo controle de interface foi tentado após esse bloqueio. Vercel/Railway/Neon não têm CLI local autenticada encontrada. Não extrair cookies nem tokens do navegador para contornar a proteção. Foram solicitadas confirmações manuais específicas desses painéis.

Proteção do banco:

- Último backup local não vazio conhecido: `backups/election-2026-post-freeze-20261025-010044.dump`, 436711 bytes. Cabeçalho do arquivo: 2026-10-25 04:00:45 (fuso não indicado no cabeçalho); mtime local 01:00:57. CUSTOM/gzip, PostgreSQL 18.6, 87 entradas no catálogo. **Restore integral executado com sucesso, exit code 0**, em PostgreSQL 18 descartável/local, sem rede e sem portas expostas. Nenhuma restauração remota foi realizada.
- SHA256: `806440076A50B4E87FB8CF9366F730453BE59D21C9EAD97ED45660D0CFD7CD4B`.
- Existem também arquivos de zero bytes em backups/; não são backups utilizáveis. Nenhum foi excluído ou staged.
- Ensaio: container `election-backup-restore-gate-20261025`, imagem local postgres:18, database `election_restore_gate`, network=none, sem bindings de porta; somente o arquivo de backup foi copiado. Banco contém 16 tabelas públicas, migrations V1–V14 success, 30 polls, 60 poll_results, 21 pollsters, 17 pollster_performance, 56 aggregate_snapshots, 24 historical_polls, 48 historical_poll_results, 29 election_result_snapshots e um pre_election_freezes já existente no backup. projection_snapshots=0, consistente com produção atual. Não foi criado freeze. Índices e função reject_pre_election_freeze_mutation presentes; zero constraints públicas NOT VALID e zero poll_results órfãos. A primeira consulta de contagem usou por engano o plural pollster_performances, falhou somente na consulta e foi corrigida para pollster_performance; o restore em si não falhou.
- Isso comprova restauração de schema e dados deste arquivo; não comprova atualidade integral nem disponibilidade da janela de recuperação Neon. O container é parado ao final e permanece local para inspeção; não usar suas credenciais trust fora do isolamento sem rede.

Comando de restore correspondente, **somente em container/banco descartável criado para o ensaio**:

```powershell
docker run --detach --name election-backup-restore-gate-20261025 --network none --env POSTGRES_HOST_AUTH_METHOD=trust postgres:18
docker cp backups/election-2026-post-freeze-20261025-010044.dump election-backup-restore-gate-20261025:/tmp/election-backup.dump
docker exec election-backup-restore-gate-20261025 createdb -U postgres election_restore_gate
docker exec election-backup-restore-gate-20261025 pg_restore --exit-on-error --no-owner --no-acl -U postgres -d election_restore_gate /tmp/election-backup.dump
```

O nome já está ocupado pelo ensaio concluído; para repetir, usar outro nome exclusivo e não sobrescrever banco existente. `--no-owner --no-acl` adapta ownership ao ambiente de teste; não testa restauração das ACLs/roles originais. Não usar `--clean` ou apontar para endpoint remoto.
- Neon: **PITR últimas seis horas confirmado pelo usuário no painel**, fuso America/Sao_Paulo, GMT-03:00. Janela móvel, não intervalo absoluto fixo. O seletor operacional exibiu **25/10/2026 02:30 GMT-03:00 como ponto selecionado**; esse valor não é evidência do limite superior nem confirmação de restauração desse instante. Não foi executado Restore.
- Snapshot manual disponível; **nenhum snapshot existe na captura informada**. A proteção explícita será um snapshot da branch production (br-solitary-poetry-b6tc25si) criado imediatamente antes da V15, adicional ao PITR e ao backup local com restore ensaiado. Preservar o dump e seu hash em local privado; um backup lógico renovado pode complementar a proteção, sem substituir a confirmação do snapshot.
- Interpretação técnica: a API Neon permite criar snapshot da branch sem fornecer timestamp/LSN alvo; portanto, obter um timestamp separado de limite superior do seletor não é pré-requisito documentado para criar essa proteção. A ausência desse valor deixa de ser bloqueio isolado **sob a condição de confirmar o snapshot pré-V15**. Isso não permite afirmar qual era o último ponto recuperável às 02:30. A criação pode ser assíncrona: aceitação da solicitação não basta. Referência oficial: https://api-docs.neon.tech/reference/createsnapshot.
- **Procedimento futuro obrigatório, não executado:** após autorização operacional e imediatamente antes do primeiro startup que aplicará V15, conferir projeto election-2026-production / branch production / id br-solitary-poetry-b6tc25si; no Backup & Restore criar snapshot manual do estado corrente, sem usar 02:30 como timestamp histórico. Aguardar conclusão e confirmar que aparece disponível no painel/listagem. Registrar ID/nome, branch, instante de captura e criação em UTC e GMT-03:00 quando disponíveis, status e expiração/retensão exibida. Não presumir retenção ilimitada ou igual à janela PITR. Se pendente/falhar, projeto/branch divergir ou não houver evidência de disponibilidade, **não iniciar V15**. Nenhum Restore, troca de branch, migration ou criação de snapshot foi executado nesta rodada.
- Query somente leitura para o operador confirmar schema:

```sql
SELECT installed_rank, version, description, success, installed_on
FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 5;
SELECT origin, count(*) FROM projection_snapshots GROUP BY origin;
```

A segunda query é somente pós-V15; antes dela, registrar count(*) e IDs/payloads legados por comparação controlada, sem exportar dados sensíveis para logs públicos.

| Ação | Resultado esperado | Abortar se | Rollback |
|---|---|---|---|
| Revalidar identidades, V14 e gatilhos registrados; preservar backup e preparar criação do snapshot imediatamente pré-V15 | Referências de rollback preservadas; snapshot planejado para branch correta, sem afirmar que já existe | Identidades divergirem ou não houver como criar/confirmar proteção pré-V15 | Manter produção atual |
| Reconfirmar compatibilidade temporária documentada e preparar smoke da web automática imediatamente após o PUSH; não há Railway neste projeto | Web nova funciona com API 0000003 enquanto a imagem nova é produzida | Candidato/contratos divergirem do ensaio ou web não mantiver fallback | Não avançar API; restaurar deployment web primário |
| Conferir diff e allowlist; excluir backups, logs, .env, arquivos locais | Somente readiness aprovada e documentação operacional | Segredo, mudança matemática ou migration antiga alterada | Não versionar; corrigir localmente |

Validação anterior registrada em `stabilization-2026-10-25.md`: backend 330, frontend 74, total 404, lint/build/diff-check aprovados. No gate anterior: **17 testes afetados novamente aprovados**, incluindo upgrade V14→V15. Nesta rodada externa não houve mudança de código ou execução de migration/suíte: houve SELECT remoto read-only e restore/checagens locais do backup. Diff-check da documentação também deve passar.

Verificação local final de compatibilidade do rollout — 25/10/2026:

| Frontend | API | Resultado e evidência |
|---|---|---|
| Release nova | Contrato do SHA 87b01855d28eedcef0de273b6b72b4db8935c651, revisão 0000003 | **A — BACKWARD_COMPATIBLE.** Executado Next dev em 127.0.0.1:3101 contra API antiga em 127.0.0.1:19081, compilada de git archive desse SHA e iniciada com dependências locais. /context retornou 404 real; home hidratou, exibiu aviso e preservou organização anterior, freeze já existente, quatro agregados, gráfico/histórico, pesquisas e resumo oficial. /apuracao exibiu oficial, projeção retida pelos gates, ritmo, mapa e tabelas. Sem tela branca ou erro não tratado observado; consulta de logs warn/error do navegador vazia após carregamento. |
| Frontend atual (SHA 87b01855…) | API nova | **Compatível por contratos preservados**, conferidos no diff e nas regressões já aprovadas: DTO oficial e rotas existentes mantidos; GET projection mantém ProjectionResponse; history mantém array/atributos e acrescenta publication. O consumidor antigo não exige novos campos nem usa o endpoint de contexto. Os testes liveManifestReferencesExactInputsAndDoesNotChangeProjectionMath e historyPreservesVersionsLabelsLegacyAndSeparatesReplay confirmam preservação do payload removendo apenas o campo aditivo; ProductionApiTests mantém endpoints públicos. Não foi simulado nesta rodada outro deploy completo da API nova nem aplicada migration, mesmo localmente. |

Ensaio restrito à máquina: backup restaurado em PostgreSQL 18, porta 55439 somente loopback; Flyway desativado no processo antigo, schedulers TSE/discovery desativados e base TSE apontando para 127.0.0.1:9. Nenhuma consulta a API/TSE/banco de produção. A API antiga foi executada por classes compiladas; empacotamento offline encontrou plugin Maven de jar incompleto no cache e foi dispensado em favor do classpath local, sem baixar dependências ou editar fonte. Frontend executado em dev, não como novo build de produção; build/lint de produção da release já aprovados na estabilização. Temporários fora do repositório, logs locais ignorados. Nenhum snapshot/freeze novo foi criado.

GETs locais: /actuator/health 200; /api/election/president/context **404**; /api/election/president, /states, /history, /projection, /projection/history, /pre-election-comparison, /api/aggregates, /api/aggregates/history, /api/polls/latest e /api/pre-election-freeze **200**. O histórico de projeções deste backup está vazio, como a produção observada; não foram fabricados snapshots para o ensaio. Sete testes frontend existentes de ElectionHome/compatibilidade de históricos novamente aprovados, zero falhas; não são sete testes novos.

O 404 de contexto é capturado pelo hook: sem fase anterior, homePriority(undefined) usa layout pré-eleitoral; a fase não é falsamente declarada NOT_STARTED. Com fase anterior recebida, o hook a preserva. Logs do backend mostraram consultas de contexto separadas por cerca de 5–6 segundos, sem retry imediato/overlap; nova navegação/remount inicia outra consulta normalmente. Ao entrar em /apuracao o polling de contexto da home foi encerrado. SetTimeout recursivo e cleanup/AbortController permanecem iguais. A retomada da API nova será percebida pelo polling seguinte, sem necessidade de redeploy web.

Risco da janela: aviso de contexto sem atualização e ausência temporária da priorização COUNTING/FINISHED na home; os dados oficiais continuam no resumo e em /apuracao. Não há promessa de que a UI nova esteja totalmente habilitada contra API antiga. Manter a janela curta e conferir web antes e depois da API. Novo frontend e API antiga compatíveis não garantem disponibilidade durante a parada deliberada da API para exclusividade.

Ordem recomendada após autorização futura: **COMMIT → PUSH master (GHCR e Vercel Production automáticos) → smoke imediato da web nova contra API 0000003 e aguardar imagem GHCR SHA/digest → desativar coletor antigo e confirmar zero réplicas → criar/confirmar snapshot manual pré-V15 → iniciar API nova/aplicar V15 → API SMOKE → WEB SMOKE final → monitoramento**. A etapa WEB DEPLOY abaixo registra o deployment automático já iniciado no PUSH; não exige segunda promoção nem configuração permanente. Se o smoke inicial da web falhar, restaurar o deployment primário e não avançar API.

## COMMIT

| Ação | Resultado esperado | Abortar se | Rollback |
|---|---|---|---|
| Após autorização explícita, executar git status --short; stage por allowlist dos arquivos aprovados; git diff --cached --check; git diff --cached --stat; revisar diff staged | Commit proposto: `feat: prepare election-day context and projection provenance`; SHA registrado | Arquivos inesperados, backups/, logs, segredo, fórmula ou migration V1–V14 no staged | Remover somente esses caminhos do index; não descartar trabalho |
| Executar git commit somente depois da revisão | Commit local corresponde ao candidato validado | Hooks/checks falharem ou diff mudar | Não publicar; corrigir e revalidar |

Não usar `git add .` nem `git add -A`. A migration V15 é nova; o DEFAULT permanece. Nenhuma alteração de dados operacionais faz parte deste commit.

## PUSH

| Ação | Resultado esperado | Abortar se | Rollback |
|---|---|---|---|
| Após autorização específica executar git push origin master; acompanhar Vercel Production e GHCR e fazer smoke inicial da web contra API 0000003 | SHA remoto correto; deployment web Ready e fallback funcional; imagem GHCR SHA/digest disponível | Build falhar, web quebrar contra API antiga ou imagem não identificável | Não avançar API; restaurar deployment web primário; não force-push |
| Registrar digest da tag SHA e manter imagem anterior disponível | Deploy usa SHA/digest imutável | Somente alias master disponível ou digest divergente | Permanecer na imagem antiga |

**Com a configuração atual, push em master cria automaticamente deployment Production no Vercel e pode atualizar os domínios.** O gatilho vem da integração Git, independentemente do workflow GHCR ou de Deploy Hooks. A ordem dos títulos deste runbook não adia essa publicação. A compatibilidade temporária A, verificada acima, permite esse adiantamento nesta release; não depender de staged/manual inexistente, não alterar Git/branch de produção nem configuração permanente. Nenhuma contenção foi configurada nesta rodada. A ordem de término GHCR/Vercel é indeterminada: observar ambos.

## API DEPLOY / MIGRATION

Não usar rolling update com coletores ativos sem barreira. maxReplicas=1 e Single **não são lock**: Azure mantém revisão antiga até a nova estar pronta. Referência: https://learn.microsoft.com/en-us/azure/container-apps/revisions.

Estratégia conservadora para esta release: **janela curta de indisponibilidade com desativação antes de iniciar**, aprovada antes de executar. A configuração atual Single espera a nova revisão ficar pronta antes de encerrar a antiga: um update normal pode iniciar dois schedulers antes da troca de tráfego. Não basta tráfego zero. Não há distributed lock no projeto; ambos TSE e publicação de projeções usam o scheduler e a discovery tem scheduler próprio.

Sequência operacional proposta, **nenhuma mutação abaixo executada nesta rodada**:

1. Revalidar revision list --all e replica list: 0000003 é a única ativa, uma réplica pronta; cinco antigas inativas/zero. Confirmar ausência de outra instalação Railway/Azure conectada ao mesmo banco; impedir sync/import/replay administrativo durante a janela. Capturar horários e último source-status/LIVE.
2. Após autorização de manutenção, mudar temporariamente para Multiple. Essa operação não cria revisão nem inicia um segundo coletor, segundo a documentação Azure. Serve para controlar explicitamente a desativação. Não ativar qualquer revisão antiga.
3. Desativar **0000003**, aguardar shutdown e observar **zero réplicas em todas as revisões**, não apenas active=false ou peso zero. SIGTERM é seguido por término forçado se necessário; aguardar o estado observado, sem depender de um sleep fixo. Se não for possível confirmar zero, abortar antes de criar revisão.
   **Antes do passo 4:** com coletores antigos já encerrados e sem operações administrativas concorrentes, criar o snapshot manual pré-V15 e confirmar disponibilidade conforme o procedimento de proteção acima. Registrar schema ainda V14 e identidade do snapshot. Sem essa evidência, interromper: não iniciar a imagem nova, pois seu startup pode aplicar V15 antes da readiness.
4. Só então executar update para a nova imagem por digest, preservando env/secretrefs, probes, baseline, parâmetros e min/max=1. Registrar o nome novo retornado, sem presumir sufixo 0000004. Não executar job de migration paralelo nem inicializar outra aplicação contra o mesmo banco.
5. Observar startup/Flyway, health e uma réplica nova pronta; todas as antigas devem continuar inativas e sem réplicas. O coletor novo pode iniciar antes de passar readiness; a garantia é que o antigo já terminou. Se falhar, desativar a nova e esperar zero antes de qualquer rollback/retry.
6. Direcionar 100% à revisão nova identificada somente após saúde; confirmar por revision list --all, ingress traffic e replica list que nenhuma outra está executando. Repetir API SMOKE e verificar coleta/publicação/logs. Só concluir após a exclusividade ficar comprovada; se desejar retornar a Single, fazê-lo apenas com a nova como única ativa e verificar novamente.
7. Rollback: enquanto em Multiple, desativar a nova e aguardar zero em todas; ativar **0000003**, restaurar 100% de tráfego para ela e verificar uma réplica pronta/coleta. Manter V15. Se já voltou a Single, entrar em Multiple antes dessa sequência. Não ativar 0000002 inadvertidamente.

Comandos documentais para o operador, a executar somente numa rodada explicitamente autorizada (o CLI não está no PATH desta máquina):

```powershell
$releaseAz = 'C:/Program Files/Microsoft SDKs/Azure/CLI2/wbin/az.cmd'
& $releaseAz account show --query id -o tsv
& $releaseAz containerapp revision list --all -n election-2026-api -g election-2026-rg -o table
& $releaseAz containerapp replica list -n election-2026-api -g election-2026-rg --revision election-2026-api--0000003 -o table
# Somente depois dos gates e da autorização de manutenção:
& $releaseAz containerapp revision set-mode -n election-2026-api -g election-2026-rg --mode multiple
& $releaseAz containerapp revision deactivate -n election-2026-api -g election-2026-rg --revision election-2026-api--0000003
# Repetir listagens de todas as revisões/réplicas até confirmar ZERO; não avançar automaticamente.
# Só depois: update para imagem/digest novo validado, registrar revisão e executar os smokes.
```

Confirmar a assinatura `966a84fd-8f8c-421e-b744-fe7c58807758` antes de qualquer mutação futura. A documentação de gerenciamento de revisões descreve set-mode, deactivate/activate e traffic: https://learn.microsoft.com/en-us/azure/container-apps/revisions-manage. Shutdown: https://learn.microsoft.com/en-us/azure/container-apps/application-lifecycle-management. Se houver automação que reative revisões ou mantenha outra instalação, a barreira não está garantida: NO-GO até contenção observável. Este plano aceita indisponibilidade temporária, em vez de prometer zero downtime sem lock.

| Ação | Resultado esperado | Abortar se | Rollback |
|---|---|---|---|
| Registrar início da janela, parar atual, verificar zero réplicas e fim dos processos; bloquear sync/import/replay manual concorrente | Nenhum coletor/publicador executando | Alguma revisão/serviço continuar coletando; contagem não observável | Reiniciar somente revisão antiga conhecida; cancelar rollout |
| Criar snapshot manual da branch production imediatamente antes do startup novo; aguardar e confirmar disponibilidade, ID e branch | Ponto explícito pré-V15 registrado, adicional ao PITR 6h e dump ensaiado | Snapshot ausente/pendente/falho ou branch incorreta | Não iniciar V15; reativar somente 0000003 se necessário, após conferir zero coletores |
| Com todas as revisões sem réplicas, configurar imagem nova por digest e iniciar uma revisão/réplica | Flyway V15 aplica uma vez; health pronto; uma única coletora | Zero não confirmado, startup/Flyway/constraints falharem, lock persistente ou duas coletoras | Desativar nova; confirmar zero; ativar 0000003 |
| Conferir flyway_schema_history e metadados de colunas; contar legados antes/depois | V15 success; origin NOT NULL DEFAULT LEGACY_UNKNOWN; publicados antigos sem horário/manifesto inventados | Perda de registros, falso LIVE, diferença inexplicada | Rollback da aplicação; manter banco e investigar |

V15 adiciona colunas/constraints/índice, pode aguardar locks. Não matar sessões, limpar Flyway, executar repair cegamente ou remover constraints. Publicação nova só vira LIVE pelo writer explícito com manifesto e publishedAt. Downgrade do binário mantém V15; não executar down migration.

## API SMOKE

Executar em PowerShell somente após saúde e revisão corretas:

```powershell
$releaseApi = 'https://election-2026-api.mangotree-d5e22651.brazilsouth.azurecontainerapps.io'
$releaseRoutes = @(
  '/actuator/health', '/api/election/source-status',
  '/api/election/president/context', '/api/election/president',
  '/api/election/president/projection', '/api/election/president/projection/history',
  '/api/aggregates', '/api/aggregates/audit'
)
foreach ($releaseRoute in $releaseRoutes) {
  Invoke-RestMethod -Uri ($releaseApi + $releaseRoute) -ErrorAction Stop | ConvertTo-Json -Depth 30
}
```

| Ação | Resultado esperado | Abortar se | Rollback |
|---|---|---|---|
| GETs acima e GET /api/election/president/projection/history/{snapshotId}/inputs para ID real retornado | Contratos preservados; contexto coerente; fontes/horários distintos; manifesto LIVE resolve IDs oficiais | 5xx persistente, fase incompatível com banco, matemática diferente, manifesto inconsistente | Parar nova; confirmar zero; imagem antiga |
| Verificar histórico e origem; comparar cálculos com referência pré-deploy | LEGACY_UNKNOWN preservado; REPLAY ausente no padrão; LIVE somente se novo vetor elegível publicado | Reclassificação falsa, REPLAY no padrão, duplicação de coletores | Mesmo rollback; preservar evidências |
| Monitorar source-status sem forçar sync | TSE recupera coleta; 304 não exige novo snapshot | Regressão de coleta/persistência ou stale crescente sem diagnóstico | Mesmo rollback |

Histórico vazio/ausência de LIVE antes dos gates não é falha. Não fabricar dados, provocar indisponibilidade TSE, apagar registros nem alterar thresholds em produção para testar cenários. Transições/stale/indisponibilidade e upgrade foram exercitados em ambiente descartável. Se ID não existir, inputs deve dar 404, não inventar manifesto. GET projection pode calcular resposta efêmera: idade publicada vem de publishedAt do LIVE, não do calculatedAt de cada GET.

## WEB DEPLOY

| Ação | Resultado esperado | Abortar se | Rollback |
|---|---|---|---|
| Registrar e conferir o deployment automático disparado no PUSH, incluindo SHA, domínios e API origin; executar smoke inicial ainda contra API antiga | Domínio Production serve release aprovada e fallback é seguro até a troca da API | SHA/origin errado, frontend quebrar ou build falhar | Restaurar deployment web primário; não avançar API |
| Após API SMOKE aprovado, conferir o mesmo deployment novamente e executar WEB SMOKE | Frontend passa a receber contexto da API nova; nenhuma segunda promoção necessária | Contexto continuar 404, falha dos endpoints ou contratos inesperados | Rollback API/web conforme origem, preservando V15 |

Não editar variáveis secretas nem mudar CORS/segurança. O adiantamento automático da web é uma janela de compatibilidade verificada para esta release, sem mudança de código; não presumir essa compatibilidade em releases futuras.

## WEB SMOKE

| Ação | Resultado esperado | Abortar se | Rollback |
|---|---|---|---|
| Abrir home, /apuracao, comparação e /metodologia em desktop e mobile; conferir requests/browser console | NOT_STARTED preserva layout; COUNTING prioriza oficial; projeção separada e com horário; pesquisas acessíveis | Tela quebrada, erro API, fonte misturada ou HIGH_COVERAGE apresentado como chance de vitória | Restaurar deployment web anterior; manter API se saudável |
| Conferir dados após refresh e stale natural, quando ocorrer | Últimos dados preservados, aviso de atualidade coerente | Desaparecimento indevido ou retorno falso para NOT_STARTED | Rollback web/API conforme origem do defeito |

Se o controlador continuar indisponível, operador faz smoke manual e registra evidência. Não declarar conferência visual concluída com base apenas em HTTP 200.

## ELECTION-DAY MONITORING

Checklist curto por ciclo operacional:

- [ ] API UP; revisão/digest esperado; uma única réplica coletora no conjunto de serviços.
- [ ] source-status: última consulta/sucesso TSE, geração, HTTP/backoff/falhas e stale.
- [ ] GET /api/election/president/context: fase coerente; indisponibilidade não regride COUNTING.
- [ ] GET /api/election/president: votos oficiais e horário da fonte, separados do modelo.
- [ ] Última projeção LIVE: snapshotId, versão, geração oficial, publishedAt, idade e manifesto.
- [ ] Histórico persiste novos vetores publicáveis; 304/mesmo vetor/gate insuficiente não exigem nova linha.
- [ ] Logs sem falha recorrente de parsing, Flyway, banco ou persistência; nenhum coletor concorrente.
- [ ] Home correta para fase, projeção identificada, pesquisas acessíveis, nenhuma probabilidade improvisada.

| Ação | Resultado esperado | Abortar/intervir se | Rollback |
|---|---|---|---|
| Acompanhar checklist e registrar UTC/Brasília | Coleta e publicação rastreáveis, fontes distintas | Falha persistente, corrupção/contrato ou concorrência | Aplicar ROLLBACK apropriado; registrar incidente |

Durante COUNTING: congelar fórmula, parâmetros, baseline, thresholds, metodologia e semântica. Intervenção somente INCIDENT_FIX ou PRESENTATION_FIX, documentada com sintoma, horário, diff mínimo, testes, responsável e rollback. Nenhuma feature, freeze, BU, probabilidade ou chamada automática nesta release.

## ROLLBACK

| Ação | Resultado esperado | Abortar se | Alternativa |
|---|---|---|---|
| Defeito somente web: restaurar deployment Production previamente registrado | Web anterior saudável, API permanece validada | Deployment antigo não identificado | Conter promoção; obter identidade pelo painel, não escolher build por suposição |
| Defeito API: parar nova revisão; esperar zero processos; configurar/iniciar somente imagem/digest antigo confirmado; verificar health e coleta | Uma coletora antiga, contratos anteriores funcionando sobre V15 | Imagem desconhecida, duas revisões coletoras, startup/Flyway falhando | Manter contenção e escalar incidente; não apagar schema |
| Manter V15 e seus dados; repetir oficial/source-status/aggregates/histórico | Writer antigo usa DEFAULT LEGACY_UNKNOWN; nenhum falso LIVE | Evidência de corrupção | Incidente de dados separado, sem restore automático |
| Reconciliar web e holds de automação | Frontend compatível; nenhum redeploy automático recoloca versão falha | Automação ainda promover SHA falho | Manter hold; corrigir só com nova autorização |

No binário anterior, contexto e inputs novos podem voltar a 404; restaurar web anterior conforme necessário. Não remover V15, não reescrever migration, não restaurar backup em produção como rollback de aplicação. Confirmar comportamento da imagem antiga exata; testes locais de compatibilidade não substituem identificação/smoke do artefato real.

Referência Neon para o mecanismo de histórico, sem inferir retenção deste projeto: https://github.com/neondatabase/website/blob/main/content/docs/postgres/backup-restore/history-window.md.
