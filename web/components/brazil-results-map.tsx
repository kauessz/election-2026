"use client";
import { useId, useRef, useState } from "react";
import type { OfficialResponse } from "@/lib/official-api";
import { officialMapSummary, isMappableUf, shade } from "@/lib/official-map";
import { brazilStates, brazilViewBox } from "@/lib/brazil-geography";
import { publicNumber, publicPercent } from "@/lib/presentation";

type Mode = "leader" | "difference" | "progress";
const palette = ["#475569", "#946f48", "#65786d"];
const modes: [Mode, string][] = [["leader", "Líder"], ["difference", "Diferença"], ["progress", "Apuração"]];
const percentage = (value: number | null) => value == null ? "—" : publicPercent(value);

export function BrazilResultsMap({ states, connectionError = false }: { states: OfficialResponse[]; connectionError?: boolean }) {
  const [mode, setMode] = useState<Mode>("leader");
  const [selected, setSelected] = useState<string | null>(null);
  const selectOnly = useRef(false);
  const patternId = useId().replace(/:/g, "");
  const rows = new Map(states.filter((row) => isMappableUf(row.scope.code)).map((row) => [row.scope.code.toUpperCase(), row]));
  const candidates = [...new Map(states.flatMap((row) => row.result?.candidates ?? []).map((candidate) => [candidate.candidateNumber, candidate])).values()]
    .sort((a, b) => a.candidateNumber.localeCompare(b.candidateNumber));
  const colors = new Map(candidates.map((candidate, i) => [candidate.candidateNumber, palette[i % palette.length]]));
  const row = selected ? rows.get(selected) : undefined;
  const summary = officialMapSummary(row);
  const geography = brazilStates.find((state) => state.uf === selected);
  function fill(uf: string) {
    const result = officialMapSummary(rows.get(uf));
    if (mode === "progress") return result.progress == null ? `url(#${patternId})` : shade("#475569", result.progress / 100);
    if (!result.counting) return `url(#${patternId})`;
    if (!result.leader) return "#e2e5e1";
    const color = colors.get(result.leader.candidateNumber) ?? palette[0];
    return mode === "difference" ? shade(color, .15 + .85 * Math.min((result.spread ?? 0) / 30, 1)) : color;
  }
  function description(uf: string, name: string) {
    const data = officialMapSummary(rows.get(uf));
    const status = connectionError || data.stale ? " Dados desatualizados." : data.final ? " Totalização finalizada." : "";
    return `${name} / ${uf}. ${data.counting ? data.tied ? "Empate." : `Líder: ${data.leader?.displayName || data.leader?.name}.` : "Sem votos observados."} Seções totalizadas: ${percentage(data.progress)}.${status} Abrir resultado estadual.`;
  }
  return <section className="state-map" aria-labelledby="state-map-title">
    <div className="state-map-heading"><div><span className="section-kicker">RESULTADOS OFICIAIS / BRASIL</span><h2 id="state-map-title">Mapa da apuração</h2></div>
      <div className="map-modes" role="group" aria-label="Modo de visualização do mapa">{modes.map(([value, label]) => <button type="button" key={value} aria-pressed={mode === value} onClick={() => setMode(value)}>{label}</button>)}</div>
    </div>
    <p>Fonte: Tribunal Superior Eleitoral (TSE)</p><p className="chart-note">Mapa baseado nos resultados oficiais recebidos pela plataforma.</p>
    <label className="map-state-picker">Consultar UF<select value={selected ?? ""} onChange={(event) => setSelected(event.target.value || null)}><option value="">Selecione um estado</option>{brazilStates.map((state) => <option key={state.uf} value={state.uf}>{state.uf} · {state.name}</option>)}</select></label>
    <div className="state-map-layout">
      <svg viewBox={brazilViewBox} className="brazil-map" role="group" aria-label="Mapa interativo dos 26 estados e Distrito Federal" aria-describedby="map-help">
        <defs><pattern id={patternId} width="6" height="6" patternUnits="userSpaceOnUse"><rect width="6" height="6" fill="#f0f2ef" /><path d="M0 6L6 0" stroke="#d7ddd9" strokeWidth=".7" /></pattern></defs>
        {[...brazilStates].sort((a, b) => Number(a.uf === "DF") - Number(b.uf === "DF")).map((state) => <a key={state.uf} href={`/apuracao/${state.uf.toLowerCase()}`} tabIndex={0} aria-label={description(state.uf, state.name)}
          className={`map-state ${selected === state.uf ? "map-state-selected" : ""}`}
          onFocus={() => setSelected(state.uf)}
          onPointerEnter={(event) => { if (event.pointerType !== "touch") setSelected(state.uf); }}
          onPointerDown={(event) => { selectOnly.current = event.pointerType === "touch" && selected !== state.uf; }}
          onClick={(event) => { if (selectOnly.current) { event.preventDefault(); setSelected(state.uf); } selectOnly.current = false; }}>
          <title>{description(state.uf, state.name)}</title>
          <path d={state.path} fill={fill(state.uf)} fillRule="evenodd" />
          {state.uf === "DF" && <circle cx={state.center[0]} cy={state.center[1]} r={11} fill={fill(state.uf)} strokeDasharray="2 2" className="map-df-target" />}
          <text x={state.center[0]} y={state.center[1]} className={mode === "leader" && officialMapSummary(rows.get(state.uf)).leader ? "map-label-light" : ""}>{state.uf}</text>
        </a>)}
      </svg>
      <div className="map-details" role="status" aria-live="polite" aria-atomic="true">
        {geography ? <><h3>{(row?.scope.name || geography.name).toLocaleUpperCase("pt-BR")} / {geography.uf}</h3>
          {summary.counting ? <><dl>{summary.candidates.map((candidate) => <div key={candidate.candidateNumber}><dt>{candidate.displayName || candidate.name}</dt><dd>{percentage(candidate.percentage)}</dd></div>)}
            <div className="map-details-spread"><dt>Diferença</dt><dd>{summary.spread == null ? "—" : `${publicNumber(summary.spread)} p.p.`}</dd></div>
          </dl>{summary.tied && <p>Empate em votos observados.</p>}</> : <p>{row?.status === "UNAVAILABLE" || !row ? "Resultado ainda indisponível" : "Apuração ainda não iniciada"}</p>}
          <dl><div><dt>Seções totalizadas</dt><dd>{percentage(summary.progress)}</dd></div><div><dt>Atualizado</dt><dd>{row?.lastSuccessfulUpdate ? new Date(row.lastSuccessfulUpdate).toLocaleTimeString("pt-BR", { timeZone: "America/Sao_Paulo" }) : "—"}</dd></div></dl>
          {(summary.stale || connectionError) && <p className="official-notice">Dados desatualizados. Último resultado recebido preservado.</p>}
          {summary.unavailable && <p>Fonte indisponível.</p>}{summary.final && <p>Totalização finalizada.</p>}
          <a href={`/apuracao/${geography.uf.toLowerCase()}`} className="text-link">Abrir resultado de {geography.uf} →</a>
        </> : <><h3>Explore os estados</h3><p>Passe o mouse ou navegue com Tab para consultar os dados de cada UF.</p><p>No celular, toque para ver detalhes e toque novamente para abrir o resultado.</p></>}
      </div>
    </div>
    <div className="map-legend" aria-label="Legenda do mapa">
      {mode !== "progress" && candidates.map((candidate) => <span key={candidate.candidateNumber}><i style={{ background: colors.get(candidate.candidateNumber) }} aria-hidden="true" />{candidate.displayName || candidate.name}</span>)}
      {mode === "progress" ? <><span><i className="map-progress-gradient" aria-hidden="true" />Seções: 0% (claro) → 100% (escuro)</span><span><i className="map-no-data" aria-hidden="true" />Sem resultado recebido</span></> : <><span><i className="map-no-data" aria-hidden="true" />Sem votos observados</span><span><i style={{ background: "#e2e5e1" }} aria-hidden="true" />Empate</span></>}
      {mode === "difference" && <span>Intensidade: 0 a 30 p.p. · 30 p.p. ou mais: máxima</span>}
    </div>
    <p id="map-help" className="chart-note">Use Tab e Enter para abrir uma UF. No toque: primeiro toque mostra detalhes; segundo abre a UF. DF tem área ampliada para interação. A tabela abaixo contém os mesmos resultados. Horários de Brasília. Exterior (ZZ) não é desenhado.</p>
  </section>;
}
