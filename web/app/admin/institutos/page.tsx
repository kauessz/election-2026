"use client";

import Link from "next/link";
import { FormEvent, useState } from "react";
import { Save, History } from "lucide-react";
import { api, apiError, Pollster, PollsterPerformance } from "@/lib/api";
import { useApi } from "@/lib/use-api";
import { date, ErrorState, number } from "@/components/poll-list";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";

function PerformanceForm({
  pollsters,
  editing,
  onSaved,
  onCancel,
}: {
  pollsters: Pollster[];
  editing: PollsterPerformance | null;
  onSaved: () => void;
  onCancel: () => void;
}) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    setBusy(true);
    const data = new FormData(event.currentTarget);
    const text = (name: string) => String(data.get(name) || "").trim();
    const payload = {
      pollsterId: text("pollsterId"),
      electionKey: text("electionKey"),
      round: Number(text("round")),
      candidateError: text("candidateError")
        ? Number(text("candidateError"))
        : null,
      spreadError: Number(text("spreadError")),
      sourceUrl: text("sourceUrl") || null,
      sourceDescription: text("sourceDescription") || null,
    };
    try {
      if (editing)
        await api.put(
          `/api/admin/pollster-performances/${editing.id}`,
          payload,
        );
      else await api.post("/api/admin/pollster-performances", payload);
      onSaved();
    } catch (error) {
      setError(apiError(error));
    } finally {
      setBusy(false);
    }
  }
  return (
    <form className="poll-form" onSubmit={submit}>
      <fieldset disabled={busy}>
        <div className="form-section">
          <div className="form-grid">
            <label className="full-width">
              Instituto *
              <select
                required
                name="pollsterId"
                defaultValue={editing?.pollster.id || ""}
              >
                <option value="" disabled>
                  Selecione um instituto
                </option>
                {pollsters.map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.name}
                    {p.active ? "" : " (inativo)"}
                  </option>
                ))}
              </select>
            </label>
            <label>
              Eleição *
              <Input
                name="electionKey"
                maxLength={100}
                required
                defaultValue={editing?.electionKey || "BR-PRESIDENT-2026"}
              />
              <small>Chave exata usada na configuração da calibração.</small>
            </label>
            <label>
              Turno *
              <select name="round" defaultValue={editing?.round || 1}>
                <option value="1">1º turno</option>
                <option value="2">2º turno</option>
              </select>
            </label>
            <label>
              Erro por candidato (p.p.)
              <Input
                type="number"
                name="candidateError"
                min="0"
                max="99999999.9999"
                step="0.0001"
                defaultValue={editing?.candidateError ?? ""}
              />
            </label>
            <label>
              Erro da diferença (p.p.) *
              <Input
                type="number"
                required
                name="spreadError"
                min="0"
                max="99999999.9999"
                step="0.0001"
                defaultValue={editing?.spreadError ?? ""}
              />
            </label>
            <label className="full-width">
              URL da fonte
              <Input
                type="url"
                name="sourceUrl"
                maxLength={2000}
                placeholder="https://"
                defaultValue={editing?.sourceUrl || ""}
              />
            </label>
            <label className="full-width">
              Descrição da fonte e do cálculo
              <textarea
                name="sourceDescription"
                rows={3}
                maxLength={2000}
                defaultValue={editing?.sourceDescription || ""}
                placeholder="Registre a referência e como os erros foram obtidos."
              />
            </label>
          </div>
          <p className="info-note">
            O fator histórico é calculado pelo backend a partir do erro da
            diferença. Não há edição manual de score.
          </p>
        </div>
        {error && (
          <div role="alert" className="form-error">
            {error}
          </div>
        )}
        <div className="form-actions">
          <span>* Campos obrigatórios</span>
          {editing && (
            <Button type="button" variant="outline" onClick={onCancel}>
              Cancelar
            </Button>
          )}
          <Button type="submit" disabled={busy}>
            <Save size={16} />
            {busy
              ? "Salvando…"
              : editing
                ? "Salvar alterações"
                : "Cadastrar desempenho"}
          </Button>
        </div>
      </fieldset>
    </form>
  );
}

