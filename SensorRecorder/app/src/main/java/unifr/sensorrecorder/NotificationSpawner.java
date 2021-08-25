package unifr.sensorrecorder;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.util.Collection;

import unifr.sensorrecorder.Evaluation.OverallEvaluation;
import unifr.sensorrecorder.EventHandlers.EvaluationReceiver;
import unifr.sensorrecorder.EventHandlers.OverallEvaluationReminder;
import unifr.sensorrecorder.EventHandlers.UpdateTFModelReceiver;

import static android.content.Context.VIBRATOR_SERVICE;

public class NotificationSpawner {

    private static final String RECORDING_CHANNEL_ID = "ForegroundServiceChannel";
    private static final String PREDICTION_CHANNEL_ID = "PredictionChannel";
    private static final String OverallEvaluation_CHANNEL_ID = "OverallEvaluationChannel";
    private static final String UPDATETFMODEL_CHANNEL_ID = "UpdateTFModelChannel";
    private static final String UPLOAD_CHANNEL_ID = "UPLOADChannel";
    public static final int DAILY_REMINDER_REQUEST_CODE = 13;
    public static final int EVALUATION_REQUEST_CODE = 12;
    public static final int UPDATETFMODEL_REQUEST_CODE = 14;
    public static final int UPLOAD_REQUEST_CODE = 15;
    public static final int DAILY_REMINDER_STARTER_REQUEST_CODE = 16;
    public static final int FG_NOTIFICATION_ID = 1;
    public static final int FG_PAUSED_NOTIFICATION_ID = 2;

