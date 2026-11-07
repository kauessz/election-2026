# Gate 06B-J3A — Guardrails de CI isolados da release eleitoral

**SECURITY_UNVERIFIED · RELEASE_BLOCKED · NO-GO**

Preparação exclusivamente local em 07/11/2026. Branch:
`fix/ci-guardrails-master-only`. Base e HEAD, sem novo commit:
`efde0f08b8224a0b8fae29bc38e7b4105e324225`.
Fonte seletiva do workflow e teste:
`7c53e435f0e42e3e0db5b50e13ff9a4eba074915`.
A branch `fix/elections-ci-release-guardrails` foi preservada nesse commit,
com seus 40 commits posteriores à base. Não houve cherry-pick integral.

## Escopo independente

Este patch contém somente:

- `.github/workflows/api-image.yml`;
- `web/tests/release-pipeline.test.mjs`;
- este documento.

Não incorpora código funcional, dependências, lockfiles, Dockerfile, migrations,
datasets ou funcionalidades regionais. O teste usa módulos nativos do Node e
js-yaml, já presente no lockfile da base (4.3.2). Não importa código regional.
Este documento é autossuficiente; não depende de relatórios ou matrizes ausentes
na master conhecida. As 142 decisões humanas da cadeia de auditoria original
continuam pendentes; nenhuma vulnerabilidade recebe aceite por esta correção.

## Problema e correção

Na base conhecida, o único workflow contém um job publish: push em master com
alterações de API/workflow ou workflow_dispatch pode construir e publicar uma
imagem no GHCR. O job solicita packages: write e atualiza tags SHA/master.
Não há job de validação ou aprovação de Environment declarado nessa revisão.
Essa descrição do YAML não comprova o estado atual das configurações externas.

O patch remove completamente o publicador, login, comandos de push de imagem e
permissão de escrita em pacotes. Existe somente o job validate, com contents: read.
Checkout usa o SHA do evento e persist-credentials: false. Nenhum secret ou OIDC é
solicitado. O workflow não produz nem promove OCI e não atualiza aliases GHCR.

A validação usa Java 21, Node 24, Maven verify e os scripts existentes de testes,
lint, TypeScript e build frontend. A URL frontend de CI aponta para loopback.
Os comandos fazem parte da definição de CI; sua presença não significa que todos
foram executados nesta preparação. Não há cache compartilhado declarado.

## Eventos e referências

| Contexto | Resultado previsto no YAML local |
|---|---|
| Push canônico em master alterando API, web ou o workflow | Validação somente |
| Dispatch canônico em master protegida, expected_sha igual ao SHA integral do evento | Validação somente |
| Dispatch em branch diferente, tag ou fork | Job negado |
| Dispatch master sem proteção ou com SHA ausente/divergente/abreviado | Job negado |
| PR, pull_request_target, workflow_run | Sem trigger; condição do job também nega |
| Qualquer contexto | Nenhum job de publicação |

O input expected_sha é comparado no if do job e não interpolado em shell.
github.ref_protected não representa aprovação humana. Não há validação automática
de PR declarada: se um ruleset exigir checks de PR que este workflow não produz,
será necessário resolver essa compatibilidade em escopo autorizado antes do merge.

## Estado remoto e integrações

O responsável informou que o workflow remoto foi desativado e o ruleset
protect-master-elections está ativo, direcionado à branch padrão, com bypass vazio.
Essas informações não foram reconsultadas nesta preparação. As regras exatas,
incluindo exigência de PR/checks, e o estado efetivo de habilitação do workflow
continuam sem nova verificação. Renomear o workflow mantendo seu caminho não
comprova que ele esteja habilitado, desabilitado ou aprovado remotamente.

