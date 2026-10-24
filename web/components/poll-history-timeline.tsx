"use client";

import { useState } from "react";
import { CartesianGrid, LabelList, ReferenceLine, ResponsiveContainer, Scatter, ScatterChart, Tooltip, XAxis, YAxis, usePlotArea, useXAxisScale, useYAxisScale } from "recharts";
import { latestSeriesLabels } from "@/lib/chart-labels";
import { expandedPercentageDomain, layoutHistoryLabels, roundTimelineDomain, type HistoryLabelPoint } from "@/lib/poll-history-chart";
import { ChartValueLabel } from "@/components/chart-value-label";
import { RefreshCw } from "lucide-react";
import type { AggregateMethod, AggregateSnapshot, Candidate, Poll } from "@/lib/api";
import { useApi } from "@/lib/use-api";
import { candidateIdentity, candidateLabel, combineTimelinePolls, emptyHistoryFilters, filterTimelinePolls,
  historyCandidates, historyPanels, roundMilestones, roundsNotice,
  type ArchivedPoll, type HistoricalCandidateResult, type HistoryFilters, type TimelinePoll } from "@/lib/poll-history";
import { modelPercent, publicPercent } from "@/lib/presentation";
import { date, number } from "@/components/poll-list";
import { Button } from "@/components/ui/button";
import "./poll-history-timeline.css";

const palette = ["#294b44", "#946f48", "#475569", "#795e84", "#6a752f", "#915854", "#306b7c", "#64665b"];
const methodLabels: Record<AggregateMethod, string> = { SIMPLE: "Média simples", RECENCY: "Recência", WEIGHTED: "Ponderada", CALIBRATED: "Calibrada" };
const shortDay = (timestamp: number) => new Date(timestamp).toLocaleDateString("pt-BR", { day: "2-digit", month: "2-digit", timeZone: "America/Sao_Paulo" });
const pollId = (poll: TimelinePoll) => `${poll.origin}:${poll.id}`;
type Point = HistoryLabelPoint & { poll: TimelinePoll; result: HistoricalCandidateResult };

function HistoryPlotLabels({ points, showAll, colors }: { points: Point[]; showAll: boolean; colors: Map<string,string> }) {
  const area=usePlotArea(), xScale=useXAxisScale(), yScale=useYAxisScale();
  if (!area || !xScale || !yScale || area.width<=0 || area.height<=0) return null;
  const pixels=points.flatMap(point=>{
    const x=xScale(point.time), y=yScale(point.percentage);
    return x!=null && y!=null ? [{...point,x,y}] : [];
  });
  const fontSize=area.width<330 ? 10 : 11;
  const labels=layoutHistoryLabels(pixels,{left:area.x+3,right:area.x+area.width+96,top:area.y+22,bottom:area.y+area.height-4},showAll,fontSize);
  return <g aria-hidden="true" pointerEvents="none" className="poll-history-pointLabels">
    {labels.map(label=><text key={label.id} x={label.x} y={label.y} dominantBaseline="middle"
      className={label.final ? "poll-history-finalValue" : "poll-history-observationValue"}
      fill={colors.get(label.candidateKey)}
      fontSize={fontSize} fontWeight={label.final ? 650 : 500} stroke="white" strokeWidth={3} paintOrder="stroke">
      {label.text}
    </text>)}
  </g>;
}

function PollDetails({ poll }: { poll: TimelinePoll }) {
  return <div className={"poll-history-pollDetails"}>
    <strong>{poll.pollster.name} · {poll.round}º turno</strong>
    <p>Publicação: {date(poll.publishedAt, true)} (Brasília)</p>
    <p>Campo: {date(poll.fieldStart)} a {date(poll.fieldEnd)}</p>
    <ul>{poll.results.map((result) => <li key={result.candidateKey}>
      <span>{candidateLabel(result)}{result.party ? ` (${result.party})` : ""}</span><strong>{publicPercent(result.originalPercentage)}</strong>
    </li>)}</ul>
    <p>Amostra: {poll.sampleSize == null ? "Não informada" : `${number(poll.sampleSize)} pessoas`}</p>
    <p>Margem: {poll.marginOfError == null ? "Não informada" : `± ${number(poll.marginOfError)} p.p.`}</p>
    <p>Base: {poll.voteBasis === "TOTAL" ? "TOTAL · votos totais" : "VALID · votos válidos"}</p>
    <p>Registro TSE: {poll.registrationNumber || "Não informado"}</p>
  </div>;
}

