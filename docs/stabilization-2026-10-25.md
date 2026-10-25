# Estabilização e preparação de produção — 25/10/2026

## Decisão e limites

Candidato aprovado nos testes locais, sem alteração funcional nesta rodada. Nenhum INCIDENT_FIX ou PRESENTATION_FIX foi necessário. Não houve commit, push, merge, deploy, alteração de configuração em produção, importação ou criação de freeze. Os smokes usaram exclusivamente loopback e PostgreSQL 17 descartável. Não foram consultados o banco, as configurações nem os logs de produção.

Não interpretar esta validação local como deploy realizado ou smoke de produção. Promoção depende de autorização explícita e do checklist abaixo.

## Diagnóstico do controlador e da inicialização

Foram separadas três questões:

1. **Inicialização local anterior:** o wrapper npm/PowerShell interpretou `--port 3001` como diretório (`next dev 3001`), deixando o servidor indisponível no primeiro acesso. A execução direta do launcher Node com `next dev --port 3002` iniciou normalmente. Nenhum script do projeto foi alterado.
2. **Automação Chrome/CDP:** a página HTML estática de controle funcionou no Chrome e no navegador integrado. A navegação Chrome para a home apresentou timeout de `Page.navigate`, enquanto o servidor respondeu HTTP 200 e o navegador integrado carregou a mesma home completamente. A rodada anterior também registrou perda de associação do debugger. Evidência aponta para limitação transitória do controlador/ambiente, não para falha reproduzida do frontend/API.
3. **Aplicação:** no navegador integrado, foram conferidos NOT_STARTED, COUNTING, fonte stale, falha HTTP do contexto preservando COUNTING e FINISHED. Oficial, projeção e conteúdo pré-eleitoral conservaram a separação; sem freeze, a comparação final informou indisponibilidade sem inventar um. Não houve erro JavaScript não tratado nos cenários normais; HTTP 503 foi injetado deliberadamente no cenário de falha. Há capturas locais com nomes/valores explicitamente de teste.

O harness HTTP temporário precisou desativar DevTools por propriedade da JVM para execução Java em modo fonte. Isso é configuração do harness, fora do repositório, não defeito identificado no artefato da aplicação. Nenhuma correção de produto foi aplicada.

## Validação repetida

| Validação | Resultado |
|---|---|
| Backend: `mvnw.cmd clean test` | 330 testes; zero falhas/erros/skips; BUILD SUCCESS |
| Frontend: `npm test` | 74 testes; zero falhas/skips |
| `npm run lint` | aprovado |
| `npm run build` | aprovado, incluindo TypeScript |
| `git diff --check` | aprovado |
| Total da suíte | **404**, sem divergência dos números aprovados |
| Smoke adicional HTTP/Spring/PostgreSQL | **74 verificações aprovadas**, fora da contagem da suíte |

Os smokes adicionais foram executados por um runner temporário fora do repositório. Usaram a aplicação Spring real em porta aleatória, PostgreSQL descartável, clock controlado e um servidor HTTP local com fixtures EA20. Nenhum código de teste temporário ou log foi incluído no candidato de commit. Os warnings observados de testes (agente Mockito e respostas negativas deliberadas das fixtures) não são incidentes de produção.

## Smokes e divergência de rota

**A rota oficial existente é GET `/api/election/president`.** A rota solicitada `/api/election/president/official` retornou 404, coerente com o controller anterior e atual. Trata-se de divergência do checklist, não de uma rota removida. Não foi criado alias durante estabilização.

Foram exercitados:

- Saúde, contexto UNKNOWN, resultado oficial UNAVAILABLE, projeção retida, histórico vazio e inputs de ID inexistente retornando 404.
- Endpoints de candidatos, institutos, pesquisas/latest, agregados/audit/history, arquivo de pesquisas, estados, histórico oficial, ritmo, pendências e status da fonte.
- Freeze ausente (204) e comparação NO_FREEZE. Comparações com freeze e erros parcial/final permanecem cobertas pela suíte backend.
- Persistência de snapshot nacional zerado: NOT_STARTED.
- Coleta manual de fixtures locais por UF/nacional: COUNTING, resultado FRESH e projeção publicável após os gates existentes.
- Publicação LIVE, horário de publicação, consulta de manifesto com 28 IDs de snapshots oficiais e parâmetros/baselines do cálculo.
- Inserção no formato legado de sete colunas, usando defaults da V15: LEGACY_UNKNOWN, sem horário ou manifesto inventado.
- Histórico entre versões, consulta explícita de REPLAY e ausência de REPLAY no histórico padrão.
- HTTP 503/stale: COUNTING preservado, último oficial mantido, projeção não publicável e nenhum LIVE novo indevido.
- Reinício da aplicação sobre o mesmo banco descartável: fase/último oficial/histórico/manifesto recuperados, sem chamada remota no startup.
- Finalização nacional oficial: FINISHED e convergência final preservada, com novo LIVE quando a geração mudou.
- Nenhuma pesquisa ou freeze criado pelo runner.