O patch local não altera o GitHub. Nenhum fetch, push, PR, dispatch ou consulta
remota foi executado. Não se deve habilitar o publicador antigo para validar este
patch. Se uma futura execução do YAML revisado for autorizada, ela só validará.
Refs antigas com código publicador, políticas de Actions, SHA pinning, acesso ao
GHCR e revisores de futura promoção exigem confirmação externa específica.
Actions permanecem em tags major; isso é um risco residual de supply chain.

Integrações Git da Vercel podem criar previews em pushes/PRs e deployment em master,
mesmo quando o patch só contém CI, teste e documentação. O YAML não controla esses
gatilhos. Nesta etapa não há evidência nova da configuração Vercel. Antes de envio
ou merge, o responsável deve confirmar um procedimento que evite publicação não
autorizada. Não há autorização de deploy ou preview neste gate.

## Validação e limites

O teste standalone cobre permissões, ausência de publicador, checkout exato,
ausência de interpolação de contexto nos scripts e 15 cenários de eventos/refs.
Sua simulação cobre o subconjunto booleano atual; não emula integralmente coerções
e comparações do GitHub Actions. Parsing YAML e Bash -n não executam comandos de CI
nem comprovam configurações externas.

Resultados nesta branch:

| Verificação local | Resultado |
|---|---|
| Standalone `node --test tests/release-pipeline.test.mjs` em web | 17 aprovados |
| `npm test` em web, após a adaptação final | 108 aprovados, zero falhas/skips; 7,73 s |
| Lint do arquivo de teste e `npm run lint` completo em web | Exit 0 |
| Parsing YAML com PyYAML | Aprovado |
| Git Bash -n sobre os dois scripts run do YAML | Aprovado; scripts não executados |
| Permissões, gatilhos e 15 cenários positivos/negativos | Aprovados pelo teste standalone |
| Allowlist do índice | Exatamente os três arquivos autorizados; nenhuma alteração funcional |
| `git diff --check` e `git diff --cached --check` | Aprovados |

A primeira execução standalone teve três timeouts com limite VM de 100 ms.
O teste foi adaptado para 5.000 ms de timeout defensivo, sem alterar expressões
ou resultados esperados. Não é uma meta de latência nem um controle de publicação.
Standalone e suíte completa foram reexecutados após essa alteração e passaram.
O workflow é idêntico ao arquivo da J1; somente o teste recebeu essa adaptação.

Não se requer PostgreSQL ou OCI para testar a política do workflow. Builds pesados,
scans, downloads de dependências e Testcontainers ficam fora desta preparação.
A instalação node_modules existente pode diferir do lockfile antigo; resultados
locais que a utilizem não certificam instalação limpa por npm ci.
Foi constatado follow-redirects 1.16.2 instalado, contra 1.16.1 no lockfile da base,
e ausência de 80 entradas opcionais do lockfile. Não houve reinstalação ou ajuste
de dependências. O pipeline futuro usará npm ci com o lockfile da base; seu resultado
continua não verificado. Actionlint não está instalado e não foi baixado.
Build, TypeScript, API, PostgreSQL, OCI e workflows remotos não foram executados:
não são necessários para verificar a política estática deste patch e excedem
o escopo de validação leve autorizado. Não houve nova qualificação da aplicação.

## Revisão, promoção futura e reversão

A troca de branch pode tornar visíveis backups/caches antes ignorados pelo
.gitignore da branch de desenvolvimento. Esses arquivos preexistentes permanecem
intactos e fora do índice. Usar somente a allowlist acima para revisão/commit;
não usar adição indiscriminada do workspace.

Antes de qualquer envio, confirmar base remota em leitura autorizada, estado do
workflow/ruleset e efeitos das integrações. Conferir o diff dos três arquivos e
obter autorização separada para push/PR. Nenhum comando de envio foi executado.

Uma futura reversão deve manter removidos o publicador e suas permissões. Reverter
integralmente à versão anterior do workflow recoloca o código de publicação no
repositório; preferir correção restrita das etapas de validação, com revisão.
Nem este patch nem o sucesso dos testes autorizam reabilitação ou publicação.
