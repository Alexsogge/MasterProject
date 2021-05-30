package unifr.sensorrecorder;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.media.MediaRecorder;
import android.os.Binder;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.support.wearable.complications.ProviderUpdateRequester;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;


/*
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dimensionalityreduction.PCA;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.ops.transforms.Transforms;
*/



import java.io.File;
import java.io.IOException;
import java.nio.ByteOrder;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.TimeZone;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

import de.uni_freiburg.ffmpeg.FFMpegProcess;
import unifr.sensorrecorder.Complication.ComplicationProvider;
import unifr.sensorrecorder.DataContainer.DataContainer;
import unifr.sensorrecorder.DataContainer.DataProcessor;
import unifr.sensorrecorder.DataContainer.StaticDataProvider;
import unifr.sensorrecorder.EventHandlers.BatteryEventHandler;
import unifr.sensorrecorder.EventHandlers.ChargeEventHandler;
import unifr.sensorrecorder.EventHandlers.EvaluationReceiver;


import static android.Manifest.permission.RECORD_AUDIO;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;


public class SensorRecordingManager extends Service implements SensorManagerInterface{
    private final Executor executor = Executors.newSingleThreadExecutor(); // change according to your requirements
    private final IBinder binder = new LocalBinder();
    private final int samplingRate = 20000;
    private final int reportRate = 1000000;
    private final int sensor_queue_size = 1000;
    private final long sensorDelay = (long) (1e9 / 50);
    private final int[] possibleSensors = new int[]{Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_GYROSCOPE, Sensor.TYPE_MAGNETIC_FIELD, Sensor.TYPE_ROTATION_VECTOR};
    //private final int[] possibleSensors = new int[]{Sensor.TYPE_ACCELEROMETER};
    private final float[] possibleHandWashActivateThresholds = new float[]{15f, 15f, -1f, -1f};
    private final int[] sensorsUsedInTFModel = new int[]{Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_GYROSCOPE};


    // handle main ui
    private Button startStopButton;
    public static boolean isRunning = true;

    // service stuff
    private Intent intent;
    private boolean initialized = false;
    private PowerManager.WakeLock wakeLock;
    private BatteryEventHandler batteryEventHandler;
    private ChargeEventHandler chargeEventHandler;

    // sensor recording stuff
    private android.hardware.SensorManager sensorManager;
    private Sensor[] activeSensors;
    private SensorListenerService[] sensorServices;
    private int[] sensorDimensions;
    private long sensorStartTime;

    // data files
    private DataProcessor dataProcessor;

    // ffmpeg stuff
    private FFMpegProcess mFFmpeg;

    // Microphone stuff
    private MediaRecorder mediaRecorder;
    private boolean ongoing_mic_record = false;
    private int micCounter = 0;
    private ReentrantLock mediaRecorderLock = new ReentrantLock();


    // config stuff
    private SharedPreferences configs;
    private boolean useMKVStream = false;
    private boolean useZIPStream = true;
    private boolean useMic = true;
    private boolean useMultipleMic = true;


    // ML stuff
    private HandWashDetection handWashDetection;


