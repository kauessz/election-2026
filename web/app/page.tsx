import Link from "next/link";
import { ArrowRight, ChartNoAxesCombined, Vote } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { PollList } from "@/components/poll-list";

export default function Home() {
  return (
    <>
      <section className="hero">
        <div className="eyebrow">
          <span className="status-dot" /> OBSERVATÓRIO ELEITORAL{" "}
          <span className="eyebrow-divider">/</span> BRASIL
        </div>
        <div className="hero-row">
          <div>
            <h1>
              Eleições 2026<span className="heading-dot">.</span>
            </h1>
            <p className="hero-subtitle">
              Pesquisas, tendências e apuração em tempo real
            </p>
          </div>
          <Badge variant="outline" className="round-badge">
            <Vote size={15} /> 2º turno presidencial
          </Badge>
        </div>
        <p className="hero-caption">
          Uma visão clara dos dados que ajudam a entender a eleição.
        </p>
      </section>
      <section className="section">
        <div className="section-heading">
          <div>
            <span className="section-kicker">01 / PANORAMA</span>
            <h2>Agregado das pesquisas</h2>
          </div>
          <Badge variant="secondary">Em desenvolvimento</Badge>
        </div>
        <div className="aggregate-empty">
          <div className="empty-icon">
            <ChartNoAxesCombined size={27} />
          </div>
          <h3>Os dados vêm primeiro.</h3>
          <p>O agregado será calculado a partir das pesquisas cadastradas.</p>
          <Link href="/metodologia" className="text-link">
            Entenda a metodologia <ArrowRight size={15} />
          </Link>
        </div>
      </section>
      <section className="section">
        <div className="section-heading">
          <div>
            <span className="section-kicker">02 / DADOS PUBLICADOS</span>
            <h2>Últimas pesquisas</h2>
            <p>
              A pesquisa mais recente de cada instituto. Resultados em votos
              válidos.
            </p>
          </div>
          <Link href="/pesquisas" className="text-link">
            Todas as pesquisas <ArrowRight size={15} />
          </Link>
        </div>
        <PollList latest />
      </section>
      <section className="counting-callout">
        <div className="empty-icon">
          <Vote size={24} />
        </div>
        <div>
          <span className="section-kicker">NO DIA DA VOTAÇÃO</span>
          <h2>Apuração oficial</h2>
          <p>
            A apuração oficial será disponibilizada a partir dos dados do
            Tribunal Superior Eleitoral.
          </p>
        </div>
        <Link href="/apuracao" className="text-link">
          Saiba mais <ArrowRight size={16} />
        </Link>
      </section>
    </>
  );
}
