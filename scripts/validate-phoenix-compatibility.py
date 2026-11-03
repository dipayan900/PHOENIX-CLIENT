#!/usr/bin/env python3
"""Validate Phoenix's declared compatibility target and truthful support matrix."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MATRIX = ROOT / "phoenix-compatibility" / "compatibility-matrix.json"

def main():
    data = json.loads(MATRIX.read_text(encoding="utf-8"))
    target = data["target"]
    assert target["minecraft"]["minimum"] == "1.8.9"
    assert target["minecraft"]["maximum"] == "latest"
    assert set(target["loaders"]) == {"vanilla", "fabric"}
    assert target["manualModInstallation"] is False

    adapters = data["architecture"]["versionAdapters"]
    assert [a["id"] for a in adapters] == ["legacy", "transition", "modern"]
    assert adapters[0]["range"] == "1.8.9-1.12.2"
    assert adapters[1]["range"] == "1.13-1.16.5"
    assert adapters[2]["range"] == "1.17-latest"

    fabric_versions = set(
        data["architecture"]["loaderAdapters"]["fabric"]["implementedMinecraftVersions"]
    )
    vanilla_status = data["architecture"]["loaderAdapters"]["vanilla"]["status"]
    assert fabric_versions == {"1.21.1"}
    assert vanilla_status == "planned"

    for build in data["verifiedBuilds"]:
        assert build["loader"] in target["loaders"]
        assert build["minecraft"] in fabric_versions if build["loader"] == "fabric" else False
        if build["status"] == "build-verified":
            assert build["runtimeVerified"] is False or build["runtimeVerified"] is True

    print("Phoenix compatibility matrix is valid.")
    print("Target: Minecraft 1.8.9–latest; Vanilla + Fabric; no manual mod installation.")
    print("Currently build-verified: Fabric 1.21.1 only. Runtime verification remains separate.")

if __name__ == "__main__":
    main()
