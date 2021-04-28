package unifr.sensorrecorder.Networking;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import unifr.sensorrecorder.R;

import okhttp3.OkHttpClient;

public class NetworkWorker extends Worker {
    public static final int STATUS_ERROR = -1;
    public static final int STATUS_SUCCESS = 1;
    public static final int STATUS_PENDING = 0;
    public static final int STATUS_FINISHED  = 2;
    public static final int STATUS_PROGRESS  = 3;
    public static final String PROGRESS = "PROGRESS";
    public static final String STATUS = "STATUS";
    public final int DO_TOAST = 0;

    protected Context context;
    protected SharedPreferences configs;
    protected Handler uiHandler;
    protected OkHttpClient client;

    public NetworkWorker(@NonNull final Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        this.context = context;
        configs = context.getSharedPreferences(context.getString(R.string.configs), Context.MODE_PRIVATE);
        client = new OkHttpClient();
        uiHandler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message message) {
                // This is where you do your work in the UI thread.
                // Your worker tells you in the message what to do.
                if(message.what == DO_TOAST){
                    Toast.makeText(context, message.obj.toString(), Toast.LENGTH_SHORT).show();
                }
            }
        };
    }

    @NonNull
    @Override
    public Result doWork() {
        return null;
    }

    protected void makeToast(final String text){
        Message message = uiHandler.obtainMessage(DO_TOAST, text);
        message.sendToTarget();
    }

    protected void sendStatus(int statusCode){
        setProgressAsync(new Data.Builder().putInt(STATUS, statusCode).build());
    }
}
