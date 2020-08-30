package com.example.sensorcollecttest;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.Nullable;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;


public class SensorListenerService extends Service implements SensorEventListener{


    private final IBinder binder = new LocalBinder();

    SensorManager sensorManager;
    Sensor acceleration_sensor;
    int samplingRate = SensorManager.SENSOR_DELAY_NORMAL;
    //int reportRate = 100000000;
    // int reportRate = 60000000;  //frequenz in marco seconds * FIFO size / values per step (3)
    int reportRate = 1000000;
    long lastTimeStamp;
    long[] recordings = new long[1000];
    int pointer = 0;
    Intent intent;
    boolean triggerPush;
    long awakeCounter = 0;
    long batchCounter = 0;
    boolean isSleeping = false;
    long lastSensorTimeStamp = 0;
    int errorCounter = 0;
    ArrayList<Long> missedDelays = new ArrayList<Long>();
    boolean initialized = false;
    int triggeredAlarms = 0;
    ArrayList<Long> alarmTriggers = new ArrayList<Long>();
    AlarmManager alarmManager;
    PendingIntent pendingIntent;
    PowerManager.WakeLock wakeLock;



    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!initialized) {
            initialized = true;
            this.intent = intent;
            sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
            acceleration_sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);


            Log.i("sensorinfo", String.valueOf(acceleration_sensor.getFifoMaxEventCount()));
            Log.i("sensorinfo", String.valueOf(acceleration_sensor.getFifoReservedEventCount()));
            Log.i("sensorinfo", String.valueOf(acceleration_sensor.getMaxDelay()));
            Log.i("sensorinfo", String.valueOf(acceleration_sensor.isWakeUpSensor()));
            Log.e("sensorinfo", "Max delay: " + acceleration_sensor.getMaxDelay() + " - Fifo count" + acceleration_sensor.getFifoReservedEventCount());

            alarmManager = (AlarmManager) this.getSystemService(Context.ALARM_SERVICE);
            pendingIntent = PendingIntent.getService(this, 0, intent, 0);
            if (pendingIntent != null && alarmManager != null) {
                alarmManager.cancel(pendingIntent);
                Log.d("alarm", "Kill old alarm");
            }
            PowerManager powerManager = (PowerManager) this.getSystemService(POWER_SERVICE);
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                    "SensorReadings::FlushSensorLock");


        } else {
            triggeredAlarms++;
            Log.e("Sensorinfo", "Triggerd Sensorservice");
            flushSensor();
            alarmTriggers.add(SystemClock.elapsedRealtime());
            alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + 15 * 60 * 1000, pendingIntent);
        }
        return START_STICKY;
    }

    public void registerToManager(){
        recordings = new long[1000];
        pointer = 0;
        awakeCounter = 0;
        batchCounter = 0;
        isSleeping = false;
        final boolean batchMode = sensorManager.registerListener(this, acceleration_sensor, samplingRate, reportRate);
        if (!batchMode){
            Log.e("Sensorinfo", "Could not register sensor to batch");
        } else {
            Log.e("Sensorinfo", "Registered sensor to batch");
        }

        alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + 10 * 1000, pendingIntent);
        alarmTriggers.add(SystemClock.elapsedRealtime());
        lastSensorTimeStamp = 0;

        wakeLock.acquire(1000);
        /*
        Intent flushSensorIntent = new Intent(this, SensorEventListener.class);
        AlarmManager alarmManager = (AlarmManager) this.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pendingIntent = PendingIntent.getService(this, 0, flushSensorIntent, 0);
        alarmManager.setInexactRepeating(AlarmManager.RTC_WAKEUP, SystemClock.elapsedRealtime() + 10 * 1000, 20 * 1000, pendingIntent);
        Log.e("Sensorinfo", "Started Alarm");
        */

    }

    public void unregisterfromManager(){
        Log.i("sensorinfo", "unregister listener");
        sensorManager.unregisterListener(this);
        alarmManager.cancel(pendingIntent);
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
        long current = System.currentTimeMillis();
        long time =  current - lastTimeStamp;
        if (lastSensorTimeStamp == 0){
            lastSensorTimeStamp = event.timestamp;
        }

        if (isSleeping && time > 300 ) {
            batchCounter++;
            // Log.i("Sensor", "NewBatch" + batchCounter);
        }
        if (isSleeping && time > 20 * 1000 ) {
            awakeCounter++;
            // Log.i("Sensor", "Awake" + awakeCounter);
        }

        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            /*
            if (time > 100){
                Log.e("sensor", "New ACCELERO "+ " -> "+ time + "ms -> [" + event.timestamp + "]" + event.values[0] + " -> "+ event.values[1] +" -> "+ event.values[2]);
            }else {
                Log.i("sensor", "New ACCELERO " + " -> " + timea + "ms -> [" + event.timestamp + "]" + event.values[0] + " -> " + event.values[1] + " -> " + event.values[2]);
            }
            */

            lastTimeStamp = current;
            // 192260000
            if(event.timestamp - lastSensorTimeStamp > 250000000){
                //Log.e("sensor", "Lost sensor readings " + " -> " + (event.timestamp - lastSensorTimeStamp)/1000 + "ms");
                missedDelays.add(event.timestamp - lastSensorTimeStamp);
                errorCounter++;
            }

            recordings[pointer] = event.timestamp;
            lastSensorTimeStamp = event.timestamp;
            pointer++;
            if (pointer >= 1000) {
                pointer = 0;
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    public void SendSensorData(){
        StringBuilder data = new StringBuilder("[");
        for(long timeStamp : recordings){
            // Log.i("sensor", "RecordingService: " + timeStamp);
            data.append(timeStamp).append(", ");
        }
        data.setLength(data.length() - 2);
        data.append("]");

        //new CallAPI().execute("http://192.168.0.101:8989", data.toString());

        new CallAPI().execute("http://home.henkela.de", data.toString());
        Log.e("http", "test");
    }
}


class CallAPI extends AsyncTask<String, String, String> {

    public CallAPI(){
        //set context variables if required
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
    }

    @Override
    protected String doInBackground(String... params) {
        String urlString = params[0]; // URL to call
        String data = params[1]; //data to post
        OutputStream out = null;
        HttpURLConnection urlConnection = null;
        try {
            URL url = new URL(urlString);
            urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setDoOutput(true);
            urlConnection.setChunkedStreamingMode(0);
            urlConnection.setRequestMethod("POST");
            urlConnection.setRequestProperty("Accept-Charset", "UTF-8");

            urlConnection.setRequestProperty("Content-Length", ""+data.getBytes("UTF-8").length);
            //urlConnection.setChunkedStreamingMode(0);
            //urlConnection.setFixedLengthStreamingMode(data.getBytes("UTF-8").length);
            //urlConnection.setChunkedStreamingMode(0);

            urlConnection.setReadTimeout(1000);
            urlConnection.setConnectTimeout(1500);
            out = new BufferedOutputStream(urlConnection.getOutputStream());

            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(out, "UTF-8"));
            writer.write(data);
            writer.flush();
            writer.close();
            out.close();

            urlConnection.connect();
        } catch (Exception e) {
            Log.e("HTTP", "HTTPError:" + e.getMessage());
        }
        return "";
    }
}
