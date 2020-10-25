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
import android.os.Binder;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;


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
    PowerManager.WakeLock wakeLock;

    File recording_file_acc;
    FileOutputStream file_output_acc;
    File recording_file_gyro;
    FileOutputStream file_output_gyro;
    public static final String CHANNEL_ID = "ForegroundServiceChannel";



    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d("sensorinfo", "Start service");

        createNotificationChannel();
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this,
                0, notificationIntent, 0);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Foreground Service")
                .setContentText("SensorRecorder")
                .setContentIntent(pendingIntent)
                .build();

        startForeground(1, notification);


        if (!initialized) {
            initialized = true;
            this.intent = intent;
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
            try {
                file_output_acc = new FileOutputStream(recording_file_acc);
                file_output_gyro = new FileOutputStream(recording_file_gyro);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }


            Log.i("sensorinfo", String.valueOf(acceleration_sensor.getFifoMaxEventCount()));
            Log.i("sensorinfo", String.valueOf(acceleration_sensor.getFifoReservedEventCount()));
            Log.i("sensorinfo", String.valueOf(acceleration_sensor.getMaxDelay()));
            Log.i("sensorinfo", String.valueOf(acceleration_sensor.isWakeUpSensor()));
            Log.e("sensorinfo", "Max delay: " + acceleration_sensor.getMaxDelay() + " - Fifo count" + acceleration_sensor.getFifoReservedEventCount());


            registerToManager();

            PowerManager powerManager = (PowerManager) this.getSystemService(POWER_SERVICE);
            PowerManager.WakeLock wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                    "SensorReadings::FlushSensorLock");
            wakeLock.acquire(600*60*1000L /*10 minutes*/);

        } else {

        }
        return START_STICKY;
    }

    public void registerToManager(){
        recording_timestamps_acc = new long[1000];
        recording_values_acc = new float[1000][3];
        recording_timestamps_gyro = new long[1000];
        recording_values_gyro = new float[1000][3];
        pointer_acc = 0;
        pointer_gyro = 0;
        boolean batchMode = sensorManager.registerListener(this, acceleration_sensor, samplingRate, reportRate);
        batchMode = sensorManager.registerListener(this, gyro_sensor, samplingRate, reportRate) && batchMode;
        if (!batchMode){
            Log.e("sensorinfo", "Could not register sensor to batch");
        } else {
            Log.e("sensorinfo", "Registered sensor to batch");
        }

        // wakeLock.acquire(1000);
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
        // wakeLock.release();
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
                pointer_acc = 0;
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
                pointer_gyro = 0;
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    public void SendSensorDataAcc() throws IOException {
        Log.e("write", "Write File");
        StringBuilder data = new StringBuilder();
        for(int i = 0; i < pointer_acc; i++){
            // Log.i("sensor", "RecordingService: " + timeStamp);
            data.append(recording_timestamps_acc[i]).append("\t");
            data.append(recording_values_acc[i][0]).append("\t");
            data.append(recording_values_acc[i][1]).append("\t");
            data.append(recording_values_acc[i][2]).append("\n");
        }
        file_output_acc.write(data.toString().getBytes());
        file_output_acc.flush();
        Log.e("write", "Flushed File acc");
    }

    public void SendSensorDataGyro() throws IOException {
        Log.e("write", "Write File");
        StringBuilder data = new StringBuilder();
        for(int i = 0; i < pointer_gyro; i++){
            // Log.i("sensor", "RecordingService: " + timeStamp);
            data.append(recording_timestamps_gyro[i]).append("\t");
            data.append(recording_values_gyro[i][0]).append("\t");
            data.append(recording_values_gyro[i][1]).append("\t");
            data.append(recording_values_gyro[i][2]).append("\n");
        }
        file_output_gyro.write(data.toString().getBytes());
        file_output_gyro.flush();
        Log.e("write", "Flushed File gyro");
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
}
