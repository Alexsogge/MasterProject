package com.example.sensorrecorder;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
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
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.util.ArrayList;

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
    private ArrayList<long[]> handWashEvents;
    private NotificationCompat.Builder notificationBuilder;



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


            try {
                final File path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM + "/android_sensor_recorder/");
                if(!path.exists())
                {
                    // Make it, if it doesn't exit
                    path.mkdirs();
                }
                recording_file_acc = new File(path, "sensor_recording_android_acc.csv");
                recording_file_acc.createNewFile();
                recording_file_gyro = new File(path, "sensor_recording_android_gyro.csv");
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
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setSmallIcon(R.drawable.preference_wrapped_icon)
                .addAction(R.drawable.action_item_background, "HandWash", pintHandWash)
                // .addAction(R.drawable.action_item_background, "Open", pintOpen);
                .setContentIntent(pintOpen);
    }

    private void registerToManager(){
        recording_timestamps_acc = new long[1000];
        recording_values_acc = new float[1000][3];
        recording_timestamps_gyro = new long[1000];
        recording_values_gyro = new float[1000][3];
        pointer_acc = 0;
        pointer_gyro = 0;

        handWashEvents = new ArrayList<>();
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
        startForeground(1, notificationBuilder.build());
        registerToManager();
    }

    public void stopRecording(){
        unregisterfromManager();
        stopForeground(true);
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
        handWashEvents.add(newEvent);
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
        flushSensor();

        unregisterfromManager();
        // Upload files after short time to ensure that everything has written
        Handler postHandler = new Handler();
        postHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    Log.d("sensorrecorder", "upload: " + recording_file_acc.getName() + " of size " + recording_file_acc.length());
                    
                    CharSequence response = new HTTPPostFile().execute("http://192.168.0.101:8000", recording_file_acc.getName()).get();
                    Toast.makeText(getBaseContext(), response, Toast.LENGTH_LONG).show();
                    response = new HTTPPostFile().execute("http://192.168.0.101:8000", recording_file_gyro.getName()).get();
                    Toast.makeText(getBaseContext(), response, Toast.LENGTH_LONG).show();

                    JSONObject additional_data = new JSONObject();
                    JSONArray array = new JSONArray();
                    for(int i = 0; i < handWashEvents.size(); i++) {
                        array.put(handWashEvents.get(i)[0]);
                    }
                    additional_data.put("hand_wash_events_acc", array);
                    array = new JSONArray();
                    for(int i = 0; i < handWashEvents.size(); i++) {
                        array.put(handWashEvents.get(i)[1]);
                    }
                    additional_data.put("hand_wash_events_gyro", array);
                    new HTTPPostJSON().execute("http://192.168.0.101:8000", additional_data.toString()).get();

                    registerToManager();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }, 2000);
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
                return "uploaded " + file.getName();
            } else {
                return "servererror";
                // throw new Exception("Non ok response returned");
            }
        } catch (MalformedURLException | ProtocolException e) {
            e.printStackTrace();
            return "server not reachable";
        }catch (ConnectException e){
            e.printStackTrace();
            return "Failed to connect to " + urlString;
        } catch (IOException e) {
            e.printStackTrace();
            return "can't read sensor file";
        } catch (Exception e) {
            e.printStackTrace();
            return "error";
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
            return "uploaded ";
        } catch (ProtocolException ex) {
            ex.printStackTrace();
        } catch (IOException ex) {
            ex.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
            return "error";
        }
        return "Uploaded info data";
    }
}