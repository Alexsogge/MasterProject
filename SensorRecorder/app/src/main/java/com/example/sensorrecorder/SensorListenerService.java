package com.example.sensorrecorder;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Build;
import android.os.Debug;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
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

import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;


public class SensorListenerService extends Service implements SensorEventListener{


    private final IBinder binder = new LocalBinder();

    SensorManager sensorManager;
    Sensor acceleration_sensor;
    Sensor gyro_sensor;
    //int samplingRate = SensorManager.SENSOR_DELAY_NORMAL;
    int samplingRate = 20000;
    //int reportRate = 100000000;
    // int reportRate = 60000000;  //frequenz in marco seconds * FIFO size / values per step (3)
    int reportRate = 1000000;
    long lastTimeStamp;
    long[] recording_timestamps_acc = new long[1000];
    float[][] recording_values_acc = new float[1000][3];
    long[] recording_timestamps_gyro = new long[1000];
    float[][] recording_values_gyro = new float[1000][3];
    int pointer_acc = 0;
    int pointer_gyro = 0;
    Intent intent;
    boolean triggerPush;
    long awakeCounter = 0;
    long batchCounter = 0;
    boolean isSleeping = false;
    long lastSensorTimeStamp = 0;
    int errorCounter = 0;
    ArrayList<Long> missedDelays = new ArrayList<Long>();
    boolean initialized = false;
    boolean isRunning = true;

