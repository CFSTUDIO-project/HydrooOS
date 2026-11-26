package com.cfstudio.hydrogenos.graphics

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.view.View
import kotlin.math.max

/**
 * Glass material for Hydrogen-owned wallpaper surfaces.
 * On API 33+ AGSL samples the app-owned wallpaper bitmap with a displaced coordinate.
 * On older APIs it deliberately falls back to a translucent, edge-lit surface.
 */
class LiquidGlassSurface(context: Context) : View(context) {
    private var backdrop: Bitmap? = null
    private var intensity = 0.72f
    private var radiusPx = 28f * resources.displayMetrics.density
    private val rect = RectF()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.15f * resources.displayMetrics.density
    }
    private val matrix = Matrix()
    private var bitmapShader: BitmapShader? = null
    private var runtimeShader: RuntimeShader? = null
    private val fallbackPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
        if (Build.VERSION.SDK_INT >= 33) {
            runtimeShader = runCatching { RuntimeShader(AGS_SHADER) }.getOrNull()
        }
    }

    fun setMaterial(image: Bitmap?, strength: Float, cornerRadiusDp: Float = 28f) {
        if (backdrop !== image) {
            backdrop = image
            bitmapShader = image?.let { BitmapShader(it, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
        }
        intensity = strength.coerceIn(0f, 1f)
        radiusPx = cornerRadiusDp * resources.displayMetrics.density
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        val round = radiusPx.coerceAtMost(minOf(width, height) / 2f)
        val image = backdrop
        val shader = runtimeShader
        val texture = bitmapShader
        val shaderRendered = if (Build.VERSION.SDK_INT >= 33 && canvas.isHardwareAccelerated && image != null && shader != null && texture != null) {
            runCatching {
                val location = IntArray(2)
                getLocationOnScreen(location)
                val display = resources.displayMetrics
                val scale = max(display.widthPixels.toFloat() / image.width, display.heightPixels.toFloat() / image.height)
                val cropX = (image.width * scale - display.widthPixels) * 0.5f
                val cropY = (image.height * scale - display.heightPixels) * 0.5f
                val inverseScale = 1f / scale
                val values = floatArrayOf(
                    inverseScale, 0f, (location[0] - cropX) * inverseScale,
                    0f, inverseScale, (location[1] - cropY) * inverseScale,
                    0f, 0f, 1f
                )
                matrix.setValues(values)
                texture.setLocalMatrix(matrix)
                shader.setInputShader("uBackdrop", texture)
                shader.setFloatUniform("uSize", width.toFloat(), height.toFloat())
                shader.setFloatUniform("uIntensity", intensity)
                shader.setFloatUniform("uCorner", round)
                fill.shader = shader
                canvas.drawRoundRect(rect, round, round, fill)
                true
            }.getOrElse {
                runtimeShader = null
                false
            }
        } else false

        if (!shaderRendered) {
            // Honest compatibility fallback: no background sampling or refraction is claimed here.
            fallbackPaint.shader = android.graphics.LinearGradient(
                0f, 0f, width.toFloat(), height.toFloat(),
                intArrayOf(0x66D9FBFF, 0x2B8ED4E8, 0x4055BBD0),
                floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(rect, round, round, fallbackPaint)
            fallbackPaint.shader = null
            fallbackPaint.color = Color.argb(26, 255, 255, 255)
            fallbackPaint.style = Paint.Style.FILL
            val innerFallback = RectF(rect).apply { inset(1f, 1f) }
            canvas.drawRoundRect(innerFallback, round, round, fallbackPaint)
        }

        edge.shader = android.graphics.LinearGradient(
            0f, 0f, width.toFloat(), height.toFloat(),
            intArrayOf(0xB8E8FFFF.toInt(), 0x3569DDF1, 0x6EFFFFFF),
            floatArrayOf(0f, 0.58f, 1f), Shader.TileMode.CLAMP
        )
        val insetEdge = RectF(rect).apply { inset(0.7f, 0.7f) }
        canvas.drawRoundRect(insetEdge, round, round, edge)
        edge.shader = null
        edge.color = 0x22FFFFFF
        edge.strokeWidth = resources.displayMetrics.density
        canvas.drawLine(radiusPx, 1.5f, width - radiusPx, 1.5f, edge)
    }

    companion object {
        private const val AGS_SHADER = """
            uniform shader uBackdrop;
            uniform float2 uSize;
            uniform float uIntensity;
            uniform float uCorner;

            vec4 main(vec2 p) {
                vec2 halfSize = uSize * 0.5;
                vec2 centered = p - halfSize;
                vec2 safeSize = max(halfSize, vec2(1.0));
                vec2 normalized = centered / safeSize;
                float edgeDistance = length(normalized);
                float cornerInset = min(min(p.x, p.y), min(uSize.x - p.x, uSize.y - p.y));
                float cornerFade = smoothstep(0.0, max(1.0, uCorner * 0.34), cornerInset);
                vec2 bendDirection = normalize(centered + vec2(0.001, 0.001));
                float bendFalloff = 1.0 - smoothstep(0.12, 1.05, edgeDistance);
                vec2 displacement = bendDirection * (1.0 - bendFalloff * 0.35) * (2.0 + 8.0 * uIntensity);
                displacement += vec2(-bendDirection.y, bendDirection.x) * sin(edgeDistance * 12.0) * (1.3 * uIntensity);
                vec4 behind = uBackdrop.eval(p + displacement);

                vec3 normal = normalize(vec3(bendDirection * 0.30, 0.94));
                float fresnel = pow(1.0 - max(dot(normal, vec3(0.0, 0.0, 1.0)), 0.0), 3.0);
                float rim = (1.0 - smoothstep(0.78, 1.02, edgeDistance)) * cornerFade;
                float specular = pow(max(0.0, 1.0 - abs(normalized.y + normalized.x * 0.28)), 22.0) * 0.13;
                vec3 coolTint = vec3(0.58, 0.86, 0.96);
                vec3 refracted = mix(behind.rgb, coolTint, 0.075 + fresnel * 0.12);
                vec3 lit = refracted + vec3(0.72, 0.91, 1.0) * (rim * 0.075 + specular);
                float alpha = 0.80 + 0.12 * rim;
                return vec4(lit, alpha);
            }
        """
    }
}