## Revisão do diff e V15

Antes deste relatório: 18 arquivos rastreados modificados, 11 novos arquivos da readiness, total de 29. `git diff --stat` dos rastreados: 188 inserções e 33 exclusões; esse comando não inclui os novos arquivos ainda não rastreados. Este relatório é o único arquivo acrescentado nesta rodada, elevando o escopo proposto a 30 arquivos.

Conferências:

- Nenhum diff nos calculadores/fatores de CALIBRATED ou nos demais agregadores.
- Nenhum diff em ProjectionEngine, ProjectionMath, ProjectionProperties, BaselineStore ou nos parâmetros de application.yaml.
- Nenhum diff em OfficialResultsService, FreezeService ou data/.
- Migrations V1–V14 não alteradas.
- V15 somente acrescenta colunas, constraints de procedência e índice; nenhum DROP, DELETE, TRUNCATE ou alteração de payload eleitoral.
- Contratos existentes mantêm campos originais, URLs e formatos principais. Histórico recebe metadados e filtros aditivos; seleção de versões e exclusão padrão de REPLAY são mudanças intencionais da readiness. A suíte compara a resposta calculada com o motor original e o JSON do snapshot sem o novo metadado.
- Revisão manual e varredura por padrões de chaves/tokens/credenciais e nomes de artefatos não identificaram segredo ou arquivo local no escopo aprovado. Exemplos de variáveis e chaves sintéticas de teste não são credenciais de produção.
- `backups/` contém dumps locais **não rastreados**, não auditados como conteúdo e excluídos do commit proposto. Não usar `git add .` ou `git add -A`.

Upgrade V14→V15 com dados existentes passou na suíte: payload legado preservado; origin NOT NULL com default LEGACY_UNKNOWN; published_at/input_manifest anuláveis e nulos nos registros antigos. Não há reclassificação retroativa como LIVE. Constraints impedem horário de publicação em REPLAY.

Rollback de compatibilidade foi exercitado no banco descartável: escrita no formato antigo é aceita; validação Flyway com apenas os recursos V1–V14 passa mantendo V15 aplicada. Os mapeamentos Hibernate relevantes não mudaram. Isso não substitui um smoke da imagem antiga exata após eventual rollback em produção, cuja tag ainda não foi consultada.

## Branch, status e commit proposto

- Branch atual: **master**.
- HEAD observado: **87b0185 — fix: improve national poll scope detection**.
- Não há alterações staged nem commit novo criado.
- Commit proposto: `feat: prepare election-day readiness and projection provenance`.
- Conteúdo: os 29 arquivos já aprovados em election-day-readiness.md mais este relatório, selecionados por allowlist explícita.
- Imagem: usar o SHA real do commit após revisão/autorização; não inventar SHA nem depender apenas da tag master.

O status completo ao concluir a revisão está registrado ao final deste documento. O diretório backups/ deve permanecer fora de staging. Não foi alterado .gitignore nem feita limpeza de arquivos locais.

## Sequência recomendada de deploy — ainda não executada

