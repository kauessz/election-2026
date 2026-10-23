# Freeze pré-eleição

PreElectionFreeze é o registro imutável de um modelo pré-eleição. Não é resultado oficial nem chamada de vencedor. O cadastro de pesquisas continua funcionando e os quatro agregados live continuam disponíveis. A comparação com a urna usa exclusivamente o freeze ativo.

## Criação e revisão

1. Preparar arquivo de pesquisas com fontes verificáveis, sem inventar números/registro.
2. Executar **dry run**, revisar normalizações/duplicatas e confirmar importação real em `/admin/importacao`.
3. Importar histórico do primeiro turno com fonte (também dry run antes de gravar); usar a ferramenta de cálculo para conferir os erros quando os insumos estiverem disponíveis.
4. Rodar data quality, corrigir ERRORs e revisar WARNINGs.
5. Revisar `/api/aggregates/audit` e os quatro modelos na tela `/admin/freeze`.
6. Gerar snapshots administrativos em `POST /api/admin/aggregates/snapshot` conforme API existente.
7. Em `/admin/freeze`, informar label, notas e commit opcional; marcar confirmação e confirmar o diálogo. A ação envia `POST /api/admin/pre-election-freezes`.
8. Registrar o commit Git do código/configuração aprovado no controle operacional (esta implementação não faz commit). Se o hash já existir, fornecê-lo na criação. O freeze não recebe patches posteriores; hash omitido deve ser associado externamente ao ID.
9. Durante a apuração, não alterar o modelo aprovado. Novos cadastros alimentam somente live, nunca o freeze.

A sequência é apropriada para a revisão de sábado antes do segundo turno. A API reexecuta a qualidade e calcula os quatro modelos em uma transação SERIALIZABLE. Importações concorrentes compartilham um lock; conflitos concorrentes exigem nova revisão/tentativa. ERROR ou agregado vazio impede criação. A API não calcula nem publica um freeze automaticamente durante deploy/build.

## Dados preservados

V12 cria `pre_election_freezes`: UUID id, label, frozenAt, gitCommit opcional, aggregationConfiguration JSONB, pollIds JSONB, pollsterPerformanceIds JSONB, aggregateResults JSONB, notes e active. Guarda ainda `composition` JSONB com as pesquisas completas, performances utilizadas (fontes/verifiedAt/notas) e o mapeamento UUID→número do candidato naquele instante. IDs de pesquisa são a união exata das contribuições dos quatro modelos, não todo o cadastro. Histórico preservado é apenas a eleição/turno configurados dos institutos considerados, com spreadError usado na calibração. Sem histórico, o fator continua neutro e há aviso.

GET `/api/pre-election-freeze` retorna o ativo; 204 quando ausente. Há um único freeze ativo, protegido por índice único. **Não há endpoint de update, delete, reativação ou substituição.** Trigger PostgreSQL rejeita UPDATE/DELETE, inclusive alteração de active. Não existe recálculo posterior: o GET lê JSONB salvo. Cadastro novo, alteração/exclusão de pesquisa/histórico, troca de configuração e nomes/números de candidatos não alteram o registro. Procedimentos excepcionais de banco fora da aplicação não fazem parte do fluxo suportado.

A home destaca “Projeção pré-eleição congelada”, data/hora Brasília, modelo calibrado e os quatro agregados; mostra composição/fontes e link para metodologia. Os agregados live ficam explicitamente identificados. `/admin/freeze` mostra a revisão atual enquanto não existe freeze e o conteúdo imutável depois que existe.

## Comparação com a urna

GET `/api/election/president/pre-election-comparison` nunca recalcula o agregado congelado e não inicia coleta TSE. Retorna NO_FREEZE sem modelo; NOT_STARTED com freeze quando não há votos oficiais válidos; PARTIAL com currentDifference por candidato e no spread; FINAL com finalError apenas quando o resultado nacional tem finalTotalization do TSE. O outro campo fica null. A diferença é absoluta; o spread usa sempre o mesmo par ordenado, para preservar o sinal antes do módulo (não apenas subtração de magnitudes de lideranças).

O vínculo com candidatos usa o mapeamento congelado de números. Falta de candidato compatível gera warning e omite diferença correspondente; não aproxima nem fabrica correspondência. O resultado oficial corrente vem da integração/cache da Fase 3; se a fonte estiver STALE/UNAVAILABLE, a resposta mantém seu status e warning, sem fingir atualização. Com mais de dois candidatos não se escolhe um par arbitrário para spread; a comparação individual permanece disponível.

A ferramenta de cálculo histórico é descrita em [data-import.md](data-import.md). Valores de fontes informadas pelo operador exigem revisão humana; verifiedAt registra o recebimento auditado, sem certificação automática do documento externo.
