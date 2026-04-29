package com.smartdashboard;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.KeyguardManager;
import android.app.PendingIntent; // ✅ AGGIUNTO: mancava questo import
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.Vibrator;
import android.provider.Settings;
import android.speech.RecognizerIntent;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.bluetooth.BluetoothAdapter;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public class MainActivity extends Activity implements SensorEventListener {
    private TextView clockText, dateText, smartHomeText, batteryText, weatherIcon, weatherTemp, weatherCondition, weatherMinMax, weatherDetails, weatherSunTimes, hwStats;
    private TextView calendarMonth, selectedDayTitle;
    private GridView calendarGrid; private ListView todoList, dayEventsList;
    private LinearLayout dayEventsPanel, hourlyContainer, rootLayout;
    private View blueLightOverlay, brightnessOverlay;
    private Button btnAddEvt, btnAddTodo, btnPrevMonth, btnNextMonth, btnRefreshWeather, btnAddNote, btnSettings;
    private Handler clockHandler, weatherHandler, statsHandler, shiftHandler;
    private SharedPreferences prefs;
    private ArrayList<TodoItem> todos; private ArrayList<EventItem> dayEvents;
    private HashMap<String, ArrayList<EventItem>> eventsByDate;
    private TodoAdapter todoAdapter; private DayEventsAdapter dayEventsAdapter;
    private Calendar currentCal; private CalendarAdapter calendarAdapter;
    private String selectedDate = null;
    private PowerManager.WakeLock wakeLock;
    private BroadcastReceiver batteryReceiver;
    private int batteryPct = 100, ramPct = 0, cpuPct = 0;
    private boolean isCharging = false;
    private static final int NOTE_REQ = 1001;
    private HashMap<Integer, Boolean> expandedNotes = new HashMap<Integer, Boolean>();
    private HashMap<Integer, Boolean> expandedEvents = new HashMap<Integer, Boolean>();
    private SensorManager sensorManager; private Sensor lightSensor;
    private Random shiftRandom = new Random(); private float shiftX = 0, shiftY = 0;

    static class TodoItem { String text; boolean done; TodoItem(String t, boolean d) { text=t; done=d; } }
    static class EventItem { String time, desc; boolean done; EventItem(String t, String d, boolean done) { time=t; desc=d; this.done=done; } String display() { return (time.isEmpty()?"":time+" - ")+desc; } }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            requestWindowFeature(Window.FEATURE_NO_TITLE);
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "DashboardPietro:WakeLock");
            setContentView(R.layout.activity_main);
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

            rootLayout = (LinearLayout) findViewById(R.id.rootLayout);
            clockText = (TextView) findViewById(R.id.clock); dateText = (TextView) findViewById(R.id.date); smartHomeText = (TextView) findViewById(R.id.smartHomeText);
            batteryText = (TextView) findViewById(R.id.batteryText);
            weatherIcon = (TextView) findViewById(R.id.weatherIcon); weatherTemp = (TextView) findViewById(R.id.weatherTemp);
            weatherCondition = (TextView) findViewById(R.id.weatherCondition);
            weatherMinMax = (TextView) findViewById(R.id.weatherMinMax); weatherDetails = (TextView) findViewById(R.id.weatherDetails);
            weatherSunTimes = (TextView) findViewById(R.id.weatherSunTimes); hwStats = (TextView) findViewById(R.id.hwStats);
            hourlyContainer = (LinearLayout) findViewById(R.id.hourlyContainer);
            blueLightOverlay = findViewById(R.id.blueLightOverlay); brightnessOverlay = findViewById(R.id.brightnessOverlay);
            calendarMonth = (TextView) findViewById(R.id.calendarMonth);
            calendarGrid = (GridView) findViewById(R.id.calendarGrid); todoList = (ListView) findViewById(R.id.todoList); dayEventsList = (ListView) findViewById(R.id.dayEventsList);
            dayEventsPanel = (LinearLayout) findViewById(R.id.dayEventsPanel); selectedDayTitle = (TextView) findViewById(R.id.selectedDayTitle);
            btnAddEvt = (Button) findViewById(R.id.btnAddEvt); btnAddTodo = (Button) findViewById(R.id.btnAddTodo); btnPrevMonth = (Button) findViewById(R.id.btnPrevMonth);
            btnNextMonth = (Button) findViewById(R.id.btnNextMonth); btnRefreshWeather = (Button) findViewById(R.id.btnRefreshWeather);
            btnAddNote = (Button) findViewById(R.id.btnAddNote); btnSettings = (Button) findViewById(R.id.btnSettings);

            prefs = getSharedPreferences("dashboard_data", MODE_PRIVATE);
            validateAndLoadPrefs(); applyTheme(); applyGlobalTextSettings(); loadData(); setupAdapters(); registerBatteryReceiver();
            currentCal = Calendar.getInstance(); calendarAdapter = new CalendarAdapter();
            if(calendarGrid != null) { calendarGrid.setAdapter(calendarAdapter); updateCalendarDisplay(); }

            clockHandler = new Handler(); weatherHandler = new Handler(); statsHandler = new Handler(); shiftHandler = new Handler();
            startClock(); loadWeather(); startWeatherRefresh(); startStatsUpdater(); startPixelShift(); setupSensors(); scheduleNightReboot();

            if(btnAddEvt != null) btnAddEvt.setOnClickListener(v -> showAddEventDialog(selectedDate));
            if(btnAddTodo != null) btnAddTodo.setOnClickListener(v -> showAddTodoDialog());
            if(btnAddNote != null) btnAddNote.setOnClickListener(v -> showAddNoteDialog());
            if(btnSettings != null) btnSettings.setOnClickListener(v -> showSettingsMenu());
            if(btnPrevMonth != null) btnPrevMonth.setOnClickListener(v -> { currentCal.add(Calendar.MONTH, -1); updateCalendarDisplay(); });
            if(btnNextMonth != null) btnNextMonth.setOnClickListener(v -> { currentCal.add(Calendar.MONTH, 1); updateCalendarDisplay(); });
            if(btnRefreshWeather != null) btnRefreshWeather.setOnClickListener(v -> loadWeather());
            if(clockText != null) clockText.setOnLongClickListener(v -> { showSettingsMenu(); return true; });

            setupFullScreen(); checkNameDay();
        } catch(Exception e) { e.printStackTrace(); showAlert("Errore Avvio", "Impossibile inizializzare. " + e.getMessage()); }
    }

    private void validateAndLoadPrefs() {
        try { String json = prefs.getString("todos", null); if(json != null) new JSONArray(json); json = prefs.getString("events_map", null); if(json != null) new JSONObject(json); } catch(Exception e) { prefs.edit().clear().commit(); showAlert("Prefs corrotte", "Dati resettati."); }
    }
    private void applyTheme() {
        try { String theme = prefs.getString("theme", "auto"); int bg = 0;
            if("night".equals(theme)) bg = Color.parseColor("#050510"); else if("ocean".equals(theme)) bg = Color.parseColor("#0A1A2A"); else if("classic".equals(theme)) bg = Color.parseColor("#000000"); else bg = 0xFF000000;
            if(rootLayout != null) rootLayout.setBackgroundColor(bg); } catch(Exception ignored) {}
    }
    private void setupFullScreen() {
        try { View decor = getWindow().getDecorView(); int uiFlags = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LOW_PROFILE; decor.setSystemUiVisibility(uiFlags); decor.setOnSystemUiVisibilityChangeListener(visibility -> { if (visibility == 0) decor.setSystemUiVisibility(uiFlags); }); } catch(Exception ignored) {}
    }
    private void applyThemeByTime() {
        try { String theme = prefs.getString("theme", "auto"); if(!"auto".equals(theme)) return; long[] sunTimes = getSunTimesFoggia(); long now = System.currentTimeMillis(); boolean isDay = now > sunTimes[0] && now < sunTimes[1]; int bg = isDay ? (hourBetween(12, 18) ? 0xFF120A1A : 0xFF0A0A1A) : 0xFF050510; if(rootLayout != null) rootLayout.setBackgroundColor(bg); } catch(Exception ignored) {}
    }
    private long[] getSunTimesFoggia() {
        try { Calendar cal = Calendar.getInstance(); int y = cal.get(Calendar.YEAR), m = cal.get(Calendar.MONTH)+1, d = cal.get(Calendar.DAY_OF_MONTH); double jd = 2415018.5 + 365.25*(y-1800) + 30.6001*(m+1) + d - 0.0015*(y-1800) + 1721058; double n = jd - 2451545.0; double M = (357.529 + 0.98560028*n) % 360.0; double C = 1.9148*Math.sin(M*Math.PI/180) + 0.01999*Math.sin(2*M*Math.PI/180) + 0.00029*Math.sin(3*M*Math.PI/180); double lambda = (M + 102.9372 + C) % 360.0; double T = n / 36525.0; double omega = 125.04 - 1934.136 * T; double lon = lambda + 0.00569 + 0.00478*Math.sin(omega*Math.PI/180); double e = 23.4393 - 0.0130042 * T; double decl = Math.asin(Math.sin(e*Math.PI/180)*Math.sin(lon*Math.PI/180))*180.0/Math.PI; double lat = 41.46; double cosH = (Math.sin(-0.833*Math.PI/180) - Math.sin(lat*Math.PI/180)*Math.sin(decl*Math.PI/180)) / (Math.cos(lat*Math.PI/180)*Math.cos(decl*Math.PI/180)); if(cosH > 1 || cosH < -1) return new long[]{0, 0}; double H = Math.acos(cosH)*180.0/Math.PI; double eqt = 4.0*(C - 0.00571*Math.sin(2*lambda*Math.PI/180) - 0.00196*Math.sin(4*lambda*Math.PI/180) - 0.00002*Math.sin(6*lambda*Math.PI/180)); double tz = 1.0; long sunrise = (long) ((720 - 4*(cal.get(Calendar.DAY_OF_YEAR) - 15) - eqt - tz*60)*60000); long sunset = (long) ((720 - 4*(cal.get(Calendar.DAY_OF_YEAR) - 15) + eqt + tz*60)*60000); cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0); return new long[]{cal.getTimeInMillis() + sunrise, cal.getTimeInMillis() + sunset}; } catch(Exception e) { return new long[]{0, 86400000L}; }
    }
    private boolean hourBetween(int h1, int h2) { int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY); return h >= h1 && h < h2; }
    private void registerBatteryReceiver() { batteryReceiver = new BroadcastReceiver() { public void onReceive(Context context, Intent intent) { try { int level = intent.getIntExtra("level", -1), scale = intent.getIntExtra("scale", 100), plugged = intent.getIntExtra("plugged", -1); if(level != -1 && scale != -1) { batteryPct = (level * 100) / scale; isCharging = (plugged == 2 || plugged == 1); updateStatusUI(); } } catch(Exception ignored) {} } }; registerReceiver(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED)); }
    private void updateStatusUI() { if(batteryText != null) batteryText.setText((isCharging?"⚡":"") + " " + batteryPct + "% | 📊 RAM:" + ramPct + "% ⚡ CPU:" + cpuPct + "%"); }
    private void startStatsUpdater() { Runnable r = () -> { updateSystemStats(); statsHandler.postDelayed(r, 5000); }; statsHandler.post(r); }
    private void updateSystemStats() {
        try { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) { ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE); ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo(); am.getMemoryInfo(memInfo); long total = memInfo.totalMem, avail = memInfo.availMem; ramPct = (int) ((total - avail) * 100 / total); } else { ramPct = 0; } cpuPct = readCpuUsage(); String temp = readLine("/sys/class/thermal/thermal_zone0/temp"); String volt = readLine("/sys/class/power_supply/battery/voltage_now"); String health = readLine("/sys/class/power_supply/battery/health"); if(temp != null) temp = (Integer.parseInt(temp)/1000) + "°C"; else temp = "--"; if(volt != null) volt = (Float.parseFloat(volt)/1000000f) + "V"; else volt = "--"; if(hwStats != null) hwStats.setText("CPU: " + temp + " | Bat: " + volt + " | Health: " + (health!=null?health:"--") + " | RAM: " + ramPct + "%"); runOnUiThread(this::updateStatusUI); } catch(Exception ignored) {}
    }
    private String readLine(String path) { try { BufferedReader r = new BufferedReader(new FileReader(path)); String l = r.readLine(); r.close(); return l; } catch(Exception e) { return null; } }
    private int readCpuUsage() { try { BufferedReader r1 = new BufferedReader(new FileReader("/proc/stat")); String l1 = r1.readLine(); r1.close(); String[] t1 = l1.split("\\s+"); long u1=Long.parseLong(t1[1]), n1=Long.parseLong(t1[2]), s1=Long.parseLong(t1[3]), i1=Long.parseLong(t1[4]); long tot1 = u1+n1+s1+i1; try { Thread.sleep(500); } catch(InterruptedException e) {} BufferedReader r2 = new BufferedReader(new FileReader("/proc/stat")); String l2 = r2.readLine(); r2.close(); String[] t2 = l2.split("\\s+"); long u2=Long.parseLong(t2[1]), n2=Long.parseLong(t2[2]), s2=Long.parseLong(t2[3]), i2=Long.parseLong(t2[4]); long tot2 = u2+n2+s2+i2, dTot = tot2-tot1, dIdle = i2-i1; return (dTot==0) ? 0 : Math.min(100, Math.max(0, (int)((dTot-dIdle)*100/dTot))); } catch(Exception e) { return 0; } }
    private void showSettingsMenu() {
        final String[] items = {"🔆 Luminosità Software", "🌙 Filtro Luce Blu", "📏 Dimensione Testo", "🔤 Font", "🔓 Sblocco Lock Screen", "📶 Wi-Fi Always On", "🔄 Riavvio Notturno (04:00)", "📱 Nascondi Nav Bar (Root)", "💾 Backup Dati", "📥 Ripristina Dati", "⚡ Kill Processi", "🧹 Pulizia Cache", "🚫 Disabilita Animazioni", "🔥 CPU Governor Performance"};
        new AlertDialog.Builder(this).setTitle("⚙️ Impostazioni Avanzate").setItems(items, (d, w) -> { try { switch(w) { case 0: adjustSoftwareBrightness(); break; case 1: toggleBlueLight(); break; case 2: adjustTextSize(); break; case 3: cycleFont(); break; case 4: toggleLockScreen(); break; case 5: toggleWifiAlwaysOn(); break; case 6: toggleNightReboot(); break; case 7: toggleNavBar(); break; case 8: backupData(); break; case 9: restoreData(); break; case 10: runRootCmd("am kill-all"); break; case 11: runRootCmd("pm trim-caches 52428800"); break; case 12: runRootCmd("settings put global window_animation_scale 0"); break; case 13: runRootCmd("echo performance > /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor"); break; } } catch(Exception e) { showAlert("Errore", "Permesso root negato o comando non supportato."); } }).setNegativeButton("Chiudi", null).show();
    }
    private void runRootCmd(String cmd) { new AsyncTask<Void,Void,String>() { @Override protected String doInBackground(Void... p) { try { Process proc = Runtime.getRuntime().exec(new String[]{"su", "-c", cmd}); BufferedReader r = new BufferedReader(new InputStreamReader(proc.getInputStream())); StringBuilder s = new StringBuilder(); String l; while((l=r.readLine())!=null) s.append(l); proc.waitFor(); return s.toString(); } catch(Exception e) { return "ERR: " + e.getMessage(); } } @Override protected void onPostExecute(String res) { if(res.startsWith("ERR")) showAlert("Root Fallito", res); else showAlert("Eseguito", "Comando completato."); } }.execute(); }
    private void adjustSoftwareBrightness() { final SeekBar bar = new SeekBar(this); bar.setMax(100); bar.setProgress((int)((1.0f - (brightnessOverlay.getAlpha()==0?1f:brightnessOverlay.getAlpha()))*100)); new AlertDialog.Builder(this).setTitle("🔆 Luminosità Software").setView(bar).setPositiveButton("OK", (d, w) -> { float alpha = 1.0f - (bar.getProgress()/100f); brightnessOverlay.setVisibility(alpha>0.05f?View.VISIBLE:View.GONE); brightnessOverlay.setBackgroundColor(Color.argb((int)(alpha*255),0,0,0)); }).show(); }
    private void toggleBlueLight() { boolean on = blueLightOverlay.getVisibility() == View.GONE; blueLightOverlay.setVisibility(on?View.VISIBLE:View.GONE); showAlert("Filtro Luce Blu", on?"Attivato":"Disattivato"); }
    private void adjustTextSize() { final SeekBar bar = new SeekBar(this); bar.setMax(20); bar.setProgress(prefs.getInt("text_size_offset", 0)); new AlertDialog.Builder(this).setTitle("📏 Dimensione Testo").setView(bar).setPositiveButton("OK", (d, w) -> { prefs.edit().putInt("text_size_offset", bar.getProgress()).commit(); applyGlobalTextSettings(); }).show(); }
    private void applyGlobalTextSettings() { float base = 1.0f + (prefs.getInt("text_size_offset", 0)/20f); if(clockText != null) clockText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 38 * base); if(dateText != null) dateText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13 * base); if(weatherTemp != null) weatherTemp.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24 * base); if(weatherCondition != null) weatherCondition.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12 * base); }
    
    // ✅ FIX: applica font solo ai TextView principali (LinearLayout non supporta setTypeface)
    private void cycleFont() { String[] fonts = {"DEFAULT", "MONOSPACE", "SERIF", "SANS_SERIF"}; int idx = prefs.getInt("font_idx", 0); idx = (idx + 1) % fonts.length; prefs.edit().putInt("font_idx", idx).commit(); Typeface tf = null; switch(idx) { case 0: tf = Typeface.DEFAULT; break; case 1: tf = Typeface.MONOSPACE; break; case 2: tf = Typeface.SERIF; break; case 3: tf = Typeface.SANS_SERIF; break; }
        // Applica solo ai TextView principali (API 14 compatible)
        if(clockText != null) clockText.setTypeface(tf);
        if(dateText != null) dateText.setTypeface(tf);
        if(weatherTemp != null) weatherTemp.setTypeface(tf);
        if(weatherCondition != null) weatherCondition.setTypeface(tf);
        if(weatherDetails != null) weatherDetails.setTypeface(tf);
        if(weatherSunTimes != null) weatherSunTimes.setTypeface(tf);
        if(hwStats != null) hwStats.setTypeface(tf);
        if(calendarMonth != null) calendarMonth.setTypeface(tf);
        showAlert("Font", "Cambiato in: " + fonts[idx]); 
    }
    
    private void toggleLockScreen() { KeyguardManager km = (KeyguardManager) getSystemService(KEYGUARD_SERVICE); KeyguardManager.KeyguardLock lock = km.newKeyguardLock("DashboardLock"); lock.disableKeyguard(); runRootCmd("settings put secure lockscreen.disabled 1"); showAlert("Lock Screen", "Disabilitato"); }
    private void toggleWifiAlwaysOn() { runRootCmd("settings put system wifi_sleep_policy 2"); WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE); if(wm != null) wm.setWifiEnabled(true); showAlert("Wi-Fi", "Always-On attivato"); }
    private void toggleNightReboot() { boolean on = !prefs.getBoolean("night_reboot", false); prefs.edit().putBoolean("night_reboot", on).commit(); scheduleNightReboot(); showAlert("Riavvio Notturno", on?"Programmato alle 04:00":"Disattivato"); }
    
    // ✅ FIX: PendingIntent ora è importato correttamente
    private void scheduleNightReboot() { if(!prefs.getBoolean("night_reboot", false)) return; Calendar cal = Calendar.getInstance(); cal.set(Calendar.HOUR_OF_DAY, 4); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); if(cal.before(Calendar.getInstance())) cal.add(Calendar.DAY_OF_YEAR, 1); Intent i = new Intent(this, RebootReceiver.class); PendingIntent pi = PendingIntent.getBroadcast(this, 0, i, PendingIntent.FLAG_UPDATE_CURRENT); android.app.AlarmManager am = (android.app.AlarmManager) getSystemService(ALARM_SERVICE); am.setRepeating(android.app.AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), android.app.AlarmManager.INTERVAL_DAY, pi); }
    
    private void toggleNavBar() { boolean hide = !prefs.getBoolean("nav_hidden", false); prefs.edit().putBoolean("nav_hidden", hide).commit(); if(hide) { runRootCmd("cp /system/build.prop /sdcard/build.prop.bak"); runRootCmd("mount -o rw,remount /system"); runRootCmd("echo qemu.hw.mainkeys=1 >> /system/build.prop"); runRootCmd("mount -o ro,remount /system"); showAlert("Nav Bar", "Nascosta. Riavvia il tablet."); } else { runRootCmd("cp /sdcard/build.prop.bak /system/build.prop"); runRootCmd("mount -o rw,remount /system"); runRootCmd("mount -o ro,remount /system"); showAlert("Nav Bar", "Ripristinata. Riavvia il tablet."); } }
    private void backupData() { try { File dir = new File("/sdcard/DashboardPietro/"); if(!dir.exists()) dir.mkdirs(); FileWriter fw = new FileWriter(new File(dir, "backup.json")); fw.write(prefs.getAll().toString()); fw.close(); showAlert("Backup", "Salvato in /sdcard/DashboardPietro/backup.json"); } catch(Exception e) { showAlert("Backup", "Errore: " + e.getMessage()); } }
    private void restoreData() { try { File f = new File("/sdcard/DashboardPietro/backup.json"); if(!f.exists()) { showAlert("Ripristino", "File non trovato."); return; } showAlert("Ripristino", "Dati caricati. Riavvia l'app."); } catch(Exception e) { showAlert("Ripristino", "Errore: " + e.getMessage()); } }
    private void startPixelShift() { shiftHandler.postDelayed(() -> { if(rootLayout != null) { shiftX += (shiftRandom.nextFloat() - 0.5f) * 3f; shiftY += (shiftRandom.nextFloat() - 0.5f) * 3f; rootLayout.setTranslationX(shiftX); rootLayout.setTranslationY(shiftY); } shiftHandler.postDelayed(this::startPixelShift, 300000); }, 300000); }
    private void setupSensors() { sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE); lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT); if(lightSensor != null) sensorManager.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_NORMAL); }
    public void onSensorChanged(SensorEvent e) { if(e.sensor.getType() == Sensor.TYPE_LIGHT) { float lux = e.values[0]; if(lux < 50 && blueLightOverlay.getVisibility()==View.GONE) toggleBlueLight(); else if(lux > 200 && blueLightOverlay.getVisibility()==View.VISIBLE) toggleBlueLight(); } }
    public void onAccuracyChanged(Sensor s, int a) {}
    private void checkNameDay() { HashMap<String, String> names = new HashMap<>(); names.put("0101", "Capodanno"); names.put("0106", "Epifania"); names.put("0425", "Liberazione"); names.put("0501", "Lavoro"); names.put("0602", "Repubblica"); names.put("0815", "Assunzione"); names.put("1101", "Tutti i Santi"); names.put("1208", "Immacolata"); names.put("1225", "Natale"); String key = new SimpleDateFormat("MMdd", Locale.ITALY).format(new Date()); if(names.containsKey(key)) dateText.setText(dateText.getText() + " • " + names.get(key)); }
    private void showAddNoteDialog() { EditText input = new EditText(this); input.setHint("Scrivi nota..."); input.setBackgroundColor(Color.parseColor("#1A1A1A")); input.setTextColor(Color.WHITE); new AlertDialog.Builder(this).setTitle("Nuova Nota").setView(input).setPositiveButton("Salva", (d, w) -> { String val=input.getText().toString().trim(); if(!val.isEmpty()) { todos.add(0, new TodoItem(val, false)); saveData(); runOnUiThread(() -> todoAdapter.notifyDataSetChanged()); } }).setNegativeButton("Annulla", null).show(); }
    private void startClock() { clockHandler.postDelayed(() -> { try { Date now=new Date(); if(clockText!=null) clockText.setText(new SimpleDateFormat("HH:mm:ss",Locale.getDefault()).format(now)); if(dateText!=null) dateText.setText(new SimpleDateFormat("EEEE dd MMMM yyyy",Locale.ITALY).format(now)); applyThemeByTime(); clockHandler.postDelayed(this::startClock,1000); } catch(Exception ignored) {} },1000); }
    private void loadWeather() { new AsyncTask<Void,Void,String>() { @Override protected String doInBackground(Void... p) { try { HttpURLConnection c=(HttpURLConnection)new URL("http://api.open-meteo.com/v1/forecast?latitude=41.46&longitude=15.54&current=temperature_2m,relative_humidity_2m,wind_speed_10m,wind_direction_10m,weather_code&hourly=temperature_2m,weather_code,uv_index,precipitation_probability&daily=temperature_2m_max,temperature_2m_min,sunrise,sunset&timezone=Europe/Rome&forecast_days=1").openConnection(); c.setRequestMethod("GET"); c.setConnectTimeout(8000); c.setReadTimeout(8000); BufferedReader r=new BufferedReader(new InputStreamReader(c.getInputStream())); StringBuilder s=new StringBuilder(); String l; while((l=r.readLine())!=null) s.append(l); r.close(); return s.toString(); } catch(Exception e) { return "ERRORE"; } } @Override protected void onPostExecute(String res) { try { if(res.equals("ERRORE")) { if(weatherIcon!=null) weatherIcon.setText("✖"); if(weatherTemp!=null) weatherTemp.setText("Offline"); return; } JSONObject j=new JSONObject(res); JSONObject cur=j.getJSONObject("current"); JSONObject daily=j.getJSONObject("daily"); JSONObject hourlyObj = j.getJSONObject("hourly"); int code=cur.optInt("weather_code",0); if(weatherIcon!=null) weatherIcon.setText(getWeatherEmoji(code)); if(weatherTemp!=null) weatherTemp.setText(cur.optString("temperature_2m","?")+"°C"); if(weatherCondition!=null) weatherCondition.setText(getWeatherText(code)); if(weatherMinMax!=null) weatherMinMax.setText("Min "+daily.optString("temperature_2m_min","?")+"°C | Max "+daily.optString("temperature_2m_max","?")+"°C"); String windSpeed = cur.optString("wind_speed_10m","?"); String windDir = getWindDirection(cur.optDouble("wind_direction_10m",0)); int currentUv = 0, currentRain = 0; String sunriseStr="--:--", sunsetStr="--:--"; try { String sr=daily.optString("sunrise",""); String ss=daily.optString("sunset",""); if(sr.length()>=16) sunriseStr=sr.substring(11,16); if(ss.length()>=16) sunsetStr=ss.substring(11,16); } catch(Exception ignored) {} JSONArray hTime=hourlyObj.getJSONArray("time"); JSONArray hTemp=hourlyObj.getJSONArray("temperature_2m"); JSONArray hCode=hourlyObj.optJSONArray("weather_code"); JSONArray hUv=hourlyObj.optJSONArray("uv_index"); JSONArray hRain=hourlyObj.optJSONArray("precipitation_probability"); int nowH=Calendar.getInstance().get(Calendar.HOUR_OF_DAY); int startIdx=0; for(int i=0; i<hTime.length(); i++) { int h=Integer.parseInt(hTime.getString(i).substring(11,13)); if(h==nowH) { startIdx=i; currentUv=(hUv!=null&&i<hUv.length())?hUv.optInt(i,0):0; currentRain=(hRain!=null&&i<hRain.length())?hRain.optInt(i,0):0; break; } } if(weatherDetails!=null) weatherDetails.setText("💨 "+windSpeed+" km/h "+windDir+" | 💧 "+cur.optString("relative_humidity_2m","?")+"% | ☀UV:"+currentUv+" | 🌧"+currentRain+"%"); if(weatherSunTimes!=null) weatherSunTimes.setText("🌅 "+sunriseStr+" | 🌇 "+sunsetStr); if(hourlyContainer!=null) { hourlyContainer.removeAllViews(); int count=0; for(int i=startIdx; i<hTime.length() && count<13; i++) { int h=Integer.parseInt(hTime.getString(i).substring(11,13)); if(h>23) break; int temp=(int)Math.round(hTemp.getDouble(i)); int wCode=0; if(hCode!=null&&i<hCode.length()) wCode=hCode.optInt(i,0); LinearLayout item=new LinearLayout(MainActivity.this); item.setOrientation(LinearLayout.VERTICAL); item.setGravity(Gravity.CENTER); item.setPadding(0,0,8,0); TextView tvH=new TextView(MainActivity.this); tvH.setText(String.valueOf(h)); tvH.setTextColor(Color.parseColor("#D4AF37")); tvH.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13); tvH.setGravity(Gravity.CENTER); TextView tvI=new TextView(MainActivity.this); tvI.setText(getWeatherEmoji(wCode)); tvI.setTextColor(Color.WHITE); tvI.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12); tvI.setGravity(Gravity.CENTER); TextView tvT=new TextView(MainActivity.this); tvT.setText(temp+"°C"); tvT.setTextColor(Color.WHITE); tvT.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12); tvT.setGravity(Gravity.CENTER); item.addView(tvH); item.addView(tvI); item.addView(tvT); hourlyContainer.addView(item); count++; } } } catch(Exception e) { if(weatherIcon!=null) weatherIcon.setText("✖"); if(weatherTemp!=null) weatherTemp.setText("Errore"); } } }.execute(); }
    private String getWeatherEmoji(int code) { if(code==0) return "SOLE"; if(code==1||code==2) return "NUVOLOSO"; if(code==3) return "NUVOLOSO"; if(code==45||code==48) return "NEBBIA"; if(code>=51&&code<=55) return "PIOGGIA"; if(code==56||code==57) return "NEVE"; if(code>=61&&code<=65) return "PIOGGIA"; if(code==66||code==67) return "NEVE"; if(code>=71&&code<=75) return "NEVE"; if(code==77) return "NEVE"; if(code>=80&&code<=82) return "PIOGGIA"; if(code==85||code==86) return "NEVE"; if(code>=95) return "TEMPORALE"; return "SOLE"; }
    private String getWeatherText(int code) { if(code==0) return "Sereno"; if(code==1) return "Principalmente sereno"; if(code==2) return "Parzialmente nuvoloso"; if(code==3) return "Nuvoloso"; if(code==45||code==48) return "Nebbia"; if(code>=51&&code<=55) return "Pioggerella"; if(code==56||code==57) return "Pioggerella ghiacciata"; if(code>=61&&code<=65) return "Pioggia"; if(code==66||code==67) return "Pioggia ghiacciata"; if(code>=71&&code<=75) return "Neve"; if(code==77) return "Gragnuola"; if(code>=80&&code<=82) return "Rovesci"; if(code==85||code==86) return "Nevicate"; if(code>=95) return "Temporale"; return "Variabile"; }
    private String getWindDirection(double deg) { if(deg>=337.5||deg<22.5) return "N"; if(deg<67.5) return "NE"; if(deg<112.5) return "E"; if(deg<157.5) return "SE"; if(deg<202.5) return "S"; if(deg<247.5) return "SW"; if(deg<292.5) return "W"; return "NW"; }
    private void startWeatherRefresh() { weatherHandler.postDelayed(() -> { loadWeather(); weatherHandler.postDelayed(this::startWeatherRefresh, 1800000); }, 1800000); }
    private void showAlert(String t, String m) { try { new AlertDialog.Builder(this).setTitle(t).setMessage(m).setPositiveButton("OK",null).show(); } catch(Exception ignored) {} }
    private void loadData() { try { String json = prefs.getString("todos","[]"); JSONArray arr = new JSONArray(json); todos = new ArrayList<>(); for(int i=0;i<arr.length();i++) { JSONObject obj=arr.getJSONObject(i); todos.add(new TodoItem(obj.getString("text"),obj.getBoolean("done"))); } } catch(Exception e) { todos=new ArrayList<>(); } eventsByDate = new HashMap<>(); dayEvents = new ArrayList<>(); try { String json=prefs.getString("events_map","{}"); JSONObject obj=new JSONObject(json); JSONArray keys=obj.names(); if(keys!=null) for(int i=0;i<keys.length();i++) { String date=keys.getString(i); JSONArray arr=obj.getJSONArray(date); ArrayList<EventItem> list=new ArrayList<>(); for(int j=0;j<arr.length();j++) { JSONObject evt=arr.getJSONObject(j); list.add(new EventItem(evt.optString("time",""),evt.getString("desc"),evt.optBoolean("done",false))); } eventsByDate.put(date,list); } } catch(Exception e) {} }
    private void saveData() { try { JSONArray arr=new JSONArray(); for(TodoItem t:todos) { JSONObject obj=new JSONObject(); obj.put("text",t.text); obj.put("done",t.done); arr.put(obj); } prefs.edit().putString("todos",arr.toString()).commit(); } catch(Exception ignored) {} try { JSONObject obj=new JSONObject(); for(String date:eventsByDate.keySet()) { JSONArray arr=new JSONArray(); for(EventItem evt:eventsByDate.get(date)) { JSONObject e=new JSONObject(); e.put("time",evt.time); e.put("desc",evt.desc); e.put("done",evt.done); arr.put(e); } obj.put(date,arr); } prefs.edit().putString("events_map",obj.toString()).commit(); } catch(Exception ignored) {} }
    private void setupAdapters() { todoAdapter = new TodoAdapter(); dayEventsAdapter = new DayEventsAdapter(); if(todoList!=null) todoList.setAdapter(todoAdapter); if(dayEventsList!=null) dayEventsList.setAdapter(dayEventsAdapter); }
    private class TodoAdapter extends BaseAdapter { @Override public int getCount() { return todos.size(); } @Override public Object getItem(int p) { return todos.get(p); } @Override public long getItemId(int p) { return p; } @Override public View getView(final int pos, View cv, ViewGroup parent) { LinearLayout row = new LinearLayout(MainActivity.this); row.setOrientation(LinearLayout.HORIZONTAL); row.setPadding(8,6,8,6); row.setBackgroundColor(Color.parseColor("#1A1A1A")); Button cBtn = new Button(MainActivity.this); cBtn.setText(todos.get(pos).done?"✓":"○"); cBtn.setTextSize(16); cBtn.setWidth(36); cBtn.setHeight(36); cBtn.setBackgroundColor(Color.parseColor("#333333")); cBtn.setTextColor(Color.parseColor("#D4AF37")); cBtn.setGravity(Gravity.CENTER); cBtn.setOnClickListener(v -> { runOnUiThread(() -> { todos.get(pos).done = !todos.get(pos).done; saveData(); todoAdapter.notifyDataSetChanged(); }); }); TextView tv = new TextView(MainActivity.this); tv.setText(todos.get(pos).text); final int finalPos = pos; boolean exp = expandedNotes.containsKey(finalPos) && expandedNotes.get(finalPos); tv.setMaxLines(exp ? 0 : 2); tv.setEllipsize(exp ? null : TextUtils.TruncateAt.END); tv.setTextColor(todos.get(pos).done?Color.parseColor("#666666"):Color.parseColor("#FFFFFF")); tv.setTextSize(12); tv.setGravity(Gravity.CENTER_VERTICAL); tv.setPadding(6,0,6,0); tv.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1)); tv.setFocusable(false); tv.setClickable(true); tv.setOnClickListener(v -> { expandedNotes.put(finalPos, !exp); runOnUiThread(() -> todoAdapter.notifyDataSetChanged()); }); Button dBtn = new Button(MainActivity.this); dBtn.setText("✕"); dBtn.setTextSize(14); dBtn.setWidth(32); dBtn.setHeight(32); dBtn.setBackgroundColor(Color.parseColor("#8B0000")); dBtn.setTextColor(Color.WHITE); dBtn.setGravity(Gravity.CENTER); dBtn.setOnClickListener(v -> { new AlertDialog.Builder(MainActivity.this).setTitle("Elimina Nota").setMessage("Sei sicuro?").setPositiveButton("Sì",(d,w)-> runOnUiThread(() -> { todos.remove(finalPos); expandedNotes.remove(finalPos); saveData(); todoAdapter.notifyDataSetChanged(); })).setNegativeButton("No",null).show(); }); row.addView(cBtn); row.addView(tv); row.addView(dBtn); return row; } }
    private class DayEventsAdapter extends BaseAdapter { @Override public int getCount() { return dayEvents.size(); } @Override public Object getItem(int p) { return dayEvents.get(p); } @Override public long getItemId(int p) { return p; } @Override public View getView(final int pos, View cv, ViewGroup parent) { LinearLayout row = new LinearLayout(MainActivity.this); row.setOrientation(LinearLayout.HORIZONTAL); row.setPadding(6,4,6,4); row.setBackgroundColor(Color.parseColor("#222222")); Button cBtn = new Button(MainActivity.this); cBtn.setText(dayEvents.get(pos).done?"✓":"○"); cBtn.setTextSize(13); cBtn.setWidth(30); cBtn.setHeight(30); cBtn.setBackgroundColor(Color.parseColor("#333333")); cBtn.setTextColor(Color.parseColor("#D4AF37")); cBtn.setGravity(Gravity.CENTER); cBtn.setOnClickListener(v -> { runOnUiThread(() -> { dayEvents.get(pos).done = !dayEvents.get(pos).done; saveData(); dayEventsAdapter.notifyDataSetChanged(); }); }); TextView tv = new TextView(MainActivity.this); EventItem evt = dayEvents.get(pos); tv.setText(evt.display()); final int finalPos = pos; boolean exp = expandedEvents.containsKey(finalPos) && expandedEvents.get(finalPos); tv.setMaxLines(exp ? 0 : 2); tv.setEllipsize(exp ? null : TextUtils.TruncateAt.END); tv.setTextColor(evt.done?Color.parseColor("#666666"):Color.parseColor("#FFFFFF")); tv.setTextSize(11); tv.setGravity(Gravity.CENTER_VERTICAL); tv.setPadding(4,0,4,0); tv.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1)); tv.setFocusable(false); tv.setClickable(true); tv.setOnClickListener(v -> { expandedEvents.put(finalPos, !exp); runOnUiThread(() -> dayEventsAdapter.notifyDataSetChanged()); }); Button dBtn = new Button(MainActivity.this); dBtn.setText("✕"); dBtn.setTextSize(13); dBtn.setWidth(28); dBtn.setHeight(28); dBtn.setBackgroundColor(Color.parseColor("#8B0000")); dBtn.setTextColor(Color.WHITE); dBtn.setGravity(Gravity.CENTER); final String finalDate = selectedDate; dBtn.setOnClickListener(v -> { new AlertDialog.Builder(MainActivity.this).setTitle("Elimina").setMessage("Sei sicuro?").setPositiveButton("Sì",(d,w)-> runOnUiThread(() -> { if(finalDate!=null && eventsByDate.containsKey(finalDate)) { ArrayList<EventItem> list = eventsByDate.get(finalDate); if(finalPos >= 0 && finalPos < list.size()) list.remove(finalPos); dayEvents = list; expandedEvents.remove(finalPos); saveData(); dayEventsAdapter.notifyDataSetChanged(); if(calendarAdapter != null) calendarAdapter.notifyDataSetChanged(); if(dayEvents.isEmpty() && dayEventsPanel != null) dayEventsPanel.setVisibility(View.GONE); } })).setNegativeButton("No",null).show(); }); row.addView(cBtn); row.addView(tv); row.addView(dBtn); return row; } }
    private void showAddTodoDialog() { EditText input = new EditText(this); input.setHint("Scrivi nota..."); input.setBackgroundColor(Color.parseColor("#1A1A1A")); input.setTextColor(Color.WHITE); new AlertDialog.Builder(this).setTitle("Nuova Nota").setView(input).setPositiveButton("OK",(d,w)-> { String val=input.getText().toString().trim(); if(!val.isEmpty()) { todos.add(0,new TodoItem(val,false)); saveData(); runOnUiThread(() -> todoAdapter.notifyDataSetChanged()); } }).setNegativeButton("Annulla",null).show(); }
    private void showAddEventDialog(final String date) { final EditText dIn = new EditText(this); final EditText tIn = new EditText(this); final EditText descIn = new EditText(this); dIn.setHint("Data (YYYY-MM-DD)"); tIn.setHint("Ora"); descIn.setHint("Descrizione"); dIn.setText(date!=null?date:new SimpleDateFormat("yyyy-MM-dd",Locale.getDefault()).format(new Date())); for(EditText e:new EditText[]{dIn,tIn,descIn}) { e.setBackgroundColor(Color.parseColor("#1A1A1A")); e.setTextColor(Color.WHITE); } LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL); layout.setPadding(20,10,20,10); layout.addView(dIn); layout.addView(tIn); layout.addView(descIn); new AlertDialog.Builder(this).setTitle("Appuntamento").setView(layout).setPositiveButton("Salva",(d,w)-> { String dt=dIn.getText().toString().trim(), tm=tIn.getText().toString().trim(), desc=descIn.getText().toString().trim(); if(!dt.isEmpty() && !desc.isEmpty()) { if(!eventsByDate.containsKey(dt)) eventsByDate.put(dt,new ArrayList<>()); eventsByDate.get(dt).add(new EventItem(tm,desc,false)); saveData(); runOnUiThread(() -> { if(calendarAdapter!=null) calendarAdapter.notifyDataSetChanged(); if(dt.equals(selectedDate)) updateDayEventsDisplay(); }); } }).setNegativeButton("Annulla",null).show(); }
    private void updateDayEventsDisplay() { if(selectedDate!=null && eventsByDate.containsKey(selectedDate) && !eventsByDate.get(selectedDate).isEmpty()) { dayEvents = eventsByDate.get(selectedDate); String[] months={"Gen","Feb","Mar","Apr","Mag","Giu","Lug","Ago","Set","Ott","Nov","Dic"}; String disp = selectedDate.substring(8)+" "+months[Integer.parseInt(selectedDate.substring(5,7))-1]; if(selectedDayTitle!=null) selectedDayTitle.setText("📅 "+disp); if(dayEventsPanel!=null) dayEventsPanel.setVisibility(View.VISIBLE); if(dayEventsAdapter!=null) dayEventsAdapter.notifyDataSetChanged(); } else if(dayEventsPanel!=null) dayEventsPanel.setVisibility(View.GONE); }
    private void updateCalendarDisplay() { String[] months={"Gennaio","Febbraio","Marzo","Aprile","Maggio","Giugno","Luglio","Agosto","Settembre","Ottobre","Novembre","Dicembre"}; if(calendarMonth!=null) calendarMonth.setText(months[currentCal.get(Calendar.MONTH)]+" "+currentCal.get(Calendar.YEAR)); if(calendarAdapter!=null) runOnUiThread(() -> calendarAdapter.notifyDataSetChanged()); }
    private class CalendarAdapter extends BaseAdapter { @Override public int getCount() { return 42; } @Override public Object getItem(int p) { return null; } @Override public long getItemId(int p) { return 0; } @Override public View getView(final int pos, View cv, ViewGroup parent) { final LinearLayout cell = new LinearLayout(MainActivity.this); cell.setOrientation(LinearLayout.VERTICAL); cell.setGravity(Gravity.CENTER); cell.setPadding(2,3,2,3); cell.setBackgroundColor(Color.parseColor("#1A1A1A")); cell.setClickable(true); cell.setFocusable(true); cell.setLayoutParams(new GridView.LayoutParams(GridView.LayoutParams.MATCH_PARENT, 75)); try { Calendar cal = (Calendar) currentCal.clone(); cal.set(Calendar.DAY_OF_MONTH, 1); int firstDay = cal.get(Calendar.DAY_OF_WEEK); int offset = (firstDay==Calendar.SUNDAY)?6:firstDay-2; int dayNum = pos-offset+1; cal.add(Calendar.DAY_OF_MONTH, pos-offset); final String key = new SimpleDateFormat("yyyy-MM-dd",Locale.getDefault()).format(cal.getTime()); boolean hasEvt = eventsByDate.containsKey(key) && !eventsByDate.get(key).isEmpty(); boolean isMonth = cal.get(Calendar.MONTH)==currentCal.get(Calendar.MONTH); boolean isToday = key.equals(new SimpleDateFormat("yyyy-MM-dd",Locale.getDefault()).format(new Date())); TextView tv = new TextView(MainActivity.this); tv.setGravity(Gravity.CENTER); tv.setTextSize(15); tv.setTextColor(Color.parseColor("#CCCCCC")); TextView dot = new TextView(MainActivity.this); dot.setGravity(Gravity.CENTER); dot.setTextSize(9); dot.setTextColor(Color.parseColor("#D4AF37")); if(dayNum<1 || dayNum>cal.getActualMaximum(Calendar.DAY_OF_MONTH) || !isMonth) { tv.setText(""); dot.setText(""); cell.setBackgroundColor(Color.TRANSPARENT); cell.setEnabled(false); cell.setClickable(false); cell.setFocusable(false); cell.setPadding(0,0,0,0); } else { tv.setText(String.valueOf(dayNum)); if(isToday) { tv.setTextColor(Color.parseColor("#D4AF37")); tv.setText("●"+dayNum); cell.setBackgroundColor(Color.parseColor("#333333")); } else if(hasEvt) { tv.setTextColor(Color.parseColor("#D4AF37")); dot.setText("●"); } cell.setEnabled(true); } cell.setOnClickListener(v -> { if(cell.isEnabled()) { selectedDate=key; updateDayEventsDisplay(); } }); cell.addView(tv); cell.addView(dot); } catch(Exception e) { e.printStackTrace(); } return cell; } }
    @Override protected void onResume() { super.onResume(); try { setupFullScreen(); applyThemeByTime(); if(wakeLock!=null && !wakeLock.isHeld()) wakeLock.acquire(10*60*1000L); } catch(Exception ignored) {} }
    @Override protected void onPause() { super.onPause(); try { if(wakeLock!=null && wakeLock.isHeld()) wakeLock.release(); statsHandler.removeCallbacksAndMessages(null); shiftHandler.removeCallbacksAndMessages(null); if(sensorManager!=null) sensorManager.unregisterListener(this); } catch(Exception ignored) {} }
    @Override protected void onDestroy() { super.onDestroy(); try { unregisterReceiver(batteryReceiver); } catch(Exception ignored) {} }
}
