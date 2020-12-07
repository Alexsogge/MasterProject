package com.example.sensorrecorder;

import android.app.Activity;
import android.net.ConnectivityManager;
import android.net.Network;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.URL;
import java.util.ArrayList;

import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class Networking {

    private Activity mainActivity;
    public SensorListenerService sensorService;

    private TextView infoText;
    private ProgressBar uploadProgressBar;
    public ArrayList<String> toBackupFiles = new ArrayList<>();
    private ArrayList<String> toUploadedFiles = new ArrayList<>();


    public Networking(Activity mainActivity, SensorListenerService sensorService){
        this.mainActivity = mainActivity;
        this.sensorService = sensorService;
        infoText = (TextView)mainActivity.findViewById(R.id.infoText);
        uploadProgressBar = (ProgressBar) mainActivity.findViewById(R.id.uploaadProgressBar);
        uploadProgressBar.setMax(100);
    }

    public void DoFileUpload(){
        mainActivity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        Log.d("sensorrecorder", "Upload sensorData");
        sensorService.prepareUpload();


    }

    private void UploadFiles(){
        infoText.setText("Check connection");
        infoText.invalidate();
        Log.d("sensorrecorder", "Check connection");
        ConnectivityManager connectivityManager =
                (ConnectivityManager) mainActivity.getSystemService(mainActivity.CONNECTIVITY_SERVICE);
        Log.d("sensorrecorder", "Get active connection");
        Network activeNetwork = connectivityManager.getActiveNetwork();
        Log.d("sensorrecorder", "active connection is: " + activeNetwork.toString());
        if (activeNetwork == null) {
            Toast.makeText(mainActivity.getBaseContext(), "No connection to internet", Toast.LENGTH_LONG).show();
            mainActivity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
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
                        CharSequence response = new Networking.HTTPPostJSON().execute("http://192.168.0.101:8000", additional_data.toString()).get();
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
                        new Networking.HTTPPostMultiPartFile().execute("http://192.168.0.101:8000", tmp_file.getName());
                        toUploadedFiles.add(tmp_file.getName());
                    }

                    // Upload all gyroscope files
                    file_name = sensorService.recording_file_gyro.getName().replaceFirst("[.][^.]+$", "");
                    for (int i = 0; i < 99; i++) {
                        tmp_file = new File(sensorService.recording_file_path, file_name + "_" + i + ".csv");
                        if (!tmp_file.exists())
                            break;
                        Log.d("sensorrecorder", "upload: " + tmp_file.getName() + " of size " + tmp_file.length());
                        new Networking.HTTPPostMultiPartFile().execute("http://192.168.0.101:8000", tmp_file.getName());
                        toUploadedFiles.add(tmp_file.getName());
                    }
                } catch (Exception e) {
                    Log.e("sensorrecorder", "Error during multipart upload");
                    e.printStackTrace();
                }
            }
        }, 2000);
    }

    private void makeToast(final String text){
        Toast.makeText(mainActivity.getBaseContext(), text, Toast.LENGTH_LONG).show();
    }

    public void finishedFileBackup(String fileName){
        toBackupFiles.remove(fileName);
        if (toBackupFiles.size() == 0){
            UploadFiles();
        }
    }

    private void FinishedFileUpload(String filename, String result){
        toUploadedFiles.remove(filename);
        makeToast(filename + ": " + result);
        if(toUploadedFiles.size() == 0){
            infoText.setText("upload finished\n" + sensorService.doubleTimeStamps + " doubles");
            sensorService.doubleTimeStamps = 0;
            uploadProgressBar.setVisibility(View.INVISIBLE);
            mainActivity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            sensorService.registerToManager();
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

            mainActivity.runOnUiThread(new Runnable() {
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
            mainActivity.runOnUiThread(new Runnable() {

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


    protected final class HTTPPostJSON extends AsyncTask<String, String, String> {

        public HTTPPostJSON(){
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
            String data = params[1]; //data to post
            HttpURLConnection connection = null;
            String boundary =  "*****"+Long.toString(System.currentTimeMillis())+"*****";

            try {
                URL url = new URL(urlString);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                // conn.setRequestProperty("Content-Type", "application/json;charset=UTF-8;boundary=\"+boundary");
                conn.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
                conn.setRequestProperty("Accept","application/json");
                conn.setDoOutput(true);
                conn.setDoInput(true);

                JSONObject jsonParam = new JSONObject(data);

                Log.i("JSON", jsonParam.toString());
                DataOutputStream os = new DataOutputStream(conn.getOutputStream());
                // os.writeBytes("--" + boundary + "\r\n");
                //os.writeBytes(URLEncoder.encode(jsonParam.toString(), "UTF-8"));
                os.writeBytes(jsonParam.toString());

                os.flush();
                os.close();

                Log.i("STATUS", String.valueOf(conn.getResponseCode()));
                Log.i("MSG" , conn.getResponseMessage());

                conn.disconnect();
                return "success: uploaded data";
            } catch (ProtocolException ex) {
                ex.printStackTrace();
                return "error: ProtocolException";
            } catch (IOException ex) {
                ex.printStackTrace();
                return "error: IOExeception";
            } catch (Exception e) {
                e.printStackTrace();
                return "error: error";
            }
        }
    }
}
