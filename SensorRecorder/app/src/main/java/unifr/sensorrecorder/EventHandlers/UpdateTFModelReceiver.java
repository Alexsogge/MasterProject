package unifr.sensorrecorder.EventHandlers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import unifr.sensorrecorder.DataContainer.StaticDataProvider;
import unifr.sensorrecorder.Networking.NetworkManager;
import unifr.sensorrecorder.R;

public class UpdateTFModelReceiver extends BroadcastReceiver {
    public static final String BROADCAST_ACTION = "UPDATE_TF_MODEL";

    @Override
    public void onReceive(Context context, Intent intent) {
        // Log.e("receiver", "Called update tf receiver");
        if(intent.hasExtra("SKIP")){
            SharedPreferences configs = context.getSharedPreferences(
                    context.getString(R.string.configs), Context.MODE_PRIVATE);
            SharedPreferences.Editor configEditor = configs.edit();
            configEditor.putString(context.getString(R.string.val_do_skip_tf_model), intent.getStringExtra("SKIP"));
            configEditor.apply();
        } else {
            NetworkManager.downloadTFModel(context);
            // StaticDataProvider.getNetworkManager().restartRecording();
            SharedPreferences configs = context.getSharedPreferences(
                    context.getString(R.string.configs), Context.MODE_PRIVATE);
            SharedPreferences.Editor configEditor = configs.edit();
            configEditor.putString(context.getString(R.string.val_do_skip_tf_model), "");
            configEditor.apply();
        }
    }
}
