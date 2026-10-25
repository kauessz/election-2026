import type { OfficialResult } from "./official-api";
export type ProjectionCandidate = { candidateNumber: string; name: string; projectedVotes: number; projectedPercentage: number | null };
export type StateProjection = {
 uf: string; available: boolean; officialGeneratedAt: string | null; baselineGeneratedAt: string | null; baselineSourceUrl: string | null;
 totalElectorate: number | null; totalizedElectorate: number | null; electorateProgress: number | null; observationWeight: number | null;
 baselineValidRate: number | null; observedValidRate: number | null; projectedValidRate: number | null; projectedValidVotes: number | null;
 candidates: { candidateNumber: string; name: string; baselineShare: number | null; observedShare: number | null; projectedShare: number | null; projectedCandidateVotes: number }[]; warnings: string[];
};
export type Projection = {
 publication?: {snapshotId:string;origin:"LIVE"|"REPLAY"|"LEGACY_UNKNOWN";publishedAt:string|null;inputManifestAvailable:boolean};
 kind: "PROJECTION"; model: string; enabled: boolean; status: "NOT_STARTED" | "INSUFFICIENT_DATA" | "ACTIVE" | "HIGH_COVERAGE" | "FINAL";
 publishable: boolean; calculatedAt: string; officialGeneratedAt: string | null; nationalElectorateProgress: number | null;
 statesWithData: number; statesTotal: number; coverageComplete: boolean; projectionIncomplete: boolean; coverage: "LOW" | "MEDIUM" | "HIGH" | "FINAL";
 missingBaselineStates: string[]; missingOfficialStates: string[]; results: ProjectionCandidate[]; spread: { leader: string | null; points: number } | null;
 states: StateProjection[]; warnings: string[]; configuration: { minimumNationalElectorateProgress: number; minimumStatesWithData: number; minimumRegionsWithData: number; minimumKeyStatesWithData: number };
 geography: { regionsWithData: number; regionsTotal: number; keyStatesWithData: number; keyStatesTotal: number; thresholdsMet: boolean };
 overseas: { available: boolean; incorporated: boolean; officialGeneratedAt: string | null; observedCandidates: { candidateNumber: string; name: string; votes: number }[]; note: string };
};
export type TrendWindow = { minutes: number; requestedFrom: string; requestedTo: string; from: string | null; to: string | null; newValidVotes: number | null;
 candidateShares: { candidateNumber: string; name: string; newVotes: number; share: number; percentage: number }[];
 available: boolean; usedIntervals: number; excludedIntervals: number; reason: string | null };
export type Trend = { kind: "TREND"; officialGeneratedAt: string | null; calculatedAt: string; windows: TrendWindow[]; warnings: string[] };
export type Remaining = { kind: "TREND"; calculatedAt: string; statesAvailable: number; statesTotal: number;
 states: { uf: string; available: boolean; totalElectorate: number | null; totalizedElectorate: number | null; remainingElectorate: number | null;
 representedPercentage: number | null; remainingPercentage: number | null; partialResult: OfficialResult | null; reason: string | null }[]; warnings: string[] };
