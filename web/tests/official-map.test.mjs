import test from "node:test";
import assert from "node:assert/strict";
import { typescriptLoader } from "./load-typescript.mjs";
const load = typescriptLoader();
const { officialMapSummary, isMappableUf } = load("lib/official-map.ts");
const row = {
  status: "FRESH", result: { sections: { totalized: 10, percentageTotalized: 73.4 }, votes: { validVotes: 1000 }, candidates: [
    { candidateNumber: "22", displayName: "Candidato A", votes: 518, percentage: 51.82 },
    { candidateNumber: "13", displayName: "Candidato B", votes: 482, percentage: 48.18 },
  ] },
};
test("leader uses official votes, preserving official percentages", () => {
  const result = officialMapSummary(row);
  assert.equal(result.leader.candidateNumber, "22");
  assert.equal(result.candidates[0].percentage, 51.82);
  assert.ok(Math.abs(result.spread - 3.64) < 1e-10);
  assert.equal(row.result.candidates[0].votes, 518);
});
test("equal votes produce a neutral tie", () => {
  const tied = structuredClone(row);
  tied.result.candidates.forEach((candidate) => { candidate.votes = 500; candidate.percentage = 50; });
  assert.equal(officialMapSummary(tied).leader, null);
  assert.equal(officialMapSummary(tied).tied, true);
  assert.equal(officialMapSummary(tied).spread, 0);
});
test("progress uses official percentageTotalized exactly, including zero", () => {
  assert.equal(officialMapSummary(row).progress, 73.4);
  const empty = structuredClone(row); empty.result.sections.percentageTotalized = 0;
  assert.equal(officialMapSummary(empty).progress, 0);
});
test("missing result, unavailable and not started remain neutral", () => {
  for (const response of [undefined, { status: "UNAVAILABLE", result: null }, { ...row, status: "NOT_STARTED" }]) {
    assert.equal(officialMapSummary(response).counting, false);
    assert.equal(officialMapSummary(response).leader, null);
  }
});
test("no observed valid votes cannot yield a leader", () => {
  const empty = structuredClone(row); empty.result.votes.validVotes = 0;
  assert.equal(officialMapSummary(empty).leader, null);
});
test("STALE preserves observed results and exposes stale status", () => {
  const stale = officialMapSummary({ ...row, status: "STALE" });
  assert.equal(stale.stale, true);
  assert.equal(stale.leader.candidateNumber, "22");
  assert.equal(stale.progress, 73.4);
});
test("FINAL keeps official results", () => assert.equal(officialMapSummary({ ...row, status: "FINAL" }).final, true));
test("valid UFs and DF are mappable, ZZ and unknown codes are not", () => {
  for (const uf of ["SP", "sp", "DF", "AC"]) assert.equal(isMappableUf(uf), true);
  for (const uf of ["ZZ", "BR", "XX", ""]) assert.equal(isMappableUf(uf), false);
});
test("geographic asset contains exactly 27 real state shapes and finite label anchors", () => {
  const { brazilStates, brazilViewBox } = load("lib/brazil-geography.ts");
  assert.equal(brazilStates.length, 27);
  assert.equal(new Set(brazilStates.map((state) => state.uf)).size, 27);
  for (const state of brazilStates) {
    assert.equal(isMappableUf(state.uf), true);
    assert.ok(state.path.startsWith("M") && !state.path.includes("NaN"));
    assert.ok(state.center.every(Number.isFinite));
    assert.ok(state.center.every((value) => value === Number(value.toFixed(2))));
  }
  assert.match(brazilViewBox, /^0 0 \d+\.\d{2} \d+\.\d{2}$/);
});
