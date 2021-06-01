package unifr.sensorrecorder.EventHandlers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import unifr.sensorrecorder.DataContainer.StaticDataProvider;
import unifr.sensorrecorder.NotificationSpawner;
import unifr.sensorrecorder.R;

public class OverallEvaluationReminder extends BroadcastReceiver {
    private static final int MAX_REMINDERS = 3;
    @Override
    public void onReceive(Context context, Intent intent) {

        int numReminders = StaticDataProvider.getOverallReminderCalls();
        if (numReminders < MAX_REMINDERS){
            StaticDataProvider.setOverallReminderCalls(numReminders+1);
            NotificationSpawner.showOverallEvaluationNotification(context.getApplicationContext());
        } else {
            NotificationSpawner.stopRepeatingOverallEvaluationReminder(context.getApplicationContext());
        }
    }
}
