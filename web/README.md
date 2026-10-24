This is a [Next.js](https://nextjs.org) project bootstrapped with [`create-next-app`](https://nextjs.org/docs/app/api-reference/cli/create-next-app).

## Fase 7A: apresentação e atualização

Os cards públicos dos agregados usam duas casas; composição, histórico e tooltips técnicos preservam até quatro. Nenhum valor da API é arredondado antes de um cálculo.

Agregados, últimas pesquisas e `/pesquisas` consultam a API a cada 60 segundos após o término da consulta anterior. O botão atualiza somente os dados do sistema, sem coletar publicações nos institutos. `useApi` preserva a última resposta durante refresh/falhas, cancela requests/timers ao desmontar ou trocar path e não sobrepõe requests. O horário exibido é o da última consulta bem-sucedida, em Brasília.

Filtros de `/pesquisas` são locais: nome/slug/registro sem distinção de caixa/acentos, instituto, base, janela de publicação e última pesquisa por instituto após os demais filtros. Janelas usam `publishedAt` e o instante da última consulta; são renovadas a cada refresh. Essa seleção visual não modifica a elegibilidade do agregador.

## Mapa geográfico: origem e licença

O asset `lib/geo/brazil-states.geo.json` contém as 27 UFs extraídas de **Natural Earth, Admin 1 — States/Provinces, escala 1:50m**, dados de **domínio público**, conforme [termos oficiais](https://www.naturalearthdata.com/about/terms-of-use/).

- [Fonte GeoJSON fixada no commit 117488dc884bad03366ff727eca013e434615127](https://github.com/nvkelso/natural-earth-vector/blob/117488dc884bad03366ff727eca013e434615127/geojson/ne_50m_admin_1_states_provinces.geojson).
- Revisão, URL e SHA-256 do arquivo original estão em `attribution` no asset.
- Reprodução opcional: `python scripts/update-map.py`, a partir de `web/`. Não roda no build nem no runtime.
- As coordenadas são preservadas; apenas selecionamos Brasil e reduzimos os metadados. O nome do DF é apresentado como Distrito Federal.
- SVG é projetado em Mercator a partir dessas coordenadas; contornos e âncoras não são desenhados manualmente. O círculo do DF amplia apenas a área de interação, sem representar seus limites.
- Mapa ilustrativo, sem finalidade cadastral. Generalização da escala pode omitir ilhas pequenas. Nenhuma biblioteca geográfica/dependência adicional ou chamada externa em produção.

O mapa recebe exclusivamente o mesmo array `OfficialResponse[]` já consultado por `Territories` a cada 15 segundos; não faz fetch adicional. A tabela estadual é preservada. Líder e diferença usam votos/percentuais oficiais; apuração usa `sections.percentageTotalized`. Nomes vêm do resultado oficial, sem candidatos hardcoded. ZZ não é desenhado e seu tratamento nacional permanece intacto.

Teclado: Tab/Enter em cada UF. Toque: primeiro mostra detalhes; segundo abre a rota estadual. O seletor de UF oferece alternativa para áreas pequenas. Resumo persistente, rótulos, foco e tabela complementam as cores. STALE mantém os valores com aviso; ausência de resultado usa preenchimento neutro; FINAL é identificado.

## Testes

`npm test` usa Node Test Runner e o TypeScript já instalado: formatação, filtros, apresentação do mapa/geometria e ciclo de refresh com requests controlados/timers simulados. Nenhum framework ou pacote novo. Os testes do controlador cobrem a lógica usada pelo hook; não substituem uma montagem React completa.

## Arquivos da Fase 7A

Para o histórico de pesquisas individuais de ambos os turnos (Fase 7A.1), acessar `/pesquisas/historico` ou o link em `/pesquisas`. Contrato completo em [docs/poll-history.md](../docs/poll-history.md). Os agregados/snapshots permanecem uma camada separada; não há nova média ou normalização de recortes do 1º turno.

Criados (13):

- Componentes: `components/brazil-results-map.tsx`, `components/refresh-controls.tsx`.
- Helpers: `lib/api-refresh.ts`, `lib/brazil-geography.ts`, `lib/official-map.ts`, `lib/poll-filters.ts`, `lib/presentation.ts`.
- Geografia: `lib/geo/brazil-states.geo.json`, `scripts/update-map.py`.
- Testes: `tests/api-refresh.test.mjs`, `tests/load-typescript.mjs`, `tests/official-map.test.mjs`, `tests/public-presentation.test.mjs`.

Modificados (8): `README.md`, `app/globals.css`, `app/pesquisas/page.tsx`, `components/aggregate-dashboard.tsx`, `components/official-dashboard.tsx`, `components/poll-list.tsx`, `lib/use-api.ts` e `package.json` (apenas comando de testes).

Nenhuma dependência adicionada. Backend, agregadores, projeção, TSE, migrations, dados e regra de freeze não foram modificados. A conferência visual usa o estado oficial disponível (`NOT_STARTED`); liderança/empate/STALE/FINAL/indisponibilidade também são verificados com fixtures sintéticas nos testes. Coordenadas SVG de apresentação são serializadas com duas casas para evitar diferenças ínfimas entre Node/Chromium na hidratação; coordenadas originais e valores eleitorais permanecem intactos.

## Getting Started

First, run the development server:

```bash
npm run dev
# or
yarn dev
# or
pnpm dev
# or
bun dev
```

Open [http://localhost:3000](http://localhost:3000) with your browser to see the result.

You can start editing the page by modifying `app/page.tsx`. The page auto-updates as you edit the file.

This project uses [`next/font`](https://nextjs.org/docs/app/building-your-application/optimizing/fonts) to automatically optimize and load [Geist](https://vercel.com/font), a new font family for Vercel.

## Learn More

To learn more about Next.js, take a look at the following resources:

- [Next.js Documentation](https://nextjs.org/docs) - learn about Next.js features and API.
- [Learn Next.js](https://nextjs.org/learn) - an interactive Next.js tutorial.

You can check out [the Next.js GitHub repository](https://github.com/vercel/next.js) - your feedback and contributions are welcome!

## Deploy on Vercel

The easiest way to deploy your Next.js app is to use the [Vercel Platform](https://vercel.com/new?utm_medium=default-template&filter=next.js&utm_source=create-next-app&utm_campaign=create-next-app-readme) from the creators of Next.js.

Check out our [Next.js deployment documentation](https://nextjs.org/docs/app/building-your-application/deploying) for more details.
