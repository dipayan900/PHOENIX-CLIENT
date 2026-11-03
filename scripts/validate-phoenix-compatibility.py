#!/usr/bin/env python3
"""Validate Phoenix's requested 1.21.1–1.21.11 target and truthful support matrix."""
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MATRIX = ROOT / "phoenix-compatibility" / "compatibility-matrix.json"
EXPECTED = [f"1.21.{minor}" for minor in range(1, 12)]

def main():
    data = json.loads(MATRIX.read_text(encoding="utf-8"))
    target = data["target"]
    mc = target["minecraft"]
    assert mc["minimum"] == "1.21.1"
    assert mc["maximum"] == "1.21.11"
    assert mc["requestedVersions"] == EXPECTED
    assert set(target["loaders"]) == {"vanilla", "fabric"}
    assert target["manualModInstallation"] is False

    adapters = data["architecture"]["versionAdapters"]
    assert len(adapters) == 1
    assert adapters[0]["id"] == "modern-1.21"
    assert adapters[0]["range"] == "1.21.1-1.21.11"

    fabric_versions = set(
        data["architecture"]["loaderAdapters"]["fabric"]["implementedMinecraftVersions"]
    )
    assert fabric_versions == {"1.21.1"}
    assert data["architecture"]["loaderAdapters"]["vanilla"]["status"] == "planned"

    rows = data["architecture"]["versionStatus"]
    assert [row["minecraft"] for row in rows] == EXPECTED
    for row in rows:
        assert row["vanilla"] == "planned"
        expected = "build-verified-runtime-unverified" if row["minecraft"] in fabric_versions else "planned"
        assert row["fabric"] == expected

    for build in data["verifiedBuilds"]:
        assert build["minecraft"] in EXPECTED
        assert build["loader"] in target["loaders"]
        if build["status"] == "build-verified":
            assert build["runtimeVerified"] in (False, True)

    print("Phoenix compatibility target: Minecraft 1.21.1–1.21.11.")
    print("Requested loaders: Vanilla + Fabric; launcher-managed installation.")
    print("Current build evidence: Fabric 1.21.1 only; runtime not yet verified.")
    print("All other version/loader combinations remain explicitly unverified.")

if __name__ == "__main__":
    main()
