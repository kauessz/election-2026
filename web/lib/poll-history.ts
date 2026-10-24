import type { Candidate, Poll, Pollster, VoteBasis } from "./api";
import { normalizeSearch } from "./poll-filters";

export const HISTORY_ELECTION = "BR-PRESIDENT-2026";
export const roundMilestones = [
  { round: 1, date: "2026-10-04T15:00:00Z", label: "04/10/2026 — 1º turno" },
  { round: 2, date: "2026-10-25T15:00:00Z", label: "25/10/2026 — 2º turno" },
] as const;
export const roundsNotice = "Os percentuais de 1º e 2º turno não são diretamente equivalentes, pois o conjunto de candidatos e a estrutura da disputa são diferentes.";
export type HistoricalCandidateResult = {
  candidateKey: string; candidateName: string; displayName: string | null; party: string | null;
  ballotNumber: number | null; originalPercentage: number; normalizedPercentage: number | null;
  currentCandidateId?: string;
};
export type TimelinePoll = {
  id: string; electionKey: string; round: 1 | 2; pollster: Pollster; registrationNumber: string | null;
  fieldStart: string; fieldEnd: string; publishedAt: string; sampleSize: number | null; marginOfError: number | null;
  methodology: string | null; sourceUrl: string; voteBasis: VoteBasis; results: HistoricalCandidateResult[];
  origin: "ARCHIVE" | "CURRENT";
};
export type ArchivedPoll = Omit<TimelinePoll, "origin"> & {
  normalization: "NONE" | "VALID_VOTES"; completeCandidateSet: boolean; expectedCandidateKeys: string[];
  notes: string | null; createdAt: string; updatedAt: string;
};
export type HistoryFilters = {
  round: "all" | "1" | "2"; pollsterId: string; from: string; to: string;
  voteBasis: VoteBasis | ""; hiddenCandidates: string[];
};
export const emptyHistoryFilters: HistoryFilters = { round: "all", pollsterId: "", from: "", to: "", voteBasis: "", hiddenCandidates: [] };

export const candidateIdentity = (result: HistoricalCandidateResult) => `key:${result.candidateKey}`;
export const candidateLabel = (result: HistoricalCandidateResult) => result.displayName || result.candidateName;

export function timelineIdentity(poll: TimelinePoll) {
  const registration = poll.registrationNumber?.trim();
  // Keep PostgreSQL microseconds in the identity even though chart positions use JS milliseconds.
  const fraction = poll.publishedAt.match(/\.(\d+)(?:Z|[+-]\d{2}:\d{2})$/i)?.[1] ?? "";
  const instant = new Date(poll.publishedAt).toISOString().replace(/Z$/, `${fraction.padEnd(6,"0").slice(3,6)}Z`);
  return `${poll.electionKey}|${poll.round}|${poll.pollster.id}|${registration ? `REG:${registration}` : `DATE:${poll.fieldEnd}|${instant}`}`;
}

/** Read-only presentation adapter. Nothing is copied into either database pipeline. */
export function combineTimelinePolls(archive: ArchivedPoll[], current: Poll[], candidates: Candidate[]): TimelinePoll[] {
  const candidatesById = new Map(candidates.map((candidate) => [candidate.id, candidate]));
  const archivedCandidates = archive.filter((poll) => poll.electionKey === HISTORY_ELECTION).flatMap((poll) => poll.results);
  const combined = new Map<string, TimelinePoll>();
  for (const poll of archive) if (poll.electionKey === HISTORY_ELECTION) {
    const observation: TimelinePoll = { ...poll, origin: "ARCHIVE" };
    combined.set(timelineIdentity(observation), observation);
  }
  for (const poll of current) {
    const observation: TimelinePoll = {
      ...poll, electionKey: HISTORY_ELECTION, round: 2, origin: "CURRENT",
      results: poll.results.map((result) => {
        const candidate = candidatesById.get(result.candidateId);
        const names = [candidate?.name, candidate?.displayName, result.candidateName].filter((name): name is string => !!name).map(normalizeSearch);
        const aliases = new Set(archivedCandidates.filter((historic) => candidate?.ballotNumber != null
          && historic.ballotNumber === candidate.ballotNumber
          && [historic.candidateName, historic.displayName].some((name) => name && names.includes(normalizeSearch(name))))
          .map((historic) => historic.candidateKey));
        return {
          candidateKey: aliases.size === 1 ? [...aliases][0] : `current-${result.candidateId}`, candidateName: candidate?.name ?? result.candidateName,
          displayName: result.candidateName, party: candidate?.party ?? null, ballotNumber: candidate?.ballotNumber ?? null,
          originalPercentage: result.originalPercentage, normalizedPercentage: result.normalizedPercentage,
          currentCandidateId: result.candidateId,
        };
      }),
    };
    // A round-2 archive copy of a current survey must not produce duplicate plotted observations.
    combined.set(timelineIdentity(observation), observation);
  }
  return [...combined.values()].sort((a, b) => Date.parse(a.publishedAt) - Date.parse(b.publishedAt) || a.id.localeCompare(b.id));
}

function publicationDay(value: string) {
  const parts = new Intl.DateTimeFormat("en", { year: "numeric", month: "2-digit", day: "2-digit", timeZone: "America/Sao_Paulo" }).formatToParts(new Date(value));
  return ["year", "month", "day"].map((part) => parts.find((p) => p.type === part)?.value).join("-");
}
export function filterTimelinePolls(polls: TimelinePoll[], filters: HistoryFilters) {
  if (filters.from && filters.to && filters.from > filters.to) return [];
  return polls.filter((poll) => {
    const day = publicationDay(poll.publishedAt);
    return (filters.round === "all" || poll.round === Number(filters.round))
      && (!filters.pollsterId || poll.pollster.id === filters.pollsterId)
      && (!filters.voteBasis || poll.voteBasis === filters.voteBasis)
      && (!filters.from || day >= filters.from) && (!filters.to || day <= filters.to)
      && poll.results.some((result) => !filters.hiddenCandidates.includes(candidateIdentity(result)));
  });
}
export function historyCandidates(polls: TimelinePoll[]) {
  const candidates = new Map<string, HistoricalCandidateResult>();
  for (const poll of polls) for (const result of poll.results) candidates.set(candidateIdentity(result), result);
  return [...candidates.entries()].sort(([, a], [, b]) => normalizeSearch(candidateLabel(a)).localeCompare(normalizeSearch(candidateLabel(b)), "pt-BR"));
}
export function historyPanels(polls: TimelinePoll[], round: HistoryFilters["round"]) {
  return ([1, 2] as const).filter((value) => round === "all" || value === Number(round))
    .map((value) => ({ round: value, polls: polls.filter((poll) => poll.round === value) }));
}
export function timelineDomain(polls: TimelinePoll[]): [number, number] {
  const dates = [...roundMilestones.map((item) => Date.parse(item.date)), ...polls.map((poll) => Date.parse(poll.publishedAt))];
  return [Math.min(...dates) - 3 * 86400000, Math.max(...dates) + 3 * 86400000];
}
