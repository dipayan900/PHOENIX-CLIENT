#!/usr/bin/env python3
"""Download exact-version Fabric AppleSkin releases for Phoenix test packages.

Missing Modrinth builds are skipped rather than substituting an incompatible JAR.
The Android installer installs a JAR only when an exact Minecraft version match exists.
"""
import hashlib
import json
import pathlib
import sys
import urllib.parse
import urllib.request
import zipfile
import io

if len(sys.argv) != 2:
    raise SystemExit("usage: download-appleskin.py <output-directory>")

out = pathlib.Path(sys.argv[1])
out.mkdir(parents=True, exist_ok=True)
headers = {"User-Agent": "PhoenixClient/1.0 (AppleSkin optional bundled dependency)"}
downloaded = 0

for minor in range(1, 12):
    mc = f"1.21.{minor}"
    query = urllib.parse.urlencode({
        "game_versions": json.dumps([mc]),
        "loaders": json.dumps(["fabric"]),
    })
    endpoint = "https://api.modrinth.com/v2/project/appleskin/version?" + query
    try:
        request = urllib.request.Request(endpoint, headers=headers)
        with urllib.request.urlopen(request, timeout=30) as response:
            versions = json.loads(response.read().decode("utf-8"))
        releases = [v for v in versions if v.get("version_type") == "release" and v.get("files")]
        releases.sort(key=lambda v: v.get("date_published", ""), reverse=True)
        selected = None
        for version in releases:
            jars = [f for f in version["files"] if f.get("filename", "").lower().endswith(".jar")]
            if jars:
                selected = (version, next((f for f in jars if f.get("primary")), jars[0]))
                break
        if selected is None:
            print(f"NOTICE: AppleSkin has no exact Fabric release for {mc}; leaving it out to protect launch stability.")
            continue
        version, file_info = selected
        req = urllib.request.Request(file_info["url"], headers=headers)
        with urllib.request.urlopen(req, timeout=60) as response:
            data = response.read()
        expected = file_info.get("hashes", {}).get("sha512")
        if expected and hashlib.sha512(data).hexdigest().lower() != expected.lower():
            raise RuntimeError(f"AppleSkin SHA-512 verification failed for {mc}")
        # Phoenix does not bundle JEI. Remove AppleSkin's optional JEI mixin
        # so it does not target JEI classes that are intentionally absent.
        with zipfile.ZipFile(io.BytesIO(data), "r") as source:
            entries = {name: source.read(name) for name in source.namelist()}
        metadata_name = "fabric.mod.json"
        if metadata_name not in entries:
            raise RuntimeError(f"AppleSkin {mc} JAR has no fabric.mod.json")
        metadata = json.loads(entries[metadata_name].decode("utf-8"))
        retained = []
        removed = []
        for item in metadata.get("mixins", []):
            config_name = item if isinstance(item, str) else item.get("config", "")
            if "jei" in str(config_name).lower():
                removed.append(str(config_name))
            else:
                retained.append(item)
        metadata["mixins"] = retained
        entries[metadata_name] = json.dumps(metadata, separators=(",", ":")).encode("utf-8")
        for config_name in removed:
            entries.pop(config_name, None)
        for name in list(entries):
            if name.lower().endswith(".mixins.json") and "jei" in name.lower():
                entries.pop(name, None)
        target = out / f"appleskin-fabric-{mc}.jar"
        with zipfile.ZipFile(target, "w", compression=zipfile.ZIP_DEFLATED) as patched:
            for name, payload in entries.items():
                patched.writestr(name, payload)
        print(f"Removed optional JEI mixin for {mc}; JEI is not bundled.")
        downloaded += 1
        print(f"AppleSkin {version['version_number']} bundled for Minecraft {mc}")
    except Exception as error:
        print(f"NOTICE: AppleSkin download skipped for {mc}: {error}")

if downloaded == 0:
    print("NOTICE: No AppleSkin jars were downloaded; Phoenix build continues without optional AppleSkin.")
else:
    print(f"Bundled exact-version AppleSkin Fabric jars: {downloaded}")
