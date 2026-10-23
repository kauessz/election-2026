import { Vote, ArrowUpRight } from "lucide-react";
import { Badge } from "@/components/ui/badge";
export const metadata = { title: "Apuração oficial" };
export default function CountingPage() {
  return (
    <section className="counting-placeholder">
      <span className="eyebrow">ELEIÇÕES 2026 / RESULTADOS</span>
      <div className="large-icon">
        <Vote size={42} strokeWidth={1.3} />
      </div>
      <Badge variant="outline">Disponível no dia da votação</Badge>
      <h1>
        Apuração oficial<span className="heading-dot">.</span>
      </h1>
      <p>
        Os resultados oficiais serão obtidos diretamente dos dados divulgados
        pelo Tribunal Superior Eleitoral.
      </p>
      <a
        href="https://www.tse.jus.br/"
        target="_blank"
        rel="noopener noreferrer"
        className="text-link"
      >
        Conheça o portal do TSE <ArrowUpRight size={15} />
      </a>
    </section>
  );
}
