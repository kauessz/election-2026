"use client";
import { FormEvent, useRef, useState } from "react";
import Link from "next/link";
import { Save, Info } from "lucide-react";
import {
  api,
  apiError,
  Candidate,
  Poll,
  PollRequest,
  Pollster,
  VoteBasis,
} from "@/lib/api";
import { useApi } from "@/lib/use-api";
import { ErrorState, PollList } from "@/components/poll-list";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Card, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";

function localDateTime(instant: string) {
  const date = new Date(instant);
  return new Date(date.getTime() - date.getTimezoneOffset() * 60000)
    .toISOString()
    .slice(0, 16);
}
function PollForm({
  candidates,
  pollsters,
  editing,
  onSaved,
  onCancel,
}: {
  candidates: Candidate[];
  pollsters: Pollster[];
  editing: Poll | null;
  onSaved: () => void;
  onCancel: () => void;
}) {
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const form = useRef<HTMLFormElement>(null);
  // Include inactive candidates already present in the edited poll to avoid losing results.
  const available = candidates.filter(
    (c) => c.active || editing?.results.some((r) => r.candidateId === c.id),
  );
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    const data = new FormData(event.currentTarget);
    const text = (name: string) => String(data.get(name) || "").trim();
    const optionalNumber = (name: string) =>
      text(name) ? Number(text(name)) : null;
    if (text("fieldEnd") < text("fieldStart")) {
      setError("O fim do campo não pode ser anterior ao início.");
      return;
    }
    const payload: PollRequest = {
      pollsterId: text("pollsterId"),
      fieldStart: text("fieldStart"),
      fieldEnd: text("fieldEnd"),
      publishedAt: new Date(text("publishedAt")).toISOString(),
      sampleSize: optionalNumber("sampleSize"),
      marginOfError: optionalNumber("marginOfError"),
      methodology: text("methodology") || null,
      registrationNumber: text("registrationNumber") || null,
      sourceUrl: text("sourceUrl"),
      voteBasis: text("voteBasis") as VoteBasis,
      results: available.map((c) => ({
        candidateId: c.id,
        originalPercentage: Number(text(`result-${c.id}`)),
      })),
    };
    setSaving(true);
    try {
      if (editing) await api.put(`/api/admin/polls/${editing.id}`, payload);
      else await api.post("/api/admin/polls", payload);
      form.current?.reset();
      onSaved();
    } catch (error) {
      setError(apiError(error));
    } finally {
      setSaving(false);
    }
  }
  return (
    <form ref={form} onSubmit={submit} className="poll-form">
      <fieldset disabled={saving}>
        <div className="form-section">
          <span className="section-kicker">01 / IDENTIFICAÇÃO</span>
          <h3>Sobre o levantamento</h3>
          <div className="form-grid">
            <label className="full-width">
              Instituto *
              <select
                name="pollsterId"
                required
                defaultValue={editing?.pollster.id || ""}
              >
                <option value="" disabled>
                  Selecione um instituto
                </option>
                {pollsters
                  .filter((p) => p.active || p.id === editing?.pollster.id)
                  .map((p) => (
                    <option key={p.id} value={p.id}>
                      {p.name}
                    </option>
                  ))}
              </select>
            </label>
            <label>
              Início do campo *
              <Input
                type="date"
                name="fieldStart"
                required
                defaultValue={editing?.fieldStart}
              />
            </label>
            <label>
              Fim do campo *
              <Input
                type="date"
                name="fieldEnd"
                required
                defaultValue={editing?.fieldEnd}
              />
            </label>
            <label className="full-width">
              Publicação *
              <Input
                type="datetime-local"
                name="publishedAt"
                required
                defaultValue={
                  editing ? localDateTime(editing.publishedAt) : undefined
                }
              />
              <small>Horário local do seu dispositivo.</small>
            </label>
            <label>
              Amostra
              <Input
                type="number"
                name="sampleSize"
                min="1"
                step="1"
                placeholder="Ex.: 2000"
                defaultValue={editing?.sampleSize ?? ""}
              />
            </label>
            <label>
              Margem de erro (p.p.)
              <Input
                type="number"
                name="marginOfError"
                min="0.0001"
                max="100"
                step="0.0001"
                placeholder="Ex.: 2"
                defaultValue={editing?.marginOfError ?? ""}
              />
            </label>
            <label className="full-width">
              Metodologia
              <textarea
                name="methodology"
                maxLength={5000}
                rows={3}
                placeholder="Descreva a forma de coleta dos dados"
                defaultValue={editing?.methodology || ""}
              />
            </label>
            <label className="full-width">
              Registro TSE
              <Input
                name="registrationNumber"
                maxLength={100}
                placeholder="Ex.: BR-00000/2026"
                defaultValue={editing?.registrationNumber || ""}
              />
            </label>
            <label className="full-width">
              URL da fonte *
              <Input
                type="url"
                name="sourceUrl"
                required
                maxLength={2000}
                placeholder="https://"
                defaultValue={editing?.sourceUrl}
              />
            </label>
          </div>
        </div>
        <div className="form-section">
          <span className="section-kicker">02 / RESULTADOS PUBLICADOS</span>
          <h3>Intenção de voto</h3>
          <p className="form-help">
            Informe os percentuais originais publicados pelo instituto.
          </p>
          <div className="form-grid">
            <label className="full-width">
              Base de votos *
              <select
                name="voteBasis"
                defaultValue={editing?.voteBasis || "TOTAL"}
              >
                <option value="TOTAL">TOTAL — votos totais</option>
                <option value="VALID">VALID — votos válidos</option>
              </select>
            </label>
            {available.map((candidate) => (
              <label key={candidate.id}>
                {candidate.displayName} (%) *
                <Input
                  name={`result-${candidate.id}`}
                  type="number"
                  min="0"
                  max="100"
                  step="0.0001"
                  required
                  placeholder="0,0000"
                  defaultValue={
                    editing?.results.find((r) => r.candidateId === candidate.id)
                      ?.originalPercentage ?? ""
                  }
                />
              </label>
            ))}
          </div>
          <p className="info-note">
            <Info size={16} /> A normalização para votos válidos será calculada
            pelo backend ao salvar.
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
              Cancelar edição
            </Button>
          )}
          <Button type="submit" disabled={saving || !available.length}>
            <Save size={16} />
            {saving
              ? "Salvando…"
              : editing
                ? "Salvar alterações"
                : "Cadastrar pesquisa"}
          </Button>
        </div>
      </fieldset>
    </form>
  );
}

