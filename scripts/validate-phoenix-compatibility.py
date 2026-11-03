#!/usr/bin/env python3
"""Validate Phoenix's requested 1.21.1-1.21.11 target and managed launch paths."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DATA = json.loads((ROOT / "phoenix-compatibility/compatibility-matrix.json").read_text())
EXPECTED = [f"1.21.{i}" for i in range(1, 12)]

def main():
    target = DATA["target"]
    assert target["minecraft"]["minimum"] == EXPECTED[0]
    assert target["minecraft"]["maximum"] == EXPECTED[-1]
    assert target["minecraft"]["requestedVersions"] == EXPECTED
    assert set(target["loaders"]) == {"vanilla", "fabric"}
    assert target["manualModInstallation"] is False

    fabric = DATA["architecture"]["loaderAdapters"]["fabric"]
    assert fabric["configuredMinecraftVersions"] == EXPECTED
    assert fabric["buildVerifiedMinecraftVersions"] == ["1.21.1"]
    vanilla = DATA["architecture"]["loaderAdapters"]["vanilla"]
    assert vanilla["status"] == "managed-bootstrap"
    assert "not unmodified Vanilla" in vanilla["integration"]

    rows = DATA["architecture"]["versionStatus"]
    assert [r["minecraft"] for r in rows] == EXPECTED
    for row in rows:
        assert row["vanilla"] == "managed-fabric-bootstrap-pending-runtime-test"
        assert row["fabric"] in {"build-verified-runtime-unverified", "build-pending"}

    print("Phoenix target: Minecraft 1.21.1-1.21.11.")
    print("CI builds 11 version-matched Fabric adapters.")
    print("Vanilla selections use a separate managed Fabric-backed runtime.")
    print("Runtime verification is still required before release claims.")

if __name__ == "__main__":
    main()
