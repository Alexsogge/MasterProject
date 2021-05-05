package unifr.sensorrecorder;

import android.Manifest;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.icu.util.Calendar;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Debug;
import android.os.IBinder;
import android.os.PowerManager;
import android.provider.Settings;
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
import androidx.fragment.app.FragmentActivity;
import androidx.wear.ambient.AmbientModeSupport;
import androidx.work.WorkManager;

import java.io.IOException;
import unifr.sensorrecorder.EventHandlers.BatteryEventHandler;
import unifr.sensorrecorder.EventHandlers.ChargeEventHandler;
import unifr.sensorrecorder.EventHandlers.OverallEvaluationReminder;
import unifr.sensorrecorder.Networking.NetworkManager;
import unifr.sensorrecorder.Networking.ServerTokenObserver;
import unifr.sensorrecorder.Networking.UploadObserver;

import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;
import static android.content.pm.PackageManager.PERMISSION_DENIED;



public class MainActivity extends FragmentActivity
        implements AmbientModeSupport.AmbientCallbackProvider{
    private static double FACTOR = 0.2; // c = a * sqrt(2)

    public static MainActivity mainActivity;
    private boolean isActive = false;

    private TextView mTextView;
    private Intent intent;
    public SensorRecordingManager sensorService;
    // public EvaluationService evaluationService;
    public NetworkManager networkManager;

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
    private ChargeEventHandler chargeEventHandler;
    private Button startStopButton;
    private Button uploadButton;

    private ScrollView mainScrollView;
    private CustomSpinner handWashSpinner;

    // ML stuff
    public HandWashDetection handWashDetection;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.d("main", "create main activity");
        if(!isActive) {
            isActive = true;
            turnOffDozeMode(this);

            WorkManager.getInstance(mainActivity).cancelAllWork();
            WorkManager.getInstance(mainActivity).pruneWork();

            mainActivity = this;
            setOverallEvaluationReminder();

            // set scroll view to correct size
            adjustInset();

            try {
                handWashDetection = new HandWashDetection(this);
            } catch (IOException e) {
                e.printStackTrace();
            }

            // initialize user interface elements
            initUI();

            // get settings. If not already set open config activity
            loadConfigs();

            if (!waitForConfigs)
                initServices();

            // NotificationSpawner.showOverallEvaluationNotification(this);
            // NotificationSpawner.spawnHandWashPredictionNotification(this, 1000);

        }

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
                    Toast.makeText(MainActivity.this, getResources().getString(R.string.toast_addedhandwash_sel) + ((TextView)view).getText(), Toast.LENGTH_SHORT).show();
               }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

                // sometimes you need nothing here
            }
        });

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
        handWashDetection.initModel();
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
        networkManager = new NetworkManager(this, null, configs);
        batteryEventHandler = new BatteryEventHandler();
        chargeEventHandler = new ChargeEventHandler();
//        Intent intentEvalServ = new Intent(this, EvaluationService.class);
//        bindService(intentEvalServ, evaluationConnection, Context.BIND_AUTO_CREATE);
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            startForegroundService(intentEvalServ);
//        } else {
//            startService(intentEvalServ);
//        }

        // check if needed permissions are granted
        if (ContextCompat.checkSelfPermission(this, WRITE_EXTERNAL_STORAGE) == PERMISSION_DENIED) {
            ActivityCompat.requestPermissions(MainActivity.this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    1);
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

    private void startServices(){
        // set system calls for battery changes
        // implicit broadcasts are not supported in manifest.xml since API level 26
        // https://developer.android.com/guide/components/broadcast-exceptions
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        filter.addAction(Intent.ACTION_BATTERY_LOW);
        this.registerReceiver(batteryEventHandler, filter);

        IntentFilter filter2 = new IntentFilter(Intent.ACTION_POWER_CONNECTED);
        filter2.addAction(Intent.ACTION_POWER_DISCONNECTED);
        this.registerReceiver(chargeEventHandler, filter2);

        startRecording();
    }

    private void startRecording(){
        intent = new Intent(this, SensorRecordingManager.class );
        bindService(intent, sensorConnection, Context.BIND_AUTO_CREATE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    private void setOverallEvaluationReminder(){
        cancelOverallEvaluationReminder();

        Calendar calendar = Calendar.getInstance();
        Calendar targetDate = Calendar.getInstance();
        targetDate.setTimeInMillis(System.currentTimeMillis());
        targetDate.set(Calendar.HOUR_OF_DAY, 18);
        targetDate.set(Calendar.MINUTE, 0);
        targetDate.set(Calendar.SECOND, 0);
        //if(targetDate.before(calendar))
        //    targetDate.add(Calendar.DATE, 1);

        Intent reminderReceiver = new Intent(this, OverallEvaluationReminder.class);
        PendingIntent reminderPint = PendingIntent.getBroadcast(this, NotificationSpawner.DAILY_REMINDER_REQUEST_CODE, reminderReceiver, PendingIntent.FLAG_UPDATE_CURRENT);
        AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, targetDate.getTimeInMillis(), AlarmManager.INTERVAL_DAY, reminderPint);
    }

    private void cancelOverallEvaluationReminder(){

    }


    private ServiceConnection sensorConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            // get SensorService instance when ready
            SensorRecordingManager.LocalBinder binder = (SensorRecordingManager.LocalBinder) service;
            sensorService = binder.getService();
            sensorService.mainActivity = mainActivity;
            mBound = true;
            // initialize services components
            sensorService.infoText = (TextView) findViewById(R.id.infoText);
            sensorService.startStopButton = startStopButton;
            networkManager.sensorService = sensorService;
            batteryEventHandler.sensorRecordingManager = sensorService;
            if(sensorService.isRunning)
                startStopButton.setText(getString(R.string.btn_stop));
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
                    Toast.makeText(MainActivity.this, getResources().getString(R.string.toast_permission_den), Toast.LENGTH_SHORT).show();
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

    @Override
    public AmbientModeSupport.AmbientCallback getAmbientCallback() {
        return null;
    }
}