export default function AdminPollsPage() {
  const candidates = useApi<Candidate[]>("/api/candidates");
  const pollsters = useApi<Pollster[]>("/api/pollsters");
  const [revision, setRevision] = useState(0);
  const [editing, setEditing] = useState<Poll | null>(null);
  const [deleting, setDeleting] = useState<Poll | null>(null);
  const [busy, setBusy] = useState(false);
  const [feedback, setFeedback] = useState<string | null>(null);
  const [deleteError, setDeleteError] = useState<string | null>(null);
  function saved() {
    setFeedback(
      editing
        ? "Pesquisa atualizada com sucesso."
        : "Pesquisa cadastrada com sucesso.",
    );
    setEditing(null);
    setRevision((r) => r + 1);
  }
  async function remove() {
    if (!deleting) return;
    setBusy(true);
    setDeleteError(null);
    try {
      await api.delete(`/api/admin/polls/${deleting.id}`);
      if (editing?.id === deleting.id) setEditing(null);
      setDeleting(null);
      setFeedback("Pesquisa excluída.");
      setRevision((r) => r + 1);
    } catch (error) {
      setDeleteError(apiError(error));
    } finally {
      setBusy(false);
    }
  }
  return (
    <>
      <div className="page-intro">
        <span className="eyebrow">ADMINISTRAÇÃO / PESQUISAS</span>
        <div className="hero-row">
          <div>
            <h1>
              Cadastro de pesquisas<span className="heading-dot">.</span>
            </h1>
            <p>
              Adicione os dados publicados e mantenha o histórico atualizado.
            </p>
          </div>
          <Badge variant="outline">Ambiente sem autenticação</Badge>
        </div>
      </div>
      <div className="admin-toolbar">
        <Link href="/admin/institutos" className="text-link">
          Desempenho dos institutos e snapshots →
        </Link>
      </div>
      {feedback && (
        <div role="status" className="success-message">
          {feedback}
        </div>
      )}
      <div className="admin-layout">
        <Card id="poll-form">
          <CardContent>
            <h2>
              {editing ? `Editar · ${editing.pollster.name}` : "Nova pesquisa"}
            </h2>
            {candidates.loading || pollsters.loading ? (
              <div
                className="space-y-6 py-6"
                role="status"
                aria-label="Carregando formulário"
              >
                <Skeleton className="h-12 w-full" />
                <Skeleton className="h-40 w-full" />
              </div>
            ) : candidates.error || pollsters.error ? (
              <ErrorState
                message={candidates.error || pollsters.error || ""}
                retry={() => {
                  candidates.retry();
                  pollsters.retry();
                }}
              />
            ) : !candidates.data?.some((c) => c.active) ||
              !pollsters.data?.some((p) => p.active) ? (
              <div className="data-state">
                <h3>Cadastro inicial necessário</h3>
                <p>
                  Cadastre candidatos e institutos ativos pela API para
                  habilitar o formulário.
                </p>
              </div>
            ) : (
              <PollForm
                key={`${editing?.id || "new"}-${revision}`}
                candidates={candidates.data}
                pollsters={pollsters.data}
                editing={editing}
                onSaved={saved}
                onCancel={() => setEditing(null)}
              />
            )}
          </CardContent>
        </Card>
        <aside className="admin-aside">
          <span className="section-kicker">BOAS PRÁTICAS</span>
          <h3>Rastreabilidade em cada dado.</h3>
          <p>
            Use a publicação original como fonte e confira o período de campo
            antes de salvar.
          </p>
          <hr />
          <h4>Votos totais ou válidos?</h4>
          <p>
            Escolha a base usada pelo instituto. Envie os valores originais; a
            plataforma mantém os dois resultados para comparação.
          </p>
          <hr />
          <h4>Sem estimativas</h4>
          <p>
            Se amostra ou margem não foram divulgadas, deixe os campos em
            branco.
          </p>
        </aside>
      </div>
      <section className="section">
        <div className="section-heading">
          <div>
            <span className="section-kicker">HISTÓRICO</span>
            <h2>Pesquisas cadastradas</h2>
          </div>
        </div>
        {deleting && (
          <div className="delete-confirm" role="alert">
            <p>
              Excluir a pesquisa de <strong>{deleting.pollster.name}</strong>,
              publicada em{" "}
              {new Date(deleting.publishedAt).toLocaleString("pt-BR", {
                timeZone: "America/Sao_Paulo",
              })}
              ?
            </p>
            <div>
              <Button
                variant="outline"
                disabled={busy}
                onClick={() => setDeleting(null)}
              >
                Cancelar
              </Button>
              <Button variant="destructive" disabled={busy} onClick={remove}>
                {busy ? "Excluindo…" : "Confirmar exclusão"}
              </Button>
            </div>
            {deleteError && <p>{deleteError}</p>}
          </div>
        )}
        <PollList
          key={revision}
          onEdit={(poll) => {
            setEditing(poll);
            setFeedback(null);
            document
              .getElementById("poll-form")
              ?.scrollIntoView({ behavior: "smooth" });
          }}
          onDelete={(poll) => {
            setDeleting(poll);
            setDeleteError(null);
          }}
        />
      </section>
    </>
  );
}
