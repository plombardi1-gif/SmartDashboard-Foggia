package com.smartdashboard;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
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
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private TextView clockText, dateText, weatherText;
    private ListView calendarList, todoList;
    private Button btnAddEvt, btnAddTodo;
    private Handler clockHandler;
    private SharedPreferences prefs;
    private ArrayList<String> events, todos;
    private ArrayAdapter<String> calendarAdapter, todoAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        clockText = (TextView) findViewById(R.id.clock);
        dateText = (TextView) findViewById(R.id.date);
        weatherText = (TextView) findViewById(R.id.weatherText);
        calendarList = (ListView) findViewById(R.id.calendarList);
        todoList = (ListView) findViewById(R.id.todoList);
        btnAddEvt = (Button) findViewById(R.id.btnAddEvt);
        btnAddTodo = (Button) findViewById(R.id.btnAddTodo);

        prefs = getSharedPreferences("dashboard_data", MODE_PRIVATE);
        loadLists();
        setupAdapters();

        clockHandler = new Handler();
        startClock();
        loadWeather();

        // Pulsanti +
        btnAddEvt.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showAddDialog("appointment", -1); }
        });
        btnAddTodo.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showAddDialog("todo", -1); }
        });

        // Click su elementi
        calendarList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> p, View v, int pos, long id) { showAddDialog("appointment", pos); }
        });
        calendarList.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override public boolean onItemLongClick(AdapterView<?> p, View v, int pos, long id) { deleteItem("appointment", pos); return true; }
        });

        todoList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> p, View v, int pos, long id) { toggleDone("todo", pos); }
        });
        todoList.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override public boolean onItemLongClick(AdapterView<?> p, View v, int pos, long id) { deleteItem("todo", pos); return true; }
        });
    }

    private void loadLists() {
        try {
            String json = prefs.getString("events", "[]");
            JSONArray arr = new JSONArray(json);
            events = new ArrayList<String>();
            for(int i=0; i<arr.length(); i++) events.add(arr.getString(i));
        } catch(Exception e) { events = new ArrayList<String>(); }

        try {
            String json = prefs.getString("todos", "[]");
            JSONArray arr = new JSONArray(json);
            todos = new ArrayList<String>();
            for(int i=0; i<arr.length(); i++) todos.add(arr.getString(i));
        } catch(Exception e) { todos = new ArrayList<String>(); }
    }

    private void setupAdapters() {
        calendarAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, events);
        calendarList.setAdapter(calendarAdapter);
        todoAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, todos);
        todoList.setAdapter(todoAdapter);
    }

    private void saveLists() {
        try {
            JSONArray arr = new JSONArray();
            for(String s : events) arr.put(s);
            prefs.edit().putString("events", arr.toString()).commit();
        } catch(Exception e){}
        try {
            JSONArray arr = new JSONArray();
            for(String s : todos) arr.put(s);
            prefs.edit().putString("todos", arr.toString()).commit();
        } catch(Exception e){}
    }

    private void showAddDialog(final String type, final int position) {
        EditText input = new EditText(this);
        input.setHint("Scrivi qui...");
        new AlertDialog.Builder(this)
            .setTitle(type.equals("appointment") ? "Modifica Appuntamento" : "Modifica Nota")
            .setView(input)
            .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int w) {
                    String val = input.getText().toString().trim();
                    if(!val.isEmpty()) {
                        if(type.equals("appointment")) {
                            if(position == -1) events.add(0, val); else events.set(position, val);
                        } else {
                            if(position == -1) todos.add(0, val); else todos.set(position, val);
                        }
                        saveLists(); setupAdapters();
                    }
                }
            }).setNegativeButton("Annulla", null).show();
    }

    private void deleteItem(String type, int position) {
        if(type.equals("appointment")) events.remove(position); else todos.remove(position);
        saveLists(); setupAdapters();
    }

    private void toggleDone(String type, int position) {
        if(type.equals("todo")) {
            String t = todos.get(position);
            todos.set(position, t.startsWith("✅ ") ? t.substring(2) : "✅ "+t);
            saveLists(); setupAdapters();
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
                    while ((line = in.readLine()) != null) {
                        sb.append(line);
                    }
                    in.close();
                    return sb.toString();
                } catch (Exception e) {
                    return "ERRORE";
                }
            }
            
            @Override
            protected void onPostExecute(String result) {
                if (result.equals("ERRORE")) {
                    weatherText.setText("❌ Offline");
                    return;
                }
                try {
                    JSONObject json = new JSONObject(result);
                    JSONObject cur = json.getJSONArray("current_condition").getJSONObject(0);
                    JSONArray weather = json.getJSONArray("weather");
                    
                    String text = "🌡️ Oggi: " + cur.getString("temp_C") + "°C\n";
                    text += "💧 " + cur.getJSONArray("weatherDesc").getJSONObject(0).getString("value") + "\n\n";
                    
                    for (int i = 0; i < Math.min(3, weather.length()); i++) {
                        JSONObject d = weather.getJSONObject(i);
                        text += "📅 " + d.getString("date") + "\n";
                        text += "   Min: " + d.getString("mintempC") + "°C | Max: " + d.getString("maxtempC") + "°C\n\n";
                    }
                    weatherText.setText(text.trim());
                } catch (Exception e) {
                    weatherText.setText("⚠️ Errore meteo");
                }
            }
        }.execute();
    }
}
