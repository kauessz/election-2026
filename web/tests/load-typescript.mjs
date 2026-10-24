import { readFileSync } from "node:fs";
import { createRequire } from "node:module";
import { dirname, resolve, extname } from "node:path";
import { fileURLToPath } from "node:url";
import { runInNewContext } from "node:vm";
import ts from "typescript";

const require = createRequire(import.meta.url);
const root = fileURLToPath(new URL("../", import.meta.url));
export function typescriptLoader(globals = {}) {
  const cache = new Map();
  function load(file) {
    const path = resolve(root, file);
    if (cache.has(path)) return cache.get(path).exports;
    if (extname(path) === ".json") return JSON.parse(readFileSync(path, "utf8"));
    const loadedModule = { exports: {} };
    cache.set(path, loadedModule);
    const compiled = ts.transpileModule(readFileSync(path, "utf8"), {
      fileName: path,
      compilerOptions: { target: ts.ScriptTarget.ES2020, module: ts.ModuleKind.CommonJS, esModuleInterop: true, jsx: ts.JsxEmit.ReactJSX },
    }).outputText;
    const localRequire = (name) => {
      if (name.startsWith(".") || name.startsWith("@/")) {
        let target = name.startsWith("@/") ? resolve(root, name.slice(2)) : resolve(dirname(path), name);
        if (!extname(target)) target += ".ts";
        return load(target);
      }
      return require(name);
    };
    runInNewContext(compiled, { module: loadedModule, exports: loadedModule.exports, require: localRequire, AbortController, setTimeout, clearTimeout, console, ...globals }, { filename: path });
    return loadedModule.exports;
  }
  return load;
}
