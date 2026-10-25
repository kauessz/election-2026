"use client";
import type { ReactNode } from "react";
import Link from "next/link";
import { useOfficialPolling } from "@/lib/official-api";
import { homePriority,type ElectionContext } from "@/lib/election-context";
import { PreElectionFreezeSummary } from "./pre-election-freeze";
import { OfficialHomeResults } from "./official-dashboard";
const time=(v:string|null|undefined)=>v?`${new Date(v).toLocaleString("pt-BR",{timeZone:"America/Sao_Paulo"})} (Brasília)`:"Indisponível";
export function ElectionHome({freeze,aggregates,polls,officialSummary}:{freeze:ReactNode;aggregates:ReactNode;polls:ReactNode;officialSummary:ReactNode}){
 const context=useOfficialPolling<ElectionContext>("/api/election/president/context");
 const priority=homePriority(context.data?.phase);
 const notice=<p className="hero-caption">Agregados live: podem incorporar novas pesquisas e não substituem a comparação congelada.</p>;
 const sourceNotice=<div role="status" className="official-notice"><p>{context.error?"Contexto sem atualização: preservamos a última fase recebida.":context.data?.phase==="UNKNOWN"?"Ainda não há evidência oficial persistida suficiente para identificar a fase.":context.data?.warnings.join(" ")||"Fase baseada nos dados oficiais persistidos."}</p>
  {context.data&&<p>Contexto verificado: {time(context.data.evaluatedAt)} · Geração oficial: {time(context.data.officialGeneratedAt)} · Última consulta bem-sucedida ao TSE: {time(context.data.lastSuccessfulUpdate)}.</p>}
 </div>;
 if(priority==="pre-election")return <>{(context.error||context.data?.phase==="UNKNOWN")&&sourceNotice}{freeze}{notice}{aggregates}{polls}{officialSummary}</>;
 return <>
  <section className="section" aria-label="Fase da eleição"><span className="section-kicker">{priority==="finished"?"TOTALIZAÇÃO OFICIAL FINALIZADA":"APURAÇÃO EM ANDAMENTO"}</span>
   <p>Resultado oficial e projeção são apresentados separadamente. <Link className="text-link" href="/apuracao">Acompanhar estados, municípios e histórico</Link></p>{sourceNotice}
  </section>
  <OfficialHomeResults includeProjection={priority==="counting"}/>
  {priority==="finished"&&<PreElectionFreezeSummary showUnavailable/>}
  <details className="section"><summary>Pesquisas e modelos pré-eleitorais — contexto anterior à apuração</summary>
   {priority==="counting"&&freeze}{notice}{aggregates}{polls}
  </details>
 </>;
}
