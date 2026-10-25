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
const helpers=typescriptLoader()("lib/live-counting.ts");
const source=ts.transpileModule(readFileSync(new URL("../components/election-analytics.tsx",import.meta.url),"utf8"),{compilerOptions:{target:ts.ScriptTarget.ES2020,module:ts.ModuleKind.CommonJS,jsx:ts.JsxEmit.ReactJSX}}).outputText;
const projection={kind:"PROJECTION",model:"STATE_SHRINKAGE_V1",enabled:true,publishable:true,status:"ACTIVE",calculatedAt:"2026-10-25T22:10:00Z",officialGeneratedAt:"2026-10-25T22:09:00Z",nationalElectorateProgress:0.3,statesWithData:27,statesTotal:27,coverage:"MEDIUM",results:[{candidateNumber:"13",name:"Candidato A",projectedPercentage:51.23},{candidateNumber:"22",name:"Candidato B",projectedPercentage:48.77}],spread:{points:2.46},states:[],warnings:[],missingBaselineStates:[],configuration:{minimumNationalElectorateProgress:0.05,minimumStatesWithData:10,minimumRegionsWithData:4,minimumKeyStatesWithData:3},geography:{regionsWithData:5,keyStatesWithData:6},overseas:{incorporated:false,note:"Sem exterior."}};
function render(current,history=[],error=null){
 const loaded={exports:{}};
 runInNewContext(source,{exports:loaded.exports,module:loaded,require:(name)=>{
  if(name==="@/lib/use-live-api")return {useLivePolling:path=>({data:path.includes("history")?history:current,error:path.includes("history")?null:error,loading:false})};
  if(name==="@/lib/live-counting")return helpers;
  if(name==="@/lib/official-api")return {hasCounting:()=>false};
  if(name==="@/components/live-update-stamp")return {LiveUpdateStamp:({timestamp,source})=>React.createElement("span",null,`${source}: ${timestamp}`)};
  if(name==="@/components/ui/badge")return {Badge:({children})=>React.createElement("span",null,children)};
  if(name==="next/link")return {default:({href,children})=>React.createElement("a",{href},children)};
  return require(name);
 }});
 return renderToStaticMarkup(React.createElement(loaded.exports.ProjectionCard,{official:null}));
}
test("unpublishable projection explains its gate without invented percentages",()=>{
 const html=render({...projection,publishable:false,status:"INSUFFICIENT_DATA",results:[{candidateNumber:"13",name:"Candidato A",projectedPercentage:0}]});
 assert.match(html,/Projeção disponível após maior cobertura/);
 assert.ok(!html.includes("51,23%"));
 assert.ok(!html.includes("PROJEÇÃO FINAL / MODELO"));
 assert.ok(!html.includes("<strong>0%</strong>"));
});
test("first LIVE uses its exact percentages, publication time and origin",()=>{
 const live={...projection,results:[{candidateNumber:"13",name:"Candidato A",projectedPercentage:50.95}],publication:{snapshotId:"live-1",origin:"LIVE",publishedAt:"2026-10-25T22:08:00Z",inputManifestAvailable:true}};
 const html=render(projection,[live]);
 assert.match(html,/Publicação LIVE: 2026-10-25T22:08:00Z/);
 assert.match(html,/Origem: LIVE · Snapshot: live-1/);
 assert.match(html,/50,95%/);
 assert.ok(!html.includes("51,23%"));
});
test("retained projection is still readable but explicitly stale during outage",()=>{
 const html=render(projection,[],"API indisponível");
 assert.match(html,/51,23%/);
 assert.match(html,/ÚLTIMA PROJEÇÃO RECEBIDA \/ DESATUALIZADA/);
 assert.match(html,/não é uma estimativa atualizada/);
 assert.match(html,/Cobertura não é probabilidade de vitória/);
});
