package com.smartdashboard;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
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
    private TextView clockText, dateText, smartHomeText, weatherIcon, weatherTemp, weatherDesc, weatherWind;
    private TextView calendarMonth, selectedDayTitle;
    private GridView calendarGrid;
    private ListView todoList, dayEventsList;
    private LinearLayout dayEventsPanel, forecastContainer;
    private Button btnAddEvt, btnAddTodo, btnPrevMonth, btnNextMonth;
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

    static class TodoItem {
        String text;
        boolean done;
        TodoItem(String t, boolean d) { text=t; done=d; }
    }
    
    static class EventItem {
        String time, desc;
        boolean done;
        EventItem(String t, String d, boolean done) { time=t; desc=d; this.done=done; }
        String display() { return (time.isEmpty()?"":time+" - ")+desc; }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // SCHERMO INTERO COMPLETO (Android 4.0+)
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, 
                            WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        
        // Wake Lock per mantenere schermo acceso
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "DashboardPietro:WakeLock");
        
        setContentView(R.layout.activity_main);

        // Blocca rotazione su landscape
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        try {
            clockText = (TextView) findViewById(R.id.clock);
            dateText = (TextView) findViewById(R.id.date);
            smartHomeText = (TextView) findViewById(R.id.smartHomeText);
            weatherIcon = (TextView) findViewById(R.id.weatherIcon);
            weatherTemp = (TextView) findViewById(R.id.weatherTemp);
            weatherDesc = (TextView) findViewById(R.id.weatherDesc);
            weatherWind = (TextView) findViewById(R.id.weatherWind);
            calendarMonth = (TextView) findViewById(R.id.calendarMonth);
            calendarGrid = (GridView) findViewById(R.id.calendarGrid);
            todoList = (ListView) findViewById(R.id.todoList);
            dayEventsList = (ListView) findViewById(R.id.dayEventsList);
            dayEventsPanel = (LinearLayout) findViewById(R.id.dayEventsPanel);
            forecastContainer = (LinearLayout) findViewById(R.id.forecastContainer);
            selectedDayTitle = (TextView) findViewById(R.id.selectedDayTitle);
            btnAddEvt = (Button) findViewById(R.id.btnAddEvt);
            btnAddTodo = (Button) findViewById(R.id.btnAddTodo);
            btnPrevMonth = (Button) findViewById(R.id.btnPrevMonth);
            btnNextMonth = (Button) findViewById(R.id.btnNextMonth);

            prefs = getSharedPreferences("dashboard_data", MODE_PRIVATE);
            loadData();
            setupAdapters();

            currentCal = Calendar.getInstance();
            calendarAdapter = new CalendarAdapter();
            if(calendarGrid != null) {
                calendarGrid.setAdapter(calendarAdapter);
                updateCalendarDisplay();
            }

            clockHandler = new Handler(Looper.getMainLooper());
            weatherHandler = new Handler(Looper.getMainLooper());
            startClock();
            loadWeather();
            startWeatherRefresh();

            if(btnAddEvt != null) btnAddEvt.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { showAddEventDialog(selectedDate); }
            });
            if(btnAddTodo != null) btnAddTodo.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { showAddTodoDialog(); }
            });
            if(btnPrevMonth != null) btnPrevMonth.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { 
                    currentCal.add(Calendar.MONTH, -1); 
                    updateCalendarDisplay(); 
                }
            });
            if(btnNextMonth != null) btnNextMonth.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { 
                    currentCal.add(Calendar.MONTH, 1); 
                    updateCalendarDisplay(); 
                }
            });
            
            // Menu impostazioni con tocco prolungato sull'orologio
            if(clockText != null) {
                clockText.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        showSettingsMenu();
                        return true;
                    }
                });
            }
            
        } catch(Exception e) {
            e.printStackTrace();
        }
    }
    
    private void showSettingsMenu() {
        new AlertDialog.Builder(this)
            .setTitle("⚙️ Impostazioni Dashboard")
            .setItems(new String[]{
                "🔋 Ottimizzazione Batteria",
                "🔄 Rotazione Schermo",
                "⏱️ Timeout Schermo",
                "🔒 Blocco Schermo",
                "📱 Modalità Launcher"
            }, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    switch(which) {
                        case 0: openBatteryOptimization(); break;
                        case 1: toggleRotation(); break;
                        case 2: openScreenTimeout(); break;
                        case 3: openLockScreenSettings(); break;
                        case 4: showLauncherInfo(); break;
                    }
                }
            })
            .setNegativeButton("Chiudi", null)
            .show();
    }
    
    private void openBatteryOptimization() {
        try {
            Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } catch(Exception e) {
            showAlert("Batteria", "Vai su Impostazioni > Batteria > Ottimizzazione e seleziona 'Non ottimizzare' per Dashboard Pietro");
        }
    }
    
    private void toggleRotation() {
        try {
            int current = getRequestedOrientation();
            if(current == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
                showAlert("Rotazione", "Rotazione automatica attivata (sensore)");
            } else {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                showAlert("Rotazione", "Rotazione bloccata su Orizzontale");
            }
        } catch(Exception e) {
            showAlert("Errore", "Impossibile modificare la rotazione");
        }
    }
    
    private void openScreenTimeout() {
        try {
            Intent intent = new Intent(Settings.ACTION_DISPLAY_SETTINGS);
            startActivity(intent);
        } catch(Exception e) {
            showAlert("Timeout", "Vai su Impostazioni > Schermo > Timeout e imposta il tempo desiderato");
        }
    }
    
    private void openLockScreenSettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_SECURITY_SETTINGS);
            startActivity(intent);
        } catch(Exception e) {
            showAlert("Blocco", "Vai su Impostazioni > Sicurezza > Blocco schermo");
        }
    }
    
    private void showLauncherInfo() {
        new AlertDialog.Builder(this)
            .setTitle("Modalità Launcher")
            .setMessage("Per usare Dashboard Pietro come launcher principale:\n\n" +
                       "1. Premi il tasto HOME del tablet\n" +
                       "2. Seleziona 'Dashboard Pietro'\n" +
                       "3. Scegli 'Sempre'\n\n" +
                       "Per uscire: Premi HOME due volte o usa le impostazioni di sistema.")
            .setPositiveButton("OK", null)
            .show();
    }

    private void loadData() {
        try {
            String json = prefs.getString("todos", "[]");
            JSONArray arr = new JSONArray(json);
            todos = new ArrayList<TodoItem>();
            for(int i=0; i<arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                todos.add(new TodoItem(obj.getString("text"), obj.getBoolean("done")));
            }
        } catch(Exception e) { 
            todos = new ArrayList<TodoItem>(); 
        }

        eventsByDate = new HashMap<String, ArrayList<EventItem>>();
        try {
            String json = prefs.getString("events_map", "{}");
            JSONObject obj = new JSONObject(json);
            JSONArray keys = obj.names();
            if(keys != null) {
                for(int i=0; i<keys.length(); i++) {
                    String date = keys.getString(i);
                    JSONArray arr = obj.getJSONArray(date);
                    ArrayList<EventItem> list = new ArrayList<EventItem>();
                    for(int j=0; j<arr.length(); j++) {
                        JSONObject evt = arr.getJSONObject(j);
                        list.add(new EventItem(evt.optString("time",""), evt.getString("desc"), evt.optBoolean("done",false)));
                    }
                    eventsByDate.put(date, list);
                }
            }
        } catch(Exception e) {}
        dayEvents = new ArrayList<EventItem>();
    }

    private void saveData() {
        try {
            JSONArray arr = new JSONArray();
            for(TodoItem t : todos) {
                JSONObject obj = new JSONObject();
                obj.put("text", t.text);
                obj.put("done", t.done);
                arr.put(obj);
            }
            prefs.edit().putString("todos", arr.toString()).commit();
        } catch(Exception e){}

        try {
            JSONObject obj = new JSONObject();
            for(String date : eventsByDate.keySet()) {
                JSONArray arr = new JSONArray();
                for(EventItem evt : eventsByDate.get(date)) {
                    JSONObject e = new JSONObject();
                    e.put("time", evt.time);
                    e.put("desc", evt.desc);
                    e.put("done", evt.done);
                    arr.put(e);
                }
                obj.put(date, arr);
            }
            prefs.edit().putString("events_map", obj.toString()).commit();
        } catch(Exception e){}
    }

    private void setupAdapters() {
        try {
            if(todoAdapter == null) {
                todoAdapter = new TodoAdapter();
                if(todoList != null) todoList.setAdapter(todoAdapter);
            }
            if(dayEventsAdapter == null) {
                dayEventsAdapter = new DayEventsAdapter();
                if(dayEventsList != null) dayEventsList.setAdapter(dayEventsAdapter);
            }
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    private class TodoAdapter extends BaseAdapter {
        @Override public int getCount() { return todos == null ? 0 : todos.size(); }
        @Override public Object getItem(int pos) { return todos.get(pos); }
        @Override public long getItemId(int pos) { return pos; }
        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            LinearLayout row = new LinearLayout(MainActivity.this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(8,6,8,6);
            row.setBackgroundColor(Color.parseColor("#1A1A1A"));
            
            try {
                Button btnCheck = new Button(MainActivity.this);
                btnCheck.setText(todos.get(position).done ? "✓" : "○");
                btnCheck.setTextSize(18);
                btnCheck.setWidth(45);
                btnCheck.setHeight(45);
                btnCheck.setBackgroundColor(Color.parseColor("#333333"));
                btnCheck.setTextColor(Color.parseColor("#D4AF37"));
                btnCheck.setGravity(Gravity.CENTER);
                btnCheck.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        try {
                            todos.get(position).done = !todos.get(position).done;
                            saveData(); 
                            runOnUiThread(new Runnable() {
                                @Override public void run() {
                                    if(todoAdapter != null) todoAdapter.notifyDataSetChanged();
                                }
                            });
                        } catch(Exception e) { e.printStackTrace(); }
                    }
                });
                
                TextView tv = new TextView(MainActivity.this);
                tv.setText(todos.get(position).text);
                tv.setTextColor(todos.get(position).done ? Color.parseColor("#666666") : Color.parseColor("#FFFFFF"));
                tv.setTextSize(14);
                tv.setGravity(Gravity.CENTER_VERTICAL);
                tv.setPadding(10,0,10,0);
                
                Button btnDel = new Button(MainActivity.this);
                btnDel.setText("✕");
                btnDel.setTextSize(18);
                btnDel.setWidth(45);
                btnDel.setHeight(45);
                btnDel.setBackgroundColor(Color.parseColor("#8B0000"));
                btnDel.setTextColor(Color.parseColor("#FFFFFF"));
                btnDel.setGravity(Gravity.CENTER);
                btnDel.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        try {
                            new AlertDialog.Builder(MainActivity.this)
                                .setTitle("Elimina Nota")
                                .setMessage("Sei sicuro?")
                                .setPositiveButton("Sì", new DialogInterface.OnClickListener() {
                                    @Override public void onClick(DialogInterface dialog, int which) {
                                        try {
                                            todos.remove(position);
                                            saveData(); 
                                            runOnUiThread(new Runnable() {
                                                @Override public void run() {
                                                    if(todoAdapter != null) todoAdapter.notifyDataSetChanged();
                                                }
                                            });
                                        } catch(Exception e) { e.printStackTrace(); }
                                    }
                                })
                                .setNegativeButton("No", null)
                                .show();
                        } catch(Exception e) { e.printStackTrace(); }
                    }
                });
                
                row.addView(btnCheck);
                row.addView(tv);
                row.addView(btnDel);
            } catch(Exception e) {
                e.printStackTrace();
            }
            return row;
        }
    }

    private class DayEventsAdapter extends BaseAdapter {
        @Override public int getCount() { return dayEvents == null ? 0 : dayEvents.size(); }
        @Override public Object getItem(int pos) { return dayEvents.get(pos); }
        @Override public long getItemId(int pos) { return pos; }
        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            LinearLayout row = new LinearLayout(MainActivity.this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(8,6,8,6);
            row.setBackgroundColor(Color.parseColor("#222222"));
            
            try {
                Button btnCheck = new Button(MainActivity.this);
                btnCheck.setText(dayEvents.get(position).done ? "✓" : "○");
                btnCheck.setTextSize(16);
                btnCheck.setWidth(40);
                btnCheck.setHeight(40);
                btnCheck.setBackgroundColor(Color.parseColor("#333333"));
                btnCheck.setTextColor(Color.parseColor("#D4AF37"));
                btnCheck.setGravity(Gravity.CENTER);
                btnCheck.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        try {
                            dayEvents.get(position).done = !dayEvents.get(position).done;
                            saveData();
                            runOnUiThread(new Runnable() {
                                @Override public void run() {
                                    if(calendarAdapter != null) calendarAdapter.notifyDataSetChanged();
                                    if(dayEventsAdapter != null) dayEventsAdapter.notifyDataSetChanged();
                                }
                            });
                        } catch(Exception e) { e.printStackTrace(); }
                    }
                });
                
                TextView tv = new TextView(MainActivity.this);
                EventItem evt = dayEvents.get(position);
                tv.setText(evt.display());
                tv.setTextColor(evt.done ? Color.parseColor("#666666") : Color.parseColor("#FFFFFF"));
                tv.setTextSize(12);
                tv.setGravity(Gravity.CENTER_VERTICAL);
                tv.setPadding(8,0,8,0);
                
                Button btnDel = new Button(MainActivity.this);
                btnDel.setText("✕");
                btnDel.setTextSize(16);
                btnDel.setWidth(40);
                btnDel.setHeight(40);
                btnDel.setBackgroundColor(Color.parseColor("#8B0000"));
                btnDel.setTextColor(Color.parseColor("#FFFFFF"));
                btnDel.setGravity(Gravity.CENTER);
                btnDel.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        try {
                            new AlertDialog.Builder(MainActivity.this)
                                .setTitle("Elimina Appuntamento")
                                .setMessage("Sei sicuro?")
                                .setPositiveButton("Sì", new DialogInterface.OnClickListener() {
                                    @Override public void onClick(DialogInterface dialog, int which) {
                                        try {
                                            if(selectedDate != null && eventsByDate.containsKey(selectedDate)) {
                                                eventsByDate.get(selectedDate).remove(position);
                                                dayEvents.remove(position);
                                                saveData();
                                                runOnUiThread(new Runnable() {
                                                    @Override public void run() {
                                                        if(dayEventsAdapter != null) dayEventsAdapter.notifyDataSetChanged();
                                                        if(calendarAdapter != null) calendarAdapter.notifyDataSetChanged();
                                                        if(dayEventsPanel != null && dayEvents.isEmpty()) {
                                                            dayEventsPanel.setVisibility(View.GONE);
                                                        }
                                                    }
                                                });
                                            }
                                        } catch(Exception e) { e.printStackTrace(); }
                                    }
                                })
                                .setNegativeButton("No", null)
                                .show();
                        } catch(Exception e) { e.printStackTrace(); }
                    }
                });
                
                row.addView(btnCheck);
                row.addView(tv);
                row.addView(btnDel);
            } catch(Exception e) {
                e.printStackTrace();
            }
            return row;
        }
    }

    private void showAddTodoDialog() {
        try {
            EditText input = new EditText(this);
            input.setHint("Scrivi nota...");
            input.setBackgroundColor(Color.parseColor("#1A1A1A"));
            input.setTextColor(Color.parseColor("#FFFFFF"));
            new AlertDialog.Builder(this)
                .setTitle("Nuova Nota")
                .setView(input)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        try {
                            String val = input.getText().toString().trim();
                            if(!val.isEmpty()) {
                                if(todos == null) todos = new ArrayList<TodoItem>();
                                todos.add(0, new TodoItem(val, false));
                                saveData(); 
                                runOnUiThread(new Runnable() {
                                    @Override public void run() {
                                        if(todoAdapter != null) todoAdapter.notifyDataSetChanged();
                                    }
                                });
                            }
                        } catch(Exception e) { 
                            e.printStackTrace();
                            showAlert("Errore", "Impossibile salvare la nota");
                        }
                    }
                }).setNegativeButton("Annulla", null).show();
        } catch(Exception e) {
            e.printStackTrace();
            showAlert("Errore", "Impossibile creare la nota");
        }
    }

    private void showAddEventDialog(final String preselectedDate) {
        try {
            final EditText dateInput = new EditText(this);
            final EditText timeInput = new EditText(this);
            final EditText descInput = new EditText(this);
            
            dateInput.setHint("Data (YYYY-MM-DD)");
            timeInput.setHint("Ora (opzionale)");
            descInput.setHint("Descrizione");
            
            if(preselectedDate != null) {
                dateInput.setText(preselectedDate);
            } else {
                dateInput.setText(new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date()));
            }
            
            for(EditText et : new EditText[]{dateInput, timeInput, descInput}) {
                et.setBackgroundColor(Color.parseColor("#1A1A1A"));
                et.setTextColor(Color.parseColor("#FFFFFF"));
            }
            
            LinearLayout layout = new LinearLayout(this);
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.setPadding(20,10,20,10);
            layout.addView(dateInput);
            layout.addView(timeInput);
            layout.addView(descInput);

            new AlertDialog.Builder(this)
                .setTitle("Nuovo Appuntamento")
                .setView(layout)
                .setPositiveButton("Salva", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        try {
                            String date = dateInput.getText().toString().trim();
                            String time = timeInput.getText().toString().trim();
                            String desc = descInput.getText().toString().trim();
                            if(!date.isEmpty() && !desc.isEmpty()) {
                                if(eventsByDate == null) eventsByDate = new HashMap<String, ArrayList<EventItem>>();
                                if(!eventsByDate.containsKey(date)) eventsByDate.put(date, new ArrayList<EventItem>());
                                eventsByDate.get(date).add(new EventItem(time, desc, false));
                                saveData();
                                runOnUiThread(new Runnable() {
                                    @Override public void run() {
                                        if(calendarAdapter != null) calendarAdapter.notifyDataSetChanged();
                                        if(date.equals(selectedDate)) {
                                            updateDayEventsDisplay();
                                        }
                                    }
                                });
                            } else {
                                showAlert("Attenzione", "Data e descrizione sono obbligatorie");
                            }
                        } catch(Exception e) { 
                            e.printStackTrace();
                            showAlert("Errore", "Impossibile salvare l'appuntamento");
                        }
                    }
                }).setNegativeButton("Annulla", null).show();
        } catch(Exception e) {
            e.printStackTrace();
            showAlert("Errore", "Impossibile creare l'appuntamento");
        }
    }

    private void updateDayEventsDisplay() {
        try {
            if(selectedDate != null && eventsByDate.containsKey(selectedDate) && !eventsByDate.get(selectedDate).isEmpty()) {
                dayEvents = eventsByDate.get(selectedDate);
                String[] months = {"Gen","Feb","Mar","Apr","Mag","Giu","Lug","Ago","Set","Ott","Nov","Dic"};
                String displayDate = selectedDate.substring(8)+" "+months[Integer.parseInt(selectedDate.substring(5,7))-1];
                if(selectedDayTitle != null) selectedDayTitle.setText("📅 "+displayDate);
                if(dayEventsPanel != null) dayEventsPanel.setVisibility(View.VISIBLE);
                if(dayEventsAdapter != null) dayEventsAdapter.notifyDataSetChanged();
            } else {
                if(dayEventsPanel != null) dayEventsPanel.setVisibility(View.GONE);
            }
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    private void updateCalendarDisplay() {
        try {
            String[] months = {"Gennaio","Febbraio","Marzo","Aprile","Maggio","Giugno","Luglio","Agosto","Settembre","Ottobre","Novembre","Dicembre"};
            if(calendarMonth != null) calendarMonth.setText(months[currentCal.get(Calendar.MONTH)] + " " + currentCal.get(Calendar.YEAR));
            if(calendarAdapter != null) runOnUiThread(new Runnable() {
                @Override public void run() {
                    calendarAdapter.notifyDataSetChanged();
                }
            });
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    private class CalendarAdapter extends BaseAdapter {
        @Override public int getCount() { return 42; }
        @Override public Object getItem(int pos) { return null; }
        @Override public long getItemId(int pos) { return 0; }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            final LinearLayout cell = new LinearLayout(MainActivity.this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER);
            cell.setPadding(2,3,2,3);
            cell.setBackgroundColor(Color.parseColor("#1A1A1A"));
            cell.setClickable(true);
            cell.setFocusable(true);
            
            try {
                Calendar cal = (Calendar) currentCal.clone();
                cal.set(Calendar.DAY_OF_MONTH, 1);
                int firstDay = cal.get(Calendar.DAY_OF_WEEK);
                int offset = (firstDay == Calendar.SUNDAY) ? 6 : firstDay - 2;
                int dayNum = position - offset + 1;
                
                cal.add(Calendar.DAY_OF_MONTH, position - offset);
                final String dateKey = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.getTime());
                boolean hasEvents = eventsByDate != null && eventsByDate.containsKey(dateKey) && !eventsByDate.get(dateKey).isEmpty();
                boolean isCurrentMonth = cal.get(Calendar.MONTH) == currentCal.get(Calendar.MONTH);
                boolean isToday = dateKey.equals(new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date()));
                
                TextView tvDay = new TextView(MainActivity.this);
                tvDay.setGravity(Gravity.CENTER);
                tvDay.setTextSize(16);
                tvDay.setTextColor(Color.parseColor("#CCCCCC"));
                
                TextView dot = new TextView(MainActivity.this);
                dot.setGravity(Gravity.CENTER);
                dot.setTextSize(10);
                dot.setTextColor(Color.parseColor("#D4AF37"));
                
                if(dayNum < 1 || dayNum > cal.getActualMaximum(Calendar.DAY_OF_MONTH) || !isCurrentMonth) {
                    tvDay.setText("");
                    dot.setText("");
                    cell.setBackgroundColor(Color.parseColor("#050505"));
                    cell.setEnabled(false);
                } else {
                    tvDay.setText(String.valueOf(dayNum));
                    if(isToday) {
                        tvDay.setTextColor(Color.parseColor("#D4AF37"));
                        tvDay.setText("●"+dayNum);
                        cell.setBackgroundColor(Color.parseColor("#333333"));
                    } else if(hasEvents) {
                        tvDay.setTextColor(Color.parseColor("#D4AF37"));
                        dot.setText("●");
                    }
                    cell.setEnabled(true);
                }
                
                cell.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        if(cell.isEnabled()) {
                            selectedDate = dateKey;
                            updateDayEventsDisplay();
                        }
                    }
                });
                
                cell.addView(tvDay);
                cell.addView(dot);
            } catch(Exception e) {
                e.printStackTrace();
            }
            return cell;
        }
    }

    private void startClock() {
        clockHandler.postDelayed(new Runnable() {
            @Override public void run() {
                try {
                    Date now = new Date();
                    if(clockText != null) clockText.setText(new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(now));
                    if(dateText != null) dateText.setText(new SimpleDateFormat("EEEE dd MMMM yyyy", Locale.ITALY).format(now));
                    clockHandler.postDelayed(this, 1000);
                } catch(Exception e) { e.printStackTrace(); }
            }
        }, 1000);
    }

    private void loadWeather() {
        new AsyncTask<Void, Void, String>() {
            @Override
            protected String doInBackground(Void... params) {
                try {
                    HttpURLConnection conn = (HttpURLConnection) new URL("http://wttr.in/Foggia,Italy?format=j1&lang=it").openConnection();
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(5000);
                    BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = in.readLine()) != null) sb.append(line);
                    in.close();
                    return sb.toString();
                } catch (Exception e) { return "ERRORE"; }
            }
            @Override
            protected void onPostExecute(String result) {
                try {
                    if (result.equals("ERRORE")) { 
                        if(weatherIcon != null) weatherIcon.setText("✖"); 
                        if(weatherTemp != null) weatherTemp.setText("Offline");
                        return; 
                    }
                    JSONObject json = new JSONObject(result);
                    JSONObject cur = json.getJSONArray("current_condition").getJSONObject(0);
                    JSONArray weather = json.getJSONArray("weather");
                    
                    String weatherCode = cur.getJSONArray("weatherDesc").getJSONObject(0).getString("value").toLowerCase();
                    String icon = "☀";
                    if(weatherCode.contains("nuvol") || weatherCode.contains("cloud")) icon = "☁";
                    else if(weatherCode.contains("piogg") || weatherCode.contains("rain")) icon = "🌧";
                    
                    if(weatherIcon != null) weatherIcon.setText(icon);
                    if(weatherTemp != null) weatherTemp.setText(cur.getString("temp_C")+"°C");
                    if(weatherDesc != null) weatherDesc.setText(cur.getJSONArray("weatherDesc").getJSONObject(0).getString("value"));
                    if(weatherWind != null) weatherWind.setText("💨 "+cur.getString("windspeedKmph")+" km/h "+cur.getString("winddir16Point"));
                    
                    // Previsioni 3 giorni (limite API gratuita)
                    if(forecastContainer != null) {
                        forecastContainer.removeAllViews();
                        
                        TextView title = new TextView(MainActivity.this);
                        title.setText("Previsioni 3 giorni");
                        title.setTextColor(Color.parseColor("#D4AF37"));
                        title.setTextSize(10);
                        title.setTypeface(null, Typeface.BOLD);
                        title.setPadding(0,0,0,4);
                        forecastContainer.addView(title);
                        
                        String[] dayNames = {"Lun","Mar","Mer","Gio","Ven","Sab","Dom"};
                        
                        for (int i = 0; i < Math.min(3, weather.length()); i++) {
                            JSONObject d = weather.getJSONObject(i);
                            String dateStr = d.getString("date");
                            Calendar c = Calendar.getInstance();
                            c.setTime(new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dateStr));
                            String dayName = dayNames[c.get(Calendar.DAY_OF_WEEK)-2];
                            if(dayName == null) dayName = "Dom";
                            
                            LinearLayout row = new LinearLayout(MainActivity.this);
                            row.setOrientation(LinearLayout.HORIZONTAL);
                            row.setPadding(4,2,4,2);
                            row.setGravity(Gravity.CENTER_VERTICAL);
                            row.setBackgroundColor(i % 2 == 0 ? Color.parseColor("#1A1A1A") : Color.parseColor("#222222"));
                            
                            TextView tvDay = new TextView(MainActivity.this);
                            tvDay.setText(dayName);
                            tvDay.setTextColor(Color.parseColor("#D4AF37"));
                            tvDay.setTextSize(9);
                            tvDay.setWidth(32);
                            tvDay.setGravity(Gravity.CENTER);
                            
                            TextView tvDate = new TextView(MainActivity.this);
                            tvDate.setText(dateStr.substring(8));
                            tvDate.setTextColor(Color.parseColor("#888888"));
                            tvDate.setTextSize(9);
                            tvDate.setWidth(20);
                            
                            String wDesc = d.getJSONArray("hourly").getJSONObject(6).getJSONArray("weatherDesc").getJSONObject(0).getString("value").toLowerCase();
                            String wIcon = "☀";
                            if(wDesc.contains("nuvol") || wDesc.contains("cloud")) wIcon = "☁";
                            else if(wDesc.contains("piogg") || wDesc.contains("rain")) wIcon = "🌧";
                            
                            TextView tvIcon = new TextView(MainActivity.this);
                            tvIcon.setText(wIcon);
                            tvIcon.setTextColor(Color.parseColor("#FFFFFF"));
                            tvIcon.setTextSize(11);
                            tvIcon.setWidth(24);
                            tvIcon.setGravity(Gravity.CENTER);
                            
                            TextView tvTemp = new TextView(MainActivity.this);
                            tvTemp.setText(d.getJSONArray("hourly").getJSONObject(6).getString("tempC")+"°");
                            tvTemp.setTextColor(Color.parseColor("#FFFFFF"));
                            tvTemp.setTextSize(10);
                            tvTemp.setGravity(Gravity.RIGHT);
                            
                            row.addView(tvDay);
                            row.addView(tvDate);
                            row.addView(tvIcon);
                            row.addView(tvTemp);
                            forecastContainer.addView(row);
                        }
                    }
                } catch (Exception e) { 
                    e.printStackTrace();
                    if(weatherIcon != null) weatherIcon.setText("✖"); 
                    if(weatherTemp != null) weatherTemp.setText("Errore");
                }
            }
        }.execute();
    }

    private void startWeatherRefresh() {
        weatherHandler.postDelayed(new Runnable() {
            @Override public void run() {
                loadWeather();
                weatherHandler.postDelayed(this, 1800000);
            }
        }, 1800000);
    }
    
    private void showAlert(String title, String message) {
        try {
            new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
        } catch(Exception e) { e.printStackTrace(); }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        // Nascondi barra di navigazione al resume
        hideNavigationBar();
        if(wakeLock != null && !wakeLock.isHeld()) {
            wakeLock.acquire(10*60*1000L /*10 minuti*/);
        }
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        if(wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
    }
    
    private void hideNavigationBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            View decorView = getWindow().getDecorView();
            int uiOptions = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
            decorView.setSystemUiVisibility(uiOptions);
        }
    }
}
