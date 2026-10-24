import test from "node:test";
import assert from "node:assert/strict";
import { typescriptLoader } from "./load-typescript.mjs";
const { candidateIdentity, combineTimelinePolls, emptyHistoryFilters, filterTimelinePolls, historyCandidates,
  historyPanels, timelineDomain, roundMilestones, roundsNotice } = typescriptLoader()("lib/poll-history.ts");
const pollster={id:"institute-a",name:"Instituto sintético A",slug:"synthetic-a",active:true,websiteUrl:null};
const result=(key,ballotNumber,percentage)=>({candidateKey:key,candidateName:`Candidato ${key}`,displayName:`Candidato ${key}`,party:"TEST",ballotNumber,originalPercentage:percentage,normalizedPercentage:null});
const historical=(id,round,publishedAt,other={})=>({id,electionKey:"BR-PRESIDENT-2026",round,pollster,registrationNumber:id,fieldStart:"2026-09-28",fieldEnd:"2026-09-30",publishedAt,sampleSize:1000,marginOfError:2,methodology:"SYNTHETIC TEST ONLY",sourceUrl:"https://example.org/SYNTHETIC-TEST",voteBasis:"TOTAL",normalization:"NONE",completeCandidateSet:false,expectedCandidateKeys:[],notes:null,createdAt:publishedAt,updatedAt:publishedAt,results:[result("a",22,40),result("b",13,35),result("c",55,5)],...other});
const activeCandidates=[{id:"active-a",name:"Candidato a",displayName:"Candidato a",party:"TEST",ballotNumber:22,active:true},{id:"active-b",name:"Candidato b",displayName:"Candidato b",party:"TEST",ballotNumber:13,active:true}];
const current={id:"current",pollster,registrationNumber:"SECOND",fieldStart:"2026-10-18",fieldEnd:"2026-10-20",publishedAt:"2026-10-21T12:00:00Z",sampleSize:1000,marginOfError:2,methodology:"SYNTHETIC TEST ONLY",sourceUrl:"https://example.org/SYNTHETIC-TEST",voteBasis:"VALID",results:[{candidateId:"active-a",candidateName:"Candidato a",originalPercentage:51,normalizedPercentage:51},{candidateId:"active-b",candidateName:"Candidato b",originalPercentage:49,normalizedPercentage:49}]};
const archive=[historical("FIRST",1,"2026-10-01T12:00:00Z"),historical("SECOND-ARCHIVE",2,"2026-10-20T12:00:00Z",{voteBasis:"VALID",pollster:{...pollster,id:"institute-b"}})];
const combined=combineTimelinePolls(archive,[current],activeCandidates);
const ids=(filters)=>Array.from(filterTimelinePolls(combined,{...emptyHistoryFilters,...filters}),p=>p.id);

