# Modelo de pesquisas — Fases 1 e 2

## Dados e normalização

Cada pesquisa identifica instituto, campo, publicação, fonte e base de votos. Amostra, margem, registro TSE e metodologia são opcionais. Não há percentuais nem desempenho histórico fictícios no seed.

- **TOTAL:** `normalizedPercentage = originalPercentage / somaDosCandidatos × 100`. Respostas como branco, nulo e indecisão podem explicar a diferença até 100%; não são linhas de candidato. Exemplo ilustrativo: 47/45 → 51,0870/48,9130. A soma TOTAL superior a 100% é rejeitada.
- **VALID:** `normalizedPercentage = originalPercentage`, inclusive diferenças de arredondamento da publicação.

Em ambas as bases, os valores individuais devem estar entre 0 e 100, com até quatro casas decimais. A soma zero, resultados vazios e candidatos duplicados são rejeitados. O backend recalcula na criação/edição; o frontend envia somente valores originais. `BigDecimal`, escala 4, `HALF_UP` e `NUMERIC(7,4)` são usados na normalização. Em TOTAL, o resíduo é atribuído ao maior percentual original, preservando zeros.

## Latest e elegibilidade

O agregador reutiliza a seleção de `/api/polls/latest`, feita no PostgreSQL com `DISTINCT ON (pollster_id)`. Ordem decrescente: `publishedAt`, `fieldEnd`, `createdAt`, UUID como desempate final. Uma rodada adicional nunca multiplica a presença de um instituto.

Nos cálculos por data, a consulta considera apenas publicações anteriores ao início do dia seguinte em `America/Sao_Paulo`. Depois, são excluídos:

- institutos inativos;
- pesquisas com fim do campo após a referência;
- pesquisas sem exatamente o conjunto completo de candidatos ativos da disputa.

A disputa precisa de pelo menos dois candidatos ativos. `excludedPolls` informa os motivos; não buscamos uma rodada anterior para substituir uma latest inelegível. Não há flag de atividade em `Poll`. O histórico público de pesquisas permanece completo.

## Referência, midpoint e idade

O serviço recebe `LocalDate referenceDate` explicitamente; os endpoints usam a data atual de Brasília obtida de um `Clock` injetado quando o parâmetro não é informado. `calculatedAt` é o instante real do cálculo. Exemplo: `/api/aggregates?referenceDate=2026-10-22`.

Interpretamos as datas de campo como dias nominais de 24 horas desde 00:00, sem mudanças por horário de verão:

```text
midpoint = fieldStart + (fieldEnd − fieldStart) / 2
ageInDays = max(0, referência às 00:00 − midpoint), em dias fracionários
```

20–22/10 → 21/10 às 00:00. 18–21/10 → 19/10 às 12:00. A publicação determina latest e o corte temporal, mas não a idade da coleta.

Reprodução por data usa **os dados e configurações atuais**, inclusive atividade dos cadastros e desempenho histórico. Não reconstrói automaticamente a informação conhecida naquela época. Snapshots preservam versões exatas.

## Modelos

Seja `vᵢ,c = normalizedPercentage` do candidato `c` na pesquisa `i`; `n` é o número de institutos elegíveis, `bᵢ` o peso bruto e `wᵢ` o peso final.

### SIMPLE

```text
bᵢ = 1
wᵢ = 1 / n
```

Pesos iguais até a precisão decimal representável, sem cap, recência, precisão ou histórico.

### RECENCY

```text
rᵢ = 2^(−ageInDaysᵢ / halfLifeDays)
bᵢ = rᵢ
wᵢ = bᵢ / Σb
```

Padrão: meia-vida de três dias. Idades 0, 3 e 6 dão fatores 1, 0,5 e 0,25. Este método não aplica cap.

### WEIGHTED

