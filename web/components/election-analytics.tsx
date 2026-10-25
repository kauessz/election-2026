"use client";
import { useState } from "react";
import Link from "next/link";
import { ArrowRight } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { OfficialResponse, hasCounting } from "@/lib/official-api";
import type { Projection, Remaining, Trend } from "@/lib/election-analytics";
import { useLivePolling } from "@/lib/use-live-api";
import { latestLiveProjection } from "@/lib/live-counting";
import { LiveUpdateStamp } from "@/components/live-update-stamp";
const number = new Intl.NumberFormat("pt-BR", { maximumFractionDigits: 2 });
const votes = new Intl.NumberFormat("pt-BR", { maximumFractionDigits: 0 });
const n = (value: number | null | undefined) => value == null ? "—" : number.format(value);
const pct = (value: number | null | undefined) => value == null ? "—" : `${n(value * 100)}%`;
const time = (value: string | null | undefined) => value ? new Date(value).toLocaleString("pt-BR", { timeZone: "America/Sao_Paulo" }) + " (Brasília)" : "—";
const coverage = { LOW: "Baixa", MEDIUM: "Média", HIGH: "Alta", FINAL: "Final oficial" };
export function ProjectionCard({ official }: { official: OfficialResponse | null }) {
 const { data: current, error, loading } = useLivePolling<Projection>("/api/election/president/projection");
 const model = current?.model ?? "STATE_SHRINKAGE_V1";
 const history = useLivePolling<Projection[]>(`/api/election/president/projection/history?origin=LIVE&modelVersion=${encodeURIComponent(model)}&limit=1`);
 const published = latestLiveProjection(history.data ?? [], model);
 const data = current?.publishable && published ? published : current;
 const available = data?.enabled && data.publishable;
 return <section className="official-section analytics-projection" aria-label="Projeção do modelo">
  <div className="analytics-heading"><div><span className="section-kicker">PROJEÇÃO ELECTION 2026 / MODELO PRÓPRIO</span><h2>Projeção territorial</h2></div><Badge variant="outline">Modelo próprio</Badge></div>
  {data && <LiveUpdateStamp timestamp={data.publication?.origin === "LIVE" ? data.publication.publishedAt : data.calculatedAt} projection source={data.publication?.origin === "LIVE" ? "Publicação LIVE" : "Cálculo atual (sem publicação LIVE)"}/>}
  <p className="official-footnote">Atualização automática a cada 15 segundos com a página visível.</p>
  <p>Estimativa baseada na distribuição territorial dos votos já totalizados e no resultado oficial do primeiro turno.</p>
  <p className="analytics-limitation">Modelo experimental. Não é resultado oficial nem chamada de vencedor e pode oscilar conforme novas regiões entram na totalização.</p>
  {error && <p className="official-notice" role="alert">{error} {available && "Última projeção recebida preservada; não é uma estimativa atualizada."}</p>}
  {history.error && <p className="official-notice" role="status">Histórico de publicações sem atualização. {published ? "Última publicação LIVE preservada; consulte seu horário." : "Ainda não foi possível confirmar uma publicação LIVE."}</p>}
  {data?.publication?.origin === "LIVE" && <p className="official-footnote">Origem: LIVE · Snapshot: {data.publication.snapshotId} · Publicado em: {time(data.publication.publishedAt)}.</p>}
  {loading ? <p role="status">Carregando modelo…</p> : !data ? <p>Modelo indisponível. Nova tentativa automática.</p> : !data.enabled ? <div className="official-empty">Projeção desabilitada. O resultado oficial continua disponível.</div> : !available ? <div className="official-empty"><h3>Projeção disponível após maior cobertura da apuração.</h3><p>Publicação exige ao menos {pct(data.configuration.minimumNationalElectorateProgress)} do eleitorado representado, {data.configuration.minimumStatesWithData} UFs com votos, {data.configuration.minimumRegionsWithData} das 5 regiões e {data.configuration.minimumKeyStatesWithData} dos 6 estados-chave (SP, MG, RJ, BA, PR, RS), com cobertura territorial suficiente.</p><p>Agora: {pct(data.nationalElectorateProgress)} do eleitorado · {data.statesWithData}/{data.statesTotal} UFs com dados · {data.geography.regionsWithData}/5 regiões · {data.geography.keyStatesWithData}/6 estados-chave.</p></div> : <>
   <div className="analytics-comparison"><div><h3>OFICIAL AGORA / TSE</h3>{official?.result?.candidates.map(c=><p key={c.candidateNumber}><span>{c.displayName}</span><strong>{hasCounting(official.result) ? `${n(c.percentage)}%` : "—"}</strong></p>)}{!official?.result && <p>Resultado oficial indisponível.</p>}<small>Geração: {time(official?.result?.generatedAt)}</small></div>
   <div><h3>{error ? "ÚLTIMA PROJEÇÃO RECEBIDA / DESATUALIZADA" : data.status === "FINAL" ? "ENCERRAMENTO / OFICIAL EXATO" : "PROJEÇÃO FINAL / MODELO"}</h3>{data.results.map(c=><p key={c.candidateNumber}><span>{c.name}</span><strong>{n(c.projectedPercentage)}%</strong></p>)}<small>Cálculo: {time(data.calculatedAt)}</small></div></div>
   <dl className="official-metrics"><div><dt>Diferença projetada</dt><dd>{n(data.spread?.points)} p.p.</dd></div><div><dt>Eleitorado representado</dt><dd>{pct(data.nationalElectorateProgress)}</dd></div><div><dt>Cobertura descritiva</dt><dd>{coverage[data.coverage]}</dd></div></dl>
   <p className="analytics-scope">{data.status === "FINAL" ? "Encerramento confirmado pelo TSE: resultado nacional oficial, incluindo o exterior." : "Estimativa das 27 UFs, acrescida apenas dos votos observados do exterior quando disponíveis. Nenhum voto restante do exterior é projetado. Cobertura não representa chance de acerto."}</p>
   <details><summary>Auditar cálculo por UF</summary><div className="official-table-wrap"><table><thead><tr><th>UF</th><th>Eleitorado representado</th><th>Peso observado</th><th>Válidos estimados</th><th>Candidato</th><th>Prior 1º turno</th><th>Observado</th><th>Projetado</th><th>Votos estimados</th></tr></thead><tbody>{data.states.flatMap(s=>s.candidates.map(c=><tr key={`${s.uf}-${c.candidateNumber}`}><td><Link href={`/apuracao/${s.uf.toLowerCase()}`}>{s.uf}</Link></td><td>{pct(s.electorateProgress)}</td><td>{pct(s.observationWeight)}</td><td>{s.projectedValidVotes == null ? "—" : votes.format(s.projectedValidVotes)}</td><td>{c.name}</td><td>{pct(c.baselineShare)}</td><td>{pct(c.observedShare)}</td><td>{pct(c.projectedShare)}</td><td>{votes.format(c.projectedCandidateVotes)}</td></tr>))}</tbody></table></div><p>Frações e votos estimados são arredondados apenas para exibição. Dados completos e fontes do baseline na API.</p></details>
  </>}
  {data && <p className="analytics-scope">Exterior: {data.overseas.incorporated ? "votos oficiais observados incorporados" : "nenhum voto incorporado separadamente"}. {data.overseas.note}</p>}
  {data && <details className="analytics-warnings"><summary>Limitações e cobertura ({data.warnings.length} avisos)</summary><ul>{data.warnings.map((w,i)=><li key={i}>{w}</li>)}</ul>{data.missingBaselineStates.length>0&&<p>Sem baseline: {data.missingBaselineStates.join(", ")}.</p>}</details>}
  <p className="analytics-limitation">O modelo não sabe quais seções ainda vão entrar dentro de cada UF e pode sofrer viés pela ordem de totalização. Não indica vencedor ou probabilidade eleitoral.</p>
  {data&&<p className="official-footnote">Modelo: {data.model} · Cálculo: {time(data.calculatedAt)} · Geração oficial usada: {time(data.officialGeneratedAt)}. Cobertura não é probabilidade de vitória.</p>}
  <Link href="/metodologia#projecao-apuracao" className="text-link">Como calculamos <ArrowRight size={14}/></Link>
 </section>;
}
function VoteRhythm() {
 const { data, error } = useLivePolling<Trend>("/api/election/president/trend");
 const [minutes,setMinutes] = useState(5); const window = data?.windows.find(w=>w.minutes===minutes);
 return <section className="official-section analytics-rhythm" aria-label="Ritmo dos votos"><span className="section-kicker">RITMO DOS VOTOS / DELTAS OFICIAIS</span><h2>Votos adicionados recentemente</h2><p>Esta métrica descreve os votos recém-totalizados e não é uma previsão.</p>
  <div className="analytics-tabs" role="group" aria-label="Janela de tempo">{[5,15,30].map(m=><button key={m} type="button" aria-pressed={minutes===m} onClick={()=>setMinutes(m)}>{m} min</button>)}</div>
  {error && <p className="official-notice" role="status">{error} {data && "Última janela recebida preservada; verifique os horários abaixo."}</p>}
  {window?.available ? <><p className="analytics-votes-total"><strong>{window.newValidVotes == null ? "—" : votes.format(window.newValidVotes)}</strong> votos válidos novos nos intervalos elegíveis</p><div className="analytics-composition">{window.candidateShares.map(c=><div key={c.candidateNumber}><span>{c.name}</span><strong>{n(c.percentage)}%</strong><small>{votes.format(c.newVotes)} votos novos ao candidato</small></div>)}</div><p>Intervalos usados: {window.usedIntervals} · ignorados: {window.excludedIntervals}</p><p>De {time(window.from)} até {time(window.to)}.</p></> : <div className="official-empty">Sem dados suficientes para a janela de {minutes} minutos. Nenhum percentual foi estimado.</div>}
  {window && <p className="official-footnote">Janela solicitada: {time(window.requestedFrom)} a {time(window.requestedTo)}. Apenas intervalos completos são somados; sem interpolação.</p>}
 </section>;
}
function RemainingVotes() {
 const { data,error }=useLivePolling<Remaining>("/api/election/president/remaining");const [all,setAll]=useState(false);
 const rows=(data?.states ?? []).filter(s=>s.available);const shown=all?rows:rows.slice(0,5);
 return <section className="official-section"><span className="section-kicker">ELEITORADO PENDENTE / CÁLCULO SOBRE DADOS OFICIAIS</span><h2>Onde ainda faltam mais votos</h2><p>Ranking por eleitorado das seções ainda não totalizadas. Eleitores pendentes não equivalem a votos válidos futuros.</p>{error&&<p className="official-notice" role="status">{error} {data&&"Últimos dados preservados."}</p>}
  {rows.length ? <><div className="official-table-wrap"><table><thead><tr><th>UF</th><th>Eleitorado pendente</th><th>% pendente</th><th>% representado</th></tr></thead><tbody>{shown.map(s=><tr key={s.uf}><td><Link href={`/apuracao/${s.uf.toLowerCase()}`}>{s.uf}</Link></td><td>{s.remainingElectorate==null?"—":votes.format(s.remainingElectorate)}</td><td>{n(s.remainingPercentage)}%</td><td>{n(s.representedPercentage)}%</td></tr>)}</tbody></table></div><button className="text-link" type="button" aria-expanded={all} onClick={()=>setAll(v=>!v)}>{all?"Mostrar top 5":"Ver todas as UFs"} <ArrowRight size={14}/></button></> : <div className="official-empty">Aguardando dados oficiais de eleitorado por UF.</div>}
  {data&&<p className="official-footnote">Dados disponíveis para {data.statesAvailable}/{data.statesTotal} UFs. {data.statesAvailable<data.statesTotal&&"UFs sem dados não são tratadas como zero pendente."}</p>}
 </section>;
}
export function ElectionAnalytics({official}:{official:OfficialResponse|null}) {return <><ProjectionCard official={official}/><VoteRhythm/><RemainingVotes/></>;}
