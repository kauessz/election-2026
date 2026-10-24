import type { Poll, VoteBasis } from "./api";

export type PollFilters = {
  search: string;
  pollsterId: string;
  voteBasis: VoteBasis | "";
  days: 0 | 1 | 3 | 7;
  latestOnly: boolean;
};
export const emptyPollFilters: PollFilters = {
  search: "", pollsterId: "", voteBasis: "", days: 0, latestOnly: false,
};
export const normalizeSearch = (value: string) =>
  value.normalize("NFD").replace(/[\u0300-\u036f]/g, "").toLowerCase().trim();

/** Filters the displayed collection only; aggregate eligibility stays on the API. */
export function filterPolls(polls: Poll[], filters: PollFilters, now: number): Poll[] {
  const search = normalizeSearch(filters.search);
  const filtered = polls.filter((poll) => {
    const published = Date.parse(poll.publishedAt);
    return (!search || [poll.pollster.name, poll.pollster.slug, poll.registrationNumber ?? ""]
      .some((value) => normalizeSearch(value).includes(search)))
      && (!filters.pollsterId || poll.pollster.id === filters.pollsterId)
      && (!filters.voteBasis || poll.voteBasis === filters.voteBasis)
      && (!filters.days || (published <= now && published >= now - filters.days * 86400000));
  });
  const sorted = [...filtered].sort((a, b) => Date.parse(b.publishedAt) - Date.parse(a.publishedAt) || a.id.localeCompare(b.id));
  if (!filters.latestOnly) return sorted;
  const seen = new Set<string>();
  return sorted.filter((poll) => {
    if (seen.has(poll.pollster.id)) return false;
    seen.add(poll.pollster.id);
    return true;
  });
}
