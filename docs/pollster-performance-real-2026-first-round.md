# Histórico presidencial real — 1º turno de 2026

Preparação auditada inicialmente em dry run; a revisão de padronização da chave e importação real está registrada ao final deste documento. Nenhuma pesquisa artificial de 1º turno foi criada.

## Fontes e base comparável

Fonte metodológica: [HubPolítico](https://hubpolitico.com.br/inteligencia/relatorios/eleicao-2026/precisao-dos-institutos). Foram abertas e conferidas as 17 páginas individuais usadas em sourceUrl. Slugs conferidos em GET /api/pollsters. Meio/Ideia usa Ideia e CNT/MDA usa MDA, com associações explícitas nas notes. Ipespe está ausente do lote por falta de histórico comparável confirmado.

Referência oficial fornecida: Flávio 47,03% e Lula 45,16%, spread 1,87 p.p. São percentuais sobre todos os votos válidos do 1º turno, não uma disputa restrita a dois candidatos. A soma de 92,19% preserva os demais candidatos; não houve renormalização dos pares.

## Endpoint ampliado, mesmas fórmulas

POST /api/admin/pollster-performances/calculate aceita agora predictedCandidateResults + actualCandidateResults sem pollId. O modo antigo por pollId permanece compatível. Ambos convergem para o mesmo cálculo. O novo modo não consulta nem grava pesquisas, candidatos ou históricos e retorna pollId=null, persisted=false.

Informe candidateA=22, candidateB=13 e officialFinal=true. Cada lista deve conter exatamente os mesmos dois candidatos distintos, percentuais válidos de 0 a 100 e soma até 100,1%. completeCandidateSet é true por padrão: as duas somas precisam ficar em 100 ± 0,1 p.p. Para os pares parciais do primeiro turno desta preparação, foi enviado explicitamente completeCandidateSet=false; permanece a mesma base de votos válidos sem completar ou inventar outros candidatos. Não misturar pollId/officialResults com o modo direto.

candidateError mantém exatamente o contrato existente: erro absoluto do candidato A (Flávio), e não a média dos candidatos. predictedSpread = previsto A − previsto B; actualSpread = oficial A − oficial B; spreadError é o valor absoluto da diferença entre spreads. Todos os erros do arquivo vieram das 17 respostas HTTP do serviço; não foram calculados manualmente.

## Erros calculados e diferenças de arredondamento

| Instituto | candidateError | spreadError exato | Esperado aproximado | Diferença | Fonte |
|---|---:|---:|---:|---:|---|
| Veritá | 0.03 | 0.13 | 0.1 | +0.03 | [individual](https://hubpolitico.com.br/inteligencia/relatorios/eleicao-2026/precisao-dos-institutos/verita) |
| Gerp | 1.63 | 0.23 | 0.2 | +0.03 | [individual](https://hubpolitico.com.br/inteligencia/relatorios/eleicao-2026/precisao-dos-institutos/gerp) |
| Futura | 2.23 | 0.33 | 0.3 | +0.03 | [individual](https://hubpolitico.com.br/inteligencia/relatorios/eleicao-2026/precisao-dos-institutos/futura) |
| Vox Brasil | 1.13 | 0.97 | 1.0 | -0.03 | [individual](https://hubpolitico.com.br/inteligencia/relatorios/eleicao-2026/precisao-dos-institutos/vox-brasil) |
| Palver | 0.47 | 2.23 | 2.2 | +0.03 | [individual](https://hubpolitico.com.br/inteligencia/relatorios/eleicao-2026/precisao-dos-institutos/palver) |
| Meio/Ideia | 7.93 | 2.87 | 2.9 | -0.03 | [individual](https://hubpolitico.com.br/inteligencia/relatorios/eleicao-2026/precisao-dos-institutos/ideia) |
| PoderData | 2.93 | 2.97 | 2.9 | +0.07 | [individual](https://hubpolitico.com.br/inteligencia/relatorios/eleicao-2026/precisao-dos-institutos/poderdata) |
| Datafolha | 3.53 | 4.07 | 4.0 | +0.07 | [individual](https://hubpolitico.com.br/inteligencia/relatorios/eleicao-2026/precisao-dos-institutos/datafolha) |
| Quaest | 3.33 | 4.17 | 4.2 | -0.03 | [individual](https://hubpolitico.com.br/inteligencia/relatorios/eleicao-2026/precisao-dos-institutos/quaest) |
| AtlasIntel | 2.93 | 4.77 | 4.8 | -0.03 | [individual](https://hubpolitico.com.br/inteligencia/relatorios/eleicao-2026/precisao-dos-institutos/atlasintel) |
| Instituto França | 6.53 | 5.27 | 5.3 | -0.03 | [individual](https://hubpolitico.com.br/inteligencia/relatorios/eleicao-2026/precisao-dos-institutos/instituto-franca) |
| Real Time Big Data | 7.03 | 5.97 | 6.0 | -0.03 | [individual](https://hubpolitico.com.br/inteligencia/relatorios/eleicao-2026/precisao-dos-institutos/real-time-big-data) |
| DataTrends | 7.23 | 6.37 | 6.4 | -0.03 | [individual](https://hubpolitico.com.br/inteligencia/relatorios/eleicao-2026/precisao-dos-institutos/datatrends) |
| Nexus | 8.03 | 7.07 | 7.1 | -0.03 | [individual](https://hubpolitico.com.br/inteligencia/relatorios/eleicao-2026/precisao-dos-institutos/nexus) |
| CNT/MDA | 4.53 | 7.57 | 7.6 | -0.03 | [individual](https://hubpolitico.com.br/inteligencia/relatorios/eleicao-2026/precisao-dos-institutos/mda) |
| Indexa | 7.03 | 7.77 | 7.8 | -0.03 | [individual](https://hubpolitico.com.br/inteligencia/relatorios/eleicao-2026/precisao-dos-institutos/indexa) |
| Alfa Inteligência | 10.93 | 9.47 | 9.5 | -0.03 | [individual](https://hubpolitico.com.br/inteligencia/relatorios/eleicao-2026/precisao-dos-institutos/alfa-inteligencia) |

Diferença máxima de 0,07 p.p. em relação aos valores aproximados fornecidos, dentro da tolerância de 0,1 p.p. A tabela pública arredonda os percentuais e alguns erros; foram preservados os pares fornecidos e a referência oficial com duas casas.

## Dry run

POST /api/admin/pollster-performances/import?dryRun=true: created=17, updated=0, unchanged=0, failed=0. Evidência completa em data/pollster-performance.real.2026-first-round.dry-run.json. SHA-256 do lote após padronização: `6a77b734912918588eb48653429cb0d6ed47e617d637c3152a8039845f032cfd`.

## Prévia do CALIBRATED

ReferenceDate=2026-10-23; os 18 insumos atuais continuam participando. Motor AggregateEngine e HistoricalScoreService existentes executados em memória; 17 históricos e Ipespe neutro. Nenhuma fórmula alterada.

**Chave padronizada antes da importação:** electionKey foi alterada exclusivamente de 2026-PRESIDENT-BR para BR-PRESIDENT-2026 nos 17 registros, alinhando o arquivo à configuração ativa. Round=1, erros, fontes e notes foram preservados integralmente. Nenhum YAML, configuração ativa ou fórmula foi alterado. A nova prévia usa a configuração ativa sem override de chave.

| Instituto | WEIGHTED anterior (%) | spreadError | Fator histórico | CALIBRATED (%) | Variação (p.p.) |
|---|---:|---:|---:|---:|---:|
| Veritá | 12.2246 | 0.13 | 1.1435 | 14.2309 | +2.0063 |
| PoderData | 9.5505 | 2.97 | 1.0015 | 9.7373 | +0.1868 |
| AtlasIntel | 8.6441 | 4.77 | 0.9115 | 8.0212 | -0.6229 |
| Vox Brasil | 6.6639 | 0.97 | 1.1015 | 7.4727 | +0.8087 |
| Datafolha | 7.7010 | 4.07 | 0.9465 | 7.4205 | -0.2806 |
| Gerp | 6.1123 | 0.23 | 1.1385 | 7.0843 | +0.9720 |
| Real Time Big Data | 6.8608 | 5.97 | 0.8515 | 5.9473 | -0.9135 |
| Alfa Inteligência | 6.7726 | 9.47 | 0.8500 | 5.8605 | -0.9121 |
| Futura | 4.5004 | 0.33 | 1.1335 | 5.1932 | +0.6928 |
| Indexa | 5.6701 | 7.77 | 0.8500 | 4.9065 | -0.7636 |
| Ipespe | 4.5568 | — | 1.0000 | 4.6390 | +0.0822 |
| Instituto França | 4.7537 | 5.27 | 0.8865 | 4.2902 | -0.4636 |
| Quaest | 4.3221 | 4.17 | 0.9415 | 4.1426 | -0.1795 |
| Palver | 3.3690 | 2.23 | 1.0385 | 3.5618 | +0.1928 |
| Nexus | 3.0562 | 7.07 | 0.8500 | 2.6446 | -0.4116 |
| Meio/Ideia | 1.9559 | 2.87 | 1.0065 | 2.0041 | +0.0482 |
| DataTrends | 2.0230 | 6.37 | 0.8500 | 1.7506 | -0.2724 |
| CNT/MDA | 1.2629 | 7.57 | 0.8500 | 1.0928 | -0.1701 |

| Modelo | Flávio | Lula | Spread (p.p.) |
|---|---:|---:|---:|
| WEIGHTED | 50.9630% | 49.0370% | 1.9260 |
| CALIBRATED | 50.9549% | 49.0451% | 1.9098 |

Confirmado: percentuais de cada pesquisa idênticos antes/depois; somente pesos mudam. Fatores entre 0,85 e 1,15, Ipespe=1, soma dos pesos=1.0000000000000000 (tolerância 1e-12), nenhum peso acima de 15%. Pesos completos sem arredondamento de apresentação e resultados em data/pollster-performance.real.2026-first-round.preview.json.

Estado na preparação inicial: 18 pesquisas, zero PollsterPerformance, quatro snapshots existentes e nenhum PreElectionFreeze. Ver revisão operacional final abaixo.


## Ressalvas da fonte

O comparativo individual de DataTrends identifica campo encerrado em 14/09 e sinaliza mais de 15 dias até a votação. O de Gerp sinaliza publicação registrada antes do término do campo. Essas ressalvas foram preservadas na auditoria; não se alteraram os pares fornecidos nem se fabricaram datas de publicação ou pesquisas anteriores. A referência a “última pesquisa” nas notes segue o recorte fornecido pelo usuário e o comparativo presidencial consultado, sem afirmar verificação adicional de todos os registros do TSE.


## Validações

`api/ .\mvnw.cmd test`: 232 testes, zero falhas/erros/skips, BUILD SUCCESS. Acrescentados 8 casos unitários e 1 teste HTTP de integração; o teste existente por pollId também passou. Cobertos pares parciais sem renormalização, conjunto completo com tolerância, soma inválida, candidatos duplicados/ausentes/divergentes, limites percentuais, mistura de modos, resultado não final e ausência de acesso/persistência no modo direto. `git diff --check` passou. Base e agregado ativo novamente conferidos após a suíte: nenhuma alteração nos registros operacionais.


## Revisão operacional: chave padronizada e importação real

Por autorização explícita do usuário, os 17 electionKey foram padronizados para `BR-PRESIDENT-2026` antes da importação. A comparação com o arquivo anterior confirma que nenhum erro, sourceUrl, sourceDescription ou notes foi alterado. Configuração ativa, round=1 e fórmulas preservados.

O novo dry run retornou created=17, updated=0, unchanged=0, failed=0. A prévia em memória usou a configuração ativa sem override e confirmou CALIBRATED 50,9549% / 49,0451%. Só então foi executada a importação real do mesmo arquivo, com created=17, updated=0, unchanged=0, failed=0. A repetição em dry run retornou created=0, updated=0, unchanged=17, failed=0.

Data-quality: READY, ready=true, issues=[]. GET /api/aggregates/calibrated e GET /api/aggregates/audit confirmaram o resultado sobre os históricos persistidos: Flávio 50,9549%, Lula 49,0451%, spread 1,9098 p.p.; 18 pesquisas, 18 institutos, 59.442 entrevistados. Todos os 17 fatores históricos foram conferidos individualmente contra os erros importados. Ipespe segue sem PollsterPerformance, fator 1. Soma dos pesos 1,0000000000000000, tolerância 1e-12, todos abaixo de 15%. As 18 pesquisas e seus percentuais permaneceram exatamente iguais ao estado anterior.

Para atender ao snapshot exclusivo, o endpoint existente ganhou parâmetro opcional `method`. Sem parâmetro mantém os quatro modelos; `POST /api/admin/aggregates/snapshot?method=CALIBRATED` cria somente esse modelo, com a mesma política de idempotência por minuto. Foram adicionados testes de seleção, preservação dos outros modelos, repetição idempotente, método inválido e ausência de pesquisas.

Após todos os checks, foi criado exatamente um novo snapshot CALIBRATED. O histórico completo passou de quatro para cinco snapshots; os quatro anteriores foram comparados integralmente e preservados.

| Snapshot CALIBRATED | ID | Data/hora de Brasília | Flávio | Lula |
|---|---|---|---:|---:|
| Anterior, fator neutro | `30df12c8-287c-48d3-95e9-cc4d04ef66da` | 23/10/2026 11:31:10 | 50.9630% | 49.0370% |
| Novo, com histórico | `408fc18e-7f83-4ac9-ac0b-7d709f9d35b9` | 23/10/2026 12:09:20 | 50.9549% | 49.0451% |

Warning operacional: apenas Ipespe sem histórico para a eleição/turno configurados; fator neutro 1. As ressalvas de fonte acima continuam documentadas. Nenhum PreElectionFreeze criado, nenhuma pesquisa modificada e nenhum commit. Evidência completa da importação, idempotência, auditoria e snapshots em `data/pollster-performance.real.2026-first-round.import.json`.

Validação desta revisão: `.\mvnw.cmd test` em api — 234 testes, zero falhas/erros/skips, incluindo dois novos testes de snapshot seletivo. `git diff --check` passou. Conferência final após os testes: 17 históricos persistidos e verificados, mesmas 18 pesquisas, cinco snapshots (apenas um novo CALIBRATED), data-quality READY sem issues.