export default function InstitutesPage() {
  const institutes = useApi<Pollster[]>("/api/pollsters");
  const performances = useApi<PollsterPerformance[]>(
    "/api/admin/pollster-performances",
  );
  const [editing, setEditing] = useState<PollsterPerformance | null>(null);
  const [revision, setRevision] = useState(0);
  const [feedback, setFeedback] = useState<string | null>(null);
  const [snapshotBusy, setSnapshotBusy] = useState(false);
  const [snapshotError, setSnapshotError] = useState<string | null>(null);
  function saved() {
    setFeedback(editing ? "Desempenho atualizado." : "Desempenho cadastrado.");
    setEditing(null);
    setRevision((r) => r + 1);
    performances.retry();
  }
  async function snapshot() {
    setSnapshotBusy(true);
    setSnapshotError(null);
    setFeedback(null);
    try {
      await api.post("/api/admin/aggregates/snapshot");
      setFeedback(
        "Snapshots dos quatro modelos salvos. Repetições no mesmo minuto reutilizam os registros existentes.",
      );
    } catch (error) {
      setSnapshotError(apiError(error));
    } finally {
      setSnapshotBusy(false);
    }
  }
  return (
    <>
      <div className="page-intro">
        <span className="eyebrow">ADMINISTRAÇÃO / INSTITUTOS</span>
        <h1>
          Desempenho histórico<span className="heading-dot">.</span>
        </h1>
        <p>
          Cadastre erros verificados para calibrar a influência dos institutos.
          Nenhum desempenho fictício é incluído.
        </p>
        <div className="admin-toolbar">
          <Link href="/admin/pesquisas" className="text-link">
            ← Cadastro de pesquisas
          </Link>
          <Badge variant="outline">Sem autenticação</Badge>
        </div>
      </div>
      <div className="snapshot-toolbar">
        <div>
          <h3>Histórico dos agregados</h3>
          <p>
            Registre os quatro modelos para acompanhar a evolução. Consultar a
            home não cria snapshots.
          </p>
        </div>
        <Button variant="outline" onClick={snapshot} disabled={snapshotBusy}>
          <History size={16} />
          {snapshotBusy ? "Salvando…" : "Salvar snapshot dos quatro modelos"}
        </Button>
      </div>
      {feedback && (
        <div role="status" className="success-message">
          {feedback}
        </div>
      )}
      {snapshotError && (
        <div role="alert" className="form-error mb-6">
          {snapshotError}
        </div>
      )}
      <div className="admin-layout">
        <Card id="performance-form">
          <CardContent>
            <h2>
              {editing
                ? `Editar · ${editing.pollster.name}`
                : "Novo desempenho"}
            </h2>
            {institutes.loading ? (
              <Skeleton
                className="h-80 w-full mt-6"
                role="status"
                aria-label="Carregando institutos"
              />
            ) : institutes.error ? (
              <ErrorState message={institutes.error} retry={institutes.retry} />
            ) : !institutes.data?.length ? (
              <div className="data-state">
                <h3>Nenhum instituto cadastrado</h3>
                <p>
                  Cadastre institutos pela API antes de informar seu desempenho.
                </p>
              </div>
            ) : (
              <PerformanceForm
                key={`${editing?.id || "new"}-${revision}`}
                pollsters={institutes.data}
                editing={editing}
                onSaved={saved}
                onCancel={() => setEditing(null)}
              />
            )}
          </CardContent>
        </Card>
        <aside className="admin-aside">
          <span className="section-kicker">CALIBRAÇÃO TRANSPARENTE</span>
          <h3>Histórico altera peso, não resultado.</h3>
          <p>
            O modelo usa apenas registros da eleição e do turno configurados. O
            padrão é BR-PRESIDENT-2026, turno 1.
          </p>
          <hr />
          <h4>Qual erro informar?</h4>
          <p>
            SpreadError é o erro absoluto, em pontos percentuais, da diferença
            entre os candidatos em relação ao resultado de referência
            verificado.
          </p>
          <hr />
          <h4>Dados com procedência</h4>
          <p>
            Informe a fonte e a forma de cálculo. Se não houver dados
            verificados, mantenha o instituto sem histórico: seu fator será 1.
          </p>
          <Link href="/metodologia" className="text-link">
            Consultar metodologia ↗
          </Link>
        </aside>
      </div>
      <section className="section">
        <div className="section-heading">
          <h2>Desempenhos cadastrados</h2>
        </div>
        {performances.loading ? (
          <Skeleton
            className="h-40 w-full"
            role="status"
            aria-label="Carregando desempenhos"
          />
        ) : performances.error ? (
          <ErrorState message={performances.error} retry={performances.retry} />
        ) : !performances.data?.length ? (
          <div className="data-state">
            <History size={26} />
            <h3>Nenhum desempenho cadastrado</h3>
            <p>
              Sem dados históricos verificados, todos os institutos usam fator
              neutro (1).
            </p>
          </div>
        ) : (
          <div className="composition-panel">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Instituto</TableHead>
                  <TableHead>Eleição / turno</TableHead>
                  <TableHead>Erro por candidato</TableHead>
                  <TableHead>Erro da diferença</TableHead>
                  <TableHead>Fator derivado</TableHead>
                  <TableHead>Fonte</TableHead>
                  <TableHead>Atualização</TableHead>
                  <TableHead>Ação</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {performances.data.map((p) => (
                  <TableRow key={p.id}>
                    <TableCell className="font-medium">
                      {p.pollster.name}
                    </TableCell>
                    <TableCell>
                      {p.electionKey} · {p.round}º
                    </TableCell>
                    <TableCell>
                      {p.candidateError == null
                        ? "—"
                        : `${number(p.candidateError)} p.p.`}
                    </TableCell>
                    <TableCell>
                      {p.spreadError == null
                        ? "—"
                        : `${number(p.spreadError)} p.p.`}
                    </TableCell>
                    <TableCell>{number(p.historicalScore)}×</TableCell>
                    <TableCell>
                      {p.sourceUrl ? (
                        <a
                          className="text-link"
                          target="_blank"
                          rel="noopener noreferrer"
                          href={p.sourceUrl}
                        >
                          Fonte ↗
                        </a>
                      ) : (
                        "Não informada"
                      )}
                    </TableCell>
                    <TableCell>{date(p.updatedAt, true)}</TableCell>
                    <TableCell>
                      <Button
                        variant="outline"
                        size="sm"
                        onClick={() => {
                          setEditing(p);
                          setFeedback(null);
                          document
                            .getElementById("performance-form")
                            ?.scrollIntoView({ behavior: "smooth" });
                        }}
                      >
                        Editar
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
        )}
      </section>
    </>
  );
}
