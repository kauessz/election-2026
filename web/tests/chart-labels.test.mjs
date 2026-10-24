import test from "node:test";
import assert from "node:assert/strict";
import { typescriptLoader } from "./load-typescript.mjs";

const load = typescriptLoader();
const { latestSeriesLabels, observationLabelOffset } = load("lib/chart-labels.ts");
const { publicPercent } = load("lib/presentation.ts");

test("labels reuse public precision including zero and 100 percent", () => {
  assert.equal(publicPercent(50.9549), "50,95%");
  assert.equal(publicPercent(0), "0,00%");
  assert.equal(publicPercent(100), "100,00%");
});

test("latest finite observation is chosen independently per series, by time rather than array order", () => {
  const points = [{time:3,a:51,b:null},{time:1,a:50,b:49},{time:2,a:null,b:48},{time:4,a:NaN,b:Infinity}];
  const before = structuredClone(points);
  const labels = latestSeriesLabels(points,["a","b","missing"]);
  assert.equal(labels.a.index,0);
  assert.equal(labels.a.value,51);
  assert.equal(labels.b.index,2);
  assert.equal(labels.b.value,48);
  assert.equal(labels.missing,undefined);
  assert.deepEqual(points,before);
});

test("zero is a valid endpoint, empty and invalid timelines have no labels", () => {
  assert.equal(latestSeriesLabels([{time:1,a:0}],["a"]).a.value,0);
  assert.equal(Object.keys(latestSeriesLabels([], ["a"])).length,0);
  assert.equal(Object.keys(latestSeriesLabels([{time:NaN,a:2},{time:1,a:null}], ["a"])).length,0);
});

test("time key can be timestamp and equal instants choose the last observed entry", () => {
  const result=latestSeriesLabels([{timestamp:2,a:50},{timestamp:2,a:51}], ["a"], "timestamp");
  assert.equal(result.a.index,1);
  assert.equal(result.a.value,51);
});

test("nearby endpoints have deterministic ordered lanes independent of key order", () => {
  const points=[{time:1,a:51,b:49,c:50,d:50}];
  const result=latestSeriesLabels(points,["a","b","c","d"]);
  assert.deepEqual(result,latestSeriesLabels(points,["d","c","b","a"]));
  assert.equal(new Set(Object.values(result).map(label=>label.dy)).size,4);
  assert.ok(result.a.dy<result.c.dy && result.c.dy<result.d.dy && result.d.dy<result.b.dy);
  assert.equal(latestSeriesLabels([{time:1,a:75,b:25}],["a","b"]).a.dy,0);
});

test("boundary clusters move inward and optional observation labels alternate without crossing the axes", () => {
  const high=latestSeriesLabels([{time:1,a:100,b:99}],["a","b"]);
  const low=latestSeriesLabels([{time:1,a:1,b:0}],["a","b"]);
  assert.ok(high.a.dy>=0 && high.b.dy>0);
  assert.ok(low.a.dy<0 && low.b.dy<=0);
  assert.notEqual(observationLabelOffset(50,0),observationLabelOffset(50,1));
  assert.ok(observationLabelOffset(100,0)>0);
  assert.ok(observationLabelOffset(0,1)<0);
});
