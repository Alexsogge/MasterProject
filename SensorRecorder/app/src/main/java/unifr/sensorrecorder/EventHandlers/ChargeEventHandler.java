package unifr.sensorrecorder.EventHandlers;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import unifr.sensorrecorder.DataContainer.StaticDataProvider;
import unifr.sensorrecorder.SensorRecordingManager;

public class ChargeEventHandler extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d("charge", "charger event");

        // SensorRecordingManager sensorRecordingManager = MainActivity.mainActivity.sensorService;
        String action = intent.getAction();
        if(action.equals(Intent.ACTION_POWER_CONNECTED)) {
            // Do something when power connected
            StaticDataProvider.getNetworkManager().DoFileUpload();
        }
        else if(action.equals(Intent.ACTION_POWER_DISCONNECTED)) {
            // Do something when power disconnected
            // MainActivity.mainActivity.toggleStartRecording();
            Intent handwashIntent = new Intent(context, SensorRecordingManager.class);
            handwashIntent.putExtra("trigger", "startRecording");
            PendingIntent pintHandWash = PendingIntent.getService(context, 565, handwashIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            try {
                pintHandWash.send();
            } catch (PendingIntent.CanceledException e) {
                e.printStackTrace();
            }
        }
    }

}
