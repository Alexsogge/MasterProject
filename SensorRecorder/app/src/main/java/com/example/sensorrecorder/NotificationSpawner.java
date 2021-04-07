package com.example.sensorrecorder;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationBuilderWithBuilderAccessor;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

public class NotificationSpawner {

    private static final String RECORDING_CHANNEL_ID = "ForegroundServiceChannel";
    private static final String PREDICTION_CHANNEL_ID = "PredictionChannel";
    private static Intent recordingServiceIntent;

    private static int notificationCounter = 2;

    public static Notification createRecordingNotification(Context context, Intent recordingServiceIntent){
        createNotificationChannel(context, RECORDING_CHANNEL_ID, "Foreground Service Channel");
        Intent notificationIntent = new Intent(context, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(context,
                0, notificationIntent, 0);

        Intent handwashIntent = new Intent(recordingServiceIntent);
        handwashIntent.putExtra("trigger", "handWash");
        PendingIntent pintHandWash = PendingIntent.getService(context, 579, handwashIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        // Intent openIntent = new Intent(intent);
        Intent openIntent = new Intent(context, MainActivity.class);

        openIntent.putExtra("trigger", "open");
        PendingIntent pintOpen = PendingIntent.getActivity(context, 579, openIntent, PendingIntent.FLAG_UPDATE_CURRENT);


        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(context, RECORDING_CHANNEL_ID)
                .setContentTitle("Running")
                .setContentText("Sensor recorder is active")
                //.setStyle(new NotificationCompat.BigTextStyle()
                //        .bigText("Sensor recorder is active"))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setSmallIcon(R.drawable.preference_wrapped_icon)
                .addAction(R.drawable.action_item_background, "HandWash", pintHandWash)
                // .addAction(R.drawable.action_item_background, "Open", pintOpen);
                .setContentIntent(pintOpen);

        return notificationBuilder.build();
    }

    public static void spawnHandWashPredictionNotification(Context context, long timestamp){
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        createNotificationChannel(context, PREDICTION_CHANNEL_ID, "Prediction Channel");

        Intent handwashIntent = new Intent(context, SensorManager.class);
        // Log.d("not", "send notification with ts " + timestamp);
        handwashIntent.putExtra("trigger", "handWashTS");
        handwashIntent.putExtra("timestamp", timestamp);
        PendingIntent pintHandWash = PendingIntent.getService(context, 579, handwashIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        Intent confirmHandWashIntent = new Intent(context, SensorManager.class);
        confirmHandWashIntent.putExtra("trigger", "handWashConfirm");
        confirmHandWashIntent.putExtra("timestamp", timestamp);
        PendingIntent pintConfirmHandWash = PendingIntent.getService(context, 571, confirmHandWashIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        Intent declineHandWashIntent = new Intent(context, SensorManager.class);
        declineHandWashIntent.putExtra("trigger", "handWashDecline");
        declineHandWashIntent.putExtra("timestamp", timestamp);
        PendingIntent pintDeclineHandWash = PendingIntent.getService(context, 572, declineHandWashIntent, PendingIntent.FLAG_UPDATE_CURRENT);


        Intent openIntent = new Intent(context, MainActivity.class);
        openIntent.putExtra("trigger", "open");
        PendingIntent pintOpen = PendingIntent.getActivity(context, 581, openIntent, PendingIntent.FLAG_UPDATE_CURRENT);


        Intent fsIntent = new Intent(context, HandwashEvaluation.class);
        PendingIntent pintFS = PendingIntent.getActivity(context, 0, fsIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, PREDICTION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_hand_wash)
                .setContentTitle("Just washed your hands?")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                //.setContentIntent(pintOpen)
                .addAction(R.drawable.ic_check, "Yes", pintConfirmHandWash)
                .addAction(R.drawable.ic_close, "No", pintDeclineHandWash)
                //.setFullScreenIntent(pintFS, true)
                //.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(true);

        notificationManager.notify(2, builder.build());
    }

    private static void createNotificationChannel(Context context, String chanelID, String chanelName) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    chanelID,
                    chanelName,
                    NotificationManager.IMPORTANCE_DEFAULT
            );

            NotificationManager manager = context.getSystemService(NotificationManager.class);
            manager.createNotificationChannel(serviceChannel);
        }
    }

}
