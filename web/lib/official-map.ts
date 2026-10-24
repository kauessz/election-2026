import type { OfficialResponse } from "./official-api";

const ufs = new Set("AC AL AP AM BA CE DF ES GO MA MT MS MG PA PB PR PE PI RJ RN RS RO RR SC SP SE TO".split(" "));
export const isMappableUf = (uf: string) => ufs.has(uf.toUpperCase());

/** Display-only differences of received official percentages. No extrapolation. */
export function officialMapSummary(row: OfficialResponse | undefined) {
  const result = row?.result;
  const counting = !!result && row?.status !== "NOT_STARTED"
    && result.sections.totalized > 0 && result.votes.validVotes > 0;
  const candidates = counting ? [...result.candidates].sort((a, b) => b.votes - a.votes || a.candidateNumber.localeCompare(b.candidateNumber)) : [];
  const tied = candidates.length > 1 && candidates[0].votes === candidates[1].votes;
  const first = candidates[0]?.percentage;
  const second = candidates[1]?.percentage;
  return {
    counting,
    candidates,
    leader: counting && !tied ? candidates[0] ?? null : null,
    tied,
    spread: first != null && second != null ? Math.abs(first - second) : null,
    progress: result?.sections.percentageTotalized ?? null,
    stale: row?.status === "STALE",
    unavailable: row?.status === "UNAVAILABLE" || !row,
    final: row?.status === "FINAL",
  };
}

export function shade(color: string, intensity: number) {
  const amount = Math.max(0, Math.min(1, intensity));
  const rgb = [1, 3, 5].map((offset) => Math.round(245 + (parseInt(color.slice(offset, offset + 2), 16) - 245) * amount));
  return `rgb(${rgb.join(", ")})`;
}
