import { Badge } from "@/components/ui/badge";

export const metadata = { title: "Metodologia" };

const sections = [
  {
    title: "Uma pesquisa por instituto",
    paragraphs: [
      "Usamos somente a pesquisa mais recente de cada instituto ativo. Publicar mais rodadas não aumenta a influência do instituto. A seleção segue publicação, fim do campo, data de cadastro e identificador como desempate final.",
      "A referência padrão é a data atual em Brasília. Para uma data histórica, consideramos publicações até o fim daquele dia. O campo deve estar concluído e os resultados devem conter todos os candidatos ativos da disputa; exclusões aparecem na composição, sem substituir a pesquisa excluída por uma rodada anterior.",
    ],
  },
  {
    title: "Sempre em votos normalizados",
    paragraphs: [
      "Pesquisas TOTAL podem incluir branco, nulo e indecisão na base. Normalizamos os percentuais dos candidatos para permitir a comparação. Pesquisas VALID preservam os percentuais publicados, inclusive diferenças de arredondamento.",
      "Todos os modelos usam normalizedPercentage. Somente ao final, os resultados agregados são reescalados proporcionalmente e arredondados a quatro casas, com ajuste do resíduo no maior resultado para somar exatamente 100%. Os percentuais de cada pesquisa permanecem intactos.",
    ],
    formula:
      "TOTAL: normalizado = original ÷ soma dos candidatos × 100\nVALID: normalizado = original",
  },
  {
    title: "Média simples",
    paragraphs: [
      "Cada instituto recebe o mesmo peso. Essa é a base de comparação para entender o efeito das ponderações. Os modelos não representam pesquisas adicionais nem observações independentes entre si.",
    ],
    formula:
      "pesoᵢ = 1 ÷ n\nresultado_c = Σ(pesoᵢ × percentual normalizadoᵢ,c)",
  },
  {
    title: "Midpoint: o ponto médio do campo",
    paragraphs: [
      "A recência usa o ponto médio da coleta, não apenas a publicação. Interpretamos as datas como dias nominais de 24 horas, desde 00:00, sem deslocamentos por horário de verão. De 20/10 a 22/10, o midpoint é 21/10 às 00:00; de 18/10 a 21/10, é 19/10 às 12:00.",
      "A idade é a diferença em dias fracionários até 00:00 da data de referência, limitada inferiormente a zero. A data é explícita no serviço, permitindo reproduzir cálculos com os mesmos dados e configurações.",
    ],
    formula:
      "midpoint = início + (fim − início) ÷ 2\nidade = máximo(0, referência − midpoint)",
  },
  {
    title: "Recência e meia-vida",
    paragraphs: [
      "A meia-vida padrão é de três dias. Uma pesquisa com midpoint na referência recebe fator 1; com três dias, 0,5; com seis dias, 0,25. Depois, os pesos brutos são divididos pela soma dos pesos.",
    ],
    formula: "rᵢ = 2^(−idadeᵢ ÷ 3)\npesoᵢ = rᵢ ÷ Σr",
  },
  {
    title: "Precisão: margem e amostra",
    paragraphs: [
      "O modelo Ponderada multiplica recência por precisão. Priorizamos a margem de erro declarada, em pontos percentuais, usando o inverso de seu quadrado. Sem margem, estimamos 98 dividido pela raiz da amostra e aplicamos a mesma função de precisão. Sem margem e amostra válidas, o fator é 1, com uma nota de ausência.",
      "O fallback estima margens de aproximadamente 2,53 p.p. para 1.500 entrevistados, 2,19 p.p. para 2.000 e 1,55 p.p. para 4.000, mantendo escala compatível com margens declaradas. A amostra precisa ser positiva.",
      "Esta é apenas uma aproximação de fallback baseada em amostragem aleatória simples, 95% de confiança e máxima variância, não uma reconstrução da metodologia do instituto. Ela não incorpora efeitos de desenho, ponderação, não resposta ou outras fontes de erro e não substitui a margem declarada.",
    ],
    formula:
      "margem estimadaᵢ = 98 ÷ √amostraᵢ (em p.p.)\npᵢ = 1 ÷ margem declaradaᵢ²\nsem margem declarada: pᵢ = 1 ÷ margem estimadaᵢ²\nsem margem e amostra válidas: pᵢ = 1\nbruto Ponderadaᵢ = rᵢ × pᵢ",
  },
  {
    title: "Limite de influência por instituto",
    paragraphs: [
      "Em Ponderada e Calibrada, o limite padrão é 15%. Fixamos no limite os institutos que o excedem e redistribuímos a massa restante proporcionalmente entre os demais. Repetimos até todos respeitarem o limite e os pesos somarem 1.",
      "São necessários pelo menos sete institutos para respeitar 15%. Com menos, usamos pesos iguais (1/n), registramos que o limite é inviável e exibimos isso no response e na interface. Nesse caso, Ponderada e Calibrada podem coincidir com a média simples.",
    ],
    formula: "cap viável quando n × limite ≥ 1\nsem viabilidade: pesoᵢ = 1 ÷ n",
  },
  {
    title: "Desempenho histórico e calibração",
    paragraphs: [
      "Desempenho histórico altera o peso do instituto, não o percentual publicado.",
      "SpreadError é o erro absoluto da diferença entre os candidatos, em pontos percentuais, contra uma referência verificada. No padrão, erro 0 gera fator 1,15; erro 3 gera 1; erro 6 ou maior gera 0,85. Sem histórico, o fator é 1.",
      "Os limites conservadores de 0,85 a 1,15 refletem a evidência histórica ainda limitada. Limites e erro neutro continuam configuráveis.",
      "O fator é derivado no backend, sem edição manual. Só usamos a eleição e o turno configurados (padrão BR-PRESIDENT-2026, turno 1). A tabela começa vazia: nenhum erro real foi inventado. As fontes podem ser registradas no admin.",
    ],
    formula:
      "h(e) = limitar(1,15 − e ÷ 20; 0,85; 1,15)\nbruto Calibradaᵢ = rᵢ × pᵢ × hᵢ\npeso final = normalizar e redistribuir com cap",
  },
  {
    title: "Histórico de agregados",
    paragraphs: [
      "A evolução mostra snapshots salvos explicitamente pelo administrador. Um GET nunca cria registros. Cada método e data de referência admite um snapshot por minuto de cálculo; uma nova solicitação no mesmo período retorna o registro original, mesmo se os dados mudaram.",
      "Armazenamos resultados, composição, configurações e notas no snapshot para preservar a auditoria após edições. O gráfico usa o instante de cálculo em Brasília, com escala de 0 a 100%; as linhas apenas conectam os registros. Dois snapshots são o mínimo para exibir uma evolução.",
    ],
  },
  {
    title: "Agregador não é previsão",
    paragraphs: [
      "Os quatro modelos resumem pesquisas cadastradas; não calculam probabilidade de vitória. Nenhum deles constitui previsão oficial. Apuração e dados oficiais do TSE são uma categoria separada, ainda não integrada.",
      "Há incertezas de cobertura, método, seleção e dependência entre pesquisas. A amostra total é uma soma descritiva dos tamanhos informados, não o tamanho de uma amostra única independente. Pesquisas sem amostra ficam sinalizadas. Não há correção por efeito de casa, janela máxima de idade ou intervalo de confiança do agregado.",
      "A reprodução por data usa os dados, candidatos ativos, histórico e configurações atuais. Ela não reconstrói automaticamente o que era conhecido na época; para preservar uma versão exata, use snapshots. Uma alteração do conjunto de candidatos ativos pode excluir pesquisas anteriores.",
    ],
  },
];

