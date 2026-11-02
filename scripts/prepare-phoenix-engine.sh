#!/usr/bin/env bash
set -euo pipefail
PHOENIX_ROOT="${1:?Phoenix repository path required}"
ENGINE_ROOT="${2:?Amethyst checkout path required}"
APP="$ENGINE_ROOT/app_pojavlauncher"
test -f "$APP/build.gradle"
# API 34+ exposes CriticalNative in the Android SDK; remove the legacy
# source stub, which conflicts with the JDK 21 java.base module during javac.
rm -f "$APP/src/main/java/dalvik/annotation/optimization/CriticalNative.java"
# Enable AGP 9 built-in Kotlin for the Phoenix Compose dashboard.
sed -i "s/^android\\.builtInKotlin=false$/android.builtInKotlin=true/" "$ENGINE_ROOT/gradle.properties"
python3 - "$APP/build.gradle" "$APP/src/main/res/values/strings.xml" "$APP/src/main/res/values/colors.xml" "$APP/src/main/assets/about_en.txt" "$APP/src/main/AndroidManifest.xml" "$APP/src/main/java/net/kdt/pojavlaunch/TestStorageActivity.java" <<'PY'
from pathlib import Path
import sys
gradle, strings, colors, about, manifest, test_storage = map(Path, sys.argv[1:])
g = gradle.read_text()
g = g.replace("compileSdk = 37", "compileSdk = 37\n    compileSdkMinor = 1")
g = g.replace("sourceCompatibility JavaVersion.VERSION_1_8", "sourceCompatibility JavaVersion.VERSION_11")
g = g.replace("targetCompatibility JavaVersion.VERSION_1_8", "targetCompatibility JavaVersion.VERSION_11")
replacements = {
    'applicationId "org.angelauramc.amethyst"': 'applicationId "com.phoenix.client"',
    'minSdkVersion 21': 'minSdkVersion 26',
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
s = s.replace('<string name="lazy_service_default_title">Amethyst Foreground Service</string>',
              '<string name="lazy_service_default_title">Phoenix Foreground Service</string>')
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
# Keep Kotlin Compose bytecode and Java sources on the same JVM target.
g = g.replace(
    "\\nafterEvaluate {",
    """
// Compose needs Kotlin 11 bytecode; keep legacy Java engine sources at Java 8.
// --release 8 also avoids JDK module-package collisions in the Android compatibility stubs.
tasks.withType(org.jetbrains.kotlin.gradle.tasks.KotlinCompile).configureEach {
    compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
}
tasks.withType(JavaCompile).configureEach {
    options.release = 8
}

afterEvaluate {
"""
)
g = g.replace("dependencies {\n", """dependencies {
    implementation platform('androidx.compose:compose-bom:2026.09.00')
    implementation 'androidx.activity:activity-compose:1.12.0'
    implementation 'androidx.compose.ui:ui'
    implementation 'androidx.compose.ui:ui-tooling-preview'
    implementation 'androidx.compose.material3:material3'
""")
gradle.write_text(g)

m = manifest.read_text()
# Android blocks cleartext HTTP by default. Phoenix sync uses bearer-token HTTP
# only on a trusted private LAN; the Paper plugin warns against public exposure.
m = m.replace(
    '<application\n',
    '<application\n        android:usesCleartextTraffic="true"\n'
)
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
mkdir -p "$APP/src/main/kotlin/net/kdt/pojavlaunch"
cp "$PHOENIX_ROOT/engine-overlay/src/main/java/net/kdt/pojavlaunch/PhoenixDashboardActivity.kt" "$APP/src/main/kotlin/net/kdt/pojavlaunch/PhoenixDashboardActivity.kt"
cp "$PHOENIX_ROOT/engine-overlay/src/main/java/net/kdt/pojavlaunch/PhoenixCosmeticsScreen.kt" "$APP/src/main/kotlin/net/kdt/pojavlaunch/PhoenixCosmeticsScreen.kt"
cp "$PHOENIX_ROOT/engine-overlay/src/main/java/net/kdt/pojavlaunch/PhoenixCharacterPreview.kt" "$APP/src/main/kotlin/net/kdt/pojavlaunch/PhoenixCharacterPreview.kt"
cp "$PHOENIX_ROOT/engine-overlay/src/main/java/net/kdt/pojavlaunch/PhoenixServerApi.kt" "$APP/src/main/kotlin/net/kdt/pojavlaunch/PhoenixServerApi.kt"
cp "$PHOENIX_ROOT/engine-overlay/src/main/java/net/kdt/pojavlaunch/PhoenixSocialScreen.kt" "$APP/src/main/kotlin/net/kdt/pojavlaunch/PhoenixSocialScreen.kt"

# Apply the Phoenix Inferno palette to the entire embedded launcher UI, not only
# the Compose dashboard. Keep every upstream view/ID so game, auth and installer
# behavior continues to be provided by the original engine.
python3 - "$APP/src/main/res/values/colors.xml" "$APP/src/main/res/values/styles.xml" "$APP/src/main/res/layout/fragment_launcher.xml" <<'PY'
from pathlib import Path
import sys
colors, styles, launcher = map(Path, sys.argv[1:])
# The upstream resource names are used by account, version, profile, installer,
# settings and mod-search screens. Recoloring these shared tokens themes them all.
c = colors.read_text()
for old, new in {
    '#9649b8': '#FF702E',
    '#181818': '#0B0910',
    '#464646': '#211923',
    '#242424': '#09070D',
    '#232323': '#151019',
    '#B2B2B2': '#B8AEBB',
}.items():
    c = c.replace(old, new)
colors.write_text(c)

s = styles.read_text()
needle = '        <item name="windowActionBar">false</item>'
if needle in s and 'name="colorAccent"' not in s:
    s = s.replace(needle, needle + '''
        <item name="colorAccent">@color/minebutton_color</item>
        <item name="android:colorAccent">@color/minebutton_color</item>
        <item name="colorControlActivated">@color/minebutton_color</item>
        <item name="android:windowLightStatusBar">false</item>
        <item name="android:windowBackground">@color/background_app</item>''')
styles.write_text(s)

# Add Phoenix identity inside the real engine launcher while retaining all
# original controls and their IDs, listeners, profile handling and game launch.
x = launcher.read_text()
if 'phoenix_engine_header' not in x:
    header = '''\t<LinearLayout
\t\tandroid:id="@+id/phoenix_engine_header"
\t\tandroid:layout_width="match_parent"
\t\tandroid:layout_height="wrap_content"
\t\tandroid:orientation="vertical"
\t\tandroid:paddingStart="18dp"
\t\tandroid:paddingTop="14dp"
\t\tandroid:paddingEnd="18dp"
\t\tandroid:paddingBottom="10dp"
\t\tapp:layout_constraintTop_toTopOf="parent">
\t\t<TextView
\t\t\tandroid:layout_width="wrap_content"
\t\t\tandroid:layout_height="wrap_content"
\t\t\tandroid:text="PHOENIX  /  JAVA EDITION"
\t\t\tandroid:textColor="@color/minebutton_color"
\t\t\tandroid:textStyle="bold"
\t\t\tandroid:letterSpacing="0.16"
\t\t\tandroid:textSize="11sp" />
\t\t<TextView
\t\t\tandroid:layout_width="wrap_content"
\t\t\tandroid:layout_height="wrap_content"
\t\t\tandroid:text="GAME CONTROL CENTER"
\t\t\tandroid:textColor="@color/primary_text"
\t\t\tandroid:textStyle="bold"
\t\t\tandroid:textSize="20sp" />
\t</LinearLayout>
'''
    x = x.replace('\t<ScrollView', header + '\t<ScrollView', 1)
    x = x.replace('app:layout_constraintTop_toTopOf="parent"\n\t\tapp:layout_constraintBottom_toTopOf="@id/mc_version_spinner"',
                  'app:layout_constraintTop_toBottomOf="@id/phoenix_engine_header"\n\t\tapp:layout_constraintBottom_toTopOf="@id/mc_version_spinner"', 1)
launcher.write_text(x)
PY


# Give the embedded engine controls a Phoenix-specific visual language and
# route its community button to the owner's official Phoenix Discord invite.
python3 - "$APP/src/main/res/values/styles.xml" "$APP/src/main/res/values/strings.xml" "$APP/src/main/java/com/kdt/mcgui/MineButton.java" "$APP/src/main/res/layout/activity_pojav_launcher.xml" <<'PY'
from pathlib import Path
import sys
styles, strings, mine_button, activity_layout = map(Path, sys.argv[1:])
s = styles.read_text()
s = s.replace(
    '<item name="windowActionBar">false</item>',
    '''<item name="windowActionBar">false</item>
        <item name="colorAccent">@color/minebutton_color</item>
        <item name="android:colorAccent">@color/minebutton_color</item>
        <item name="colorControlActivated">@color/minebutton_color</item>
        <item name="colorControlHighlight">#66FF702E</item>
        <item name="android:windowLightStatusBar">false</item>
        <item name="android:windowBackground">@color/background_app</item>'''
) if 'name="colorAccent"' not in s else s
s = s.replace(
    '<style name="LauncherMenuButton" parent="Widget.AppCompat.Button">\n        <item name="android:background">?android:attr/selectableItemBackground</item>',
    '''<style name="LauncherMenuButton" parent="Widget.AppCompat.Button">
        <item name="android:background">@drawable/phoenix_menu_button</item>
        <item name="android:textColor">@color/primary_text</item>
        <item name="android:textAllCaps">false</item>
        <item name="android:fontFamily">sans</item>'''
)
styles.write_text(s)

# A subtle dark tile with an ember border and a pressed-state glow.
normal = activity_layout.parent.parent / "drawable/phoenix_menu_button.xml"
normal.write_text('''<?xml version="1.0" encoding="utf-8"?>
<selector xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:state_pressed="true">
        <shape android:shape="rectangle">
            <corners android:radius="14dp" />
            <solid android:color="#332A1C1A" />
            <stroke android:width="1.5dp" android:color="#FFFF702E" />
        </shape>
    </item>
    <item>
        <shape android:shape="rectangle">
            <corners android:radius="14dp" />
            <solid android:color="#FF171019" />
            <stroke android:width="1dp" android:color="#FF38232A" />
        </shape>
    </item>
</selector>
''')

st = strings.read_text()
st = st.replace('<string name="mcl_tab_wiki">Wiki</string>', '<string name="mcl_tab_wiki">Help &amp; Guides</string>')
st = st.replace('<string name="mcl_button_discord">Discord</string>', '<string name="mcl_button_discord">Phoenix Community</string>')
st = st.replace('<string name="main_play">Play</string>', '<string name="main_play">Launch Game</string>')
import re
st = re.sub(r'(<string name="discord_invite"[^>]*>).*?(</string>)',
            r'\g<1>https://discord.gg/MnxS3g8gxA\g<2>', st, count=1)
strings.write_text(st)

# Primary actions use Phoenix ember orange; the engine's click listeners and
# account/game-launch logic remain unchanged.
mb = mine_button.read_text()
if 'setBackgroundTintList' not in mb:
    mb = mb.replace('import android.content.*;', 'import android.content.*;\nimport android.content.res.ColorStateList;')
    mb = mb.replace(
        'setBackground(ResourcesCompat.getDrawable(getResources(), R.drawable.mine_button_background, null));',
        '''setBackground(ResourcesCompat.getDrawable(getResources(), R.drawable.mine_button_background, null));
        setBackgroundTintList(ColorStateList.valueOf(ResourcesCompat.getColor(
                getResources(), R.color.minebutton_color, getContext().getTheme())));
        setTextColor(android.graphics.Color.BLACK);'''
    )
mine_button.write_text(mb)

a = activity_layout.read_text().replace(
    'android:background="@android:color/transparent"',
    'android:background="@color/background_app"'
)
activity_layout.write_text(a)
PY

echo "Phoenix branding, Inferno engine theme, custom engine controls and Compose dashboard integration applied."

# Phoenix offline/local accounts: upstream accidentally hides Local Login until an
# authenticated account already exists, making the first local account impossible.
python3 - "$APP/src/main/java/net/kdt/pojavlaunch/fragments/LocalLoginFragment.java" <<'PY'
from pathlib import Path
import sys
p = Path(sys.argv[1])
s = p.read_text()
guard = '''        if (!hasOnlineProfile()){
            Tools.swapFragment(requireActivity(), MainMenuFragment.class, MainMenuFragment.TAG, null);
        }
'''
if guard in s:
    s = s.replace(guard, '''        // Phoenix permits device-local offline profiles without a prior Microsoft login.
        // The engine still labels them as local accounts and online-mode servers
        // continue to enforce their own authenticated account requirements.
''')
elif 'if (!hasOnlineProfile())' in s:
    raise SystemExit("LocalLoginFragment guard changed; refusing an unsafe partial patch.")
p.write_text(s)
PY

# Phoenix offline accounts and independent game instances.
# A local/offline identity is intentionally device-only and never grants
# Microsoft authentication or online-mode entitlement.
python3 - "$APP/src/main/java/net/kdt/pojavlaunch/fragments/SelectAuthFragment.java" "$APP/src/main/java/net/kdt/pojavlaunch/fragments/LocalLoginFragment.java" "$APP/src/main/java/net/kdt/pojavlaunch/fragments/ProfileTypeSelectFragment.java" <<'PY'
from pathlib import Path
import sys
select_auth, local_login, profile_types = map(Path, sys.argv[1:])

# Local Login must be reachable even when no Microsoft account is present.
# The upstream dialog/guard prevented first-time offline users from creating
# their first local account.
s = select_auth.read_text()
old = 'mLocalButton.setOnClickListener(v -> hasNoOnlineProfileDialog(requireActivity(), () -> Tools.swapFragment(requireActivity(), LocalLoginFragment.class, LocalLoginFragment.TAG, null)));'
new = 'mLocalButton.setOnClickListener(v -> Tools.swapFragment(requireActivity(), LocalLoginFragment.class, LocalLoginFragment.TAG, null));'
if old not in s:
    raise SystemExit("Phoenix patch failed: SelectAuthFragment local-login action changed upstream.")
select_auth.write_text(s.replace(old, new, 1))

import re
s = local_login.read_text()
s, count = re.subn(
    r'(?m)^\\s*// This is overkill but meh\\n\\s*if \\(!hasOnlineProfile\\(\\)\\)\\s*\\{\\n\\s*Tools\\.swapFragment\\(requireActivity\\(\\), MainMenuFragment\\.class, MainMenuFragment\\.TAG, null\\);\\n\\s*\\}\\n',
    '',
    s,
    count=1
)
if count != 1:
    raise SystemExit("Phoenix patch failed: LocalLoginFragment account guard pattern not found.")
local_login.write_text(s)

# Allow creating/installing vanilla, Fabric, Forge, NeoForge, Quilt and
# modpack instances without requiring Microsoft sign-in. Installers still
# need network access; actual online Minecraft play still requires entitlement.
s = profile_types.read_text()
old = '''    private void tryInstall(Class<? extends Fragment> fragmentClass, String tag){
        if(!hasOnlineProfile()){
            hasNoOnlineProfileDialog(requireActivity());
        } else {
            Tools.swapFragment(requireActivity(), fragmentClass, tag, null);
        }
    }'''
new = '''    private void tryInstall(Class<? extends Fragment> fragmentClass, String tag){
        Tools.swapFragment(requireActivity(), fragmentClass, tag, null);
    }'''
if old not in s:
    raise SystemExit("Phoenix patch failed: ProfileTypeSelectFragment installer gate changed upstream.")
profile_types.write_text(s.replace(old, new, 1))
PY
