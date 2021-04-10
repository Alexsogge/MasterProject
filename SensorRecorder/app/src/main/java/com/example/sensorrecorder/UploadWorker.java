package com.example.sensorrecorder;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.sensorrecorder.dataContainer.DataContainer;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.net.ConnectException;
import java.util.ArrayList;
import java.util.HashMap;

import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class UploadWorker extends Worker {
    private final int DO_TOAST = 0;
    private Context context;
    private SharedPreferences configs;

    private TextView infoText;
    private ProgressBar uploadProgressBar;
    private ArrayList<String> toUploadDirectories = new ArrayList<>();
    private HashMap<String, String> directoryUploadTokens = new HashMap<String, String>();
    private Handler uiHandler;

    private ArrayList<String> toUploadedFiles = new ArrayList<>();

    public UploadWorker(
            @NonNull final Context context,
            @NonNull WorkerParameters params) {
        super(context, params);
        this.context = context;
        configs = context.getSharedPreferences(context.getString(R.string.configs), Context.MODE_PRIVATE);
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

    @SuppressLint("WrongThread")
    @Override
    public Result doWork() {
        Log.d("net", "start upload worker");

        // we need an upload token from the server to signalise which files belong together
        directoryUploadTokens.clear();
        toUploadDirectories.clear();
        for(File directory: DataContainer.getSubdirectories()) {
            toUploadDirectories.add(directory.getPath());
            new HTTPGetUploadToken().execute(directory.getPath());
        }
        startUploadIfReady();

        // Indicate whether the work finished successfully with the Result
        return Result.success();
    }


    private void finishedFileUpload(String filePath, String fileName, String result){
        // get uploaded file and remove it from queue
        toUploadedFiles.remove(filePath);
        // show status of uploaded file
        makeToast(fileName + ": " + result);

        // check if upload of all files is done
        if(toUploadedFiles.size() == 0){
            //infoText.setText(context.getString(R.string.it_upload_fin));
            //uploadProgressBar.setVisibility(View.INVISIBLE);
            //mainActivity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    private void startUploadIfReady(){
        for(String directory: toUploadDirectories){
            if(!directoryUploadTokens.containsKey(directory)) {
                return;
            }
        }
        UploadFiles();
    }

    private void UploadFiles(){
        // initialize progress bar for upload status uf current file
//        uploadProgressBar = (ProgressBar) ((Application) context).findViewById(R.id.uploaadProgressBar);
//        uploadProgressBar.setMax(100);

        // Upload files after short time to ensure that everything has written
        Handler postHandler = new Handler();
        postHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
//                uploadProgressBar.setVisibility(View.VISIBLE);
                // go through all stored data files and upload them
                for(HashMap.Entry<String, String> keyValue: directoryUploadTokens.entrySet()) {

                    for (DataContainer container : DataProcessor.allDataContainers) {
                        for (File dataFile : container.getAllVariantsInSubDirectory(new File(keyValue.getKey()))) {
                            new HTTPPostMultiPartFile().execute("", keyValue.getValue(), dataFile.getPath());
                            toUploadedFiles.add(dataFile.getPath());
                        }
                    }
                }
            }
        }, 500);
    }

    private void makeToast(final String text){
        Message message = uiHandler.obtainMessage(DO_TOAST, text);
        message.sendToTarget();
    }

    private void receivedUploadToken(){
        startUploadIfReady();
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
//                mainActivity.runOnUiThread(new Runnable() {
//                    @Override
//                    public void run() {
//                        infoText.setText(mainActivity.getString(R.string.it_upload) + fileName);
//                    }
//                });
                uploadMultipartFile(file, uploadToken);
//                return mainActivity.getString(R.string.str_success_upload);
                return "success";

            } catch (Exception e) {
                e.printStackTrace();
//                return mainActivity.getString(R.string.str_err);
                return "error";
            }
        }

        private void uploadMultipartFile(File file, String uploadToken) throws Exception {
//            uploadProgressBar.setProgress(0);
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
//                    uploadProgressBar.setProgress((int)percentage);
                    publishProgress(String.valueOf(Math.round(percentage)));
                }
            });

            String serverUrl = configs.getString(context.getString(R.string.conf_serverName), "")
                    + serverUrlSuffix
                    + uploadToken;
            Request request = new Request.Builder()
                    .url(serverUrl)
                    .addHeader("Authorization", "Bearer " + configs.getString(context.getString(R.string.conf_serverToken), ""))
                    .post(progressRequestBody)
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()){
                    throw new IOException(context.getString(R.string.str_unexpected_code) + response);
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
            // uploadProgressBar.setProgress(Integer.parseInt(values[0]));
        }

        @Override
        protected void onPostExecute(String result) {
            finishedFileUpload(filePath, fileName, result);
        }
    }

    protected final class HTTPGetUploadToken extends AsyncTask<String, String, String> {

        private final OkHttpClient client = new OkHttpClient();

        @Override
        protected String doInBackground(String... strings) {
            String directory = strings[0];
            String serverName = configs.getString(context.getString(R.string.conf_serverName), "");
            String serverToken = configs.getString(context.getString(R.string.conf_serverToken), "");
            Request request = new Request.Builder()
                    .url(serverName + "/recording/new/")
                    .addHeader("Authorization", "Bearer " + serverToken)
                    .build();
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()){
                    throw new IOException(context.getString(R.string.str_unexpected_code) + response);
                } else {
                    String jsonData = response.body().string();
                    JSONObject Jobject = new JSONObject(jsonData);
                    String status = Jobject.getString("status");
                    if (status.equals("success")){
                        directoryUploadTokens.put(directory, Jobject.getString("uuid"));
                        makeToast(context.getString(R.string.toast_got_token));
                    } else{
                        makeToast(context.getString(R.string.toast_error_during_request));
                    }
                }

                //System.out.println(response.body().string());
            } catch (ConnectException | java.net.SocketTimeoutException e) {
                makeToast(context.getString(R.string.toast_cant_conn_to_server));
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

}