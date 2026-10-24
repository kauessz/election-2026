import type { LabelProps } from "recharts";
import { publicPercent } from "@/lib/presentation";
import type { SeriesEndLabel } from "@/lib/chart-labels";

/** LabelList supplies the actual point coordinates, including responsive chart geometry. */
export function ChartValueLabel({ label, endpoint, color, prefix = "" }: {
  label: LabelProps; endpoint?: SeriesEndLabel; color: string; prefix?: string;
}) {
  const box = label.viewBox;
  if (!endpoint || label.index !== endpoint.index || !box || !("x" in box) || !("y" in box)
    || typeof box.x !== "number" || typeof box.y !== "number") return <g />;
  return <g aria-hidden="true" pointerEvents="none" className="chart-end-value">
    {endpoint.dy !== 0 && <path d={`M${box.x + 3},${box.y} L${box.x + 7},${box.y + endpoint.dy}`}
      stroke={color} fill="none" strokeWidth={1} opacity={0.6} />}
    <text x={box.x + 9} y={box.y + endpoint.dy} dominantBaseline="middle" fontSize={11}
      fontWeight={600} fill={color} stroke="white" strokeWidth={3} paintOrder="stroke">
      {prefix}{publicPercent(endpoint.value)}
    </text>
  </g>;
}
