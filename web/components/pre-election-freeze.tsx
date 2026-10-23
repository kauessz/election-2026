"use client";
import Link from "next/link";
import { useApi } from "@/lib/use-api";
import { useOfficialPolling } from "@/lib/official-api";
import type { Aggregate } from "@/lib/api";
import { formatFreezeTime, type Freeze, type Comparison } from "@/lib/pre-election";
const pct = (v: number) => new Intl.NumberFormat("pt-BR", { maximumFractionDigits: 4 }).format(v);
const names: Record<string,string> = { SIMPLE: "Simples", RECENCY: "Recência", WEIGHTED: "Ponderado", CALIBRATED: "Calibrado" };
export function FrozenAggregates({ aggregates }: { aggregates: Aggregate[] }) {
 return <div className="freeze-grid">{aggregates.map(a=><article key={a.method} className={a.method === "CALIBRATED" ? "freeze-calibrated" : ""}><h3>{names[a.method]}</h3>{a.results.map(c=><p key={c.candidateId}>{c.candidateName} <strong>{pct(c.percentage)}%</strong></p>)}<small>{a.pollCount} pesquisas · {a.totalSampleSize.toLocaleString("pt-BR")} entrevistados</small></article>)}</div>;
}
export function PreElectionFreezeSummary(){
 const freeze=useApi<Freeze>("/api/pre-election-freeze");
 const comparison=useOfficialPolling<Comparison>("/api/election/president/pre-election-comparison",30000);
 if(freeze.loading)return <p role="status">Consultando modelo pré-eleição…</p>;
 if(freeze.error)return <p role="alert">Modelo congelado indisponível: {freeze.error} <button onClick={freeze.retry}>Tentar novamente</button></p>;
 if(!freeze.data)return null;const f=freeze.data;
 return <section className="section freeze-panel"><span className="section-kicker">PROJEÇÃO PRÉ-ELEIÇÃO CONGELADA</span><h2>Modelo pré-eleição congelado em {formatFreezeTime(f.frozenAt)}</h2><p>Modelo próprio, sem caráter de resultado oficial. Novas pesquisas alteram apenas os agregados live; a comparação eleitoral usa esta composição imutável.</p><FrozenAggregates aggregates={f.aggregateResults}/><Link className="text-link" href="/metodologia#freeze">Ver metodologia e composição</Link><details id="composicao-freeze"><summary>Composição congelada · {f.label}</summary><p>ID: {f.id} · Commit: {f.gitCommit || "Não informado"}</p><ul>{f.composition.polls.map(p=><li key={p.id}>{p.pollster.name} · campo {p.fieldStart} a {p.fieldEnd} · <a href={p.sourceUrl} target="_blank" rel="noreferrer">Fonte</a> · {p.id}</li>)}</ul><p>{f.pollsterPerformanceIds.length} registros históricos preservados.</p></details>
 {comparison.error&&<p role="alert">Comparação indisponível: {comparison.error}</p>}
 {comparison.data&&<div><h3>{comparison.data.status==="FINAL"?"Erro final em relação ao resultado oficial":comparison.data.status==="NOT_STARTED"?"Comparação com a urna":"Diferença atual em relação à apuração parcial"}</h3>{comparison.data.models.length===0?<p>Aguardando votos oficiais para comparar.</p>:comparison.data.models.map(m=><div key={m.method}><h4>{names[m.method]}</h4>{m.candidates.map(c=><p key={c.candidateId}>{c.name}: congelado {pct(c.frozenPercentage)}% · oficial {pct(c.officialPercentage)}% · {comparison.data?.status==="FINAL"?"erro final":"diferença atual"} {pct(c.finalError ?? c.currentDifference ?? 0)} p.p.</p>)}<p>Spread: {pct(m.finalError ?? m.currentDifference ?? 0)} p.p.</p></div>)}{comparison.data.warnings.map((w,i)=><p key={i}>{w}</p>)}</div>}
 </section>;
}

export function PostFreezeNotice(){const f=useApi<Freeze>("/api/pre-election-freeze");return f.data?<p className="official-notice">Existe um modelo pré-eleição congelado em {formatFreezeTime(f.data.frozenAt)}. Novos cadastros e alterações afetam somente os agregados live; o freeze e a comparação com a urna permanecem imutáveis.</p>:null;}
