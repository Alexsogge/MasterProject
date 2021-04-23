package unifr.sensorrecorder.Networking;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import unifr.sensorrecorder.HandWashDetection;
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

import kotlin.Pair;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class NetworkManager {
    public SensorRecordingManager sensorService;
    private Activity mainActivity;
    private final Executor executor = Executors.newSingleThreadExecutor();

    private TextView infoText;
    private SharedPreferences configs;

    private CountDownLatch sensorStopLatch;


    public NetworkManager(final Activity mainActivity, SensorRecordingManager sensorService, SharedPreferences configs){
        this.mainActivity = mainActivity;
        this.sensorService = sensorService;
        infoText = (TextView)mainActivity.findViewById(R.id.infoText);
        this.configs = configs;
    }

    public void DoFileUpload(){
        // stop recording and create backup
        sensorStopLatch = new CountDownLatch(1);
        sensorService.waitForStopRecording(sensorStopLatch);


        // update info text
        infoText.setText(mainActivity.getString(R.string.btn_stopping));
        infoText.invalidate();

        // check if server was specified
        if(configs.getString(mainActivity.getString(R.string.conf_serverName), "").equals("")){
            Toast.makeText(mainActivity.getBaseContext(), mainActivity.getString(R.string.toast_no_server_name), Toast.LENGTH_LONG).show();
            return;
        }

        Log.e("networking", "start upload");
        executor.execute(new UploadTaskStarter());
    }


    private void makeToast(final String text){
        mainActivity.runOnUiThread(new Runnable() {
            public void run() {
                Toast.makeText(mainActivity, text, Toast.LENGTH_LONG).show();
            }
        });
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
            infoText.setText(mainActivity.getString(R.string.it_start_upload));
            infoText.invalidate();


            OneTimeWorkRequest uploadWorkRequest = buildUploadWorkRequest();
            WorkManager.getInstance(mainActivity).cancelAllWork();
            WorkManager.getInstance(mainActivity).pruneWork();
            if(configs.getString(mainActivity.getString(R.string.conf_serverToken), "").equals("")) {
                OneTimeWorkRequest serverTokenWorkRequest = buildGetServerTokenWorkRequest();
                Log.d("net", "enque upload worker");
                WorkManager.getInstance(mainActivity)
                        .beginWith(serverTokenWorkRequest)
                        .then(uploadWorkRequest)
                        .enqueue();
            } else {
                WorkManager.getInstance(mainActivity).enqueue(uploadWorkRequest);
            }
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


    public void downloadTFModel(){
        new NetworkManager.HTTPGetTFModel().execute();
    }

    public void requestServerToken(){
        OneTimeWorkRequest serverTokenWorkRequest = buildGetServerTokenWorkRequest();
        WorkManager.getInstance(mainActivity).enqueue(serverTokenWorkRequest);
    }

    protected final class HTTPGetTFModel extends AsyncTask<String, String, String> {

        private final OkHttpClient client = new OkHttpClient();

        @Override
        protected String doInBackground(String... strings) {
            String serverName = configs.getString(mainActivity.getString(R.string.conf_serverName), "");
            try {
                String tfFileName = downloadTFFile(serverName + "/tfmodel/get/latest/", HandWashDetection.modelName);
                downloadTFFile(serverName + "/tfmodel/get/settings/", HandWashDetection.modelSettingsName);
                makeToast(mainActivity.getString(R.string.toast_downloaded_tf) + ":\n" + tfFileName);
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }

        private String downloadTFFile(String url, String filename) throws IOException {
            String fileName = "default name";
            Pattern p = Pattern.compile(".+filename=(.+?)\\..*");
            Request request = new Request.Builder().url(url).build();
            Response response = null;
            response = client.newCall(request).execute();
            if (!response.isSuccessful()) {
                makeToast(mainActivity.getString(R.string.toast_failed_to_dl_file) + response);
            } else {
                File path = HandWashDetection.modelFilePath;
                if (!path.exists())
                    path.mkdirs();
                File tfFile = new File(path, filename);
                FileOutputStream fos = new FileOutputStream(tfFile);
                fos.write(response.body().bytes());
                fos.close();
                String contentDisposition = response.header("content-disposition");
                if(contentDisposition != null) {
                    Matcher m = p.matcher(contentDisposition);
                    if(m.find()) {
                        fileName = m.group(1);
                    }
                }
            }
            return fileName;
        }
    }
}
