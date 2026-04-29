package com.smartdashboard;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Vibrator;

public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            // Vibra per 3 secondi
            Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null) vibrator.vibrate(3000);

            // Suona notifica di sistema
            Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            MediaPlayer player = new MediaPlayer();
            player.setAudioStreamType(AudioManager.STREAM_NOTIFICATION);
            player.setDataSource(context, notification);
            player.prepare();
            player.start();
            player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                public void onCompletion(MediaPlayer mp) { mp.release(); }
            });

            // Mostra dialog
            Intent dialogIntent = new Intent(context, AlarmDialogActivity.class);
            dialogIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(dialogIntent);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

// Activity per mostrare il dialog della sveglia
class AlarmDialogActivity extends Activity {
    @Override
    protected void onCreate(android.os.Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("⏰ Promemoria!");
            builder.setMessage("Il tuo promemoria è scaduto.");
            builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    finish();
                }
            });
            builder.setCancelable(false);
            builder.create().show();
        } catch (Exception e) { 
            e.printStackTrace(); 
            finish(); 
        }
    }
}