export default function MethodologyPage() {
  return (
    <>
      <div className="page-intro">
        <span className="eyebrow">TRANSPARÊNCIA / COMO LER OS DADOS</span>
        <h1>
          Metodologia<span className="heading-dot">.</span>
        </h1>
        <p>
          Quatro formas de resumir as pesquisas. Cada peso, fórmula e limitação
          à vista.
        </p>
      </div>
      <div className="method-layout">
        <aside>
          <Badge variant="outline">Agregador · Fase 2</Badge>
          <p>
            Regras descritas para a configuração padrão. Cada resposta inclui os
            parâmetros efetivamente usados e a composição de seus institutos.
          </p>
          <p>Os modelos não calculam probabilidade de vitória.</p>
        </aside>
        <article className="prose-content">
          {sections.map((section, i) => (
            <section key={section.title}>
              <span className="section-kicker">
                {String(i + 1).padStart(2, "0")} / MODELO TRANSPARENTE
              </span>
              <h2>{section.title}</h2>
              {section.paragraphs.map((p) => (
                <p key={p}>{p}</p>
              ))}
              {section.formula && (
                <pre className="formula whitespace-pre-wrap font-mono text-xs">
                  {section.formula}
                </pre>
              )}
            </section>
          ))}
          <section>
            <span className="section-kicker">FONTE OFICIAL / TSE</span>
            <h2>Apuração oficial</h2>
            <p>Os resultados de apuração são publicados pelo Tribunal Superior Eleitoral (eleição 6258, 2º turno) e ficam separados do agregador de pesquisas. Preservamos votos e percentuais oficiais. Diferenças em votos e pontos percentuais são cálculos derivados identificados na interface.</p>
            <p>O percentual do candidato no EA20 é calculado pelo TSE sobre votos a votáveis concorrentes, que podem incluir votos anulados ou sub judice. Votos válidos, brancos e nulos são apresentados nos totais próprios. O sinal oficial de totalização final, e não apenas 100% das seções, define a conclusão.</p>
            <p>Antes da totalização, os percentuais principais aparecem como traço. Se a fonte não puder ser atualizada, mantemos o último dado válido e sinalizamos desatualização. O histórico mostra somente arquivos oficiais recebidos; não é uma projeção de resultado.</p>
            <p><a className="text-link" href="https://www.tse.jus.br/eleicoes/informacoes-tecnicas-sobre-a-divulgacao-de-resultados" target="_blank" rel="noopener noreferrer">Documentação técnica oficial do TSE</a></p>
          </section>
        </article>
      </div>
    </>
  );
}
