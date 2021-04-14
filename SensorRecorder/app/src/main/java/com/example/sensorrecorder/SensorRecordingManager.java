package com.example.sensorrecorder;

import android.app.Activity;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.media.MediaRecorder;
import android.os.BatteryManager;
import android.os.Binder;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.Vibrator;
import android.provider.Settings;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;


/*
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dimensionalityreduction.PCA;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.ops.transforms.Transforms;
*/

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
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
    public Activity mainActivity;
    public Button startStopButton;
    public static boolean isRunning = true;
    public TextView infoText;
    private Vibrator vibrator;

    // service stuff
    public boolean stopping;
    private Intent intent;
    private Handler mainLoopHandler;
    private boolean initialized = false;
    private NotificationCompat.Builder notificationBuilder;
    private PowerManager.WakeLock wakeLock;


    // sensor recording stuff
    private android.hardware.SensorManager sensorManager;
    private Sensor[] activeSensors;
    private SensorListenerService[] sensorServices;
    private int[] sensorDimensions;
    private long sensorStartTime;


    // data files
    public static DataProcessor dataProcessor;


    // ffmpeg stuff
    private FFMpegProcess mFFmpeg;
    private OutputStream[] sensorPipeOutputs;

    private ByteBuffer mBuf = ByteBuffer.allocate(4 * 3);
    private Long mStartTimeNS = -1l;
    private CountDownLatch mSyncLatch = null;

    // Microphone stuff
    private MediaRecorder mediaRecorder;
    private long last_mic_record;
    private boolean ongoing_mic_record = false;
    private float mic_activate_threshold_acc = 8;
    private float mic_activate_threshold_gyro = 8;
    private Handler micHandler;
    private int micCounter = 0;
    private ReentrantLock mediaRecorderLock = new ReentrantLock();


    // config stuff
    private SharedPreferences configs;
    private boolean useMKVStream = false;
    private boolean useZIPStream = true;
    private boolean useMic = true;
    private boolean useMultipleMic = true;


    // ML stuff
    public HandWashDetection handWashDetection;
    private long lastHandwashPrediction;
    private long lastPossibleHandWash;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // due to we call this function multiple times from the notification buttons we have to determine when we called it the first time
        // if a call wasn't the initial one it came from a notification button
        // therefore we have to determine which action should be triggered
        if (intent.getStringExtra("trigger") != null){
            // new hand wash event
            if (intent.getStringExtra("trigger").equals("handWash")) {
                if(isRunning)
                    addHandWashEventNow();
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
            }

            // open app
            if (intent.getStringExtra("trigger").equals("open")){
                Intent mainIntent = new Intent(this, MainActivity.class);
                startActivity(mainIntent);
            }
        } else {
            if (!initialized) {
                initialized = true;
                this.intent = intent;
                sensorManager = (android.hardware.SensorManager) getSystemService(SENSOR_SERVICE);

                initSensors();
                // acceleration_sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
                // gyro_sensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);

                mainLoopHandler = new Handler(Looper.getMainLooper());
                PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);

                wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                        "SensorRecorder::WakelockTag");

                dataProcessor = new DataProcessor();
                loadDataContainers();

                configs = this.getSharedPreferences(getString(R.string.configs), Context.MODE_PRIVATE);
                micHandler = new Handler();
                vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

                handWashDetection = MainActivity.mainActivity.handWashDetection;

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

    private void loadDataContainers(){
        // initialize all existing data container we could use

        try {
            dataProcessor.loadDefaultContainers();
            for(Sensor sensor: activeSensors){
                dataProcessor.addSensorContainer(sensor.getStringType());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private void initSensors(){
        ArrayList<Sensor> availableSensors = new ArrayList<>();
        for(int i = 0; i < possibleSensors.length; i++){
            int possibleSensorType = possibleSensors[i];
            Sensor availableSensor = sensorManager.getDefaultSensor(possibleSensorType);
            if (availableSensor != null){
                availableSensors.add(availableSensor);
            }
        }

        activeSensors = new Sensor[availableSensors.size()];
        sensorDimensions = new int[availableSensors.size()];
        sensorPipeOutputs = new OutputStream[activeSensors.length];
        sensorServices = new SensorListenerService[availableSensors.size()];

        for(int i = 0; i < availableSensors.size(); i++){
            Sensor availableSensor = availableSensors.get(i);
            activeSensors[i] = availableSensor;
            sensorDimensions[i] = getNumChannels(availableSensor.getType());
            sensorServices[i] = new SensorListenerService(this, availableSensor, i, sensor_queue_size, sensorStartTime, sensorDelay, sensorDimensions[i], getHandWashActivateThreshold(availableSensor.getType()), useMKVStream, mFFmpeg, useZIPStream, dataProcessor);
        }
    }

    private void resetSensorBuffers(){
        for(int i = 0; i < activeSensors.length; i++){
            sensorServices[i] = new SensorListenerService(this, activeSensors[i], i, sensor_queue_size, sensorStartTime, sensorDelay, sensorDimensions[i], getHandWashActivateThreshold(activeSensors[i].getType()), useMKVStream, mFFmpeg, useZIPStream, dataProcessor);
        }
    }


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

        sensorPipeOutputs = new OutputStream[activeSensors.length];
        mBuf.order(ByteOrder.nativeOrder());
    }


    public static String getDefaultOutputPath(Context context) {
        File path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
        return new File(path, getDefaultFileName(context)).toString();
    }

    public static String getDefaultFileName(Context context) {
        TimeZone tz = TimeZone.getTimeZone("UTC");
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mmZ");
        df.setTimeZone(tz);
        String aid = Settings.Secure.getString(context.getContentResolver(),
                Settings.Secure.ANDROID_ID);
        return df.format(new Date()) + "_" + aid + ".mkv";
    }

    public static String getCurrentDateAsIso() {
        // see https://stackoverflow.com/questions/3914404/how-to-get-current-moment-in-iso-8601-format
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'");
        df.setTimeZone(TimeZone.getTimeZone("UTC"));
        return df.format(new Date());
    }

    public void registerToManager(){
        // create buffers
        resetSensorBuffers();

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

    private void unregisterFromManager(){
        for(SensorListenerService sensorService: sensorServices){
            sensorManager.unregisterListener(sensorService);

        }
    }

    private void stopSensors(){
        for(SensorListenerService sensorService: sensorServices){
            sensorService.close();
            sensorManager.flush(sensorService);
        }
    }

    public void startRecording(){
        // we have to set a wakelock to prevent the system goes to doze mode and stops our recording
        wakeLock.acquire(5000*60*1000L /*5000 minutes*/);
        // show the foreground notification
        startForeground(1, NotificationSpawner.createRecordingNotification(this, this.intent));

        // load config
        useZIPStream = configs.getBoolean(getString(R.string.conf_useZip), true) && ContextCompat.checkSelfPermission(this, WRITE_EXTERNAL_STORAGE) == PERMISSION_GRANTED;
        useMKVStream = configs.getBoolean(getString(R.string.conf_useMKV), false);
        useMic = configs.getBoolean(getString(R.string.conf_useMic), true);
        useMic &= ContextCompat.checkSelfPermission(this, RECORD_AUDIO) == PERMISSION_GRANTED;
        useMultipleMic = configs.getBoolean(getString(R.string.conf_multipleMic), true);

        // reset all containers before we activate the new ones
        dataProcessor.deactivateAllContainer();
        try {
            ActivateUsedContainer();
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
        dataProcessor.openFileStream();

        // setup sensor manager
        sensorStartTime = SystemClock.elapsedRealtimeNanos();
        registerToManager();

       initPrediction();

        // update ui stuff
        isRunning = true;
        if (startStopButton != null)
            startStopButton.setText(getResources().getString(R.string.btn_stop));
    }

    private void stopRecording(CountDownLatch stopLatch){
        if(!isRunning) {
            stopLatch.countDown();
            return;
        }

        startStopButton.setText(getResources().getString(R.string.btn_stopping));
        Log.d("mgr", "Stop recording");
        // stop sensor manager
        //unregisterFromManager();
        stopSensors();

        Log.d("mgr", "unregistered sensors");
        executor.execute(new StopSessionTask(stopLatch));
        stopForeground(true);
        if(wakeLock.isHeld())
            wakeLock.release();
    }

    public void directlyStopRecording(){
        CountDownLatch stopLatch = new CountDownLatch(1);
        stopRecording(stopLatch);
    }

    public void waitForStopRecording(CountDownLatch stopLatch){
        stopRecording(stopLatch);
    }


    private void ActivateUsedContainer() throws IOException {
        // depending on current config, set the actually used ones to active
        if(useZIPStream) {
            dataProcessor.activateSensorContainers();
        }
        dataProcessor.activateDefaultContainer(useMKVStream, useMic);
    }


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

    private void startMediaRecorder(){
        initMediaRecorder();
        mediaRecorder.start();
    }

    private void resumeMediaRecorder(){
        if(useMultipleMic) {
            initMediaRecorder();
            mediaRecorder.start();
        } else
            mediaRecorder.resume();
    }

    private void pauseMediaRecorder(){
        if(useMultipleMic)
            stopMediaRecorder();
        else
            mediaRecorder.pause();
    }

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

    public void flushSensor(){
        for(SensorListenerService sensorService: sensorServices){
            sensorManager.flush(sensorService);
        }
        // sensorManager.flush(this);
    }

    private void setInfoText(final String text){
        mainLoopHandler.post(new Runnable() {
            @Override
            public void run() {
                infoText.setText(text);
                infoText.invalidate();
            }
        });
    }

    public void addBatteryState(float percent) throws IOException {
        long timestamp = SystemClock.elapsedRealtimeNanos();
        String line = timestamp + "\t"+ percent + "\n";
        dataProcessor.writeBatteryTS(line);
    }


    public void addHandWashEventNow(){
        long timestamp = SystemClock.elapsedRealtimeNanos();
        try {
            addHandWashEvent(timestamp);
            makeToast(getResources().getString(R.string.toast_addedhandwash));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public void addHandWashEventBefore(long past_seconds){
        long timestamp = (SystemClock.elapsedRealtimeNanos() - (past_seconds * 1000 * 1000000));
        try {
            addHandWashEvent(timestamp);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void addHandWashEvent(long time_stamp) throws IOException {
        String lineContent = time_stamp + "\n";
        dataProcessor.writeHandWashTS(lineContent);
    }



    @Override
    public void flushBuffer(int sensorIndex, float[][] buffer, long[] timestamps) {
        if(isSensorUsedInTFModel(sensorIndex))
            handWashDetection.queueBuffer(sensorIndex, buffer, timestamps);
    }

    @Override
    public void startMicRecording(long timestamp) {
        mediaRecorderLock.lock();
        try {
            if (useMic && !ongoing_mic_record) {
                //Log.d("mic", "start mic record");
                resumeMediaRecorder();
                //Log.d("mic", "resumed media recorder");
                ongoing_mic_record = true;
                last_mic_record = System.currentTimeMillis();
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


    private void stopMicRecording(){
        ongoing_mic_record = false;
        last_mic_record = System.currentTimeMillis();
        pauseMediaRecorder();
    }


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

    private void makeToast(final String text){
        mainActivity.runOnUiThread(new Runnable() {
            public void run() {
                Toast.makeText(mainActivity, text, Toast.LENGTH_SHORT).show();
            }
        });
    }

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

    private boolean isSensorUsedInTFModel(int sensorIndex){
        int sensorType = activeSensors[sensorIndex].getType();
        for(int i = 0; i < sensorsUsedInTFModel.length; i++){
            if (sensorsUsedInTFModel[i] == sensorType)
                return true;
        }
        return false;
    }


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
    }


    private class StopSessionTask implements Runnable{
        private CountDownLatch stopLatch;

        public StopSessionTask(CountDownLatch stopLatch){
            this.stopLatch = stopLatch;
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
            dataProcessor.backup_recording_files();
            Log.d("mgr", "saved data");

            isRunning = false;
            if (startStopButton != null)
                startStopButton.setText("Start");

            Log.d("mgr", "finished stop");
            stopLatch.countDown();
        }
    }
}
