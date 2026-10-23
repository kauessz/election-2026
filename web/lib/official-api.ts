"use client";
import { useEffect, useState } from "react";
import { api, apiError } from "./api";
export type OfficialStatus = "NOT_STARTED" | "FRESH" | "STALE" | "UNAVAILABLE" | "FINAL";
export type Scope = { type: "NATIONAL" | "STATE" | "MUNICIPALITY" | "ZONE"; code: string; name: string };
export type OfficialCandidate = { candidateNumber: string; candidateSequence: string; name: string; displayName: string; votes: number; percentage: number | null; precisePercentage: number | null; localCandidateId: string | null };
export type OfficialResult = {
 electionId: string; round: number; scope: Scope; generatedAt: string; generationId: string; finalTotalization: boolean;
 sections: { total: number; totalized: number; percentageTotalized: number | null; notTotalized: number | null };
 electorate: { total: number; totalized: number | null; percentageTotalized: number | null; abstentions: number | null; abstentionPercentage: number | null; turnout: number | null };
 votes: { totalVotes: number; validVotes: number; blankVotes: number | null; nullVotes: number | null; nominalVotes: number | null; competingVotes: number | null };
 candidates: OfficialCandidate[]; warnings: string[];
};
export type OfficialResponse = { source: "TSE"; official: true; status: OfficialStatus; lastSuccessfulUpdate: string | null; refreshing: boolean; scope: Scope; result: OfficialResult | null };
export type Municipality = { code: string; name: string; uf: string; zones: string[] };
export function hasCounting(result: OfficialResult | null | undefined) {
 return !!result && result.sections.totalized > 0 && result.votes.validVotes > 0;
}
// Recursive polling avoids overlapping requests. Failed requests retain the last good response.
export function useOfficialPolling<T>(path: string, interval = 5000) {
 const [state, setState] = useState<{ path: string; data: T | null; loading: boolean; error: string | null }>({ path, data: null, loading: true, error: null });
 useEffect(() => {
  const controller = new AbortController(); let timer: ReturnType<typeof setTimeout>;
  async function fetchData() {
   try {
    const { data } = await api.get<T>(path, { signal: controller.signal });
    if (!controller.signal.aborted) setState({ path, data, loading: false, error: null });
   } catch (error) {
    if (!controller.signal.aborted) setState(previous => ({ path, data: previous.path === path ? previous.data : null, loading: false, error: apiError(error) }));
   } finally { if (!controller.signal.aborted) timer = setTimeout(fetchData, interval); }
  }
  void fetchData();
  return () => { controller.abort(); clearTimeout(timer); };
 }, [path, interval]);
 return state.path === path ? state : { data: null, loading: true, error: null };
}
let catalogueRequest: Promise<Municipality[]> | undefined;
export function municipalityIndex() {
 if (!catalogueRequest) catalogueRequest = api.get<Municipality[]>("/api/election/president/municipalities").then(r => r.data).catch(error => { catalogueRequest = undefined; throw error; });
 return catalogueRequest;
}
