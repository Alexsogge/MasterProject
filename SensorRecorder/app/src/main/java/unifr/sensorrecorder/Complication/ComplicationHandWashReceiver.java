package unifr.sensorrecorder.Complication;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.wearable.complications.ProviderUpdateRequester;
import android.util.Log;

import unifr.sensorrecorder.DataContainer.StaticDataProvider;
import unifr.sensorrecorder.EventHandlers.EvaluationReceiver;
import unifr.sensorrecorder.NotificationSpawner;
import unifr.sensorrecorder.SensorRecordingManager;

public class ComplicationHandWashReceiver extends BroadcastReceiver {

    private static final String EXTRA_PROVIDER_COMPONENT =
            "unifr.android.wearable.watchface.provider.action.PROVIDER_COMPONENT";
    private static final String EXTRA_COMPLICATION_ID =
            "unifr.android.wearable.watchface.provider.action.COMPLICATION_ID";

    static final int MAX_NUMBER = 20;
    static final String COMPLICATION_PROVIDER_PREFERENCES_FILE_KEY =
            "unifr.android.wearable.watchface.COMPLICATION_PROVIDER_PREFERENCES_FILE_KEY";

    @Override
    public void onReceive(Context context, Intent intent) {

        Bundle extras = intent.getExtras();
        ComponentName provider = extras.getParcelable(EXTRA_PROVIDER_COMPONENT);
        int complicationId = extras.getInt(EXTRA_COMPLICATION_ID);

        Log.d("CompRec", "Complication hand wash on receive");
        Intent handwashIntent = new Intent(context, SensorRecordingManager.class);
        handwashIntent.putExtra("trigger", "handWash");
        handwashIntent.setPackage(context.getPackageName());

        PendingIntent pintHandWash = PendingIntent
          .getService(context,
                      578,
                      handwashIntent,
                      PendingIntent.FLAG_UPDATE_CURRENT |
                      (android.os.Build.VERSION.SDK_INT >= 23 ?
                       PendingIntent.FLAG_IMMUTABLE : 0));

        try {
            pintHandWash.send();
        } catch (PendingIntent.CanceledException e) {
            e.printStackTrace();
        }

        // Request an update for the complication that has just been tapped.
        ProviderUpdateRequester requester = new ProviderUpdateRequester(context, provider);
        requester.requestUpdate(complicationId);

    }

    /**
     * Returns a pending intent, suitable for use as a tap intent, that causes a complication to be
     * toggled and updated.
     */
    static PendingIntent getToggleIntent(
            Context context, ComponentName provider, int complicationId) {
        Intent intent = new Intent(context, ComplicationHandWashReceiver.class);
        Log.d("comp", "Prov:" + provider);
        Log.d("comp", "compID:" + complicationId);
        intent.putExtra(EXTRA_PROVIDER_COMPONENT, provider);
        intent.putExtra(EXTRA_COMPLICATION_ID, complicationId);
        StaticDataProvider.setCounterComplicationId(complicationId);

        Log.d("CompRec", "get toggle hand wash intent");
        // Pass complicationId as the requestCode to ensure that different complications get
        // different intents.
        return PendingIntent.getBroadcast(
                context, complicationId, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /**
     * Returns the key for the shared preference used to hold the current state of a given
     * complication.
     */
    static String getPreferenceKey(ComponentName provider, int complicationId) {
        return provider.getClassName() + complicationId;
    }

}
