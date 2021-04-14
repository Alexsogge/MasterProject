package com.example.sensorrecorder.EventHandlers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.BatteryManager;

import com.example.sensorrecorder.SensorRecordingManager;

import java.io.IOException;

public class BatteryEventHandler extends BroadcastReceiver {
    public SensorRecordingManager sensorRecordingManager = null;

    @Override
    public void onReceive(Context context, Intent intent) {
        int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

        float batteryPct = level * 100 / (float)scale;

        if (sensorRecordingManager != null && sensorRecordingManager.isRunning){
            try {
                sensorRecordingManager.addBatteryState(batteryPct);
            } catch (IOException e) {
                e.printStackTrace();
            }
            if(batteryPct < 16) {
                sensorRecordingManager.directlyStopRecording();
                // sensorManager.dataProcessor.backup_recording_files();
            }
        }
    }
}
