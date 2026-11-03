package net.kdt.pojavlaunch

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.os.Bundle
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.Locale
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.cos
import kotlin.math.sin

/**
 * Phoenix Visual Studio: persisted appearance, per-module visual controls,
 * animation preferences and profile-scoped keybind presets.
 */
class PhoenixVisualStudioActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PhoenixVisualStudioScreen { finish() } }
    }
}

private data class PhoenixVisualModule(val id: String, val title: String, val detail: String)
private data class PhoenixBind(val id: String, val title: String, val defaultKey: String)

@Composable
private fun PhoenixVisualStudioScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("phoenix_client", Context.MODE_PRIVATE) }
    val accentOptions = listOf("INFERNO", "FROST", "AMETHYST")
    var appearance by remember { mutableStateOf(prefs.getString("appearance_mode", "SYSTEM") ?: "SYSTEM") }
    var accent by remember { mutableStateOf(prefs.getString("theme_choice", "EMBER") ?: "EMBER") }
    var activeProfile by remember { mutableStateOf(prefs.getString("active_profile_id", "main") ?: "main") }
    var reducedMotion by remember { mutableStateOf(prefs.getBoolean("visual_reduced_motion", false)) }
    var animations by remember { mutableStateOf(prefs.getBoolean("visual_animations", true)) }
    var focusEffects by remember { mutableStateOf(prefs.getBoolean("visual_focus_effects", true)) }
    var selectedPanel by remember { mutableStateOf("CREST") }
    var notice by remember { mutableStateOf("Visual settings are saved locally on this device.") }
    val modules = remember { listOf(
        PhoenixVisualModule("launcher", "Launcher interface", "Home, navigation, account and version screens"),
        PhoenixVisualModule("profiles", "Profile cards", "Profile tiles, selection and management panels"),
        PhoenixVisualModule("mods", "Mods & content", "Mod browser, installed mods and pack cards"),
        PhoenixVisualModule("social", "Friends & community", "Friends, messages and community panels"),
        PhoenixVisualModule("performance", "Performance centre", "FPS, memory and device tuning panels"),
        PhoenixVisualModule("overlays", "In-game overlays", "HUD widgets, status panels and quick controls"),
        PhoenixVisualModule("dialogs", "Dialogs & sheets", "Confirmation dialogs, editors and bottom sheets")
    ) }
    val binds = remember { listOf(
        PhoenixBind("forward", "Move forward", "W"),
        PhoenixBind("back", "Move backward", "S"),
        PhoenixBind("left", "Move left", "A"),
        PhoenixBind("right", "Move right", "D"),
        PhoenixBind("jump", "Jump", "SPACE"),
        PhoenixBind("sneak", "Sneak", "LEFT_SHIFT"),
        PhoenixBind("sprint", "Sprint", "LEFT_CONTROL"),
        PhoenixBind("inventory", "Inventory", "E"),
        PhoenixBind("chat", "Open chat", "T"),
        PhoenixBind("screenshot", "Take screenshot", "F2"),
        PhoenixBind("perspective", "Change perspective", "F5"),
        PhoenixBind("hud", "Toggle HUD", "F1")
    ) }
    val dark = when (appearance) {
        "DARK" -> true
        "LIGHT" -> false
        else -> androidx.compose.foundation.isSystemInDarkTheme()
    }
    val bg = if (dark) Color(0xFF0C0910) else Color(0xFFF3F1F5)
    val panel = if (dark) Color(0xFF19131D) else Color.White
    val ink = if (dark) Color(0xFFF8F3FA) else Color(0xFF211A25)
    val muted = if (dark) Color(0xFFB8AABB) else Color(0xFF6D6472)
    val targetAccent = when (accent) {
        "FROST" -> Color(0xFF62C9FF)
        "AMETHYST" -> Color(0xFFB58CFF)
        else -> Color(0xFFFF702E)
    }
    val orange by animateColorAsState(targetValue = targetAccent, label = "phoenix-studio-accent")
    MaterialTheme(colorScheme = if (dark) darkColorScheme(primary = orange, background = bg, surface = panel) else lightColorScheme(primary = orange, background = bg, surface = panel)) {
        Scaffold(containerColor = bg, topBar = {
            Row(Modifier.fillMaxWidth().background(panel).padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("‹", color = orange, fontSize = 30.sp, modifier = Modifier.clickable { onBack() }.padding(end = 14.dp))
                Column(Modifier.weight(1f)) {
                    Text("PHOENIX VISUAL STUDIO", color = ink, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
                    AnimatedContent(targetState = selectedPanel, label = "phoenix-panel-title") { panelName ->
                        Text("${panelName.lowercase(Locale.ROOT).replaceFirstChar { it.uppercase() }} · Appearance & controls", color = muted, fontSize = 11.sp)
                    }
                }
                Text("v1", color = orange, fontWeight = FontWeight.Bold)
            }
        }) { padding ->
            LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        listOf("CREST", "THEME", "MODULES", "MOTION", "KEYBINDS").forEach { tab ->
                            val active = selectedPanel == tab
                            Surface(
                                color = if (active) orange.copy(alpha = .18f) else panel,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f).clickable { selectedPanel = tab }
                            ) { Text(tab, color = if (active) orange else muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 11.dp).wrapContentWidth(Alignment.CenterHorizontally)) }
                        }
                    }
                }
                when (selectedPanel) {
                    "CREST" -> {
                        item {
                            StudioCard(panel, {
                                Text("RENDERED PHOENIX CREST", color = orange, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text("A real OpenGL ES 2.0 mesh, rendered with depth and perspective. Drag across the crest to rotate it.", color = muted, fontSize = 12.sp)
                                AndroidView(
                                    factory = { ctx -> PhoenixCrestSurface(ctx).apply { setRenderer(PhoenixCrestRenderer()); renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY } },
                                    modifier = Modifier.fillMaxWidth().height(250.dp)
                                )
                                Text("Live 3D · GPU rendered · touch rotation", color = muted, fontSize = 10.sp)
                            })
                        }
                    }
                    "THEME" -> {
                        item {
                            StudioCard(panel, {
                                Text("GLOBAL APPEARANCE MODE", color = orange, fontWeight = FontWeight.Bold)
                                Text("Choose how Phoenix screens resolve light and dark surfaces.", color = muted, fontSize = 12.sp)
                                StudioChoice(listOf("SYSTEM", "DARK", "LIGHT"), appearance, ink) {
                                    appearance = it
                                    prefs.edit().putString("appearance_mode", it).apply()
                                    notice = "Appearance saved. Phoenix screens will use this mode when reopened."
                                }
                                Spacer(Modifier.height(8.dp))
                                Text("VISUAL IDENTITY", color = orange, fontWeight = FontWeight.Bold)
                                StudioChoice(accentOptions, when (accent) { "FROST" -> "FROST"; "VIOLET" -> "AMETHYST"; else -> "INFERNO" }, ink) {
                                    accent = when (it) { "FROST" -> "FROST"; "AMETHYST" -> "VIOLET"; else -> "EMBER" }
                                    prefs.edit().putString("theme_choice", accent).apply()
                                }
                                Text("Inferno · ember orange and obsidian   |   Frost · ice blue   |   Amethyst · violet", color = muted, fontSize = 11.sp)
                            })
                        }
                    }
                    "MODULES" -> {
                        items(modules, key = { it.id }) { module ->
                            StudioCard(panel, {
                                var enabled by remember(module.id) { mutableStateOf(prefs.getBoolean("visual_module_${module.id}", true)) }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(module.title, color = ink, fontWeight = FontWeight.SemiBold)
                                        Text(module.detail, color = muted, fontSize = 11.sp)
                                    }
                                    Switch(checked = enabled, onCheckedChange = {
                                        enabled = it
                                        prefs.edit().putBoolean("visual_module_${module.id}", it).apply()
                                    })
                                }
                                Text("Module-specific visual preference", color = orange, fontSize = 10.sp)
                            })
                        }
                    }
                    "MOTION" -> {
                        item {
                            StudioCard(panel, {
                                Text("MOTION & ACCESSIBILITY", color = orange, fontWeight = FontWeight.Bold)
                                StudioToggle("Enable interface animations", animations, ink) {
                                    animations = it; prefs.edit().putBoolean("visual_animations", it).apply()
                                }
                                StudioToggle("Reduced motion", reducedMotion, ink) {
                                    reducedMotion = it; prefs.edit().putBoolean("visual_reduced_motion", it).apply()
                                }
                                StudioToggle("Keyboard/controller focus highlights", focusEffects, ink) {
                                    focusEffects = it; prefs.edit().putBoolean("visual_focus_effects", it).apply()
                                }
                                Text("Reduced motion takes priority over decorative transitions. Focus highlighting is stored as a shared preference for Phoenix UI components.", color = muted, fontSize = 11.sp)
                            })
                        }
                    }
                    "KEYBINDS" -> {
                        item {
                            StudioCard(panel, {
                                Text("KEYBIND PROFILES", color = orange, fontWeight = FontWeight.Bold)
                                OutlinedTextField(activeProfile, { activeProfile = it.take(40); prefs.edit().putString("active_profile_id", activeProfile).apply() }, label = { Text("Active keybind profile ID") }, singleLine = true)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = {
                                        activeProfile = "profile-" + java.util.UUID.randomUUID().toString().take(8)
                                        prefs.edit().putString("active_profile_id", activeProfile).apply()
                                        notice = "Created a new independent keybind profile."
                                    }) { Text("NEW PROFILE") }
                                    OutlinedButton(onClick = {
                                        activeProfile = "main"
                                        prefs.edit().putString("active_profile_id", activeProfile).apply()
                                        notice = "Switched to the main keybind profile."
                                    }) { Text("MAIN PROFILE") }
                                }
                                Text("Bindings are saved per Phoenix profile. Tap a binding to edit its key name.", color = muted, fontSize = 11.sp)
                            })
                        }
                        items(binds, key = { it.id }) { bind ->
                            var key by remember(activeProfile, bind.id) { mutableStateOf(prefs.getString("bind_${activeProfile}_${bind.id}", bind.defaultKey) ?: bind.defaultKey) }
                            StudioCard(panel, {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(bind.title, color = ink, fontWeight = FontWeight.SemiBold)
                                        Text(bind.id.uppercase(Locale.ROOT), color = muted, fontSize = 9.sp)
                                    }
                                    OutlinedTextField(key, { value ->
                                        key = value.uppercase(Locale.ROOT).filter { it.isLetterOrDigit() || it == '_' }.take(18)
                                        prefs.edit().putString("bind_${activeProfile}_${bind.id}", key).apply()
                                    }, modifier = Modifier.width(132.dp), singleLine = true, label = { Text("Key") })
                                }
                            })
                        }
                        item {
                            StudioCard(panel, {
                                Text("PROFILE PRESETS", color = orange, fontWeight = FontWeight.Bold)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = {
                                        val editor = prefs.edit()
                                        binds.forEach { editor.remove("bind_${activeProfile}_${it.id}") }
                                        editor.apply()
                                        notice = "Default keybinds restored for this profile."
                                    }) { Text("RESET PROFILE") }
                                    Button(onClick = {
                                        val data = binds.joinToString(";") { "${it.id}=" + (prefs.getString("bind_${activeProfile}_${it.id}", it.defaultKey) ?: it.defaultKey) }
                                        prefs.edit().putString("bind_profile_snapshot_${activeProfile}", data).apply()
                                        notice = "Keybind profile snapshot saved."
                                    }) { Text("SAVE PRESET") }
                                }
                            })
                        }
                    }
                }
                item {
                    Text(notice, color = muted, fontSize = 11.sp)
                    Text("Changes are stored locally. Module switches and keybind presets are Phoenix UI preferences; they do not silently rewrite Minecraft's options.txt or external game files.", color = muted, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun StudioCard(panel: Color, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().background(panel, RoundedCornerShape(17.dp)).padding(15.dp), verticalArrangement = Arrangement.spacedBy(9.dp), content = content)
}

@Composable
private fun StudioChoice(options: List<String>, selected: String, ink: Color, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { option ->
            FilterChip(selected = selected == option, onClick = { onSelect(option) }, label = { Text(option, fontSize = 10.sp) })
        }
    }
}

