package com.example.sensorrecorder.EventHandlers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.BatteryManager;

import com.example.sensorrecorder.SensorManager;

import java.io.IOException;

public class BatteryEventHandler extends BroadcastReceiver {
    public SensorManager sensorManager = null;

    @Override
    public void onReceive(Context context, Intent intent) {
        int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

        float batteryPct = level * 100 / (float)scale;

        if (sensorManager != null && sensorManager.isRunning){
            try {
                sensorManager.addBatteryState(batteryPct);
            } catch (IOException e) {
                e.printStackTrace();
            }
            if(batteryPct < 16) {
                sensorManager.stopRecording();
                sensorManager.dataProcessor.backup_recording_files();
            }
        }
    }
}
