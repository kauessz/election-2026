import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { createRequire } from "node:module";
import { runInNewContext } from "node:vm";
import ts from "typescript";
import axios from "axios";

const require = createRequire(import.meta.url);
const source = readFileSync(new URL("../lib/api.ts", import.meta.url), "utf8");
const compiled = ts.transpileModule(source, {
  compilerOptions: { module: ts.ModuleKind.CommonJS, esModuleInterop: true },
}).outputText;
function loadApi(env) {
  const sandboxModule = { exports: {} };
  runInNewContext(compiled, { module: sandboxModule, exports: sandboxModule.exports, require, process: { env } });
  return sandboxModule.exports;
}

test("API origin normalizes trailing slashes for SSR and client requests", () => {
  const { api } = loadApi({ NEXT_PUBLIC_API_URL: "https://api.example.org///" });
  assert.equal(api.getUri({ url: "/api/polls" }), "https://api.example.org/api/polls");
});
test("missing production URL never sends users to localhost", () => {
  const { api } = loadApi({ NODE_ENV: "production" });
  assert.equal(api.getUri({ url: "/api/polls" }), "/api/polls");
});
test("an unavailable API produces a recoverable user message", () => {
  const { apiError } = loadApi({});
  const message = apiError(new axios.AxiosError("Network Error", "ERR_NETWORK"));
  assert.match(message, /Não foi possível acessar os dados/);
  assert.match(message, /tente novamente/);
});
