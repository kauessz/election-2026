# Importação auditável de dados

As fórmulas das Fases 1–4 não foram alteradas. Os arquivos de `data/*.example.json` são **exemplos fictícios**, com instituto/candidatos inexistentes na configuração real. Não são evidência eleitoral e não devem ser carregados em produção. Arquivos reais podem ficar em `data/private/`, ignorado pelo Git. Não há carga real automática nesta fase.

## Pesquisas

`POST /api/admin/polls/import?dryRun=true` recebe `{ "polls": [ ... ] }` ou um array; 1 a 1.000 registros. Para gravar, repetir o conteúdo validado em `POST /api/admin/polls/import` (ou `dryRun=false`). Cada registro admite:

- `pollsterSlug`: slug exato de instituto cadastrado, após trim; inexistência/ambiguidade falham.
- `tseRegistrationNumber`: opcional, trim; vazio vira null. Não inventar registro TSE.
- `fieldStart` / `fieldEnd`: datas ISO; fim não pode preceder início.
- `publishedAt`: instante ISO com fuso, por exemplo `2026-10-23T12:00:00Z`.
- `sampleSize`: inteiro positivo opcional; `marginOfError`: positivo opcional, até 100, quatro casas, em pontos percentuais.
- `methodology`: texto opcional até 5.000 caracteres.
- `sourceUrl`: obrigatório, HTTP(S), até 2.000 caracteres; usar a publicação original/documento verificável.
- `voteBasis`: `TOTAL` ou `VALID`.
- `candidateResults`: array não vazio de `{ "ballotNumber": 9001, "percentage": 40 }` (números aqui meramente ilustrativos). Número precisa corresponder a exatamente um candidato cadastrado.

A importação converte slugs/números para IDs e chama a **mesma validação e normalização de PollService** do cadastro individual. Nenhuma fórmula paralela. `TOTAL` normaliza os candidatos para 100, mantendo originais. `VALID` mantém os percentuais declarados; a qualidade verifica soma em 100 ±0,1 p.p. sem corrigir silenciosamente a origem.

### Idempotência e falhas

Chave: instituto + registro quando não vazio. Sem registro: instituto + fieldEnd + publishedAt (instante). Pesquisas de institutos diferentes não são confundidas. Chave com mais de uma pesquisa existente falha e exige correção, sem escolha arbitrária. A mesma chave atualiza o registro e preserva seu UUID; reimportar conteúdo equivalente é `UNCHANGED`, independentemente de ordem dos candidatos ou escala decimal.

Cada registro real tem transação própria; falha reverte aquele registro, os demais prosseguem. Lock transacional PostgreSQL serializa importações concorrentes (também a criação do freeze). A UI exige dry run bem-sucedido do texto atual e confirmação explícita. A API permite execução direta para clientes administrativos; **o procedimento operacional exige dry run antes de qualquer carga real**. Todos os registros são revalidados no envio real.

Dry run não escreve nem atualiza verifiedAt. Mantém estado virtual por chave dentro do lote, detectando repetição/atualização entre linhas como no envio real. Retorna `created`, `updated`, `unchanged`, `failed`, e `details` com índice **zero-based**, status, ID existente (novo ID é null na simulação), chave lógica, normalização e mensagem. Arquivo inválido como envelope gera 400; falhas por registro retornam relatório 200, inclusive falha parcial. Revisar `failed`, não apenas o HTTP.

## Desempenho histórico

`POST /api/admin/pollster-performances/import?dryRun=true` e execução real recebem `{ "performances": [...] }` ou array.

Campos: pollsterSlug, electionKey, round (1/2), candidateError, spreadError, sourceUrl obrigatório, sourceDescription opcional, notes opcional. Erros são absolutos, não negativos, até quatro casas. Chave: instituto + electionKey + round. O fator é sempre derivado por HistoricalScoreService. `historicalScore`, `historicalFactor` e `historicalWeightFactor` arbitrários são rejeitados.

Uma importação real criada/atualizada registra `verifiedAt` com o relógio do backend: significa que o operador forneceu proveniência nesse instante, **não** que a API confirmou automaticamente a veracidade da publicação. Reimportação inalterada preserva o instante. Migration não preenche origem/verificação de registros legados.

### Cálculo de erro do primeiro turno

`POST /api/admin/pollster-performances/calculate` recebe:

```json
{
  "pollId": "UUID da última pesquisa anterior ao primeiro turno",
  "electionDate": "2026-10-04",
  "candidateA": 9001,
  "candidateB": 9002,
  "officialFinal": true,
  "officialResults": [
    { "ballotNumber": 9001, "percentage": 55 },
    { "ballotNumber": 9002, "percentage": 45 }
  ]
}
```

Exemplo de resultados **fictício**, sem dados eleitorais reais. Fornecer votos válidos finais e todos os mesmos candidatos da pesquisa, soma 100 ±0,1 p.p. A API verifica se o pollId é a última pesquisa publicada e com campo encerrado antes do dia do primeiro turno para aquele instituto. A confirmação `officialFinal` é uma afirmação do operador; este deve auditar a fonte. Este endpoint não busca nem autentica um resultado TSE informado manualmente.

Retorno: absolute(predicted−actual) por candidato, candidateError do candidato A, spread previsto A−B, real A−B, spreadError absoluto, persisted=false. Nenhuma persistência automática; anexar fontes ao importar o desempenho calculado. Não usar pesquisas do segundo turno para calcular desempenho do primeiro.

## Auditoria e qualidade

`GET /api/aggregates/audit` retorna os quatro modelos com o AggregateResponse original: pesos/fatores por pesquisa, percentuais, amostra, configuração e warnings. Acrescenta pesquisas consideradas com fonte, todas as descartadas (inclusive substituídas e futuras), motivo, soma normalizada/bruta de pesos e extremos de midpoint. “Antiga” significa campo encerrado há mais de 14 dias; “poucas” significa menos de 3 pesquisas. São alertas, não filtros novos. Cap e ausência de histórico mantêm o comportamento estatístico existente.

`GET /api/admin/data-quality`: status READY sem issues, WARNINGS sem ERROR mas com avisos, ERRORS com algum ERROR. `ready=true` **somente** sem ERROR. Verifica slugs, resultados vazios, soma VALID, TOTAL zero, fonte, chaves duplicadas com e sem registro, origem de performance, candidato desconhecido/número ambíguo, datas futuras/campo invertido e referências das contribuições de snapshots a pesquisas ausentes. Chaves estrangeiras/checks também impedem inconsistências na escrita. Performance legada sem verifiedAt é WARNING; sem fonte é ERROR. Snapshot histórico sem campo contributions não permite verificar IDs e não inventa vínculos. Freeze guarda cópias históricas independentes, não referências mutáveis; exclusão posterior de cadastro não apaga a composição congelada.

Administração: `/admin/importacao` (pesquisas, histórico e qualidade), `/admin/freeze` (revisão e confirmação). As rotas seguem o mesmo perímetro administrativo das fases anteriores; esta fase não adiciona autenticação.
