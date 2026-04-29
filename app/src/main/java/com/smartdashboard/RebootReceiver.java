package com.smartdashboard;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
public class RebootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context ctx, Intent intent) {
        try { Runtime.getRuntime().exec(new String[]{"su", "-c", "reboot"}); } catch(Exception ignored) {}
    }
}
