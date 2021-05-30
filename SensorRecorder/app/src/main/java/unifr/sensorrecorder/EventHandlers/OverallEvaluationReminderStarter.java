package unifr.sensorrecorder.EventHandlers;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.util.Log;

import unifr.sensorrecorder.NotificationSpawner;

import static android.content.Context.ALARM_SERVICE;

public class OverallEvaluationReminderStarter extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Intent reminderReceiver = new Intent(context, OverallEvaluationReminder.class);
        PendingIntent reminderPint = PendingIntent.getBroadcast(context, NotificationSpawner.DAILY_REMINDER_REQUEST_CODE, reminderReceiver, PendingIntent.FLAG_UPDATE_CURRENT);
        AlarmManager am = (AlarmManager) context.getSystemService(ALARM_SERVICE);
        am.setRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis(), 5 * 60 * 1000, reminderPint);
    }
}
