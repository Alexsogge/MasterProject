package com.example.sensorrecorder;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.provider.Settings;
import android.support.wearable.activity.WearableActivity;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import androidx.wear.widget.WearableLinearLayoutManager;
import androidx.wear.widget.WearableRecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;
import static android.content.pm.PackageManager.PERMISSION_DENIED;


public class MainActivity extends WearableActivity {

    private TextView mTextView;
    Intent intent;
    SensorListenerService sensorService;
    boolean mBound = false;

    private TextView infoText;
    private ProgressBar uploadProgressBar;
    private ArrayList<String> toUploadedFiles = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        turnOffDozeMode(this);
        /*
        WearableRecyclerView myView = (WearableRecyclerView)findViewById(R.id.recycler_launcher_view);
        myView.setEdgeItemsCenteringEnabled(true);
        // myView.setLayoutManager(new WearableLinearLayoutManager(this));
        CustomScrollingLayoutCallback customScrollingLayoutCallback =
                new CustomScrollingLayoutCallback();
        myView.setLayoutManager(
                new WearableLinearLayoutManager(this, customScrollingLayoutCallback));

        */

        infoText = (TextView)findViewById(R.id.infoText);
        uploadProgressBar = (ProgressBar) findViewById(R.id.uploaadProgressBar);
        uploadProgressBar.setMax(100);


