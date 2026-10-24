import test from "node:test";
import assert from "node:assert/strict";
import { typescriptLoader } from "./load-typescript.mjs";

const load = typescriptLoader();
const { publicNumber, publicPercent, modelNumber } = load("lib/presentation.ts");
test("public cards use two decimal places without changing source values", () => {
  assert.equal(publicNumber(50.9551), "50,96");
  assert.equal(publicNumber(49.0449), "49,04");
  assert.equal(publicNumber(1.9102), "1,91");
  assert.equal(publicPercent(50), "50,00%");
});
test("technical presentation retains up to four decimals", () => {
  assert.equal(modelNumber(50.9551), "50,9551");
  assert.equal(modelNumber(49.0449), "49,0449");
});

const { filterPolls, emptyPollFilters } = load("lib/poll-filters.ts");
const now = Date.parse("2026-10-23T15:00:00Z");
const polls = [
  { id: "a-old", pollster: { id: "a", name: "Instituto França", slug: "instituto-franca" }, registrationNumber: "BR-12345/2026", voteBasis: "TOTAL", publishedAt: "2026-10-20T15:00:00Z" },
  { id: "b", pollster: { id: "b", name: "Vox Brasil", slug: "vox-brasil" }, registrationNumber: "BR-67890/2026", voteBasis: "VALID", publishedAt: "2026-10-22T18:00:00Z" },
  { id: "a-new", pollster: { id: "a", name: "Instituto França", slug: "instituto-franca" }, registrationNumber: null, voteBasis: "VALID", publishedAt: "2026-10-23T12:00:00Z" },
  { id: "old", pollster: { id: "c", name: "Outra", slug: "outra" }, registrationNumber: null, voteBasis: "VALID", publishedAt: "2026-10-15T12:00:00Z" },
];
const ids = (filters) => Array.from(filterPolls(polls, { ...emptyPollFilters, ...filters }, now), (poll) => poll.id);
for (const [label, search, expected] of [
  ["accent-insensitive name", "FRANCA", ["a-new", "a-old"]],
  ["slug", "VOX-BRASIL", ["b"]],
  ["registration", "br-12345", ["a-old"]],
]) test(`poll search by ${label}`, () => assert.deepEqual(ids({ search }), expected));
test("institute selector uses pollster id", () => assert.deepEqual(ids({ pollsterId: "a" }), ["a-new", "a-old"]));
test("vote basis TOTAL and VALID", () => {
  assert.deepEqual(ids({ voteBasis: "TOTAL" }), ["a-old"]);
  assert.deepEqual(ids({ voteBasis: "VALID" }), ["a-new", "b", "old"]);
});
test("publication windows include the lower boundary and exclude older/future polls", () => {
  assert.deepEqual(ids({ days: 1 }), ["a-new", "b"]);
  assert.deepEqual(ids({ days: 3 }), ["a-new", "b", "a-old"]);
  assert.deepEqual(ids({ days: 7 }), ["a-new", "b", "a-old"]);
  const future = { ...polls[0], publishedAt: "2026-10-24T00:00:00Z" };
  assert.equal(filterPolls([future], { ...emptyPollFilters, days: 1 }, now).length, 0);
});
test("latest per institute is selected after the other filters", () => {
  assert.deepEqual(ids({ latestOnly: true }), ["a-new", "b", "old"]);
  assert.deepEqual(ids({ latestOnly: true, voteBasis: "TOTAL" }), ["a-old"]);
});
test("filters combine and can produce an empty list", () => {
  assert.deepEqual(ids({ search: "franca", pollsterId: "a", voteBasis: "VALID", days: 1, latestOnly: true }), ["a-new"]);
  assert.deepEqual(ids({ search: "franca", pollsterId: "b" }), []);
});
test("clearing filters restores the full list and never mutates source order", () => {
  assert.equal(ids(emptyPollFilters).length, 4);
  assert.deepEqual(polls.map((poll) => poll.id), ["a-old", "b", "a-new", "old"]);
});
