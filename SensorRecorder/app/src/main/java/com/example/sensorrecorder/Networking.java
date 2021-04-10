package com.example.sensorrecorder;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.os.AsyncTask;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.WorkRequest;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.sensorrecorder.dataContainer.DataContainer;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class Networking {
    public SensorManager sensorService;
    private Activity mainActivity;
    private final Executor executor = Executors.newSingleThreadExecutor();

    private TextView infoText;
    private ProgressBar uploadProgressBar;
    private ArrayList<String> toUploadDirectories = new ArrayList<>();
    private HashMap<String, String> directoryUploadTokens = new HashMap<String, String>();

    private ArrayList<String> toUploadedFiles = new ArrayList<>();
    private SharedPreferences configs;

    private String serverAddress = "";

    private CountDownLatch sensorStopLatch;


    public Networking(final Activity mainActivity, SensorManager sensorService, SharedPreferences configs){
        this.mainActivity = mainActivity;
        this.sensorService = sensorService;
        infoText = (TextView)mainActivity.findViewById(R.id.infoText);
        this.configs = configs;
    }

    public void DoFileUpload(){
        // update info text
        infoText.setText(mainActivity.getString(R.string.it_check_conn));
        infoText.invalidate();

        // check if there is network connection
        ConnectivityManager connectivityManager =
                (ConnectivityManager) mainActivity.getSystemService(mainActivity.CONNECTIVITY_SERVICE);

        Network activeNetwork = connectivityManager.getActiveNetwork();

        // signalise if there is no network connection and abort upload
        if (activeNetwork == null) {
            Toast.makeText(mainActivity.getBaseContext(), mainActivity.getString(R.string.toast_no_inet_conn), Toast.LENGTH_LONG).show();
            mainActivity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            return;
        }

        // check if server token exists. If not request it, else proceed upload
        if(configs.getString(mainActivity.getString(R.string.conf_serverToken), "").equals("")) {
            requestServerToken();
        } else {
            // keep the screen on to prevent system goes to sleep and interrupts process
            // mainActivity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            Log.e("networking", "start upload");

            // stop recording and create backup
            sensorStopLatch = new CountDownLatch(1);
            sensorService.waitForStopRecording(sensorStopLatch);
            executor.execute(new UploadTask());
        }
    }


    private void makeToast(final String text){
        mainActivity.runOnUiThread(new Runnable() {
            public void run() {
                Toast.makeText(mainActivity, text, Toast.LENGTH_SHORT).show();
            }
        });
    }



    private class UploadTask implements Runnable{

        @Override
        public void run() {
            // wait for finished sensors
            try {
                sensorStopLatch.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            Constraints constraints = new Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build();

            WorkRequest uploadWorkRequest = new OneTimeWorkRequest.Builder(UploadWorker.class)
                    .setConstraints(constraints)
                    .setBackoffCriteria(
                            BackoffPolicy.LINEAR,
                            5,
                            TimeUnit.MINUTES)
                    .addTag("upload")
                    .build();
            Log.d("net", "enque upload worker");
            WorkManager.getInstance(mainActivity).enqueue(uploadWorkRequest);

        }
    }



    public void downloadTFModel(){
        new Networking.HTTPGetTFModel().execute();
    }

    public void requestServerToken(){
        new Networking.HTTPGetServerToken().execute();
    }



    protected final class HTTPGetServerToken extends AsyncTask<String, String, String> {

        private final OkHttpClient client = new OkHttpClient();

        @Override
        protected String doInBackground(String... strings) {
            String serverName = configs.getString(mainActivity.getString(R.string.conf_serverName), "");
            String userIdentifier = configs.getString(mainActivity.getString(R.string.conf_userIdentifier), "");
            Request request = new Request.Builder()
                    .url(serverName + "/tokenauth/request/?identifier="+userIdentifier)
                    .build();
            Log.d("http", "request token from " + serverName + "/tokenauth/request/?identifier="+userIdentifier);
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()){
                    throw new IOException(mainActivity.getString(R.string.str_unexpected_code) + response);
                } else {
                    String jsonData = response.body().string();
                    JSONObject Jobject = new JSONObject(jsonData);
                    String status = Jobject.getString("status");
                    if (status.equals("grant")){
                        SharedPreferences.Editor configEditor = configs.edit();
                        configEditor.putString(mainActivity.getString(R.string.conf_serverToken), Jobject.getString("token"));
                        configEditor.apply();
                        makeToast(mainActivity.getString(R.string.toast_auth_granted));
                        mainActivity.runOnUiThread(new Runnable() {

                            @Override
                            public void run() {
                                DoFileUpload();
                            }
                        });
                    } else{
                        makeToast(mainActivity.getString(R.string.toast_not_auth) + Jobject.getString("msg"));
                    }
                }

                //System.out.println(response.body().string());
            } catch (ConnectException e){
                makeToast(mainActivity.getString(R.string.toast_cant_conn_to_server));
            } catch (IOException | JSONException e) {
                e.printStackTrace();
                makeToast(mainActivity.getString(R.string.toast_cant_conn_to_server));
            }
            return null;
        }
    }




    protected final class HTTPGetTFModel extends AsyncTask<String, String, String> {

        private final OkHttpClient client = new OkHttpClient();

        @Override
        protected String doInBackground(String... strings) {
            String serverName = configs.getString(mainActivity.getString(R.string.conf_serverName), "");
            try {
                downloadTFFile(serverName + "/tfmodel/get/latest/", HandWashDetection.modelName);
                downloadTFFile(serverName + "/tfmodel/get/settings/", HandWashDetection.modelSettingsName);
                makeToast(mainActivity.getString(R.string.toast_downloaded_tf));
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }

        private void downloadTFFile(String url, String filename) throws IOException {
            Request request = new Request.Builder().url(url).build();
            Response response = null;
            response = client.newCall(request).execute();
            if (!response.isSuccessful()) {
                makeToast(mainActivity.getString(R.string.toast_failed_to_dl_file) + response);
            }
            File path = HandWashDetection.modelFilePath;
            if(!path.exists())
                path.mkdirs();
            File tfFile = new File(path, filename);
            FileOutputStream fos = new FileOutputStream(tfFile);
            fos.write(response.body().bytes());
            fos.close();
        }
    }
}
