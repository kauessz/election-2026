import Link from "next/link";
import { ArrowRight, Vote } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { PollList } from "@/components/poll-list";
import { OfficialSummary } from "@/components/official-dashboard";
import { AggregateDashboard } from "@/components/aggregate-dashboard";

import { PreElectionFreezeSummary } from "@/components/pre-election-freeze";

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
      <PreElectionFreezeSummary />
      <p className="hero-caption">Agregados live abaixo: podem incorporar novas pesquisas e não substituem a comparação congelada.</p>
      <AggregateDashboard />
      <section className="section">
        <div className="section-heading">
          <div>
            <span className="section-kicker">03 / DADOS PUBLICADOS</span>
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
      <OfficialSummary />
    </>
  );
}
