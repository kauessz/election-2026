import type { Projection } from "./election-analytics";
export function projectionVersions(history:Projection[]){return [...new Set(history.filter(p=>p.publishable&&p.publication?.origin!=="REPLAY").map(p=>p.model))];}
export function selectProjectionHistory(history:Projection[],version:string){return history.filter(p=>p.publishable&&p.model===version&&p.publication?.origin!=="REPLAY");}
