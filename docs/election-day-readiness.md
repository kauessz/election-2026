# Preparação segura da apuração — 25/10/2026

Esta entrega mantém CALIBRATED e STATE_SHRINKAGE_V1, seus parâmetros, gates e fórmulas. Não há probabilidade, intervalo improvisado, chamada de vencedor, BU colaborativo ou intenção de voto.

## Contexto oficial

GET `/api/election/president/context` é público e somente leitura. A evidência vem de `election_result_snapshots`, eleição configurada, segundo turno, escopos nacionais/estaduais, com geração e captura não futuras. Contagem observada em UF ou exterior já estabelece COUNTING; apenas `FINAL` nacional estabelece FINISHED. Um resultado estadual final ou 100% sem sinal nacional final não encerra a eleição.

- UNKNOWN: ainda não há evidência nacional nem contagem persistida.
- NOT_STARTED: há snapshot nacional persistido, sem contagem observada.
- COUNTING: houve seções/votos observados em algum snapshot elegível.
- FINISHED: há snapshot nacional com totalização final oficial.

A fase considera a evidência histórica, não apenas o último arquivo; arquivo zerado posterior, stale ou indisponibilidade não desfazem contagem/finalização já persistidas. Reinício reconstrói a fase do banco. Retorna sourceStatus, dataAvailable (há resultado em cache), sourceFresh, stale, horários de geração/consulta/persistência, avaliação e primeira captura da contagem/finalização. Esses primeiros horários são observação pelo sistema, não início real da totalização. Última captura pode ser de UF/ZZ e não significa atualidade de todas as UFs.

Há cache compartilhado de evidência por até cinco segundos; a home consulta a cada cinco segundos. Atraso combinado de transição pode chegar a aproximadamente dez segundos. Não existe novo scheduler, chamada ao TSE ou escrita causada pelo contexto. A disponibilidade da fonte permanece independente da fase.

## Home e horários

Antes da contagem, conserva a ordem anterior. Em COUNTING, resultado oficial vem primeiro e projeção tem card separado. Freeze, agregados live e pesquisas ficam em área recolhível e continuam com seus controles/atualizações existentes. Em FINISHED, resultado oficial e comparação com o freeze vêm primeiro. Falhas de HTTP preservam o último contexto recebido. Fase desconhecida é sinalizada, sem inventar NOT_STARTED.

Resultado oficial mostra geração e última consulta; modelo mostra horário do cálculo e geração usada; freeze mostra congelamento; agregados e pesquisas conservam seus horários próprios. HIGH_COVERAGE continua cobertura descritiva. Não há substituição do resultado oficial pelo modelo. Não se altera a matemática para reduzir a importância das pesquisas: apenas sua posição visual muda.

## Rastreabilidade V15

V15 é aditiva e conserva payloads, chaves e unicidade existentes. Acrescenta origin, published_at e input_manifest a projection_snapshots:

- LIVE: novos snapshots publicáveis gravados pelo serviço/scheduler, com manifesto e horário de disponibilização no histórico público.
- LEGACY_UNKNOWN: registros anteriores/legados, sem inventar publicação ou manifesto histórico.
- REPLAY: reservado para reprocessamento explicitamente identificado; não pode ter published_at.

Não há endpoint que grave replay nesta rodada. ProjectionReplay permanece sem escrita. GET de projeção continua calculando sem persistência, com o mesmo contrato e as mesmas fórmulas. Uma resposta transitória de GET não é automaticamente uma publicação histórica. publishedAt não comprova visualização por visitantes.

Manifesto guarda IDs dos snapshots oficiais selecionados, escopos, geração/captura, configuração e cópia dos valores/fontes/horários dos baselines fornecidos ao cálculo. Não chama o TSE e não transforma o baseline atual em um arquivo completo de revisões. Não há FK nova que apague insumos/auditoria por cascade. A deduplicação original por versão/geração oficial continua intacta; mudança somente de baseline/parâmetros não força nova publicação nesta entrega.

GET `/api/election/president/projection/history` mantém lista e campos originais e acrescenta publication {snapshotId, origin, publishedAt, inputManifestAvailable}. Inclui versões diferentes, com modelVersion opcional; origin opcional aceita LIVE, LEGACY_UNKNOWN ou REPLAY. Consulta padrão exclui REPLAY. Datas e limites existentes continuam válidos. O gráfico usa uma versão por vez, priorizando V1 por padrão, e avisa quando há registros legados.

GET `/api/election/president/projection/history/{snapshotId}/inputs` fornece o manifesto individual e a identificação do modelo/geração. Manifestos são consultados sob demanda para não repetir todos os baselines em cada refresh do gráfico. Legados retornam manifesto nulo; ID inexistente retorna 404.