    /**
     * Initial method of service. It's called if we start the service or send triggers over an intent
     * @param intent
     * @param flags
     * @param startId
     * @return
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // due to we call this function multiple times from the notification buttons we have to determine when we called it the first time
        // if a call wasn't the initial one it came from a notification button
        // therefore we have to determine which action should be triggered

        if (intent != null && intent.getStringExtra("trigger") != null){
            // start recording
            if (intent.getStringExtra("trigger").equals("startRecording")) {
                if(!isRunning && initialized) {
                    if(configs.getBoolean(getString(R.string.conf_check_for_tf_update), false) || configs.getBoolean(getString(R.string.conf_auto_update_tf),false))
                        StaticDataProvider.getNetworkManager().checkForTFModelUpdate();
                    startRecording();
                }
            }

            // new hand wash event
            if (intent.getStringExtra("trigger").equals("handWash")) {
                if(isRunning)
                    addHandWashEventNow();
                else
                    makeToast(getString(R.string.toast_record_not_active));
            }

            // new hand wash event with timestamp
            if (intent.getStringExtra("trigger").equals("handWashTS")) {
                if(isRunning) {
                    long timestamp = intent.getLongExtra("timestamp", -1);
                    if (timestamp > -1) {
                        try {
                            addHandWashEvent(timestamp);
                            makeToast("Added hand wash");
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
                else
                    makeToast(getString(R.string.toast_record_not_active));
            }

            // open app
            if (intent.getStringExtra("trigger").equals("open")){
                Intent mainIntent = new Intent(this, MainActivity.class);
                startActivity(mainIntent);
            }
        // if there isn't a trigger we just start the service
        } else {
            if (!initialized && intent != null) {
                initialized = true;
                this.intent = intent;
                startForeground(NotificationSpawner.FG_NOTIFICATION_ID, NotificationSpawner.createRecordingPausedNotification(this.getApplicationContext(), this.intent));

                PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);

                wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                        "SensorRecorder::WakelockTag");

                dataProcessor = StaticDataProvider.getProcessor();
                sensorManager = (android.hardware.SensorManager) getSystemService(SENSOR_SERVICE);
                initSensors();
                loadDataContainers();

                configs = this.getSharedPreferences(getString(R.string.configs), Context.MODE_PRIVATE);

                // handWashDetection = MainActivity.mainActivity.handWashDetection;
                try {
                    handWashDetection = new HandWashDetection(this.getApplicationContext());
                } catch (IOException e) {
                    e.printStackTrace();
                }

                registerReceivers();

                // start recording at app startup
                /*
                // get charging state
                IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
                Intent batteryStatus = this.registerReceiver(null, ifilter);
                int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                // if we're not charging, initial start recording
                boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL;
                Log.d("rec", "charge stuts: " + batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1));
                if(!isCharging){
                    Log.d("rec", "Start recording");
                    startRecording();
                }
                */
                startRecording();
            }
        }
        return START_STICKY;
    }

    public void initUIElements(Button startStopButton){
        this.startStopButton = startStopButton;
    }

    private void registerReceivers(){
        batteryEventHandler = new BatteryEventHandler();
        batteryEventHandler.sensorRecordingManager = this;
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        filter.addAction(Intent.ACTION_BATTERY_LOW);
        this.registerReceiver(batteryEventHandler, filter);

        chargeEventHandler = new ChargeEventHandler();
        IntentFilter filter2 = new IntentFilter(Intent.ACTION_POWER_CONNECTED);
        filter2.addAction(Intent.ACTION_POWER_DISCONNECTED);
        this.registerReceiver(chargeEventHandler, filter2);
    }

    /**
     * Initialize the used sensors. These can vary for different devices since they don't support
     * all sensors. So we take the union of available and wanted sensors and save them as active
     * sensors.
     */
    private void initSensors(){
        // get sensors which are supported by the device
        ArrayList<Sensor> availableSensors = new ArrayList<>();
        for(int i = 0; i < possibleSensors.length; i++){
            int possibleSensorType = possibleSensors[i];
            Sensor availableSensor = sensorManager.getDefaultSensor(possibleSensorType);
            if (availableSensor != null){
                availableSensors.add(availableSensor);
            }
        }

        // setup active sensors
        activeSensors = new Sensor[availableSensors.size()];
        sensorDimensions = new int[availableSensors.size()];
        // each sensor gets its own listener service
        sensorServices = new SensorListenerService[availableSensors.size()];

        // initialize active sensors
        for(int i = 0; i < availableSensors.size(); i++){
            Sensor availableSensor = availableSensors.get(i);
            activeSensors[i] = availableSensor;
            sensorDimensions[i] = getNumChannels(availableSensor.getType());
            sensorServices[i] = new SensorListenerService(this, availableSensor, i, sensor_queue_size, sensorStartTime, sensorDelay, sensorDimensions[i], getHandWashActivateThreshold(availableSensor.getType()), useMKVStream, mFFmpeg, useZIPStream, dataProcessor);
        }
    }

    /**
     * Initialize all existing data container we could use
     */
    private void loadDataContainers(){
        try {
            dataProcessor.loadDefaultContainers();
            for(Sensor sensor: activeSensors){
                dataProcessor.addSensorContainer(sensor.getStringType());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    /**
     * Reinitialize sensor listener services with new ones
     * @ Todo: Maybe delete this, because of ugly
     */
    private void resetSensorListeners(){
        for(int i = 0; i < activeSensors.length; i++){
            sensorServices[i] = new SensorListenerService(this, activeSensors[i], i, sensor_queue_size, sensorStartTime, sensorDelay, sensorDimensions[i], getHandWashActivateThreshold(activeSensors[i].getType()), useMKVStream, mFFmpeg, useZIPStream, dataProcessor);
        }
    }

    /**
     * Use ffmpeg package and set up everything needed
     */
    private void setupFFMPEGfromLocal(){
        String platform = Build.BOARD + " " + Build.DEVICE + " " + Build.VERSION.SDK_INT,
                output = getDefaultOutputPath(getApplicationContext()),
                output2 = dataProcessor.containerMKV.recordingFile.getAbsolutePath(),
                android_id = Settings.Secure.getString(
                        getContentResolver(), Settings.Secure.ANDROID_ID),
                format = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN ? "f32le" : "f32be";
        File tmpFile = new File(output);
        Log.i("Sensorrecorder", "Output: " + output + tmpFile.exists());
        Log.i("Sensorrecorder", "Output2: " + output2);
        try {
            FFMpegProcess.Builder b = new FFMpegProcess.Builder(getApplicationContext())
                    .setOutput(output2, "matroska")
                    .setCodec("a", "wavpack")
                    .addOutputArgument("-shortest")
                    .setTag("recorder", "sensorrecorder 1.0")
                    .setTag("android_id", android_id)
                    .setTag("platform", platform)
                    .setTag("fingerprint", Build.FINGERPRINT)
                    .setTag("beginning", getCurrentDateAsIso());

            b.addAudio(format, 50.0, 3)
                    .setStreamTag("name", "Acceleration");
            b.addAudio(format, 50.0, 3)
                    .setStreamTag("name", "Gyroscope");
            mFFmpeg = b.build();


            /**
             * for each sensor there is thread that copies data to the ffmpeg process. For startup
             * synchronization the threads are blocked until the starttime has been set at which
             * point the threadlock will be released.
             */
            /*
            int us = reportRate;

            mStartTimeNS = -1L;
            mSyncLatch = new CountDownLatch(2);

            for(Sensor sensor: activeSensors) {
                sensorManager.registerListener(new SensorEventListener() {
                    @Override
                    public void onSensorChanged(SensorEvent event) {
                        mStartTimeNS = Long.max(event.timestamp, mStartTimeNS);
                        mSyncLatch.countDown();
                        sensorManager.unregisterListener(this);
                    }

                    @Override
                    public void onAccuracyChanged(Sensor sensor, int accuracy) {
                    }
                }, sensor, us);
            }
            */
        } catch (Exception e) {

            e.printStackTrace();
        }
    }

    /**
     * Used by ffmpeg setup
     * @param context
     * @return
     */
    public static String getDefaultOutputPath(Context context) {
        File path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
        return new File(path, getDefaultFileName(context)).toString();
    }

    /**
     * Used by ffmpeg setup
     * @param context
     * @return
     */
    public static String getDefaultFileName(Context context) {
        TimeZone tz = TimeZone.getTimeZone("UTC");
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mmZ");
        df.setTimeZone(tz);
        String aid = Settings.Secure.getString(context.getContentResolver(),
                Settings.Secure.ANDROID_ID);
        return df.format(new Date()) + "_" + aid + ".mkv";
    }

    /**
     * Used by ffmpeg setup
     * @return
     */
    public static String getCurrentDateAsIso() {
        // see https://stackoverflow.com/questions/3914404/how-to-get-current-moment-in-iso-8601-format
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'");
        df.setTimeZone(TimeZone.getTimeZone("UTC"));
        return df.format(new Date());
    }

    /**
     * Register active sensors to sensor manager to enable receive of sensor events.
     * Each sensor service runs in its own thread
     */
    public void registerToManager(){
        // create buffers
        resetSensorListeners();

        // due to the ffmpeg service can't run on the ui thread we have to initialize the sensor recorder which writes to the pipe also in an separate thread
        boolean batchMode = true;
        for(int sensorIndex = 0; sensorIndex < activeSensors.length; sensorIndex++) {
            HandlerThread t_sen = new HandlerThread(activeSensors[sensorIndex].getName());
            t_sen.start();
            Handler h_sen = new Handler(t_sen.getLooper());
            batchMode &= sensorManager.registerListener(sensorServices[sensorIndex], activeSensors[sensorIndex], samplingRate, reportRate, h_sen);
        }
        if (!batchMode){
            Log.e("sensorinfo", "Could not register sensors to batch");
        } else {
            Log.d("sensorinfo", "Registered sensors to batch");
        }
    }

    /**
     * Unregister active sensors from sensor manager
     */
    private void unregisterFromManager(){
        for(SensorListenerService sensorService: sensorServices){
            sensorManager.unregisterListener(sensorService);
        }
    }

    /**
     * Flag each sensor service as stopped und do a last flush.
     * They will report all pending events and ignore new ones from now on.
     */
    private void stopSensors(){
        for(SensorListenerService sensorService: sensorServices){
            sensorService.close();
            sensorManager.flush(sensorService);
        }
    }

    /**
     * Start the active listening to sensor events.
     * We have to set a wakelock to prevent the system goes to doze mode and stops our recording.
     * Also we start a foreground notification to inform the user about the running service.
     * After that we clean every state and start up all managers
     */
    public void startRecording(){
        wakeLock.acquire(5000*60*1000L /*5000 minutes*/);
        // show the foreground notification

        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(NotificationSpawner.FG_NOTIFICATION_ID, NotificationSpawner.createRecordingNotification(getApplicationContext(), this.intent));

        // load config
        useZIPStream = configs.getBoolean(getString(R.string.conf_useZip), true) && ContextCompat.checkSelfPermission(this, WRITE_EXTERNAL_STORAGE) == PERMISSION_GRANTED;
        useMKVStream = configs.getBoolean(getString(R.string.conf_useMKV), false);
        useMic = configs.getBoolean(getString(R.string.conf_useMic), true);
        useMic &= ContextCompat.checkSelfPermission(this, RECORD_AUDIO) == PERMISSION_GRANTED;
        useMultipleMic = configs.getBoolean(getString(R.string.conf_multipleMic), true);

        handWashDetection.initModel();

        // reset all containers before we activate the new ones
        dataProcessor.deactivateAllContainer();
        try {
            activateUsedContainer();
        } catch (IOException e) {
            e.printStackTrace();
        }

        // reset mic counter
        micCounter = 0;
        // if we want to record the microphone and use a single file, we have to initialize the recorder once
        if(useMic && !useMultipleMic) {
            startMediaRecorder();
            mediaRecorder.pause();
        }
        // if we want to record into mkv files we have to setup and start the ffmpeg process
        if (useMKVStream)
            setupFFMPEGfromLocal();

        // initialize all fileoutputstreams
        DataContainer.generateFileNameSuffix(this);
        dataProcessor.openFileStream();

        // setup sensor manager
        sensorStartTime = SystemClock.elapsedRealtimeNanos();
        registerToManager();

        initPrediction();

        // update ui stuff
        isRunning = true;
        if (startStopButton != null)
            setButtonText(getResources().getString(R.string.btn_stop));
    }

    /**
     * Stop the active listening to sensor events.
     * First unregister sensors and do a last flush. Because this happens in a separate thread,
     * we start a new task which waits for the complete close and then proceed to save all data.
     * The stopLatch is used so signalise when stop and save is completed
     * @param stopLatch
     * @param doSave
     */
    private void stopRecording(CountDownLatch stopLatch, boolean doSave){
        // if we*re not recording we can simply  ignore this call
        if(!isRunning) {
            stopLatch.countDown();
            return;
        }
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(NotificationSpawner.FG_NOTIFICATION_ID, NotificationSpawner.createRecordingPausedNotification(getApplicationContext(), this.intent));

        if(startStopButton != null)
            setButtonText(getResources().getString(R.string.btn_stopping));
        Log.d("mgr", "Stop recording");
        // stop sensor manager
        stopSensors();

        Log.d("mgr", "unregistered sensors");
        executor.execute(new StopSessionTask(stopLatch, doSave));
        if(wakeLock.isHeld())
            wakeLock.release();
    }


    /**
     * Call stopRecoding and ignore save task signal
     */
    public void directlyStopRecording(){
        CountDownLatch stopLatch = new CountDownLatch(1);
        stopRecording(stopLatch, true);
    }

    /**
     * Call stopRecording and get signal when all data is saved
     * @param stopLatch
     */
    public void waitForStopRecording(CountDownLatch stopLatch){
        stopRecording(stopLatch, true);
    }

    /**
     * Call stopRecording and get signal when all data is saved
     * @param stopLatch
     */
    public void waitForStopRecording(CountDownLatch stopLatch, boolean doSave){
        stopRecording(stopLatch, doSave);
    }


    /**
     * Depending on current config, set the actually used ones to active
     * @throws IOException
     */
    private void activateUsedContainer() throws IOException {
        if(useZIPStream) {
            dataProcessor.activateSensorContainers();
        }
        dataProcessor.activateDefaultContainer(useMKVStream, useMic);
    }


    /**
     * Setup the media recorder which is used to record the microphone.
     * We use the most minimal quality settings which are possible
     */
    private void initMediaRecorder(){
        File new_recording_mic_file;
        try {
            // String file_name =  recording_file_mic.getName().replaceFirst("[.][^.]+$", "");
            new_recording_mic_file = new File(dataProcessor.containerMic.recordingFilePath, dataProcessor.containerMic.name + "_" + micCounter + ".3gp");
            new_recording_mic_file.createNewFile();
            micCounter++;
            mediaRecorder = new MediaRecorder();
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
            mediaRecorder.setOutputFile(new_recording_mic_file);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            mediaRecorder.setAudioEncodingBitRate(8000);
            mediaRecorder.prepare();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Since our available sensors can differ from the used in the prediction model,
     * we have to union them.
     * With this sensors we set up our prediction
     */
    private void initPrediction(){
        ArrayList<Integer> usedTFSensors = new ArrayList<>();
        for (int i = 0; i < activeSensors.length; i++){
            if(isSensorUsedInTFModel(i)){
                usedTFSensors.add(i);
            }
        }
        int[] sensorTypes = new int[usedTFSensors.size()];
        int[] newSensorDims = new int[usedTFSensors.size()];
        float[] activationThresholds = new float[usedTFSensors.size()];

        for(int i = 0; i < newSensorDims.length; i++){
            sensorTypes[i] = activeSensors[usedTFSensors.get(i)].getType();
            newSensorDims[i] = sensorDimensions[usedTFSensors.get(i)];
            activationThresholds[i] = getHandWashActivateThreshold(activeSensors[usedTFSensors.get(i)].getType());
        }

        handWashDetection.setup(dataProcessor, sensorTypes, newSensorDims, activationThresholds, sensor_queue_size);
    }

    /**
     * Start the microphone recorder
     */
    private void startMediaRecorder(){
        initMediaRecorder();
        mediaRecorder.start();
    }

    /**
     * Pause current microphone recording to save energy.
     * Depending on the config we just pause the media recorder or stop it entirely
     */
    private void pauseMediaRecorder(){
        if(useMultipleMic)
            stopMediaRecorder();
        else
            mediaRecorder.pause();
    }

    /**
     * Resume current microphone recording
     */
    private void resumeMediaRecorder(){
        if(useMultipleMic) {
            initMediaRecorder();
            mediaRecorder.start();
        } else
            mediaRecorder.resume();
    }

    /**
     * Stop the microphone recorder
     */
    private void stopMediaRecorder(){
        mediaRecorderLock.lock();
        try {
            if (mediaRecorder != null) {
                mediaRecorder.stop();
                mediaRecorder.release();
            }
            mediaRecorder = null;
        } finally {
            mediaRecorderLock.unlock();
        }
    }

    /**
     * Flag current recording status and pause recording
     */
    private void stopMicRecording(){
        ongoing_mic_record = false;
        pauseMediaRecorder();
    }


    /**
     * Add new battery status to current data set
     * @param percent
     * @throws IOException
     */
    public void addBatteryState(float percent) throws IOException {
        long timestamp = SystemClock.elapsedRealtimeNanos();
        String line = timestamp + "\t"+ percent + "\n";
        dataProcessor.writeBatteryTS(line);
    }

    /**
     * Add new hand wash event to current data set.
     * Event time stamp is to current time when this method is called
     */
    public void addHandWashEventNow(){
        long timestamp = SystemClock.elapsedRealtimeNanos();
        try {
            addHandWashEvent(timestamp);
            makeToast(getResources().getString(R.string.toast_addedhandwash));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Add new hand wash event to current data set.
     * Event time stamp is to current time when this method is called minus given seconds
     */
    public void addHandWashEventBefore(long past_seconds){
        long timestamp = (SystemClock.elapsedRealtimeNanos() - (past_seconds * 1000 * 1000000));
        try {
            addHandWashEvent(timestamp);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Add hand wash event with given timestamp to current data set.
     */
    private void addHandWashEvent(long time_stamp) throws IOException {
        String lineContent = time_stamp + "\n";
        dataProcessor.writeHandWashTS(lineContent);

        // Update all complications
        ComponentName provider = new ComponentName(this, ComplicationProvider.class);

        int complicationId = StaticDataProvider.getCounterComplicationId();
        ProviderUpdateRequester requester = new ProviderUpdateRequester(this, provider);
        requester.requestUpdate(complicationId);


        // open Evaluation
        Intent confirmHandWashIntent = new Intent(this, EvaluationReceiver.class);
        confirmHandWashIntent.putExtra("trigger", "handWashConfirm");
        confirmHandWashIntent.putExtra("timestamp", time_stamp);
        PendingIntent pintConfirmHandWash = PendingIntent.getBroadcast(this, NotificationSpawner.EVALUATION_REQUEST_CODE, confirmHandWashIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        try {
            pintConfirmHandWash.send();
        } catch (PendingIntent.CanceledException e) {
            e.printStackTrace();
        }
    }

    /**
     * This method is called from sensor services if their buffer is full.
     * We use them to predict hand wash events
     * @param sensorIndex: The sensor which recorded the data
     * @param buffer: Array of sensor values
     * @param timestamps: Array with time stamp for each sensor value
     */
    @Override
    public void flushBuffer(int sensorIndex, float[][] buffer, long[] timestamps) {
        if(isSensorUsedInTFModel(sensorIndex))
            handWashDetection.queueBuffer(sensorIndex, buffer, timestamps);
    }

    /**
     * This method is called from sensor services if there was a certain impact on their sensors.
     * These signalise us to start the microphone recording.
     * After that we queue a task which stops the recording after two seconds.
     * @param timestamp: when the impact happened
     */
    @Override
    public void startMicRecording(long timestamp) {
        mediaRecorderLock.lock();
        try {
            if (useMic && !ongoing_mic_record) {
                //Log.d("mic", "start mic record");
                resumeMediaRecorder();
                //Log.d("mic", "resumed media recorder");
                ongoing_mic_record = true;
                String lineContent = timestamp + "\n";
                try {
                    dataProcessor.writeMicTS(lineContent);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                Handler handler = new Handler();
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        stopMicRecording();
                    }
                }, 2000);
            }
        }finally {
            mediaRecorderLock.unlock();
        }
    }


    /**
     * It seems that android doesn't provide a function to get the actual dimension of a sensor.
     * @param sensorType
     * @return
     */
    public static int getNumChannels(int sensorType) {
        /*
         * https://developer.android.com/reference/android/hardware/SensorEvent#sensor
         */
        switch (sensorType) {
            case Sensor.TYPE_ACCELEROMETER:
            case Sensor.TYPE_GYROSCOPE:
            case Sensor.TYPE_MAGNETIC_FIELD:
                return 3;

            case Sensor.TYPE_ROTATION_VECTOR:
                return 5;

            case Sensor.TYPE_RELATIVE_HUMIDITY:
            case Sensor.TYPE_PRESSURE:
            case Sensor.TYPE_LIGHT:
            case Sensor.TYPE_AMBIENT_TEMPERATURE:
                return 1;

            default:
                return 0;
        }
    }

    /**
     * We just can trigger toast on the UI-thread
     * @param text
     */
    private void makeToast(final String text){
        /*
        context.runOnUiThread(new Runnable() {
            public void run() {
                Toast.makeText(context, text, Toast.LENGTH_LONG).show();
            }
        });
         */
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), text, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setButtonText(final String newText){
        if (startStopButton != null) {
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                public void run() {
                    startStopButton.setText(newText);
                }
            });
        }
    }


    /**
     * Get the required sensor value for a given sensor to trigger a prediction.
     * @param sensorType
     * @return
     */
    public float getHandWashActivateThreshold(int sensorType){
        int sensorIndex = 0;
        for(int i = 0; i < possibleSensors.length; i++){
            if(possibleSensors[i] == sensorType){
                sensorIndex = i;
                break;
            }
        }
        return possibleHandWashActivateThresholds[sensorIndex];
    }

    /**
     * Check if given sensor is used in our prediction
     * @param sensorIndex
     * @return
     */
    private boolean isSensorUsedInTFModel(int sensorIndex){
        int sensorType = activeSensors[sensorIndex].getType();
        for(int i = 0; i < sensorsUsedInTFModel.length; i++){
            if (sensorsUsedInTFModel[i] == sensorType)
                return true;
        }
        return false;
    }


    /**
     * Binder is used to share object instances between activity and service
     * @param intent
     * @return
     */
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }


    public class LocalBinder extends Binder {
        SensorRecordingManager getService() {
            // Return this instance of LocalService so clients can call public methods
            return SensorRecordingManager.this;
        }
    }

    @Override
    public void onDestroy(){
        this.unregisterFromManager();
        unregisterReceiver(batteryEventHandler);
        batteryEventHandler = null;
        unregisterReceiver(chargeEventHandler);
        chargeEventHandler = null;
    }


    /**
     * This task waits until all sensor listeners have stopped.
     * After that it saves all data and close the streams.
     */
    private class StopSessionTask implements Runnable{
        private CountDownLatch stopLatch;
        private boolean doSave;

        public StopSessionTask(CountDownLatch stopLatch, boolean doSave){
            this.stopLatch = stopLatch;
            this.doSave = doSave;
        }

        @Override
        public void run() {
            boolean waitForClose = true;
            while (waitForClose){
                waitForClose = false;
                for(SensorListenerService sensorService: sensorServices){
                    if(!sensorService.closed || !sensorService.flushed)
                        waitForClose = true;
                }
            }
            unregisterFromManager();

            try {
                // we have to close the zip streams correctly to prevent corruptions
                if (useZIPStream) {
                    dataProcessor.closeSensorStreams();
                }

            } catch (IOException e){
                e.printStackTrace();
            }

            // stop ongoing mic recordings and close recorder
            if (mediaRecorder != null)
                stopMediaRecorder();

            Log.d("mgr", "Stopped mediarecorder");

            // close ffmpeg process
            try {
                // mFFmpeg.waitFor();
                if (mFFmpeg != null)
                    mFFmpeg.terminate();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            mFFmpeg = null;

            Log.d("mgr", "closed ffmpeg");

            // there could be a lot of microphone files. That's why we collect them in a single zip
            try {
                dataProcessor.packMicFilesIntoZip();
            } catch (IOException e) {
                e.printStackTrace();
            }

            Log.d("mgr", "packed mic files");

            // write on disk
            dataProcessor.flushAllContainer();

            Log.d("mgr", "flushed data");
            if(doSave) {
                dataProcessor.backup_recording_files();
                Log.d("mgr", "saved data");
            }

            isRunning = false;
            if (startStopButton != null)
                setButtonText("Start");

            Log.d("mgr", "finished stop");
            stopLatch.countDown();
        }
    }
}
