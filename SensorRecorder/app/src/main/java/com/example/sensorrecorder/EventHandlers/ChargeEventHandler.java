package com.example.sensorrecorder.EventHandlers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.example.sensorrecorder.MainActivity;
import com.example.sensorrecorder.SensorManager;

public class ChargeEventHandler extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d("charge", "charger event");

        SensorManager sensorManager = MainActivity.mainActivity.sensorService;
        String action = intent.getAction();
        if(action.equals(Intent.ACTION_POWER_CONNECTED)) {
            // Do something when power connected
            if(sensorManager.isRunning)
                MainActivity.mainActivity.networkManager.DoFileUpload();

        }
        else if(action.equals(Intent.ACTION_POWER_DISCONNECTED)) {
            // Do something when power disconnected
            if(!sensorManager.isRunning)
                MainActivity.mainActivity.toggleStartRecording();
        }
    }

}
