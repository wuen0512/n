package com.example.batterytracker

import android.app.*
import android.content.*
import android.os.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

class MonitorService : Service() {
 companion object { const val CHANNEL="battery_monitor"; const val UPDATE="com.example.batterytracker.UPDATE"; const val PREF="battery_settings" }
 private val handler=Handler(Looper.getMainLooper())
 private val fmt=SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
 private var receiver: BroadcastReceiver?=null
 private var lastLevel=-1; private var lastTime=0L; private var charging=false; private var temperature=0.0
 private var anchor=-1; private var anchorTime=0L; private var direction=0
 private var mode="監測"; private var endAt=0L; private var sessionStart=0L; private var sessionStartLevel=0
 private var lastSampleAt=0L; private var stopped=false
 private val tick=object:Runnable { override fun run() {
  if(endAt>0 && System.currentTimeMillis()>=endAt) stopSession("時間結束")
  if(endAt>0 && temperature>=42.0) stopSession("電池溫度達 42°C")
  if(lastLevel>=0 && System.currentTimeMillis()-lastSampleAt>=60_000) sample(System.currentTimeMillis())
  broadcast(); handler.postDelayed(this,1000)
 } }
 override fun onCreate(){ super.onCreate()
  getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL,"電量監測",NotificationManager.IMPORTANCE_LOW))
  val pending=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
  startForeground(1,Notification.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.ic_lock_idle_low_battery).setContentTitle("Battery Tracker 正在監測").setContentText("持續記錄電量與溫度").setContentIntent(pending).setOngoing(true).build())
  receiver=object:BroadcastReceiver(){override fun onReceive(context:Context?,intent:Intent?){if(intent!=null) readBattery(intent)}}
  registerReceiver(receiver,IntentFilter(Intent.ACTION_BATTERY_CHANGED))
  handler.post(tick)
 }
 override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int { when(intent?.action){
  "STOP"->{stopSession("監測停止");stopSelf()}
  "SESSION"->{ val name=intent.getStringExtra("mode")?:"遊戲"; val minutes=intent.getIntExtra("minutes",30).coerceIn(1,240); beginSession(name,minutes) }
  "STOP_SESSION"->stopSession("手動結束")
 }; return START_STICKY }
 private fun readBattery(i:Intent){
  val level=i.getIntExtra(BatteryManager.EXTRA_LEVEL,-1);val scale=i.getIntExtra(BatteryManager.EXTRA_SCALE,100)
  if(level<0||scale<=0)return
  val p=(level*100.0/scale).toInt().coerceIn(0,100)
  val state=i.getIntExtra(BatteryManager.EXTRA_STATUS,-1)
  val isCharging=state==BatteryManager.BATTERY_STATUS_CHARGING||state==BatteryManager.BATTERY_STATUS_FULL
  temperature=i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE,0)/10.0
  val now=System.currentTimeMillis()
  if(lastLevel<0){lastLevel=p;lastTime=now;charging=isCharging;anchor=p;anchorTime=now;sample(now);broadcast();return}
  if(isCharging!=charging){charging=isCharging;anchor=p;anchorTime=now;direction=0;sample(now)}
  if(p!=lastLevel){
   val d=if(p>lastLevel)1 else -1
   if(direction!=d){direction=d;anchor=lastLevel;anchorTime=lastTime}
   val step=interval()
   // Only full grid-aligned intervals count. Interpolate skipped levels to estimate crossing times.
   var boundary=if(d>0)(lastLevel/step+1)*step else ((lastLevel-1)/step)*step
   while(if(d>0)boundary<=p else boundary>=p){
    val fraction=abs(boundary-lastLevel).toDouble()/abs(p-lastLevel)
    val crossing=lastTime+((now-lastTime)*fraction).toLong()
    if(anchor>=0 && abs(boundary-anchor)==step && crossing>anchorTime)segment(anchorTime,crossing,anchor,boundary,d)
    anchor=boundary;anchorTime=crossing
    boundary+=d*step
   }
   lastLevel=p;lastTime=now;sample(now)
  }
  if(endAt>0 && temperature>=42.0)stopSession("電池溫度達 42°C")
  broadcast()
 }
 private fun interval()=getSharedPreferences(PREF,MODE_PRIVATE).getInt("interval",10).coerceIn(5,20)
 private fun append(name:String,header:String,row:String){val f=File(filesDir,name);synchronized(this){if(!f.exists())f.writeText(header+"\n");f.appendText(row+"\n")}}
 private fun stamp(t:Long)=fmt.format(Date(t))
 private fun sample(t:Long){if(lastLevel<0)return;lastSampleAt=t;append("samples.csv","time,percent,charging,temp_c,mode","${stamp(t)},$lastLevel,$charging,$temperature,$mode")}
 private fun segment(start:Long,end:Long,from:Int,to:Int,d:Int){val ms=end-start;if(ms<=0)return
  val rate=abs(to-from)*3600000.0/ms
  append("segments.csv","start,end,type,from_percent,to_percent,seconds,percent_per_hour,mode","${stamp(start)},${stamp(end)},${if(d>0)"charge" else "discharge"},$from,$to,${ms/1000},${String.format(Locale.US,"%.2f",rate)},$mode")
 }
 private fun beginSession(name:String,minutes:Int){
  if(endAt>0)stopSession("切換模式")
  mode=name;sessionStart=System.currentTimeMillis();sessionStartLevel=lastLevel;endAt=sessionStart+minutes*60_000L
  // Do not assign partially measured battery intervals to a new mode.
  anchor=lastLevel;anchorTime=sessionStart;direction=0;sample(sessionStart);broadcast()
 }
 private fun stopSession(reason:String){if(endAt==0L)return
  val now=System.currentTimeMillis()
  append("sessions.csv","start,end,mode,start_percent,end_percent,seconds,reason","${stamp(sessionStart)},${stamp(now)},$mode,$sessionStartLevel,$lastLevel,${(now-sessionStart)/1000},$reason")
  endAt=0;mode="監測";anchor=lastLevel;anchorTime=now;direction=0;sample(now);broadcast()
 }
 private fun broadcast(){sendBroadcast(Intent(UPDATE).setPackage(packageName).putExtra("percent",lastLevel).putExtra("charging",charging).putExtra("temp",temperature).putExtra("mode",mode).putExtra("remaining",if(endAt>0)((endAt-System.currentTimeMillis())/1000).coerceAtLeast(0) else -1L))}
 override fun onDestroy(){stopped=true;stopSession("服務結束");handler.removeCallbacks(tick);receiver?.let{unregisterReceiver(it)};super.onDestroy()}
 override fun onBind(intent:Intent?):IBinder?=null
}
