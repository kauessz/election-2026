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
const contextHelper=typescriptLoader()("lib/election-context.ts");
const source=ts.transpileModule(readFileSync(new URL("../components/election-home.tsx",import.meta.url),"utf8"),{compilerOptions:{target:ts.ScriptTarget.ES2020,module:ts.ModuleKind.CommonJS,jsx:ts.JsxEmit.ReactJSX}}).outputText;
function render(phase,error=null){
 const loaded={exports:{}};const data=phase?{phase,evaluatedAt:"2026-10-25T22:00:00Z",officialGeneratedAt:null,lastSuccessfulUpdate:null,warnings:[]}:null;
 const element=(name)=>React.createElement("div",null,name);
 runInNewContext(source,{exports:loaded.exports,module:loaded,require:(name)=>{
  if(name==="@/lib/official-api")return {useOfficialPolling:()=>({data,error})};
  if(name==="@/lib/election-context")return contextHelper;
  if(name==="./official-dashboard")return {OfficialHomeResults:({includeProjection})=>React.createElement("div",null,"OFFICIAL_PRIMARY",includeProjection?element("MODEL_SEPARATE"):null)};
  if(name==="./pre-election-freeze")return {PreElectionFreezeSummary:({showUnavailable})=>element(showUnavailable?"FINAL_COMPARISON":"FREEZE")};
  if(name==="next/link")return {default:({href,children})=>React.createElement("a",{href},children)};
  return require(name);
 }});
 return renderToStaticMarkup(React.createElement(loaded.exports.ElectionHome,{freeze:element("FREEZE"),aggregates:element("AGGREGATES"),polls:element("POLLS"),officialSummary:element("OFFICIAL_SUMMARY")}));
}
test("before counting the home keeps its existing block order",()=>{
 for(const phase of [undefined,"UNKNOWN","NOT_STARTED"]){const html=render(phase);assert.ok(html.indexOf("FREEZE")<html.indexOf("AGGREGATES"));assert.ok(html.indexOf("AGGREGATES")<html.indexOf("POLLS"));assert.ok(html.indexOf("POLLS")<html.indexOf("OFFICIAL_SUMMARY"));assert.ok(!html.includes("OFFICIAL_PRIMARY"));}
});
test("counting prioritizes official results, separates the model and collapses pre-election content",()=>{
 const html=render("COUNTING");assert.ok(html.indexOf("OFFICIAL_PRIMARY")<html.indexOf("MODEL_SEPARATE"));assert.ok(html.indexOf("MODEL_SEPARATE")<html.indexOf("AGGREGATES"));assert.match(html,/<details class="section"><summary>Pesquisas/);assert.ok(!html.includes("<details open"));
});
test("finished counting prioritizes the final official result and frozen comparison",()=>{
 const html=render("FINISHED");assert.ok(html.indexOf("OFFICIAL_PRIMARY")<html.indexOf("FINAL_COMPARISON"));assert.ok(html.indexOf("FINAL_COMPARISON")<html.indexOf("AGGREGATES"));assert.ok(!html.includes("MODEL_SEPARATE"));
});
test("a context fetch failure preserves the last counting layout and exposes its warning",()=>{
 const html=render("COUNTING","offline");assert.ok(html.includes("OFFICIAL_PRIMARY"));assert.ok(html.includes("preservamos a última fase recebida"));assert.ok(!html.includes("OFFICIAL_SUMMARY"));
});
