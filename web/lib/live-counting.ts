import type { Projection } from "./election-analytics";

export const LIVE_POLL_INTERVAL = 15000;

/** A successful HTTP response can still mean the source temporarily has no usable data. */
export function liveResponseWarning(previous: unknown, incoming: unknown): string | null {
  if (previous && incoming == null) return "API temporariamente sem conteúdo. Últimos dados válidos preservados.";
  if (!previous || !incoming || typeof previous !== "object" || typeof incoming !== "object") return null;
  const before = previous as Record<string, unknown>;
  const next = incoming as Record<string, unknown>;
  if (before.official === true && next.official === true && before.result) {
    if (!next.result || next.status === "UNAVAILABLE") return "Fonte oficial temporariamente indisponível. Últimos dados válidos preservados.";
    const oldResult = before.result as { generatedAt: string };
    const newResult = next.result as { generatedAt: string };
    if (Date.parse(newResult.generatedAt) < Date.parse(oldResult.generatedAt)) return "Resposta oficial anterior à última geração recebida. Últimos dados válidos preservados.";
  }
  if (before.kind === "PROJECTION" && next.kind === "PROJECTION" && before.publishable === true && next.enabled === true && next.publishable === false) {
    return "A projeção atual não está publicável. Exibindo somente a última projeção recebida, desatualizada, sem estimar novos votos.";
  }
  return null;
}

export function latestLiveProjection(history: Projection[], model: string): Projection | null {
  return history.filter(p => p.model === model && p.publishable && p.publication?.origin === "LIVE" && p.publication.publishedAt && Number.isFinite(Date.parse(p.publication.publishedAt)))
    .reduce<Projection | null>((latest, p) => !latest || Date.parse(p.publication!.publishedAt!) > Date.parse(latest.publication!.publishedAt!) ? p : latest, null);
}

export function updateAgeSeconds(timestamp: string | null | undefined, now: number): number | null {
  if (!timestamp || !Number.isFinite(now)) return null;
  const time = Date.parse(timestamp);
  return Number.isFinite(time) ? Math.max(0, Math.floor((now - time) / 1000)) : null;
}
