package unifr.sensorrecorder.EventHandlers;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.icu.util.Calendar;
import android.util.Log;

import unifr.sensorrecorder.ConfActivity;
import unifr.sensorrecorder.DataContainer.StaticDataProvider;
import unifr.sensorrecorder.NotificationSpawner;
import unifr.sensorrecorder.R;

import static android.content.Context.ALARM_SERVICE;

public class OverallEvaluationReminder extends BroadcastReceiver {
    private static final int MAX_REMINDERS = 6;
    private static final int REPEAT_DELAY = 1000 * 60 * 10; // 10 minutes

    @Override
    public void onReceive(Context context, Intent intent) {
        Calendar currentDate = Calendar.getInstance();
        currentDate.setTimeInMillis(System.currentTimeMillis());
        int numReminders = intent.getIntExtra("numCalls", 0);

        if (currentDate.get(Calendar.HOUR_OF_DAY) >= OverallEvaluationReminderStarter.REMINDER_HOUR) {
            SharedPreferences configs = context.getSharedPreferences(
                    context.getString(R.string.configs), Context.MODE_PRIVATE);
            if (configs.getBoolean(context.getString(R.string.conf_open_evaluation), true)) {
                NotificationSpawner.showOverallEvaluationNotification(context.getApplicationContext());
                if (numReminders < MAX_REMINDERS) {
                    setNextReminder(context, numReminders + 1);
                }
            }
        }
    }

    public static void setNextReminder(Context context, int numCalls){
        Intent reminderReceiver = new Intent(context, OverallEvaluationReminder.class);
        reminderReceiver.putExtra("numCalls", numCalls);
        PendingIntent reminderPint = PendingIntent.getBroadcast(context, NotificationSpawner.DAILY_REMINDER_REQUEST_CODE, reminderReceiver, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        AlarmManager am = (AlarmManager) context.getSystemService(ALARM_SERVICE);
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + (REPEAT_DELAY * numCalls), reminderPint);
    }
}
