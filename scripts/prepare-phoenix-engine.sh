#!/usr/bin/env bash
set -euo pipefail
PHOENIX_ROOT="${1:?Phoenix repository path required}"
ENGINE_ROOT="${2:?Amethyst checkout path required}"
APP="$ENGINE_ROOT/app_pojavlauncher"
test -f "$APP/build.gradle"
python3 - "$APP/build.gradle" "$APP/src/main/res/values/strings.xml" "$APP/src/main/res/values/colors.xml" "$APP/src/main/assets/about_en.txt" "$APP/src/main/AndroidManifest.xml" "$APP/src/main/java/net/kdt/pojavlaunch/TestStorageActivity.java" <<'PY'
from pathlib import Path
import sys
gradle, strings, colors, about, manifest, test_storage = map(Path, sys.argv[1:])
g = gradle.read_text()
g = g.replace("compileSdk = 37", "compileSdk = 36\n    compileSdkExtension = 18")
replacements = {
    'applicationId "org.angelauramc.amethyst"': 'applicationId "com.phoenix.client"',
    "applicationIdSuffix '.debug'": "// Phoenix uses one stable application ID across build types.",
    'resValue "string", "app_name", "Amethyst (Debug)"': 'resValue "string", "app_name", "Phoenix Client"',
    'resValue "string", "app_short_name", "Amethyst (Debug)"': 'resValue "string", "app_short_name", "Phoenix"',
    'resValue "string", "app_name", "Amethyst"': 'resValue "string", "app_name", "Phoenix Client"',
    'resValue "string", "app_short_name", "Amethyst"': 'resValue "string", "app_short_name", "Phoenix"',
    "'org.angelauramc.amethyst.debug.scoped.gamefolder'": "'com.phoenix.client.scoped.gamefolder'",
    "'org.angelauramc.amethyst.debug.scoped.controlfolder'": "'com.phoenix.client.scoped.controlfolder'",
    "'org.angelauramc.amethyst.scoped.gamefolder'": "'com.phoenix.client.scoped.gamefolder'",
    "'org.angelauramc.amethyst.scoped.controlfolder'": "'com.phoenix.client.scoped.controlfolder'",
    "'org.angelauramc.amethyst.debug'": "'com.phoenix.client'",
    "'org.angelauramc.amethyst'": "'com.phoenix.client'",
}
for old, new in replacements.items():
    if old not in g:
        print(f"NOTICE: expected upstream setting not found: {old}")
    g = g.replace(old, new)
gradle.write_text(g)
s = strings.read_text()
s = s.replace('<string name="app_name" translatable="false">Amethyst</string>',
              '<string name="app_name" translatable="false">Phoenix Client</string>')
s = s.replace('Amethyst has unexpectedly crashed', 'Phoenix Client has unexpectedly crashed')
strings.write_text(s)
c = colors.read_text()
for old, new in {'#9649b8':'#FF6A24','#181818':'#100D12','#464646':'#30242C','#242424':'#171018','#232323':'#171018'}.items():
    c = c.replace(old, new)
colors.write_text(c)
a = about.read_text()
a = a.replace('%s (Minecraft Java Launcher for Android), version %s by Tran Khanh Duy (based on "Boardwalk" app)',
              'Phoenix Client — Minecraft Java Edition for Android, version %s. Built using the Amethyst engine.')
about.write_text(a)

# Enable Jetpack Compose in the engine app so Phoenix is the actual launcher UI.
g = gradle.read_text()
g = g.replace(
    "plugins {\n    id 'com.android.application' version '9.3.1'\n}",
    "plugins {\n    id 'com.android.application' version '9.3.1'\n    id 'org.jetbrains.kotlin.plugin.compose' version '2.3.0'\n}"
)
g = g.replace("buildFeatures {\n        prefab true", "buildFeatures {\n        compose true\n        prefab true")
g = g.replace("dependencies {\n", """dependencies {
    implementation platform('androidx.compose:compose-bom:2026.09.00')
    implementation 'androidx.activity:activity-compose:1.12.0'
    implementation 'androidx.compose.ui:ui'
    implementation 'androidx.compose.ui:ui-tooling-preview'
    implementation 'androidx.compose.material3:material3'
""")
gradle.write_text(g)

m = manifest.read_text()
m = m.replace(
    '<activity\n            android:name=".LauncherActivity"',
    '<activity android:name=".PhoenixDashboardActivity" android:exported="false" />\n        <activity\n            android:name=".LauncherActivity"'
)
manifest.write_text(m)

t = test_storage.read_text()
t = t.replace("new Intent(this, LauncherActivity.class)", "new Intent(this, PhoenixDashboardActivity.class)")
test_storage.write_text(t)
PY
mkdir -p "$APP/src/main/res/drawable"
cp "$PHOENIX_ROOT/app/src/main/res/drawable/ic_phoenix.xml" "$APP/src/main/res/drawable/ic_phoenix_foreground.xml"
cat > "$APP/src/main/res/drawable/phoenix_icon_background.xml" <<'XML'
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <solid android:color="#171018" />
</shape>
XML
cat > "$APP/src/main/res/mipmap-anydpi-v26/ic_launcher.xml" <<'XML'
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/phoenix_icon_background" />
    <foreground android:drawable="@drawable/ic_phoenix_foreground" />
</adaptive-icon>
XML
cp "$APP/src/main/res/mipmap-anydpi-v26/ic_launcher.xml" "$APP/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml"
mkdir -p "$APP/src/main/java/net/kdt/pojavlaunch"
cp "$PHOENIX_ROOT/engine-overlay/src/main/java/net/kdt/pojavlaunch/PhoenixDashboardActivity.kt" "$APP/src/main/java/net/kdt/pojavlaunch/PhoenixDashboardActivity.kt"
echo "Phoenix branding and Compose dashboard integration applied to the Amethyst engine checkout."