function ObservationTable({ polls, hidden }: { polls: TimelinePoll[]; hidden: string[] }) {
  return <details className={"poll-history-textTable"}><summary>Ver pesquisas individuais em tabela ({polls.length})</summary>
    <div className={"poll-history-tableScroll"}><table><caption>Percentuais originais publicados, separados por turno e base</caption><thead><tr>
      <th>Publicação / campo</th><th>Instituto / registro TSE</th><th>Candidato</th><th>Turno / base</th><th>Original</th><th>Normalizado*</th><th>Amostra / margem</th><th>Fonte</th>
    </tr></thead><tbody>{polls.flatMap((poll) => poll.results.filter((result) => !hidden.includes(candidateIdentity(result))).map((result) => <tr key={`${pollId(poll)}:${result.candidateKey}`}>
      <td>{date(poll.publishedAt, true)}<small>{date(poll.fieldStart)} a {date(poll.fieldEnd)}</small></td>
      <td>{poll.pollster.name}<small>{poll.registrationNumber || "Registro não informado"}</small></td>
      <td>{candidateLabel(result)}<small>{[result.party, result.ballotNumber].filter((value) => value != null).join(" · ") || "—"}</small></td>
      <td>{poll.round}º · {poll.voteBasis}</td><td>{modelPercent(result.originalPercentage)}</td>
      <td>{result.normalizedPercentage == null ? "Não aplicado" : modelPercent(result.normalizedPercentage)}</td>
      <td>{poll.sampleSize == null ? "Não informada" : number(poll.sampleSize)}<small>{poll.marginOfError == null ? "Margem não informada" : `± ${number(poll.marginOfError)} p.p.`}</small></td>
      <td><a className="text-link" href={poll.sourceUrl} target="_blank" rel="noopener noreferrer">Publicação ↗</a></td>
    </tr>))}</tbody></table></div>
    <p className="chart-note">* Coluna técnica; não é a série desenhada. Arquivo histórico: só disponível após declaração de conjunto completo. Pesquisas atuais: valor já publicado pela API do 2º turno.</p>
  </details>;
}

