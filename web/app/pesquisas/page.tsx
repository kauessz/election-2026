import { PollList } from "@/components/poll-list";
export const metadata = { title: "Pesquisas" };
export default function PollsPage() {
  return (
    <>
      <div className="page-intro">
        <span className="eyebrow">BASE DE DADOS / 2º TURNO</span>
        <h1>
          Pesquisas eleitorais<span className="heading-dot">.</span>
        </h1>
        <p>
          Todos os levantamentos cadastrados, com resultados originais e
          normalizados para votos válidos.
        </p>
      </div>
      <div className="section-heading">
        <h2>Histórico de pesquisas</h2>
        <span className="subtle-label">Publicação mais recente primeiro</span>
      </div>
      <PollList />
    </>
  );
}
