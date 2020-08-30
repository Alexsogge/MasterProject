package com.example.sensorcollecttest;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorEventListener2;
import android.hardware.SensorListener;
import android.hardware.SensorManager;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;

import static android.content.Context.POWER_SERVICE;

public class FlushSensor extends Service {
    private static final String TAG = "FlushSensor";
    SensorListenerService sensorService;
    Intent serviceIntent;
    boolean mBound = false;

    public void onCreate(){
        Log.d("alarm", "created Flush sensor Service");
        serviceIntent = new Intent(this, SensorListenerService.class );
        bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        PowerManager powerManager = (PowerManager) this.getSystemService(POWER_SERVICE);
        PowerManager.WakeLock wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                "SensorReadings::FlushSensorLock");
        wakeLock.acquire(1000);

        Log.d("alarm", "Flush sensor Service");


        if (mBound)
            sensorService.flushSensor();


        wakeLock.release();
        return START_STICKY;
    }


    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private ServiceConnection connection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            SensorListenerService.LocalBinder binder = (SensorListenerService.LocalBinder) service;
            sensorService = binder.getService();
            mBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;
        }
    };
}