function IndividualRound({ round, polls, colors, hidden, pending, unavailable, showValues }: {
  round: 1 | 2; polls: TimelinePoll[]; colors: Map<string,string>; hidden: string[];
  pending: boolean; unavailable: boolean; showValues: boolean;
}) {
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [expanded, setExpanded] = useState(true);
  const selected = polls.find((poll) => pollId(poll) === selectedId);
  const candidates = historyCandidates(polls).filter(([key]) => !hidden.includes(key));
  const domain=roundTimelineDomain(polls,round);
  const points: Point[]=candidates.flatMap(([key,candidate],seriesIndex)=>polls.flatMap(poll=>poll.results
    .filter(result=>candidateIdentity(result)===key).map(result=>({
      id:`${pollId(poll)}:${key}`,candidateKey:key,name:candidateLabel(candidate),seriesIndex,
      time:Date.parse(poll.publishedAt),percentage:result.originalPercentage,poll,result,
    }))));
  const yDomain: [number,number]=expanded ? expandedPercentageDomain(points.map(point=>point.percentage)) : [0,100];
  const yTicks=expanded ? Array.from({length:(yDomain[1]-yDomain[0])/5+1},(_,i)=>yDomain[0]+i*5) : [0,25,50,75,100];
  const milestone=roundMilestones.find(item=>item.round===round)!;
  return <section className={"poll-history-roundPanel"} aria-label={`Pesquisas individuais do ${round}º turno`} data-round={round}>
    <span className="section-kicker">PESQUISAS INDIVIDUAIS</span><h2>{round}º turno</h2>
    <p className="chart-note">Cada ponto representa uma observação publicada. Não há média, linha de tendência ou conexão entre institutos.</p>
    <div className="poll-history-scale" role="group" aria-label={`Escala vertical do ${round}º turno`}>
      <span>Escala:</span>{([[true,"Ampliada"],[false,"0–100%"]] as const).map(([value,label])=><button key={String(value)} type="button"
        aria-pressed={expanded===value} onClick={()=>setExpanded(value)}>{label}</button>)}
    </div>
    <p className="poll-history-scaleNotice">{expanded ? "Escala ampliada para facilitar a leitura das diferenças; o eixo vertical não começa necessariamente em zero." : "Escala completa: eixo vertical de 0 a 100%."}</p>
    {!polls.length && <p className={"poll-history-empty"} role="status">{pending ? "Carregando pesquisas…" : unavailable ? "Não foi possível carregar esta fonte. Tente atualizar os dados." : round === 1 ? "Nenhuma observação do 1º turno disponível para estes filtros. O arquivo será preenchido apenas com fontes verificadas." : "Nenhuma observação do 2º turno corresponde aos filtros."}</p>}
    <div className="poll-history-candidateLegend poll-history-plotLegend" aria-label={`Legenda dos candidatos do ${round}º turno`}>{candidates.map(([key,candidate])=><span key={key}><i style={{background:colors.get(key)}} aria-hidden="true"/>{candidateLabel(candidate)}{candidate.ballotNumber!=null && <strong>· {candidate.ballotNumber}</strong>}</span>)}</div>
    <div className="poll-history-chart poll-history-individualChart" aria-label={`Observações individuais do ${round}º turno; escala ${yDomain[0]} a ${yDomain[1]}%; valores completos na tabela abaixo`}>
      <ResponsiveContainer width="100%" height="100%" minWidth={0} initialDimension={{ width: 600, height: 320 }}>
        <ScatterChart margin={{ top: 42, right: 108, bottom: 20, left: 0 }}>
          <CartesianGrid stroke="#e2e8e3" strokeDasharray="3 3" />
          <XAxis dataKey="time" type="number" domain={domain} allowDataOverflow tickFormatter={shortDay} tick={{fontSize:11}} minTickGap={32} />
          <YAxis dataKey="percentage" type="number" domain={yDomain} ticks={yTicks} allowDataOverflow unit="%" tick={{fontSize:11}} width={46} />
          <ReferenceLine x={Date.parse(milestone.date)} stroke="#809087" strokeDasharray="4 4" label={{value:milestone.label,position:"top",offset:12,fontSize:10}} />
          <Tooltip cursor={{strokeDasharray:"3 3"}} content={({active,payload}) => {
            const point=payload?.[0]?.payload as Point | undefined;
            return active && point ? <div className={"poll-history-tooltip"}><PollDetails poll={point.poll} /></div> : null;
          }} />
          {candidates.map(([key, candidate]) => {
            return <Scatter key={key} name={candidateLabel(candidate)} data={points.filter(point=>point.candidateKey===key)} fill={colors.get(key)} line={false} isAnimationActive={false}
              shape={(props: unknown) => {
                const {cx,cy,payload} = props as {cx:number;cy:number;payload:Point};
                const description=`${candidateLabel(payload.result)}, ${publicPercent(payload.percentage)}, ${payload.poll.pollster.name}, ${date(payload.poll.publishedAt,true)}, base ${payload.poll.voteBasis}. Mostrar detalhes da pesquisa.`;
                return <circle cx={cx} cy={cy} r={5} fill={payload.poll.voteBasis === "TOTAL" ? "#fff" : colors.get(key)} stroke={colors.get(key)} strokeWidth={2}
                  tabIndex={0} role="button" aria-label={description} className={"poll-history-point"}
                  onFocus={() => setSelectedId(pollId(payload.poll))} onMouseEnter={() => setSelectedId(pollId(payload.poll))}
                  onClick={() => setSelectedId(pollId(payload.poll))} onKeyDown={(event) => { if (event.key === "Enter" || event.key === " ") { event.preventDefault(); setSelectedId(pollId(payload.poll)); } }}>
                  <title>{description}</title>
                </circle>;
              }} />;
          })}
          <HistoryPlotLabels points={points} showAll={showValues} colors={colors} />
        </ScatterChart>
      </ResponsiveContainer>
    </div>
    <p className={"poll-history-legend"}>● Votos válidos (VALID) · ○ Votos totais (TOTAL). Use Tab, Enter ou toque para detalhar um ponto abaixo.</p>
    {showValues && <p className="chart-note">Somente os textos recebem deslocamentos para reduzir colisões; os pontos mantêm suas posições. Em áreas muito densas ainda pode haver sobreposição: consulte o tooltip ou a tabela, ou desligue os valores gerais.</p>}
    <div className={"poll-history-selection"} aria-live="polite" aria-atomic="true">
      {selected ? <><PollDetails poll={selected} /><a className="text-link" href={selected.sourceUrl} target="_blank" rel="noopener noreferrer">Consultar publicação ↗</a></> : <p>Selecione um ponto para consultar instituto, campo, publicação e resultados completos. Pontos coincidentes também podem ser consultados na tabela.</p>}
    </div>
    <ObservationTable polls={polls} hidden={hidden} />
  </section>;
}

