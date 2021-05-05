package unifr.sensorrecorder.EventHandlers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import unifr.sensorrecorder.NotificationSpawner;

public class OverallEvaluationReminder extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d("not", "ringring");
        NotificationSpawner.showOverallEvaluationNotification(context);
    }
}