```text
estimatedMarginOfErrorᵢ = 98 / sqrt(sampleSizeᵢ)  sem margem, com amostra positiva
pᵢ = 1 / marginOfErrorᵢ²                       se houver margem declarada válida
pᵢ = 1 / estimatedMarginOfErrorᵢ²              sem margem, com amostra positiva
pᵢ = 1                                        sem margem e amostra válidas
bᵢ = rᵢ × pᵢ
w = redistribuirComCap(b / Σb)
```

`precisionSource` informa `MARGIN_OF_ERROR`, `SAMPLE_SIZE` ou `NEUTRAL`. A ausência de ambos os insumos gera uma nota.

A margem é expressa em **pontos percentuais**. A margem declarada válida tem prioridade, mesmo quando uma amostra também é informada. Sem margem, a estimativa é aproximadamente 2,53 p.p. para n=1.500, 2,19 p.p. para n=2.000 e 1,55 p.p. para n=4.000. Aplicar a mesma função `1/margem²` mantém a escala compatível com margens declaradas; algebricamente, o fallback equivale a `sampleSize/9604`.

Esta é **apenas uma aproximação de fallback baseada em amostragem aleatória simples, 95% de confiança e máxima variância** (`1,96 × sqrt(0,5 × 0,5) × 100 = 98`). Não é uma reconstrução da metodologia do instituto: não incorpora efeito de desenho, ponderações, não resposta ou outras fontes de erro. A margem estimada não substitui a margem declarada nem é publicada como se tivesse sido informada pelo instituto. Amostra nula, zero ou negativa não é válida para estimar a margem; sem margem válida, o fator permanece neutro (1).

### CALIBRATED

```text
bᵢ = rᵢ × pᵢ × hᵢ
w = redistribuirComCap(b / Σb)
```

`hᵢ` é o fator histórico limitado. Sem histórico, `hᵢ=1` e o modelo coincide com WEIGHTED. **Desempenho histórico altera o peso do instituto, não o percentual publicado.**

## Cap e redistribuição

WEIGHTED e CALIBRATED usam `c=0,15` por padrão. O limite é viável quando `n×c≥1`, exigindo pelo menos sete institutos no padrão.

1. Distribuir massa 1 proporcionalmente aos pesos brutos.
2. Fixar em `c` os institutos cujo peso excede esse limite.
3. Subtrair a massa fixada e redistribuir o restante entre os institutos livres, proporcionalmente a seus pesos brutos.
4. Repetir até todos respeitarem o limite.
5. Arredondar para baixo a 16 casas e distribuir apenas o resíduo, respeitando a capacidade restante.

Se inviável, usamos **pesos iguais `1/n`**. `cap.feasible=false`, `cap.effective` informa o limite efetivo e `warnings` explica o fallback. `capped` identifica apenas institutos fixados pelo algoritmo quando o cap é viável. Com poucos institutos, WEIGHTED e CALIBRATED podem coincidir com SIMPLE.

Os pesos finais somam exatamente 1 em decimal. A tolerância nomeada nos testes para comparação de divisões/exponenciais é `1E-12`.

## Resultado agregado e diferença

```text
s_c = Σᵢ(wᵢ × vᵢ,c)
P_c = 100 × s_c / Σ_c s_c
```

A normalização proporcional acontece **somente no resultado agregado**, inclusive para lidar com arredondamentos publicados em VALID. Arredondamos `P_c` a quatro casas com `HALF_UP` e atribuímos o resíduo ao maior `s_c` (UUID resolve empate). O total final é exatamente 100,0000%. Os valores individuais da composição permanecem intactos.

`spread.points` é a diferença dos dois maiores percentuais finais. Em empate, a diferença é zero e o líder é null. A amostra total usa `long`/`BIGINT`, soma apenas valores informados e sinaliza ausências em `pollsWithoutSampleSize`; não é uma amostra independente única.

### Fronteira numérica

Somas, produtos, divisões e raiz quadrada usam `BigDecimal`, com `MathContext.DECIMAL128` (34 dígitos) onde necessário. Pesos finais: escala 16. Percentuais finais/persistidos: escala 4. Não há `double` persistido.