function ModelSnapshots({ visibleIds, from, to }: { visibleIds: string[]; from: string; to: string }) {
  const state=useApi<AggregateSnapshot[]>("/api/aggregates/history",{refreshMs:60000});
  const [method,setMethod]=useState<AggregateMethod>("CALIBRATED");
  const selected=(state.data ?? []).filter((snapshot) => snapshot.method === method
    && (!from || Date.parse(snapshot.calculatedAt)>=Date.parse(`${from}T00:00:00-03:00`))
    && (!to || Date.parse(snapshot.calculatedAt)<=Date.parse(`${to}T23:59:59.999-03:00`)))
    .sort((a,b)=>Date.parse(a.calculatedAt)-Date.parse(b.calculatedAt));
  const candidates=[...new Map(selected.flatMap((snapshot)=>snapshot.results).filter((candidate)=>visibleIds.includes(candidate.candidateId)).map((candidate)=>[candidate.candidateId,candidate])).values()];
  const points = selected.map((snapshot)=>({time:Date.parse(snapshot.calculatedAt),...Object.fromEntries(snapshot.results.map((result)=>[result.candidateId,result.percentage]))}));
  const endLabels = latestSeriesLabels(points,candidates.map((candidate)=>candidate.candidateId));
  return <section className={"poll-history-modelPanel"} aria-label="Agregado do modelo: snapshots existentes">
    <span className="section-kicker">AGREGADO DO MODELO / 2º TURNO</span><h2>Snapshots existentes · {methodLabels[method]}</h2>
    <label>Modelo<select value={method} onChange={(event)=>setMethod(event.target.value as AggregateMethod)}>{Object.entries(methodLabels).map(([key,label])=><option key={key} value={key}>{label}</option>)}</select></label>
    <p>Camada separada das pesquisas individuais. Votos válidos; linhas unem somente snapshots do mesmo modelo e candidato, sem nova média ou tendência. O período usa a data de cálculo.</p>
    {state.error && <p role="status">Não foi possível atualizar os snapshots. {state.data ? "Últimos dados preservados." : state.error}</p>}
    {!selected.length ? <p>{state.loading ? "Carregando snapshots…" : "Sem snapshots para este modelo e período."}</p> : <>
      <div className={"poll-history-chart"}><ResponsiveContainer width="100%" height="100%" minWidth={0} initialDimension={{width:600,height:320}}><ScatterChart margin={{top:40,right:72,bottom:28,left:0}}>
        <CartesianGrid strokeDasharray="3 3"/><XAxis dataKey="time" type="number" domain={[(min:number)=>min-3600000,(max:number)=>max+3600000]} tickFormatter={shortDay}/><YAxis dataKey="percentage" type="number" domain={[0,100]} unit="%"/>
        <Tooltip content={({active,payload})=>{
          const point=payload?.[0]?.payload as {time:number;percentage:number;candidateName:string} | undefined;
          return active&&point ? <div className={"poll-history-tooltip"}><strong>Agregado do modelo · {methodLabels[method]}</strong><p>{date(new Date(point.time).toISOString(),true)}</p><p>{point.candidateName}: {modelPercent(point.percentage)}</p></div> : null;
        }}/>
        {candidates.map((candidate,i)=>{
          const series=selected.flatMap((snapshot)=>snapshot.results.filter((result)=>result.candidateId===candidate.candidateId).map((result)=>({time:Date.parse(snapshot.calculatedAt),percentage:result.percentage,candidateName:result.candidateName})));
          const endpoint=endLabels[candidate.candidateId];
          return <Scatter key={candidate.candidateId} name={`Agregado do modelo — ${candidate.candidateName}`} data={series}
            fill={palette[i%palette.length]} line={{strokeDasharray:"5 4"}} lineType="joint" lineJointType="linear" shape="diamond" isAnimationActive={false}>
            <LabelList dataKey="percentage" content={(label)=><ChartValueLabel label={label}
              endpoint={endpoint ? {...endpoint,index:series.length-1} : undefined} color={palette[i%palette.length]}/>}/>
          </Scatter>;
        })}
      </ScatterChart></ResponsiveContainer></div>
      <div className="poll-history-candidateLegend" aria-label="Candidatos nos snapshots">{candidates.map((candidate,i)=><span key={candidate.candidateId}><i style={{background:palette[i%palette.length]}} aria-hidden="true"/>{candidate.candidateName}</span>)}</div>
      <p className={"poll-history-legend"}>Losangos e linha tracejada: agregado do modelo.</p>
      <details className={"poll-history-textTable"}><summary>Ver snapshots em tabela</summary><div className={"poll-history-tableScroll"}><table><thead><tr><th>Cálculo</th><th>Modelo</th><th>Candidato</th><th>Percentual válido</th></tr></thead><tbody>{selected.flatMap((snapshot)=>snapshot.results.filter((result)=>visibleIds.includes(result.candidateId)).map((result)=><tr key={`${snapshot.id}:${result.candidateId}`}><td>{date(snapshot.calculatedAt,true)}</td><td>{methodLabels[method]}</td><td>{result.candidateName}</td><td>{modelPercent(result.percentage)}</td></tr>))}</tbody></table></div></details>
    </>}
  </section>;
}

