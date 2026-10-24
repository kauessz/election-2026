import Link from "next/link";
import { PollHistoryTimeline } from "@/components/poll-history-timeline";

export const metadata = { title: "Evolução das pesquisas", description: "Observações individuais das pesquisas presidenciais de 2026, separadas por turno e base." };
export default function PollHistoryPage() {
  return <><div className="page-intro"><span className="eyebrow">PESQUISAS PRESIDENCIAIS / 2026</span><h1>Evolução das pesquisas<span className="heading-dot">.</span></h1>
    <p>Observe os levantamentos publicados no 1º e 2º turnos. Pesquisas individuais, agregados e resultados oficiais são camadas distintas.</p>
    <Link href="/pesquisas" className="text-link">← Consultar pesquisas atuais</Link></div><PollHistoryTimeline/></>;
}
