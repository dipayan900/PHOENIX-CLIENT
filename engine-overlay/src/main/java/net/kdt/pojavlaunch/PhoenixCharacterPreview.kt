package net.kdt.pojavlaunch

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.view.MotionEvent
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.max

/**
 * Small real OpenGL ES 2.0 player mannequin preview. Drag horizontally to orbit.
 * The meshes are generated in code and require no external model downloads.
 */
@Composable
fun PhoenixCharacterPreview(cosmeticId: String, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { context -> PhoenixCharacterGLView(context).apply { setCosmetic(cosmeticId) } },
        update = { it.setCosmetic(cosmeticId) }
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
                    previewRenderer.rotate(dx * 0.65f)
                    true
                }
                else -> true
            }
        }
    }
    fun setCosmetic(id: String) { previewRenderer.setCosmetic(id) }
}

private class PhoenixCharacterRenderer : GLSurfaceView.Renderer {
    private val projection = FloatArray(16)
    private val view = FloatArray(16)
    private val model = FloatArray(16)
    private val mvp = FloatArray(16)
    private var program = 0
    private var angle = -18f
    @Volatile private var cosmetic = ""
    private var width = 1
    private var height = 1
    private lateinit var vertices: FloatBuffer
    private lateinit var indices: ShortBuffer
    private var positionHandle = 0
    private var matrixHandle = 0
    private var colorHandle = 0

    fun rotate(delta: Float) { angle = (angle + delta) % 360f }
    fun setCosmetic(id: String) { cosmetic = id }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.055f, 0.035f, 0.075f, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        val vertexShader = compile(GLES20.GL_VERTEX_SHADER,
            "attribute vec3 aPosition; uniform mat4 uMvp; void main(){ gl_Position=uMvp*vec4(aPosition,1.0); }")
        val fragmentShader = compile(GLES20.GL_FRAGMENT_SHADER,
            "precision mediump float; uniform vec4 uColor; void main(){ gl_FragColor=uColor; }")
        program = GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vertexShader); GLES20.glAttachShader(it, fragmentShader); GLES20.glLinkProgram(it)
        }
        positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        matrixHandle = GLES20.glGetUniformLocation(program, "uMvp")
        colorHandle = GLES20.glGetUniformLocation(program, "uColor")
        val v = floatArrayOf(
            -0.5f,-0.5f,0.5f, 0.5f,-0.5f,0.5f, 0.5f,0.5f,0.5f, -0.5f,0.5f,0.5f,
            -0.5f,-0.5f,-0.5f, 0.5f,-0.5f,-0.5f, 0.5f,0.5f,-0.5f, -0.5f,0.5f,-0.5f
        )
        val ix = shortArrayOf(
            0,1,2,0,2,3, 5,4,7,5,7,6, 4,0,3,4,3,7,
            1,5,6,1,6,2, 3,2,6,3,6,7, 4,5,1,4,1,0
        )
        vertices = ByteBuffer.allocateDirect(v.size*4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(v); position(0) }
        indices = ByteBuffer.allocateDirect(ix.size*2).order(ByteOrder.nativeOrder()).asShortBuffer().apply { put(ix); position(0) }
    }

    private fun compile(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source); GLES20.glCompileShader(shader)
        val status = IntArray(1); GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        check(status[0] == GLES20.GL_TRUE) { GLES20.glGetShaderInfoLog(shader) }
        return shader
    }

    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
        width = max(1,w); height = max(1,h); GLES20.glViewport(0,0,width,height)
        Matrix.frustumM(projection,0,-width.toFloat()/height, width.toFloat()/height,-1f,1f,2.5f,30f)
        Matrix.setLookAtM(view,0,0f,1.6f,7.5f,0f,1.1f,0f,0f,1f,0f)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        GLES20.glUseProgram(program)
        Matrix.setIdentityM(model,0)
        Matrix.rotateM(model,0,angle,0f,1f,0f)
        val base = floatArrayOf(0.70f,0.57f,0.48f,1f)
        val suit = floatArrayOf(0.25f,0.22f,0.32f,1f)
        val accent = cosmeticColor(cosmetic)
        // Body, head, arms, legs.
        cube(-0.0f,1.05f,0f,0.64f,0.82f,0.34f,suit)
        cube(0f,1.72f,0f,0.48f,0.48f,0.42f,base)
        cube(-0.48f,1.08f,0f,0.22f,0.78f,0.25f,base)
        cube(0.48f,1.08f,0f,0.22f,0.78f,0.25f,base)
        cube(-0.19f,0.38f,0f,0.25f,0.72f,0.29f,floatArrayOf(0.30f,0.32f,0.40f,1f))
        cube(0.19f,0.38f,0f,0.25f,0.72f,0.29f,floatArrayOf(0.30f,0.32f,0.40f,1f))
        when {
            cosmetic.contains("cape") -> cube(0f,1.02f,-0.25f,0.78f,1.18f,0.08f,accent)
            cosmetic.contains("wing") -> {
                cube(-0.72f,1.18f,-0.18f,0.72f,0.92f,0.12f,accent, -16f)
                cube(0.72f,1.18f,-0.18f,0.72f,0.92f,0.12f,accent, 16f)
            }
            cosmetic.contains("halo") || cosmetic.contains("crown") || cosmetic.contains("helmet") ->
                cube(0f,2.03f,0f,0.62f,0.14f,0.55f,accent)
            cosmetic.contains("tail") || cosmetic.contains("back") || cosmetic.contains("pack") ->
                cube(0f,1.05f,-0.38f,0.46f,0.62f,0.25f,accent)
            else -> cube(0f,1.05f,-0.23f,0.68f,0.84f,0.06f,accent)
        }
    }

    private fun cosmeticColor(id: String): FloatArray = when {
        id.contains("frost") || id.contains("ice") || id.contains("ocean") -> floatArrayOf(0.25f,0.72f,0.95f,1f)
        id.contains("void") || id.contains("nebula") || id.contains("galaxy") -> floatArrayOf(0.58f,0.32f,0.88f,1f)
        id.contains("royal") || id.contains("crown") || id.contains("solar") -> floatArrayOf(0.98f,0.68f,0.20f,1f)
        id.contains("moss") || id.contains("leaf") -> floatArrayOf(0.25f,0.70f,0.38f,1f)
        id.contains("crimson") || id.contains("samurai") -> floatArrayOf(0.90f,0.20f,0.22f,1f)
        else -> floatArrayOf(0.98f,0.38f,0.12f,1f)
    }

    private fun cube(x: Float,y: Float,z: Float,sx: Float,sy: Float,sz: Float,color: FloatArray,roll: Float=0f) {
        val part = FloatArray(16); val vp = FloatArray(16)
        Matrix.setIdentityM(part,0)
        Matrix.translateM(part,0,x,y,z)
        if(roll!=0f) Matrix.rotateM(part,0,roll,0f,0f,1f)
        Matrix.scaleM(part,0,sx,sy,sz)
        Matrix.multiplyMM(vp,0,model,0,part,0)
        Matrix.multiplyMM(mvp,0,projection,0,view,0)
        Matrix.multiplyMM(mvp,0,mvp,0,vp,0)
        GLES20.glUniformMatrix4fv(matrixHandle,1,false,mvp,0)
        GLES20.glUniform4fv(colorHandle,1,color,0)
        vertices.position(0)
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle,3,GLES20.GL_FLOAT,false,12,vertices)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES,36,GLES20.GL_UNSIGNED_SHORT,indices)
    }
}
