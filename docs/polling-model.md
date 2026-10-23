# Modelo de pesquisas — fundação

## Dados e base de votos

Cada pesquisa identifica instituto, período de campo, instante de publicação, fonte e base de votos. Amostra, margem, registro TSE e metodologia são opcionais. Cada resultado vincula um candidato, seu percentual original e o percentual normalizado calculado no backend. Não há pesquisas fictícias no seed.

### TOTAL

Os percentuais publicados usam a base total de entrevistados. Respostas como branco, nulo e indecisão podem explicar a diferença entre a soma dos candidatos e 100%. Nesta fase, essas respostas não possuem linhas próprias em `poll_results`.

Para cada candidato informado na disputa:

```text
normalizado = original / somaDosCandidatos * 100
```

Exemplo ilustrativo: `47 + 45 = 92`; os valores normalizados são `51,0870%` e `48,9130%`. Uma soma TOTAL superior a 100% é rejeitada.

### VALID

O instituto já publicou os resultados sobre votos válidos:

```text
normalizado = original
```

Não reescalamos valores VALID para corrigir arredondamentos da publicação. Sua soma pode diferir ligeiramente de 100%. Em ambas as bases, os percentuais individuais precisam estar entre 0 e 100 e a soma dos candidatos deve ser maior que zero. Pesquisas sem resultados e candidatos duplicados são rejeitados.

## Precisão decimal

O algoritmo usa `BigDecimal`, escala 4 e `RoundingMode.HALF_UP`; o banco usa `NUMERIC(7,4)`. Requests com mais de quatro casas decimais são rejeitados para evitar divergência entre valores persistidos e calculados. Não há `float`/`double` para percentuais persistidos.

Em TOTAL, após a divisão, qualquer resíduo de arredondamento é atribuído ao candidato com o maior percentual original (o primeiro informado em caso de empate). Isso garante soma exatamente igual a `100,0000%`, preservando candidatos com percentual zero. Em VALID, o valor original é preservado na mesma escala.

O backend recalcula ao criar e editar. O frontend envia somente os percentuais originais e exibe os valores retornados pela API; não determina a normalização. A apresentação pode arredondar a duas casas, enquanto a API preserva quatro.

## Latest poll por instituto

`GET /api/polls/latest` seleciona exatamente uma pesquisa por instituto no PostgreSQL com `DISTINCT ON (pollster_id)` e índice composto. Critérios, em ordem decrescente:

1. `publishedAt`;
2. `fieldEnd`;
3. `createdAt`;
4. `id`, somente para desempate determinístico restante.

A lista final é ordenada pelos mesmos critérios, da mais recente para a mais antiga. Não há filtro do histórico completo em memória. `GET /api/polls` preserva todas as rodadas em ordem decrescente. Não existe filtro implícito por atividade dos cadastros no histórico.

O recorte de uma pesquisa por instituto prepara a entrada de um agregador futuro, evitando repetição de rodadas do mesmo instituto. Esta fase não calcula médias, tendências, probabilidades ou previsões.

## Desempenho histórico

`pollster_performance` armazena instituto, chave da eleição, turno, erro por candidato, erro de diferença e score histórico. O trio instituto/eleição/turno é único. Métricas permanecem opcionais e não há algoritmo de score ou endpoint de calibração nesta fase.

## Interpretação e limites

Nenhuma pesquisa individual constitui previsão oficial do resultado. Os dados publicados, os futuros modelos estatísticos e os resultados oficiais do TSE serão apresentados separadamente. O seed dos candidatos corresponde ao escopo configurado para o produto e não substitui os registros oficiais.

## Próximas etapas

- Média simples.
- Peso por recência.
- Peso por precisão.
- Desempenho histórico.
- Poll of polls calibrado.

Essas etapas ainda não estão implementadas; suas fórmulas e hipóteses serão documentadas junto às respectivas implementações.
