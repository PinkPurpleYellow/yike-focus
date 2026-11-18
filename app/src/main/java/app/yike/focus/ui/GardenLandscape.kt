package app.yike.focus.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlin.math.*

internal data class GardenVariety(val name:String,val color:Color,val shade:Color)
internal val GardenVarieties=listOf(
    GardenVariety("晨光红果",Color(0xFFD46B52),Color(0xFFAC483B)),
    GardenVariety("蜜金番茄",Color(0xFFE5B65D),Color(0xFFC58C3C)),
    GardenVariety("青柠小果",Color(0xFFA2B778),Color(0xFF738957)),
    GardenVariety("粉霞樱桃",Color(0xFFDA9C9B),Color(0xFFB8767C)),
    GardenVariety("暮紫番茄",Color(0xFF9B839D),Color(0xFF705A7A)),
    GardenVariety("日落条纹",Color(0xFFD89858),Color(0xFFAD643D)))

@Composable internal fun GardenDrop(color:Color,modifier:Modifier=Modifier) {
    Canvas(modifier) {
        val w=size.width;val h=size.height
        val path=Path().apply {moveTo(w*.5f,0f);cubicTo(w*.42f,h*.25f,w*.1f,h*.47f,w*.12f,h*.68f)
            cubicTo(w*.14f,h*1.08f,w*.86f,h*1.08f,w*.88f,h*.68f);cubicTo(w*.9f,h*.47f,w*.58f,h*.25f,w*.5f,0f);close()}
        drawPath(path,Brush.verticalGradient(listOf(color.copy(alpha=.65f),color)))
        drawOval(Color.White.copy(alpha=.45f),Offset(w*.28f,h*.5f),Size(w*.14f,h*.24f))
    }
}

