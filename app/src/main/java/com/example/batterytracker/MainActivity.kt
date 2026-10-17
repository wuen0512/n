package com.example.batterytracker

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.*
import android.graphics.*
import android.view.*
import android.widget.*
import java.io.File
import kotlin.math.*

class MainActivity:Activity(){
 private lateinit var status:TextView;private lateinit var history:TextView;private lateinit var simulation:SimulationView;private lateinit var chart:BatteryChart
 private lateinit var modeSpinner:Spinner;private lateinit var durationSpinner:Spinner;private lateinit var intervalSpinner:Spinner
 private var receiver:BroadcastReceiver?=null;private var exportName="";private var simulating=false
 private val handler=Handler(Looper.getMainLooper())
 private val timer=object:Runnable{override fun run(){if(simulating)checkTemperatureAndTimer();handler.postDelayed(this,1000)}}
 private var finishAt=0L
 private fun text(s:String,size:Float=16f)=TextView(this).apply{text=s;textSize=size;setPadding(8,12,8,12)}
 private fun button(s:String,action:()->Unit)=Button(this).apply{text=s;setOnClickListener{action()}}
 private fun spinner(items:List<String>)=Spinner(this).apply{adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,items)}
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState)
  val outer=ScrollView(this);val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(20,18,20,28)};outer.addView(root);setContentView(outer)
  root.addView(text("Battery Tracker",28f));status=text("等待電池資料…",19f);root.addView(status)
  root.addView(button("啟動背景監測"){command("START")});root.addView(button("停止背景監測"){stopSimulation();command("STOP")})
  root.addView(text("自動情境模擬",23f));root.addView(text("模擬必須留在此畫面；離開畫面會停止負載，但電量監測仍可在背景繼續。",13f))
  modeSpinner=spinner(listOf("遊戲","社交","影片","待機"));root.addView(modeSpinner)
  durationSpinner=spinner(listOf("10","30","60","120"));root.addView(text("測試時間（分鐘）"));root.addView(durationSpinner)
  intervalSpinner=spinner(listOf("5","10","20"));root.addView(text("電量區間（%）"));root.addView(intervalSpinner)
  val prefs=getSharedPreferences(MonitorService.PREF,MODE_PRIVATE);intervalSpinner.setSelection(listOf(5,10,20).indexOf(prefs.getInt("interval",10)).coerceAtLeast(0))
  root.addView(button("儲存區間設定"){prefs.edit().putInt("interval",intervalSpinner.selectedItem.toString().toInt()).apply();Toast.makeText(this,"已設定；新區間從下一個刻度開始",Toast.LENGTH_LONG).show()})
  simulation=SimulationView(this).apply{layoutParams=LinearLayout.LayoutParams(-1,520)};root.addView(simulation)
  root.addView(button("開始自動模擬"){startSimulation()});root.addView(button("結束模擬"){stopSimulation()})
  root.addView(text("電量曲線（最近 200 筆採樣）",23f));chart=BatteryChart(this).apply{file=File(filesDir,"samples.csv");layoutParams=LinearLayout.LayoutParams(-1,330)};root.addView(chart)
  root.addView(text("區間歷史紀錄",23f));root.addView(button("重新整理"){refreshHistory()});history=text("");root.addView(history)
  root.addView(button("匯出區間紀錄 CSV"){export("segments.csv")});root.addView(button("匯出每分鐘採樣 CSV"){export("samples.csv")});root.addView(button("匯出模擬場次 CSV"){export("sessions.csv")})
  root.addView(text("說明：電量僅有系統提供的百分比精度。未完整跨越刻度的區間不會計入。遊戲、社交、影片是合成負載，影片不使用硬體影片解碼。測試時請勿覆蓋螢幕，勿在高溫環境長時間運行。",13f))
  if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),10)
  receiver=object:BroadcastReceiver(){override fun onReceive(context:Context?,intent:Intent?){if(intent==null)return
   val temp=intent.getDoubleExtra("temp",0.0)
   status.text="電量 ${intent.getIntExtra("percent",-1)}% | ${if(intent.getBooleanExtra("charging",false))"充電中" else "放電中"}\n電池溫度 ${temp}°C | ${intent.getStringExtra("mode")?:"監測"}\n剩餘時間 ${intent.getLongExtra("remaining",-1)} 秒"
   if(simulating && temp>=42.0){stopSimulation();Toast.makeText(this@MainActivity,"電池溫度過高，已停止模擬",Toast.LENGTH_LONG).show()}
  }}
  if(Build.VERSION.SDK_INT>=33)registerReceiver(receiver,IntentFilter(MonitorService.UPDATE),Context.RECEIVER_NOT_EXPORTED) else @Suppress("DEPRECATION") registerReceiver(receiver,IntentFilter(MonitorService.UPDATE))
  handler.post(timer);refreshHistory()
 }
 private fun command(action:String,mode:String="",minutes:Int=30){val i=Intent(this,MonitorService::class.java).setAction(action).putExtra("mode",mode).putExtra("minutes",minutes)
  if(action=="START"||action=="SESSION")startForegroundService(i) else startService(i)
 }
 private fun startSimulation(){
  if(simulating)stopSimulation()
  val mode=modeSpinner.selectedItem.toString();val minutes=durationSpinner.selectedItem.toString().toInt()
  getSharedPreferences(MonitorService.PREF,MODE_PRIVATE).edit().putInt("interval",intervalSpinner.selectedItem.toString().toInt()).apply()
  command("SESSION",mode,minutes);simulating=true;finishAt=SystemClock.elapsedRealtime()+minutes*60_000L
  simulation.begin(mode);window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
  if(mode=="待機")Toast.makeText(this,"待機基準模式不施加負載；為確保計時，畫面保持開啟",Toast.LENGTH_LONG).show()
 }
 private fun checkTemperatureAndTimer(){if(SystemClock.elapsedRealtime()>=finishAt)stopSimulation()}
 private fun stopSimulation(){if(!simulating)return;simulating=false;simulation.halt();window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);command("STOP_SESSION");refreshHistory()}
 private fun refreshHistory(){if(::chart.isInitialized)chart.invalidate();val f=File(filesDir,"segments.csv")
  history.text=if(!f.exists())"尚無完整電量區間。請讓電量跨越兩個連續刻度。" else f.readLines().drop(1).takeLast(40).asReversed().joinToString("\n\n"){row->val c=row.split(',');if(c.size>=8)"${c[0]}\n${c[2]}  ${c[3]}% → ${c[4]}%\n${c[5]} 秒 | ${c[6]} %/小時 | ${c[7]}" else row}
 }
 private fun export(filename:String){if(!File(filesDir,filename).exists()){Toast.makeText(this,"尚無資料",Toast.LENGTH_SHORT).show();return};exportName=filename
  val i=Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("text/csv").putExtra(Intent.EXTRA_TITLE,filename)
  @Suppress("DEPRECATION") startActivityForResult(i,101)
 }
 @Deprecated("Legacy result")override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){super.onActivityResult(requestCode,resultCode,data)
  if(requestCode==101&&resultCode==RESULT_OK)data?.data?.let{uri->contentResolver.openOutputStream(uri)?.use{out->File(filesDir,exportName).inputStream().use{it.copyTo(out)}}}
 }
 override fun onPause(){super.onPause();if(simulating)stopSimulation()}
 override fun onDestroy(){handler.removeCallbacks(timer);receiver?.let{unregisterReceiver(it)};super.onDestroy()}
}