    public static Notification createRecordingNotification(Context context, Intent recordingServiceIntent){
        Intent handwashIntent = new Intent(recordingServiceIntent);
        handwashIntent.putExtra("trigger", "handWash");
        PendingIntent pintHandWash = PendingIntent.getService(context, 579, handwashIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(context, RECORDING_CHANNEL_ID)
                .setContentTitle(context.getResources().getString(R.string.not_running))
                .setContentText(context.getResources().getString(R.string.not_sen_rec_active))
                //.setStyle(new NotificationCompat.BigTextStyle()
                //        .bigText("Sensor recorder is active"))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setSmallIcon(R.drawable.ic_hand_wash)
                .addAction(R.drawable.ic_hand_wash, context.getResources().getString(R.string.not_btn_hw), pintHandWash)
                ;

        return notificationBuilder.build();
    }

    public static Notification createRecordingPausedNotification(Context context, Intent recordingServiceIntent){
        Intent handwashIntent = new Intent(recordingServiceIntent);
        handwashIntent.putExtra("trigger", "startRecording");
        PendingIntent pintHandWash = PendingIntent.getService(context, 580, handwashIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(context, RECORDING_CHANNEL_ID)
                .setContentTitle(context.getResources().getString(R.string.not_paused))
                .setContentText(context.getResources().getString(R.string.not_sen_rec_not_active))
                //.setStyle(new NotificationCompat.BigTextStyle()
                //        .bigText("Sensor recorder is active"))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setSmallIcon(R.drawable.ic_hand_wash)
                .addAction(R.drawable.ic_hand_wash, context.getResources().getString(R.string.btn_start), pintHandWash)
                ;

        return notificationBuilder.build();
    }

    public static void spawnHandWashPredictionNotification(Context context, long timestamp){
        //NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        try {
            closeOldPredictionNotification(notificationManager);
        } catch (PendingIntent.CanceledException e) {
            e.printStackTrace();
        }

        /*
        Intent handwashIntent = new Intent(context, SensorRecordingManager.class);
        // Log.d("not", "send notification with ts " + timestamp);
        handwashIntent.putExtra("trigger", "handWashTS");
        handwashIntent.putExtra("timestamp", timestamp);
        PendingIntent pintHandWash = PendingIntent.getService(context, 579, handwashIntent, PendingIntent.FLAG_UPDATE_CURRENT);
         */
        /*
        Intent confirmHandWashIntent = new Intent(context, SensorManager.class);
        confirmHandWashIntent.putExtra("trigger", "handWashConfirm");
        confirmHandWashIntent.putExtra("timestamp", timestamp);
        PendingIntent pintConfirmHandWash = PendingIntent.getService(context, 571, confirmHandWashIntent, PendingIntent.FLAG_UPDATE_CURRENT);
         */

        Intent confirmHandWashIntent = new Intent(context, EvaluationReceiver.class);
        confirmHandWashIntent.putExtra("trigger", "handWashConfirm");
        confirmHandWashIntent.putExtra("timestamp", timestamp);
        //TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
        //stackBuilder.addNextIntentWithParentStack(confirmHandWashIntent);
//        confirmHandWashIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
//                | Intent.FLAG_ACTIVITY_CLEAR_TASK);


        PendingIntent pintConfirmHandWash = PendingIntent.getBroadcast(context, EVALUATION_REQUEST_CODE, confirmHandWashIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        //PendingIntent pintConfirmHandWash = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);

        Intent declineHandWashIntent = new Intent(context, EvaluationReceiver.class);
        declineHandWashIntent.putExtra("trigger", "handWashDecline");
        declineHandWashIntent.putExtra("timestamp", timestamp);
        PendingIntent pintDeclineHandWash = PendingIntent.getBroadcast(context, EVALUATION_REQUEST_CODE+1, declineHandWashIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        Intent closeHandWashIntent = new Intent(context, EvaluationReceiver.class);
        closeHandWashIntent.putExtra("trigger", "close");
        closeHandWashIntent.putExtra("timestamp", timestamp);
        PendingIntent pintClose = PendingIntent.getBroadcast(context, EVALUATION_REQUEST_CODE+2, closeHandWashIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        /*
        Intent openIntent = new Intent(context, MainActivity.class);
        openIntent.putExtra("trigger", "open");
        PendingIntent pintOpen = PendingIntent.getActivity(context, EVALUATION_REQUEST_CODE, openIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        */
        /*
        Intent fsIntent = new Intent(context, HandwashEvaluation.class);
        PendingIntent pintFS = PendingIntent.getActivity(context, 0, fsIntent, PendingIntent.FLAG_UPDATE_CURRENT);
         */

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, PREDICTION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_hand_wash)
                .setContentTitle(context.getResources().getString(R.string.not_just_washed_hands))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                //.setContentIntent(pintOpen)
                .addAction(R.drawable.ic_check, context.getResources().getString(R.string.not_btn_yes), pintConfirmHandWash)
                .addAction(R.drawable.ic_close, context.getResources().getString(R.string.not_btn_no), pintDeclineHandWash)
                //.setFullScreenIntent(pintFS, true)
                //.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setDeleteIntent(pintClose)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setTimeoutAfter(1000 * 60 * 10)
                //.setAutoCancel(true)
        ;
        notificationManager.notify(EVALUATION_REQUEST_CODE, builder.build());
        // Vibrator vibrator = (Vibrator) context.getSystemService(VIBRATOR_SERVICE);
        // vibrator.vibrate(VibrationEffect.createWaveform(new long[]{1000, 1000, 700, 500}, -1));
    }


    public static void showOverallEvaluationNotification(Context context){
        Intent startEvalIntent = new Intent(context, OverallEvaluation.class);
        startEvalIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent resultPendingIntent = PendingIntent.getActivity(context, DAILY_REMINDER_REQUEST_CODE, startEvalIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        Uri alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(context, OverallEvaluation_CHANNEL_ID)
                .setContentTitle(context.getResources().getString(R.string.not_oar_title))
                .setContentText(context.getResources().getString(R.string.not_oar_text))
                //.setStyle(new NotificationCompat.BigTextStyle()
                //        .bigText("Sensor recorder is active"))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setSmallIcon(R.drawable.ic_hand_wash)
                // .setVibrate(new long[]{1000, 500, 1000, 500})
                // .setSound(alarmSound)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                //.addAction(R.drawable.action_item_background, context.getResources().getString(R.string.not_btn_hw), pintHandWash)
                // .addAction(R.drawable.action_item_background, "Open", pintOpen);
                .setContentIntent(resultPendingIntent);
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(DAILY_REMINDER_REQUEST_CODE, notificationBuilder.build());

        // manual vibration cause vibration defined in notification doesn't always work
        Vibrator vibrator = (Vibrator) context.getSystemService(VIBRATOR_SERVICE);
        vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0, 1000, 700, 1000}, -1));
    }

    public static void closeOverallEvaluationNotification(Context context){
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.cancel(DAILY_REMINDER_REQUEST_CODE);
    }

    public static void showUpdateTFModelNotification(Context context, String modelName){
        // Log.d("not", "show update tf model notification");
        //Intent startUpdateIntent = new Intent(UpdateTFModelReceiver.BROADCAST_ACTION);
        Intent startUpdateIntent = new Intent(context, UpdateTFModelReceiver.class);
        PendingIntent startUpdatePendingIntent = PendingIntent.getBroadcast(context, 43, startUpdateIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        // Intent skipUpdateIntent = new Intent(UpdateTFModelReceiver.BROADCAST_ACTION);
        Intent skipUpdateIntent = new Intent(context, UpdateTFModelReceiver.class);
        skipUpdateIntent.putExtra("SKIP", modelName);
        PendingIntent skipUpdatePendingIntent = PendingIntent.getBroadcast(context, 44, skipUpdateIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(context, UPDATETFMODEL_CHANNEL_ID)
                .setContentTitle( context.getResources().getString(R.string.not_update_tf_title))
                .setContentText( context.getResources().getString(R.string.not_update_tf_text) + modelName + "?")
                //.setStyle(new NotificationCompat.BigTextStyle()
                //        .bigText("Sensor recorder is active"))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setSmallIcon(R.drawable.ic_hand_wash)
                // .setVibrate(new long[]{1000, 500, 1000, 500})
                // .setSound(alarmSound)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .addAction(R.drawable.action_item_background, context.getResources().getString(R.string.not_btn_yes), startUpdatePendingIntent)
                .addAction(R.drawable.action_item_background, context.getResources().getString(R.string.not_btn_no), skipUpdatePendingIntent)
                .setAutoCancel(true)
                // .setContentIntent(resultPendingIntent)
                ;
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(UPDATETFMODEL_REQUEST_CODE, notificationBuilder.build());
    }

    public static void showUploadNotification(Context context, String notText){
        // Log.d("not", "show update tf model notification");

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(context, UPLOAD_CHANNEL_ID)
                .setContentTitle(context.getResources().getString(R.string.not_upload_title))
                .setContentText(notText)
                //.setStyle(new NotificationCompat.BigTextStyle()
                //        .bigText("Sensor recorder is active"))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setSmallIcon(R.drawable.ic_hand_wash)
                .setOnlyAlertOnce(true)
                // .setVibrate(new long[]{1000, 500, 1000, 500})
                // .setSound(alarmSound)
                .setProgress(0, 0, true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                ;
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(UPLOAD_REQUEST_CODE, notificationBuilder.build());
    }

    private static void closeOldPredictionNotification(NotificationManager notificationManager) throws PendingIntent.CanceledException {
        StatusBarNotification[] activeNotifications = notificationManager.getActiveNotifications();
        for (StatusBarNotification notification: activeNotifications){
            if(notification.getId() == 2){
                notification.getNotification().deleteIntent.send();
                break;
            }
        }
        notificationManager.cancel(EVALUATION_REQUEST_CODE);
    }

    private static void createNotificationChannel(Context context, String chanelID, String chanelName, long[] vibrationPattern) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    chanelID,
                    chanelName,
                    NotificationManager.IMPORTANCE_HIGH
            );
            if(vibrationPattern != null) {
                serviceChannel.enableVibration(true);
                serviceChannel.setVibrationPattern(vibrationPattern);
            }

            NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            manager.createNotificationChannel(serviceChannel);
        }
    }

    public static void createChannels(Context context){
        long[] vibrationPattern = new long[]{1000, 1000, 500, 1000};
        createNotificationChannel(context, RECORDING_CHANNEL_ID, "Recording Channel", vibrationPattern);
        createNotificationChannel(context, PREDICTION_CHANNEL_ID, "Prediction Channel", null);
        createNotificationChannel(context, OverallEvaluation_CHANNEL_ID, "OverallEvaluation Channel", null);
        createNotificationChannel(context, UPDATETFMODEL_CHANNEL_ID, "Update TF model Channel", vibrationPattern);
        createNotificationChannel(context, UPLOAD_CHANNEL_ID, "Upload Channel", null);
    }

    public static void deleteAllChannels(Context context){
        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.deleteNotificationChannel(RECORDING_CHANNEL_ID);
        notificationManager.deleteNotificationChannel(PREDICTION_CHANNEL_ID);
        notificationManager.deleteNotificationChannel(OverallEvaluation_CHANNEL_ID);
        notificationManager.deleteNotificationChannel(UPDATETFMODEL_CHANNEL_ID);
    }

}