    File recording_file_acc;
    FileOutputStream file_output_acc;
    File recording_file_gyro;
    FileOutputStream file_output_gyro;
    public static final String CHANNEL_ID = "ForegroundServiceChannel";
    public ArrayList<ArrayList<long[]>> handWashEvents;
    private NotificationCompat.Builder notificationBuilder;
    private PowerManager.WakeLock wakeLock;
    public final File recording_file_path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM + "/android_sensor_recorder/");
    public TextView infoText;
    private Handler mainLoopHandler;

    private static final MediaType MEDIA_TYPE_CSV = MediaType.parse("text/csv");
    private final OkHttpClient client = new OkHttpClient();
    private final String serverUrl = "http://192.168.0.101:8000";


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d("sensorinfo", "Start service" + flags + " | " + startId + initialized);
        Log.d("sensorinfo", "Params: " + intent.getStringExtra("trigger"));



        if (!initialized) {
            initialized = true;
            this.intent = intent;

            createForeGroundNotification();
            // startForeground(1, notificationBuilder.build());

            sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
            acceleration_sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            gyro_sensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);

            handWashEvents = new ArrayList<>();
            mainLoopHandler = new Handler(Looper.getMainLooper());
            PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);

            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                    "SensorRecorder::WakelockTag");

            try {

                if(!recording_file_path.exists())
                {
                    // Make it, if it doesn't exit
                    recording_file_path.mkdirs();
                }
                recording_file_acc = new File(recording_file_path, "sensor_recording_android_acc.csv");
                recording_file_acc.createNewFile();
                recording_file_gyro = new File(recording_file_path, "sensor_recording_android_gyro.csv");
                recording_file_gyro.createNewFile();

                //recording_file_acc = new File(String.valueOf(this.openFileOutput("sensor_recording_android.csv", Context.MODE_PRIVATE)));
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }

            Log.i("sensorinfo", String.valueOf(acceleration_sensor.getFifoMaxEventCount()));
            Log.i("sensorinfo", String.valueOf(acceleration_sensor.getFifoReservedEventCount()));
            Log.i("sensorinfo", String.valueOf(acceleration_sensor.getMaxDelay()));
            Log.i("sensorinfo", String.valueOf(acceleration_sensor.isWakeUpSensor()));
            //Log.e("sensorinfo", "Max delay: " + acceleration_sensor.getMaxDelay() + " - Fifo count" + acceleration_sensor.getFifoReservedEventCount());

            startRecording();

        } else {
            if (intent.getStringExtra("trigger") != null){
                if (intent.getStringExtra("trigger").equals("testCall"))
                    TestCall();
                if (intent.getStringExtra("trigger").equals("handWash"))
                    AddHandWashEvent();
                if (intent.getStringExtra("trigger").equals("open")){
                    Intent mainIntent = new Intent(this, MainActivity.class);
                    startActivity(mainIntent);
                }
            }
        }
        return START_STICKY;
    }

    private void TestCall(){
        Log.d("fgservice", "Test call");
    }

    private void createForeGroundNotification(){
        createNotificationChannel();
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this,
                0, notificationIntent, 0);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Foreground Service")
                .setContentText("SensorRecorder")
                .setContentIntent(pendingIntent)
                .build();

        Intent handwashIntent = new Intent(this.intent);
        handwashIntent.putExtra("trigger", "handWash");
        PendingIntent pintHandWash = PendingIntent.getService(this, 579, handwashIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        // Intent openIntent = new Intent(intent);
        Intent openIntent = new Intent(getApplicationContext(), MainActivity.class);

        openIntent.putExtra("trigger", "open");
        PendingIntent pintOpen = PendingIntent.getActivity(getApplicationContext(), 579, openIntent, PendingIntent.FLAG_UPDATE_CURRENT);


        notificationBuilder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Running")
                .setContentText("Sensor recorder is active")
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText("Sensor recorder is active"))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setSmallIcon(R.drawable.preference_wrapped_icon)
                .addAction(R.drawable.action_item_background, "HandWash", pintHandWash)
                // .addAction(R.drawable.action_item_background, "Open", pintOpen);
                .setContentIntent(pintOpen);
    }

    public void registerToManager(){
        recording_timestamps_acc = new long[1000];
        recording_values_acc = new float[1000][3];
        recording_timestamps_gyro = new long[1000];
        recording_values_gyro = new float[1000][3];
        pointer_acc = 0;
        pointer_gyro = 0;

        handWashEvents.add(new ArrayList<long[]>());
        OpenFileStream();

        boolean batchMode = sensorManager.registerListener(this, acceleration_sensor, samplingRate, reportRate);
        batchMode = batchMode && sensorManager.registerListener(this, gyro_sensor, samplingRate, reportRate);
        if (!batchMode){
            Log.e("sensorinfo", "Could not register sensors to batch");
        } else {
            Log.e("sensorinfo", "Registered sensors to batch");
        }
        isRunning = true;
        // wakeLock.acquire(1000);
        /*
        Intent flushSensorIntent = new Intent(this, SensorEventListener.class);
        AlarmManager alarmManager = (AlarmManager) this.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pendingIntent = PendingIntent.getService(this, 0, flushSensorIntent, 0);
        alarmManager.setInexactRepeating(AlarmManager.RTC_WAKEUP, SystemClock.elapsedRealtime() + 10 * 1000, 20 * 1000, pendingIntent);
        Log.e("Sensorinfo", "Started Alarm");
        */

    }

    private void unregisterfromManager(){
        Log.i("sensorinfo", "unregister listener");
        try {
            SendSensorDataAcc();
            SendSensorDataGyro();
            file_output_acc.close();
            file_output_gyro.close();
        } catch (IOException e){
            e.printStackTrace();
        }
        sensorManager.unregisterListener(this);
        isRunning = false;
        // wakeLock.release();
    }

    public void startRecording(){
        wakeLock.acquire(1000*60*1000L /*1000 minutes*/);

        startForeground(1, notificationBuilder.build());
        registerToManager();
    }

    public void stopRecording(){
        unregisterfromManager();
        stopForeground(true);
        wakeLock.release();
    }

    public void flushSensor(){
        Log.d("Sensorinfo", "Flushed sensor");
        sensorManager.flush(this);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    /*
    @Override
    public void onAlarm() {
        Log.d("Sensorinfo", "Sensor Alarm");
        flushSensor();
    }
    */

    public class LocalBinder extends Binder {
        SensorListenerService getService() {
            // Return this instance of LocalService so clients can call public methods
            return SensorListenerService.this;
        }
    }

    @Override
    public void onDestroy(){
        this.unregisterfromManager();
    }


    @Override
    public void onSensorChanged(SensorEvent event) {

        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            // Log.d("sensor", "[" + event.timestamp + "]" + event.values[0] + " " + event.values[1] + " " + event.values[2]);
            recording_timestamps_acc[pointer_acc] = event.timestamp;
            recording_values_acc[pointer_acc][0] = event.values[0];
            recording_values_acc[pointer_acc][1] = event.values[1];
            recording_values_acc[pointer_acc][2] = event.values[2];
            pointer_acc++;
            // Log.d("sensor", ""+pointer_acc);
            if(pointer_acc == 1000) {
                try {
                    SendSensorDataAcc();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                recording_timestamps_acc[0] = recording_timestamps_acc[pointer_acc-1];
                pointer_acc = 1;
            }
        }
        if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            // Log.d("sensor", "[" + event.timestamp + "]" + event.values[0] + " " + event.values[1] + " " + event.values[2]);
            recording_timestamps_gyro[pointer_gyro] = event.timestamp;
            recording_values_gyro[pointer_gyro][0] = event.values[0];
            recording_values_gyro[pointer_gyro][1] = event.values[1];
            recording_values_gyro[pointer_gyro][2] = event.values[2];
            pointer_gyro++;
            // Log.d("sensor", ""+pointer_acc);
            if(pointer_gyro == 1000) {
                try {
                    SendSensorDataGyro();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                recording_timestamps_gyro[0] = recording_timestamps_gyro[pointer_gyro-1];
                pointer_gyro = 1;
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    private void OpenFileStream(){
        try {
            file_output_acc = new FileOutputStream(recording_file_acc);
            file_output_gyro = new FileOutputStream(recording_file_gyro);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    private void SendSensorDataAcc() throws IOException {
        Log.e("write", "Write File");
        StringBuilder data = new StringBuilder();
        for(int i = 0; i < pointer_acc; i++){
            // Log.i("sensor", "RecordingService: " + timeStamp);
            data.append(recording_timestamps_acc[i]).append("\t");
            data.append(recording_values_acc[i][0]).append("\t");
            data.append(recording_values_acc[i][1]).append("\t");
            data.append(recording_values_acc[i][2]).append("\n");
        }
        if(ContextCompat.checkSelfPermission(this, WRITE_EXTERNAL_STORAGE) == PERMISSION_GRANTED) {
            file_output_acc.write(data.toString().getBytes());
            file_output_acc.flush();
            Log.e("write", "Flushed File acc");
        } else {
            Log.e("write", "Can't write file");
        }
    }

    private void SendSensorDataGyro() throws IOException {
        Log.e("write", "Write File");
        StringBuilder data = new StringBuilder();
        for(int i = 0; i < pointer_gyro; i++){
            // Log.i("sensor", "RecordingService: " + timeStamp);
            data.append(recording_timestamps_gyro[i]).append("\t");
            data.append(recording_values_gyro[i][0]).append("\t");
            data.append(recording_values_gyro[i][1]).append("\t");
            data.append(recording_values_gyro[i][2]).append("\n");
        }
        if(ContextCompat.checkSelfPermission(this, WRITE_EXTERNAL_STORAGE) == PERMISSION_GRANTED) {
            file_output_gyro.write(data.toString().getBytes());
            file_output_gyro.flush();
            Log.e("write", "Flushed File gyro");
        } else {
            Log.e("write", "Can't write file");
        }
    }

    private void AddHandWashEvent(){
        Log.d("fgservice", "New handwash at " + (pointer_acc - 1) + "   " + (pointer_gyro - 1));
        flushSensor();
        if(pointer_acc == 0 || pointer_gyro == 0)
            return;
        long[] newEvent = {recording_timestamps_acc[pointer_acc - 1], recording_timestamps_gyro[pointer_gyro - 1]};
        handWashEvents.get(handWashEvents.size()-1).add(newEvent);
        Log.d("fgservice", "New handwash at " + newEvent[0] + "   " + newEvent[1]);

    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Foreground Service Channel",
                    NotificationManager.IMPORTANCE_DEFAULT
            );

            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(serviceChannel);
        }
    }


    public void UploadSensorData(){
        Log.d("sensorrecorder", "Upload sensorData");
        PowerManager mgr = (PowerManager)this.getSystemService(Context.POWER_SERVICE);
        final PowerManager.WakeLock wakeLock = mgr.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "sensor_recorder::upload_lock");
        wakeLock.acquire(10*60*1000L /*10 minutes*/);

        flushSensor();

        unregisterfromManager();

        setInfoText("Backup files");
        Log.d("sensorrecorder", "Backup files");
        backup_recording_files();
        setInfoText("Check connection");
        Log.d("sensorrecorder", "Check connection");
        ConnectivityManager connectivityManager =
                (ConnectivityManager) getSystemService(this.CONNECTIVITY_SERVICE);
        Log.d("sensorrecorder", "Get active connection");
        Network activeNetwork = connectivityManager.getActiveNetwork();
        Log.d("sensorrecorder", "active connection is: " + activeNetwork.toString());
        if (activeNetwork == null) {
            Toast.makeText(getBaseContext(), "No connection to internet", Toast.LENGTH_LONG).show();
            registerToManager();
            return;
        }

        // Upload files after short time to ensure that everything has written
        Handler postHandler = new Handler();
        postHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    // upload hand wash events
                    for(int j = handWashEvents.size() - 1; j >= 0; j--) {
                        JSONObject additional_data = new JSONObject();
                        JSONArray array = new JSONArray();
                        for (int i = 0; i < handWashEvents.get(j).size(); i++) {
                            array.put(handWashEvents.get(j).get(i)[0]);
                        }
                        additional_data.put("hand_wash_events_acc_" + j, array);
                        array = new JSONArray();
                        for (int i = 0; i < handWashEvents.get(j).size(); i++) {
                            array.put(handWashEvents.get(j).get(i)[1]);
                        }
                        Log.d("sensorrecorder", "upload: handwash events");
                        setInfoText("upload " + "hand_wash_events_gyro_" + j);
                        additional_data.put("hand_wash_events_gyro_" + j, array);
                        CharSequence response = new HTTPPostJSON().execute("http://192.168.0.101:8000", additional_data.toString()).get();
                        makeToast(response.toString());
                        if(response.subSequence(0, "success:".length()).equals("success:")){
                            handWashEvents.remove(j);
                        }
                    }

                    File tmp_file = null;
                    // Upload all acceleration files
                    String file_name = recording_file_acc.getName().replaceFirst("[.][^.]+$", "");
                    for (int i = 0; i < 99; i++) {
                        tmp_file = new File(recording_file_path, file_name + "_" + i + ".csv");
                        if (!tmp_file.exists())
                            break;
                        Log.d("sensorrecorder", "upload: " + tmp_file.getName() + " of size " + tmp_file.length());
                        setInfoText("upload " + tmp_file.getName());
                        //CharSequence response = new HTTPPostFile().execute("http://192.168.0.101:8000", tmp_file.getName()).get();
                        CharSequence response = new HTTPPostMultiPartFile().execute("http://192.168.0.101:8000", tmp_file.getName()).get();
                        Log.d("sensorrecorder", "upload finished");
                        makeToast(response.toString());
                        if(response.subSequence(0, "success:".length()).equals("success:")){
                            tmp_file.delete();
                        }
                    }

                    // Upload all gyroscope files
                    file_name = recording_file_gyro.getName().replaceFirst("[.][^.]+$", "");
                    for (int i = 0; i < 99; i++) {
                        tmp_file = new File(recording_file_path, file_name + "_" + i + ".csv");
                        if (!tmp_file.exists())
                            break;
                        Log.d("sensorrecorder", "upload: " + tmp_file.getName() + " of size " + tmp_file.length());
                        setInfoText("upload " + tmp_file.getName());
                        //CharSequence response = new HTTPPostFile().execute("http://192.168.0.101:8000", tmp_file.getName()).get();
                        CharSequence response = new HTTPPostMultiPartFile().execute("http://192.168.0.101:8000", tmp_file.getName()).get();
                        Log.d("sensorrecorder", "upload finished");
                        makeToast(response.toString());
                        if(response.subSequence(0, "success:".length()).equals("success:")){
                            tmp_file.delete();
                        }

                    }

                    setInfoText("upload finished");
                    registerToManager();
                    wakeLock.release();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }, 2000);
    }

    public void prepareUpload(){
        flushSensor();
        unregisterfromManager();
        setInfoText("Backup files");
        Log.d("sensorrecorder", "Backup files");
        backup_recording_files();
        registerToManager();
    }

    public File[] backup_recording_files(){
        File[] files = new File[2];
        try {
            files[0] = backup_file(recording_file_acc);
            files[1] = backup_file(recording_file_gyro);
        } catch (IOException e){
            Log.e("sensorrecorder", "Error while backup file");
            e.printStackTrace();
        }
        return files;
    }

    public File backup_file(File src) throws IOException {
        String backup_name = src.getName().replaceFirst("[.][^.]+$", "");
        File dst = null;
        for (int i = 0; i < 99; i++){
            dst = new File(recording_file_path, backup_name + "_" + i + ".csv");
            if (!dst.exists())
                break;
        }
        Log.d("sensorrecorder", "Backup " + dst.getName());
        dst.createNewFile();
        try (InputStream in = new FileInputStream(src)) {
            try (OutputStream out = new FileOutputStream(dst)) {
                // Transfer bytes from in to out
                byte[] buf = new byte[1024];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
            }
        }
        return dst;
    }

    private void setInfoText(final String text){
        mainLoopHandler.post(new Runnable() {
            @Override
            public void run() {
                infoText.setText(text);
            }
        });
    }

    private void makeToast(final String text){
        mainLoopHandler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getBaseContext(), text, Toast.LENGTH_LONG).show();
            }
        });
    }
}



