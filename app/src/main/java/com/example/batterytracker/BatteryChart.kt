package com.example.batterytracker

import android.content.Context
import android.graphics.*
import android.view.View
import java.io.File

class BatteryChart(context:Context):View(context){
 private val paint=Paint(3)
 var file:File?=null
 override fun onDraw(canvas:Canvas){super.onDraw(canvas)
  canvas.drawColor(Color.rgb(246,248,251));val f=file?:return
  if(!f.exists())return
  val data=f.readLines().drop(1).takeLast(200).mapNotNull{it.split(',').getOrNull(1)?.toFloatOrNull()}
  if(data.size<2)return
  val left=42f;val right=width-12f;val top=16f;val bottom=height-28f
  paint.color=Color.LTGRAY;paint.strokeWidth=2f
  for(i in 0..4){val y=top+(bottom-top)*i/4f;canvas.drawLine(left,y,right,y,paint)}
  paint.color=Color.rgb(35,100,190);paint.strokeWidth=4f;paint.style=Paint.Style.STROKE
  val path=Path()
  data.forEachIndexed{idx,v->val x=left+(right-left)*idx/(data.size-1);val y=bottom-(bottom-top)*v/100f;if(idx==0)path.moveTo(x,y) else path.lineTo(x,y)}
  canvas.drawPath(path,paint);paint.style=Paint.Style.FILL;paint.textSize=16f;paint.color=Color.DKGRAY
  canvas.drawText("100%",2f,top+14f,paint);canvas.drawText("0%",8f,bottom,paint)
 }
}