Para a exponencial, decompomos `x=age/halfLife` em inteiro `k` e fração `f`:

```text
2^(−x) = StrictMath.pow(2, −f) / BigDecimal(2).pow(k)
```

Apenas `f∈[0,1)` e seu fator passam por `double`, com retorno via `BigDecimal.valueOf`. Isso evita underflow para pesquisas antigas. O limite numérico explícito de expoente 1.000.000 rejeita combinações patológicas de idade/meia-vida. Não existe janela estatística máxima de idade nesta fase.

## Desempenho histórico

`pollster_performance` tem unicidade de instituto/eleição/turno. `candidateError` é informativo; a calibração usa `spreadError`, erro absoluto da diferença dos candidatos em pontos percentuais contra referência verificada. V9 adiciona `sourceUrl`, `sourceDescription` e um índice, **sem inserir números inventados**. A tabela permanece vazia até cadastro explícito.

Sejam `m=historicalMinFactor`, `M=historicalMaxFactor`, `t=historicalNeutralSpreadError`, `e=spreadError≥0`:

```text
e ≤ t: h(e) = 1 + (M−1) × (1−e/t)
e > t: h(e) = max(m, 1−(1−m) × (e−t)/t)
sem histórico ou spreadError null: h = 1
```

O fator é limitado a `[m,M]`. Com os padrões `m=0,85`, `M=1,15`, `t=3`:

```text
h(e) = clamp(1,15 − e/20, 0,85, 1,15)
```

Erros 0, 3 e 6+ produzem 1,15, 1 e 0,85. Os limites conservadores refletem a evidência histórica ainda limitada. O fator positivo limitado impede que o histórico sozinho zere ou domine um peso; o cap permanece obrigatório quando viável. Limites e erro neutro continuam configuráveis.

A consulta usa **somente** `historicalElectionKey` e `historicalRound`, sem misturar eleições ou escolher uma linha arbitrária. `historicalScore` é gravado como espelho derivado a quatro casas na criação/edição. Agregação e response sempre derivam novamente o fator do erro e da configuração atual. Não há edição manual de score; registros legados sem erro são neutros.

O admin `/admin/institutos` permite cadastro/edição e registro de fonte. A fonte é opcional no schema para acomodar legado, mas deve ser informada para dados verificados. Nenhuma fonte real de erros consta no repositório.

## Configuração

Binding tipado e validado em `polling.aggregation`:

| Propriedade | Padrão | Validação |
| --- | --- | --- |
| `half-life-days` | 3 | maior que zero |
| `max-institute-weight` | 0.15 | maior que zero, até 1, até 8 casas |
| `historical-min-factor` | 0.85 | maior que zero e até 1 |
| `historical-max-factor` | 1.15 | entre 1 e 2 |
| `historical-neutral-spread-error` | 3 | maior que zero |
| `historical-election-key` | BR-PRESIDENT-2026 | não vazia |
| `historical-round` | 1 | 1 ou 2 |

Os parâmetros efetivamente usados integram cada response e snapshot. Podem ser sobrescritos por propriedades Spring, inclusive variáveis como `POLLING_AGGREGATION_HALF_LIFE_DAYS`.

## Exemplo completo

Exemplo **didático**, sem relação com institutos reais e sem seed. Referência: 22/10/2026. Oito institutos ativos, uma latest completa por instituto, dois candidatos X/Y e amostra 1.000 em cada pesquisa:

| Instituto | Campo | X / Y normalizados | Margem | Erro histórico | Fator |
| --- | --- | --- | --- | --- | --- |
| A | 22/10–22/10 | 60 / 40 | 1 | 0 | 1,15 |
| B | 19/10–19/10 | 40 / 60 | 2 | 6 | 0,85 |
| C–H, cada um | 22/10–22/10 | 50 / 50 | 2 | ausente | 1 |

