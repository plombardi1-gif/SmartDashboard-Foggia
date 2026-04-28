package com.smartdashboard;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
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
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;

public class MainActivity extends Activity {
    private TextView clockText, dateText, smartHomeText, batteryText, weatherIcon, weatherTemp, weatherDesc, weatherWind;
    private TextView calendarMonth, selectedDayTitle;
    private GridView calendarGrid;
    private ListView todoList, dayEventsList;
    private LinearLayout dayEventsPanel, forecastContainer;
    private Button btnAddEvt, btnAddTodo, btnPrevMonth, btnNextMonth, btnRefreshWeather;
    private Handler clockHandler, weatherHandler;
    private SharedPreferences prefs;
    private ArrayList<TodoItem> todos;
    private ArrayList<EventItem> dayEvents;
    private HashMap<String, ArrayList<EventItem>> eventsByDate;
    private TodoAdapter todoAdapter;
    private DayEventsAdapter dayEventsAdapter;
    private Calendar currentCal;
    private CalendarAdapter calendarAdapter;
    private String selectedDate = null;
    private PowerManager.WakeLock wakeLock;
    private BroadcastReceiver batteryReceiver;

    static class TodoItem { String text; boolean done; TodoItem(String t, boolean d) { text=t; done=d; } }
    static class EventItem { String time, desc; boolean done; EventItem(String t, String d, boolean done) { time=t; desc=d; this.done=done; } String display() { return (time.isEmpty()?"":time+" - ")+desc; } }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "DashboardPietro:WakeLock");
        setContentView(R.layout.activity_main);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        setupFullScreen();

        try {
            clockText = findViewById(R.id.clock); dateText = findViewById(R.id.date); smartHomeText = findViewById(R.id.smartHomeText);
            batteryText = findViewById(R.id.batteryText); weatherIcon = findViewById(R.id.weatherIcon); weatherTemp = findViewById(R.id.weatherTemp);
            weatherDesc = findViewById(R.id.weatherDesc); weatherWind = findViewById(R.id.weatherWind); calendarMonth = findViewById(R.id.calendarMonth);
            calendarGrid = findViewById(R.id.calendarGrid); todoList = findViewById(R.id.todoList); dayEventsList = findViewById(R.id.dayEventsList);
            dayEventsPanel = findViewById(R.id.dayEventsPanel); forecastContainer = findViewById(R.id.forecastContainer); selectedDayTitle = findViewById(R.id.selectedDayTitle);
            btnAddEvt = findViewById(R.id.btnAddEvt); btnAddTodo = findViewById(R.id.btnAddTodo); btnPrevMonth = findViewById(R.id.btnPrevMonth);
            btnNextMonth = findViewById(R.id.btnNextMonth); btnRefreshWeather = findViewById(R.id.btnRefreshWeather);

            prefs = getSharedPreferences("dashboard_data", MODE_PRIVATE); loadData(); setupAdapters(); registerBatteryReceiver();
            currentCal = Calendar.getInstance(); calendarAdapter = new CalendarAdapter();
            if(calendarGrid != null) { calendarGrid.setAdapter(calendarAdapter); updateCalendarDisplay(); }
            clockHandler = new Handler(Looper.getMainLooper()); weatherHandler = new Handler(Looper.getMainLooper());
            startClock(); loadWeather(); startWeatherRefresh();

            btnAddEvt.setOnClickListener(v -> showAddEventDialog(selectedDate));
            btnAddTodo.setOnClickListener(v -> showAddTodoDialog());
            btnPrevMonth.setOnClickListener(v -> { currentCal.add(Calendar.MONTH, -1); updateCalendarDisplay(); });
            btnNextMonth.setOnClickListener(v -> { currentCal.add(Calendar.MONTH, 1); updateCalendarDisplay(); });
            btnRefreshWeather.setOnClickListener(v -> loadWeather());
            if(clockText != null) clockText.setOnLongClickListener(v -> { showSettingsMenu(); return true; });
        } catch(Exception e) { e.printStackTrace(); }
    }

    private void setupFullScreen() {
        View decor = getWindow().getDecorView();
        int uiFlags = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN;
        decor.setSystemUiVisibility(uiFlags);
        decor.setOnSystemUiVisibilityChangeListener(visibility -> {
            if (visibility == 0) decor.setSystemUiVisibility(uiFlags);
        });
    }

    private void registerBatteryReceiver() {
        batteryReceiver = intent -> {
            try {
                int level = intent.getIntExtra("level", -1), scale = intent.getIntExtra("scale", 100), plugged = intent.getIntExtra("plugged", -1);
                if(level != -1 && scale != -1 && batteryText != null) batteryText.setText((plugged==2||plugged==1?"⚡":"🔋") + " " + (level*100)/scale + "%");
            } catch(Exception ignored) {}
        };
        registerReceiver(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
    }

    private void showSettingsMenu() {
        new AlertDialog.Builder(this).setTitle("⚙️ Impostazioni").setItems(new String[]{"🔋 Batteria","🔄 Rotazione","⏱️ Timeout","🔒 Blocco","📱 Launcher"},
            (d,w) -> { switch(w) { case 0: openBatteryOpt(); break; case 1: toggleRotation(); break; case 2: startActivity(new Intent(Settings.ACTION_DISPLAY_SETTINGS)); break; case 3: startActivity(new Intent(Settings.ACTION_SECURITY_SETTINGS)); break; case 4: new AlertDialog.Builder(this).setTitle("Launcher").setMessage("Premi HOME → Dashboard Pietro → Sempre").setPositiveButton("OK",null).show(); break; } }).setNegativeButton("Chiudi",null).show();
    }
    private void openBatteryOpt() { try { startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).setData(Uri.parse("package:"+getPackageName()))); } catch(Exception e) { showAlert("Batteria","Disabilita ottimizzazione per Dashboard Pietro"); } }
    private void toggleRotation() { try { if(getRequestedOrientation()==ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) { setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR); showAlert("Rotazione","Automatica"); } else { setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE); showAlert("Rotazione","Orizzontale"); } } catch(Exception e) {} }

    private void loadData() {
        try { String json = prefs.getString("todos","[]"); JSONArray arr = new JSONArray(json); todos = new ArrayList<>(); for(int i=0;i<arr.length();i++) { JSONObject obj=arr.getJSONObject(i); todos.add(new TodoItem(obj.getString("text"),obj.getBoolean("done"))); } } catch(Exception e) { todos=new ArrayList<>(); }
        eventsByDate = new HashMap<>(); dayEvents = new ArrayList<>();
        try { String json=prefs.getString("events_map","{}"); JSONObject obj=new JSONObject(json); JSONArray keys=obj.names(); if(keys!=null) for(int i=0;i<keys.length();i++) { String date=keys.getString(i); JSONArray arr=obj.getJSONArray(date); ArrayList<EventItem> list=new ArrayList<>(); for(int j=0;j<arr.length();j++) { JSONObject evt=arr.getJSONObject(j); list.add(new EventItem(evt.optString("time",""),evt.getString("desc"),evt.optBoolean("done",false))); } eventsByDate.put(date,list); } } catch(Exception e) {}
    }

    private void saveData() {
        try { JSONArray arr=new JSONArray(); for(TodoItem t:todos) { JSONObject obj=new JSONObject(); obj.put("text",t.text); obj.put("done",t.done); arr.put(obj); } prefs.edit().putString("todos",arr.toString()).commit(); } catch(Exception ignored) {}
        try { JSONObject obj=new JSONObject(); for(String date:eventsByDate.keySet()) { JSONArray arr=new JSONArray(); for(EventItem evt:eventsByDate.get(date)) { JSONObject e=new JSONObject(); e.put("time",evt.time); e.put("desc",evt.desc); e.put("done",evt.done); arr.put(e); } obj.put(date,arr); } prefs.edit().putString("events_map",obj.toString()).commit(); } catch(Exception ignored) {}
    }

    private void setupAdapters() { todoAdapter = new TodoAdapter(); dayEventsAdapter = new DayEventsAdapter(); if(todoList!=null) todoList.setAdapter(todoAdapter); if(dayEventsList!=null) dayEventsList.setAdapter(dayEventsAdapter); }

    private class TodoAdapter extends BaseAdapter {
        @Override public int getCount() { return todos.size(); } @Override public Object getItem(int p) { return todos.get(p); } @Override public long getItemId(int p) { return p; }
        @Override public View getView(final int pos, View cv, ViewGroup parent) {
            LinearLayout row = new LinearLayout(MainActivity.this); row.setOrientation(LinearLayout.HORIZONTAL); row.setPadding(8,6,8,6); row.setBackgroundColor(Color.parseColor("#1A1A1A"));
            Button cBtn = new Button(MainActivity.this); cBtn.setText(todos.get(pos).done?"✓":"○"); cBtn.setTextSize(18); cBtn.setWidth(45); cBtn.setHeight(45); cBtn.setBackgroundColor(Color.parseColor("#333333")); cBtn.setTextColor(Color.parseColor("#D4AF37")); cBtn.setGravity(Gravity.CENTER);
            cBtn.setOnClickListener(v -> runOnUiThread(() -> { todos.get(pos).done = !todos.get(pos).done; saveData(); todoAdapter.notifyDataSetChanged(); }));
            TextView tv = new TextView(MainActivity.this); tv.setText(todos.get(pos).text); tv.setTextColor(todos.get(pos).done?Color.parseColor("#666666"):Color.parseColor("#FFFFFF")); tv.setTextSize(14); tv.setGravity(Gravity.CENTER_VERTICAL); tv.setPadding(10,0,10,0);
            Button dBtn = new Button(MainActivity.this); dBtn.setText("✕"); dBtn.setTextSize(18); dBtn.setWidth(45); dBtn.setHeight(45); dBtn.setBackgroundColor(Color.parseColor("#8B0000")); dBtn.setTextColor(Color.parseColor("#FFFFFF")); dBtn.setGravity(Gravity.CENTER);
            final int finalPos = pos;
            dBtn.setOnClickListener(v -> new AlertDialog.Builder(MainActivity.this).setTitle("Elimina Nota").setMessage("Sei sicuro?").setPositiveButton("Sì",(d,w)->runOnUiThread(()->{ todos.remove(finalPos); saveData(); todoAdapter.notifyDataSetChanged(); })).setNegativeButton("No",null).show());
            row.addView(cBtn); row.addView(tv); row.addView(dBtn); return row;
        }
    }

    private class DayEventsAdapter extends BaseAdapter {
        @Override public int getCount() { return dayEvents.size(); } @Override public Object getItem(int p) { return dayEvents.get(p); } @Override public long getItemId(int p) { return p; }
        @Override public View getView(final int pos, View cv, ViewGroup parent) {
            LinearLayout row = new LinearLayout(MainActivity.this); row.setOrientation(LinearLayout.HORIZONTAL); row.setPadding(8,6,8,6); row.setBackgroundColor(Color.parseColor("#222222"));
            Button cBtn = new Button(MainActivity.this); cBtn.setText(dayEvents.get(pos).done?"✓":"○"); cBtn.setTextSize(16); cBtn.setWidth(40); cBtn.setHeight(40); cBtn.setBackgroundColor(Color.parseColor("#333333")); cBtn.setTextColor(Color.parseColor("#D4AF37")); cBtn.setGravity(Gravity.CENTER);
            cBtn.setOnClickListener(v -> runOnUiThread(() -> { dayEvents.get(pos).done = !dayEvents.get(pos).done; saveData(); dayEventsAdapter.notifyDataSetChanged(); }));
            TextView tv = new TextView(MainActivity.this); EventItem evt = dayEvents.get(pos); tv.setText(evt.display()); tv.setTextColor(evt.done?Color.parseColor("#666666"):Color.parseColor("#FFFFFF")); tv.setTextSize(12); tv.setGravity(Gravity.CENTER_VERTICAL); tv.setPadding(8,0,8,0);
            Button dBtn = new Button(MainActivity.this); dBtn.setText("✕"); dBtn.setTextSize(16); dBtn.setWidth(40); dBtn.setHeight(40); dBtn.setBackgroundColor(Color.parseColor("#8B0000")); dBtn.setTextColor(Color.parseColor("#FFFFFF")); dBtn.setGravity(Gravity.CENTER);
            final int finalPos = pos; final String finalDate = selectedDate;
            dBtn.setOnClickListener(v -> new AlertDialog.Builder(MainActivity.this).setTitle("Elimina").setMessage("Sei sicuro?").setPositiveButton("Sì",(d,w)->runOnUiThread(()->{
                if(finalDate!=null && eventsByDate.containsKey(finalDate)) {
                    ArrayList<EventItem> list = eventsByDate.get(finalDate);
                    if(finalPos >= 0 && finalPos < list.size()) list.remove(finalPos);
                    dayEvents = list;
                    saveData();
                    dayEventsAdapter.notifyDataSetChanged();
                    if(calendarAdapter != null) calendarAdapter.notifyDataSetChanged();
                    if(dayEvents.isEmpty() && dayEventsPanel != null) dayEventsPanel.setVisibility(View.GONE);
                }
            })).setNegativeButton("No",null).show());
            row.addView(cBtn); row.addView(tv); row.addView(dBtn); return row;
        }
    }

    private void showAddTodoDialog() {
        EditText input = new EditText(this); input.setHint("Scrivi nota..."); input.setBackgroundColor(Color.parseColor("#1A1A1A")); input.setTextColor(Color.parseColor("#FFFFFF"));
        new AlertDialog.Builder(this).setTitle("Nuova Nota").setView(input).setPositiveButton("OK",(d,w)->{ String val=input.getText().toString().trim(); if(!val.isEmpty()) { todos.add(0,new TodoItem(val,false)); saveData(); runOnUiThread(()->todoAdapter.notifyDataSetChanged()); } }).setNegativeButton("Annulla",null).show();
    }
    private void showAddEventDialog(final String date) {
        final EditText dIn = new EditText(this); final EditText tIn = new EditText(this); final EditText descIn = new EditText(this);
        dIn.setHint("Data (YYYY-MM-DD)"); tIn.setHint("Ora"); descIn.setHint("Descrizione");
        dIn.setText(date!=null?date:new SimpleDateFormat("yyyy-MM-dd",Locale.getDefault()).format(new Date()));
        for(EditText e:new EditText[]{dIn,tIn,descIn}) { e.setBackgroundColor(Color.parseColor("#1A1A1A")); e.setTextColor(Color.parseColor("#FFFFFF")); }
        LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL); layout.setPadding(20,10,20,10); layout.addView(dIn); layout.addView(tIn); layout.addView(descIn);
        new AlertDialog.Builder(this).setTitle("Appuntamento").setView(layout).setPositiveButton("Salva",(d,w)->{
            String dt=dIn.getText().toString().trim(), tm=tIn.getText().toString().trim(), desc=descIn.getText().toString().trim();
            if(!dt.isEmpty() && !desc.isEmpty()) { if(!eventsByDate.containsKey(dt)) eventsByDate.put(dt,new ArrayList<>()); eventsByDate.get(dt).add(new EventItem(tm,desc,false)); saveData(); runOnUiThread(()->{ if(calendarAdapter!=null) calendarAdapter.notifyDataSetChanged(); if(dt.equals(selectedDate)) updateDayEventsDisplay(); }); }
        }).setNegativeButton("Annulla",null).show();
    }

    private void updateDayEventsDisplay() {
        if(selectedDate!=null && eventsByDate.containsKey(selectedDate) && !eventsByDate.get(selectedDate).isEmpty()) {
            dayEvents = eventsByDate.get(selectedDate);
            String[] months={"Gen","Feb","Mar","Apr","Mag","Giu","Lug","Ago","Set","Ott","Nov","Dic"};
            String disp = selectedDate.substring(8)+" "+months[Integer.parseInt(selectedDate.substring(5,7))-1];
            if(selectedDayTitle!=null) selectedDayTitle.setText("📅 "+disp); if(dayEventsPanel!=null) dayEventsPanel.setVisibility(View.VISIBLE); if(dayEventsAdapter!=null) dayEventsAdapter.notifyDataSetChanged();
        } else if(dayEventsPanel!=null) dayEventsPanel.setVisibility(View.GONE);
    }
    private void updateCalendarDisplay() {
        String[] months={"Gennaio","Febbraio","Marzo","Aprile","Maggio","Giugno","Luglio","Agosto","Settembre","Ottobre","Novembre","Dicembre"};
        if(calendarMonth!=null) calendarMonth.setText(months[currentCal.get(Calendar.MONTH)]+" "+currentCal.get(Calendar.YEAR));
        if(calendarAdapter!=null) runOnUiThread(calendarAdapter::notifyDataSetChanged);
    }

    private class CalendarAdapter extends BaseAdapter {
        @Override public int getCount() { return 42; } @Override public Object getItem(int p) { return null; } @Override public long getItemId(int p) { return 0; }
        @Override public View getView(final int pos, View cv, ViewGroup parent) {
            final LinearLayout cell = new LinearLayout(MainActivity.this); cell.setOrientation(LinearLayout.VERTICAL); cell.setGravity(Gravity.CENTER); cell.setPadding(2,3,2,3); cell.setBackgroundColor(Color.parseColor("#1A1A1A")); cell.setClickable(true); cell.setFocusable(true);
            try {
                Calendar cal = (Calendar) currentCal.clone(); cal.set(Calendar.DAY_OF_MONTH, 1);
                int firstDay = cal.get(Calendar.DAY_OF_WEEK); int offset = (firstDay==Calendar.SUNDAY)?6:firstDay-2; int dayNum = pos-offset+1;
                cal.add(Calendar.DAY_OF_MONTH, pos-offset);
                final String key = new SimpleDateFormat("yyyy-MM-dd",Locale.getDefault()).format(cal.getTime());
                boolean hasEvt = eventsByDate.containsKey(key) && !eventsByDate.get(key).isEmpty();
                boolean isMonth = cal.get(Calendar.MONTH)==currentCal.get(Calendar.MONTH);
                boolean isToday = key.equals(new SimpleDateFormat("yyyy-MM-dd",Locale.getDefault()).format(new Date()));
                TextView tv = new TextView(MainActivity.this); tv.setGravity(Gravity.CENTER); tv.setTextSize(16); tv.setTextColor(Color.parseColor("#CCCCCC"));
                TextView dot = new TextView(MainActivity.this); dot.setGravity(Gravity.CENTER); dot.setTextSize(10); dot.setTextColor(Color.parseColor("#D4AF37"));
                if(dayNum<1||dayNum>cal.getActualMaximum(Calendar.DAY_OF_MONTH)||!isMonth) { tv.setText(""); dot.setText(""); cell.setBackgroundColor(Color.parseColor("#050505")); cell.setEnabled(false); }
                else { tv.setText(String.valueOf(dayNum)); if(isToday) { tv.setTextColor(Color.parseColor("#D4AF37")); tv.setText("●"+dayNum); cell.setBackgroundColor(Color.parseColor("#333333")); } else if(hasEvt) { tv.setTextColor(Color.parseColor("#D4AF37")); dot.setText("●"); } cell.setEnabled(true); }
                cell.setOnClickListener(v -> { if(cell.isEnabled()) { selectedDate=key; updateDayEventsDisplay(); } });
                cell.addView(tv); cell.addView(dot);
            } catch(Exception e) { e.printStackTrace(); }
            return cell;
        }
    }

    private void startClock() { clockHandler.postDelayed(()->{ try { Date now=new Date(); if(clockText!=null) clockText.setText(new SimpleDateFormat("HH:mm:ss",Locale.getDefault()).format(now)); if(dateText!=null) dateText.setText(new SimpleDateFormat("EEEE dd MMMM yyyy",Locale.ITALY).format(now)); clockHandler.postDelayed(this::startClock,1000); } catch(Exception ignored) {} },1000); }

    private void loadWeather() {
        new AsyncTask<Void,Void,String>() { @Override protected String doInBackground(Void... p) { try { HttpURLConnection c=(HttpURLConnection)new URL("http://wttr.in/Foggia,Italy?format=j1&lang=it").openConnection(); c.setRequestMethod("GET"); c.setConnectTimeout(5000); BufferedReader r=new BufferedReader(new InputStreamReader(c.getInputStream())); StringBuilder s=new StringBuilder(); String l; while((l=r.readLine())!=null) s.append(l); r.close(); return s.toString(); } catch(Exception e) { return "ERRORE"; }}
        @Override protected void onPostExecute(String res) {
            try { if(res.equals("ERRORE")) { if(weatherIcon!=null) weatherIcon.setText("✖"); if(weatherTemp!=null) weatherTemp.setText("Offline"); return; }
                JSONObject j=new JSONObject(res); JSONObject cur=j.getJSONArray("current_condition").getJSONObject(0); JSONArray w=j.getJSONArray("weather");
                String code=cur.getJSONArray("weatherDesc").getJSONObject(0).getString("value").toLowerCase(); String ic="☀"; if(code.contains("nuvol")||code.contains("cloud")) ic="☁"; else if(code.contains("piogg")||code.contains("rain")) ic="🌧";
                if(weatherIcon!=null) weatherIcon.setText(ic); if(weatherTemp!=null) weatherTemp.setText(cur.getString("temp_C")+"°C"); if(weatherDesc!=null) weatherDesc.setText(cur.getJSONArray("weatherDesc").getJSONObject(0).getString("value")); if(weatherWind!=null) weatherWind.setText("💨 "+cur.getString("windspeedKmph")+" km/h "+cur.getString("winddir16Point"));
                if(forecastContainer!=null) { forecastContainer.removeAllViews(); TextView t=new TextView(MainActivity.this); t.setText("Previsioni 3 gg"); t.setTextColor(Color.parseColor("#D4AF37")); t.setTextSize(10); t.setTypeface(null,Typeface.BOLD); t.setPadding(0,0,0,6); forecastContainer.addView(t);
                    String[] dn={"Lun","Mar","Mer","Gio","Ven","Sab","Dom"}; for(int i=0;i<Math.min(3,w.length());i++) { JSONObject d=w.getJSONObject(i); String ds=d.getString("date"); Calendar c=Calendar.getInstance(); c.setTime(new SimpleDateFormat("yyyy-MM-dd",Locale.getDefault()).parse(ds)); String dy=dn[c.get(Calendar.DAY_OF_WEEK)-2]; if(dy==null) dy="Dom";
                        LinearLayout r=new LinearLayout(MainActivity.this); r.setOrientation(LinearLayout.HORIZONTAL); r.setPadding(6,4,6,4); r.setGravity(Gravity.CENTER_VERTICAL); r.setBackgroundColor(i%2==0?Color.parseColor("#1A1A1A"):Color.parseColor("#222222"));
                        TextView td=new TextView(MainActivity.this); td.setText(dy); td.setTextColor(Color.parseColor("#D4AF37")); td.setTextSize(10); td.setWidth(45); td.setGravity(Gravity.CENTER);
                        TextView td2=new TextView(MainActivity.this); td2.setText(ds.substring(8)); td2.setTextColor(Color.parseColor("#888888")); td2.setTextSize(10); td2.setWidth(30);
                        String wd=d.getJSONArray("hourly").getJSONObject(6).getJSONArray("weatherDesc").getJSONObject(0).getString("value").toLowerCase(); String wi="☀"; if(wd.contains("nuvol")||wd.contains("cloud")) wi="☁"; else if(wd.contains("piogg")||wd.contains("rain")) wi="🌧";
                        TextView ti=new TextView(MainActivity.this); ti.setText(wi); ti.setTextColor(Color.parseColor("#FFFFFF")); ti.setTextSize(12); ti.setWidth(30); ti.setGravity(Gravity.CENTER);
                        TextView tt=new TextView(MainActivity.this); tt.setText(d.getJSONArray("hourly").getJSONObject(6).getString("tempC")+"°"); tt.setTextColor(Color.parseColor("#FFFFFF")); tt.setTextSize(11); tt.setGravity(Gravity.RIGHT);
                        r.addView(td); r.addView(td2); r.addView(ti); r.addView(tt); forecastContainer.addView(r); } } } catch(Exception e) { if(weatherIcon!=null) weatherIcon.setText("✖"); if(weatherTemp!=null) weatherTemp.setText("Errore"); } }
        }.execute();
    }

    private void startWeatherRefresh() { weatherHandler.postDelayed(()->{ loadWeather(); weatherHandler.postDelayed(this::startWeatherRefresh,1800000); },1800000); }
    private void showAlert(String t, String m) { try { new AlertDialog.Builder(this).setTitle(t).setMessage(m).setPositiveButton("OK",null).show(); } catch(Exception ignored) {} }
    @Override protected void onResume() { super.onResume(); setupFullScreen(); if(wakeLock!=null && !wakeLock.isHeld()) wakeLock.acquire(10*60*1000L); }
    @Override protected void onPause() { super.onPause(); if(wakeLock!=null && wakeLock.isHeld()) wakeLock.release(); }
    @Override protected void onDestroy() { super.onDestroy(); try { unregisterReceiver(batteryReceiver); } catch(Exception ignored) {} }
}
