"use client";
import Link from "next/link";
import { ArrowUpRight, FileSearch, AlertCircle } from "lucide-react";
import { Poll } from "@/lib/api";
import { useApi } from "@/lib/use-api";
import { Card, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";

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
  onEdit,
  onDelete,
}: {
  latest?: boolean;
  onEdit?: (poll: Poll) => void;
  onDelete?: (poll: Poll) => void;
}) {
  const { data, loading, error, retry } = useApi<Poll[]>(
    latest ? "/api/polls/latest" : "/api/polls",
  );
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
  if (error) return <ErrorState message={error} retry={retry} />;
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
