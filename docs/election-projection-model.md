# Ritmo e projeção durante a apuração — Fase 4

## Três conceitos independentes

- **OFFICIAL_RESULT**: DTOs `OfficialElectionResult`/`OfficialResultResponse` da integração TSE, sem alteração de votos ou percentuais.
- **TREND**: `TrendResponse` descreve deltas recebidos. `RemainingResponse` organiza eleitorado pendente, cálculo derivado do EA20, sem prever votos.
- **PROJECTION**: `ProjectionResponse` é estimativa própria, modelo **STATE_SHRINKAGE_V1**. Nenhuma estimativa é escrita em campos oficiais, baseline oficial ou snapshots TSE.

Não há previsão de vencedor, chamada de eleição, probabilidade eleitoral, enquete, intenção de voto ou aprendizado de máquina. Cobertura é volume de eleitorado representado, não chance de acerto.

## Fonte e carga do baseline

[Documentação técnica oficial do TSE](https://www.tse.jus.br/eleicoes/informacoes-tecnicas-sobre-a-divulgacao-de-resultados) confirma a eleição federal **6257** no primeiro turno. Usamos o mesmo [EA20 oficial](https://www.tse.jus.br/eleicoes/eleicoes-2026-content/arquivos/divulgacao-de-resultados/tse-ea20-arquivo-de-resultado-unificado/@@download/file), cargo **0001**, ambiente oficial de 2026.

URLs centralizadas em `TseUrlBuilder.baseline`, base `tse.results.base-url`:

```text
/oficial/ele2026/6257/dados/br/br-c0001-e006257-u.json
/oficial/ele2026/6257/dados/{uf}/{uf}-c0001-e006257-u.json
```

Sincronização **somente explícita**, nunca pelo scheduler:

```powershell
Invoke-RestMethod -Method Post `
  -Uri http://localhost:8081/api/admin/election-baselines/first-round/sync `
  -TimeoutSec 240
```

Consulta apenas Brasil e as 27 UFs conhecidas: **28 escopos**, sem municípios. Compartilha o cliente, limite de taxa, timeouts, ETag/Last-Modified e backoff da Fase 3. A operação pode durar dezenas de segundos porque respeita o orçamento global. Uma nova chamada retorna 304 quando aplicável e reutiliza o baseline persistente. Não iniciar sincronizações concorrentes: o serviço serializa a operação por processo.

Cada arquivo precisa ter eleição 6257, turno 1, fase oficial, abrangência esperada, `tf=s`, eleitorado e votos válidos positivos e ambos os números eleitorais 13 e 22. Rejeitamos geração futura ou anterior à já armazenada e contagens incompatíveis com o domínio do modelo. Um arquivo inválido não substitui o anterior nem impede o processamento dos demais. Resultado do POST: `successCount`, `failureCount`, `successfulScopes`, `errors[{scope,error}]`. HTTP 200 com erros parciais exige verificar o resumo, não apenas o status HTTP.

GET `/api/admin/election-baselines/first-round` permite auditar os registros sem rede externa. Após a carga, a projeção lê exclusivamente o PostgreSQL. Não é necessário repetir sync durante a apuração. Autenticação administrativa continua fora do escopo, como nas fases anteriores.

## Baseline por UF

Todos os candidatos oficiais e seus valores são preservados no resultado normalizado do primeiro turno. Somente o **prior entre os dois concorrentes** ignora os demais:

```text
baselineShare13 = votes13 / (votes13 + votes22)
baselineShare22 = 1 - baselineShare13
firstRoundValidRate = firstRoundValidVotes / firstRoundElectorate
```

Persistimos eleitorado, votos totais/válidos/brancos/nulos, taxa válida, candidatos originais, pairwise shares derivados, geração, captura e URL. `v.tvn` inclui nulos técnicos, conforme a Fase 3. Número eleitoral, nunca nome, identifica o candidato.

A normalização pairwise **não estima como eleitores dos outros candidatos transferirão seus votos**. Esse é um prior transparente, não expectativa perfeita do segundo turno. O baseline nacional é preservado para auditoria; não é usado para extrapolar o percentual nacional parcial.

## Modelo estadual

Sejam `E` o eleitorado da UF no segundo turno, `R` o eleitorado das seções totalizadas, `V` os votos válidos observados, `a` o prior equivalente (0,05 padrão).

```text
progress = R / E
priorElectorate = E × a
observationWeight = R / (R + priorElectorate)    se R < E
observationWeight = 1                          se R = E
```

O peso cresce com o eleitorado representado: 0% → 0; 5% → 0,50; 20% → 0,80; 50% → aproximadamente 0,9091. A quantidade de seções não substitui o eleitorado como peso.

Com votos reais dos dois candidatos:

```text
observedShare13 = currentVotes13 / (currentVotes13 + currentVotes22)
projectedShare13 = weight × observedShare13 + (1-weight) × baselineShare13
projectedShare22 = 1 - projectedShare13
```

Sem votos, o share é o baseline. Taxa de votos válidos:

```text
observedValidRate = V / R                        se R > 0
projectedValidRate = weight × observedValidRate + (1-weight) × firstRoundValidRate
projectedValidVotes = projectedValidRate × E
projectedVotes13 = projectedValidVotes × projectedShare13
projectedVotes22 = projectedValidVotes - projectedVotes13
```

Sem eleitorado totalizado, a taxa e os shares são exatamente os do baseline. Havendo eleitorado representado mas zero votos válidos, a taxa observada zero participa do blending; o share sem observação continua no prior.

### Restrições físicas

Eleitorado totalizado ausente ou fora de `[0,E]` torna a UF indisponível para modelagem. Não corrigimos o fato oficial.

A estimativa de votos válidos é limitada a `[V, V + (E-R)]`. Assim, nunca apaga válidos já computados nem pressupõe mais votos novos que eleitores pendentes. O share também respeita os limites impostos pelos votos já computados de cada candidato; qualquer limitação é informada no diagnóstico estadual. Quantidades e frações usam `BigDecimal` com `DECIMAL128`, sem dependência de `double` no motor. O resíduo de votos estimados fica no segundo candidato para manter a soma exata.

Se, em apuração parcial, a soma dos votos dos dois candidatos diferir dos válidos, a UF é marcada indisponível para projeção. Não redistribuímos votos anulados/sub judice para fazer caber a fórmula. O resultado oficial continua exposto normalmente.

### Convergência obrigatória

Em `R=E`, **bypass explícito** do shrinkage: válidos e votos de cada candidato são os oficiais exatos; share observado também é exato. Em 100%, o peso passa a 1, e não `1/1,05`. Isso introduz uma pequena descontinuidade em relação ao limite da fórmula parcial, decisão explícita para respeitar a exigência de convergência. A finalização de uma UF não é inferida como finalização nacional.

No `tf=s` nacional, a resposta final usa diretamente os votos e percentuais nacionais oficiais, sem prior nem chamada própria de eleição. Isso garante convergência nacional incluindo votos do exterior, mesmo se o último arquivo estadual coletado estiver atrasado. Não se extrapola o percentual nacional **parcial** em nenhum momento.

## Agregação nacional e exterior

Antes da finalização oficial:

```text
projectedNationalVotes[c] = soma dos projectedStateVotes[c] das 27 UFs + votos oficialmente observados de ZZ
projectedNationalPercentage[c] = projectedNationalVotes[c] / soma dos votos projetados dos candidatos × 100
```

UFs maiores pesam por seus volumes estimados, não por média simples de percentuais. O progresso nacional usado no gate é `e.est/e.te` do snapshot nacional, independente do share nacional parcial.

**Exterior observado, sem baseline/modelo próprio:** o endpoint oficial `https://resultados.tse.jus.br/oficial/ele2026/6258/dados/zz/zz-c0001-e006258-u.json` foi confirmado por HTTP 200 em 23/10/2026. O arquivo é EA20, `tpabr=uf`, `cdabr=zz`, eleição 6258, turno 2, fase oficial, com os candidatos 13 e 22. A fixture original está versionada. Não inferimos o exterior por diferença entre arquivos.

O builder centraliza a URL comprovada. A coleta usa o mesmo cliente, cache, timeouts, backoff, snapshots e orçamento global da Fase 3, a cada 30s. `ZZ` permanece fora de `BrazilState`, da lista de 27 UFs e das cinco regiões; é somente uma abrangência técnica UF do TSE. Os baselines continuam sendo exatamente 28 (Brasil + 27 UFs), sem carga adicional do primeiro turno.

Na soma parcial, acrescentamos **somente `vap` oficialmente observado por candidato em ZZ**, sem extrapolar comparecimento, share ou votos restantes. Sem arquivo ou sem votos, contribuição zero, sem prejudicar `coverageComplete`/`projectionIncomplete` das 27 UFs. `overseas.available`, `overseas.incorporated`, geração, candidatos/votos observados e nota explicativa ficam no response. Os cortes de geração/captura do replay também se aplicam a ZZ. No `tf=s` nacional, copiamos o resultado oficial nacional sem adicionar ZZ novamente. A ausência de dado de ZZ não altera os gates geográficos.

O sinal de incorporação descreve o cálculo interno e continua exposto quando o gate impede a publicação dos percentuais. A cobertura geográfica é sempre das 27 UFs; os votos ainda não observados do exterior não são modelados.

## Falta de baseline ou dados

- Sem baseline e sem observação suficiente, UF indisponível; `projectionIncomplete=true`, `coverageComplete=false`, publicação bloqueada.
- Sem baseline, mas com pelo menos 80% do eleitorado representado e share observado válido, usamos somente a taxa/share observados (`weight=1`) e registramos aviso. Limiar configurável.
- Em 100% estadual, não é necessário baseline para copiar os votos observados.
- UF sem snapshot ou com dados inválidos nunca é tratada como zero. `missingOfficialStates` e `missingBaselineStates` ficam no response.
- Baseline ausente é informado mesmo quando o fallback permite cobertura completa.
- Os estados podem estar em gerações diferentes. O diagnóstico expõe a geração de cada UF e do baseline para auditoria. Ausência de atualização dentro de uma UF é uma limitação; não buscamos seções específicas.

## Gate, estados e desativação

Publicação exige **simultaneamente** progresso nacional ≥5%, ≥10 UFs com eleitorado representado e votos válidos, dados em ≥4 das 5 regiões brasileiras, ≥3 dos seis estados-chave (SP, MG, RJ, BA, PR, RS), 27 UFs modeláveis e fonte nacional disponível/recente. Os gates numéricos e geográficos são cumulativos para ACTIVE e HIGH_COVERAGE; nenhum é substituído por alta cobertura nacional. `geography` informa regiões/estados-chave com dados e se os critérios foram satisfeitos. O mapeamento estático e exaustivo é `BrazilRegion.of(BrazilState)`, e o conjunto de estados-chave é imutável. FINAL continua dependendo exclusivamente da totalização oficial nacional.

Antes do gate, a API pública omite `results`, `spread` e diagnósticos com projeção numérica; `publishable=false`. O motor puro pode calcular internamente para testes.

- `NOT_STARTED`: nacional válido sem contagem e nenhuma UF com votos.
- `INSUFFICIENT_DATA`: gate não atingido, cobertura incompleta, fonte nacional desatualizada/indisponível ou projeção desabilitada.
- `ACTIVE`: gate atingido.
- `HIGH_COVERAGE`: gate atingido e progresso nacional ≥80%, configurável.
- `FINAL`: totalização final nacional oficial `tf=s`, sem inferir por percentuais ou cobertura.

Cobertura descritiva: `LOW` abaixo de 20%; `MEDIUM` de 20% até menos de 60%; `HIGH` a partir de 60%; `FINAL` apenas no encerramento oficial. Não são probabilidades de acerto nem intervalos de confiança. Em desatualização, a fonte e seus snapshots permanecem intactos, mas a projeção não é apresentada como atual. O histórico recebido anteriormente continua identificado pelo horário.

Configuração tipada e validada em `ProjectionProperties`:

```yaml
election:
  projection:
    enabled: true
    prior-equivalent-progress: 0.05
    minimum-national-electorate-progress: 0.05
    minimum-states-with-data: 10
    minimum-regions-with-data: 4
    minimum-key-states-with-data: 3
    high-coverage-progress: 0.80
    minimum-observed-progress-without-baseline: 0.80
```

Todos têm overrides `ELECTION_PROJECTION_*`. `enabled=false` mantém endpoints respondendo, sinaliza a desativação e retorna histórico de projeção vazio; oficial, ritmo e pendências continuam funcionando. Não agenda snapshots de projeção quando desabilitado. Testes mantêm `tse.results.scheduling-enabled=false` por padrão.

## Ritmo 5/15/30 minutos

Janelas terminam no instante real do cálculo. Ordenamos snapshots nacionais por geração, captura e UUID. Deltas somente de pares consecutivos da mesma eleição/turno/abrangência/conjunto de candidatos, com geração crescente:

```text
deltaValidVotes = current.validVotes - previous.validVotes
deltaCandidateVotes[c] = current.votes[c] - previous.votes[c]
newVotesShare[c] = soma dos deltas do candidato / soma dos deltas de todos os candidatos elegíveis
```

Descartamos delta válido ≤0, delta negativo de candidato, regressão de seções, geração duplicada ou par incompatível. Não construímos ponte artificial sobre intervalo rejeitado: o par seguinte usa seu predecessor real. Percentuais dos votos novos podem ter denominador diferente do delta de válidos; ambos ficam identificados.

Só somamos intervalos inteiramente dentro da janela; um par que cruza a borda é excluído, sem rateio ou interpolação. Horários efetivamente usados, limites solicitados, contagem de intervalos usados/ignorados e motivo de indisponibilidade ficam no response. Um único snapshot, falta de coleta ou deltas inválidos retornam `available=false`, `newValidVotes=null`, lista de shares vazia — nunca um zero inventado.

## Eleitorado pendente

Por UF: `remainingElectorate = e.te - e.est`; representado = `e.est/e.te`. Ordenação decrescente, top 5 na interface e expansão para todas. Totais ausentes/inválidos permanecem nulos. Inclui resultado parcial oficial em campo próprio. Eleitorado pendente **não equivale a votos válidos futuros**, pois inclui abstenções/brancos/nulos ainda desconhecidos.

## Persistência, consultas e replay

V11 cria `election_baselines` (unique eleição/turno/escopo) e `projection_snapshots` (JSONB auditável, versão, geração, cobertura, captura). Baselines fazem upsert transacional por abrangência; uma falha não reverte as demais.

A projeção carrega últimos snapshots nacionais/estaduais em **uma consulta `DISTINCT ON`**, e baselines em outra consulta. Não executa 27 consultas e não chama o TSE. O ritmo lê somente os 30 minutos relevantes mais um predecessor para avaliar a borda.

Um scheduler interno verifica a cada intervalo nacional (5s padrão) e só salva projeção publicável ACTIVE/HIGH_COVERAGE/FINAL. GETs não persistem snapshots. `INSERT ON CONFLICT DO NOTHING` deduplica. Logs INFO somente quando um novo snapshot é salvo, com modelo, cobertura e diferença; não despejam payload.

**Geração oficial é um vetor de entradas**, hash dos IDs dos snapshots nacionais e estaduais efetivamente usados, combinado com `modelVersion` na chave única. Isso é deliberado: uma UF pode mudar enquanto o nacional retorna 304; deduplicar só pelo timestamp do nacional perderia essa alteração territorial. Mesma entrada e versão não duplica. Troca de parâmetros ou baseline sem mudança oficial não cria snapshot; cada payload inclui os parâmetros/fontes utilizados. Alterações materiais do algoritmo exigem nova versão. Histórico retorna registros cronológicos com limite e corte de data.

Para impedir look-ahead bias, consultas exigem **geração e captura ≤ instante de corte**. `ProjectionReplay.replay` recebe snapshots artificiais/fixtures com seus tempos de captura, baselines e instantes de reprodução. Seleciona apenas o que já estava disponível naquele instante. Testes garantem que um arquivo recebido depois não é usado no passado, mesmo quando sua geração é anterior.

O replay é uma ferramenta Java pura, sem HTTP nem escrita no banco. Pode ser usado em testes fornecendo `List<AnalyticsInputStore.Recorded>`, `List<BaselineData>` e `List<Instant>`. A tabela de baseline guarda a versão mais recente por UF, não todo seu histórico; para reproduzir revisões antigas é necessário fornecer ao replay os baselines históricos capturados. Não considerar uma validação com fixtures artificiais como evidência de qualidade preditiva real.

## Endpoints

| Método | Endpoint | Função |
|---|---|---|
| POST | `/api/admin/election-baselines/first-round/sync` | sincroniza apenas 28 escopos |
| GET | `/api/admin/election-baselines/first-round` | audita baseline persistido |
| GET | `/api/election/president/trend` | ritmo 5/15/30min |
| GET | `/api/election/president/remaining` | ranking de eleitorado pendente |
| GET | `/api/election/president/projection` | projeção/estado de publicação |
| GET | `/api/election/president/projection/history?from=&to=&limit=` | histórico cronológico (padrão 500; 1–2000) |

## Validação e decisões para revisão humana

Em 23/10/2026, a sincronização real respondeu **28 sucessos, zero falhas**. O segundo turno ainda estava em pré-apuração: nenhuma projeção foi publicada. A interface exibe cards separados, espera pelo gate, ritmo indisponível e ranking de eleitorado. Cenários parciais/finais e replay são testados com fixtures artificiais identificadas, sem inserir votos sintéticos no banco local real.

Precisam de avaliação empírica/humana antes de uso editorial: prior 5%, gate 5%/10 UFs/4 regiões/3 estados-chave, fallback sem baseline em 80%, incorporação somente observada do exterior sem modelar seus votos restantes, descontinuidade explícita na convergência e sensibilidade à ordem de totalização. Não houve backtest com uma série histórica completa real do segundo turno; não alegamos calibração ou precisão preditiva. O modelo não conhece quais seções específicas restam dentro de cada UF e pode sofrer viés territorial intrastadual, mudanças de comparecimento e transferência de votos entre turnos.