1. **SIMPLE:** cada peso é `0,125`. X=`50,0000%`; Y=`50,0000%`.
2. **RECENCY:** fatores A=1, B=0,5, C–H=1. Soma=7,5. Pesos A=0,133333…, B=0,066666…, C–H=0,133333… cada. X=`50,6667%`; Y=`49,3333%`.
3. **WEIGHTED:** precisão A=1, demais=0,25. Brutos A=1, B=0,125, C–H=0,25. A fica no cap 0,15. Restam 0,85 para soma bruta 1,625: B=0,065384615…, C–H=0,130769230… cada. X=`50,8462%`; Y=`49,1538%`.
4. **CALIBRATED:** brutos A=1,15, B=0,10625, C–H=0,25. A fica no cap 0,15. Restam 0,85 para soma bruta 1,60625: B=0,0562256809…, C–H=0,1322957198… cada. X=`50,9377%`; Y=`49,0623%`.

Nenhum percentual individual foi corrigido pelo erro histórico. Com apenas A/B, o cap seria inviável: WEIGHTED e CALIBRATED usariam 0,5/0,5 e informariam esse fallback.

## Snapshots e evolução

`POST /api/admin/aggregates/snapshot` calcula os quatro modelos uma única vez e salva quatro snapshots atomicamente; aceita `referenceDate`. Sem pesquisas elegíveis, retorna 400, sem snapshots vazios. GETs não persistem e não há agendamento.

Idempotência: chave `(method, reference_date, snapshot_minute)`, sendo o minuto o instante de cálculo truncado em UTC. Repetições no mesmo período retornam o registro original, **mesmo se dados/configuração mudaram**. Um novo minuto permite novo snapshot. `INSERT ... ON CONFLICT DO NOTHING` protege a unicidade sob concorrência.

Escolhemos JSONB para o payload completo (resultados, composição, parâmetros, notas, exclusões). São poucos institutos; preservar os insumos permite auditoria após edições/exclusões. Metadados consultados/indexados ficam em colunas relacionais. Não há FK para pesquisas: excluir uma fonte não apaga snapshots.

`GET /api/aggregates/history?method=CALIBRATED` retorna projeção leve com resultados/metadados, ordenada por `calculatedAt` e UUID. O gráfico usa instante de cálculo no eixo X, Brasília na exibição, eixo Y de 0 a 100% e linhas que conectam registros sem projetar valores. Exige dois snapshots. Backfills mantêm sua `referenceDate` na tabela de valores.

## Performance e endpoints

`GET /api/aggregates` retorna quatro modelos usando um carregamento compartilhado. Há também `/simple`, `/recency`, `/weighted`, `/calibrated`. A seleção completa executa cinco consultas independentemente do número de institutos: latest; pesquisas com institutos; resultados com candidatos; candidatos ativos; histórico da eleição/turno. Um teste com 14 institutos impede N+1 e consultas repetidas de latest.

Desempenho: `GET/POST /api/admin/pollster-performances`, `PUT /api/admin/pollster-performances/{id}`. Campos: `pollsterId`, `electionKey`, `round`, `spreadError`; opcionais: `candidateError`, `sourceUrl`, `sourceDescription`. Duplicatas retornam 409; validações retornam ProblemDetail 400.

## Limitações e próximas etapas

Agregado não é previsão oficial nem probabilidade de vitória. Não há intervalo de confiança do agregado, correção de efeito de casa, modelagem da dependência entre pesquisas ou ajuste de desenho amostral. A soma de amostras é descritiva. Alterações nos candidatos ativos podem excluir pesquisas anteriores. Resultados de pesquisas, modelos e apuração oficial são categorias separadas.

Próximos passos: revisão empírica das escalas de precisão, sensibilidade dos parâmetros, cadastro de desempenho real com fontes e avaliação de dependências/metodologias. Nada disso altera silenciosamente as fórmulas implementadas. TSE, apuração, mapa e autenticação administrativa já existem em camadas separadas. SSE, scraping editorial e projeção probabilística permanecem fora deste agregador.