export function PollHistoryTimeline() {
  const archive=useApi<ArchivedPoll[]>("/api/poll-history?electionKey=BR-PRESIDENT-2026",{refreshMs:60000});
  const current=useApi<Poll[]>("/api/polls",{refreshMs:60000});
  const candidates=useApi<Candidate[]>("/api/candidates");
  const [filters,setFilters]=useState<HistoryFilters>({...emptyHistoryFilters});
  const [showModel,setShowModel]=useState(false);
  const [showValues,setShowValues]=useState(false);
  const all=combineTimelinePolls(archive.data ?? [],current.data ?? [],candidates.data ?? []);
  const visible=filterTimelinePolls(all,filters);
  const catalog=historyCandidates(all);
  const colors=new Map(catalog.map(([key],i)=>[key,palette[i%palette.length]]));
  const institutes=[...new Map(all.map((poll)=>[poll.pollster.id,poll.pollster])).values()].sort((a,b)=>a.name.localeCompare(b.name,"pt-BR"));
  const refreshing=archive.refreshing||current.refreshing||candidates.refreshing;
  const canOverlay=filters.round!=="1" && !filters.pollsterId && filters.voteBasis!=="TOTAL";
  const visibleIds=all.filter((poll)=>poll.origin==="CURRENT").flatMap((poll)=>poll.results)
    .filter((result)=>!filters.hiddenCandidates.includes(candidateIdentity(result))).flatMap((result)=>result.currentCandidateId ? [result.currentCandidateId] : []);
  return <div className={"poll-history-timeline"}>
    <div className={"poll-history-toolbar"}><div role="group" aria-label="Turno das pesquisas" className={"poll-history-modes"}>
      {([["1","1º turno"],["2","2º turno"],["all","Visão geral"]] as const).map(([value,label])=><button key={value} type="button" aria-pressed={filters.round===value} onClick={()=>setFilters({...filters,round:value})}>{label}</button>)}
    </div><Button variant="outline" onClick={()=>{archive.refresh();current.refresh();candidates.refresh();}} disabled={refreshing || archive.loading || current.loading}><RefreshCw size={15} className={refreshing ? "refresh-spinning" : undefined}/>Atualizar histórico</Button></div>
    <p className={"poll-history-notice"}>{roundsNotice}</p>
    <p className="chart-note">Os gráficos mostram percentuais originais na base de cada publicação. Filtre TOTAL/VALID para comparar bases equivalentes. Sem coleta nos sites dos institutos; atualização do sistema a cada 60 segundos.</p>
    {(archive.error || current.error || candidates.error) && <div className="official-notice" role="status">
      {archive.error && <p>Arquivo histórico: {archive.error} {archive.data && "Última resposta preservada."}</p>}
      {current.error && <p>Pesquisas atuais: {current.error} {current.data && "Última resposta preservada."}</p>}
      {candidates.error && <p>Metadados de candidatos indisponíveis; observações não compatibilizadas permanecem separadas.</p>}
    </div>}
    <div className={"poll-history-filters"} aria-label="Filtros do histórico">
      <label>Instituto<select value={filters.pollsterId} onChange={(event)=>setFilters({...filters,pollsterId:event.target.value})}><option value="">Todos os institutos</option>{institutes.map((institute)=><option key={institute.id} value={institute.id}>{institute.name}</option>)}</select></label>
      <label>Base<select value={filters.voteBasis} onChange={(event)=>setFilters({...filters,voteBasis:event.target.value as HistoryFilters["voteBasis"]})}><option value="">Todas as bases</option><option value="TOTAL">Votos totais (TOTAL)</option><option value="VALID">Votos válidos (VALID)</option></select></label>
      <label>Publicação a partir de<input type="date" value={filters.from} max={filters.to||undefined} onChange={(event)=>setFilters({...filters,from:event.target.value})}/></label>
      <label>Publicação até<input type="date" value={filters.to} min={filters.from||undefined} onChange={(event)=>setFilters({...filters,to:event.target.value})}/></label>
      <fieldset><legend>Candidatos</legend><div className={"poll-history-candidates"}>{catalog.map(([key,candidate])=><label key={key}><input type="checkbox" checked={!filters.hiddenCandidates.includes(key)} onChange={(event)=>setFilters({...filters,hiddenCandidates:event.target.checked ? filters.hiddenCandidates.filter((item)=>item!==key) : [...filters.hiddenCandidates,key]})}/><i style={{background:colors.get(key)}} aria-hidden="true"/>{candidateLabel(candidate)}{candidate.ballotNumber!=null && <small>· {candidate.ballotNumber}</small>}</label>)}</div></fieldset>
      <div className={"poll-history-filterSummary"}><p role="status">Exibindo {visible.length} de {all.length} pesquisas</p><Button variant="ghost" onClick={()=>setFilters({...emptyHistoryFilters})}>Limpar filtros</Button></div>
    </div>
    {filters.from && filters.to && filters.from>filters.to && <p role="alert">O início do período deve ser anterior ou igual ao fim.</p>}
    <div className="poll-history-overlayControl"><label><input type="checkbox" checked={showValues} onChange={(event)=>setShowValues(event.target.checked)}/> Mostrar valores nos pontos</label></div>
    <div className={"poll-history-milestones"} aria-label="Datas dos turnos de 2026">{roundMilestones.map((milestone)=><span key={milestone.round}>{milestone.label}</span>)}</div>
    {historyPanels(visible,filters.round).map((panel,index)=><div key={panel.round}>
      {index>0 && <div className={"poll-history-roundDivider"} role="separator" aria-label="Separação entre as disputas do primeiro e segundo turnos"><strong>Nova disputa · 2º turno</strong><span>04/10/2026 — 1º turno → 25/10/2026 — 2º turno</span></div>}
      <IndividualRound round={panel.round} polls={panel.polls} colors={colors} hidden={filters.hiddenCandidates} showValues={showValues}
        pending={panel.round===1 ? archive.loading : archive.loading||current.loading} unavailable={panel.round===1 ? !!archive.error && !archive.data : !!current.error && !current.data}/>
    </div>)}
    {filters.round!=="1" && <div className={"poll-history-overlayControl"}><label><input type="checkbox" checked={showModel&&canOverlay} disabled={!canOverlay} onChange={(event)=>setShowModel(event.target.checked)}/> Mostrar agregado do modelo em painel separado (snapshots existentes)</label>
      {!canOverlay && <p className="chart-note">Para comparar com os snapshots gerais, selecione todos os institutos e uma base que inclua votos válidos. O agregado não é recalculado pelos filtros.</p>}
    </div>}
    {showModel && canOverlay && <ModelSnapshots visibleIds={visibleIds} from={filters.from} to={filters.to}/>}
  </div>;
}
