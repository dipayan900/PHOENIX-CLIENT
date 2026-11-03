#!/usr/bin/env python3
"""Resolve published Yarn and Fabric API builds for Minecraft 1.21.1–1.21.11."""
import re
import sys
import urllib.request
import xml.etree.ElementTree as ET

if len(sys.argv) != 2:
    raise SystemExit("usage: resolve-fabric-versions.py <minecraft-version>")

minecraft = sys.argv[1]
match = re.fullmatch(r"1\.21\.(\d+)", minecraft)
if not match or not 1 <= int(match.group(1)) <= 11:
    raise SystemExit(f"Unsupported Phoenix Minecraft target: {minecraft} (expected 1.21.1–1.21.11)")

def published_versions(url):
    request = urllib.request.Request(url, headers={"User-Agent": "PhoenixClient-CI"})
    with urllib.request.urlopen(request, timeout=45) as response:
        root = ET.fromstring(response.read())
    return [node.text for node in root.findall("./versioning/versions/version") if node.text]

yarn_versions = published_versions(
    "https://maven.fabricmc.net/net/fabricmc/yarn/maven-metadata.xml"
)
yarn = [
    value for value in yarn_versions
    if re.fullmatch(re.escape(minecraft) + r"\+build\.\d+", value)
]
if not yarn:
    raise SystemExit(f"No Yarn mappings are published for Minecraft {minecraft}")
yarn.sort(key=lambda value: int(value.rsplit(".", 1)[-1]))

api_versions = published_versions(
    "https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml"
)
api = [value for value in api_versions if value.endswith("+" + minecraft)]
if not api:
    raise SystemExit(f"No Fabric API build is published for Minecraft {minecraft}")

def numeric_version(value):
    return tuple(int(part) for part in value.split("+", 1)[0].split(".") if part.isdigit())

api.sort(key=numeric_version)
print(yarn[-1])
print(api[-1])
