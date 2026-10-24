import test from "node:test";
import assert from "node:assert/strict";
import { typescriptLoader } from "./load-typescript.mjs";
const { canApproveDiscovery,terminalDiscovery,discoveryStatusLabels }=typescriptLoader()("lib/poll-discovery.ts");
const ready={status:"READY_FOR_REVIEW",dryRunHash:"verified-hash",dryRunReport:{failed:0}};
test("inbox import requires current successful preview, clean form and explicit approval",()=>{
  assert.equal(canApproveDiscovery(ready,false,true),true);
  for(const [item,dirty,approved] of [[ready,true,true],[ready,false,false],[{...ready,dryRunHash:null},false,true],[{...ready,dryRunReport:{failed:1}},false,true],[{...ready,status:"IMPORTED"},false,true]])assert.equal(canApproveDiscovery(item,dirty,approved),false);
});
test("terminal discoveries remain read-only and every server status has a label",()=>{
  assert.equal(terminalDiscovery({status:"IMPORTED"}),true);assert.equal(terminalDiscovery({status:"IGNORED"}),true);
  assert.equal(terminalDiscovery(ready),false);assert.equal(Object.keys(discoveryStatusLabels).length,7);
});
