package com.cfstudio.hydrogenos.graphics

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.graphics.PixelFormat
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/** Short-lived OpenGL ES 2.0 scene: indexed 3D tear mesh, normal lighting and environment response. */
class DropletIntroView(context: Context) : GLSurfaceView(context) {
    init {
        setEGLContextClientVersion(2)
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        holder.setFormat(PixelFormat.TRANSLUCENT)
        setZOrderMediaOverlay(true)
        setRenderer(DropletRenderer())
        renderMode = RENDERMODE_CONTINUOUSLY
        preserveEGLContextOnPause = false
    }
}

private class DropletRenderer : GLSurfaceView.Renderer {
    private var program = 0
    private var envTexture = 0
    private var rippleProgram = 0
    private lateinit var rippleBuffer: FloatBuffer
    private var positionBuffer: FloatBuffer? = null
    private var normalBuffer: FloatBuffer? = null
    private var indexBuffer: ShortBuffer? = null
    private var indexCount = 0
    private var rendererReady = false
    private val projection = FloatArray(16)
    private val view = FloatArray(16)
    private val model = FloatArray(16)
    private val mv = FloatArray(16)
    private val mvp = FloatArray(16)
    private val startedAt = System.nanoTime()
    private val dropIndices = generateTearMesh()

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        rendererReady = false
        GLES20.glClearColor(0f, 0f, 0f, 0f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        runCatching {
            program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
            rippleProgram = createProgram(RIPPLE_VERTEX_SHADER, RIPPLE_FRAGMENT_SHADER)
            rippleBuffer = floatBuffer(FloatArray(96 * 3).also { values ->
                for (i in 0 until 96) {
                    val angle = (i / 96f) * (Math.PI * 2.0).toFloat()
                    values[i * 3] = cos(angle)
                    values[i * 3 + 1] = 0f
                    values[i * 3 + 2] = sin(angle)
                }
            })
            val (positions, normals, indices) = dropIndices
            positionBuffer = floatBuffer(positions)
            normalBuffer = floatBuffer(normals)
            indexBuffer = shortBuffer(indices)
            indexCount = indices.size
            envTexture = createEnvironmentTexture()
            rendererReady = true
        }.onFailure { error ->
            android.util.Log.e("HydrogenIntro", "OpenGL intro unavailable; continuing with the brand composition", error)
            if (program != 0) GLES20.glDeleteProgram(program)
            if (rippleProgram != 0) GLES20.glDeleteProgram(rippleProgram)
            if (envTexture != 0) GLES20.glDeleteTextures(1, intArrayOf(envTexture), 0)
            program = 0
            rippleProgram = 0
            envTexture = 0
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        Matrix.perspectiveM(projection, 0, 42f, width.toFloat() / max(1, height), 0.1f, 20f)
        Matrix.setLookAtM(view, 0, 0f, 0f, 3.4f, 0f, 0f, 0f, 1f, 0f)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        if (!rendererReady || program == 0) return
        val seconds = ((System.nanoTime() - startedAt) / 1_000_000_000.0).toFloat()
        val t = seconds.coerceIn(0f, 6.5f)
        val fall = smoothStep(0.15f, 2.75f, t)
        val y = 1.36f - fall * 1.18f
        val shrink = 1f - smoothStep(2.65f, 3.15f, t) * 0.72f

        Matrix.setIdentityM(model, 0)
        Matrix.translateM(model, 0, 0f, y, 0f)
        Matrix.rotateM(model, 0, t * 17f, 0.2f, 1f, 0.1f)
        Matrix.scaleM(model, 0, 0.56f * shrink, 0.77f * shrink, 0.53f * shrink)
        Matrix.multiplyMM(mv, 0, view, 0, model, 0)
        Matrix.multiplyMM(mvp, 0, projection, 0, mv, 0)

        GLES20.glUseProgram(program)
        val mvpLocation = GLES20.glGetUniformLocation(program, "uMvp")
        val modelLocation = GLES20.glGetUniformLocation(program, "uModel")
        GLES20.glUniformMatrix4fv(mvpLocation, 1, false, mvp, 0)
        GLES20.glUniformMatrix4fv(modelLocation, 1, false, model, 0)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uOpacity"), (1f - smoothStep(3.05f, 3.5f, t) * 0.78f).coerceIn(0f, 1f))
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, envTexture)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "uEnvironment"), 0)

        val pos = GLES20.glGetAttribLocation(program, "aPosition")
        val nor = GLES20.glGetAttribLocation(program, "aNormal")
        val positions = positionBuffer ?: return
        val normals = normalBuffer ?: return
        val indices = indexBuffer ?: return
        positions.position(0)
        GLES20.glEnableVertexAttribArray(pos)
        GLES20.glVertexAttribPointer(pos, 3, GLES20.GL_FLOAT, false, 0, positions)
        normals.position(0)
        GLES20.glEnableVertexAttribArray(nor)
        GLES20.glVertexAttribPointer(nor, 3, GLES20.GL_FLOAT, false, 0, normals)
        indices.position(0)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, indexCount, GLES20.GL_UNSIGNED_SHORT, indices)
        GLES20.glDisableVertexAttribArray(pos)
        GLES20.glDisableVertexAttribArray(nor)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        GLES20.glUseProgram(0)

        if (t > 2.72f && t < 4.5f) drawRipple(t - 2.72f)
    }

    private fun drawRipple(elapsed: Float) {
        val alpha = (1f - elapsed / 1.78f).coerceIn(0f, 1f)
        val radius = 0.14f + elapsed * 0.52f
        Matrix.setIdentityM(model, 0)
        Matrix.translateM(model, 0, 0f, -0.12f - elapsed * 0.05f, 0f)
        Matrix.scaleM(model, 0, radius, radius * 0.20f, radius * 0.38f)
        Matrix.multiplyMM(mv, 0, view, 0, model, 0)
        Matrix.multiplyMM(mvp, 0, projection, 0, mv, 0)
        GLES20.glUseProgram(rippleProgram)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(rippleProgram, "uMvp"), 1, false, mvp, 0)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(rippleProgram, "uAlpha"), alpha * 0.52f)
        val location = GLES20.glGetAttribLocation(rippleProgram, "aPosition")
        rippleBuffer.position(0)
        GLES20.glEnableVertexAttribArray(location)
        GLES20.glVertexAttribPointer(location, 3, GLES20.GL_FLOAT, false, 0, rippleBuffer)
        GLES20.glLineWidth(2.0f)
        GLES20.glDrawArrays(GLES20.GL_LINE_LOOP, 0, 96)
        GLES20.glDisableVertexAttribArray(location)
        GLES20.glUseProgram(0)
    }

    private fun createEnvironmentTexture(): Int {
        val bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(0f, 0f, 256f, 256f, intArrayOf(0xFF102A47.toInt(), 0xFF2E9BAE.toInt(), 0xFFBFEFF1.toInt()), null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, 256f, 256f, paint)
        paint.shader = RadialGradient(78f, 68f, 90f, intArrayOf(0xBFFFFFFF.toInt(), 0x00FFFFFF), null, Shader.TileMode.CLAMP)
        canvas.drawCircle(78f, 68f, 90f, paint)
        paint.shader = RadialGradient(198f, 202f, 74f, intArrayOf(0xA66CEBEE.toInt(), 0x006CEBEE), null, Shader.TileMode.CLAMP)
        canvas.drawCircle(198f, 202f, 74f, paint)
        paint.shader = null
        paint.color = 0x77FFFFFF
        paint.strokeWidth = 3f
        canvas.drawArc(18f, 20f, 236f, 238f, 210f, 92f, false, paint)
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ids[0])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        android.opengl.GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        bitmap.recycle()
        return ids[0]
    }

    private fun generateTearMesh(): Triple<FloatArray, FloatArray, ShortArray> {
        val rings = 32
        val segments = 40
        val positions = ArrayList<Float>((rings + 1) * (segments + 1) * 3)
        val normals = ArrayList<Float>((rings + 1) * (segments + 1) * 3)
        for (r in 0..rings) {
            val v = r.toFloat() / rings
            val y = 0.78f - v * 1.56f
            val bulb = sin(Math.PI * v).toFloat().coerceAtLeast(0f).let { it * Math.pow(it.toDouble(), 0.78).toFloat() }
            val asymmetry = 0.72f + 0.56f * v
            val radius = 0.58f * bulb * asymmetry
            for (s in 0..segments) {
                val a = (s.toFloat() / segments) * (Math.PI * 2.0).toFloat()
                val x = cos(a) * radius
                val z = sin(a) * radius * 0.78f
                positions += x; positions += y; positions += z
                val nx = x * 1.45f
                val ny = (0.78f - y) * 0.22f - 0.17f
                val nz = z * 1.55f
                val len = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(0.0001f)
                normals += nx / len; normals += ny / len; normals += nz / len
            }
        }
        val indices = ArrayList<Short>(rings * segments * 6)
        for (r in 0 until rings) for (s in 0 until segments) {
            val a = (r * (segments + 1) + s).toShort()
            val b = (a.toInt() + segments + 1).toShort()
            val c = (a.toInt() + 1).toShort()
            val d = (b.toInt() + 1).toShort()
            indices += a; indices += b; indices += c
            indices += c; indices += b; indices += d
        }
        return Triple(positions.toFloatArray(), normals.toFloatArray(), indices.toShortArray())
    }

    private fun floatBuffer(values: FloatArray): FloatBuffer = ByteBuffer.allocateDirect(values.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(values); position(0) }
    private fun shortBuffer(values: ShortArray): ShortBuffer = ByteBuffer.allocateDirect(values.size * 2).order(ByteOrder.nativeOrder()).asShortBuffer().apply { put(values); position(0) }

    private fun createProgram(vertexSource: String, fragmentSource: String): Int {
        val vertex = compileShader(GLES20.GL_VERTEX_SHADER, vertexSource)
        val fragment = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        return GLES20.glCreateProgram().also { shaderProgram ->
            GLES20.glAttachShader(shaderProgram, vertex)
            GLES20.glAttachShader(shaderProgram, fragment)
            GLES20.glLinkProgram(shaderProgram)
            val status = IntArray(1)
            GLES20.glGetProgramiv(shaderProgram, GLES20.GL_LINK_STATUS, status, 0)
            if (status[0] == 0) {
                val log = GLES20.glGetProgramInfoLog(shaderProgram)
                GLES20.glDeleteProgram(shaderProgram)
                throw IllegalStateException("Hydrogen intro shader link failed: $log")
            }
            GLES20.glDeleteShader(vertex)
            GLES20.glDeleteShader(fragment)
            shaderProgram
        }
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            throw IllegalStateException("Hydrogen intro shader compile failed: $log")
        }
        return shader
    }

    private fun smoothStep(a: Float, b: Float, value: Float): Float {
        val x = ((value - a) / (b - a)).coerceIn(0f, 1f)
        return x * x * (3f - 2f * x)
    }

    companion object {
        private const val RIPPLE_VERTEX_SHADER = "attribute vec3 aPosition; uniform mat4 uMvp; void main(){ gl_Position=uMvp*vec4(aPosition,1.0); }"
        private const val RIPPLE_FRAGMENT_SHADER = "precision mediump float; uniform float uAlpha; void main(){ gl_FragColor=vec4(0.42,0.88,0.96,uAlpha); }"
        private const val VERTEX_SHADER = """
            uniform mat4 uMvp;
            uniform mat4 uModel;
            attribute vec3 aPosition;
            attribute vec3 aNormal;
            varying vec3 vNormal;
            varying vec3 vPosition;
            void main() {
                vec4 world = uModel * vec4(aPosition, 1.0);
                vPosition = world.xyz;
                vNormal = normalize(mat3(uModel) * aNormal);
                gl_Position = uMvp * vec4(aPosition, 1.0);
            }
        """
        private const val FRAGMENT_SHADER = """
            precision mediump float;
            uniform sampler2D uEnvironment;
            uniform float uOpacity;
            varying vec3 vNormal;
            varying vec3 vPosition;
            void main() {
                vec3 N = normalize(vNormal);
                vec3 V = normalize(vec3(0.0, 0.0, 3.4) - vPosition);
                float fresnel = pow(1.0 - max(dot(N, V), 0.0), 3.5);
                vec3 R = reflect(-V, N);
                vec2 refractUV = clamp(vec2(0.5 + N.x * 0.36, 0.5 - N.y * 0.36) + N.xy * 0.045, 0.02, 0.98);
                vec2 reflectUV = clamp(vec2(0.5 + R.x * 0.34, 0.5 + R.y * 0.34), 0.02, 0.98);
                vec3 transmitted = texture2D(uEnvironment, refractUV).rgb;
                vec3 reflected = texture2D(uEnvironment, reflectUV).rgb;
                vec3 L = normalize(vec3(-0.55, 0.72, 0.8));
                float specular = pow(max(dot(reflect(-L, N), V), 0.0), 54.0);
                vec3 color = mix(transmitted, reflected, 0.28 + fresnel * 0.62);
                color += vec3(0.68, 0.93, 1.0) * (specular * 0.75 + fresnel * 0.22);
                float alpha = (0.34 + fresnel * 0.54 + specular * 0.22) * uOpacity;
                gl_FragColor = vec4(color, alpha);
            }
        """
    }
}