        if(ContextCompat.checkSelfPermission(this, WRITE_EXTERNAL_STORAGE) == PERMISSION_DENIED){
            ActivityCompat.requestPermissions(MainActivity.this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    1);

        }else {
            StartRecordService();
            Button uploadButton = (Button)findViewById(R.id.uploadaButton);
            uploadButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // sensorService.UploadSensorData();
                    DoFileUpload();
                }
            });
            final Button startStopButton = (Button)findViewById(R.id.startStopButton);
            startStopButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if(sensorService.isRunning){
                        sensorService.stopRecording();
                        startStopButton.setText("Start");
                    } else {
                        sensorService.startRecording();
                        startStopButton.setText("Stop");
                    }
                }
            });
        }



        //sensorService.registerToManager();
        // Enables Always-on
        //setAmbientEnabled();
    }

    private void StartRecordService(){
        intent = new Intent(this, SensorListenerService.class );
        bindService(intent, connection, Context.BIND_AUTO_CREATE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }


    private void DoFileUpload(){
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        Log.d("sensorrecorder", "Upload sensorData");
        sensorService.prepareUpload();

        infoText.setText("Check connection");
        infoText.invalidate();
        Log.d("sensorrecorder", "Check connection");
        ConnectivityManager connectivityManager =
                (ConnectivityManager) getSystemService(this.CONNECTIVITY_SERVICE);
        Log.d("sensorrecorder", "Get active connection");
        Network activeNetwork = connectivityManager.getActiveNetwork();
        Log.d("sensorrecorder", "active connection is: " + activeNetwork.toString());
        if (activeNetwork == null) {
            Toast.makeText(getBaseContext(), "No connection to internet", Toast.LENGTH_LONG).show();
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            return;
        }

        // Upload files after short time to ensure that everything has written
        Handler postHandler = new Handler();
        postHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    // upload hand wash events
                    for(int j = sensorService.handWashEvents.size() - 1; j >= 0; j--) {
                        JSONObject additional_data = new JSONObject();
                        JSONArray array = new JSONArray();
                        for (int i = 0; i < sensorService.handWashEvents.get(j).size(); i++) {
                            array.put(sensorService.handWashEvents.get(j).get(i)[0]);
                        }
                        additional_data.put("hand_wash_events_acc_" + j, array);
                        array = new JSONArray();
                        for (int i = 0; i < sensorService.handWashEvents.get(j).size(); i++) {
                            array.put(sensorService.handWashEvents.get(j).get(i)[1]);
                        }
                        Log.d("sensorrecorder", "upload: handwash events");
                        infoText.setText("upload " + "hand_wash_events_gyro_" + j);
                        infoText.invalidate();
                        additional_data.put("hand_wash_events_gyro_" + j, array);
                        CharSequence response = new HTTPPostJSON().execute("http://192.168.0.101:8000", additional_data.toString()).get();
                        makeToast(response.toString());
                        if(response.subSequence(0, "success:".length()).equals("success:")){
                            sensorService.handWashEvents.remove(j);
                        }
                    }
                    uploadProgressBar.setVisibility(View.VISIBLE);

                    File tmp_file = null;
                    // Upload all acceleration files
                    String file_name = sensorService.recording_file_acc.getName().replaceFirst("[.][^.]+$", "");
                    for (int i = 0; i < 99; i++) {
                        tmp_file = new File(sensorService.recording_file_path, file_name + "_" + i + ".csv");
                        if (!tmp_file.exists())
                            break;
                        Log.d("sensorrecorder", "upload: " + tmp_file.getName() + " of size " + tmp_file.length());
                        new HTTPPostMultiPartFile().execute("http://192.168.0.101:8000", tmp_file.getName());
                        toUploadedFiles.add(tmp_file.getName());
                    }

                    // Upload all gyroscope files
                    file_name = sensorService.recording_file_gyro.getName().replaceFirst("[.][^.]+$", "");
                    for (int i = 0; i < 99; i++) {
                        tmp_file = new File(sensorService.recording_file_path, file_name + "_" + i + ".csv");
                        if (!tmp_file.exists())
                            break;
                        Log.d("sensorrecorder", "upload: " + tmp_file.getName() + " of size " + tmp_file.length());
                        new HTTPPostMultiPartFile().execute("http://192.168.0.101:8000", tmp_file.getName());
                        toUploadedFiles.add(tmp_file.getName());
                    }
                } catch (Exception e) {
                    Log.e("sensorrecorder", "Error during multipart upload");
                    e.printStackTrace();
                }
            }
        }, 2000);
    }

    private void FinishedFileUpload(String filename, String result){
        toUploadedFiles.remove(filename);
        makeToast(filename + ": " + result);
        if(toUploadedFiles.size() == 0){
            infoText.setText("upload finished");
            uploadProgressBar.setVisibility(View.INVISIBLE);
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            sensorService.registerToManager();
        }
    }

    private ServiceConnection connection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            SensorListenerService.LocalBinder binder = (SensorListenerService.LocalBinder) service;
            sensorService = binder.getService();
            mBound = true;
            sensorService.infoText = (TextView) findViewById(R.id.infoText);
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;
        }
    };

    private void makeToast(final String text){
        Toast.makeText(getBaseContext(), text, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case 1: {

                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.
                    StartRecordService();
                } else {

                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                    Toast.makeText(MainActivity.this, "Permission denied to read your External storage", Toast.LENGTH_SHORT).show();
                }
                return;
            }

            // other 'case' lines to check for other
            // permissions this app might request
        }
    }

    public void turnOffDozeMode(Context context){  //you can use with or without passing context
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent intent = new Intent();
            String packageName = context.getPackageName();
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (pm.isIgnoringBatteryOptimizations(packageName)) // if you want to desable doze mode for this package
                intent.setAction(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
            else { // if you want to enable doze mode
                intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + packageName));
            }
            context.startActivity(intent);
        }
    }

    protected final class HTTPPostMultiPartFile extends AsyncTask<String, String, String> {

        private final MediaType MEDIA_TYPE_CSV = MediaType.parse("text/csv");
        private final OkHttpClient client = new OkHttpClient();
        private final String serverUrl = "http://192.168.0.101:8000";

        private String filename;

        public HTTPPostMultiPartFile(){
            //set context variables if required
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected String doInBackground(String... params) {
            Log.d("sensorrecorder", "Post sensorData");
            String urlString = params[0]; // URL to call
            filename = params[1]; //data to post

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    infoText.setText("upload: " + filename);
                }
            });

            File file = null;
            //String[] q = recording_file_acc.pat.split("/");
            //int idx = q.length - 1;
            try {
                final File path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM + "/android_sensor_recorder/");
                file = new File(path, filename);
                Log.d("sensorrecorder", "Load File "+ filename + " of size:" + file.length());
                uploadMultipartFile(file);
                return "success: uploaded";

            } catch (Exception e) {
                e.printStackTrace();
                return "error: error";
            }
        }

        private void uploadMultipartFile(File file) throws Exception {
            // Use the imgur image upload API as documented at https://api.imgur.com/endpoints/image
            uploadProgressBar.setProgress(0);
            RequestBody requestBody = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    //.addFormDataPart("name", "file")
                    .addPart(Headers.of("Content-Disposition", "form-data; name=\"file\"; filename=\"" + file.getName() +"\""), RequestBody.create(MEDIA_TYPE_CSV, file))
                    //.addFormDataPart("filename", file.getName(),
                    //        RequestBody.create(MEDIA_TYPE_CSV, file))
                    .build();


            ProgressRequestBody progressRequestBody = new ProgressRequestBody(requestBody, new ProgressRequestBody.Listener() {
                @Override
                public void onRequestProgress(long bytesWritten, long contentLength) {
                    float percentage = 100f * bytesWritten / contentLength;
                    // Log.d("sensorrecorder", "Progress2: " + percentage);
                    uploadProgressBar.setProgress((int)percentage);
                    publishProgress(String.valueOf(Math.round(percentage)));
                }
            });


            Request request = new Request.Builder()
                    .url(serverUrl)
                    .post(progressRequestBody)
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()){
                    throw new IOException("Unexpected code " + response);
                } else {
                    file.delete();
                }

                //System.out.println(response.body().string());
            }
        }

        @Override
        protected void onProgressUpdate(final String... values) {
            runOnUiThread(new Runnable() {

                @Override
                public void run() {

                    // infoText.setText(values[0] + "%");
                    uploadProgressBar.setProgress(Integer.parseInt(values[0]));

                }
            });
        }

        @Override
        protected void onPostExecute(String result) {
            FinishedFileUpload(filename, result);
        }
    }

}


class CustomScrollingLayoutCallback extends WearableLinearLayoutManager.LayoutCallback {
    /**
     * How much should we scale the icon at most.
     */
    private static final float MAX_ICON_PROGRESS = 0.65f;

    private float progressToCenter;

    @Override
    public void onLayoutFinished(View child, RecyclerView parent) {

        // Figure out % progress from top to bottom
        float centerOffset = ((float) child.getHeight() / 2.0f) / (float) parent.getHeight();
        float yRelativeToCenterOffset = (child.getY() / parent.getHeight()) + centerOffset;

        // Normalize for center
        progressToCenter = Math.abs(0.5f - yRelativeToCenterOffset);
        // Adjust to the maximum scale
        progressToCenter = Math.min(progressToCenter, MAX_ICON_PROGRESS);

        child.setScaleX(1 - progressToCenter);
        child.setScaleY(1 - progressToCenter);
    }
}