@Composable
private fun StudioToggle(label: String, checked: Boolean, ink: Color, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = ink, modifier = Modifier.weight(1f), fontSize = 13.sp)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

private class PhoenixCrestSurface(context: Context) : GLSurfaceView(context) {
    private var lastX = 0f
    private var lastY = 0f
    private var rendererRef: PhoenixCrestRenderer? = null
    init {
        setEGLContextClientVersion(2)
        preserveEGLContextOnPause = true
    }
    override fun setRenderer(renderer: Renderer) {
        super.setRenderer(renderer)
        rendererRef = renderer as? PhoenixCrestRenderer
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> { lastX = event.x; lastY = event.y; return true }
            MotionEvent.ACTION_MOVE -> {
                rendererRef?.rotateBy((event.x - lastX) * .55f, (event.y - lastY) * .45f)
                lastX = event.x; lastY = event.y
                requestRender()
                return true
            }
        }
        return true
    }
}

private class PhoenixCrestRenderer : GLSurfaceView.Renderer {
    @Volatile private var yaw = -12f
    @Volatile private var pitch = 4f
    private var program = 0
    private var vertexBuffer: FloatBuffer? = null
    private var vertexCount = 0
    private val projection = FloatArray(16)
    private val view = FloatArray(16)
    private val model = FloatArray(16)
    private val mvp = FloatArray(16)
    private var position = 0
    private var color = 0
    private var matrix = 0
    private val vertexShader = """
        attribute vec3 aPosition;
        attribute vec4 aColor;
        uniform mat4 uMvp;
        varying vec4 vColor;
        void main(){ gl_Position=uMvp*vec4(aPosition,1.0); vColor=aColor; }
    """
    private val fragmentShader = """
        precision mediump float;
        varying vec4 vColor;
        void main(){ gl_FragColor=vColor; }
    """
    fun rotateBy(dx: Float, dy: Float) { yaw += dx; pitch = (pitch + dy).coerceIn(-55f, 55f) }
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(.035f, .025f, .05f, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        program = linkProgram(vertexShader, fragmentShader)
        position = GLES20.glGetAttribLocation(program, "aPosition")
        color = GLES20.glGetAttribLocation(program, "aColor")
        matrix = GLES20.glGetUniformLocation(program, "uMvp")
        val data = CrestMesh.build()
        vertexCount = data.size / 7
        vertexBuffer = ByteBuffer.allocateDirect(data.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(data); position(0) }
    }
    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        Matrix.perspectiveM(projection, 0, 34f, width.toFloat() / height.coerceAtLeast(1), .1f, 20f)
        Matrix.setLookAtM(view, 0, 0f, .05f, 5.8f, 0f, 0f, 0f, 0f, 1f, 0f)
    }
    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        GLES20.glUseProgram(program)
        Matrix.setIdentityM(model, 0)
        Matrix.rotateM(model, 0, yaw, 0f, 1f, 0f)
        Matrix.rotateM(model, 0, pitch, 1f, 0f, 0f)
        Matrix.rotateM(model, 0, -5f, 0f, 0f, 1f)
        Matrix.multiplyMM(mvp, 0, view, 0, model, 0)
        Matrix.multiplyMM(mvp, 0, projection, 0, mvp, 0)
        GLES20.glUniformMatrix4fv(matrix, 1, false, mvp, 0)
        val buffer = vertexBuffer ?: return
        buffer.position(0)
        GLES20.glEnableVertexAttribArray(position)
        GLES20.glEnableVertexAttribArray(color)
        GLES20.glVertexAttribPointer(position, 3, GLES20.GL_FLOAT, false, 28, buffer)
        buffer.position(3)
        GLES20.glVertexAttribPointer(color, 4, GLES20.GL_FLOAT, false, 28, buffer)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertexCount)
        GLES20.glDisableVertexAttribArray(position)
        GLES20.glDisableVertexAttribArray(color)
        // Cap the continuously rendered preview to roughly 30 fps.
        try { Thread.sleep(33) } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
    }
    private fun linkProgram(vs: String, fs: String): Int {
        fun compile(type: Int, source: String): Int {
            val shader = GLES20.glCreateShader(type)
            GLES20.glShaderSource(shader, source)
            GLES20.glCompileShader(shader)
            val status = IntArray(1)
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
            check(status[0] == GLES20.GL_TRUE) { GLES20.glGetShaderInfoLog(shader) }
            return shader
        }
        val v = compile(GLES20.GL_VERTEX_SHADER, vs)
        val f = compile(GLES20.GL_FRAGMENT_SHADER, fs)
        return GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, v); GLES20.glAttachShader(it, f); GLES20.glLinkProgram(it)
            val status = IntArray(1); GLES20.glGetProgramiv(it, GLES20.GL_LINK_STATUS, status, 0)
            check(status[0] == GLES20.GL_TRUE) { GLES20.glGetProgramInfoLog(it) }
            GLES20.glDeleteShader(v); GLES20.glDeleteShader(f)
        }
    }
}

