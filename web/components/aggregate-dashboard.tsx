"use client";

import { useState } from "react";
import Link from "next/link";
import { ArrowDown, ChartNoAxesCombined, History } from "lucide-react";
import {
  BarChart,
  Bar,
  CartesianGrid,
  Legend,
  LineChart,
  Line,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { Aggregate, AggregateMethod, AggregateSnapshot } from "@/lib/api";
import { useApi } from "@/lib/use-api";
import { modelPercent as percent, publicNumber } from "@/lib/presentation";
import { RefreshControls } from "@/components/refresh-controls";
import type { ApiState } from "@/lib/api-refresh";
import { date, ErrorState, number } from "@/components/poll-list";
import { Card, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";

export const methodLabels: Record<AggregateMethod, string> = {
  SIMPLE: "Média simples",
  RECENCY: "Recência",
  WEIGHTED: "Ponderada",
  CALIBRATED: "Calibrada",
};
const descriptions: Record<AggregateMethod, string> = {
  SIMPLE: "O mesmo peso para cada instituto.",
  RECENCY: "Peso pelo ponto médio do campo.",
  WEIGHTED: "Recência e precisão, com limite de influência.",
  CALIBRATED: "Recência, precisão e desempenho histórico.",
};
const seriesColors = ["#294b44", "#9aa7a2", "#647077", "#b9b6b0"];

function Composition({ aggregate }: { aggregate: Aggregate }) {
  return (
    <div className="composition-panel" id="aggregate-composition">
      <div className="section-heading">
        <div>
          <span className="section-kicker">TRANSPARÊNCIA</span>
          <h3>Composição · {methodLabels[aggregate.method]}</h3>
        </div>
        <Badge variant="outline">
          Referência: {date(aggregate.referenceDate)}
        </Badge>
      </div>
      <p className="chart-note">
        Percentuais normalizados publicados pela API. O peso representa a
        influência final de cada instituto.
      </p>
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>Instituto</TableHead>
            <TableHead>Resultado normalizado</TableHead>
            <TableHead>Campo</TableHead>
            <TableHead>Amostra</TableHead>
            <TableHead>Margem</TableHead>
            <TableHead>Peso</TableHead>
            <TableHead>Cap aplicado</TableHead>
            <TableHead>Fator histórico</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {aggregate.contributions.map((c) => (
            <TableRow key={c.pollId}>
              <TableCell className="font-medium">{c.pollsterName}</TableCell>
              <TableCell>
                {c.candidateResults.map((r) => (
                  <div key={r.candidateId}>
                    {r.candidateName}:{" "}
                    <strong>{percent(r.normalizedPercentage)}</strong>
                  </div>
                ))}
              </TableCell>
              <TableCell className="whitespace-nowrap">
                {date(c.fieldStart)} a {date(c.fieldEnd)}
              </TableCell>
              <TableCell>
                {c.sampleSize == null ? "Não informada" : number(c.sampleSize)}
              </TableCell>
              <TableCell>
                {c.marginOfError == null
                  ? "Não informada"
                  : `± ${number(c.marginOfError)} p.p.`}
              </TableCell>
              <TableCell className="font-semibold">
                {percent(c.normalizedWeight * 100)}
              </TableCell>
              <TableCell>
                {aggregate.cap ? (
                  c.capped ? (
                    <Badge variant="secondary">Sim</Badge>
                  ) : (
                    "Não"
                  )
                ) : (
                  "Não se aplica"
                )}
              </TableCell>
              <TableCell>
                {c.historicalWeightFactor == null
                  ? "—"
                  : `${number(c.historicalWeightFactor)}×`}
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
      {aggregate.cap && (
        <p className="chart-note">
          Limite solicitado: {percent(aggregate.cap.requested * 100)}.{" "}
          {aggregate.cap.feasible
            ? "O excesso é redistribuído proporcionalmente entre os demais institutos."
            : "Limite inviável com esta quantidade de institutos; distribuição igualitária aplicada."}
        </p>
      )}
      {aggregate.warnings.length > 0 && (
        <details className="model-notes">
          <summary>Notas do cálculo ({aggregate.warnings.length})</summary>
          <ul>
            {aggregate.warnings.map((warning, i) => (
              <li key={i}>{warning}</li>
            ))}
          </ul>
        </details>
      )}
      {aggregate.excludedPolls.length > 0 && (
        <details className="model-notes">
          <summary>
            Pesquisas excluídas ({aggregate.excludedPolls.length})
          </summary>
          <ul>
            {aggregate.excludedPolls.map((p) => (
              <li key={p.pollId}>
                <strong>{p.pollsterName}:</strong> {p.reason}
              </li>
            ))}
          </ul>
        </details>
      )}
    </div>
  );
}

function Comparison({ aggregates }: { aggregates: Aggregate[] }) {
  const candidates = aggregates[0].results;
  const data = aggregates.map((a) => ({
    model: methodLabels[a.method],
    ...Object.fromEntries(a.results.map((r) => [r.candidateId, r.percentage])),
  }));
  return (
    <div className="chart-panel">
      <h3>Comparação dos modelos</h3>
      <p className="chart-note">
        Votos válidos · escala completa de 0 a 100%. Consulte os valores dos
        cards para diferenças pequenas.
      </p>
      <div
        className="chart-frame"
        role="img"
        aria-label="Comparação dos percentuais dos candidatos nos quatro modelos. Os valores também estão nos cards acima."
      >
        <ResponsiveContainer width="100%" height="100%">
          <BarChart
            data={data}
            layout="vertical"
            margin={{ top: 10, right: 24, bottom: 5, left: 0 }}
          >
            <CartesianGrid horizontal={false} stroke="#e8eeeb" />
            <XAxis
              type="number"
              domain={[0, 100]}
              ticks={[0, 25, 50, 75, 100]}
              unit="%"
              tick={{ fontSize: 10 }}
              axisLine={false}
              tickLine={false}
            />
            <YAxis
              type="category"
              dataKey="model"
              width={86}
              tick={{ fontSize: 11 }}
              axisLine={false}
              tickLine={false}
            />
            <Tooltip formatter={(value) => percent(Number(value))} />
            <Legend wrapperStyle={{ fontSize: 12 }} />
            {candidates.map((c, i) => (
              <Bar
                key={c.candidateId}
                dataKey={c.candidateId}
                name={c.candidateName}
                fill={seriesColors[i % seriesColors.length]}
                radius={[0, 3, 3, 0]}
                maxBarSize={17}
              />
            ))}
          </BarChart>
        </ResponsiveContainer>
      </div>
    </div>
  );
}

function Models() {
  const state = useApi<Aggregate[]>("/api/aggregates", { refreshMs: 60000 });
  return <><RefreshControls {...state} hasData={state.data !== null} /><ModelsContent {...state} /></>;
}

function ModelsContent({ data, loading, error, retry }: ApiState<Aggregate[]> & { retry: () => void }) {
  const [selected, setSelected] = useState<AggregateMethod | null>(null);
  if (loading)
    return (
      <div
        className="aggregate-grid"
        role="status"
        aria-label="Calculando agregados"
      >
        {[0, 1, 2, 3].map((i) => (
          <Card key={i}>
            <CardContent className="space-y-6">
              <Skeleton className="h-6 w-2/3" />
              <Skeleton className="h-28 w-full" />
            </CardContent>
          </Card>
        ))}
      </div>
    );
  if (error && !data) return <ErrorState message={error} retry={retry} />;
  if (!data?.some((a) => a.pollCount > 0))
    return (
      <div className="data-state">
        <ChartNoAxesCombined size={28} />
        <h3>Nenhuma pesquisa elegível para o agregado</h3>
        <p>
          Cadastre pesquisas completas dos candidatos ativos, de institutos
          ativos, com publicação e campo até a data de referência.
        </p>
        {data?.[0]?.excludedPolls.map((p) => (
          <p key={p.pollId}>
            {p.pollsterName}: {p.reason}
          </p>
        ))}
        <Link href="/admin/pesquisas" className="text-link">
          Cadastrar pesquisa →
        </Link>
      </div>
    );
  const active = data.find((a) => a.method === selected);
  return (
    <>
      <p className="model-disclaimer">
        Agregados de pesquisas, sem previsão de vencedor. Apenas a pesquisa mais
        recente de cada instituto ativo, quando elegível.
      </p>
      <div className="aggregate-grid">
        {data.map((a) => (
          <Card
            key={a.method}
            className={`aggregate-card ${a.method === "CALIBRATED" ? "calibrated-card" : ""}`}
          >
            <CardContent>
              <div className="aggregate-card-title">
                <h3>{methodLabels[a.method]}</h3>
                {a.method === "CALIBRATED" && (
                  <Badge variant="secondary">Modelo calibrado</Badge>
                )}
              </div>
              <p className="model-description">{descriptions[a.method]}</p>
              <div className="aggregate-results">
                {a.results.map((r) => (
                  <div key={r.candidateId}>
                    <span>{r.candidateName}</span>
                    <strong>
                      {publicNumber(r.percentage)}
                      <small>%</small>
                    </strong>
                  </div>
                ))}
              </div>
              <p className="aggregate-spread">
                {a.spread?.leaderCandidateName ? (
                  <>
                    <strong>{publicNumber(a.spread.points)} p.p.</strong> de
                    diferença · {a.spread.leaderCandidateName}
                  </>
                ) : (
                  "Empate nos valores exibidos pelo modelo"
                )}
              </p>
              <dl className="aggregate-meta">
                <div>
                  <dt>Institutos</dt>
                  <dd>{a.pollsterCount}</dd>
                </div>
                <div>
                  <dt>
                    Entrevistados
                    {a.pollsWithoutSampleSize > 0 ? " informados" : ""}
                  </dt>
                  <dd>
                    {number(a.totalSampleSize)}
                    {a.pollsWithoutSampleSize > 0 ? "*" : ""}
                  </dd>
                </div>
              </dl>
              <p className="aggregate-updated">
                Última atualização: {date(a.calculatedAt, true)}
              </p>
              {a.method === "CALIBRATED" &&
                a.contributions.every(
                  (c) => c.historicalWeightFactor === 1,
                ) && (
                  <p className="chart-note">
                    Fatores históricos neutros (1×) neste cálculo.
                  </p>
                )}
              {a.cap && !a.cap.feasible && (
                <p className="cap-note">
                  Poucos institutos: pesos iguais; cap de{" "}
                  {percent(a.cap.requested * 100)} inviável.
                </p>
              )}
              <Button
                variant="outline"
                size="sm"
                className="composition-button"
                aria-expanded={selected === a.method}
                aria-controls="aggregate-composition"
                onClick={() =>
                  setSelected(selected === a.method ? null : a.method)
                }
              >
                Ver composição <ArrowDown size={13} />
              </Button>
            </CardContent>
          </Card>
        ))}
      </div>
      {active && <Composition aggregate={active} />}
      <Comparison aggregates={data} />
    </>
  );
}

function AggregateHistory() {
  const { data, loading, error, retry } = useApi<AggregateSnapshot[]>(
    "/api/aggregates/history",
  );
  const [method, setMethod] = useState<AggregateMethod>("CALIBRATED");
  const selected = (data || []).filter((s) => s.method === method);
  const candidates = Array.from(
    new Map(
      selected.flatMap((s) =>
        s.results.map((r) => [r.candidateId, r.candidateName] as const),
      ),
    ).entries(),
  );
  const points = selected.map((s) => ({
    timestamp: new Date(s.calculatedAt).getTime(),
    reference: s.referenceDate,
    ...Object.fromEntries(s.results.map((r) => [r.candidateId, r.percentage])),
  }));
  return (
    <section className="section">
      <div className="section-heading">
        <div>
          <span className="section-kicker">02 / HISTÓRICO DOS MODELOS</span>
          <h2>Evolução dos agregados</h2>
          <p>
            Agregados registrados em snapshots explícitos, em ordem de cálculo.
          </p>
        </div>
        <label className="history-selector">
          Modelo
          <select
            value={method}
            onChange={(e) => setMethod(e.target.value as AggregateMethod)}
          >
            {Object.entries(methodLabels).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
        </label>
      </div>
      {loading ? (
        <Skeleton
          className="h-72 w-full"
          aria-label="Carregando evolução"
          role="status"
        />
      ) : error ? (
        <ErrorState message={error} retry={retry} />
      ) : selected.length < 2 ? (
        <div className="data-state">
          <History size={26} />
          <h3>O histórico está começando</h3>
          <p>
            São necessários pelo menos dois snapshots deste modelo para mostrar
            sua evolução. Os snapshots são salvos pelo administrador.
          </p>
          <Link href="/admin/institutos" className="text-link">
            Administrar histórico →
          </Link>
        </div>
      ) : (
        <div className="chart-panel">
          <p className="chart-note">
            {methodLabels[method]} · percentuais em votos válidos · escala de 0
            a 100%. As linhas conectam snapshots, sem interpolação de
            estimativas.
          </p>
          <div
            className="chart-frame"
            role="img"
            aria-label={`Evolução do modelo ${methodLabels[method]}; tabela de valores disponível abaixo.`}
          >
            <ResponsiveContainer width="100%" height="100%">
              <LineChart
                data={points}
                margin={{ top: 15, right: 24, left: 0, bottom: 10 }}
              >
                <CartesianGrid stroke="#e8eeeb" />
                <XAxis
                  dataKey="timestamp"
                  type="number"
                  domain={["dataMin", "dataMax"]}
                  tickFormatter={(v) =>
                    new Intl.DateTimeFormat("pt-BR", {
                      day: "2-digit",
                      month: "2-digit",
                      hour: "2-digit",
                      minute: "2-digit",
                      timeZone: "America/Sao_Paulo",
                    }).format(new Date(v))
                  }
                  tick={{ fontSize: 10 }}
                  minTickGap={35}
                />
                <YAxis
                  domain={[0, 100]}
                  ticks={[0, 25, 50, 75, 100]}
                  unit="%"
                  tick={{ fontSize: 10 }}
                />
                <Tooltip
                  labelFormatter={(value) =>
                    date(new Date(Number(value)).toISOString(), true)
                  }
                  formatter={(value) => percent(Number(value))}
                />
                <Legend wrapperStyle={{ fontSize: 12 }} />
                {candidates.map(([id, name], i) => (
                  <Line
                    key={id}
                    dataKey={id}
                    name={name}
                    type="linear"
                    stroke={seriesColors[i % seriesColors.length]}
                    strokeWidth={2}
                    dot={{ r: 3 }}
                    connectNulls={false}
                    isAnimationActive={false}
                  />
                ))}
              </LineChart>
            </ResponsiveContainer>
          </div>
          <details className="model-notes">
            <summary>Ver valores dos snapshots</summary>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Cálculo (Brasília)</TableHead>
                  <TableHead>Referência</TableHead>
                  {candidates.map(([id, name]) => (
                    <TableHead key={id}>{name}</TableHead>
                  ))}
                </TableRow>
              </TableHeader>
              <TableBody>
                {selected.map((s) => (
                  <TableRow key={s.id}>
                    <TableCell>{date(s.calculatedAt, true)}</TableCell>
                    <TableCell>{date(s.referenceDate)}</TableCell>
                    {candidates.map(([id]) => (
                      <TableCell key={id}>
                        {s.results.find((r) => r.candidateId === id)
                          ?.percentage != null
                          ? percent(
                              s.results.find((r) => r.candidateId === id)!
                                .percentage,
                            )
                          : "—"}
                      </TableCell>
                    ))}
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </details>
        </div>
      )}
    </section>
  );
}

export function AggregateDashboard() {
  return (
    <>
      <section className="section">
        <div className="section-heading">
          <div>
            <span className="section-kicker">01 / PANORAMA</span>
            <h2>Agregado das pesquisas</h2>
          </div>
          <Link href="/metodologia" className="text-link">
            Como calculamos ↗
          </Link>
        </div>
        <Models />
      </section>
      <AggregateHistory />
    </>
  );
}
