export const discoveryStatusLabels = {
  DETECTED:"Detectada",AWAITING_RESULTS:"Aguardando resultado",READY_FOR_REVIEW:"Pronta para revisão",
  UNMAPPED_POLLSTER:"Instituto não mapeado",INVALID:"Inválida / requer revisão",IMPORTED:"Importada",IGNORED:"Ignorada",
};
export type DiscoveryStatus=keyof typeof discoveryStatusLabels;
export type Discovery={
  id:string;registrationNumber:string;pollsterName:string;pollsterSlug:string|null;
  fieldStart:string;fieldEnd:string;expectedPublicationDate:string;publishedAt:string|null;
  sampleSize:number|null;marginOfError:number|null;methodology:string|null;sourceUrl:string|null;voteBasis:"TOTAL"|"VALID"|null;
  candidateResults:{ballotNumber:number;percentage:number}[]|null;status:DiscoveryStatus;
  detectedAt:string;updatedAt:string;reviewedAt:string|null;importedPollId:string|null;notes:string|null;
  tseMetadata:Record<string,string>;latestTseMetadata:Record<string,string>;dryRunHash:string|null;
  dryRunReport:import("./pre-election").ImportReport|null;secondRoundConfirmed:boolean;publicationAllowedConfirmed:boolean;
  importReport?:import("./pre-election").ImportReport|null;snapshotIds?:string[];
};
export type DiscoveryInbox={items:Discovery[];counts:Record<DiscoveryStatus,number>;detectedToday:number;expectedPublicationToday:number};
export function canApproveDiscovery(d:Discovery,dirty:boolean,approved:boolean){
  return !dirty&&approved&&d.status==="READY_FOR_REVIEW"&&!!d.dryRunHash&&d.dryRunReport?.failed===0;
}
export const terminalDiscovery=(d:Discovery)=>d.status==="IMPORTED"||d.status==="IGNORED";
