/*
 * Rounded-rectangle distance and refraction adapted from AndroidLiquidGlass 1.0.0,
 * Copyright 2025 Kyant, Apache License 2.0.
 * https://github.com/Kyant0/AndroidLiquidGlass/tree/eada9a8600142c1bd6a5c5fc143d06f599cdfaef
 * Modifications: padded sampling, foreground isolation, magnification, pointer-driven depth,
 * bounded velocity response and shape-correct corner selection. See assets/glass_LICENSE.txt.
 */
package app.yike.focus.ui

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import androidx.annotation.RequiresApi

/** One shader per surface; only uniforms change during a gesture. No bitmap capture. */
@RequiresApi(33)
internal class GlassOptics(blur: Float, shaderSource: String = SOURCE, alreadyBlurred: Boolean = false) {
    private val shader = RuntimeShader(shaderSource)
    private val blurEffect = if(alreadyBlurred) null else RenderEffect.createBlurEffect(blur, blur, Shader.TileMode.CLAMP)
    private val lastParameters = FloatArray(16) { Float.NaN }
    private val nextParameters = FloatArray(16)
    private var cachedEffect: RenderEffect? = null

    fun update(w: Float, h: Float, padding: Float, corners: FloatArray, density: Float,
        role: GlassRole, progress: Float, px: Float, py: Float, vx: Float, vy: Float, readable: Boolean): RenderEffect {
        nextParameters[0]=w;nextParameters[1]=h;nextParameters[2]=padding;nextParameters[3]=density
        nextParameters[4]=role.ordinal.toFloat();nextParameters[5]=progress;nextParameters[6]=px;nextParameters[7]=py
        nextParameters[8]=vx;nextParameters[9]=vy;nextParameters[10]=if(readable) 1f else 0f
        for(i in 0..3) nextParameters[11+i]=corners[i]
        if(nextParameters.contentEquals(lastParameters) && cachedEffect!=null) return cachedEffect!!
        configure(w,h,padding,corners,density,role,progress,px,py,vx,vy,readable)
        val lens=RenderEffect.createRuntimeShaderEffect(shader,"scene")
        // Blur lives on the shared scene, rather than being rerun inside each moving lens.
        GlassDiagnostics.effectUpdates++
        return (if(blurEffect!=null) RenderEffect.createChainEffect(lens,blurEffect) else lens)
            .also { cachedEffect=it;nextParameters.copyInto(lastParameters) }
    }

    /** Shared by Android RenderEffect and host shader compilation/uniform validation. */
    internal fun configure(w: Float, h: Float, padding: Float, corners: FloatArray, density: Float,
        role: GlassRole, progress: Float, px: Float, py: Float, vx: Float, vy: Float, readable: Boolean): RuntimeShader {
        shader.setFloatUniform("extent", w, h)
        shader.setFloatUniform("padding", padding)
        shader.setFloatUniform("radii", corners)
        shader.setFloatUniform("pointer", px.coerceIn(0f, 1f), py.coerceIn(0f, 1f))
        shader.setFloatUniform("velocity", vx.coerceIn(-1f, 1f), vy.coerceIn(-1f, 1f))
        val control = role == GlassRole.CONTROL
        shader.setFloatUniform("depth", if (readable) 0f else (if (control) 7f + progress * 9f else 5f + 5f * progress) * density)
        shader.setFloatUniform("edgeWidth", minOf(minOf(w, h) * .42f, (if (control) 18f else 12f) * density))
        shader.setFloatUniform("magnification", if (readable) 1f else if (control) 1.035f + progress * .085f else 1f + progress * .008f)
        shader.setFloatUniform("active", progress)
        return shader
    }

    companion object {
        // Coordinates are local to the real shape, rather than guessed from its aspect ratio.
        internal const val SOURCE = """
            uniform shader scene;
            uniform float2 extent;
            uniform float padding;
            uniform float4 radii;
            uniform float2 pointer;
            uniform float2 velocity;
            uniform float depth;
            uniform float edgeWidth;
            uniform float magnification;
            uniform float active;
            float radiusAt(float2 p) {
                return p.x < 0.0 ? (p.y < 0.0 ? radii.x : radii.w) : (p.y < 0.0 ? radii.y : radii.z);
            }
            float sdRound(float2 p, float2 halfSize, float r) {
                float2 q = abs(p) - (halfSize - r);
                return length(max(q, float2(0.0))) + min(max(q.x, q.y), 0.0) - r;
            }
            float2 normalRound(float2 p, float2 halfSize, float r) {
                float2 q = abs(p) - (halfSize - r);
                float2 n = max(q, float2(0.0));
                if (max(q.x,q.y) > 0.001) return sign(p) * n / max(length(n), 0.001);
                return sign(p) * (q.x > q.y ? float2(1.0,0.0) : float2(0.0,1.0));
            }
            half4 main(float2 coord) {
                float2 p = coord - padding;
                float2 halfSize = extent * 0.5;
                float2 c = p - halfSize;
                float r = radiusAt(c);
                float sd = sdRound(c, halfSize, r);
                float edge = clamp(1.0 + sd / max(edgeWidth, 0.01), 0.0, 1.0);
                // Convex thickness profile, with a continuous zero-displacement inner boundary.
                float bend = (edge * edge * (3.0 - 2.0 * edge)) * depth;
                float2 normal = normalRound(c, halfSize, r);
                float2 lensCenter = halfSize + (pointer - 0.5) * extent * (0.10 * active);
                float2 sampled = lensCenter + (p - lensCenter) / magnification;
                sampled -= normal * bend;
                sampled -= velocity * (depth * 0.20 * active * edge);
                return scene.eval(sampled + padding);
            }
        """
    }
}
