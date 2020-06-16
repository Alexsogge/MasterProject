package com.example.sensorcollecttest;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;


public class SensorListenerService extends Service implements SensorEventListener {


    private final IBinder binder = new LocalBinder();

    SensorManager sensorManager;
    Sensor sensor;
    int samplingRate = SensorManager.SENSOR_DELAY_NORMAL;
    int reportRate = 100000000;
    long lastTimeStamp;
    long[] recordings = new long[1000];
    int pointer = 0;
    Intent intent;
    boolean triggerPush;
    long awakeCounter = 0;
    boolean isSleeping = false;



    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        this.intent = intent;
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        Log.i("sensorinfo", String.valueOf(sensor.getFifoMaxEventCount()));
        Log.i("sensorinfo", String.valueOf(sensor.getFifoReservedEventCount()));
        Log.i("sensorinfo", String.valueOf(sensor.getMaxDelay()));
        Log.i("sensorinfo", String.valueOf(sensor.isWakeUpSensor()));
        Log.e("sensorinfo","Max delay: " + sensor.getMaxDelay() + " - Fifo count" + sensor.getFifoReservedEventCount());

        return START_STICKY;
    }

    public void registerToManager(){
        recordings = new long[1000];
        pointer = 0;
        awakeCounter = 0;
        isSleeping = false;
        final boolean batchMode = sensorManager.registerListener(this, sensor, samplingRate, reportRate);
        if (!batchMode){
            Log.e("Sensorinfo", "Could not register sensor to batch");
        } else {
            Log.e("Sensorinfo", "Registered sensor to batch");
        }
    }

    public void unregisterfromManager(){
        Log.i("sensorinfo", "unregister listener");
        sensorManager.unregisterListener(this);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

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

        if (isSleeping && time > 300 ) {
            awakeCounter++;
            // Log.i("Sensor", "Wakeup" + awakeCounter);
        }

        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            /*
            if (time > 100){
                Log.e("sensor", "New ACCELERO "+ " -> "+ time + "ms -> [" + event.timestamp + "]" + event.values[0] + " -> "+ event.values[1] +" -> "+ event.values[2]);
            }else {
                Log.i("sensor", "New ACCELERO " + " -> " + time + "ms -> [" + event.timestamp + "]" + event.values[0] + " -> " + event.values[1] + " -> " + event.values[2]);
            }
            */

            lastTimeStamp = current;

            recordings[pointer] = event.timestamp;
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