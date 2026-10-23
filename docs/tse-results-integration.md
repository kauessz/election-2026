# Integração oficial TSE — Fase 3

## Fonte e referências verificadas

Divulgação presidencial do segundo turno de 2026: eleição **6258**, cargo **0001**, ambiente **oficial**. Consulta manual em 23/10/2026. Não há previsão, ajuste estatístico dos votos, nem inferência de vencedor.

Fontes primárias:

- [Informações técnicas de divulgação do TSE](https://www.tse.jus.br/eleicoes/informacoes-tecnicas-sobre-a-divulgacao-de-resultados)
- [EA20 — resultado unificado (PDF oficial)](https://www.tse.jus.br/eleicoes/eleicoes-2026-content/arquivos/divulgacao-de-resultados/tse-ea20-arquivo-de-resultado-unificado/@@download/file), versão interna 13/10/2026, 22 páginas.
- [Resolução TSE 23.751/2026, art. 282 — horário de Brasília na divulgação](https://www.tse.jus.br/legislacao/compilada/res/2026/resolucao-no-23-751-de-26-de-fevereiro-de-2026)
- [EA12 — configuração de municípios](https://www.tse.jus.br/eleicoes/eleicoes-2026-content/arquivos/divulgacao-de-resultados/tse-ea12-arquivo-de-configuracao-de-municipios)
- [EA14 — acompanhamento Brasil](https://www.tse.jus.br/eleicoes/eleicoes-2026-content/arquivos/divulgacao-de-resultados/tse-ea14-arquivo-de-acompanhamento-brasil/@@download/file)
- [EA15 — acompanhamento UF](https://www.tse.jus.br/eleicoes/eleicoes-2026-content/arquivos/divulgacao-de-resultados/tse-ea15-arquivo-de-acompanhamento-uf)

O limite divulgado é 100 requisições/s por IP. O TSE também alerta contra 404 repetidos; excesso pode bloquear o IP por dez minutos, renovados por novas tentativas. O consumidor deste projeto usa, por padrão, **no máximo 2 requisições/s compartilhadas entre todos os escopos**, sem retries imediatos. Múltiplas instâncias compartilham o limite do IP: nesta fase, operar **uma instância coletora**.

## Arquitetura

`TseResultsProperties` → `TseUrlBuilder` → `TseResultsClient` (RestClient MVC) → DTOs `tse.dto.raw` → `TseResultParser` → `OfficialResultsService` → `OfficialSnapshotStore`/PostgreSQL → API → polling frontend.

Somente o domínio `tse` conhece nomes compactos. Nenhum módulo de pesquisas/agregação participa da transformação dos resultados. O vínculo local usa exclusivamente `Candidate.ballotNumber`; falta ou ambiguidade mantém o candidato oficial, com `localCandidateId=null`, aviso e log. Nome e percentual publicados nunca são substituídos pelos do cadastro local.

## URLs centralizadas

Base padrão: `https://resultados.tse.jus.br`. Prefixo: `/oficial/ele2026/6258`. Eleição com seis dígitos nos arquivos; cargo com quatro; município com cinco, zona com quatro. Códigos municipais são strings, conservando zeros.

Consumidos e confirmados por HTTP 200 real:

| Escopo | Caminho após o prefixo |
|---|---|
| Brasil | `/dados/br/br-c0001-e006258-u.json` |
| UF (exemplo AC) | `/dados/ac/ac-c0001-e006258-u.json` |
| Município (Acrelândia) | `/dados/ac/ac01120-c0001-e006258-u.json` |
| Catálogo EA12 | `/config/mun-e006258-cm.json` |

Zona: builder preparado para `/dados/{uf}/{uf}{municipio}-z{zona}-c0001-e006258-u.json`, conforme seção 2 do EA20. Só aceita zona constante do EA12 daquele município. Enum/contrato preparados; **não há coleta nem endpoint de zona nesta fase**. Não foi validada uma resposta real de zona. Não há varredura de códigos nem consulta de seção/BU.

EA14 identifica mudanças nas UFs e EA15 nos municípios. **TODO explícito:** validar fixtures e semântica reais desses arquivos, inclusive sua relação temporal com EA20, antes de implementar `EA14 → EA15 → EA20`. Não consumimos arquivos de acompanhamento nem inventamos seus campos. A atualização gradual de UFs e municípios sob demanda é a alternativa desta fase. A documentação alerta que arquivos são gerados em paralelo e podem ter atrasos no CDN.

## Campos interpretados do EA20

| Origem | Campo normalizado / significado |
|---|---|
| `ele`, `t`, `f` | eleição, turno e fase; exigimos eleição configurada, turno 2 e fase oficial `o` |
| `tpabr`, `cdabr` | tipo e código; devem corresponder ao escopo solicitado |
| `dg`, `hg` | `generatedAt`, convertido do horário de Brasília para `Instant` |
| `idg` | identificador de geração preservado; **não ordenar numericamente**, não é garantido crescente |
| `tf` | `finalTotalization=true` somente para `s` |
| `carg[].cd`, `agr[].par[].cand[]` | somente candidatos do cargo presidencial configurado |
| `n`, `sqcand`, `nm`, `nmu` | número eleitoral, sequência TSE, nome completo e nome de urna |
| `vap`, `pvap`, `pvapn` | votos computados do candidato, percentual de exibição e percentual de maior precisão |
| `s.ts`, `s.st`, `s.pst`, `s.snt` | seções totais, totalizadas, percentual e não totalizadas |
| `e.te`, `e.est`, `e.pest` | eleitorado total, eleitorado das seções totalizadas e seu percentual |
| `e.a`, `e.pa`, `e.c` | abstenção, percentual de abstenção e comparecimento |
| `v.tv`, `v.vv`, `v.vb` | votos totais, válidos e brancos |
| `v.tvn` | total de votos nulos, **incluindo técnicos** (`vn + vnt`) |
| `v.vnom`, `v.vvc` | votos nominais válidos e votos a votáveis concorrentes |

**Base dos percentuais:** o EA20 define `pvap`/`pvapn` sobre votos a votáveis concorrentes (`vvc`), que podem incluir anulados e anulados sub judice. Não dividir `vap` por `vv` para substituir o percentual oficial. O gráfico explica essa diferença em relação aos votos válidos; os totais válidos continuam em campo próprio. O percentual de maior precisão é preservado sem substituir o valor de exibição. Diferenças em votos/p.p. são derivadas e identificadas como tal.

`TseNumbers` concentra vírgula/ponto decimal e validações. Percentuais são `BigDecimal`; contagens são `Long`, sem conversão para `double`. Campos opcionais vazios/ausentes permanecem `null`, nunca viram zero. Campos essenciais (eleição, escopo, geração, seções totais/totalizadas, eleitorado total, votos totais/válidos, número/votos dos candidatos) ausentes ou inválidos rejeitam o arquivo inteiro. Campos desconhecidos são ignorados para tolerar evolução. A API mantém o último arquivo válido após rejeição. Não corrigimos somas ou percentuais oficiais.

## Cache HTTP e falhas

Um orçamento global serializa chamadas, com conexão 3s e leitura 5s por padrão, `User-Agent: Election2026/0.3 (official-results consumer)` e redirects desabilitados. Cada recurso conserva ETag e Last-Modified em memória; a próxima consulta envia `If-None-Match` e `If-Modified-Since`.

- **200:** parsear, validar, vincular, persistir se mudou, então aceitar os validadores HTTP. Arquivo inválido não envenena o cache de ETag.
- **304:** sucesso de contato, sem baixar/parsear o corpo, alterar o resultado/geração ou inserir snapshot. Atualiza apenas a hora de contato. Não há notificações de alteração/SSE.
- **404:** espera mínima de dez minutos para o recurso conhecido; não tenta códigos alternativos.
- **429:** pausa global de pelo menos dez minutos, respeitando `Retry-After` maior (segundos ou data HTTP).
- **5xx, outros HTTP e transporte:** backoff por recurso, 5s exponencial até 5min. Nenhum retry no mesmo ciclo.
- **Parsing/armazenamento:** preserva cache válido, registra falha e aplica espera. Erros não encerram a aplicação.

O cache HTTP não é durável. Após reiniciar, os resultados são recuperados do banco e a primeira coleta baixa 200 para reconstruir os validadores. Isso evita 304 sem corpo quando não existe cache válido em memória.

## Atualização e disponibilidade

- Nacional: `fixedDelay` de 5s depois da conclusão da coleta.
- UFs: uma UF conhecida por tick de 1s, ciclo de aproximadamente 27s **mais a latência**. Pode aumentar em falhas/backoff; não se promete um intervalo rígido por UF. Os GETs nacional/UFs só leem cache/banco.
- Municípios: GET valida o código no catálogo persistente e agenda coleta sob demanda, no máximo uma a cada 30s por município. Executor único, fila limitada a 32 trabalhos, sem varrer todos os municípios. Resposta pode trazer `refreshing=true` e nenhum resultado enquanto carrega.
- EA12: carga inicial e renovação diária após sucesso. Em falha, o scheduler verifica a cada minuto, respeitando backoff. Substituição do catálogo é transacional; mantém o anterior se incompleto/inválido. Inclui 27 UFs; `ZZ` (exterior) é excluído da busca municipal brasileira. Não confundir código TSE com IBGE.
- Backend: três threads de scheduler, mas um orçamento HTTP global. Acesso ao estado corrente é sincronizado por escopo; requisições lentas podem atrasar a leitura daquele escopo pelo timeout configurado.
- Frontend: polling nacional/UF 5s, municípios 10s, estados/histórico 15s; requisições não se sobrepõem. Falha da API conserva dados já exibidos e mostra aviso. Busca global usa catálogo em cache no cliente; lista municipal da UF é consultada a cada minuto na API interna.

Estados da resposta (`result` contém os fatos oficiais; `status` descreve a disponibilidade):

| Estado | Regra |
|---|---|
| `UNAVAILABLE` | nunca houve arquivo válido; `result=null`, não fabricar totais |
| `NOT_STARTED` | arquivo válido, zero seções totalizadas e zero votos totais, contato recente |
| `FRESH` | contagem em andamento com geração e contato dentro de `stale-threshold` (2min padrão) |
| `STALE` | último contato válido vencido; para arquivo parcial, também geração vencida, mesmo recebendo 304 |
| `FINAL` | `tf=s` (totalização final formal, distinta de `and=f`) documentado pelo EA20 e contato recente; nunca inferido só por 100% das seções |

Pré-apuração e finalização podem ter geração antiga legitimamente: usam idade do contato para disponibilidade. Durante indisponibilidade além do limite, até o resultado final é `STALE`, mantendo `result.finalTotalization=true`. `lastSuccessfulUpdate` é o último **contato/validação** bem-sucedido, não a hora de geração. Recuperação após reinício usa a captura do último snapshot como limite conservador de contato, até confirmar com o TSE. Uma geração mais antiga ou mais de cinco minutos no futuro é rejeitada. Nenhum número de votos é apagado por falha.

## Persistência (V10)

`election_result_snapshots`: entidade `ElectionResultSnapshot`, contagens numéricas, percentual de seções decimal, candidatos e resultado normalizado completo em JSONB, ETag de origem, geração/captura/status e hash SHA-256 dos fatos oficiais. Não armazena o JSON cru integral com campos não interpretados.

Índice por eleição/turno/escopo/geração/captura. Unique por eleição/turno/escopo/geração/hash impede duplicata lógica, permitindo correção factual com a mesma geração. O serviço compara fatos (seções, eleitorado, votos, candidatos e finalização); troca de ETag/ID de geração/data ou vínculo local sem mudança factual não insere snapshot. `INSERT ON CONFLICT DO NOTHING` protege repetição. Histórico ordenado por geração e captura, retornado cronologicamente, limitado aos pontos mais recentes do intervalo. Lacunas significam ausência de coleta, não interpolação.

`election_municipalities`: catálogo durável por eleição/UF/código de cinco dígitos, nome e zonas oficiais JSONB. Continua disponível sem rede e após reinício.

O histórico nacional não agrega totais estaduais: o nacional pode incluir exterior e os arquivos territoriais podem estar em instantes diferentes.

## API interna

Todos os caminhos abaixo começam com `/api/election`:

| GET | Conteúdo |
|---|---|
| `/president` | envelope `source/official/status/lastSuccessfulUpdate/refreshing/scope/result` |
| `/president/states` | 27 envelopes, um por UF, inclusive indisponíveis identificados por escopo |
| `/president/states/{uf}` | resultado estadual em cache |
| `/president/municipalities` | catálogo brasileiro para busca client-side, sem HTTP ao TSE |
| `/president/states/{uf}/municipalities` | catálogo oficial da UF |
| `/president/states/{uf}/municipalities/{municipalityCode}` | resultado municipal em cache; agenda coleta autorizada pelo EA12 |
| `/president/history?from=&to=&limit=` | resultados nacionais normalizados, ordem cronológica; datas ISO-8601 com fuso; limite 1–2000 (padrão 500) |
| `/source-status` | saúde nacional, URL base pública, último HTTP/contato, falhas consecutivas e contadores globais |

Sem catálogo válido, os endpoints de catálogo retornam 503; código desconhecido em catálogo carregado retorna 404, sem consultar o TSE. UF/código malformado e intervalo/limite inválidos retornam 400. Os endpoints não permitem URLs externas arbitrárias.

`/source-status.metrics`: requests, responses200, responses304, errors, lastRequestAt, lastSuccessfulUpdate (HTTP), lastHttpStatus. A saúde nacional usa somente seu último resultado **válido**; métricas globais incluem todos os recursos e parsing rejeitado. Contadores reiniciam com o processo. Logs INFO em mudança factual incluem abrangência, geração, seções e votos; 304 em DEBUG; falhas em WARN, sem despejar payload completo.

## Configuração e testes

Propriedades `tse.results` em `application.yaml`, com overrides `TSE_RESULTS_*` explícitos. `president-office-code` é string para preservar `0001`. Para suspender toda coleta automática e sob demanda: `TSE_RESULTS_SCHEDULING_ENABLED=false`.

`src/test/resources/application.properties` desabilita scheduling por padrão em **todos os contextos de teste**. Clientes de teste usam somente servidor HTTP local. Nenhum teste depende do TSE, de DNS externo ou de disponibilidade eleitoral. Fixtures reais têm URLs, data e hashes em `api/src/test/resources/tse/README.md`; o cenário parcial é rotulado sintético. Cobertura inclui parsing/tipos/ausências, escopos, mapeamento, 200/304/404/429/500/timeout, validadores condicionais, preservação/offline, snapshots/deduplicação/ordem e contratos API com PostgreSQL Testcontainers.

Validação manual em 23/10/2026: PostgreSQL local saudável, Flyway V10, API real retornando `NOT_STARTED`, dois candidatos oficiais (13 e 22), zero seções/votos e metadados oficiais preservados. `/apuracao` conferido no navegador: mensagem de espera e percentuais principais `—`; nenhum resultado eleitoral fictício. Não inserir fixtures sintéticas no banco da aplicação para demonstração.

## Pendências delimitadas

- Otimização seletiva EA14/EA15 depende de contratos e fixtures reais ainda não validados; fallback territorial conservador está implementado.
- Zona tem contrato/builder documentados/testados; coleta e interface de zona ficam para a próxima fase.
- Não interpretar demais campos compactos sem necessidade/documentação. Nenhum campo atualmente exposto permanece com semântica presumida; não usar `and`, `md` ou `idg` para decidir vencedor/finalização/ordem.
- Não há coordenação distribuída de rate limit nem retenção automática/expurgo de snapshots nesta fase. Uma instância coletora e PostgreSQL são os componentes operacionais previstos.

### Complemento conservador da Fase 4 — exterior

O EA20 `/oficial/ele2026/6258/dados/zz/zz-c0001-e006258-u.json` foi confirmado por HTTP 200 em 23/10/2026: tipo técnico `uf`, código `zz`, presidencial do segundo turno oficial. É coletado a cada 30s pelo mesmo cliente/orçamento global e persistido como STATE/ZZ, separado das 27 UFs brasileiras. A projeção incorpora apenas votos observados; não há baseline nem extrapolação do exterior. A ausência de ZZ não reduz a cobertura das UFs.
