# Fixtures TSE

Capturadas em 23/10/2026 via HTTPS, sem alterações de conteúdo. Arquivos públicos de pré-apuração.

| Arquivo | URL oficial | SHA-256 |
|---|---|---|
| ea20-national-official.json | https://resultados.tse.jus.br/oficial/ele2026/6258/dados/br/br-c0001-e006258-u.json | d57a0b93daff57aca9767c05cf2c9b10a293dc5eea42be96cb1cfd3993c131c9 |
| ea20-state-ac-official.json | https://resultados.tse.jus.br/oficial/ele2026/6258/dados/ac/ac-c0001-e006258-u.json | a7e2cd664292bf75d6e2688069bf077c6b47799c00624c95e2f1d90ddbfbd79a |
| ea20-municipality-ac01120-official.json | https://resultados.tse.jus.br/oficial/ele2026/6258/dados/ac/ac01120-c0001-e006258-u.json | a0ef7180a52fa95ff1461150917762527f8484fb619ad9c43643437d649f0da5 |
| ea12-municipalities-official.json | https://resultados.tse.jus.br/oficial/ele2026/6258/config/mun-e006258-cm.json | e4a2f2d45bac325f486e1866973fd65ae86744896d0f19e791b0bbb58d86e839 |

`ea20-progress-synthetic.json` é **sintética**, derivada da estrutura nacional real exclusivamente para testes de contagem parcial. Números, geração e andamento foram alterados; não representa apuração real. Cenários de finalização/correção são derivações explícitas dessa fixture nos testes.

`.gitattributes` preserva os bytes das fixtures oficiais: desabilita conversão de fim de linha e verificação de espaços finais apenas nesses arquivos, pois esses espaços vêm da fonte. Os hashes acima verificam a cópia original.
