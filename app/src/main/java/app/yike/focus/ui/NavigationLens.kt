package app.yike.focus.ui

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.semantics

internal val NavigationLensPosition=SemanticsPropertyKey<Float>("NavigationLensPosition")

/** One sharp alpha layer contains both icons and labels. It never samples its own output. */
@Composable internal fun Modifier.navigationContentLens(position: () -> Float, vertical: Boolean,
    interaction: GlassInteraction): Modifier {
    val layer=rememberGraphicsLayer()
    val density=LocalDensity.current.density
    val readable=glassReadable()
    val enabled=motionMillis(1)>0 && !readable
    val progress by androidx.compose.animation.core.animateFloatAsState(
        if(enabled && interaction.pressed) 1f else 0f,
        androidx.compose.animation.core.tween(motionMillis(if(interaction.pressed) 120 else 240)),label="nav_content_depth")
    val optics=remember {if(Build.VERSION.SDK_INT>=33) runCatching {NavigationOptics()}.getOrNull() else null}
    return semantics {this[NavigationLensPosition]=position()}.drawWithContent {
        val p=position()
        if(Build.VERSION.SDK_INT>=33 && optics!=null && drawContext.canvas.nativeCanvas.isHardwareAccelerated && enabled) {
            layer.record {this@drawWithContent.drawContent()}
            layer.renderEffect=optics.update(size.width,size.height,p,vertical,progress,density,interaction.velocity).asComposeRenderEffect()
            drawLayer(layer)
        } else drawContent()
    }
}

@RequiresApi(33)
internal class NavigationOptics(source: String=SOURCE) {
    private val shader=RuntimeShader(source)
    private val previous=FloatArray(8){Float.NaN};private val next=FloatArray(8)
    private var effect:RenderEffect?=null
    fun update(w:Float,h:Float,position:Float,vertical:Boolean,progress:Float,density:Float,velocity:Offset):RenderEffect {
        next[0]=w;next[1]=h;next[2]=position;next[3]=if(vertical) 1f else 0f
        next[4]=progress;next[5]=density;next[6]=velocity.x;next[7]=velocity.y
        if(next.contentEquals(previous) && effect!=null) return effect!!
        configure(w,h,position,vertical,progress,density,velocity)
        next.copyInto(previous)
        return RenderEffect.createRuntimeShaderEffect(shader,"scene").also {effect=it;GlassDiagnostics.effectUpdates++}
    }
    internal fun configure(w:Float,h:Float,position:Float,vertical:Boolean,progress:Float,density:Float,velocity:Offset):RuntimeShader {
        shader.setFloatUniform("lensSize",if(vertical) w else w/3,if(vertical) h/3 else h)
        shader.setFloatUniform("center",if(vertical) w/2 else (position+.5f)*w/3,if(vertical) (position+.5f)*h/3 else h/2)
        shader.setFloatUniform("strength",progress.coerceIn(0f,1f))
        shader.setFloatUniform("pixel",density)
        shader.setFloatUniform("velocity",velocity.x.coerceIn(-1f,1f),velocity.y.coerceIn(-1f,1f))
        return shader
    }
    companion object {
        internal const val SOURCE="""
            uniform shader scene;
            uniform float2 lensSize;
            uniform float2 center;
            uniform float strength;
            uniform float pixel;
            uniform float2 velocity;
            half4 main(float2 coord) {
                float2 p=coord-center;
                float radius=min(lensSize.x,lensSize.y)*0.5;
                float2 q=abs(p)-(lensSize*0.5-radius);
                float sd=length(max(q,float2(0)))+min(max(q.x,q.y),0.0)-radius;
                float weight=1.0-smoothstep(-8.0*pixel,2.0*pixel,sd);
                float2 n=normalize(p+float2(0.0001));
                float edge=1.0-smoothstep(0.0,14.0*pixel,-sd);
                float magnification=1.045+strength*0.105;
                float2 delta=p/magnification-p;
                delta-=n*edge*(1.0+strength*2.0)*pixel;
                delta-=velocity*edge*strength*pixel;
                return scene.eval(coord+delta*weight);
            }
        """
    }
}
