/** Presentation metadata only. Never changes, fills or interpolates source values. */
export type SeriesEndLabel = { index: number; value: number; dy: number };

export function latestSeriesLabels(
  points: readonly Record<string, unknown>[], keys: readonly string[], timeKey = "time",
): Record<string, SeriesEndLabel> {
  const labels: Record<string, SeriesEndLabel> = {};
  for (const key of keys) {
    let latest = -Infinity;
    points.forEach((point, index) => {
      const time = point[timeKey];
      const value = point[key];
      if (typeof time === "number" && Number.isFinite(time) && time >= latest
        && typeof value === "number" && Number.isFinite(value)) {
        labels[key] = { index, value, dy: 0 };
        latest = time;
      }
    });
  }
  // Nearby endpoint values get stable vertical lanes, ordered from highest to lowest.
  const sorted = Object.entries(labels).sort((a, b) => b[1].value - a[1].value || a[0].localeCompare(b[0]));
  for (let start = 0; start < sorted.length;) {
    let end = start + 1;
    while (end < sorted.length && sorted[end - 1][1].value - sorted[end][1].value < 6) end++;
    const size = end - start;
    const shift = sorted[start][1].value >= 94 ? (size - 1) * 9
      : sorted[end - 1][1].value <= 6 ? -(size - 1) * 9 : 0;
    for (let i = start; i < end; i++) sorted[i][1].dy = (i - start - (size - 1) / 2) * 18 + shift;
    start = end;
  }
  return labels;
}

export function observationLabelOffset(value: number, seriesIndex: number) {
  return value >= 95 ? 15 : value <= 5 ? -11 : seriesIndex % 2 === 0 ? -11 : 15;
}