1. **Gate de autorização:** revisar o relatório/diff; escolher os 30 caminhos aprovados explicitamente; conferir `git diff --cached` sem dumps, envs, logs ou credenciais. Criar commit/push somente após solicitação do usuário.
2. **Registrar rollback:** capturar imagem/revisão saudável da API, deployment saudável da Vercel, configurações/secretrefs atuais e evidência do backup Neon. A tag/revisão real em produção ainda não foi consultada. Não presumir que HEAD é a imagem em produção.
3. **Controlar promoção:** confirmar se push em master dispara produção automática na Vercel. Coordenar a promoção para API primeiro, web depois. Não mudar essa configuração sem autorização. O workflow GHCR existente publica imagem por SHA após push autorizado.
4. **Preparar API:** produzir a imagem imutável pelo workflow existente; confirmar target port 8081, secrets, CORS e uma réplica, preservando configurações estatísticas e baseline. Não copiar defaults do repositório sobre valores ativos de produção.
5. **Promover API:** startup aplica V15 antes da validação Hibernate. ALTER TABLE/índice precisam de lock; evitar transações longas e rollout na janela crítica. Não encerrar sessões ou remover dados para contornar lock. Se a migração falhar, interromper promoção e investigar.
6. **Evitar coletores duplicados:** confirmar estratégia de revisão/rollout. Max replicas=1 não impede, por si só, sobreposição transitória entre versões; não manter duas revisões coletoras ativas. Qualquer pausa operacional deve ser coordenada, sem alterar parâmetros estatísticos.
7. **Smoke API:** executar a lista abaixo antes de promover a web. Os endpoints antigos devem continuar respondendo para a web anterior. Não invocar imports, baseline sync, freeze ou gravação de snapshots como smoke.
8. **Promover web:** mesma revisão aprovada, com NEXT_PUBLIC_API_URL correto; manter o endpoint e CORS já usados. Conferir home, apuração e metodologia. A web nova degrada para o layout anterior caso o contexto ainda esteja indisponível, mas isso não substitui a ordem API→web.
9. **Observar:** confirmar recuperação dos dados, atualização TSE e gravação de novos LIVE quando houver nova geração publicável. Não preencher ausência de LIVE com reprocessamento ou mudança de rótulo legado.

Não houve Docker build/push novo nem execução da imagem na Azure nesta rodada; a validação local usou classes compiladas e HTTP real. Publicação da imagem e smoke do ambiente real pertencem à próxima etapa autorizada.

## Checklist pós-deploy somente leitura

```text
GET /actuator/health
GET /api/election/source-status
GET /api/election/president/context
GET /api/election/president
GET /api/election/president/states
GET /api/election/president/history?limit=2
GET /api/election/president/projection
GET /api/election/president/projection/history?limit=5
GET /api/election/president/projection/history?origin=LIVE&limit=1
GET /api/election/president/projection/history/{snapshotId}/inputs
GET /api/pre-election-freeze
GET /api/election/president/pre-election-comparison
GET /api/aggregates
GET /api/aggregates/audit
GET /api/polls/latest
```

Escolher snapshotId realmente retornado pelo histórico. Para legado, manifesto/publicação nulos são esperados. Sem dados, UNKNOWN/UNAVAILABLE e histórico vazio são respostas válidas. Antes dos gates, ausência de projeção LIVE é esperada. Contexto não deve regredir por indisponibilidade temporária. Verificar separação de oficial/projeção, horários e nenhum tratamento de HIGH_COVERAGE como probabilidade.

## Rollback da aplicação

- Se o problema for somente web, restaurar o deployment Vercel saudável e manter API/V15.
- Se for API, restaurar imediatamente a imagem/revisão saudável registrada; restaurar a web anterior quando necessário. A web nova preserva o último contexto em falha e, sem contexto prévio, usa layout anterior; acompanhar essa degradação durante o rollback.
- **Manter V15 aplicada.** Não apagar colunas, manifesto, snapshots ou registros; não executar down migration, baseline sync ou novo freeze. Escritores antigos usam defaults legados.
- Preservar secrets, CORS e parâmetros atuais; garantir uma única revisão coletora ativa.
- Repetir saúde, resultado oficial, status TSE e histórico. No binário anterior, contexto/inputs novos podem retornar 404; esse é um efeito esperado de rollback, não motivo para apagar a migration.
- Não restaurar backup do banco como rollback rotineiro da aplicação. Suspeita de corrupção de dados exige um incidente separado, evidência e autorização específica.

## Checklist curto para a apuração de hoje

