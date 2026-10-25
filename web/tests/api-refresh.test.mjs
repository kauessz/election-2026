import test from "node:test";
import assert from "node:assert/strict";
import { typescriptLoader } from "./load-typescript.mjs";
const flush = () => new Promise((resolve) => setImmediate(resolve));
function setup() {
  const timers = new Map(); let timerId = 0;
  const load = typescriptLoader({ setTimeout: (callback, delay) => { timers.set(++timerId, { callback, delay }); return timerId; }, clearTimeout: (id) => timers.delete(id) });
  const { createApiRefresh } = load("lib/api-refresh.ts");
  const updates = [], requests = [];
  const session = createApiRefresh({ refreshMs: 60000, now: () => Date.parse("2026-10-23T12:00:00Z"),
    notify: (state) => updates.push(state), errorMessage: (error) => error.message,
    load: (signal) => new Promise((resolve, reject) => requests.push({ signal, resolve, reject })),
  });
  return { session, updates, requests, timers };
}
test("first load uses loading; refresh retains data and exposes refreshing", async () => {
  const { session, updates, requests } = setup(); session.refresh(); await flush();
  assert.equal(updates.at(-1).loading, true);
  requests[0].resolve({ value: 1 }); await flush();
  session.refresh(); await flush();
  assert.deepEqual(updates.at(-1).data, { value: 1 });
  assert.equal(updates.at(-1).loading, false); assert.equal(updates.at(-1).refreshing, true);
  requests[1].resolve({ value: 2 }); await flush();
  assert.deepEqual(updates.at(-1).data, { value: 2 });
  assert.equal(updates.at(-1).refreshing, false); session.dispose();
});
test("failed refresh retains last good data and successful timestamp", async () => {
  const { session, updates, requests } = setup(); session.refresh(); await flush();
  requests[0].resolve([1]); await flush(); const timestamp = updates.at(-1).lastUpdatedAt;
  session.refresh(); await flush(); requests[1].reject(new Error("Offline")); await flush();
  assert.deepEqual(updates.at(-1).data, [1]); assert.equal(updates.at(-1).error, "Offline");
  assert.equal(updates.at(-1).lastUpdatedAt, timestamp); session.dispose();
});
test("requests never overlap; timer starts only after completion and manual refresh cancels it", async () => {
  const { session, requests, timers } = setup(); session.refresh(); session.refresh(); await flush();
  assert.equal(requests.length, 1); assert.equal(timers.size, 0);
  requests[0].resolve([]); await flush(); assert.equal(timers.size, 1);
  assert.equal([...timers.values()][0].delay, 60000);
  session.refresh(); await flush(); assert.equal(timers.size, 0); assert.equal(requests.length, 2);
  session.refresh(); await flush(); assert.equal(requests.length, 2); session.dispose();
});
test("automatic refresh runs recursively and stops on disposal", async () => {
  const { session, requests, timers } = setup(); session.refresh(); await flush();
  requests[0].resolve([]); await flush(); [...timers.values()][0].callback(); await flush();
  assert.equal(requests.length, 2); session.dispose();
  assert.equal(requests[1].signal.aborted, true); assert.equal(timers.size, 0);
});
test("disposed path session aborts and ignores late responses, leaving new data intact", async () => {
  const first = setup(), second = setup(); first.session.refresh(); await flush();
  const before = first.updates.length; first.session.dispose(); second.session.refresh(); await flush();
  second.requests[0].resolve("new path"); first.requests[0].resolve("old path"); await flush();
  assert.equal(first.requests[0].signal.aborted, true); assert.equal(first.updates.length, before);
  assert.equal(second.updates.at(-1).data, "new path"); second.session.dispose();
});
test("initial failure permits retry without stale loading state", async () => {
  const { session, updates, requests } = setup(); session.refresh(); await flush();
  requests[0].reject(new Error("Offline")); await flush(); assert.equal(updates.at(-1).loading, false);
  session.refresh(); await flush(); assert.equal(updates.at(-1).loading, true);
  requests[1].resolve([]); await flush(); assert.equal(updates.at(-1).error, null); session.dispose();
});

test("hidden live sessions do not load until visibility resumes", async () => {
  let visible=false,calls=0;
  const {createApiRefresh}=typescriptLoader()("lib/api-refresh.ts");
  const session=createApiRefresh({isVisible:()=>visible,load:async()=>++calls,notify:()=>{},errorMessage:String});
  session.refresh();await flush();assert.equal(calls,0);
  visible=true;session.refresh();await flush();assert.equal(calls,1);session.dispose();
});

test("hiding during a pending request pauses its next timer; returning cannot overlap it", async () => {
  let visible=true,resolve;const timers=new Map();let id=0,calls=0;
  const {createApiRefresh}=typescriptLoader({setTimeout:(fn,ms)=>{timers.set(++id,{fn,ms});return id;},clearTimeout:key=>timers.delete(key)})("lib/api-refresh.ts");
  const session=createApiRefresh({refreshMs:15000,isVisible:()=>visible,load:()=>{calls++;return new Promise(r=>resolve=r);},notify:()=>{},errorMessage:String});
  session.refresh();await flush();visible=false;session.pause();session.refresh();await flush();assert.equal(calls,1);
  resolve(1);await flush();assert.equal(timers.size,0);
  visible=true;session.refresh();session.refresh();await flush();assert.equal(calls,2);
  resolve(2);await flush();assert.equal([...timers.values()][0].ms,15000);
  session.pause();assert.equal(timers.size,0);session.dispose();
});

test("live errors stay visible during a retry until a successful response arrives", async () => {
  const updates=[],requests=[];const {createApiRefresh}=typescriptLoader()("lib/api-refresh.ts");
  const session=createApiRefresh({retainErrorWhileRefreshing:true,load:()=>new Promise((resolve,reject)=>requests.push({resolve,reject})),notify:s=>updates.push(s),errorMessage:e=>e.message});
  session.refresh();await flush();requests[0].resolve({votes:123});await flush();
  session.refresh();await flush();requests[1].reject(new Error("offline"));await flush();
  session.refresh();await flush();assert.equal(updates.at(-1).error,"offline");assert.equal(updates.at(-1).data.votes,123);
  requests[2].resolve({votes:124});await flush();assert.equal(updates.at(-1).error,null);assert.equal(updates.at(-1).data.votes,124);session.dispose();
});
