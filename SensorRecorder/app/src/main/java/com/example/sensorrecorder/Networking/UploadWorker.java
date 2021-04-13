package com.example.sensorrecorder.Networking;

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
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.sensorrecorder.DataProcessor;
import com.example.sensorrecorder.ProgressRequestBody;
import com.example.sensorrecorder.R;
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

public class UploadWorker extends NetworkWorker {

    private final MediaType MEDIA_TYPE_CSV = MediaType.parse("text/csv");
    private final MediaType MEDIA_TYPE_ZIP = MediaType.parse("application/zip, application/octet-stream");
    private final MediaType MEDIA_TYPE_MKV = MediaType.parse("video/x-matroska, audio/x-matroska");
    private final MediaType MEDIA_TYPE_3GP = MediaType.parse("video/3gpp, audio/3gpp, video/3gpp2, audio/3gpp2");
    private final String serverUrlSuffix = "/recording/new/?uuid=";

    private ArrayList<String> toUploadDirectories = new ArrayList<>();
    private HashMap<String, String> directoryUploadTokens = new HashMap<String, String>();


    public UploadWorker(
            @NonNull final Context context,
            @NonNull WorkerParameters params) {
        super(context, params);
        setProgressAsync(new Data.Builder().putInt(PROGRESS, 0).build());
        sendStatus(STATUS_PENDING);
    }

    //@SuppressLint("WrongThread")
    @Override
    public Result doWork() {
        Log.d("net", "start upload worker");

        // check if server token exists.
        if(configs.getString(context.getString(R.string.conf_serverToken), "").equals("")) {
            sendStatus(STATUS_ERROR);
            return Result.failure();
        }

        // we need an upload token from the server to signalise which files belong together
        directoryUploadTokens.clear();
        toUploadDirectories.clear();
        for(File directory: DataContainer.getSubdirectories()) {
            toUploadDirectories.add(directory.getPath());
            if(getUploadToken(directory.getPath()) == STATUS_ERROR){
                sendStatus(STATUS_ERROR);
                return Result.retry();
            }
        }

        for(HashMap.Entry<String, String> keyValue: directoryUploadTokens.entrySet()) {
            for (File dataFile : DataProcessor.getAllFilesInSubdirectory(keyValue.getKey())) {
                try {
                    if(uploadMultipartFile(dataFile, keyValue.getValue()) == STATUS_ERROR) {
                        sendStatus(STATUS_ERROR);
                        return Result.retry();
                    }
                    makeToast(context.getString(R.string.str_success_upload) + ": " + dataFile.getName());
                } catch (Exception e) {
                    e.printStackTrace();
                    sendStatus(STATUS_ERROR);
                    return Result.retry();
                }
            }
        }
        sendStatus(STATUS_SUCCESS);
        // Indicate whether the work finished successfully with the Result
        return Result.success(new Data.Builder().putInt(STATUS, STATUS_FINISHED).build());
    }



    private int uploadMultipartFile(File file, String uploadToken) throws IOException {
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
                setProgressAsync(new Data.Builder().putInt(PROGRESS, (int)percentage).build());
//                    uploadProgressBar.setProgress((int)percentage);
                //publishProgress(String.valueOf(Math.round(percentage)));
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
                return STATUS_SUCCESS;
            }
        }
    }


    private int getUploadToken(String directory) {
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
                    return STATUS_SUCCESS;
                } else{
                    makeToast(context.getString(R.string.toast_error_during_request));
                    return STATUS_ERROR;
                }
            }

            //System.out.println(response.body().string());
        } catch (ConnectException | java.net.SocketTimeoutException e) {
            makeToast(context.getString(R.string.toast_cant_conn_to_server));
            return STATUS_ERROR;
        } catch (IOException | JSONException e) {
            e.printStackTrace();
        }
        return STATUS_ERROR;
    }
}