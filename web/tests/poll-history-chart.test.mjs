import test from "node:test";
import assert from "node:assert/strict";
import { typescriptLoader } from "./load-typescript.mjs";
const load=typescriptLoader();
const { roundTimelineDomain, expandedPercentageDomain, latestCandidatePoints, layoutHistoryLabels }=load("lib/poll-history-chart.ts");
const { historyViewport,compactHistoryPercent,mobileHistoryTicks,nearestHistoryPoint }=load("lib/poll-history-chart.ts");
const DAY=86400000;

test("each X domain uses only its round, with its own milestone and temporal padding",()=>{
  const polls=[{round:1,publishedAt:"2026-09-28T12:00:00Z"},{round:2,publishedAt:"2026-10-22T12:00:00Z"}];
  const [start,end]=roundTimelineDomain(polls,1);
  assert.ok(start<Date.parse(polls[0].publishedAt));
  assert.ok(end>Date.parse("2026-10-04T15:00:00Z"));
  assert.ok(end<Date.parse("2026-10-06T00:00:00Z"));
  const second=roundTimelineDomain(polls,2);
  assert.ok(second[0]>Date.parse("2026-10-20T00:00:00Z"));
  assert.ok(second[1]>Date.parse("2026-10-25T15:00:00Z"));
  assert.deepEqual(Array.from(roundTimelineDomain([polls[0]],1)),[start,end]);
});

test("empty, invalid and same-date observations have a nonzero bounded X range",()=>{
  for (const polls of [[],[{round:1,publishedAt:"invalid"}],[{round:1,publishedAt:"2026-10-04T15:00:00Z"}]]) {
    const [start,end]=roundTimelineDomain(polls,1);
    assert.equal(end-start,DAY);
    assert.ok(Number.isFinite(start) && Number.isFinite(end));
  }
});

test("expanded Y pads visible originals and rounds outwards to multiples of five",()=>{
  const values=[34,47];
  assert.deepEqual(Array.from(expandedPercentageDomain(values)),[25,55]);
  assert.deepEqual(values,[34,47]);
  const [low,high]=expandedPercentageDomain([44.1,47.03]);
  assert.equal(low%5,0); assert.equal(high%5,0);
  assert.ok(low<=39.1 && high>=52.03);
});

test("expanded Y clamps to 0–100 and preserves at least twenty percentage points",()=>{
  for (const values of [[0],[100],[50],[49.9,50.1],[0,100],[1,4],[96,99]]) {
    const [low,high]=expandedPercentageDomain(values);
    assert.ok(low>=0 && high<=100 && high-low>=20);
    assert.equal(low%5,0); assert.equal(high%5,0);
    assert.ok(low<=Math.min(...values) && high>=Math.max(...values));
  }
});

test("empty Y has full range and nonfinite percentages are ignored",()=>{
  assert.deepEqual(Array.from(expandedPercentageDomain([])),[0,100]);
  assert.deepEqual(Array.from(expandedPercentageDomain([NaN,Infinity])),[0,100]);
  assert.deepEqual(expandedPercentageDomain([NaN,44]),expandedPercentageDomain([44]));
});

const point=(id,candidateKey,time,percentage,x=80,y=100,seriesIndex=0)=>({id,candidateKey,time,percentage,x,y,seriesIndex,name:candidateKey==="a"?"Lula":"Flávio Bolsonaro"});
test("latest point is selected per candidate by chronology, including zero and stable ties",()=>{
  const points=[point("new","a",3,47),point("old","a",1,42),point("zero","b",2,0),point("z","a",3,46),point("bad","b",4,NaN)];
  const before=structuredClone(points);
  const latest=latestCandidatePoints(points);
  assert.deepEqual(Array.from(latest,p=>p.id),["z","zero"]);
  assert.deepEqual(points,before);
  assert.deepEqual(latestCandidatePoints(points.slice().reverse()),latest);
});

const bounds={left:45,right:300,top:30,bottom:370};
const intersects=(a,b)=>a.x<b.x+b.width && a.x+a.width>b.x && a.y-a.height/2<b.y+b.height/2 && a.y+a.height/2>b.y-b.height/2;
test("default labels retain every candidate's latest name and public percentage, with no other labels",()=>{
  const points=[point("a1","a",1,44),point("a2","a",2,47),point("b2","b",2,44.1,80,110,1)];
  const labels=layoutHistoryLabels(points,bounds,false);
  assert.equal(labels.length,2);
  assert.ok(labels.every(label=>label.final));
  assert.deepEqual(Array.from(labels,l=>l.text).sort(),["Flávio 44,10%","Lula 47,00%"]);
});

