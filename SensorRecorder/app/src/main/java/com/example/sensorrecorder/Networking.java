package com.example.sensorrecorder;

import android.app.Activity;
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
    private Handler uiHandler;

    private String serverAddress = "";

    private CountDownLatch sensorStopLatch;


    public Networking(final Activity mainActivity, SensorManager sensorService, SharedPreferences configs){
        this.mainActivity = mainActivity;
        this.sensorService = sensorService;
        infoText = (TextView)mainActivity.findViewById(R.id.infoText);
        this.configs = configs;
    }

    public void DoFileUpload(){
        // check if server token exists. If not request it, else proceed upload
        if(configs.getString(mainActivity.getString(R.string.conf_serverToken), "").equals("")) {
            requestServerToken();
        } else {
            // keep the screen on to prevent system goes to sleep and interrupts process
            mainActivity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            Log.e("networking", "start upload");
            // stop recording and create backup
            sensorStopLatch = new CountDownLatch(1);
            sensorService.waitForStopRecording(sensorStopLatch);
            executor.execute(new UploadTask());
        }
    }

    private void UploadFiles(){
        // update info text
        infoText.setText("Check connection");
        infoText.invalidate();

        // check if there is network connection
        ConnectivityManager connectivityManager =
                (ConnectivityManager) mainActivity.getSystemService(mainActivity.CONNECTIVITY_SERVICE);

        Network activeNetwork = connectivityManager.getActiveNetwork();

        // signalise if there is no network connection and abort upload
        if (activeNetwork == null) {
            Toast.makeText(mainActivity.getBaseContext(), "No connection to internet", Toast.LENGTH_LONG).show();
            mainActivity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            return;
        }
        // initialize progress bar for upload status uf current file
        uploadProgressBar = (ProgressBar) mainActivity.findViewById(R.id.uploaadProgressBar);
        uploadProgressBar.setMax(100);

        // Upload files after short time to ensure that everything has written
        Handler postHandler = new Handler();
        postHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                uploadProgressBar.setVisibility(View.VISIBLE);
                // go through all stored data files and upload them
                for(HashMap.Entry<String, String> keyValue: directoryUploadTokens.entrySet()) {

                    for (DataContainer container : sensorService.dataProcessor.allDataContainers) {
                        for (File dataFile : container.getAllVariantsInSubDirectory(new File(keyValue.getKey()))) {
                            new Networking.HTTPPostMultiPartFile().execute(serverAddress, keyValue.getValue(), dataFile.getPath());
                            toUploadedFiles.add(dataFile.getPath());
                        }
                    }
                }
            }
        }, 2000);
    }

    private void makeToast(final String text){
        mainActivity.runOnUiThread(new Runnable() {
            public void run() {
                Toast.makeText(mainActivity, text, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void receivedUploadToken(){
        startUploadIfReady();
    }

    private void startUploadIfReady(){
        for(String directory: toUploadDirectories){
            if(!directoryUploadTokens.containsKey(directory)) {
                return;
            }
        }
        UploadFiles();
    }

    private void finishedFileUpload(String filePath, String fileName, String result){
        // get uploaded file and remove it from queue
        toUploadedFiles.remove(filePath);
        // show status of uploaded file
        makeToast(fileName + ": " + result);

        // check if upload of all files is done
        if(toUploadedFiles.size() == 0){
            infoText.setText("upload finished");
            uploadProgressBar.setVisibility(View.INVISIBLE);
            mainActivity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
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

            // we need an upload token from the server to signalise which files belong together
            directoryUploadTokens.clear();
            toUploadDirectories.clear();
            for(File directory: DataContainer.getSubdirectories()) {
                toUploadDirectories.add(directory.getPath());
                new Networking.HTTPGetUploadToken().execute(directory.getPath());
            }

            startUploadIfReady();
        }
    }

    public void downloadTFModel(){
        new Networking.HTTPGetTFModel().execute();
    }

    public void requestServerToken(){
        new Networking.HTTPGetServerToken().execute();
    }

    protected final class HTTPPostMultiPartFile extends AsyncTask<String, String, String> {

        private final MediaType MEDIA_TYPE_CSV = MediaType.parse("text/csv");
        private final MediaType MEDIA_TYPE_ZIP = MediaType.parse("application/zip, application/octet-stream");
        private final MediaType MEDIA_TYPE_MKV = MediaType.parse("video/x-matroska, audio/x-matroska");
        private final MediaType MEDIA_TYPE_3GP = MediaType.parse("video/3gpp, audio/3gpp, video/3gpp2, audio/3gpp2");
        private final OkHttpClient client = new OkHttpClient();
        private final String serverUrlSuffix = "/recording/new/?uuid=";

        private String filePath;
        private String fileName;

        public HTTPPostMultiPartFile(){
            //set context variables if required
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected String doInBackground(String... params) {
            String uploadToken = params[1];
            filePath = params[2]; //data to post

            File file = null;
            try {
                // final File path = DataContainer.recordingFilePath;
                file = new File(filePath);
                fileName = file.getName();
                mainActivity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        infoText.setText("upload: " + fileName);
                    }
                });
                uploadMultipartFile(file, uploadToken);
                return "success: uploaded";

            } catch (Exception e) {
                e.printStackTrace();
                return "error: error";
            }
        }

        private void uploadMultipartFile(File file, String uploadToken) throws Exception {
            uploadProgressBar.setProgress(0);
            MediaType media_type = MEDIA_TYPE_ZIP;
            if (file.getName().substring(file.getName().length()-4).equals(".mkv"))
                media_type = MEDIA_TYPE_MKV;
            if (file.getName().substring(file.getName().length()-4).equals(".csv"))
                media_type = MEDIA_TYPE_CSV;
            if (file.getName().substring(file.getName().length()-4).equals(".3gp"))
                media_type = MEDIA_TYPE_3GP;
            RequestBody requestBody = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addPart(Headers.of("Content-Disposition", "form-data; name=\"file\"; filename=\"" + file.getName() +"\""), RequestBody.create(media_type, file))
                    .build();

            // we use a ProgressRequestBody to get events for current upload status and visualize this with a progressbar
            ProgressRequestBody progressRequestBody = new ProgressRequestBody(requestBody, new ProgressRequestBody.Listener() {
                @Override
                public void onRequestProgress(long bytesWritten, long contentLength) {
                    float percentage = 100f * bytesWritten / contentLength;
                    uploadProgressBar.setProgress((int)percentage);
                    publishProgress(String.valueOf(Math.round(percentage)));
                }
            });

            String serverUrl = configs.getString(mainActivity.getString(R.string.conf_serverName), "")
                                + serverUrlSuffix
                                + uploadToken;
            Request request = new Request.Builder()
                    .url(serverUrl)
                    .addHeader("Authorization", "Bearer " + configs.getString(mainActivity.getString(R.string.conf_serverToken), ""))
                    .post(progressRequestBody)
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()){
                    throw new IOException("Unexpected code " + response);
                } else {
                    File directory = file.getParentFile();
                    // remove file after upload
                    file.delete();
                    // test if this is last file
                    if (directory.list().length == 1) {
                        directory.delete();
                    }
                }
            }
        }

        @Override
        protected void onProgressUpdate(final String... values) {
            mainActivity.runOnUiThread(new Runnable() {

                @Override
                public void run() {
                    uploadProgressBar.setProgress(Integer.parseInt(values[0]));
                }
            });
        }

        @Override
        protected void onPostExecute(String result) {
            finishedFileUpload(filePath, fileName, result);
        }
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
                    throw new IOException("Unexpected code " + response);
                } else {
                    String jsonData = response.body().string();
                    JSONObject Jobject = new JSONObject(jsonData);
                    String status = Jobject.getString("status");
                    if (status.equals("grant")){
                        SharedPreferences.Editor configEditor = configs.edit();
                        configEditor.putString(mainActivity.getString(R.string.conf_serverToken), Jobject.getString("token"));
                        configEditor.apply();
                        makeToast("Authentication granted");
                        mainActivity.runOnUiThread(new Runnable() {

                            @Override
                            public void run() {
                                DoFileUpload();
                            }
                        });
                    } else{
                        makeToast("Not authenticated: " + Jobject.getString("msg"));
                    }
                }

                //System.out.println(response.body().string());
            } catch (ConnectException e){
                makeToast("Can't connect to server");
            } catch (IOException | JSONException e) {
                e.printStackTrace();
                makeToast("Can't connect to server");
            }
            return null;
        }
    }

    protected final class HTTPGetUploadToken extends AsyncTask<String, String, String> {

        private final OkHttpClient client = new OkHttpClient();

        @Override
        protected String doInBackground(String... strings) {
            String directory = strings[0];
            String serverName = configs.getString(mainActivity.getString(R.string.conf_serverName), "");
            String serverToken = configs.getString(mainActivity.getString(R.string.conf_serverToken), "");
            Request request = new Request.Builder()
                    .url(serverName + "/recording/new/")
                    .addHeader("Authorization", "Bearer " + serverToken)
                    .build();
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()){
                    throw new IOException("Unexpected code " + response);
                } else {
                    String jsonData = response.body().string();
                    JSONObject Jobject = new JSONObject(jsonData);
                    String status = Jobject.getString("status");
                    if (status.equals("success")){
                        directoryUploadTokens.put(directory, Jobject.getString("uuid"));
                        makeToast("Got Token");
                    } else{
                        makeToast("Error during request");
                    }
                }

                //System.out.println(response.body().string());
            } catch (ConnectException e){
                makeToast("Can't connect to server");
            } catch (IOException | JSONException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(String result) {
            receivedUploadToken();
        }
    }


    protected final class HTTPGetTFModel extends AsyncTask<String, String, String> {

        private final OkHttpClient client = new OkHttpClient();

        @Override
        protected String doInBackground(String... strings) {
            String serverName = configs.getString(mainActivity.getString(R.string.conf_serverName), "");
            String downloadUrl = serverName + "/tfmodel/get/latest/";
            Request request = new Request.Builder().url(downloadUrl).build();
            Response response = null;
            try {
                response = client.newCall(request).execute();
                if (!response.isSuccessful()) {
                    makeToast("Failed to download file: " + response);
                }
                File path = HandWashDetection.modelFilePath;
                if(!path.exists())
                    path.mkdirs();
                File modelFile = new File(path, HandWashDetection.modelName);
                FileOutputStream fos = new FileOutputStream(modelFile);
                fos.write(response.body().bytes());
                fos.close();
                makeToast("Downloaded new Model");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            return null;
        }
    }
}