/** Drawn vectors, no perpetual animation or external artwork. Values only change on interaction. */
@Composable internal fun GardenLandscape(growth:Float,sway:Float,water:Float,picking:Float,variety:Int,modifier:Modifier=Modifier,collected:Int=0) {
    val dark=Paper.luminance()<.5f
    val stage=growth.toInt().coerceIn(0,4)
    Canvas(modifier.clip(RoundedCornerShape(24.dp)).semantics {contentDescription="番茄花房，${listOf("种子","萌芽","长叶","开花","结果")[stage]}"}) {
        val u=minOf(size.width/520f,size.height/460f)
        val left=(size.width-520f*u)/2;val top=(size.height-460f*u)/2
        withTransform({translate(left,top);scale(u,u,pivot=Offset.Zero)}) {
            val sky=if(dark) Color(0xFF293D49) else Color(0xFFDEE9DD)
            val skyEnd=if(dark) Color(0xFF46595C) else Color(0xFFF0EAD7)
            drawRoundRect(Brush.verticalGradient(listOf(sky,skyEnd),0f,460f),size=Size(520f,460f),cornerRadius=CornerRadius(28f))
            val win=Path().apply {moveTo(66f,294f);lineTo(66f,130f);cubicTo(66f,3f,454f,3f,454f,130f);lineTo(454f,294f);close()}
            drawPath(win,if(dark) Color(0xFF20343F) else Color(0xFFF7F1DF))
            clipPath(win) {
                drawRect(Brush.verticalGradient(listOf(if(dark) Color(0xFF203441) else Color(0xFFD0E2DA),if(dark) Color(0xFF617274) else Color(0xFFF9E6C5)),30f,290f),size=Size(520f,300f))
                drawCircle(if(dark) Color(0xFFF1E2BA) else Color(0xFFF6D998),35f,Offset(376f,92f))
                if(dark) {
                    drawCircle(Color(0xFF263A46),31f,Offset(388f,81f))
                    for(i in 0..14) {val x=95f+(i*73%345);val y=40f+(i*47%170);drawCircle(Color(0xFFF4E8CB).copy(alpha=.45f),if(i%3==0) 1.8f else 1.2f,Offset(x,y))}
                } else {
                    drawOval(Color.White.copy(alpha=.32f),Offset(96f,106f),Size(130f,24f))
                    drawOval(Color.White.copy(alpha=.24f),Offset(235f,157f),Size(140f,20f))
                    val beam=Path().apply {moveTo(398f,51f);lineTo(520f,375f);lineTo(235f,375f);close()}
                    drawPath(beam,Color(0xFFFFF7DB).copy(alpha=.25f))
                }
                val hills=Path().apply {moveTo(0f,270f);cubicTo(130f,145f,215f,300f,350f,219f);cubicTo(420f,187f,495f,219f,540f,261f);lineTo(540f,300f);lineTo(0f,300f);close()}
                drawPath(hills,if(dark) Color(0xFF3A5754) else Color(0xFFADC5AE))
                drawLine(if(dark) Color(0xFF85918B) else Color(0xFFF8F3E7),Offset(260f,35f),Offset(260f,296f),5f)
                drawLine(if(dark) Color(0xFF85918B) else Color(0xFFF8F3E7),Offset(66f,176f),Offset(454f,176f),5f)
            }
            drawPath(win,if(dark) Color(0xFF91A09A).copy(alpha=.45f) else Color.White.copy(alpha=.76f),style=Stroke(4f))
            drawRoundRect(if(dark) Color(0xFF65756D) else Color(0xFFE7DDC7),Offset(53f,292f),Size(414f,13f),CornerRadius(5f))
            // A quiet shelf with a tiny succulent, seed packet and terracotta dish.
            drawRoundRect(if(dark) Color(0xFF4E5E56) else Color(0xFFE1D5BF),Offset(0f,336f),Size(520f,124f),CornerRadius(0f))
            drawLine(Color.White.copy(alpha=if(dark) .08f else .35f),Offset(18f,354f),Offset(502f,354f),1f)
            drawOval(Color.Black.copy(alpha=.06f),Offset(66f,360f),Size(114f,14f))
            drawRoundRect(if(dark) Color(0xFF7E8980) else Color(0xFFC5CBB6),Offset(91f,318f),Size(42f,40f),CornerRadius(9f))
            for(i in 0..4) {
                rotate((i-2)*26f,Offset(112f,326f)) {drawOval(if(dark) Color(0xFF7B9C82) else Color(0xFF8CA887),Offset(105f,282f),Size(15f,47f))}
            }
            rotate(-9f,Offset(410f,341f)) {
                drawRoundRect(if(dark) Color(0xFFB4AC91) else Color(0xFFF5E9CB),Offset(386f,292f),Size(52f,65f),CornerRadius(5f))
                drawCircle(Color(0xFFBC725B),11f,Offset(412f,319f))
                drawLine(Color(0xFF6C8C63),Offset(408f,309f),Offset(419f,309f),3f)
                drawLine(Color(0xFFAFAB8E),Offset(397f,340f),Offset(427f,340f),2f)
            }
            drawBasket(Offset(382f,381f),dark)
            repeat(minOf(collected,4)) {i ->drawTomato(Offset(395f+i*14f,380f),10f,i%6,true)}
            drawOval(Color.Black.copy(alpha=.09f),Offset(180f,381f),Size(164f,23f))
            drawOval(if(dark) Color(0xFF516258) else Color(0xFFC7D2B8),Offset(172f,376f),Size(180f,31f))
            val grow=growth.coerceIn(0f,4f)
            val leaf=if(dark) Color(0xFF94B08D) else Color(0xFF608560)
            val stemHeight=(grow*44f).coerceAtMost(154f)
            rotate(sway,Offset(260f,332f)) {
                if(grow>0f) {
                    val stem=Path().apply {moveTo(260f,336f);cubicTo(246f,296f,276f,336f-stemHeight*.6f,260f,336f-stemHeight)}
                    drawPath(stem,leaf,style=Stroke(4f,cap=StrokeCap.Round))
                    for(i in 0..5) {
                        val weight=(grow*1.6f-i*.70f).coerceIn(0f,1f)
                        if(weight>0f) {
                            val side=if(i%2==0) -1f else 1f;val y=312f-i*22f
                            val x=260f+if(i%2==0) -3f else 3f
                            scale(weight,weight,pivot=Offset(x,y)) {
                                val path=Path().apply {moveTo(x,y);cubicTo(x+side*23f,y-42f,x+side*57f,y-24f,x+side*56f,y-31f)
                                    cubicTo(x+side*51f,y+6f,x+side*20f,y+12f,x,y);close()}
                                drawPath(path,if(i%2==0) leaf else lerp(leaf,Color(0xFFB5C68E),.2f))
                                drawLine(Color.White.copy(alpha=.2f),Offset(x,y),Offset(x+side*43f,y-22f),1f)
                            }
                        }
                    }
                    val bloom=(grow-2f).coerceIn(0f,1f)
                    if(bloom>0f) for(side in listOf(-1f,1f)) {
                        val center=Offset(260f+side*23f,189f)
                        scale(bloom,bloom,pivot=center) {repeat(5) {i ->
                            rotate(i*72f,center) {drawOval(Color(0xFFE5C577),center+Offset(-4f,-12f),Size(8f,14f))}}
                            drawCircle(Color(0xFFF7E8AC),3f,center)
                        }
                    }
                    val fruit=(grow-3f).coerceIn(0f,1f)
                    if(fruit>0f) {
                        val c=Offset(276f,245f)
                        val travel=Offset(146f*picking,-110f*sin(picking*PI).toFloat()+140f*picking)
                        scale(fruit*(1f-.4f*picking),fruit*(1f-.4f*picking),pivot=c+travel) {
                            drawTomato(c+travel,23f,variety,true)
                        }
                        if(picking>0f) repeat(9) {i ->
                            val angle=i*2.39996f;val reach=picking*46f
                            drawCircle(Color(0xFFF2D79B).copy(alpha=(1f-picking)*.8f),2f, c+travel+Offset(cos(angle)*reach,sin(angle)*reach))
                        }
                    }
                } else drawOval(leaf.copy(alpha=.7f),Offset(255f,329f),Size(10f,4f))
            }
            // Soft clay shading and a wide lip ground the plant in the scene.
            val pot=Path().apply {moveTo(218f,333f);lineTo(302f,333f);lineTo(292f,387f);quadraticTo(260f,399f,228f,387f);close()}
            drawPath(pot,Brush.horizontalGradient(listOf(Color(0xFFB57D66),Color(0xFFD19C7E),Color(0xFFBD8268)),218f,302f))
            drawOval(Color(0xFF8F6753),Offset(216f,325f),Size(88f,20f))
            drawOval(Color(0xFF685145),Offset(223f,328f),Size(74f,12f))
            drawRoundRect(Color(0xFFCC977A),Offset(211f,334f),Size(98f,12f),CornerRadius(5f))
            drawLine(Color.White.copy(alpha=.22f),Offset(235f,353f),Offset(240f,381f),3f,StrokeCap.Round)
            if(water>0f && water<1f) {
                for(i in 0..15) {
                    val t=((water-i*.022f)/.62f).coerceIn(0f,1f)
                    if(t>0f && t<1f) {
                        val p=Offset(340f-75f*t+(i%3-1)*5f,135f+192f*t*t)
                        drawOval(Color(0xFF72A9BA).copy(alpha=.8f*(1f-t*.4f)),p,Size(3f,7f))
                    }
                }
                val ripple=(water-.45f).coerceAtLeast(0f)*1.8f
                if(ripple>0f) drawOval(Color(0xFF91C2CB).copy(alpha=(1f-ripple).coerceIn(0f,1f)*.7f),Offset(260f-ripple*30f,329f-ripple*5f),Size(ripple*60f,ripple*10f),style=Stroke(1.5f))
            }
        }
    }
}

