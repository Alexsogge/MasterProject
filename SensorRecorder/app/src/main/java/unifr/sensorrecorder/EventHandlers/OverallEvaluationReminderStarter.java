package unifr.sensorrecorder.EventHandlers;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.icu.util.Calendar;
import android.os.SystemClock;
import android.util.Log;

import unifr.sensorrecorder.DataContainer.StaticDataProvider;
import unifr.sensorrecorder.NotificationSpawner;
import unifr.sensorrecorder.R;

import static android.content.Context.ALARM_SERVICE;

public class OverallEvaluationReminderStarter extends BroadcastReceiver {
    public static final int REMINDER_HOUR = 18;

    @Override
    public void onReceive(Context context, Intent intent) {
        // Log.e("alarm", "start overall reminder");
        Intent reminderReceiver = new Intent(context, OverallEvaluationReminder.class);
        reminderReceiver.putExtra("numCalls", 0);
        PendingIntent reminderPint = PendingIntent.getBroadcast(context, NotificationSpawner.DAILY_REMINDER_REQUEST_CODE, reminderReceiver, PendingIntent.FLAG_UPDATE_CURRENT);
        // AlarmManager am = (AlarmManager) context.getSystemService(ALARM_SERVICE);
        // am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis(), reminderPint);
        OverallEvaluationReminder.setNextReminder(context.getApplicationContext(), 1);
        setOverallEvaluationReminder(context);
        NotificationSpawner.showOverallEvaluationNotification(context.getApplicationContext());
    }

    private void setOverallEvaluationReminder(Context context){
        Calendar targetDate = Calendar.getInstance();
        targetDate.setTimeInMillis(System.currentTimeMillis());
        if(targetDate.get(Calendar.HOUR_OF_DAY) >= REMINDER_HOUR)
            targetDate.add(Calendar.DATE, 1);
        targetDate.set(Calendar.HOUR_OF_DAY, REMINDER_HOUR);
        targetDate.set(Calendar.MINUTE, 0);
        targetDate.set(Calendar.SECOND, 0);

        // Log.e("alarm", "set new alarm for " + targetDate.get(Calendar.DATE) + "  " +targetDate.get(Calendar.HOUR_OF_DAY));

        Intent reminderReceiver = new Intent(context, OverallEvaluationReminderStarter.class);
        PendingIntent reminderPint = PendingIntent.getBroadcast(context, NotificationSpawner.DAILY_REMINDER_STARTER_REQUEST_CODE, reminderReceiver, PendingIntent.FLAG_UPDATE_CURRENT);
        AlarmManager am = (AlarmManager) context.getSystemService(ALARM_SERVICE);
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, targetDate.getTimeInMillis(), reminderPint);
    }

    public static void stopReminderAlarm(Context context){
        Intent reminderReceiver = new Intent(context, OverallEvaluationReminder.class);
        PendingIntent reminderPint = PendingIntent.getBroadcast(context, NotificationSpawner.DAILY_REMINDER_REQUEST_CODE, reminderReceiver, PendingIntent.FLAG_UPDATE_CURRENT);
        AlarmManager am = (AlarmManager) context.getSystemService(ALARM_SERVICE);
        am.cancel(reminderPint);
    }
}
