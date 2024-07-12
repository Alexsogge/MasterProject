package unifr.sensorrecorder.Networking;

import android.Manifest;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import org.json.JSONException;
import org.json.JSONObject;

import unifr.sensorrecorder.HandWashDetection;
import unifr.sensorrecorder.NotificationSpawner;
import unifr.sensorrecorder.R;
import unifr.sensorrecorder.SensorRecordingManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class NetworkManager {
    public SensorRecordingManager sensorService;
    private Context context;
    private static final Executor executor = Executors.newSingleThreadExecutor();
    private boolean initialized = false;

    private TextView infoText;
    private SharedPreferences configs;
    private CountDownLatch sensorStopLatch;

    public void initialize(Context context, SensorRecordingManager sensorService, TextView infoText){
        if(!initialized) {
            this.context = context;
            this.sensorService = sensorService;
            //this.infoText = (TextView) context.findViewById(R.id.infoText);
            this.infoText = infoText;
            initialized = true;
            configs = context.getSharedPreferences(context.getString(R.string.configs), Context.MODE_PRIVATE);
        }
    }

    public void DoFileUpload(){
        // stop recording and create backup
        sensorStopLatch = new CountDownLatch(1);
        if (sensorService != null) {
            sensorService.waitForStopRecording(sensorStopLatch);
            // update info text
            setInfoText(context.getString(R.string.btn_stopping));
        } else {
            sensorStopLatch.countDown();
        }


        // check if server was specified
        if(configs.getString(context.getString(R.string.conf_serverName), "").equals("")){
            Toast.makeText(context, context.getString(R.string.toast_no_server_name), Toast.LENGTH_LONG).show();
            return;
        }

        Log.e("networking", "start upload");
        executor.execute(new UploadTaskStarter());
    }


    private static void makeToast(final String text, final Context context){
        /*
        context.runOnUiThread(new Runnable() {
            public void run() {
                Toast.makeText(context, text, Toast.LENGTH_LONG).show();
            }
        });
         */
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(context, text, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void setInfoText(final String text){
        if(infoText != null) {
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                public void run() {
                    infoText.setVisibility(View.VISIBLE);
                    infoText.setText(text);
                    infoText.invalidate();
                }
            });
        }
    }

    private class UploadTaskStarter implements Runnable{

        @Override
        public void run() {
            // wait for finished sensors
            try {
                sensorStopLatch.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            // update info text

            setInfoText(context.getString(R.string.it_start_upload));


            OneTimeWorkRequest uploadWorkRequest = buildUploadWorkRequest();
            WorkManager.getInstance(context).cancelAllWork();
            WorkManager.getInstance(context).pruneWork();
            if(configs.getString(context.getString(R.string.conf_serverToken), "").equals("")) {
                OneTimeWorkRequest serverTokenWorkRequest = buildGetServerTokenWorkRequest();
                WorkManager.getInstance(context)
                        .beginWith(serverTokenWorkRequest)
                        .then(uploadWorkRequest)
                        .enqueue();
            } else {
                NotificationSpawner.showUploadNotification(context, context.getString(R.string.not_upload_connection));
                WorkManager.getInstance(context).enqueue(uploadWorkRequest);
            }
        }
    }

    public void restartRecording(){
        executor.execute(new RestartRecordingTask());
    }

    private class RestartRecordingTask implements Runnable{
        @Override
        public void run() {
            sensorStopLatch = new CountDownLatch(1);
            sensorService.waitForStopRecording(sensorStopLatch, false);
            // wait for finished sensors
            try {
                sensorStopLatch.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            // ((MainActivity) context).toggleStartRecording();
            sendStartRecordingIntent();
        }
    }

    private void sendStartRecordingIntent(){
        Log.e("networking", "send start recording");
        Intent handwashIntent = new Intent(context, SensorRecordingManager.class);
        handwashIntent.setPackage(context.getPackageName());
        handwashIntent.putExtra("trigger", "startRecording");
        PendingIntent pintHandWash = PendingIntent.getService(context, 565, handwashIntent,
                      PendingIntent.FLAG_UPDATE_CURRENT |
                      (android.os.Build.VERSION.SDK_INT >= 23 ?
                       PendingIntent.FLAG_IMMUTABLE : 0));
        try {
            pintHandWash.send();
        } catch (PendingIntent.CanceledException e) {
            e.printStackTrace();
        }
    }

    private OneTimeWorkRequest buildUploadWorkRequest(){
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        OneTimeWorkRequest uploadWorkRequest = new OneTimeWorkRequest.Builder(UploadWorker.class)
                .setConstraints(constraints)
                .setBackoffCriteria(
                        BackoffPolicy.LINEAR,
                        5,
                        TimeUnit.MINUTES)
                .addTag("uploadWorker")
                .build();
        return uploadWorkRequest;
    }

    private OneTimeWorkRequest buildGetServerTokenWorkRequest(){
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        OneTimeWorkRequest serverTokenWorkRequest = new OneTimeWorkRequest.Builder(ServerTokenWorker.class)
                .setConstraints(constraints)
                .setBackoffCriteria(
                        BackoffPolicy.LINEAR,
                        5,
                        TimeUnit.MINUTES)
                .addTag("serverTokenWorker")
                .build();
        return serverTokenWorkRequest;
    }


    public static void downloadTFModel(Context context){
        SharedPreferences configs = context.getSharedPreferences(context.getString(R.string.configs), Context.MODE_PRIVATE);
        executor.execute(new NetworkManager.HTTPGetTFModel(configs, context, false));
    }

    public static void downloadTFModelSettings(Context context){
        SharedPreferences configs = context.getSharedPreferences(context.getString(R.string.configs), Context.MODE_PRIVATE);
        executor.execute(new NetworkManager.HTTPGetTFModel(configs, context, true));
    }

    public static void checkForTFModelUpdate(Context context){
        Log.d("detection", "check for update");
        if(ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            SharedPreferences configs = context.getSharedPreferences(context.getString(R.string.configs), Context.MODE_PRIVATE);
            executor.execute(new NetworkManager.HTTPCheckForNewTFModel(configs, context));
        }
    }

    public void requestServerToken(){
        OneTimeWorkRequest serverTokenWorkRequest = buildGetServerTokenWorkRequest();
        WorkManager.getInstance(context).enqueue(serverTokenWorkRequest);
    }

    protected static class HTTPGetTFModel implements Runnable {

        private final OkHttpClient client = new OkHttpClient();
        private SharedPreferences configs;
        private Context context;
        private boolean justSettings;

        public HTTPGetTFModel(SharedPreferences configs, Context context, boolean justSettings){
            this.configs = configs;
            this.context = context;
            this.justSettings = justSettings;
        }

        @Override
        public void run() {
            String serverName = configs.getString(context.getString(R.string.conf_serverName), "");
            String androidId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
            try {
                if(!justSettings) {
                    String tfFileName = downloadTFFile(serverName + "/tfmodel/get/latest/?androidid=" + androidId, HandWashDetection.modelName);
                    makeToast(context.getString(R.string.toast_downloaded_tf) + ":\n" + tfFileName, context);
                    SharedPreferences.Editor configEditor = configs.edit();
                    configEditor.putString(context.getApplicationContext().getString(R.string.val_current_tf_model), tfFileName);
                    configEditor.apply();
                }
                downloadTFFile(serverName + "/tfmodel/get/settings/?androidid=" + androidId, HandWashDetection.modelSettingsName);

            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private String downloadTFFile(String url, String targetFilename) throws IOException {
            String fileName = "default.tflite";
            String fileExtension = "tflite";
            Pattern p = Pattern.compile(".+filename=(.+?)\\..*");
            Request request = new Request.Builder().url(url).build();
            Response response = null;
            response = client.newCall(request).execute();
            if (!response.isSuccessful()) {
                makeToast(context.getString(R.string.toast_failed_to_dl_file) + response, context);
                throw new IOException(response.toString());
            } else {
                String contentDisposition = response.header("content-disposition");
                if(contentDisposition != null) {
                    fileName = contentDisposition.split("filename=")[1];
                    fileExtension = fileName.split("\\.(?=[^\\.]+$)")[1];
                }
                File path = new File(context.getExternalFilesDir(Environment.DIRECTORY_DCIM),
                               "hand_wash_prediction");
                if (!path.exists())
                    path.mkdirs();
                File tfFile = new File(path, targetFilename + '.' + fileExtension);
                FileOutputStream fos = new FileOutputStream(tfFile);
                fos.write(response.body().bytes());
                fos.close();
            }

            return fileName;
        }
    }


   protected static class HTTPCheckForNewTFModel implements Runnable{

        private final OkHttpClient client = new OkHttpClient();
        private SharedPreferences configs;
        private Context context;


        public HTTPCheckForNewTFModel(SharedPreferences configs, Context context){
            this.configs = configs;
            this.context = context;
        }

        @Override
        public void run() {
            String serverName = configs.getString(context.getString(R.string.conf_serverName), "");
            String androidId = Settings.Secure.getString( context.getContentResolver(), Settings.Secure.ANDROID_ID);
            try {
                String tfFileName = getActiveTFFile(serverName + "/tfmodel/check/latest/?androidid=" + androidId);
                if(tfFileName.length() > 0){
                    String currentModel = configs.getString(context.getString(R.string.val_current_tf_model), "");
                    String doSkip = configs.getString(context.getString(R.string.val_do_skip_tf_model), "");
                    // Log.d("net", "active: " + tfFileName + " using " + currentModel + " skip " + doSkip);
                    if(!tfFileName.equals(currentModel)){
                        if(!tfFileName.equals(doSkip)){
                            if (configs.getBoolean(context.getApplicationContext().getString(R.string.conf_auto_update_tf), false))
                                downloadTFModel(context);
                            else if (configs.getBoolean(context.getApplicationContext().getString(R.string.conf_check_for_tf_update), true))
                                NotificationSpawner.showUpdateTFModelNotification(context.getApplicationContext(), tfFileName);
                        }
                    } else if(!currentModel.equals("")){
                        downloadTFModelSettings(context);
                    }
                    SharedPreferences.Editor configEditor = configs.edit();
                    configEditor.putString(context.getApplicationContext().getString(R.string.val_last_checked_tf_model),tfFileName);
                    configEditor.apply();
                }

            } catch (IOException | JSONException e) {
                e.printStackTrace();
            }
        }

        private String getActiveTFFile(String url) throws IOException, JSONException {
            Request request = new Request.Builder().url(url).build();
            Response response = null;
            response = client.newCall(request).execute();
            if (!response.isSuccessful()) {
                // makeToast(context.getString(R.string.toast_failed_to_dl_file) + response);
                Log.e("NETWORKMANAGER", response.toString());
            } else {
                String jsonData = response.body().string();
                JSONObject jObject = new JSONObject(jsonData);
                if(jObject.has("activeModel"))
                    return jObject.getString("activeModel");
            }
            return "";
        }

   }

}