test("nearby coincident observations receive deterministic nonoverlapping text boxes without moving points",()=>{
  const points=Array.from({length:8},(_,i)=>point(`p${i}`,i%2===0?"a":"b",i,45+i/100,100,150+i/10,i%2));
  const before=structuredClone(points);
  const labels=layoutHistoryLabels(points,bounds,true);
  assert.equal(labels.length,points.length);
  assert.ok(labels.every(l=>/^\d+,\d{2}%$/.test(l.text)));
  assert.ok(labels.every(l=>!l.crowded));
  for(let i=0;i<labels.length;i++) for(let j=i+1;j<labels.length;j++) assert.ok(!intersects(labels[i],labels[j]));
  assert.deepEqual(labels,layoutHistoryLabels(points.slice().reverse(),bounds,true));
  assert.deepEqual(points,before);
});

test("mobile label bounds keep all text boxes inside the chart including 0 and 100 percent",()=>{
  const points=[point("low","a",1,0,50,365),point("high","b",1,100,155,35,1)];
  for (const showAll of [false,true]) for (const label of layoutHistoryLabels(points,bounds,showAll,10)) {
    assert.ok(label.x>=bounds.left && label.x+label.width<=bounds.right);
    assert.ok(label.y-label.height/2>=bounds.top && label.y+label.height/2<=bounds.bottom);
  }
});

test("crowded fallback never silently omits a latest label or an observation label",()=>{
  const points=Array.from({length:30},(_,i)=>point(`id${i}`,`candidate${i}`,1,50,50,50,i));
  const tiny={left:0,right:140,top:0,bottom:40};
  const labels=layoutHistoryLabels(points,tiny,false);
  assert.equal(labels.length,30);
  assert.ok(labels.some(l=>l.crowded));
  assert.ok(labels.every(l=>l.final));
});

test("mobile breakpoints include 640 and narrow 420 without changing desktop",()=>{
  assert.equal(historyViewport(641),"desktop");assert.equal(historyViewport(640),"mobile");
  assert.equal(historyViewport(421),"mobile");assert.equal(historyViewport(420),"narrow");
  for(const width of [390,360,320])assert.equal(historyViewport(width),"narrow");
});
test("compact mobile values round for display only",()=>{
  assert.equal(compactHistoryPercent(50.5),"50,5%");assert.equal(compactHistoryPercent(49.49),"49,5%");
  assert.equal(compactHistoryPercent(50),"50,0%");assert.equal(compactHistoryPercent(100),"100,0%");
});
test("mobile ticks preserve first and last observation dates with no dense intervening ticks",()=>{
  const times=[3*DAY,DAY,2*DAY,NaN];
  assert.deepEqual(Array.from(mobileHistoryTicks(times)),[DAY,3*DAY]);
  assert.deepEqual(Array.from(mobileHistoryTicks([DAY,DAY+1000])),[DAY]);
  assert.equal(mobileHistoryTicks([]).length,0);
});
test("mobile labels prioritize endpoints and selected survey instead of labeling the cloud",()=>{
  const points=[point("old-a","a",1,45),point("old-b","b",1,44,80,110,1),point("new-a","a",2,50.5),point("new-b","b",2,49.5,80,110,1)];
  const labels=layoutHistoryLabels(points,bounds,true,11,{mobile:true,selectedIds:["old-a","old-b"]});
  assert.equal(labels.length,4);
  assert.ok(labels.some(l=>l.text==="Lula 50,5%"));
  assert.ok(labels.some(l=>l.text==="Flávio 49,5%"));
  assert.equal(layoutHistoryLabels(points,bounds,true,11,{mobile:true}).length,2);
  assert.equal(layoutHistoryLabels(points,bounds,false,11,{mobile:true,selectedIds:["old-a"]}).length,2);
});
test("mobile final labels fit inside actual plot bounds without a right gutter",()=>{
  const mobileBounds={left:38,right:205,top:42,bottom:310};
  const points=[point("a","a",1,50.5,195,140),point("b","b",1,49.5,195,150,1)];
  const labels=layoutHistoryLabels(points,mobileBounds,false,11,{mobile:true});
  assert.equal(labels.length,2);assert.ok(!intersects(labels[0],labels[1]));
  assert.ok(labels.every(l=>l.x>=mobileBounds.left && l.x+l.width<=mobileBounds.right));
});
test("overlapping hit areas resolve to the closest real point without changing coordinates",()=>{
  const targets=[{id:"near",x:10,y:10},{id:"topmost",x:20,y:11}];
  assert.equal(nearestHistoryPoint(targets,11,10),"near");
  assert.equal(nearestHistoryPoint(targets,21,11),"topmost");
  assert.equal(nearestHistoryPoint([],0,0),null);
  assert.deepEqual(targets,[{id:"near",x:10,y:10},{id:"topmost",x:20,y:11}]);
});
