package com.example.sensorrecorder;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.provider.Settings;
import android.support.wearable.activity.WearableActivity;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import androidx.wear.widget.WearableLinearLayoutManager;
import androidx.wear.widget.WearableRecyclerView;

import com.google.android.gms.common.util.ArrayUtils;

import java.util.ArrayList;


import static android.Manifest.permission.RECORD_AUDIO;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;
import static android.content.pm.PackageManager.PERMISSION_DENIED;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;


public class MainActivity extends WearableActivity {
    private static double FACTOR = 0.2; // c = a * sqrt(2)

    private TextView mTextView;
    private Intent intent;
    private SensorListenerService sensorService;
    private Networking networking;

    boolean mBound = false;

    private TextView infoText;
    private ProgressBar uploadProgressBar;
    private SharedPreferences configs;
    private Intent configIntent;

    // Requesting permission to RECORD_AUDIO and
    private boolean permissionToRecordAccepted = false;
    private String [] permissions = {Manifest.permission.RECORD_AUDIO};
    private BatteryEventHandler batteryEventHandler;
    private Button startStopButton;
    private Button uploadButton;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        turnOffDozeMode(this);
        /*
        WearableRecyclerView myView = (WearableRecyclerView)findViewById(R.id.recycler_launcher_view);
        myView.setEdgeItemsCenteringEnabled(true);
        // myView.setLayoutManager(new WearableLinearLayoutManager(this));
        CustomScrollingLayoutCallback customScrollingLayoutCallback =
                new CustomScrollingLayoutCallback();
        myView.setLayoutManager(
                new WearableLinearLayoutManager(this, customScrollingLayoutCallback));

        */

        adjustInset();

        try {
            PackageInfo pInfo = this.getPackageManager().getPackageInfo(this.getPackageName(), 0);
            String version = pInfo.versionName;
            Log.e("sensorrecorder", "Android version: " + version);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }


        initUI();


        configs = this.getSharedPreferences(
                getString(R.string.configs), Context.MODE_PRIVATE);
        configIntent = new Intent(this, ConfActivity.class);
        if (!configs.contains(getString(R.string.conf_serverName)) || !configs.contains(getString(R.string.conf_userIdentifier))){
            startActivity(configIntent);
        }

        networking = new Networking(this, null, configs);
        batteryEventHandler = new BatteryEventHandler();




        if (ContextCompat.checkSelfPermission(this, WRITE_EXTERNAL_STORAGE) == PERMISSION_DENIED ||
                ContextCompat.checkSelfPermission(this, RECORD_AUDIO) == PERMISSION_DENIED) {
            ActivityCompat.requestPermissions(MainActivity.this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.RECORD_AUDIO},
                    1);

        } else {
            IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            filter.addAction(Intent.ACTION_BATTERY_LOW);
            this.registerReceiver(batteryEventHandler, filter);


            StartRecordService();
            uploadButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // sensorService.UploadSensorData();
                    networking.DoFileUpload();

                }
            });

            startStopButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (sensorService.isRunning) {
                        sensorService.stopRecording();
                        sensorService.backup_recording_files();
                    } else {
                        sensorService.startRecording();
                    }
                }
            });
        }


        // Enables Always-on
        //setAmbientEnabled();
    }

    private void initUI(){
        infoText = (TextView) findViewById(R.id.infoText);
        uploadProgressBar = (ProgressBar) findViewById(R.id.uploaadProgressBar);
        uploadProgressBar.setMax(100);

        startStopButton = (Button) findViewById(R.id.startStopButton);
        uploadButton = (Button) findViewById(R.id.uploadaButton);

        Button configButton = (Button)findViewById(R.id.buttonConfig);
        configButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(configIntent);
            }
        });
    }

    private void StartRecordService(){
        intent = new Intent(this, SensorListenerService.class );
        bindService(intent, connection, Context.BIND_AUTO_CREATE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
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
            sensorService.infoText = (TextView) findViewById(R.id.infoText);
            sensorService.startStopButton = startStopButton;
            networking.sensorService = sensorService;
            sensorService.networking = networking;
            batteryEventHandler.sensorListenerService = sensorService;
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;
        }
    };


    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case 1: {

                for (int i = 0; i < permissions.length; i++){
                    Log.e("sensorrecorder", "Permission: " + permissions[i] + " -> " + grantResults[i]);
                }

                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.
                    StartRecordService();
                } else {

                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                    Toast.makeText(MainActivity.this, "Permission denied to read your External storage", Toast.LENGTH_SHORT).show();
                }
                return;
            }

            // other 'case' lines to check for other
            // permissions this app might request
        }
    }

    public void turnOffDozeMode(Context context){  //you can use with or without passing context
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent intent = new Intent();
            String packageName = context.getPackageName();
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (pm.isIgnoringBatteryOptimizations(packageName)) // if you want to desable doze mode for this package
                intent.setAction(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
            else { // if you want to enable doze mode
                intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + packageName));
            }
            context.startActivity(intent);
        }
    }

    private void adjustInset() {
        if (getResources().getConfiguration().isScreenRound()) {
            int inset = (int)(FACTOR * getResources().getConfiguration().screenWidthDp);
            View layout = (View) findViewById(R.id.mainview);
            layout.setPadding(inset, inset, inset, inset);
        }
    }
}


class CustomScrollingLayoutCallback extends WearableLinearLayoutManager.LayoutCallback {
    /**
     * How much should we scale the icon at most.
     */
    private static final float MAX_ICON_PROGRESS = 0.65f;

    private float progressToCenter;

    @Override
    public void onLayoutFinished(View child, RecyclerView parent) {

        // Figure out % progress from top to bottom
        float centerOffset = ((float) child.getHeight() / 2.0f) / (float) parent.getHeight();
        float yRelativeToCenterOffset = (child.getY() / parent.getHeight()) + centerOffset;

        // Normalize for center
        progressToCenter = Math.abs(0.5f - yRelativeToCenterOffset);
        // Adjust to the maximum scale
        progressToCenter = Math.min(progressToCenter, MAX_ICON_PROGRESS);

        child.setScaleX(1 - progressToCenter);
        child.setScaleY(1 - progressToCenter);
    }
}

