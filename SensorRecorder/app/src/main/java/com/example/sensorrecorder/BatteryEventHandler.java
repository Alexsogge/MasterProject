package com.example.sensorrecorder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.BatteryManager;
import android.os.Bundle;
import android.util.Log;

import java.io.IOException;

public class BatteryEventHandler extends BroadcastReceiver {
    SensorListenerService sensorListenerService = null;

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i("sensorrecorder", "Received battery event" + intent.getDataString() + "  |  " + intent.getExtras() + " state: " + intent.getBooleanExtra("battery_low", false));
        /*
        Bundle bundle = intent.getExtras();
        if (bundle != null) {
            for (String key : bundle.keySet()) {
                Log.e("sensorrecorder", key + " : " + (bundle.get(key) != null ? bundle.get(key) : "NULL"));
            }
        }*/

        int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

        float batteryPct = level * 100 / (float)scale;

        Log.i("sensorrecorder", "battery charge" + batteryPct);



        if (sensorListenerService != null && sensorListenerService.isRunning){
            try {
                sensorListenerService.addBatteryState(batteryPct);
            } catch (IOException e) {
                e.printStackTrace();
            }
            if(batteryPct < 16) {
                sensorListenerService.stopRecording();
                sensorListenerService.backup_recording_files();
            }
        }
    }
}