- [ ] API UP e revisão/imagem esperada em execução.
- [ ] Última consulta TSE, geração, status HTTP e falhas consecutivas verificados em source-status.
- [ ] ElectionContext coerente com a evidência; stale/indisponibilidade não retornam a NOT_STARTED.
- [ ] Resultado oficial com votos/percentuais e horários próprios; nenhuma estimativa misturada.
- [ ] Última projeção LIVE identificada por snapshotId, versão, geração e manifesto.
- [ ] Idade do LIVE calculada por publishedAt, não pelo calculatedAt de um GET efêmero; comparar também a geração oficial usada.
- [ ] Histórico cresce quando entra novo vetor oficial **publicável**; 304, gates insuficientes ou mesma geração não exigem nova linha.
- [ ] Logs sem falha recorrente de parsing, persistência, Flyway ou banco; registrar contexto de 404/429/5xx e backoff sem repetir sync cegamente.
- [ ] Home na fase correta; projeção separada, pesquisas disponíveis e comparação coerente com freeze/totalização final.
- [ ] Integração TSE/scheduler e uma réplica coletora confirmados; falha da fonte mantém último oficial e retém projeção atual conforme os gates existentes.

## Congelamento funcional durante COUNTING

Não alterar fórmula, parâmetros, baseline, thresholds, metodologia ou semântica dos campos. Não avançar funcionalidades.

Toda intervenção precisa ser registrada como **INCIDENT_FIX** (restaurar funcionamento, disponibilidade ou persistência sem mudar o modelo) ou **PRESENTATION_FIX** (corrigir exibição/rotulagem sem mudar cálculo, significado ou fonte dos valores).

Registro mínimo: horário Brasília/UTC, fase, responsável/aprovação, sintoma, evidência, classificação, diff mínimo, testes, impacto nos contratos/dados, revisão da ausência de mudança estatística e rollback. Se a correção exigir alterar itens congelados, parar e solicitar decisão explícita; não disfarçar mudança metodológica como incidente.

## Riscos conhecidos

- Dependência do TSE, limites/backoff e cadência de atualização das UFs continuam iguais.
- V1 ainda não conhece a distribuição das seções restantes dentro da UF; atualidade nacional não garante uniformidade temporal estadual.
- Contexto tem cache de até cinco segundos e polling da home, podendo refletir transições com pequeno atraso.
- Legados não comprovam publicação ao vivo. Mesma versão/vetor já deduplicado permanece legado até nova geração elegível, sem reclassificação artificial para produzir LIVE.
- Limite do histórico não representa exportação completa. Não inferir primeira projeção histórica a partir de uma janela truncada.
- A promoção automática Vercel, tags reais de rollback, locks e sobreposição de coletores precisam ser conferidos quando a etapa externa for autorizada.
- Este relatório não valida disponibilidade atual de Azure, Neon, Vercel ou TSE real; nenhuma consulta de produção foi feita nesta rodada.


## Git status ao concluir a revisão

```text
 M README.md
 M api/src/main/java/br/com/election/projection/ElectionAnalyticsController.java
 M api/src/main/java/br/com/election/projection/ProjectionService.java
 M api/src/main/java/br/com/election/projection/ProjectionSnapshotStore.java
 M api/src/test/java/br/com/election/PollApiTests.java
 M api/src/test/java/br/com/election/ProductionApiTests.java
 M api/src/test/java/br/com/election/ProjectionIntegrationTests.java
 M docs/deployment.md
 M docs/election-projection-model.md
 M docs/polling-model.md
 M docs/production-runbook.md
 M web/app/metodologia/page.tsx
 M web/app/page.tsx
 M web/components/election-analytics.tsx
 M web/components/official-dashboard.tsx
 M web/components/pre-election-freeze.tsx
 M web/lib/election-analytics.ts
 M web/lib/pre-election.ts
?? api/src/main/java/br/com/election/projection/ProjectionInputManifest.java
?? api/src/main/java/br/com/election/tse/ElectionContextController.java
?? api/src/main/java/br/com/election/tse/ElectionContextService.java
?? api/src/main/resources/db/migration/V15__projection_publication_provenance.sql
?? api/src/test/java/br/com/election/tse/ElectionContextTests.java
?? backups/
?? docs/election-day-readiness.md
?? docs/stabilization-2026-10-25.md
?? web/components/election-home.tsx
?? web/lib/election-context.ts
?? web/lib/projection-publication.ts
?? web/tests/election-day.test.mjs
?? web/tests/election-home-render.test.mjs
```

O fingerprint dos 29 arquivos aprovados permaneceu idêntico durante esta rodada; a única adição do produto de trabalho é este relatório operacional. Nenhum desses arquivos foi staged.
