import geography from "./geo/brazil-states.geo.json";

type Point = number[];
// Mercator projection of the versioned geographic coordinates, with uniform scale.
const mercator = ([longitude, latitude]: Point) => [longitude, -Math.log(Math.tan(Math.PI / 4 + latitude * Math.PI / 360)) * 180 / Math.PI];
const rings = (geometry: { type: string; coordinates: unknown }) => geometry.type === "Polygon"
  ? geometry.coordinates as Point[][] : (geometry.coordinates as Point[][][]).flat();
const points = geography.features.flatMap((feature) => rings(feature.geometry).flat().map(mercator));
const x0 = Math.min(...points.map(([x]) => x));
const y0 = Math.min(...points.map(([, y]) => y));
const width = Math.max(...points.map(([x]) => x)) - x0;
const height = Math.max(...points.map(([, y]) => y)) - y0;
const scale = 600 / Math.max(width, height);
const project = (point: Point) => {
  const [x, y] = mercator(point);
  return [(x - x0) * scale + 20, (y - y0) * scale + 20];
};

// Serialize display coordinates consistently across Node/Chromium Math.log implementations.
export const brazilViewBox = `0 0 ${(width * scale + 40).toFixed(2)} ${(height * scale + 40).toFixed(2)}`;
export const brazilStates = geography.features.map((feature) => {
  const polygons = rings(feature.geometry).map((ring) => ring.map(project));
  // Label anchor is the bounding-box center of the largest ring, never hand-placed.
  const main = [...polygons].sort((a, b) => b.length - a.length)[0];
  const center = [0, 1].map((axis) => Number(((Math.min(...main.map((p) => p[axis])) + Math.max(...main.map((p) => p[axis]))) / 2).toFixed(2)));
  return {
    ...feature.properties,
    center,
    path: polygons.map((ring) => ring.map(([x, y], i) => `${i ? "L" : "M"}${x.toFixed(2)},${y.toFixed(2)}`).join("") + "Z").join(""),
  };
});
