package com.smartdashboard;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
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
    private TextView clockText, dateText, weatherIcon, weatherTemp, weatherDesc, weatherWind;
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
        setContentView(R.layout.activity_main);

        clockText = (TextView) findViewById(R.id.clock);
        dateText = (TextView) findViewById(R.id.date);
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
        calendarGrid.setAdapter(calendarAdapter);
        updateCalendarDisplay();

        clockHandler = new Handler();
        weatherHandler = new Handler();
        startClock();
        loadWeather();
        startWeatherRefresh();

        btnAddEvt.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showAddEventDialog(selectedDate); }
        });
        btnAddTodo.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showAddTodoDialog(); }
        });
        btnPrevMonth.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { 
                currentCal.add(Calendar.MONTH, -1); 
                updateCalendarDisplay(); 
            }
        });
        btnNextMonth.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { 
                currentCal.add(Calendar.MONTH, 1); 
                updateCalendarDisplay(); 
            }
        });
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
        } catch(Exception e) { todos = new ArrayList<TodoItem>(); }

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
        todoAdapter = new TodoAdapter();
        todoList.setAdapter(todoAdapter);
        dayEventsAdapter = new DayEventsAdapter();
        dayEventsList.setAdapter(dayEventsAdapter);
    }

    // Adapter NOTE con pulsanti ✅ e 🗑️ VISIBILI
    private class TodoAdapter extends BaseAdapter {
        @Override public int getCount() { return todos.size(); }
        @Override public Object getItem(int pos) { return todos.get(pos); }
        @Override public long getItemId(int pos) { return pos; }
        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            LinearLayout row = new LinearLayout(MainActivity.this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(5,5,5,5);
            row.setBackgroundColor(Color.parseColor("#1a1a2e"));
            row.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 
                LinearLayout.LayoutParams.WRAP_CONTENT));
            
            // Pulsante CHECK ✅ - GRANDE E VISIBILE
            Button btnCheck = new Button(MainActivity.this);
            btnCheck.setText(todos.get(position).done ? "✅" : "⬜");
            btnCheck.setTextSize(16);
            btnCheck.setWidth(45);
            btnCheck.setHeight(45);
            btnCheck.setBackgroundColor(Color.parseColor("#2d2d4a"));
            btnCheck.setTextColor(Color.parseColor("#ffd700"));
            btnCheck.setGravity(Gravity.CENTER);
            btnCheck.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    todos.get(position).done = !todos.get(position).done;
                    saveData(); 
                    notifyDataSetChanged();
                }
            });
            
            // Testo NOTA
            TextView tv = new TextView(MainActivity.this);
            tv.setText(todos.get(position).text);
            tv.setTextColor(todos.get(position).done ? Color.parseColor("#6a6a8a") : Color.parseColor("#ffffff"));
            tv.setTextSize(14);
            tv.setGravity(Gravity.CENTER_VERTICAL);
            tv.setPadding(8,0,8,0);
            tv.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1));
            
            // Pulsante ELIMINA 🗑️ - GRANDE E VISIBILE (ROSSO)
            Button btnDel = new Button(MainActivity.this);
            btnDel.setText("🗑️");
            btnDel.setTextSize(16);
            btnDel.setWidth(45);
            btnDel.setHeight(45);
            btnDel.setBackgroundColor(Color.parseColor("#dc143c"));
            btnDel.setTextColor(Color.parseColor("#ffffff"));
            btnDel.setGravity(Gravity.CENTER);
            btnDel.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Elimina Nota")
                        .setMessage("Sei sicuro di voler eliminare questa nota?")
                        .setPositiveButton("Sì", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                todos.remove(position);
                                saveData(); 
                                notifyDataSetChanged();
                            }
                        })
                        .setNegativeButton("No", null)
                        .show();
                }
            });
            
            row.addView(btnCheck);
            row.addView(tv);
            row.addView(btnDel);
            return row;
        }
    }

    // Adapter EVENTI con pulsanti ✅ e 🗑️ VISIBILI
    private class DayEventsAdapter extends BaseAdapter {
        @Override public int getCount() { return dayEvents.size(); }
        @Override public Object getItem(int pos) { return dayEvents.get(pos); }
        @Override public long getItemId(int pos) { return pos; }
        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            LinearLayout row = new LinearLayout(MainActivity.this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(4,4,4,4);
            row.setBackgroundColor(Color.parseColor("#15152a"));
            row.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 
                LinearLayout.LayoutParams.WRAP_CONTENT));
            
            // Pulsante CHECK ✅
            Button btnCheck = new Button(MainActivity.this);
            btnCheck.setText(dayEvents.get(position).done ? "✅" : "⬜");
            btnCheck.setTextSize(14);
            btnCheck.setWidth(40);
            btnCheck.setHeight(40);
            btnCheck.setBackgroundColor(Color.parseColor("#2d2d4a"));
            btnCheck.setTextColor(Color.parseColor("#ffd700"));
            btnCheck.setGravity(Gravity.CENTER);
            btnCheck.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    dayEvents.get(position).done = !dayEvents.get(position).done;
                    if(selectedDate != null && eventsByDate.containsKey(selectedDate)) {
                        saveData();
                        if(calendarAdapter != null) calendarAdapter.notifyDataSetChanged();
                    }
                    notifyDataSetChanged();
                }
            });
            
            // Testo EVENTO
            TextView tv = new TextView(MainActivity.this);
            EventItem evt = dayEvents.get(position);
            tv.setText(evt.display());
            tv.setTextColor(evt.done ? Color.parseColor("#6a6a8a") : Color.parseColor("#e8e8ff"));
            tv.setTextSize(12);
            tv.setGravity(Gravity.CENTER_VERTICAL);
            tv.setPadding(6,0,6,0);
            tv.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1));
            
            // Pulsante ELIMINA 🗑️
            Button btnDel = new Button(MainActivity.this);
            btnDel.setText("🗑️");
            btnDel.setTextSize(14);
            btnDel.setWidth(40);
            btnDel.setHeight(40);
            btnDel.setBackgroundColor(Color.parseColor("#dc143c"));
            btnDel.setTextColor(Color.parseColor("#ffffff"));
            btnDel.setGravity(Gravity.CENTER);
            btnDel.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Elimina Appuntamento")
                        .setMessage("Sei sicuro di voler eliminare questo appuntamento?")
                        .setPositiveButton("Sì", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                if(selectedDate != null && eventsByDate.containsKey(selectedDate)) {
                                    eventsByDate.get(selectedDate).remove(position);
                                    dayEvents.remove(position);
                                    saveData();
                                    notifyDataSetChanged();
                                    if(calendarAdapter != null) calendarAdapter.notifyDataSetChanged();
                                    if(dayEvents.isEmpty()) {
                                        dayEventsPanel.setVisibility(View.GONE);
                                    }
                                }
                            }
                        })
                        .setNegativeButton("No", null)
                        .show();
                }
            });
            
            row.addView(btnCheck);
            row.addView(tv);
            row.addView(btnDel);
            return row;
        }
    }

    private void showAddTodoDialog() {
        EditText input = new EditText(this);
        input.setHint("Scrivi nota...");
        input.setBackgroundColor(Color.parseColor("#1a1a2e"));
        input.setTextColor(Color.parseColor("#ffffff"));
        new AlertDialog.Builder(this)
            .setTitle("Nuova Nota")
            .setView(input)
            .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int w) {
                    String val = input.getText().toString().trim();
                    if(!val.isEmpty()) {
                        todos.add(0, new TodoItem(val, false));
                        saveData(); 
                        todoAdapter.notifyDataSetChanged();
                    }
                }
            }).setNegativeButton("Annulla", null).show();
    }

    private void showAddEventDialog(final String preselectedDate) {
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
            et.setBackgroundColor(Color.parseColor("#1a1a2e"));
            et.setTextColor(Color.parseColor("#ffffff"));
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
                    String date = dateInput.getText().toString().trim();
                    String time = timeInput.getText().toString().trim();
                    String desc = descInput.getText().toString().trim();
                    if(!date.isEmpty() && !desc.isEmpty()) {
                        if(!eventsByDate.containsKey(date)) eventsByDate.put(date, new ArrayList<EventItem>());
                        eventsByDate.get(date).add(new EventItem(time, desc, false));
                        saveData();
                        if(calendarAdapter != null) calendarAdapter.notifyDataSetChanged();
                        if(date.equals(selectedDate)) {
                            dayEvents = eventsByDate.get(date);
                            dayEventsPanel.setVisibility(View.VISIBLE);
                            dayEventsAdapter.notifyDataSetChanged();
                        }
                    }
                }
            }).setNegativeButton("Annulla", null).show();
    }

    private void updateDayEventsDisplay() {
        if(selectedDate != null && eventsByDate.containsKey(selectedDate) && !eventsByDate.get(selectedDate).isEmpty()) {
            dayEvents = eventsByDate.get(selectedDate);
            String[] months = {"Gen","Feb","Mar","Apr","Mag","Giu","Lug","Ago","Set","Ott","Nov","Dic"};
            String displayDate = selectedDate.substring(8)+" "+months[Integer.parseInt(selectedDate.substring(5,7))-1];
            selectedDayTitle.setText("📅 "+displayDate);
            dayEventsPanel.setVisibility(View.VISIBLE);
            dayEventsAdapter.notifyDataSetChanged();
        } else {
            dayEventsPanel.setVisibility(View.GONE);
        }
    }

    private void updateCalendarDisplay() {
        String[] months = {"Gennaio","Febbraio","Marzo","Aprile","Maggio","Giugno","Luglio","Agosto","Settembre","Ottobre","Novembre","Dicembre"};
        calendarMonth.setText(months[currentCal.get(Calendar.MONTH)] + " " + currentCal.get(Calendar.YEAR));
        if(calendarAdapter != null) calendarAdapter.notifyDataSetChanged();
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
            cell.setBackgroundColor(Color.parseColor("#1a1a2e"));
            cell.setClickable(true);
            cell.setFocusable(true);
            cell.setLayoutParams(new GridView.LayoutParams(GridView.LayoutParams.MATCH_PARENT, 55));
            
            Calendar cal = (Calendar) currentCal.clone();
            cal.set(Calendar.DAY_OF_MONTH, 1);
            int firstDay = cal.get(Calendar.DAY_OF_WEEK);
            int offset = (firstDay == Calendar.SUNDAY) ? 6 : firstDay - 2;
            int dayNum = position - offset + 1;
            
            cal.add(Calendar.DAY_OF_MONTH, position - offset);
            final String dateKey = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.getTime());
            boolean hasEvents = eventsByDate.containsKey(dateKey) && !eventsByDate.get(dateKey).isEmpty();
            boolean isCurrentMonth = cal.get(Calendar.MONTH) == currentCal.get(Calendar.MONTH);
            boolean isToday = dateKey.equals(new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date()));
            
            TextView tvDay = new TextView(MainActivity.this);
            tvDay.setGravity(Gravity.CENTER);
            tvDay.setTextSize(16);
            tvDay.setTextColor(Color.parseColor("#b0b0c0"));
            tvDay.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
            
            TextView dot = new TextView(MainActivity.this);
            dot.setGravity(Gravity.CENTER);
            dot.setTextSize(10);
            dot.setTextColor(Color.parseColor("#ffd700"));
            dot.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 0.3f));
            
            if(dayNum < 1 || dayNum > cal.getActualMaximum(Calendar.DAY_OF_MONTH) || !isCurrentMonth) {
                tvDay.setText("");
                dot.setText("");
                cell.setBackgroundColor(Color.parseColor("#0f0f1a"));
                cell.setEnabled(false);
            } else {
                tvDay.setText(String.valueOf(dayNum));
                if(isToday) {
                    tvDay.setTextColor(Color.parseColor("#ffd700"));
                    tvDay.setText("●"+dayNum);
                    cell.setBackgroundColor(Color.parseColor("#2a2a4a"));
                } else if(hasEvents) {
                    tvDay.setTextColor(Color.parseColor("#ffd700"));
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
            return cell;
        }
    }

    private void startClock() {
        clockHandler.postDelayed(new Runnable() {
            @Override public void run() {
                Date now = new Date();
                clockText.setText(new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(now));
                dateText.setText(new SimpleDateFormat("EEEE dd MMMM yyyy", Locale.ITALY).format(now));
                clockHandler.postDelayed(this, 1000);
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
                if (result.equals("ERRORE")) { 
                    weatherIcon.setText("❌"); 
                    weatherTemp.setText("Offline");
                    return; 
                }
                try {
                    JSONObject json = new JSONObject(result);
                    JSONObject cur = json.getJSONArray("current_condition").getJSONObject(0);
                    JSONArray weather = json.getJSONArray("weather");
                    
                    String weatherCode = cur.getJSONArray("weatherDesc").getJSONObject(0).getString("value").toLowerCase();
                    String icon = "☀️";
                    if(weatherCode.contains("nuvol") || weatherCode.contains("cloud")) icon = "☁️";
                    else if(weatherCode.contains("piogg") || weatherCode.contains("rain")) icon = "🌧️";
                    else if(weatherCode.contains("temporal") || weatherCode.contains("thunder")) icon = "⛈️";
                    else if(weatherCode.contains("nebb") || weatherCode.contains("fog")) icon = "🌫️";
                    
                    weatherIcon.setText(icon);
                    weatherTemp.setText(cur.getString("temp_C")+"°C");
                    weatherDesc.setText(cur.getJSONArray("weatherDesc").getJSONObject(0).getString("value"));
                    weatherWind.setText("💨 "+cur.getString("windspeedKmph")+" km/h "+cur.getString("winddir16Point"));
                    
                    forecastContainer.removeAllViews();
                    TextView title = new TextView(MainActivity.this);
                    title.setText("📅 Previsioni 7 giorni");
                    title.setTextColor(Color.parseColor("#ffd700"));
                    title.setTextSize(11);
                    title.setTypeface(null, Typeface.BOLD);
                    title.setPadding(0,0,0,4);
                    forecastContainer.addView(title);
                    
                    String[] dayNames = {"Lun","Mar","Mer","Gio","Ven","Sab","Dom"};
                    for (int i = 0; i < Math.min(7, weather.length()); i++) {
                        JSONObject d = weather.getJSONObject(i);
                        String dateStr = d.getString("date");
                        Calendar c = Calendar.getInstance();
                        c.setTime(new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dateStr));
                        String dayName = dayNames[c.get(Calendar.DAY_OF_WEEK)-2];
                        if(dayName == null) dayName = "Dom";
                        
                        LinearLayout row = new LinearLayout(MainActivity.this);
                        row.setOrientation(LinearLayout.HORIZONTAL);
                        row.setPadding(0,2,0,2);
                        row.setBackgroundColor(Color.parseColor("#1a1a2e"));
                        
                        TextView tvDay = new TextView(MainActivity.this);
                        tvDay.setText(dayName+" "+dateStr.substring(8));
                        tvDay.setTextColor(Color.parseColor("#a0a0c0"));
                        tvDay.setTextSize(10);
                        tvDay.setWidth(50);
                        
                        TextView tvIcon = new TextView(MainActivity.this);
                        String wDesc = d.getJSONArray("hourly").getJSONObject(6).getJSONArray("weatherDesc").getJSONObject(0).getString("value").toLowerCase();
                        String wIcon = "☀️";
                        if(wDesc.contains("nuvol") || wDesc.contains("cloud")) wIcon = "☁️";
                        else if(wDesc.contains("piogg") || wDesc.contains("rain")) wIcon = "🌧️";
                        tvIcon.setText(wIcon);
                        tvIcon.setTextSize(12);
                        tvIcon.setWidth(30);
                        tvIcon.setGravity(Gravity.CENTER);
                        
                        TextView tvTemp = new TextView(MainActivity.this);
                        tvTemp.setText(d.getJSONArray("hourly").getJSONObject(6).getString("tempC")+"°C");
                        tvTemp.setTextColor(Color.parseColor("#e8e8ff"));
                        tvTemp.setTextSize(11);
                        tvTemp.setGravity(Gravity.CENTER);
                        
                        row.addView(tvDay);
                        row.addView(tvIcon);
                        row.addView(tvTemp);
                        forecastContainer.addView(row);
                    }
                    
                } catch (Exception e) { 
                    weatherIcon.setText("⚠️"); 
                    weatherTemp.setText("Errore");
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
}
