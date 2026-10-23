import axios from "axios";

export type Candidate = {
  id: string;
  name: string;
  displayName: string;
  party: string;
  ballotNumber: number;
  active: boolean;
};
export type Pollster = {
  id: string;
  name: string;
  slug: string;
  websiteUrl: string | null;
  active: boolean;
};
export type VoteBasis = "TOTAL" | "VALID";
export type PollResult = {
  candidateId: string;
  candidateName: string;
  originalPercentage: number;
  normalizedPercentage: number;
};
export type Poll = {
  id: string;
  pollster: Pollster;
  registrationNumber: string | null;
  fieldStart: string;
  fieldEnd: string;
  publishedAt: string;
  sampleSize: number | null;
  marginOfError: number | null;
  methodology: string | null;
  sourceUrl: string;
  voteBasis: VoteBasis;
  results: PollResult[];
};
export type PollRequest = Omit<Poll, "id" | "pollster" | "results"> & {
  pollsterId: string;
  results: { candidateId: string; originalPercentage: number }[];
};

export type AggregateMethod = "SIMPLE" | "RECENCY" | "WEIGHTED" | "CALIBRATED";
export type AggregateResult = {
  candidateId: string;
  candidateName: string;
  percentage: number;
};
export type AggregateSpread = {
  leaderCandidateId: string | null;
  leaderCandidateName: string | null;
  points: number;
};
export type AggregateContribution = {
  pollId: string;
  pollsterId: string;
  pollsterName: string;
  fieldStart: string;
  fieldEnd: string;
  fieldMidpoint: string;
  publishedAt: string;
  sampleSize: number | null;
  marginOfError: number | null;
  rawWeight: number;
  normalizedWeight: number;
  recencyWeight: number | null;
  precisionWeight: number | null;
  precisionSource: string | null;
  historicalWeightFactor: number | null;
  capped: boolean;
  candidateResults: PollResult[];
};
export type Aggregate = {
  method: AggregateMethod;
  calculatedAt: string;
  referenceDate: string;
  pollCount: number;
  pollsterCount: number;
  totalSampleSize: number;
  pollsWithoutSampleSize: number;
  results: AggregateResult[];
  spread: AggregateSpread | null;
  contributions: AggregateContribution[];
  cap: { requested: number; effective: number; feasible: boolean } | null;
  configuration: {
    halfLifeDays: number;
    maxInstituteWeight: number;
    historicalMinFactor: number;
    historicalMaxFactor: number;
    historicalNeutralSpreadError: number;
    historicalElectionKey: string;
    historicalRound: number;
  };
  warnings: string[];
  excludedPolls: { pollId: string; pollsterName: string; reason: string }[];
};
export type AggregateSnapshot = Pick<
  Aggregate,
  | "method"
  | "calculatedAt"
  | "referenceDate"
  | "pollCount"
  | "pollsterCount"
  | "totalSampleSize"
  | "results"
  | "spread"
> & { id: string };
export type PollsterPerformance = {
  id: string;
  pollster: Pollster;
  electionKey: string;
  round: number;
  candidateError: number | null;
  spreadError: number | null;
  historicalScore: number;
  sourceUrl: string | null;
  sourceDescription: string | null;
  updatedAt: string;
};

export const api = axios.create({
  baseURL: (process.env.NEXT_PUBLIC_API_URL?.trim() || "/").replace(/\/+$/, ""),
  timeout: 15000,
});

export function apiError(error: unknown): string {
  if (axios.isAxiosError(error)) {
    const problem = error.response?.data as
      { detail?: string; errors?: string[] } | undefined;
    if (problem?.detail)
      return [problem.detail, ...(problem.errors || [])].join(" ");
    return "Não foi possível acessar os dados. Verifique a conexão com a API e tente novamente.";
  }
  return error instanceof Error
    ? error.message
    : "Não foi possível concluir a operação.";
}
