package net.kdt.pojavlaunch

import android.content.Context
import android.graphics.BitmapFactory
import android.opengl.GLUtils
import java.io.File
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** Real, lit OpenGL ES 2.0 mannequin preview. Drag horizontally to orbit the model. */
@Composable
fun PhoenixCharacterPreview(cosmeticId: String = "phoenix", skinPath: String? = null, capePath: String? = null, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { context -> PhoenixCharacterGLView(context).apply { setCosmetic(cosmeticId); setAppearance(skinPath, capePath) } },
        update = { it.setCosmetic(cosmeticId); it.setAppearance(skinPath, capePath) }
    )
}

private class PhoenixCharacterGLView(context: Context) : GLSurfaceView(context) {
    private val previewRenderer = PhoenixCharacterRenderer()
    private var lastX = 0f

    init {
        setEGLContextClientVersion(2)
        setRenderer(previewRenderer)
        renderMode = RENDERMODE_CONTINUOUSLY
        setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { lastX = event.x; true }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.x - lastX
                    lastX = event.x
                    previewRenderer.rotate(dx * 0.55f)
                    true
                }
                else -> true
            }
        }
    }

    fun setCosmetic(id: String) { previewRenderer.setCosmetic(id) }
    fun setAppearance(skin: String?, cape: String?) { previewRenderer.setAppearance(skin, cape) }
}

private class PhoenixCharacterRenderer : GLSurfaceView.Renderer {
    private val projection = FloatArray(16)
    private val view = FloatArray(16)
    private val model = FloatArray(16)
    private val mvp = FloatArray(16)
    private val part = FloatArray(16)
    private val vp = FloatArray(16)
    private var program = 0
    @Volatile private var angle = -22f
    @Volatile private var cosmetic = "ember_cape"
    private var width = 1
    private var height = 1
    private lateinit var vertices: FloatBuffer
    private lateinit var indices: ShortBuffer
    private var positionHandle = 0
    private var normalHandle = 0
    private var matrixHandle = 0
    private var colorHandle = 0
    private var texCoordHandle = 0
    private var samplerHandle = 0
    private var useTextureHandle = 0
    private var skinTexture = 0
    private var capeTexture = 0
    @Volatile private var requestedSkinPath: String? = null
    @Volatile private var requestedCapePath: String? = null
    private var loadedSkinPath: String? = null
    private var loadedCapePath: String? = null
    private lateinit var texCoords: FloatBuffer

