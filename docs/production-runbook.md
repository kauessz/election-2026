# Runbook de produção

## Antes do domingo

- Uma réplica da API, uma região, private networking do Postgres e health UP.
- ADMIN_API_KEY selada no Railway; ADMIN_UI_ENABLED=false no Vercel; testar 401/403/200 e CORS exato.
- Habilitar scheduled backups no serviço Postgres, aba Backups: ao menos Daily e, conforme retenção necessária, Weekly/Monthly. Confirmar execução recente e retenção no painel.
- Criar backup manual antes de qualquer freeze futuro e antes de mudanças operacionais importantes. Esta fase não cria freeze nem backup via código.
- Anotar versão da aplicação, migrations aplicadas, horário/ID do backup e contagens da base. Guardar evidência fora de logs contendo segredos.
- Ensaiar restore e medir o tempo antes de depender do procedimento em incidente.

## Restore Railway

1. Registrar incidente, estado atual e horário-alvo; pausar mudanças admin e parar a API/scheduler para impedir escritas durante a recuperação.
2. Se possível, tirar backup do estado atual antes de restaurar.
3. No Postgres > Backups, escolher backup pelo timestamp, clicar Restore, revisar volume e mudanças staged, depois Deploy. Restore é operação destrutiva para dados posteriores ao backup: confirmar o alvo no painel.
4. A documentação Railway restringe restore ao mesmo projeto/ambiente; backups do volume não substituem cópia externa e desaparecem se o volume for apagado. Conferir limites/retenção vigentes antes da operação.
5. Validar Postgres; subir a API com uma réplica e a mesma configuração privada. Flyway deve validar/aplicar migrations compatíveis com a versão restaurada.
6. Executar smoke read-only, data-quality, aggregates/audit, history e apuração. Conferir contagens com evidência do backup e preservar registros de auditoria; só então retomar escritas.

Opcional: `pg_dump --format=custom --file=election-backup.dump` com PGHOST/PGPORT/PGDATABASE/PGUSER e senha via `.pgpass` fora do Git (permissões restritas). O comando não inclui senha/URL com credenciais. Dentro da rede Railway, usar host privado; de fora, só com acesso/túnel explicitamente configurado. `pg_restore --list election-backup.dump` inspeciona o arquivo sem escrita. Ensaio de restore deve apontar para banco separado de teste, nunca para produção por padrão. Não implementar backup customizado dentro da aplicação.

## Startup e recuperação TSE

A aplicação precisa de PostgreSQL funcional e migrations válidas. Após Flyway/Hibernate, no ApplicationReadyEvent, recupera os últimos snapshots oficiais de Brasil, 27 UFs e exterior do banco, sem depender de HTTP TSE. O scheduler segue as mesmas frequências, timeout, rate limiter, ETag, Last-Modified, backoff e deduplicação. Nenhuma agressividade foi aumentada.

`election.tse.enabled=true` é explícito no production profile; ELECTION_TSE_ENABLED controla o default de scheduling. TSE_RESULTS_SCHEDULING_ENABLED=false desliga os jobs e consultas municipais em testes/manutenção. Quando ambas forem informadas, o override específico de scheduling prevalece. Uma indisponibilidade TSE não derruba o startup/health: a última totalização segue disponível com status STALE quando aplicável. Sem snapshot anterior, UNAVAILABLE é esperado. Health não faz fetch ao TSE.

Se escalar para mais de uma réplica, implementar leader election/lock distribuído antes. Durante troca de versão, evitar sobreposição prolongada de instâncias: interromper a antiga antes de iniciar a nova se precisar garantir exclusividade absoluta do scheduler; o rollout com healthcheck pode sobrepor processos temporariamente mesmo com numReplicas=1.

## Diagnóstico

- Health 503: conferir disponibilidade DB, recursos e prontidão; não mascarar falha com health falso. Erro de migration/schema impede inicialização.
- Admin 401/403: conferir presença/chave correta no HTTP client; nunca colocar segredo no navegador. Sem ADMIN_API_KEY, production falha antes de servir tráfego.
- Browser bloqueado por CORS: conferir domínio exato em APP_CORS_ALLOWED_ORIGINS (CSV). Nenhum wildcard e nenhuma URL Preview arbitrária liberada automaticamente.
- TSE 304 é sucesso de revalidação; 404/429/transport failures usam backoff existente. Conferir source-status e logs. Não reduzir intervalos para compensar indisponibilidade.
- Recovery: log `startup official_recovery_complete`; fetch debug `tse status=200/304`; falhas/retry em WARN; snapshot `persisted=true/false`; projeção registra model/status/coverage. Sem dump de payload, headers, chave, senha ou URL JDBC com credenciais.
- Frontend: NEXT_PUBLIC_API_URL exige novo build se mudar. ADMIN_UI_ENABLED deve permanecer false em produção/preview. API indisponível deve apresentar mensagens de conexão/retry, preservando a navegação pública.

## Referências

[Backups Railway](https://docs.railway.com/volumes/backups) e [backup/restore Postgres](https://docs.railway.com/guides/postgres-backups-restores). Habilitar/agendar e testar backups requer acesso ao projeto real; preparar estes arquivos não executa nenhuma operação no Railway.
