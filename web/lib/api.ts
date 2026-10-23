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

export const api = axios.create({
  baseURL: process.env.NEXT_PUBLIC_API_URL || "http://localhost:8081",
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