private fun DrawScope.drawBasket(origin:Offset,dark:Boolean) {
    drawRoundRect(if(dark) Color(0xFF947C5E) else Color(0xFFD1B188),origin,Size(72f,29f),CornerRadius(7f))
    repeat(5) {i ->drawLine(Color.White.copy(alpha=.18f),origin+Offset(8f+i*13f,3f),origin+Offset(10f+i*13f,26f),2f)}
    drawRoundRect(Color(0xFFBA966C),origin-Offset(3f,2f),Size(78f,7f),CornerRadius(3f))
}

private fun DrawScope.drawTomato(center:Offset,radius:Float,variety:Int,unlocked:Boolean) {
    val v=GardenVarieties[variety.coerceIn(0,5)]
    val color=if(unlocked) v.color else Color(0xFFB8BEB3)
    val shade=if(unlocked) v.shade else Color(0xFFA6AEA2)
    drawOval(Brush.radialGradient(listOf(color,shade),center-Offset(radius*.25f,radius*.3f),radius*1.7f),center-Offset(radius,radius*.85f),Size(radius*2,radius*1.8f))
    if(variety==5 && unlocked) repeat(4) {i ->
        drawArc(Color(0xFFEAC584).copy(alpha=.8f),80f+i*18f,85f,false,center-Offset(radius*(1f-i*.18f),radius*.8f),Size(radius*(2f-i*.3f),radius*1.65f),style=Stroke(radius*.1f))
    }
    drawOval(Color.White.copy(alpha=.25f),center-Offset(radius*.6f,radius*.55f),Size(radius*.35f,radius*.2f))
    val green=if(unlocked) Color(0xFF64865B) else Color(0xFF9DA695)
    val crown=Path().apply {moveTo(center.x,center.y-radius*.66f);lineTo(center.x-radius*.5f,center.y-radius)
        lineTo(center.x-radius*.13f,center.y-radius*.93f);lineTo(center.x,center.y-radius*1.19f)
        lineTo(center.x+radius*.14f,center.y-radius*.93f);lineTo(center.x+radius*.5f,center.y-radius);close()}
    drawPath(crown,green)
}

@Composable internal fun GardenFruit(variety:Int,unlocked:Boolean,modifier:Modifier=Modifier) {
    Canvas(modifier) {
        val r=minOf(size.width,size.height)*.28f;val c=Offset(size.width/2,size.height*.51f)
        drawOval(Color.Black.copy(alpha=.06f),c+Offset(-r*.85f,r*.8f),Size(r*1.7f,r*.25f))
        drawTomato(c,r,variety,unlocked)
    }
}
