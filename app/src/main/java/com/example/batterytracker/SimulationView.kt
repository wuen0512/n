package com.example.batterytracker

import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.view.View
import kotlin.math.*
import java.util.Random

/** On-screen synthetic workloads: no external apps, data connection, or real video decoding. */
class SimulationView(context:Context):View(context){
 var mode="遊戲";var running=false;var onUnsafe:(String)->Unit={}
 private val paint=Paint(3);private val random=Random(1234);private val tiles=IntArray(48){Color.rgb(random.nextInt(210),random.nextInt(210),random.nextInt(210))}
 private var frame=0L;private var lastDraw=0L
 override fun onDraw(canvas:Canvas){super.onDraw(canvas);if(!running)return
  val now=SystemClock.uptimeMillis();frame++
  val w=width.toFloat();val h=height.toFloat()
  canvas.drawColor(Color.rgb(14,20,35))
  when(mode){
   "遊戲"->{
    // Bounded math work and many moving vector shapes to exercise CPU and GPU.
    var acc=0.2
    repeat(1600){acc=sin(acc+it*0.0003)+cos(acc*0.3)}
    for(i in 0 until 85){val x=((i*79+frame*3)%max(1,width)).toFloat();val y=((i*113+frame*2)%max(1,height)).toFloat()
     paint.color=Color.HSVToColor(floatArrayOf(((i*19+frame)%360).toFloat(),0.7f,0.9f));canvas.drawCircle(x,y,9f+(i%8)*4f,paint)}
    label(canvas,"GAME / synthetic graphics + math",w)
   }
   "社交"->{
    val offset=(frame*3%240).toFloat()
    for(i in 0..8){val top=i*240f-offset-200f
     paint.color=Color.rgb(35,45,65);canvas.drawRoundRect(12f,top,w-12f,top+220f,18f,18f,paint)
     paint.color=tiles[i%tiles.size];canvas.drawRect(28f,top+38f,w-28f,top+175f,paint)
     paint.color=Color.WHITE;paint.textSize=18f;canvas.drawText("Post #$i    ♥  ↗",30f,top+207f,paint)
    }
    label(canvas,"SOCIAL / scrolling cards + image-like tiles",w)
   }
   "影片"->{
    // Synthetic moving frames, not hardware video decoding or streaming.
    for(y in 0 until 18)for(x in 0 until 12){val hue=((x*18+y*7+frame*2)%360).toFloat()
     paint.color=Color.HSVToColor(floatArrayOf(hue,0.75f,0.85f));canvas.drawRect(x*w/12,y*h/18,(x+1)*w/12+1,(y+1)*h/18+1,paint)}
    label(canvas,"VIDEO-LIKE / animated frames (no codec)",w)
   }
   else->{paint.color=Color.LTGRAY;paint.textSize=25f;canvas.drawText("IDLE: no synthetic workload",24f,h/2)}
  }
  lastDraw=now
  postInvalidateDelayed(33L)
 }
 private fun label(c:Canvas,s:String,w:Float){paint.color=Color.BLACK;paint.alpha=200;c.drawRect(0f,0f,w,55f,paint);paint.alpha=255;paint.color=Color.WHITE;paint.textSize=16f;c.drawText(s,12f,32f,paint)}
 fun begin(name:String){mode=name;running=true;frame=0;invalidate()}
 fun halt(){running=false;invalidate()}
}
