package com.example.sensorrecorder;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.provider.Settings;
import android.support.wearable.activity.WearableActivity;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;


import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;
import static android.content.pm.PackageManager.PERMISSION_DENIED;


public class MainActivity extends WearableActivity {
    private static double FACTOR = 0.2; // c = a * sqrt(2)

    private TextView mTextView;
    private Intent intent;
    private SensorListenerService sensorService;
    private Networking networking;

    private boolean mBound = false;
    private boolean waitForConfigs = false;

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

    private ScrollView mainScrollView;
    private CustomSpinner handWashSpinner;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        turnOffDozeMode(this);

        // set scroll view to correct size
        adjustInset();

        // initialize user interface elements
        initUI();

        // get settings. If not already set open config activity
        loadConfigs();

        if(!waitForConfigs)
            initServices();

        // Enables Always-on
        //setAmbientEnabled();
    }

    private void loadConfigs(){
        configs = this.getSharedPreferences(
                getString(R.string.configs), Context.MODE_PRIVATE);
        configIntent = new Intent(this, ConfActivity.class);
        if (!configs.contains(getString(R.string.conf_serverName)) || !configs.contains(getString(R.string.conf_userIdentifier))){
            Log.d("config", configs.contains(getString(R.string.conf_serverName)) + "  " + configs.contains(getString(R.string.conf_userIdentifier)));
            if(!waitForConfigs) {
                waitForConfigs = true;
                startActivity(configIntent);
            }
        } else {
            waitForConfigs = false;
        }
        updateUploadButton();
    }

    private void initUI(){
        mainScrollView = (ScrollView)findViewById(R.id.mainview);

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

        handWashSpinner = (CustomSpinner) findViewById(R.id.handwash_spinner);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.handwash_spinner_values, android.R.layout.simple_spinner_item);

        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        handWashSpinner.setAdapter(adapter);

        handWashSpinner.setSpinnerEventsListener(new CustomSpinner.OnSpinnerEventsListener(){

            @Override
            public void onSpinnerOpened(Spinner spin) {
                mainScrollView.scrollTo(0, 100);
            }

            @Override
            public void onSpinnerClosed(Spinner spin) {

            }
        });

        handWashSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
               if (sensorService != null){
                    sensorService.addHandWashEventBefore(position * 60 * 5);
                    handWashSpinner.setNoSelection();
                    Toast.makeText(MainActivity.this, "Added hand wash\n" + ((TextView)view).getText(), Toast.LENGTH_SHORT).show();
               }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

                // sometimes you need nothing here
            }
        });
    }

    private void updateUploadButton(){
        if(configs.getString(getString(R.string.conf_serverName), "").equals("")){
            uploadButton.setEnabled(false);
        } else {
            uploadButton.setEnabled(true);
        }
    }

    private void initServices(){
        networking = new Networking(this, null, configs);
        batteryEventHandler = new BatteryEventHandler();

        // check if needed permissions are granted
        if (ContextCompat.checkSelfPermission(this, WRITE_EXTERNAL_STORAGE) == PERMISSION_DENIED) {
            ActivityCompat.requestPermissions(MainActivity.this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    1);
        } else {
            startServices();
        }
    }

    private void startServices(){
        // set system calls for battery changes
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        filter.addAction(Intent.ACTION_BATTERY_LOW);
        this.registerReceiver(batteryEventHandler, filter);

        uploadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // sensorService.UploadSensorData();
                if(!configs.getString(getString(R.string.conf_serverName), "").equals("")) {
                    mainScrollView.scrollTo(0, 150);
                    networking.DoFileUpload();
                }
            }
        });

        startStopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (sensorService.isRunning) {
                    sensorService.stopRecording();
                    sensorService.dataProcessor.backup_recording_files();
                } else {
                    sensorService.startRecording();
                }
            }
        });

        startRecording();
    }

    private void startRecording(){
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
            // get SensorService instance when ready
            SensorListenerService.LocalBinder binder = (SensorListenerService.LocalBinder) service;
            sensorService = binder.getService();
            mBound = true;
            // initialize services components
            sensorService.infoText = (TextView) findViewById(R.id.infoText);
            sensorService.startStopButton = startStopButton;
            networking.sensorService = sensorService;
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
        Log.d("Sensorrecorder", "rc: " + requestCode +  "length: "+permissions.length + " gr: " + grantResults.length);
        switch (requestCode) {
            case 1: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // permission was granted, start services
                    startServices();
                    startRecording();
                } else {
                    ActivityCompat.requestPermissions(MainActivity.this,
                            new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                            1);
                    // permission denied
                    Toast.makeText(MainActivity.this, "Permission denied", Toast.LENGTH_SHORT).show();
                }
                return;
            }
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

    protected void onResume () {
        super.onResume();
        Log.d("sensorrecorderevent", "Resume main actitvity");
        if(waitForConfigs){
            loadConfigs();
            if(!waitForConfigs)
                initServices();
        }
        updateUploadButton();
    }

    protected void onPause () {
        super.onPause();
        Log.d("sensorrecorderevent", "Pause main actitvity");
    }
}
