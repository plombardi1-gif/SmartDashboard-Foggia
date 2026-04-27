package com.smartdashboard;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridView;
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
    private TextView clockText, dateText, weatherText, calendarMonth;
    private GridView calendarGrid;
    private ListView todoList;
    private Button btnAddEvt, btnAddTodo, btnPrevMonth, btnNextMonth;
    private Handler clockHandler, weatherHandler;
    private SharedPreferences prefs;
    private ArrayList<String> todos;
    private HashMap<String, ArrayList<String>> eventsByDate; // "YYYY-MM-DD" -> list of events
    private ArrayAdapter<String> todoAdapter;
    private Calendar currentCal;
    private CalendarAdapter calendarAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        clockText = (TextView) findViewById(R.id.clock);
        dateText = (TextView) findViewById(R.id.date);
        weatherText = (TextView) findViewById(R.id.weatherText);
        calendarMonth = (TextView) findViewById(R.id.calendarMonth);
        calendarGrid = (GridView) findViewById(R.id.calendarGrid);
        todoList = (ListView) findViewById(R.id.todoList);
        btnAddEvt = (Button) findViewById(R.id.btnAddEvt);
        btnAddTodo = (Button) findViewById(R.id.btnAddTodo);
        btnPrevMonth = (Button) findViewById(R.id.btnPrevMonth);
        btnNextMonth = (Button) findViewById(R.id.btnNextMonth);

        prefs = getSharedPreferences("dashboard_data", MODE_PRIVATE);
        loadData();
        setupTodoAdapter();

        currentCal = Calendar.getInstance();
        calendarAdapter = new CalendarAdapter();
        calendarGrid.setAdapter(calendarAdapter);
        updateCalendarDisplay();

        clockHandler = new Handler();
        weatherHandler = new Handler();
        startClock();
        loadWeather();
        startWeatherRefresh();

        // Pulsanti
        btnAddEvt.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showAddEventDialog(null); }
        });
        btnAddTodo.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showAddTodoDialog(-1); }
        });
        btnPrevMonth.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { currentCal.add(Calendar.MONTH, -1); updateCalendarDisplay(); }
        });
        btnNextMonth.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { currentCal.add(Calendar.MONTH, 1); updateCalendarDisplay(); }
        });

        // Note: click per spuntare, long-press per eliminare
        todoList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> p, View v, int pos, long id) { toggleDone(pos); }
        });
        todoList.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override public boolean onItemLongClick(AdapterView<?> p, View v, int pos, long id) { deleteTodo(pos); return true; }
        });
    }

    private void loadData() {
        // Carica notes
        try {
            String json = prefs.getString("todos", "[]");
            JSONArray arr = new JSONArray(json);
            todos = new ArrayList<String>();
            for(int i=0; i<arr.length(); i++) todos.add(arr.getString(i));
        } catch(Exception e) { todos = new ArrayList<String>(); }

        // Carica eventi per data
        eventsByDate = new HashMap<String, ArrayList<String>>();
        try {
            String json = prefs.getString("events_map", "{}");
            JSONObject obj = new JSONObject(json);
            JSONArray keys = obj.names();
            if(keys != null) {
                for(int i=0; i<keys.length(); i++) {
                    String date = keys.getString(i);
                    JSONArray arr = obj.getJSONArray(date);
                    ArrayList<String> list = new ArrayList<String>();
                    for(int j=0; j<arr.length(); j++) list.add(arr.getString(j));
                    eventsByDate.put(date, list);
                }
            }
        } catch(Exception e) {}
    }

    private void saveData() {
        // Salva notes
        try {
            JSONArray arr = new JSONArray();
            for(String s : todos) arr.put(s);
            prefs.edit().putString("todos", arr.toString()).commit();
        } catch(Exception e){}

        // Salva eventi
        try {
            JSONObject obj = new JSONObject();
            for(String date : eventsByDate.keySet()) {
                JSONArray arr = new JSONArray();
                for(String evt : eventsByDate.get(date)) arr.put(evt);
                obj.put(date, arr);
            }
            prefs.edit().putString("events_map", obj.toString()).commit();
        } catch(Exception e){}
    }

    private void setupTodoAdapter() {
        todoAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, todos) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView tv = (TextView) super.getView(position, convertView, parent);
                String text = todos.get(position);
                if(text.startsWith("✅ ")) {
                    tv.setTextColor(Color.parseColor("#6a6a8a"));
                } else {
                    tv.setTextColor(Color.parseColor("#e0e0f0"));
                }
                tv.setBackgroundColor(Color.parseColor("#1a1a2e"));
                tv.setPadding(8,6,8,6);
                return tv;
            }
        };
        todoList.setAdapter(todoAdapter);
    }

    private void showAddTodoDialog(final int position) {
        EditText input = new EditText(this);
        input.setHint("Scrivi nota...");
        input.setBackgroundColor(Color.parseColor("#1a1a2e"));
        input.setTextColor(Color.parseColor("#e0e0f0"));
        new AlertDialog.Builder(this)
            .setTitle("Nuova Nota")
            .setView(input)
            .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int w) {
                    String val = input.getText().toString().trim();
                    if(!val.isEmpty()) {
                        if(position == -1) todos.add(0, val); else todos.set(position, val);
                        saveData(); todoAdapter.notifyDataSetChanged();
                    }
                }
            }).setNegativeButton("Annulla", null).show();
    }

    private void toggleDone(int position) {
        String t = todos.get(position);
        todos.set(position, t.startsWith("✅ ") ? t.substring(2) : "✅ "+t);
        saveData(); todoAdapter.notifyDataSetChanged();
    }

    private void deleteTodo(int position) {
        todos.remove(position);
        saveData(); todoAdapter.notifyDataSetChanged();
    }

    private void showAddEventDialog(final String selectedDate) {
        final EditText dateInput = new EditText(this);
        final EditText timeInput = new EditText(this);
        final EditText descInput = new EditText(this);
        
        dateInput.setHint("Data (es: 2024-01-20)");
        timeInput.setHint("Ora (es: 15:30)");
        descInput.setHint("Descrizione");
        
        if(selectedDate != null) {
            dateInput.setText(selectedDate);
            dateInput.setEnabled(false);
        }
        
        dateInput.setBackgroundColor(Color.parseColor("#1a1a2e"));
        timeInput.setBackgroundColor(Color.parseColor("#1a1a2e"));
        descInput.setBackgroundColor(Color.parseColor("#1a1a2e"));
        dateInput.setTextColor(Color.parseColor("#e0e0f0"));
        timeInput.setTextColor(Color.parseColor("#e0e0f0"));
        descInput.setTextColor(Color.parseColor("#e0e0f0"));
        
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
                        String entry = (time.isEmpty() ? "" : time+" - ") + desc;
                        if(!eventsByDate.containsKey(date)) eventsByDate.put(date, new ArrayList<String>());
                        eventsByDate.get(date).add(entry);
                        saveData();
                        if(calendarAdapter != null) calendarAdapter.notifyDataSetChanged();
                    }
                }
            }).setNegativeButton("Annulla", null).show();
    }

    private void updateCalendarDisplay() {
        String[] months = {"Gennaio","Febbraio","Marzo","Aprile","Maggio","Giugno","Luglio","Agosto","Settembre","Ottobre","Novembre","Dicembre"};
        calendarMonth.setText(months[currentCal.get(Calendar.MONTH)] + " " + currentCal.get(Calendar.YEAR));
        if(calendarAdapter != null) calendarAdapter.notifyDataSetChanged();
    }

    private class CalendarAdapter extends BaseAdapter {
        @Override public int getCount() { return 42; } // 6 righe x 7 giorni
        @Override public Object getItem(int pos) { return null; }
        @Override public long getItemId(int pos) { return 0; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            TextView tv = new TextView(MainActivity.this);
            tv.setGravity(Gravity.CENTER);
            tv.setPadding(2,4,2,4);
            tv.setTextSize(12);
            tv.setBackgroundColor(Color.parseColor("#1a1a2e"));
            
            Calendar cal = (Calendar) currentCal.clone();
            cal.set(Calendar.DAY_OF_MONTH, 1);
            int firstDay = cal.get(Calendar.DAY_OF_WEEK); // 1=Dom, 2=Lun...
            int offset = (firstDay == Calendar.SUNDAY) ? 6 : firstDay - 2; // ajusta per Lun=0
            int dayNum = position - offset + 1;
            
            cal.add(Calendar.DAY_OF_MONTH, position - offset);
            String dateKey = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.getTime());
            boolean hasEvents = eventsByDate.containsKey(dateKey) && !eventsByDate.get(dateKey).isEmpty();
            boolean isCurrentMonth = cal.get(Calendar.MONTH) == currentCal.get(Calendar.MONTH);
            
            if(dayNum < 1 || dayNum > cal.getActualMaximum(Calendar.DAY_OF_MONTH) || !isCurrentMonth) {
                tv.setText("");
                tv.setTextColor(Color.parseColor("#333344"));
            } else {
                tv.setText(String.valueOf(dayNum));
                tv.setTextColor(hasEvents ? Color.parseColor("#d4af37") : Color.parseColor("#b0b0c0"));
                if(hasEvents) tv.setText(dayNum + "●");
            }
            
            final String clickDate = dateKey;
            tv.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { showAddEventDialog(clickDate); }
            });
            
            return tv;
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
                    HttpURLConnection conn = (HttpURLConnection) new URL("http://wttr.in/Foggia,Italy?format=j1").openConnection();
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
                if (result.equals("ERRORE")) { weatherText.setText("❌ Offline"); return; }
                try {
                    JSONObject json = new JSONObject(result);
                    JSONObject cur = json.getJSONArray("current_condition").getJSONObject(0);
                    JSONArray weather = json.getJSONArray("weather");
                    
                    StringBuilder sb = new StringBuilder();
                    sb.append("✨ ").append(cur.getJSONArray("weatherDesc").getJSONObject(0).getString("value")).append("\n");
                    sb.append("🌡️ ").append(cur.getString("temp_C")).append("°C  ");
                    sb.append("💧 ").append(cur.getString("humidity")).append("%\n\n");
                    sb.append("📅 Prossimi 3 giorni:\n");
                    for (int i = 0; i < Math.min(3, weather.length()); i++) {
                        JSONObject d = weather.getJSONObject(i);
                        sb.append("• ").append(d.getString("date").substring(5)).append(": ");
                        sb.append(d.getJSONArray("hourly").getJSONObject(6).getString("tempC")).append("°C ");
                        sb.append(d.getJSONArray("hourly").getJSONObject(6).getJSONArray("weatherDesc").getJSONObject(0).getString("value")).append("\n");
                    }
                    weatherText.setText(sb.toString().trim());
                } catch (Exception e) { weatherText.setText("⚠️ Errore"); }
            }
        }.execute();
    }

    private void startWeatherRefresh() {
        weatherHandler.postDelayed(new Runnable() {
            @Override public void run() {
                loadWeather();
                weatherHandler.postDelayed(this, 1800000); // 30 minuti
            }
        }, 1800000);
    }
}
