import test from "node:test";
import assert from "node:assert/strict";
import { typescriptLoader } from "./load-typescript.mjs";
const load=typescriptLoader();
const {homePriority}=load("lib/election-context.ts");
const {projectionVersions,selectProjectionHistory}=load("lib/projection-publication.ts");
test("home prioritizes official counting and final comparison, preserving pre-election fallback",()=>{
 assert.equal(homePriority("UNKNOWN"),"pre-election");assert.equal(homePriority(undefined),"pre-election");assert.equal(homePriority("NOT_STARTED"),"pre-election");
 assert.equal(homePriority("COUNTING"),"counting");assert.equal(homePriority("FINISHED"),"finished");
});
test("projection chart isolates model versions and never treats a replay as a live observation",()=>{
 const live={model:"V1",publishable:true,publication:{origin:"LIVE",publishedAt:"2026-10-25T22:00:00Z"}};
 const history=[{...live,model:"V0"},live,{...live,model:"V2",publication:{origin:"REPLAY",publishedAt:null}},{...live,publishable:false}];
 assert.deepEqual(Array.from(projectionVersions(history)),["V0","V1"]);
 assert.deepEqual(Array.from(selectProjectionHistory(history,"V1")),[live]);assert.equal(selectProjectionHistory(history,"V2").length,0);
});
test("legacy snapshots remain readable without manufacturing publication time",()=>{
 const old={model:"V1",publishable:true};const labelled={...old,publication:{origin:"LEGACY_UNKNOWN",publishedAt:null}};
 const result=selectProjectionHistory([old,labelled],"V1");assert.equal(result.length,2);assert.equal(result[0].publication,undefined);assert.equal(result[1].publication.publishedAt,null);
});
