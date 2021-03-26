package com.example.sensorrecorder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.Toast;

public class ChargeEventHandler extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d("charge", "charger event");

        SensorListenerService sensorListenerService = MainActivity.mainActivity.sensorService;
        String action = intent.getAction();
        if(action.equals(Intent.ACTION_POWER_CONNECTED)) {
            // Do something when power connected
            if(sensorListenerService.isRunning)
                MainActivity.mainActivity.toggleStopRecording();

        }
        else if(action.equals(Intent.ACTION_POWER_DISCONNECTED)) {
            // Do something when power disconnected
            if(!sensorListenerService.isRunning)
                MainActivity.mainActivity.toggleStartRecording();
        }
    }

}
