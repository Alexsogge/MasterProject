package unifr.sensorrecorder.EventHandlers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import unifr.sensorrecorder.MainActivity;
import unifr.sensorrecorder.SensorRecordingManager;

public class ChargeEventHandler extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d("charge", "charger event");

        SensorRecordingManager sensorRecordingManager = MainActivity.mainActivity.sensorService;
        String action = intent.getAction();
        if(action.equals(Intent.ACTION_POWER_CONNECTED)) {
            // Do something when power connected
            if(sensorRecordingManager.isRunning)
                MainActivity.mainActivity.networkManager.DoFileUpload();

        }
        else if(action.equals(Intent.ACTION_POWER_DISCONNECTED)) {
            // Do something when power disconnected
            if(!sensorRecordingManager.isRunning)
                MainActivity.mainActivity.toggleStartRecording();
        }
    }

}
