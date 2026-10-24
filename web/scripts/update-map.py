"""Reproduce the local geographic asset; never runs in the application/build."""
import hashlib
import json
import pathlib
import urllib.request

REVISION = "117488dc884bad03366ff727eca013e434615127"
SOURCE = f"https://raw.githubusercontent.com/nvkelso/natural-earth-vector/{REVISION}/geojson/ne_50m_admin_1_states_provinces.geojson"
raw = urllib.request.urlopen(SOURCE, timeout=60).read()
source = json.loads(raw)
features = []
for feature in source["features"]:
    props = feature["properties"]
    if props["admin"] != "Brazil":
        continue
    uf = props["iso_3166_2"].removeprefix("BR-")
    features.append({
        "type": "Feature",
        "properties": {"uf": uf, "name": "Distrito Federal" if uf == "DF" else props["name_pt"]},
        "geometry": feature["geometry"],
    })
assert len(features) == len({f["properties"]["uf"] for f in features}) == 27
asset = {
    "type": "FeatureCollection",
    "attribution": {"source": SOURCE, "revision": REVISION, "sourceSha256": hashlib.sha256(raw).hexdigest(),
                    "license": "Public domain", "terms": "https://www.naturalearthdata.com/about/terms-of-use/"},
    "features": sorted(features, key=lambda f: f["properties"]["uf"]),
}
target = pathlib.Path(__file__).resolve().parents[1] / "lib/geo/brazil-states.geo.json"
target.parent.mkdir(parents=True, exist_ok=True)
target.write_text(json.dumps(asset, ensure_ascii=False, separators=(",", ":")) + "\n", encoding="utf-8")
print(f"27 UFs; {target.stat().st_size} bytes; source SHA-256 {asset['attribution']['sourceSha256']}")