    fun rotate(delta: Float) { angle = (angle + delta) % 360f }
    fun setCosmetic(id: String) { cosmetic = id }
    fun setAppearance(skin: String?, cape: String?) { requestedSkinPath = skin; requestedCapePath = cape }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.035f, 0.025f, 0.055f, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDisable(GLES20.GL_CULL_FACE)
        val vertexShader = compile(GLES20.GL_VERTEX_SHADER, """
            attribute vec3 aPosition;
            attribute vec3 aNormal;
            uniform mat4 uMvp;
            attribute vec2 aTexCoord;
            varying vec2 vTexCoord;
            varying float vLight;
            void main() {
                vec3 n = normalize(aNormal);
                vec3 lightDir = normalize(vec3(-0.45, 0.82, 0.58));
                vLight = 0.34 + 0.66 * max(dot(n, lightDir), 0.0);
                vTexCoord = aTexCoord;
                gl_Position = uMvp * vec4(aPosition, 1.0);
            }
        """.trimIndent())
        val fragmentShader = compile(GLES20.GL_FRAGMENT_SHADER, """
            precision mediump float;
            uniform vec4 uColor;
            uniform sampler2D uTexture;
            uniform int uUseTexture;
            varying vec2 vTexCoord;
            varying float vLight;
            void main() {
                vec4 base = (uUseTexture == 1) ? texture2D(uTexture, vTexCoord) : uColor;
                gl_FragColor = vec4(base.rgb * vLight, base.a);
            }
        """.trimIndent())
        program = GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vertexShader)
            GLES20.glAttachShader(it, fragmentShader)
            GLES20.glLinkProgram(it)
        }
        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
        check(linkStatus[0] == GLES20.GL_TRUE) { GLES20.glGetProgramInfoLog(program) }
        positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        normalHandle = GLES20.glGetAttribLocation(program, "aNormal")
        matrixHandle = GLES20.glGetUniformLocation(program, "uMvp")
        colorHandle = GLES20.glGetUniformLocation(program, "uColor")
        texCoordHandle = GLES20.glGetAttribLocation(program, "aTexCoord")
        samplerHandle = GLES20.glGetUniformLocation(program, "uTexture")
        useTextureHandle = GLES20.glGetUniformLocation(program, "uUseTexture")

        // 24 vertices (four per face) keep hard cube edges and correct face normals.
        val faces = arrayOf(
            floatArrayOf(0f,0f,1f, -1f,-1f,1f, 1f,-1f,1f, 1f,1f,1f, -1f,1f,1f),
            floatArrayOf(0f,0f,-1f, 1f,-1f,-1f, -1f,-1f,-1f, -1f,1f,-1f, 1f,1f,-1f),
            floatArrayOf(-1f,0f,0f, -1f,-1f,-1f, -1f,-1f,1f, -1f,1f,1f, -1f,1f,-1f),
            floatArrayOf(1f,0f,0f, 1f,-1f,1f, 1f,-1f,-1f, 1f,1f,-1f, 1f,1f,1f),
            floatArrayOf(0f,1f,0f, -1f,1f,1f, 1f,1f,1f, 1f,1f,-1f, -1f,1f,-1f),
            floatArrayOf(0f,-1f,0f, -1f,-1f,-1f, 1f,-1f,-1f, 1f,-1f,1f, -1f,-1f,1f)
        )
        val data = FloatArray(24 * 6)
        var cursor = 0
        faces.forEach { face ->
            val nx = face[0]; val ny = face[1]; val nz = face[2]
            for (i in 0 until 4) {
                data[cursor++] = face[3 + i * 3] * 0.5f
                data[cursor++] = face[4 + i * 3] * 0.5f
                data[cursor++] = face[5 + i * 3] * 0.5f
                data[cursor++] = nx; data[cursor++] = ny; data[cursor++] = nz
            }
        }
        val ix = ShortArray(36)
        for (face in 0 until 6) {
            val b = face * 4
            val p = face * 6
            ix[p] = b.toShort(); ix[p + 1] = (b + 1).toShort(); ix[p + 2] = (b + 2).toShort()
            ix[p + 3] = b.toShort(); ix[p + 4] = (b + 2).toShort(); ix[p + 5] = (b + 3).toShort()
        }
        vertices = ByteBuffer.allocateDirect(data.size * 4).order(ByteOrder.nativeOrder())
            .asFloatBuffer().apply { put(data); position(0) }
        indices = ByteBuffer.allocateDirect(ix.size * 2).order(ByteOrder.nativeOrder())
            .asShortBuffer().apply { put(ix); position(0) }
        texCoords = ByteBuffer.allocateDirect(24 * 2 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
    }

    private fun compile(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        check(status[0] == GLES20.GL_TRUE) { GLES20.glGetShaderInfoLog(shader) }
        return shader
    }

    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
        width = max(1, w)
        height = max(1, h)
        GLES20.glViewport(0, 0, width, height)
        val aspect = width.toFloat() / height.toFloat()
        if (aspect >= 1f) Matrix.frustumM(projection, 0, -aspect, aspect, -1f, 1f, 2.5f, 30f)
        else Matrix.frustumM(projection, 0, -1f, 1f, -1f / aspect, 1f / aspect, 2.5f, 30f)
        Matrix.setLookAtM(view, 0, 0f, 1.35f, 7.4f, 0f, 1.12f, 0f, 0f, 1f, 0f)
    }

    override fun onDrawFrame(gl: GL10?) {
        refreshTexture(requestedSkinPath, true)
        refreshTexture(requestedCapePath, false)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        GLES20.glUseProgram(program)
        Matrix.setIdentityM(model, 0)
        Matrix.rotateM(model, 0, angle, 0f, 1f, 0f)
        val skin = floatArrayOf(0.76f, 0.57f, 0.43f, 1f)
        val shirt = floatArrayOf(0.13f, 0.12f, 0.19f, 1f)
        val pants = floatArrayOf(0.17f, 0.19f, 0.28f, 1f)
        val metal = floatArrayOf(0.42f, 0.45f, 0.54f, 1f)
        val accent = cosmeticColor(cosmetic)
        val t = System.nanoTime() / 1_000_000_000f

        // Minecraft-proportioned block player. Uploaded PNG atlases are mapped onto every body part.
        if (skinTexture != 0) {
            drawSkinBox(0f,1.77f,0f,.25f,.25f,.25f,HEAD_UV,skinTexture,64,64)
            drawSkinBox(0f,1.10f,0f,.25f,.375f,.125f,TORSO_UV,skinTexture,64,64)
            drawSkinBox(-.34f,1.10f,0f,.125f,.375f,.125f,RIGHT_ARM_UV,skinTexture,64,64)
            drawSkinBox(.34f,1.10f,0f,.125f,.375f,.125f,LEFT_ARM_UV,skinTexture,64,64)
            drawSkinBox(-.125f,.375f,0f,.125f,.375f,.125f,RIGHT_LEG_UV,skinTexture,64,64)
            drawSkinBox(.125f,.375f,0f,.125f,.375f,.125f,LEFT_LEG_UV,skinTexture,64,64)
        } else {
            draw(0f,1.77f,0f,.25f,.25f,.25f,skin)
            draw(0f,1.10f,0f,.25f,.375f,.125f,shirt)
            draw(-.34f,1.10f,0f,.125f,.375f,.125f,skin)
            draw(.34f,1.10f,0f,.125f,.375f,.125f,skin)
            draw(-.125f,.375f,0f,.125f,.375f,.125f,pants)
            draw(.125f,.375f,0f,.125f,.375f,.125f,pants)
        }
        if (capeTexture != 0) drawSkinBox(0f,1.08f,-.155f,.31f,.46f,.025f,CAPE_UV,capeTexture,64,32)
        when {
            cosmetic.endsWith("_cape") || cosmetic.contains("cape") -> drawCape(accent, t)
            cosmetic.contains("wing") || cosmetic.contains("glider") -> drawWings(accent, t, cosmetic)
            cosmetic.contains("halo") -> drawHalo(accent, t)
            cosmetic.contains("crown") -> drawCrown(accent)
            cosmetic.contains("helmet") || cosmetic.contains("samurai") -> drawHelmet(accent, cosmetic)
            cosmetic.contains("mask") || cosmetic.contains("visor") || cosmetic.contains("shades") ||
                cosmetic.contains("respirator") || cosmetic.contains("monocle") -> drawFaceGear(accent, cosmetic)
            cosmetic.contains("tail") -> drawTail(accent, t, cosmetic)
            cosmetic.contains("backpack") || cosmetic.contains("pack") || cosmetic.contains("jet") ||
                cosmetic.contains("shoulder") || cosmetic.contains("shield") || cosmetic.contains("sheath") -> drawBackGear(accent, cosmetic, t)
            cosmetic.contains("bracer") || cosmetic.contains("arm") -> drawBracers(accent)
            cosmetic.contains("aura") || cosmetic.contains("trail") || cosmetic.contains("orbs") ||
                cosmetic.contains("pulse") || cosmetic.contains("burst") || cosmetic.contains("footsteps") -> drawAura(accent, t)
            else -> drawSignatureGear(accent, cosmetic)
        }
    }

    private fun refreshTexture(path: String?, isSkin: Boolean) {
        val loaded = if (isSkin) loadedSkinPath else loadedCapePath
        if (path == loaded) return
        val old = if (isSkin) skinTexture else capeTexture
        if (old != 0) GLES20.glDeleteTextures(1, intArrayOf(old), 0)
        val texture = if (path.isNullOrBlank() || !File(path).isFile) 0 else runCatching {
            val bitmap = BitmapFactory.decodeFile(path) ?: return@runCatching 0
            val ids = IntArray(1)
            GLES20.glGenTextures(1, ids, 0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ids[0])
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_NEAREST)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_NEAREST)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE)
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D,0,bitmap,0)
            bitmap.recycle()
            ids[0]
        }.getOrDefault(0)
        if (isSkin) { skinTexture=texture; loadedSkinPath=path } else { capeTexture=texture; loadedCapePath=path }
    }

    private fun drawSkinBox(x:Float,y:Float,z:Float,sx:Float,sy:Float,sz:Float,uv:FloatArray,texture:Int,tw:Int,th:Int) {
        val coords=FloatArray(48)
        for(face in 0 until 6) {
            val p=face*4
            val l=uv[face*4]/tw; val top=uv[face*4+1]/th
            val r=uv[face*4+2]/tw; val bottom=uv[face*4+3]/th
            val vTop=1f-top; val vBottom=1f-bottom
            val values=floatArrayOf(l,vBottom,r,vBottom,r,vTop,l,vTop)
            for(i in 0 until 8) coords[face*8+i]=values[i]
        }
        texCoords.clear();texCoords.put(coords);texCoords.position(0)
        Matrix.setIdentityM(part,0);Matrix.translateM(part,0,x,y,z);Matrix.scaleM(part,0,sx,sy,sz)
        Matrix.multiplyMM(vp,0,model,0,part,0)
        Matrix.multiplyMM(mvp,0,projection,0,view,0);Matrix.multiplyMM(mvp,0,mvp,0,vp,0)
        GLES20.glUniformMatrix4fv(matrixHandle,1,false,mvp,0)
        GLES20.glUniform1i(useTextureHandle,1);GLES20.glUniform1i(samplerHandle,0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,texture)
        vertices.position(0);GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle,3,GLES20.GL_FLOAT,false,24,vertices)
        vertices.position(3);GLES20.glEnableVertexAttribArray(normalHandle)
        GLES20.glVertexAttribPointer(normalHandle,3,GLES20.GL_FLOAT,false,24,vertices)
        GLES20.glEnableVertexAttribArray(texCoordHandle)
        GLES20.glVertexAttribPointer(texCoordHandle,2,GLES20.GL_FLOAT,false,0,texCoords)
        vertices.position(0);indices.position(0);GLES20.glDrawElements(GLES20.GL_TRIANGLES,36,GLES20.GL_UNSIGNED_SHORT,indices)
        GLES20.glUniform1i(useTextureHandle,0)
    }

    companion object {
        private val HEAD_UV=floatArrayOf(8f,8f,16f,16f, 24f,8f,32f,16f, 0f,8f,8f,16f, 16f,8f,24f,16f, 8f,0f,16f,8f, 16f,0f,24f,8f)
        private val TORSO_UV=floatArrayOf(20f,20f,28f,32f, 32f,20f,40f,32f, 16f,20f,20f,32f, 28f,20f,32f,32f, 20f,16f,28f,20f, 28f,16f,36f,20f)
        private val RIGHT_ARM_UV=floatArrayOf(44f,20f,48f,32f, 52f,20f,56f,32f, 40f,20f,44f,32f, 48f,20f,52f,32f, 44f,16f,48f,20f, 48f,16f,52f,20f)
        private val LEFT_ARM_UV=floatArrayOf(36f,52f,40f,64f, 44f,52f,48f,64f, 32f,52f,36f,64f, 40f,52f,44f,64f, 36f,48f,40f,52f, 40f,48f,44f,52f)
        private val RIGHT_LEG_UV=floatArrayOf(4f,20f,8f,32f, 12f,20f,16f,32f, 0f,20f,4f,32f, 8f,20f,12f,32f, 4f,16f,8f,20f, 8f,16f,12f,20f)
        private val LEFT_LEG_UV=floatArrayOf(20f,52f,24f,64f, 28f,52f,32f,64f, 16f,52f,20f,64f, 24f,52f,28f,64f, 20f,48f,24f,52f, 24f,48f,28f,52f)
        private val CAPE_UV=floatArrayOf(0f,1f,1f,17f, 12f,1f,22f,17f, 0f,1f,1f,17f, 11f,1f,12f,17f, 1f,0f,11f,1f, 11f,0f,21f,1f)
    }

    private fun drawCape(c: FloatArray, t: Float) {
        val sway = sin(t * 2.0f) * 3.0f
        draw(0f, 1.08f, -.235f, .70f, .92f, .075f, c, sway)
        draw(-.25f, 1.08f, -.285f, .18f, .88f, .045f, shade(c,.62f), sway - 2f)
        draw(.25f, 1.08f, -.285f, .18f, .88f, .045f, shade(c,.62f), sway + 2f)
        draw(0f, 1.52f, -.27f, .74f, .13f, .09f, shade(c,1.25f))
        draw(-.31f, 1.08f, -.30f, .035f, .94f, .035f, shade(c,1.4f))
        draw(.31f, 1.08f, -.30f, .035f, .94f, .035f, shade(c,1.4f))
        draw(0f, 1.20f, -.34f, .18f, .22f, .035f, shade(c,1.45f))
    }

    private fun drawWings(c: FloatArray, t: Float, id: String) {
        val flap = sin(t * 3.1f) * 5.5f
        draw(-.40f, 1.35f, -.22f, .24f, .35f, .18f, shade(c,.68f), 0f, 0f, -12f-flap)
        draw(.40f, 1.35f, -.22f, .24f, .35f, .18f, shade(c,.68f), 0f, 0f, 12f+flap)
        for (i in 0..3) {
            val y = 1.55f - i * .20f
            val x = .57f + i * .13f
            val length = .54f - i * .07f
            val feather = if (id.contains("dragon")) shade(c, if(i%2==0) 1.18f else .72f) else shade(c, if(i%2==0) 1.22f else .82f)
            draw(-x, y, -.27f, .20f, length, .10f, feather, 0f, 0f, -18f-flap-i*2f)
            draw(x, y, -.27f, .20f, length, .10f, feather, 0f, 0f, 18f+flap+i*2f)
        }
        draw(-.92f, 1.33f, -.26f, .13f, .32f, .09f, shade(c,1.35f), 0f, 0f, -28f-flap)
        draw(.92f, 1.33f, -.26f, .13f, .32f, .09f, shade(c,1.35f), 0f, 0f, 28f+flap)
    }

    private fun drawHalo(c: FloatArray, t: Float) {
        for (i in 0 until 12) {
            val a = (i * 30f + t * 12f) * Math.PI.toFloat() / 180f
            draw(cos(a)*.37f, 2.18f + sin(a)*.08f, -.01f, .12f, .12f, .12f, if(i%3==0) shade(c,1.35f) else c, 0f, 0f, i*30f)
        }
    }

    private fun drawCrown(c: FloatArray) {
        draw(0f, 2.02f, 0f, .57f, .12f, .50f, shade(c,.8f))
        for (i in -2..2) {
            draw(i*.105f, 2.12f + if(i%2==0) .08f else .02f, 0f, .075f, .22f, .075f, shade(c,if(i==0)1.45f else 1.12f), 0f, 0f, i*5f)
        }
        draw(0f, 2.10f, .265f, .10f, .10f, .04f, floatArrayOf(.95f,.22f,.20f,1f))
    }

    private fun drawHelmet(c: FloatArray, id: String) {
        draw(0f, 1.80f, 0f, .59f, .53f, .52f, c)
        draw(0f, 1.67f, .275f, .46f, .13f, .035f, shade(c,.48f))
        draw(0f, 1.78f, .30f, .30f, .07f, .025f, floatArrayOf(.22f,.78f,.92f,1f))
        if (id.contains("samurai")) {
            draw(0f, 2.12f, -.02f, .78f, .08f, .42f, shade(c,1.35f))
            draw(0f, 2.24f, -.02f, .10f, .24f, .12f, shade(c,1.5f))
        }
    }

    private fun drawFaceGear(c: FloatArray, id: String) {
        if (id.contains("monocle")) {
            draw(.12f, 1.73f, .25f, .16f, .16f, .05f, shade(c,1.25f))
            draw(.12f, 1.58f, .25f, .025f, .18f, .025f, c)
        } else {
            draw(0f, 1.72f, .245f, .42f, .14f, .06f, c)
            draw(0f, 1.60f, .245f, if(id.contains("respirator")) .34f else .24f, .10f, .07f, shade(c,.65f))
            if (id.contains("fox") || id.contains("oni")) {
                draw(-.15f, 1.84f, .23f, .12f, .10f, .06f, shade(c,1.3f))
                draw(.15f, 1.84f, .23f, .12f, .10f, .06f, shade(c,1.3f))
            }
        }
    }

    private fun drawTail(c: FloatArray, t: Float, id: String) {
        val sway = sin(t*2.2f)*.16f
        draw(0f, .88f, -.38f, .16f, .36f, .16f, c, 0f, 0f, -12f+sway*20f)
        draw(-.04f+sway, .60f, -.47f, .14f, .34f, .14f, shade(c,1.2f), 0f, 0f, 18f+sway*30f)
        draw(.04f+sway, .34f, -.48f, .19f, .25f, .18f, shade(c,if (id.contains("dragon")) 0.65f else 1.4f), 0f, 0f, -8f+sway*35f)
    }

    private fun drawBackGear(c: FloatArray, id: String, t: Float) {
        if (id.contains("shoulder")) {
            draw(.40f, 1.58f, -.18f, .25f, .25f, .25f, c, 0f, t*18f, 0f)
            draw(.40f, 1.76f, -.18f, .15f, .15f, .15f, shade(c,1.35f))
            return
        }
        draw(0f, 1.08f, -.34f, .43f, .58f, .28f, c)
        draw(0f, 1.08f, -.50f, .27f, .37f, .08f, shade(c,.65f))
        draw(-.25f, 1.12f, -.36f, .12f, .42f, .20f, shade(c,1.2f))
        draw(.25f, 1.12f, -.36f, .12f, .42f, .20f, shade(c,1.2f))
        if (id.contains("jet")) {
            draw(-.30f, .92f, -.36f, .16f, .48f, .18f, metalColor())
            draw(.30f, .92f, -.36f, .16f, .48f, .18f, metalColor())
            draw(-.30f, .62f, -.38f, .12f, .14f, .14f, floatArrayOf(.25f,.68f,1f,1f))
            draw(.30f, .62f, -.38f, .12f, .14f, .14f, floatArrayOf(.25f,.68f,1f,1f))
        }
    }

    private fun drawBracers(c: FloatArray) {
        draw(-.35f, .70f, .02f, .25f, .22f, .33f, c)
        draw(.35f, .70f, .02f, .25f, .22f, .33f, c)
        draw(-.35f, .70f, .20f, .14f, .12f, .035f, shade(c,1.5f))
        draw(.35f, .70f, .20f, .14f, .12f, .035f, shade(c,1.5f))
    }

    private fun drawAura(c: FloatArray, t: Float) {
        for (i in 0 until 14) {
            val a = (i * 25.7f + t * 35f) * Math.PI.toFloat() / 180f
            val y = .48f + (i % 5) * .34f
            draw(cos(a)*.58f, y + sin(t*2f+i)*.04f, .02f, .08f, .08f, .08f, if(i%2==0)shade(c,1.35f) else c)
        }
    }

    private fun drawSignatureGear(c: FloatArray, id: String) {
        if (id.contains("cat_ears") || id.contains("horn")) {
            draw(-.17f, 2.08f, 0f, .13f, .24f, .13f, c, 0f, 0f, -22f)
            draw(.17f, 2.08f, 0f, .13f, .24f, .13f, c, 0f, 0f, 22f)
        } else {
            draw(0f, 1.10f, -.24f, .70f, .80f, .10f, c)
            draw(0f, 1.10f, -.31f, .38f, .48f, .07f, shade(c,1.25f))
        }
    }

    private fun shade(color: FloatArray, factor: Float): FloatArray =
        floatArrayOf((color[0]*factor).coerceIn(0f,1f),(color[1]*factor).coerceIn(0f,1f),(color[2]*factor).coerceIn(0f,1f),color[3])

    private fun metalColor() = floatArrayOf(.42f,.46f,.54f,1f)

    private fun cosmeticColor(id: String): FloatArray = when {
        id.contains("frost") || id.contains("ice") || id.contains("glacier") || id.contains("ocean") -> floatArrayOf(.22f,.70f,.95f,1f)
        id.contains("void") || id.contains("nebula") || id.contains("galaxy") || id.contains("shadow") -> floatArrayOf(.56f,.31f,.90f,1f)
        id.contains("royal") || id.contains("crown") || id.contains("solar") -> floatArrayOf(.98f,.67f,.18f,1f)
        id.contains("moss") || id.contains("leaf") || id.contains("verdant") -> floatArrayOf(.25f,.70f,.38f,1f)
        id.contains("crimson") || id.contains("samurai") || id.contains("ember") -> floatArrayOf(.92f,.22f,.12f,1f)
        id.contains("fairy") || id.contains("celestial") -> floatArrayOf(.75f,.78f,1f,1f)
        else -> floatArrayOf(.98f,.40f,.12f,1f)
    }

    private fun draw(
        x: Float, y: Float, z: Float, sx: Float, sy: Float, sz: Float, color: FloatArray,
        yaw: Float = 0f, pitch: Float = 0f, roll: Float = 0f
    ) {
        Matrix.setIdentityM(part, 0)
        Matrix.translateM(part, 0, x, y, z)
        if (yaw != 0f) Matrix.rotateM(part, 0, yaw, 0f, 1f, 0f)
        if (pitch != 0f) Matrix.rotateM(part, 0, pitch, 1f, 0f, 0f)
        if (roll != 0f) Matrix.rotateM(part, 0, roll, 0f, 0f, 1f)
        Matrix.scaleM(part, 0, sx, sy, sz)
        Matrix.multiplyMM(vp, 0, model, 0, part, 0)
        Matrix.multiplyMM(mvp, 0, projection, 0, view, 0)
        Matrix.multiplyMM(mvp, 0, mvp, 0, vp, 0)
        GLES20.glUniformMatrix4fv(matrixHandle, 1, false, mvp, 0)
        GLES20.glUniform1i(useTextureHandle, 0)
        GLES20.glUniform4fv(colorHandle, 1, color, 0)
        vertices.position(0)
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 24, vertices)
        vertices.position(3)
        GLES20.glEnableVertexAttribArray(normalHandle)
        GLES20.glVertexAttribPointer(normalHandle, 3, GLES20.GL_FLOAT, false, 24, vertices)
        vertices.position(0)
        indices.position(0)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, 36, GLES20.GL_UNSIGNED_SHORT, indices)
    }
}
