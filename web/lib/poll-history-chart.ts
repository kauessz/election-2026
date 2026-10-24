import { publicPercent } from "./presentation";
import { roundMilestones } from "./poll-history";

const DAY = 86_400_000;
export const historyViewport = (width: number) => width<=420 ? "narrow" : width<=640 ? "mobile" : "desktop";
export const compactHistoryPercent = (value: number) => `${value.toLocaleString("pt-BR",{minimumFractionDigits:1,maximumFractionDigits:1})}%`;
export function nearestHistoryPoint(points: readonly {id:string;x:number;y:number}[], x:number,y:number) {
  return points.reduce<{id:string;distance:number}|null>((nearest,point)=>{
    const distance=(point.x-x)**2+(point.y-y)**2;
    return !nearest || distance<nearest.distance ? {id:point.id,distance} : nearest;
  },null)?.id ?? null;
}

/** Mobile keeps the endpoints of the relevant observation period, not the padded domain. */
export function mobileHistoryTicks(times: readonly number[]): number[] {
  const sorted=times.filter(Number.isFinite).slice().sort((a,b)=>a-b);
  if (!sorted.length) return [];
  const first=sorted[0], last=sorted[sorted.length-1];
  return first===last || last-first<DAY ? [first] : [first,last];
}

/** Axis bounds only; observations and the other round never supply these bounds. */
export function roundTimelineDomain(polls: readonly { round: number; publishedAt: string }[], round: 1 | 2): [number, number] {
  const milestone = Date.parse(roundMilestones.find(item => item.round === round)!.date);
  const dates = polls.filter(poll => poll.round === round).map(poll => Date.parse(poll.publishedAt)).filter(Number.isFinite);
  const first = Math.min(milestone, ...dates), last = Math.max(milestone, ...dates);
  const padding = Math.max(DAY / 2, (last - first) * 0.06);
  return [first - padding, last + padding];
}

export function expandedPercentageDomain(values: readonly number[]): [number, number] {
  const finite = values.filter(Number.isFinite);
  if (!finite.length) return [0, 100];
  let low = Math.max(0, Math.floor((Math.min(...finite) - 5) / 5) * 5);
  let high = Math.min(100, Math.ceil((Math.max(...finite) + 5) / 5) * 5);
  while (high - low < 20) {
    if (low > 0) low -= 5;
    if (high - low < 20 && high < 100) high += 5;
  }
  return [low, high];
}

export type HistoryLabelPoint = {
  id: string; candidateKey: string; time: number; percentage: number; name: string; seriesIndex: number;
};

/** One latest observation per candidate; tied instants use a stable observation ID. */
export function latestCandidatePoints<T extends HistoryLabelPoint>(points: readonly T[]): T[] {
  const latest = new Map<string, T>();
  for (const point of points) {
    if (!Number.isFinite(point.time) || !Number.isFinite(point.percentage)) continue;
    const previous = latest.get(point.candidateKey);
    if (!previous || point.time > previous.time || (point.time === previous.time && point.id > previous.id)) latest.set(point.candidateKey, point);
  }
  return [...latest.values()].sort((a,b) => a.candidateKey.localeCompare(b.candidateKey));
}

export type PixelHistoryPoint = HistoryLabelPoint & { x: number; y: number };
export type LabelBounds = { left: number; right: number; top: number; bottom: number };
type Box = { x: number; y: number; width: number; height: number };
export type PlacedHistoryLabel = PixelHistoryPoint & Box & { text: string; final: boolean; crowded: boolean };
const overlap = (a: Box, b: Box) => Math.max(0, Math.min(a.x+a.width,b.x+b.width)-Math.max(a.x,b.x))
  * Math.max(0, Math.min(a.y+a.height/2,b.y+b.height/2)-Math.max(a.y-a.height/2,b.y-b.height/2));
const clamp = (value: number, min: number, max: number) => Math.max(min,Math.min(max,value));

/** Collision search moves text boxes only. Latest labels have priority; no label is dropped. */
export function layoutHistoryLabels(points: readonly PixelHistoryPoint[], bounds: LabelBounds, showAll: boolean, fontSize = 11,
  options: { mobile?: boolean; selectedIds?: readonly string[] } = {}): PlacedHistoryLabel[] {
  const finals = new Set(latestCandidatePoints(points).map(point => point.id));
  const ordered = points.filter(point => finals.has(point.id) || (showAll && (!options.mobile || options.selectedIds?.includes(point.id)))).slice().sort((a,b) =>
    Number(finals.has(b.id))-Number(finals.has(a.id)) || a.y-b.y || a.x-b.x || a.id.localeCompare(b.id));
  const placed: PlacedHistoryLabel[] = [];
  const pointBoxes = points.map(point => ({ x:point.x-6, y:point.y, width:12, height:12 }));
  for (const point of ordered) {
    const final = finals.has(point.id);
    const firstName = point.name.trim().split(/\s+/)[0];
    const shortName = firstName.length > 12 ? `${firstName.slice(0,11)}…` : firstName;
    const percentage=options.mobile ? compactHistoryPercent(point.percentage) : publicPercent(point.percentage);
    const text = final && (!showAll || options.mobile) ? `${shortName} ${percentage}` : percentage;
    // Conservative glyph width, including stroke and a gap between neighboring labels.
    const width = Math.min(bounds.right-bounds.left, text.length*fontSize*0.68+6), height = fontSize+7;
    const direction = point.seriesIndex%2 === 0 ? -1 : 1;
    const candidates: Box[] = [];
    for (let step=0; step<=Math.ceil((bounds.bottom-bounds.top)/height); step++) {
      for (const sign of step === 0 ? [direction] : [direction,-direction]) {
        const y=clamp(point.y+sign*(12+step*height),bounds.top+height/2,bounds.bottom-height/2);
        for (const x of options.mobile ? [point.x-width-8,point.x+8] : [point.x+8,point.x-width-8]) candidates.push({x:clamp(x,bounds.left,bounds.right-width),y,width,height});
      }
    }
    let best=candidates[0], minimum=Infinity;
    for (const box of candidates) {
      const score = placed.reduce((sum,label)=>sum+overlap(box,label),0)*4 + pointBoxes.reduce((sum,marker)=>sum+overlap(box,marker),0);
      if (score < minimum) { best=box; minimum=score; }
      if (score === 0) break;
    }
    placed.push({...point,...best,text,final,crowded:minimum>0});
  }
  return placed;
}
