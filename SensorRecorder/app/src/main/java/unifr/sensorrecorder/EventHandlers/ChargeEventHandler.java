package unifr.sensorrecorder.EventHandlers;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.util.Log;

import unifr.sensorrecorder.DataContainer.StaticDataProvider;
import unifr.sensorrecorder.SensorRecordingManager;

public class ChargeEventHandler extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        // Log.d("charge", "charger event");

        // SensorRecordingManager sensorRecordingManager = MainActivity.mainActivity.sensorService;
        
        String action = intent.getAction();
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = context.registerReceiver(null, ifilter);
        int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL;

        int plugged = batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
        boolean bCharging= plugged == BatteryManager.BATTERY_PLUGGED_AC || plugged == BatteryManager.BATTERY_PLUGGED_USB;

        int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

        float batteryPct = level * 100 / (float)scale;
        // Log.d("battery", "Trigger: " + action + "   Status:" + isCharging + "    Plugged: " + bCharging);
        // Log.d("battery", "1. " + (action.equals(Intent.ACTION_POWER_DISCONNECTED) && !isCharging && !bCharging) + "   2. " + (action.equals(Intent.ACTION_BATTERY_CHANGED) && !isCharging && !bCharging) + "->" + ((action.equals(Intent.ACTION_POWER_DISCONNECTED) && !isCharging && !bCharging) || (action.equals(Intent.ACTION_BATTERY_CHANGED) && !isCharging && !bCharging)));
        if(action.equals(Intent.ACTION_POWER_CONNECTED)) {
            // Do something when power connected
            StaticDataProvider.getNetworkManager().DoFileUpload();
        }
        else if((action.equals(Intent.ACTION_POWER_DISCONNECTED) && !isCharging && !bCharging) || (action.equals(Intent.ACTION_BATTERY_CHANGED) && !isCharging && !bCharging)) {
            // Do something when power disconnected
            // MainActivity.mainActivity.toggleStartRecording();
            // Log.d("battery", "startRecording");
            if (!StaticDataProvider.getIsRunning() && batteryPct >= 20.0) {
                Intent handwashIntent = new Intent(context, SensorRecordingManager.class);
                handwashIntent.setPackage(context.getPackageName());
                handwashIntent.putExtra("trigger", "startRecording");
                PendingIntent pintHandWash = PendingIntent.getService(context, 565, handwashIntent,
                      PendingIntent.FLAG_UPDATE_CURRENT |
                      (android.os.Build.VERSION.SDK_INT >= 23 ?
                       PendingIntent.FLAG_IMMUTABLE : 0));

                try {
                    pintHandWash.send();
                } catch (PendingIntent.CanceledException e) {
                    e.printStackTrace();
                }
            }
        }
    }

}