## Operação e smoke sem escrita

Aplicar somente pelo fluxo normal de inicialização Flyway após revisão/backup. Não editar V10–V14. ALTER TABLE precisa de lock: evitar transação longa no banco e rollout na janela crítica. Binário anterior continua compatível com as colunas adicionais; seus snapshots ficam LEGACY_UNKNOWN. Não desfazer a migration apagando rastreabilidade.

Conferir saúde, contexto, resultado oficial, projeção atual e histórico. Antes da contagem, ausência de projeção publicável é esperada. Com dados parciais, verificar separação de blocos e horários. Após finalização nacional, comparação usa freeze existente; não criar freeze retroativo se ausente.

```text
GET /actuator/health
GET /api/election/president/context
GET /api/election/president
GET /api/election/president/projection
GET /api/election/president/projection/history?limit=5
GET /api/election/president/projection/history?origin=LIVE&limit=5
GET /api/election/president/pre-election-comparison
```

## Limitações e trabalho posterior

Mantêm-se coleta em uma réplica, dependência da disponibilidade do TSE, cadência de UFs e limitações de viés dentro da UF. Atualidade nacional não garante uniformidade temporal de todos os insumos estaduais. O manifesto melhora auditoria, não precisão. Há limites de histórico; exportações completas/paginação e download de metodologia/relatório ficam para outra entrega.

Após a eleição: avaliar ProjectionInputAssembler, incerteza validada, decisão editorial/estatística, BaselineRevision, ModelValidationRun, ProjectionDecisionEvent, SiteFeedback e ElectionReport. Não há interfaces vazias ou entidades antecipadas nesta entrega. Relatório futuro deve separar LIVE, REPLAY e legado desconhecido, sem atribuir retrospectivamente uma primeira previsão ou resultado praticamente definido. BU permanece em backlog; intenção de voto fora da versão.

## Arquivos desta entrega

Validação final: `mvnw.cmd clean test` com **330 testes aprovados**, sem falhas/erros/skips; frontend com **74 testes aprovados**, ESLint e build Next.js/TypeScript aprovados; `git diff --check` aprovado. Total de **404 testes distintos**, sendo 18 novos (11 backend, 7 frontend). Testes de renderização cobrem a ordem dos blocos antes/durante/depois da contagem e manutenção da fase em falha HTTP. A migração V14→V15 foi exercitada em schema descartável, com payload legado preservado e sem backfill de publicação. Há teste de igualdade dos cálculos/respostas com o motor original e de versões/legado/replay no histórico.

A checagem local de navegador confirmou o layout inicial de COUNTING com fixture em memória; o controlador perdeu conexão e impediu a verificação visual completa/captura. Os demais estados foram validados pelos testes de renderização e backend. Não houve smoke de produção, deploy, commit, push, importação de dados ou criação de freeze nesta entrega. Fixtures e PostgreSQL de teste são descartáveis; os backups existentes não foram alterados.

Modificados:

- `README.md`
- `api/src/main/java/br/com/election/projection/ElectionAnalyticsController.java`
- `api/src/main/java/br/com/election/projection/ProjectionService.java`
- `api/src/main/java/br/com/election/projection/ProjectionSnapshotStore.java`
- `api/src/test/java/br/com/election/PollApiTests.java`
- `api/src/test/java/br/com/election/ProductionApiTests.java`
- `api/src/test/java/br/com/election/ProjectionIntegrationTests.java`
- `docs/deployment.md`
- `docs/election-projection-model.md`
- `docs/polling-model.md`
- `docs/production-runbook.md`
- `web/app/metodologia/page.tsx`
- `web/app/page.tsx`
- `web/components/election-analytics.tsx`
- `web/components/official-dashboard.tsx`
- `web/components/pre-election-freeze.tsx`
- `web/lib/election-analytics.ts`
- `web/lib/pre-election.ts`

Criados:

- `api/src/main/java/br/com/election/projection/ProjectionInputManifest.java`
- `api/src/main/java/br/com/election/tse/ElectionContextController.java`
- `api/src/main/java/br/com/election/tse/ElectionContextService.java`
- `api/src/main/resources/db/migration/V15__projection_publication_provenance.sql`
- `api/src/test/java/br/com/election/tse/ElectionContextTests.java`
- `docs/election-day-readiness.md`
- `web/components/election-home.tsx`
- `web/lib/election-context.ts`
- `web/lib/projection-publication.ts`
- `web/tests/election-day.test.mjs`
- `web/tests/election-home-render.test.mjs`
