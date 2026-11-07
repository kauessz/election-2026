import assert from "node:assert/strict";
import { readFileSync, readdirSync } from "node:fs";
import { createRequire } from "node:module";
import test from "node:test";
import { runInNewContext } from "node:vm";

// js-yaml is already in the locked ESLint toolchain. No new runtime dependency.
const { load } = createRequire(import.meta.url)("js-yaml");
const directory = new URL("../../.github/workflows/", import.meta.url);
const source = readFileSync(new URL("api-image.yml", directory), "utf8");
const workflow = load(source, { json: false });
const sha = "a".repeat(40);

test("all workflow jobs are read-only and expose no publication path", () => {
  assert.deepEqual(readdirSync(directory).filter(name => /\.ya?ml$/.test(name)), ["api-image.yml"]);
  assert.deepEqual(Object.keys(workflow.jobs), ["validate"]);
  assert.deepEqual(workflow.permissions, { contents: "read" });
  assert.deepEqual(workflow.jobs.validate.permissions, { contents: "read" });
  assert(!/secrets\.|packages:|id-token:|docker\s+(?:login|push)|gh\s+release|--push/.test(source));
  assert.deepEqual(Object.keys(workflow.on), ["push", "workflow_dispatch"]);
  assert.deepEqual(workflow.on.push.branches, ["master"]);
  assert.deepEqual(workflow.on.push.paths, ["api/**", "web/**", ".github/workflows/api-image.yml"]);
});

test("validation preserves checks, exact checkout and credential isolation", () => {
  const { validate } = workflow.jobs;
  assert.deepEqual(validate.permissions, { contents: "read" });
  const checkout = validate.steps.find(step => step.uses?.startsWith("actions/checkout@"));
  assert.deepEqual(checkout.with, { ref: "${{ github.sha }}", "persist-credentials": false });
  const commands = validate.steps.map(step => step.run ?? "").join("\n");
  for (const command of ["mvnw -B -ntp verify", "npm ci", "npm test", "npm run lint", "tsc --noEmit", "npm run build"]) {
    assert(commands.includes(command), `Missing validation: ${command}`);
  }
  assert(!commands.includes("${{"), "Context/input expressions must not be interpolated into shell scripts");
  assert(validate.steps.every(step => !step.with?.cache));
  assert.equal(workflow.on.workflow_dispatch.inputs.expected_sha.required, true);
  assert.equal(workflow.on.workflow_dispatch.inputs.expected_sha.type, "string");
});

// Static simulation of the current boolean subset, not the entire GitHub evaluator.
const expression = workflow.jobs.validate.if;
assert.match(expression, /^[\w\s.'=&|()/\-]+$/);
const defaults = {
  repository: "kauessz/election-2026", ref: "refs/heads/master", ref_type: "branch",
  event_name: "workflow_dispatch", ref_protected: true, sha,
};
const cases = [
  ["protected master manual with exact commit", {}, { expected_sha: sha }, true],
  ["master push validates only", { event_name: "push", ref_protected: false }, {}, true],
  ["manual on unprotected master", { ref_protected: false }, { expected_sha: sha }, false],
  ["manual on feature branch", { ref: "refs/heads/feature" }, { expected_sha: sha }, false],
  ["manual on tag", { ref: "refs/tags/master", ref_type: "tag" }, { expected_sha: sha }, false],
  ["push on feature branch", { event_name: "push", ref: "refs/heads/feature" }, {}, false],
  ["push on tag", { event_name: "push", ref: "refs/tags/master", ref_type: "tag" }, {}, false],
  ["fork repository", { repository: "someone/election-2026" }, { expected_sha: sha }, false],
  ["pull request", { event_name: "pull_request", ref: "refs/pull/1/merge" }, {}, false],
  ["pull request target", { event_name: "pull_request_target" }, {}, false],
  ["workflow run", { event_name: "workflow_run" }, {}, false],
  ["manual without expected commit", {}, {}, false],
  ["manual with stale commit", {}, { expected_sha: "b".repeat(40) }, false],
  ["manual with abbreviated commit", {}, { expected_sha: "aaaaaaa" }, false],
  ["manual with shell input", {}, { expected_sha: "$(echo untrusted)" }, false],
];
for (const [name, github, inputs, allowed] of cases) {
  test(`validation event/ref guard: ${name}`, () => {
    assert.equal(runInNewContext(expression, { github: { ...defaults, ...github }, inputs }, { timeout: 5000 }), allowed);
    assert.equal(workflow.jobs.publish, undefined);
  });
}
