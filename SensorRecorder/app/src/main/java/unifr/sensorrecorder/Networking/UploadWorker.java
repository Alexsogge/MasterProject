package unifr.sensorrecorder.Networking;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.WorkerParameters;

import unifr.sensorrecorder.DataContainer.DataProcessor;
import unifr.sensorrecorder.NotificationSpawner;
import unifr.sensorrecorder.R;
import unifr.sensorrecorder.DataContainer.DataContainer;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
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

    private int previousProgress = 0;


    public UploadWorker(
            @NonNull final Context context,
            @NonNull WorkerParameters params) {
        super(context, params);

    }

    //@SuppressLint("WrongThread")
    @Override
    public Result doWork() {
        //sendUploadProgress(0);
        sendStatus(STATUS_PENDING);

        // check if server token exists.
        if(configs.getString(context.getString(R.string.conf_serverToken), "").equals("")) {
            sendStatus(STATUS_ERROR);
            return Result.failure();
        }

        NotificationSpawner.showUploadNotification(this.context, this.context.getString(R.string.not_upload_start));

        // we need an upload token from the server to signalise which files belong together
        directoryUploadTokens.clear();
        toUploadDirectories.clear();
        for(File directory: DataContainer.getAllSubdirectories()) {
            toUploadDirectories.add(directory.getPath());
            if(loadUploadToken(directory.getPath()) == STATUS_ERROR){
                sendStatus(STATUS_ERROR);
                NotificationSpawner.showUploadNotification(this.context, this.context.getString(R.string.not_upload_error));
                return Result.retry();
            }
        }
        for(HashMap.Entry<String, String> keyValue: directoryUploadTokens.entrySet()) {
            sendUploadProgress(0);
            int uploadedFiles = 0;
            ArrayList<File> dataFiles =  DataProcessor.getAllFilesInSubdirectory(keyValue.getKey());
            for (File dataFile : dataFiles) {
                try {
                    if(uploadMultipartFile(dataFile, keyValue.getValue()) == STATUS_ERROR) {
                        sendStatus(STATUS_ERROR);
                        NotificationSpawner.showUploadNotification(this.context, this.context.getString(R.string.not_upload_error));
                        return Result.retry();
                    }
                    uploadedFiles++;
                    sendUploadProgress((int)(((float)uploadedFiles/dataFiles.size())*100));
                    // makeToast(context.getString(R.string.str_success_upload) + ": " + dataFile.getName());
                } catch (Exception e) {
                    e.printStackTrace();
                    sendStatus(STATUS_ERROR);
                    NotificationSpawner.showUploadNotification(this.context, this.context.getString(R.string.not_upload_error));
                    return Result.retry();
                }
            }
        }
        NotificationSpawner.showUploadNotification(this.context, this.context.getString(R.string.not_upload_end));
        // sendStatus(STATUS_FINISHED);
        // Indicate whether the work finished successfully with the Result
        return Result.success(new Data.Builder().putInt(STATUS, STATUS_FINISHED).build());
    }

    private void sendUploadProgress(int progress){
        // Log.d("worker", "send progress: " + progress);
        Data progressData = new Data.Builder()
                .putInt(STATUS, STATUS_PROGRESS)
                .putInt(PROGRESS, (int)progress)
                .build();
        setProgressAsync(progressData);
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

        /*
        // we use a ProgressRequestBody to get events for current upload status and visualize this with a progressbar
        ProgressRequestBody progressRequestBody = new ProgressRequestBody(requestBody, new ProgressRequestBody.Listener() {
            @Override
            public void onRequestProgress(long bytesWritten, long contentLength) {
                float percentage = 100f * bytesWritten / contentLength;
                if(percentage > previousProgress + 5){
                    previousProgress = (int)percentage;
                    sendUploadProgress((int)percentage);
                }

//                    uploadProgressBar.setProgress((int)percentage);
                //publishProgress(String.valueOf(Math.round(percentage)));
            }
        });
        */

        String serverUrl = configs.getString(context.getString(R.string.conf_serverName), "")
                + serverUrlSuffix
                + uploadToken;
        Request request = new Request.Builder()
                .url(serverUrl)
                .addHeader("Authorization", "Bearer " + configs.getString(context.getString(R.string.conf_serverToken), ""))
                //.post(progressRequestBody)
                .post(requestBody)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()){
                Log.e("upload", "error during upload of " + file.getName());
                throw new IOException(context.getString(R.string.str_unexpected_code) + response);
            } else {
                File directory = file.getParentFile();

                // test if this is last file and delete directory if this is the case
                boolean deleteDirectory = true;
                for(String remainedFile: directory.list()){
                    if(!(remainedFile.charAt(0) == '.' || remainedFile.equals(file.getName())))
                        deleteDirectory = false;
                }
                if(deleteDirectory) {
                    for (File remainedFile: directory.listFiles()){
                        remainedFile.delete();
                    }
                    directory.delete();
                }
                else
                    // remove file after upload
                    file.delete();
                return STATUS_SUCCESS;
            }
        }
    }


    private int loadUploadToken(String directory){
        String token;
        try {
            token = getStoredUploadToken(directory);
            if (token == null){
                token = requestUploadToken(directory);
                if (token == null)
                    return STATUS_ERROR;
                storeUploadToken(directory, token);
            }
        } catch (IOException | JSONException e) {
            e.printStackTrace();
            return STATUS_ERROR;
        }
        directoryUploadTokens.put(directory, token);
        return STATUS_SUCCESS;
    }

    private String getStoredUploadToken(String directory) throws IOException {
        String token = null;
        File tokenFile = new File(directory, ".token.txt");
        if(tokenFile.exists()) {
            BufferedReader br = new BufferedReader(new FileReader(tokenFile));
            token = br.readLine();
            br.close();
            // makeToast(context.getString(R.string.toast_got_token));
        }
        return token;
    }

    private void storeUploadToken(String directory, String token) throws IOException {
        File tokenFile = new File(directory, ".token.txt");
        FileOutputStream outputStream = new FileOutputStream(tokenFile);
        outputStream.write(token.getBytes());
        outputStream.flush();
        outputStream.close();
    }

    private String requestUploadToken(String directory) throws IOException, JSONException {
        String serverName = configs.getString(context.getString(R.string.conf_serverName), "");
        String serverToken = configs.getString(context.getString(R.string.conf_serverToken), "");
        Request request = new Request.Builder()
                .url(serverName + "/recording/new/")
                .addHeader("Authorization", "Bearer " + serverToken)
                .build();
        Response response = client.newCall(request).execute();
        if (!response.isSuccessful()){
            return null;
        } else {
            String jsonData = response.body().string();
            JSONObject Jobject = new JSONObject(jsonData);
            String status = Jobject.getString("status");
            if (status.equals("success")){
                //directoryUploadTokens.put(directory, Jobject.getString("uuid"));
                // makeToast(context.getString(R.string.toast_got_token));
                return Jobject.getString("uuid");
            } else{
                makeToast(context.getString(R.string.toast_error_during_request));
                return null;
            }
        }
    }
}