import type { OfficialStatus } from "./official-api";
export type ElectionPhase="UNKNOWN"|"NOT_STARTED"|"COUNTING"|"FINISHED";
export type ElectionContext={phase:ElectionPhase;source:"TSE";sourceStatus:OfficialStatus;dataAvailable:boolean;stale:boolean;sourceFresh:boolean;
 evaluatedAt:string;officialGeneratedAt:string|null;lastSuccessfulUpdate:string|null;latestPersistedAt:string|null;
 countingFirstObservedAt:string|null;finishedFirstObservedAt:string|null;warnings:string[]};
export function homePriority(phase:ElectionPhase|undefined){return phase==="COUNTING"?"counting":phase==="FINISHED"?"finished":"pre-election";}
