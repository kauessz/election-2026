"use client";
import { useState } from "react";
import Link from "next/link";
import { ArrowUpRight, FileSearch, AlertCircle } from "lucide-react";
import { Poll } from "@/lib/api";
import { useApi } from "@/lib/use-api";
import { Card, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { Input } from "@/components/ui/input";
import { RefreshControls } from "@/components/refresh-controls";
import { emptyPollFilters, filterPolls, PollFilters } from "@/lib/poll-filters";

export const number = (value: number) =>
  value.toLocaleString("pt-BR", { maximumFractionDigits: 2 });
export function date(value: string, time = false) {
  return new Intl.DateTimeFormat(
    "pt-BR",
    time
      ? {
          dateStyle: "short",
          timeStyle: "short",
          timeZone: "America/Sao_Paulo",
        }
      : { dateStyle: "short", timeZone: "UTC" },
  ).format(new Date(time ? value : `${value.slice(0, 10)}T12:00:00Z`));
}
export function ErrorState({
  message,
  retry,
}: {
  message: string;
  retry: () => void;
}) {
  return (
    <div className="data-state" role="alert">
      <AlertCircle size={24} />
      <h3>Não foi possível carregar os dados</h3>
      <p>{message}</p>
      <Button variant="outline" onClick={retry}>
        Tentar novamente
      </Button>
    </div>
  );
}
export function PollList({
  latest = false,
  showFilters = false,
  onEdit,
  onDelete,
}: {
  latest?: boolean;
  showFilters?: boolean;
  onEdit?: (poll: Poll) => void;
  onDelete?: (poll: Poll) => void;
}) {
  const state = useApi<Poll[]>(
    latest ? "/api/polls/latest" : "/api/polls",
    { refreshMs: latest || showFilters ? 60000 : undefined },
  );
  const { data } = state;
  const [filters, setFilters] = useState<PollFilters>({ ...emptyPollFilters });
  const pollsters = [...new Map((data ?? []).map((poll) => [poll.pollster.id, poll.pollster])).values()]
    .sort((a, b) => a.name.localeCompare(b.name, "pt-BR"));
  // lastUpdatedAt changes after each refresh, keeping rolling publication windows current.
  const visible = showFilters ? filterPolls(data ?? [], filters, Date.parse(state.lastUpdatedAt ?? "1970-01-01")) : data;
  return <>
    {(latest || showFilters) && <RefreshControls {...state} hasData={data !== null} label="Atualizar pesquisas" />}
    {showFilters && <div className="poll-filters" role="search" aria-label="Filtrar pesquisas">
      <label className="poll-search">Buscar instituto ou registro TSE
        <Input type="search" placeholder="Nome, slug ou registro TSE" value={filters.search} onChange={(e) => setFilters({ ...filters, search: e.target.value })} />
      </label>
      <label>Instituto<select value={filters.pollsterId} onChange={(e) => setFilters({ ...filters, pollsterId: e.target.value })}>
        <option value="">Todos os institutos</option>{pollsters.map((pollster) => <option value={pollster.id} key={pollster.id}>{pollster.name}</option>)}
      </select></label>
      <label>Base<select value={filters.voteBasis} onChange={(e) => setFilters({ ...filters, voteBasis: e.target.value as PollFilters["voteBasis"] })}>
        <option value="">Todas</option><option value="VALID">Votos válidos</option><option value="TOTAL">Votos totais</option>
      </select></label>
      <label>Publicação<select value={filters.days} onChange={(e) => setFilters({ ...filters, days: Number(e.target.value) as PollFilters["days"] })}>
        <option value={0}>Todas</option><option value={1}>Últimas 24h</option><option value={3}>Últimos 3 dias</option><option value={7}>Últimos 7 dias</option>
      </select></label>
      <label className="poll-latest-toggle"><input type="checkbox" checked={filters.latestOnly} onChange={(e) => setFilters({ ...filters, latestOnly: e.target.checked })} />Somente a mais recente de cada instituto</label>
      <div className="poll-filter-summary"><p role="status">Exibindo {visible?.length ?? 0} de {data?.length ?? 0} pesquisas</p><Button variant="ghost" onClick={() => setFilters({ ...emptyPollFilters })}>Limpar filtros</Button></div>
    </div>}
    {showFilters && data?.length && !visible?.length ? <div className="data-state"><FileSearch size={28} /><h3>Nenhuma pesquisa corresponde aos filtros</h3><p>Amplie o período ou limpe os filtros para ver outros levantamentos.</p><Button variant="outline" onClick={() => setFilters({ ...emptyPollFilters })}>Limpar filtros</Button></div> : <PollCards data={visible ?? null} loading={state.loading} error={state.error} retry={state.retry} latest={latest} onEdit={onEdit} onDelete={onDelete} />}
  </>;
}

function PollCards({ data, loading, error, retry, latest, onEdit, onDelete }: {
  data: Poll[] | null; loading: boolean; error: string | null; retry: () => void; latest: boolean;
  onEdit?: (poll: Poll) => void; onDelete?: (poll: Poll) => void;
}) {
  if (loading)
    return (
      <div
        className="poll-grid"
        aria-label="Carregando pesquisas"
        role="status"
      >
        {[0, 1].map((i) => (
          <Card key={i}>
            <CardContent className="space-y-5 pt-6">
              <Skeleton className="h-5 w-1/3" />
              <Skeleton className="h-24 w-full" />
              <Skeleton className="h-4 w-2/3" />
            </CardContent>
          </Card>
        ))}
      </div>
    );
  if (error && !data) return <ErrorState message={error} retry={retry} />;
  if (!data?.length)
    return (
      <div className="data-state">
        <FileSearch size={28} />
        <h3>Nenhuma pesquisa cadastrada</h3>
        <p>
          As pesquisas aparecerão aqui assim que forem adicionadas à plataforma.
        </p>
        <Link href="/admin/pesquisas" className="text-link">
          Cadastrar a primeira pesquisa <ArrowUpRight size={15} />
        </Link>
      </div>
    );
  return (
    <div className="poll-grid">
      {data.map((poll) => (
        <Card key={poll.id} className="poll-card">
          <CardContent>
            <div className="poll-card-header">
              <div>
                <span className="section-kicker">
                  {date(poll.publishedAt, true)}
                </span>
                <h3>{poll.pollster.name}</h3>
              </div>
              <Badge variant="outline">
                {poll.voteBasis === "TOTAL" ? "Votos totais" : "Votos válidos"}
              </Badge>
            </div>
            <div className="result-grid">
              {poll.results.map((result) => (
                <div key={result.candidateId} className="candidate-result">
                  <span>{result.candidateName}</span>
                  <strong>
                    {number(result.normalizedPercentage)}
                    <small>%</small>
                  </strong>
                  <div className="result-track">
                    <div style={{ width: `${result.normalizedPercentage}%` }} />
                  </div>
                  {!latest && (
                    <span className="original">
                      Original: {number(result.originalPercentage)}%
                    </span>
                  )}
                </div>
              ))}
            </div>
            <p className="result-note">
              {poll.voteBasis === "TOTAL"
                ? "Resultados normalizados para votos válidos"
                : "Resultados publicados em votos válidos"}
            </p>
            <dl className="poll-meta">
              <div>
                <dt>Período de campo</dt>
                <dd>
                  {date(poll.fieldStart)} a {date(poll.fieldEnd)}
                </dd>
              </div>
              <div>
                <dt>Amostra</dt>
                <dd>
                  {poll.sampleSize
                    ? `${number(poll.sampleSize)} pessoas`
                    : "Não informada"}
                </dd>
              </div>
              <div>
                <dt>Margem de erro</dt>
                <dd>
                  {poll.marginOfError != null
                    ? `± ${number(poll.marginOfError)} p.p.`
                    : "Não informada"}
                </dd>
              </div>
            </dl>
            {!latest && (
              <div className="poll-details">
                <p>
                  <span>Registro TSE</span>
                  {poll.registrationNumber || "Não informado"}
                </p>
                <p>
                  <span>Metodologia</span>
                  {poll.methodology || "Não informada"}
                </p>
              </div>
            )}
            <div className="poll-card-footer">
              <a
                className="text-link"
                href={poll.sourceUrl}
                target="_blank"
                rel="noopener noreferrer"
              >
                Consultar fonte <ArrowUpRight size={15} />
              </a>
              {onEdit && (
                <Button
                  size="sm"
                  variant="outline"
                  onClick={() => onEdit(poll)}
                >
                  Editar
                </Button>
              )}
              {onDelete && (
                <Button
                  size="sm"
                  variant="destructive"
                  onClick={() => onDelete(poll)}
                >
                  Excluir
                </Button>
              )}
            </div>
          </CardContent>
        </Card>
      ))}
    </div>
  );
}
