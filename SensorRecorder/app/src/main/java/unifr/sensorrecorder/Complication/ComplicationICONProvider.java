package unifr.sensorrecorder.Complication;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.support.wearable.complications.ComplicationData;
import android.support.wearable.complications.ComplicationManager;
import android.support.wearable.complications.ComplicationProviderService;
import android.util.Log;

import unifr.sensorrecorder.R;
import unifr.sensorrecorder.SensorRecordingManager;

public class ComplicationICONProvider extends ComplicationProviderService{

    private static final String TAG = "ComplicationICONProvider";
    /*
     * Called when a complication has been activated. The method is for any one-time
     * (per complication) set-up.
     *
     * You can continue sending data for the active complicationId until onComplicationDeactivated()
     * is called.
     */
    @Override
    public void onComplicationActivated(
            int complicationId, int dataType, ComplicationManager complicationManager) {
        Log.d(TAG, "onComplicationActivated(): " + complicationId);
    }

    /*
     * Called when the complication needs updated data from your provider. There are four scenarios
     * when this will happen:
     *
     *   1. An active watch face complication is changed to use this provider
     *   2. A complication using this provider becomes active
     *   3. The period of time you specified in the manifest has elapsed (UPDATE_PERIOD_SECONDS)
     *   4. You triggered an update from your own class via the
     *       ProviderUpdateRequester.requestUpdate() method.
     */
    @Override
    public void onComplicationUpdate(
            int complicationId, int dataType, ComplicationManager complicationManager) {
        Log.d(TAG, "onComplicationUpdate() id: " + complicationId);

        Intent handwashIntent = new Intent(this, SensorRecordingManager.class);
        handwashIntent.putExtra("trigger", "handWash");
        handwashIntent.setPackage(this.getPackageName());
        PendingIntent pintHandWash = PendingIntent.getService(this, 578, handwashIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        ComplicationData complicationData = null;

        switch (dataType) {
            case ComplicationData.TYPE_ICON:
                complicationData =
                        new ComplicationData.Builder(ComplicationData.TYPE_ICON)
                                .setIcon(Icon.createWithResource(this, R.drawable.ic_hand_wash))
                                .setTapAction(pintHandWash)
                                //.setShortText(ComplicationText.plainText(predictionsText))
                                .build();
                break;
            default:
                if (Log.isLoggable(TAG, Log.WARN)) {
                    Log.w(TAG, "Unexpected complication type " + dataType);
                }
        }

        if (complicationData != null) {
            complicationManager.updateComplicationData(complicationId, complicationData);
        } else {
            // If no data is sent, we still need to inform the ComplicationManager, so the update
            // job can finish and the wake lock isn't held any longer than necessary.
            complicationManager.noUpdateRequired(complicationId);
        }
    }

    /*
     * Called when the complication has been deactivated.
     */
    @Override
    public void onComplicationDeactivated(int complicationId) {
        Log.d(TAG, "onComplicationDeactivated(): " + complicationId);
    }
}