test("history and current polls are joined only in a read-only presentation adapter",()=>{
  const originalArchive=structuredClone(archive), originalCurrent=structuredClone(current);
  const rows=combineTimelinePolls(archive,[current],activeCandidates);
  assert.equal(rows.length,3); assert.equal(rows.find(p=>p.id==="current").round,2);
  assert.deepEqual(archive,originalArchive); assert.deepEqual(current,originalCurrent);
  assert.equal(rows.find(p=>p.round===1).results.find(r=>r.candidateKey==="c").originalPercentage,5);
  assert.equal(rows.find(p=>p.round===1).results.find(r=>r.candidateKey==="c").normalizedPercentage,null);
});
test("absence of historical records never fabricates first-round observations",()=>{
  const rows=combineTimelinePolls([],[current],activeCandidates);
  const panels=historyPanels(rows,"all");
  assert.equal(panels[0].polls.length,0); assert.equal(panels[1].polls.length,1);
});
test("current and archive observations are deduplicated only within the same round and natural key",()=>{
  const duplicate=historical("copy",2,current.publishedAt,{registrationNumber:"SECOND"});
  const differentRound=historical("first",1,current.publishedAt,{registrationNumber:"SECOND"});
  const rows=combineTimelinePolls([duplicate,differentRound],[current],activeCandidates);
  assert.equal(rows.length,2); assert.equal(rows.find(p=>p.round===2).origin,"CURRENT");
  assert.equal(rows.find(p=>p.round===1).origin,"ARCHIVE");
});
test("fallback identity treats equivalent publication instants consistently",()=>{
  const duplicate=historical("copy",2,"2026-10-21T09:00:00-03:00",{registrationNumber:null,fieldEnd:current.fieldEnd});
  assert.equal(combineTimelinePolls([duplicate],[{...current,registrationNumber:null}],activeCandidates).length,1);
  const first=historical("micro-one",2,"2026-10-21T12:00:00.123001Z",{registrationNumber:null,fieldEnd:current.fieldEnd});
  const second=historical("micro-two",2,"2026-10-21T12:00:00.123002Z",{registrationNumber:null,fieldEnd:current.fieldEnd});
  assert.equal(combineTimelinePolls([first,second],[],[]).length,2);
});
test("different election keys are excluded from the 2026 presidential view",()=>{
  assert.equal(combineTimelinePolls([historical("other",1,"2026-10-01T00:00:00Z",{electionKey:"OTHER-ELECTION"})],[],[]).length,0);
});
test("candidate keys remain independent of current eligibility or ballot number",()=>{
  assert.notEqual(candidateIdentity(result("one",55,1)),candidateIdentity(result("two",55,1)));
  assert.ok(historyCandidates(combined).some(([key])=>key==="key:c"));
  assert.equal(activeCandidates.some(c=>c.ballotNumber===55),false);
});
test("current/archive candidate reconciliation requires compatible number, name and unique key",()=>{
  const currentA=combined.find(p=>p.origin==="CURRENT").results[0]; assert.equal(currentA.candidateKey,"a");
  const ambiguous=historical("other",1,"2026-10-02T12:00:00Z",{results:[{...result("other-key",22,40),candidateName:"Candidato a",displayName:"Candidato a"}]});
  assert.equal(combineTimelinePolls([...archive,ambiguous],[current],activeCandidates).find(p=>p.origin==="CURRENT").results[0].candidateKey,"current-active-a");
  const mismatch=historical("other",1,"2026-10-02T12:00:00Z",{results:[result("different-person",22,40)]});
  assert.equal(combineTimelinePolls([mismatch],[current],activeCandidates).find(p=>p.origin==="CURRENT").results[0].candidateKey,"current-active-a");
});
test("missing current metadata retains supplied names without inventing party or ballot number",()=>{
  const row=combineTimelinePolls([],[current],[])[0];
  assert.equal(row.results[0].displayName,"Candidato a"); assert.equal(row.results[0].party,null); assert.equal(row.results[0].ballotNumber,null);
});
test("round and institute filters",()=>{
  assert.deepEqual(ids({round:"1"}),["FIRST"]);
  assert.deepEqual(ids({round:"2"}),["SECOND-ARCHIVE","current"]);
  assert.deepEqual(ids({pollsterId:"institute-b"}),["SECOND-ARCHIVE"]);
});
test("TOTAL/VALID filter never normalizes or changes observed percentages",()=>{
  assert.deepEqual(ids({voteBasis:"TOTAL"}),["FIRST"]);
  assert.deepEqual(ids({voteBasis:"VALID"}),["SECOND-ARCHIVE","current"]);
  assert.equal(filterTimelinePolls(combined,{...emptyHistoryFilters,voteBasis:"TOTAL"})[0].results[0].originalPercentage,40);
});
test("publication period uses the Brazilian date, including the full last day",()=>{
  const edge=combineTimelinePolls([historical("edge",1,"2026-10-02T02:59:59Z")],[],[]);
  assert.equal(filterTimelinePolls(edge,{...emptyHistoryFilters,from:"2026-10-01",to:"2026-10-01"}).length,1);
  assert.equal(filterTimelinePolls(edge,{...emptyHistoryFilters,from:"2026-10-02"}).length,0);
  assert.deepEqual(ids({from:"2026-10-20",to:"2026-10-21"}),["SECOND-ARCHIVE","current"]);
  assert.deepEqual(ids({from:"2026-10-22",to:"2026-10-01"}),[]);
});
test("candidate selection includes historical-only candidates and can hide every candidate",()=>{
  assert.deepEqual(ids({hiddenCandidates:["key:a","key:b"]}),["FIRST","SECOND-ARCHIVE"]);
  assert.deepEqual(ids({hiddenCandidates:["key:a","key:b","key:c"]}),[]);
});
test("combined filters and clearing restore the observational collection",()=>{
  assert.deepEqual(ids({round:"2",pollsterId:"institute-b",voteBasis:"VALID",from:"2026-10-20",to:"2026-10-20",hiddenCandidates:["key:a"]}),["SECOND-ARCHIVE"]);
  assert.equal(ids(emptyHistoryFilters).length,3);
});
test("overview has distinct round panels even for the same candidate and shared temporal domain",()=>{
  const panels=historyPanels(combined,"all");
  assert.equal(panels.length,2); assert.ok(panels[0].polls.every(p=>p.round===1)); assert.ok(panels[1].polls.every(p=>p.round===2));
  assert.equal(historyPanels(combined,"1").length,1); assert.equal(historyPanels(combined,"2")[0].round,2);
  const [start,end]=timelineDomain(combined);
  assert.ok(roundMilestones.every(m=>Date.parse(m.date)>=start && Date.parse(m.date)<=end));
  assert.match(roundsNotice,/não são diretamente equivalentes/);
});
