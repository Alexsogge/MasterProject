package com.example.sensorcollecttest;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.hardware.SensorAdditionalInfo;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.IBinder;
import android.support.wearable.activity.WearableActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import java.util.Calendar;
import java.util.Date;

public class MainActivity extends WearableActivity {

    private TextView mTextView;
    long delay_sec = 5;
    long multiplicator = 1000000000;
    long delay = delay_sec * multiplicator;
    boolean triggerPush = false;
    Intent intent;
    SensorListenerService sensorService;
    boolean mBound = false;
    Date startTime;
    float startCharge;
    boolean isActive = false;
    Intent batteryStatus;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mTextView = (TextView) findViewById(R.id.text);

        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        batteryStatus = registerReceiver(null, ifilter);

        intent = new Intent(this, SensorListenerService.class );
        bindService(intent, connection, Context.BIND_AUTO_CREATE);
        startService(intent);


        final TextView resultText = (TextView)findViewById(R.id.textViewResult);

        final Button button = (Button) findViewById(R.id.buttonStartStop);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isActive){
                    button.setText("Stop");
                    isActive = true;
                    startTime = Calendar.getInstance().getTime();

                    int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                    int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

                    startCharge = level * 100 / (float)scale;
                    sensorService.registerToManager();

                } else {
                    button.setText("Start");
                    Date endTime = Calendar.getInstance().getTime();
                    IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
                    batteryStatus = registerReceiver(null, ifilter);
                    int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                    int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

                    float endCharge = level * 100 / (float)scale;

                    long processDuration = endTime.getTime() - startTime.getTime();

                    int hours = (int)(processDuration / (1000 * 60 * 60));
                    int minutes = (int)(processDuration / (1000 * 60)) % (60);
                    float chargeDiff = startCharge - endCharge;

                    Log.i("report", "start at " + startTime.toString() + " level: " + startCharge);
                    Log.i("report", "stop at " + endTime.toString() + " level: " + endCharge);
                    Log.i("report", "lost " + chargeDiff + "% charge over " + hours + " hours and " + minutes + " minutes.");
                    Log.i("report", "Did " + sensorService.awakeCounter + " wake ups.");
                    isActive = false;
                    resultText.setText("lost " + chargeDiff + "% charge over " + hours + " hours and " + minutes + " minutes.\n");
                    resultText.append("Did " + sensorService.awakeCounter + " wake ups.");
                    sensorService.unregisterfromManager();
                }
            }
        });

        // Enables Always-on
        setAmbientEnabled();

    }

    @Override
    protected void onResume() {
        super.onResume();
        if(mBound) {
            sensorService.SendSensorData();
            sensorService.isSleeping = false;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.i("sensorinfo", "Paused -> start service");
        if(mBound) {
            sensorService.isSleeping = true;
        }
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
