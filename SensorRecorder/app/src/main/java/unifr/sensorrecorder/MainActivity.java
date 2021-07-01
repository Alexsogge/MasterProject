package unifr.sensorrecorder;

import android.Manifest;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.icu.util.Calendar;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.wear.ambient.AmbientModeSupport;
import androidx.work.WorkManager;

import unifr.sensorrecorder.DataContainer.StaticDataProvider;
import unifr.sensorrecorder.EventHandlers.OverallEvaluationReminder;
import unifr.sensorrecorder.EventHandlers.OverallEvaluationReminderStarter;
import unifr.sensorrecorder.EventHandlers.UpdateTFModelReceiver;
import unifr.sensorrecorder.Networking.NetworkManager;
import unifr.sensorrecorder.Networking.ServerTokenObserver;
import unifr.sensorrecorder.Networking.UploadObserver;

import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;
import static android.content.pm.PackageManager.PERMISSION_DENIED;



public class MainActivity extends FragmentActivity
        implements AmbientModeSupport.AmbientCallbackProvider{
    private static double FACTOR = 0.146467f; // c = a * sqrt(2)
    private boolean isActive = false;

    private TextView mTextView;
    private Intent intent;
    private SensorRecordingManager sensorService;
    // public EvaluationService evaluationService;
    private NetworkManager networkManager;

    private boolean mBound = false;
    private boolean waitForConfigs = false;

    private TextView infoText;
    private ProgressBar uploadProgressBar;
    private SharedPreferences configs;
    private Intent configIntent;

    // Requesting permission to RECORD_AUDIO and
    private boolean permissionToRecordAccepted = false;
    private String [] permissions = {Manifest.permission.RECORD_AUDIO};
    private UpdateTFModelReceiver updateTFModelReceiver;
    private Button startStopButton;
    private Button uploadButton;

    private ScrollView mainScrollView;
    //private CustomSpinner handWashSpinner;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.d("main", "create main activity");
        if(!isActive) {
            isActive = true;
            turnOffDozeMode(this);

            WorkManager.getInstance(this).cancelAllWork();
            WorkManager.getInstance(this).pruneWork();

            // NotificationSpawner.deleteAllChannels(this.getApplicationContext());
            NotificationSpawner.createChannels(this.getApplicationContext());
            setOverallEvaluationReminder();

            // set scroll view to correct size
            adjustInset();

            // initialize user interface elements
            initUI();

            // get settings. If not already set open config activity
            loadConfigs();

            if (!waitForConfigs)
                initServices();

            // NotificationSpawner.showOverallEvaluationNotification(this.getApplicationContext());
            // NotificationSpawner.spawnHandWashPredictionNotification(this.getApplicationContext(), 1000);

        }

        adjustInset();
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
        mainScrollView = (ScrollView) findViewById(R.id.mainScrollView);

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

        /*
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
                    Toast.makeText(MainActivity.this, getResources().getString(R.string.toast_addedhandwash_sel) + ((TextView)view).getText(), Toast.LENGTH_SHORT).show();
               }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

                // sometimes you need nothing here
            }
        });
        */

        uploadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // sensorService.UploadSensorData();
                toggleUpload();
            }
        });

        startStopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (sensorService.isRunning) {
                    toggleStopRecording();
                } else {
                    toggleStartRecording();
                }
            }
        });
    }

    public void toggleStartRecording(){
        if(configs.getBoolean(getString(R.string.conf_check_for_tf_update), false) || configs.getBoolean(getString(R.string.conf_auto_update_tf), false))
            NetworkManager.checkForTFModelUpdate(getApplicationContext());
        // handWashDetection.initModel();
        sensorService.startRecording();
    }

    public void toggleStopRecording(){
        sensorService.directlyStopRecording();
        // sensorService.dataProcessor.backup_recording_files();
    }

    public void toggleUpload(){
        if(!configs.getString(getString(R.string.conf_serverName), "").equals("")) {
            mainScrollView.scrollTo(0, 150);
            networkManager.DoFileUpload();
        }
    }

    private void updateUploadButton(){
        if(configs.getString(getString(R.string.conf_serverName), "").equals("")){
            uploadButton.setEnabled(false);
        } else {
            uploadButton.setEnabled(true);
        }
    }

    private void initServices(){
        networkManager = StaticDataProvider.getNetworkManager();
        networkManager.initialize(this, sensorService, infoText);
        updateTFModelReceiver = new UpdateTFModelReceiver();

        boolean bluetooth = (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED);
        boolean bluetoothAdmin = (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED);
        boolean coarseLocation = (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED);
        boolean writeExternalStorage = (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED);

        boolean useBluetooth = configs.getBoolean(getString(R.string.conf_scan_bluetooth_beacons), false);

        // Request enable Bluetooth if not yet enabled
        if (useBluetooth && bluetooth && bluetoothAdmin) {
            if ((BluetoothAdapter.getDefaultAdapter() == null) || !BluetoothAdapter.getDefaultAdapter().isEnabled()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, 2);
            }
        }

        if (useBluetooth && (!bluetooth || !bluetoothAdmin || !coarseLocation) || !writeExternalStorage) {
            askForPermission();
        } else {
            startServices();
        }

        WorkManager.getInstance(this)
                .getWorkInfosByTagLiveData("uploadWorker")
                .observe(this, new UploadObserver(infoText, uploadProgressBar, this));

        WorkManager.getInstance(this)
                .getWorkInfosByTagLiveData("serverTokenWorker")
                .observe(this, new ServerTokenObserver(infoText, this));
    }

    private void askForPermission(){
        boolean useBluetooth = configs.getBoolean(getString(R.string.conf_scan_bluetooth_beacons), false);
        String[] permissions = new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE};
        if(useBluetooth)
            permissions = new String[]{
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_ADMIN,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            };
        ActivityCompat.requestPermissions(MainActivity.this, permissions, 1);
    }

    private void startServices(){
        // set system calls for battery changes
        // implicit broadcasts are not supported in manifest.xml since API level 26
        // https://developer.android.com/guide/components/broadcast-exceptions

        /*
        IntentFilter filter3 = new IntentFilter();
        filter3.addAction(UpdateTFModelReceiver.BROADCAST_ACTION);
        this.registerReceiver(updateTFModelReceiver, filter3);
        */
        startRecording();
    }

    private void startRecording(){
        if(configs.getBoolean(getString(R.string.conf_check_for_tf_update), false) || configs.getBoolean(getString(R.string.conf_auto_update_tf),false))
            NetworkManager.checkForTFModelUpdate(getApplicationContext());
        intent = new Intent(this, SensorRecordingManager.class );
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
        if(!mBound)
            bindService(intent, sensorConnection, Context.BIND_AUTO_CREATE);
    }

    private void setOverallEvaluationReminder(){
        Calendar targetDate = Calendar.getInstance();
        targetDate.setTimeInMillis(System.currentTimeMillis());
        targetDate.set(Calendar.HOUR_OF_DAY, OverallEvaluationReminderStarter.REMINDER_HOUR);
        targetDate.set(Calendar.MINUTE, 15);
        targetDate.set(Calendar.SECOND, 0);
        // Calendar calendar = Calendar.getInstance();
        // if(targetDate.before(calendar))
        //    targetDate.add(Calendar.DATE, 1);

        Intent reminderReceiver = new Intent(this, OverallEvaluationReminderStarter.class);
        PendingIntent reminderPint = PendingIntent.getBroadcast(this, NotificationSpawner.DAILY_REMINDER_STARTER_REQUEST_CODE, reminderReceiver, PendingIntent.FLAG_UPDATE_CURRENT);
        AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, targetDate.getTimeInMillis(), reminderPint);
    }



    private ServiceConnection sensorConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            // get SensorService instance when ready
            SensorRecordingManager.LocalBinder binder = (SensorRecordingManager.LocalBinder) service;
            sensorService = binder.getService();
            mBound = true;
            // initialize services components
            sensorService.initUIElements(startStopButton);
            networkManager.sensorService = sensorService;
            if(sensorService.isRunning)
                startStopButton.setText(getString(R.string.btn_stop));
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;
        }
    };


    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        Log.d("Sensorrecorder", "rc: " + requestCode +  "length: "+permissions.length + " gr: " + grantResults.length);
        if (requestCode == 1) {
            if (grantResults.length > 1) {
                boolean bluetooth = grantResults[0] == PackageManager.PERMISSION_GRANTED;
                boolean bluetoothAdmin = grantResults[1] == PackageManager.PERMISSION_GRANTED;
                boolean fineLocation = grantResults[2] == PackageManager.PERMISSION_GRANTED;
                boolean writeExternalStorage = grantResults[3] == PackageManager.PERMISSION_GRANTED;

                if(bluetooth && bluetoothAdmin && fineLocation && writeExternalStorage) {
                    startServices();
                } else {
                    askForPermission();
                    Toast.makeText(this, getResources().getString(R.string.toast_permission_den), Toast.LENGTH_SHORT).show();
                }
            } else if(grantResults.length == 1){
                boolean writeExternalStorage = grantResults[0] == PackageManager.PERMISSION_GRANTED;
                if(writeExternalStorage){
                    startServices();
                } else {
                    askForPermission();
                    Toast.makeText(this, getResources().getString(R.string.toast_permission_den), Toast.LENGTH_SHORT).show();
                }
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
            int inset = (int)(FACTOR * getResources().getDisplayMetrics().widthPixels);
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

    public void onStop() {
        super.onStop();
        Log.d("activity", "on stop main");
    }

    public void onDestroy () {
        super.onDestroy();
        Log.d("activity", "on destroy main");
        if (mBound) {
            unbindService(sensorConnection);
        }
        //unregisterReceiver(updateTFModelReceiver);
        //updateTFModelReceiver = null;
    }

    @Override
    public AmbientModeSupport.AmbientCallback getAmbientCallback() {
        return null;
    }
}
