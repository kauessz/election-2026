import { Badge } from "@/components/ui/badge";
export const metadata = { title: "Metodologia" };
export default function MethodologyPage() {
  return (
    <>
      <div className="page-intro">
        <span className="eyebrow">TRANSPARÊNCIA / COMO LER OS DADOS</span>
        <h1>
          Metodologia<span className="heading-dot">.</span>
        </h1>
        <p>
          As mesmas bases de comparação. O contexto necessário para interpretar
          cada pesquisa.
        </p>
      </div>
      <div className="method-layout">
        <aside>
          <Badge variant="outline">Fundação do modelo</Badge>
          <p>
            Publicamos somente os cálculos implementados. O agregado estatístico
            ainda está em desenvolvimento.
          </p>
        </aside>
        <article className="prose-content">
          <section>
            <span className="section-kicker">01 / BASE DE VOTOS</span>
            <h2>Votos totais e votos válidos</h2>
            <p>
              Pesquisas em votos totais (TOTAL) incluem na base respostas como
              branco, nulo e indecisão. Os percentuais dos candidatos podem,
              portanto, somar menos de 100%.
            </p>
            <p>
              Em votos válidos (VALID), os percentuais já estão expressos sobre
              os votos atribuídos aos candidatos. Preservamos os valores
              publicados, inclusive eventuais diferenças de arredondamento.
            </p>
          </section>
          <section>
            <span className="section-kicker">02 / COMPARABILIDADE</span>
            <h2>Como normalizamos</h2>
            <p>
              Para pesquisas TOTAL, dividimos o percentual de cada candidato
              pela soma dos percentuais dos candidatos informados e
              multiplicamos por 100.
            </p>
            <div className="formula">
              Percentual normalizado =<br />
              <strong>percentual original ÷ soma dos candidatos × 100</strong>
            </div>
            <p>
              Por exemplo, 47% e 45% somam 92%. Após a normalização, os
              resultados são 51,0870% e 48,9130%. O backend usa precisão decimal
              de quatro casas e ajusta o resíduo de arredondamento para
              totalizar 100%. A soma zero é rejeitada.
            </p>
          </section>
          <section>
            <span className="section-kicker">03 / RECORTE</span>
            <h2>Uma pesquisa por instituto</h2>
            <p>
              A visão geral apresenta somente a pesquisa mais recente de cada
              instituto. Esse recorte será usado no futuro agregado para evitar
              que institutos com mais publicações sejam representados várias
              vezes.
            </p>
            <p>
              Selecionamos pela data de publicação, depois pelo fim do campo e
              pela data de cadastro em caso de empate. Um identificador resolve
              empates restantes. O histórico completo permanece na página de
              pesquisas.
            </p>
          </section>
          <section>
            <span className="section-kicker">04 / INTERPRETAÇÃO</span>
            <h2>Pesquisas não são resultados oficiais</h2>
            <p>
              Nenhuma pesquisa individual constitui previsão oficial do
              resultado. Cada levantamento representa um período de campo, uma
              amostra e uma metodologia próprios, sujeitos à margem de erro e a
              outras fontes de incerteza.
            </p>
            <p>
              Os futuros modelos estatísticos serão apresentados separadamente
              dos dados oficiais do Tribunal Superior Eleitoral. Não há
              projeções ou agregados calculados nesta fase.
            </p>
          </section>
        </article>
      </div>
    </>
  );
}
