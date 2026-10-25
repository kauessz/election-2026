import test from "node:test";
import assert from "node:assert/strict";
import { typescriptLoader } from "./load-typescript.mjs";
const { LIVE_POLL_INTERVAL, liveResponseWarning, latestLiveProjection, updateAgeSeconds } = typescriptLoader()("lib/live-counting.ts");

test("live polling uses fifteen seconds independently of backend and ElectionContext", () => {
  assert.equal(LIVE_POLL_INTERVAL, 15000);
});
test("age uses the data timestamp, handles unknown dates and never becomes negative", () => {
  const at="2026-10-25T21:00:00Z";
  assert.equal(updateAgeSeconds(at, Date.parse(at)+15999),15);
  assert.equal(updateAgeSeconds(at, Date.parse(at)-1000),0);
  for(const value of [null, undefined, "invalid"]) assert.equal(updateAgeSeconds(value, Date.now()),null);
  assert.equal(updateAgeSeconds(at, NaN),null);
});
test("an unavailable or empty official response retains the previous snapshot", () => {
  const previous={official:true,status:"FRESH",result:{generatedAt:"2026-10-25T21:00:00Z",votes:{totalVotes:12345}}};
  assert.ok(liveResponseWarning(previous,{official:true,status:"UNAVAILABLE",result:null}));
  assert.ok(liveResponseWarning(previous,null));
  assert.equal(previous.result.votes.totalVotes,12345);
  assert.equal(liveResponseWarning(null,{official:true,status:"UNAVAILABLE",result:null}),null);
});
test("older official generations cannot replace newer data but legitimate vote corrections are accepted", () => {
  const old={official:true,result:{generatedAt:"2026-10-25T21:00:00Z",votes:{totalVotes:100}}};
  assert.ok(liveResponseWarning(old,{official:true,result:{generatedAt:"2026-10-25T20:00:00Z"}}));
  assert.equal(liveResponseWarning(old,{official:true,result:{generatedAt:"2026-10-25T21:01:00Z",votes:{totalVotes:99}}}),null);
});
test("temporarily unpublishable projections retain earlier numbers with a warning; explicit disable is honored", () => {
  const previous={kind:"PROJECTION",enabled:true,publishable:true};
  assert.ok(liveResponseWarning(previous,{...previous,publishable:false}));
  assert.equal(liveResponseWarning(previous,{...previous,enabled:false,publishable:false}),null);
  assert.equal(liveResponseWarning(null,{...previous,publishable:false}),null);
});
test("context phase is never rewritten by the live data policy", () => {
  assert.equal(liveResponseWarning({phase:"COUNTING"},{phase:"COUNTING",sourceStatus:"UNAVAILABLE"}),null);
});
test("latest LIVE uses publishedAt, preserves its metadata and excludes other models, replay and legacy", () => {
  const snapshot=(origin,publishedAt,model="V1")=>({model,publishable:true,publication:{origin,publishedAt,snapshotId:publishedAt}});
  const first=snapshot("LIVE","2026-10-25T21:00:00Z");
  const latest=snapshot("LIVE","2026-10-25T21:01:00Z");
  const rows=[latest,first,snapshot("REPLAY","2026-10-25T22:00:00Z"),snapshot("LEGACY_UNKNOWN",null),snapshot("LIVE","2026-10-25T23:00:00Z","V2"),snapshot("LIVE","invalid")];
  assert.equal(latestLiveProjection(rows,"V1"),latest);
  assert.equal(latestLiveProjection(rows,"V1").publication,latest.publication);
  assert.equal(latestLiveProjection(rows,"V3"),null);
  assert.equal(latestLiveProjection([{...latest,publishable:false}],"V1"),null);
});
