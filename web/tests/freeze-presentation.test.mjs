import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import {createRequire} from "node:module";
import {runInNewContext} from "node:vm";
import ts from "typescript";
import React from "react";
import {renderToStaticMarkup} from "react-dom/server";
import {typescriptLoader} from "./load-typescript.mjs";
const require=createRequire(import.meta.url);
const helpers=typescriptLoader()("lib/pre-election.ts");
const source=ts.transpileModule(readFileSync(new URL("../components/pre-election-freeze.tsx",import.meta.url),"utf8"),{compilerOptions:{target:ts.ScriptTarget.ES2020,module:ts.ModuleKind.CommonJS,jsx:ts.JsxEmit.ReactJSX}}).outputText;
const freeze={id:"freeze-internal-uuid",label:"Auditoria do segundo turno",frozenAt:"2026-10-25T12:00:00Z",gitCommit:"abcdef0123456789abcdef0123456789abcdef0123",pollIds:["source-internal-uuid"],pollsterPerformanceIds:["performance-internal-uuid"],aggregateResults:["SIMPLE","RECENCY","WEIGHTED","CALIBRATED"].map(method=>({method,pollCount:18,totalSampleSize:67942,calculatedAt:"2026-10-25T11:59:00Z",referenceDate:"2026-10-25",results:[{candidateId:"candidate-internal-uuid",candidateName:"Candidato A",percentage:50.95}]})),composition:{polls:[{id:"source-internal-uuid",pollster:{id:"institute-internal-uuid",name:"Instituto de exemplo"},fieldStart:"2026-10-20",fieldEnd:"2026-10-22",publishedAt:"2026-10-23T13:00:00Z",sourceUrl:"https://example.org/pesquisa"}],performances:[]},notes:"Registro de auditoria preservado",active:true};
function render(data=freeze,showUnavailable=false){
 const loaded={exports:{}};
 runInNewContext(source,{exports:loaded.exports,module:loaded,require:(name)=>{
  if(name==="@/lib/use-api")return {useApi:()=>({data,loading:false,error:null})};
  if(name==="@/lib/official-api")return {useOfficialPolling:()=>({data:{status:"NOT_STARTED",models:[],warnings:[]}})};
  if(name==="@/lib/pre-election")return helpers;
  if(name==="next/link")return {default:({href,children})=>React.createElement("a",{href},children)};
  return require(name);
 }});
 return renderToStaticMarkup(React.createElement(loaded.exports.PreElectionFreezeSummary,{showUnavailable}));
}
test("public frozen composition shows all models, sample counts, freeze date, institutes and field dates",()=>{
 const html=render();const publicPart=html.split('<details class="freeze-technical">')[0];
 for(const value of ["Simples","Recência","Ponderado","Calibrado","18 pesquisas","67.942 entrevistados","COMPOSIÇÃO CONGELADA / 2º TURNO","25/10/2026","Instituto de exemplo","2026-10-20","2026-10-22"])assert.ok(publicPart.includes(value),value);
 assert.match(publicPart,/<a href="https:\/\/example.org\/pesquisa"/);
 for(const value of [freeze.id,freeze.gitCommit,freeze.pollIds[0],freeze.pollsterPerformanceIds[0],"candidate-internal-uuid","institute-internal-uuid"])assert.ok(!publicPart.includes(value),value);
 assert.ok(!publicPart.includes("<details"),"the public source list is visible without expanding a disclosure");
});
test("closed technical disclosure preserves IDs, complete SHA and original technical timestamps",()=>{
 const html=render();const technical=html.slice(html.indexOf('<details class="freeze-technical">'));
 assert.match(technical,/^<details class="freeze-technical"><summary>Detalhes técnicos<\/summary>/);
 for(const value of [freeze.id,freeze.gitCommit,...freeze.pollIds,...freeze.pollsterPerformanceIds,"candidate-internal-uuid","institute-internal-uuid",freeze.frozenAt,freeze.aggregateResults[0].calculatedAt,freeze.composition.polls[0].publishedAt,freeze.notes])assert.ok(technical.includes(value),value);
 assert.ok(!technical.includes("<details open"));
 assert.deepEqual(freeze.pollIds,["source-internal-uuid"]);
});
test("missing freeze still provides the finished-election explanation without creating data",()=>{
 assert.equal(render(null),"");
 assert.match(render(null,true),/Nenhum freeze retroativo é criado/);
});