class HTTPPostFile extends AsyncTask<String, String, String> {

    public HTTPPostFile(){
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
        DataOutputStream outputStream = null;
        InputStream inputStream = null;
        String boundary =  "*****"+Long.toString(System.currentTimeMillis())+"*****";
        int bytesRead, bytesAvailable, bufferSize;
        byte[] buffer;
        File file = null;
        //String[] q = recording_file_acc.pat.split("/");
        //int idx = q.length - 1;
        try {
            final File path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM + "/android_sensor_recorder/");
            file = new File(path, data);
            FileInputStream fileInputStream = new FileInputStream(file);
            Log.d("sensorrecorder", "Load File "+ data + " of size:" + file.length());
            Log.d("sensorrecorder", "File exists " + file.exists() + " can read " + file.canRead());
            URL url = null;
            url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            connection.setDoInput(true);
            connection.setDoOutput(true);
            connection.setUseCaches(false);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Connection", "Keep-Alive");
            connection.setRequestProperty("User-Agent", "Android Multipart HTTP Client 1.0");
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary="+boundary);
            outputStream = new DataOutputStream(connection.getOutputStream());
            outputStream.writeBytes("--" + boundary + "\r\n");
            outputStream.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"" + file.getName() + "\"" + "\r\n");
            outputStream.writeBytes("Content-Type: text/csv" + "\r\n");
            outputStream.writeBytes("Content-Transfer-Encoding: binary" + "\r\n");
            outputStream.writeBytes("\r\n");
            bytesAvailable = fileInputStream.available();
            bufferSize = Math.min(bytesAvailable, 1048576);
            buffer = new byte[bufferSize];
            bytesRead = fileInputStream.read(buffer, 0, bufferSize);
            while(bytesRead > 0) {
                outputStream.write(buffer, 0, bufferSize);
                bytesAvailable = fileInputStream.available();
                bufferSize = Math.min(bytesAvailable, 1048576);
                bytesRead = fileInputStream.read(buffer, 0, bufferSize);
            }
            outputStream.writeBytes("\r\n");
            outputStream.writeBytes("--" + boundary + "--" + "\r\n");
            inputStream = connection.getInputStream();
            int status = connection.getResponseCode();
            if (status == HttpURLConnection.HTTP_OK) {
                Log.d("sensorrecorder", "HTTP OK");
                BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                String inputLine;
                StringBuffer response = new StringBuffer();
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                inputStream.close();
                connection.disconnect();
                fileInputStream.close();
                outputStream.flush();
                outputStream.close();
                return "success: uploaded " + file.getName();
            } else {
                return "error: servererror";
                // throw new Exception("Non ok response returned");
            }
        } catch (MalformedURLException | ProtocolException e) {
            e.printStackTrace();
            return "error: server not reachable";
        }catch (ConnectException e){
            e.printStackTrace();
            return "error: Failed to connect to " + urlString;
        } catch (IOException e) {
            e.printStackTrace();
            return "error: can't read sensor file";
        } catch (Exception e) {
            e.printStackTrace();
            return "error: error";
        }
    }
}

class HTTPPostJSON extends AsyncTask<String, String, String> {

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

class HTTPPostMultiPartFile extends AsyncTask<String, String, String> {

    private static final MediaType MEDIA_TYPE_CSV = MediaType.parse("text/csv");
    private final OkHttpClient client = new OkHttpClient();
    private final String serverUrl = "http://192.168.0.101:8000";

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
        String data = params[1]; //data to post
        File file = null;
        //String[] q = recording_file_acc.pat.split("/");
        //int idx = q.length - 1;
        try {
            final File path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM + "/android_sensor_recorder/");
            file = new File(path, data);
            Log.d("sensorrecorder", "Load File "+ data + " of size:" + file.length());
            uploadMultipartFile(file);
            return "success: uploaded";

        } catch (Exception e) {
            e.printStackTrace();
            return "error: error";
        }
    }

    private void uploadMultipartFile(File file) throws Exception {
        // Use the imgur image upload API as documented at https://api.imgur.com/endpoints/image

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
                Log.d("sensorrecorder", "Progress: " + percentage);

            }
        });


        Request request = new Request.Builder()
                .url(serverUrl)
                .post(progressRequestBody)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);

            System.out.println(response.body().string());
        }
    }
}