private object CrestMesh {
    private val vertices = ArrayList<Float>()
    fun build(): FloatArray {
        vertices.clear()
        // Layered wing feathers, central breast and raised flame crown.
        ellipsoid(0f, -.12f, 0f, .22f, .58f, .20f, 1f, .36f, .10f)
        ellipsoid(0f, .18f, .12f, .15f, .22f, .15f, 1f, .72f, .24f)
        for (side in listOf(-1f, 1f)) {
            ribbon(0f, .12f, -.02f, side * .45f, .63f, -.08f, side * 1.02f, .34f, -.12f, 1f, .34f, .09f)
            ribbon(0f, -.02f, -.02f, side * .52f, .24f, -.10f, side * .95f, -.08f, -.13f, .94f, .19f, .055f)
            ribbon(0f, -.18f, -.02f, side * .43f, -.20f, -.08f, side * .77f, -.42f, -.13f, .78f, .12f, .045f)
            ribbon(side * .08f, .38f, .02f, side * .34f, .82f, .02f, side * .42f, .58f, -.02f, 1f, .66f, .18f)
        }
        ribbon(0f, .28f, .02f, -.12f, .72f, .02f, 0f, 1.08f, -.02f, 1f, .82f, .25f)
        ribbon(0f, .28f, .02f, .12f, .72f, .02f, 0f, 1.08f, -.02f, 1f, .52f, .12f)
        ribbon(0f, -.48f, 0f, -.12f, -.82f, -.02f, 0f, -1.12f, -.04f, .92f, .24f, .07f)
        ribbon(0f, -.48f, 0f, .12f, -.82f, -.02f, 0f, -1.12f, -.04f, 1f, .52f, .12f)
        return vertices.toFloatArray()
    }
    private fun triangle(a: FloatArray, b: FloatArray, c: FloatArray, r: Float, g: Float, bl: Float) {
        for (p in listOf(a, b, c)) { vertices.add(p[0]); vertices.add(p[1]); vertices.add(p[2]); vertices.add(r); vertices.add(g); vertices.add(bl); vertices.add(1f) }
    }
    private fun ribbon(x0: Float, y0: Float, z0: Float, x1: Float, y1: Float, z1: Float, x2: Float, y2: Float, z2: Float, r: Float, g: Float, b: Float) {
        val a = floatArrayOf(x0,y0,z0); val c = floatArrayOf(x2,y2,z2)
        val mid = floatArrayOf((x0+x1+x2)/3f, (y0+y1+y2)/3f, (z0+z1+z2)/3f + .09f)
        val tip = floatArrayOf(x1,y1,z1)
        triangle(a, tip, mid, r,g,b); triangle(tip,c,mid,r*.88f,g*.72f,b*.65f); triangle(c,a,mid,r*.72f,g*.42f,b*.22f)
    }
    private fun ellipsoid(cx: Float, cy: Float, cz: Float, rx: Float, ry: Float, rz: Float, r: Float, g: Float, b: Float) {
        val rows = 12; val cols = 20
        for (i in 0 until rows) for (j in 0 until cols) {
            fun point(row: Int, col: Int): FloatArray {
                val phi = Math.PI * row / rows
                val theta = 2.0 * Math.PI * col / cols
                return floatArrayOf(cx + rx * sin(phi).toFloat() * cos(theta).toFloat(), cy + ry * cos(phi).toFloat(), cz + rz * sin(phi).toFloat() * sin(theta).toFloat())
            }
            val a=point(i,j); val b1=point(i+1,j); val c1=point(i+1,j+1); val d=point(i,j+1)
            triangle(a,b1,c1,r,g,b); triangle(a,c1,d,r*.82f,g*.72f,b*.55f)
        }
    }
}
