package com.example.sensorrecorder;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
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
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.TimeZone;
import java.util.concurrent.CountDownLatch;

import de.uni_freiburg.ffmpeg.FFMpegProcess;


import static android.Manifest.permission.RECORD_AUDIO;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;


public class SensorListenerService extends Service implements SensorEventListener{
    private final IBinder binder = new LocalBinder();
    private final int samplingRate = 20000;
    private final int reportRate = 1000000;
    private final int sensor_queue_size = 1000;
    private static final String CHANNEL_ID = "ForegroundServiceChannel";
    private final int[] possibleSensors = new int[]{Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_GYROSCOPE, Sensor.TYPE_MAGNETIC_FIELD, Sensor.TYPE_ROTATION_VECTOR};

    // handle main ui
    public Button startStopButton;
    public boolean isRunning = true;
    public TextView infoText;

    // service stuff
    private Intent intent;
    private Handler mainLoopHandler;
    private boolean initialized = false;
    private NotificationCompat.Builder notificationBuilder;
    private PowerManager.WakeLock wakeLock;


    // sensor recording stuff
    private SensorManager sensorManager;
    private Sensor[] activeSensors;
    private HashMap<Integer, Integer> sensorMapping;
    private int[] sensorDimensions;
    private long[][] sensorTimestampsBuffer;
    private float[][][] sensorValuesBuffer;
    private int[] sensorPointers;

    // data files
    public DataProcessor dataProcessor;


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
    private float mic_activate_threshold = 8;
    private Handler micHandler;
    private int micCounter = 0;


    // config stuff
    private SharedPreferences configs;
    private boolean useMKVStream = false;
    private boolean useZIPStream = true;
    private boolean useMic = true;
    private boolean useMultipleMic = true;



    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // due to we call this function multiple times from the notification buttons we have to determine when we called it the first time
        if (!initialized) {
            initialized = true;
            this.intent = intent;

            createForeGroundNotification();

            sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);

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
            sensorTimestampsBuffer = new long[availableSensors.size()][];
            sensorValuesBuffer = new float[availableSensors.size()][][];
            sensorPointers = new int[availableSensors.size()];
            sensorMapping = new HashMap<>();
            sensorPipeOutputs = new OutputStream[activeSensors.length];

            for(int i = 0; i < availableSensors.size(); i++){
                Sensor availableSensor = availableSensors.get(i);
                activeSensors[i] = availableSensor;
                sensorDimensions[i] = getNumChannels(availableSensor);
                sensorTimestampsBuffer[i] = new long[sensor_queue_size];
                sensorValuesBuffer[i] = new float[sensor_queue_size][getNumChannels(availableSensor)];
                sensorPointers[i] = 0;
                sensorMapping.put(availableSensor.getType(), i);
            }
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

            // start recording at app startup
            startRecording();

        } else {
            // if a call wasn't the initial one it came from a notification button
            // therefore we have to determine which action should be triggered
            if (intent.getStringExtra("trigger") != null){

                // new hand wash event
                if (intent.getStringExtra("trigger").equals("handWash")) {
                    addHandWashEventNow();
                }

                // open app
                if (intent.getStringExtra("trigger").equals("open")){
                    Intent mainIntent = new Intent(this, MainActivity.class);
                    startActivity(mainIntent);
                }
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

    private void createForeGroundNotification(){
        createNotificationChannel();
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this,
                0, notificationIntent, 0);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Foreground Service")
                .setContentText("SensorRecorder")
                .setContentIntent(pendingIntent)
                .build();

        Intent handwashIntent = new Intent(this.intent);
        handwashIntent.putExtra("trigger", "handWash");
        PendingIntent pintHandWash = PendingIntent.getService(this, 579, handwashIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        // Intent openIntent = new Intent(intent);
        Intent openIntent = new Intent(getApplicationContext(), MainActivity.class);

        openIntent.putExtra("trigger", "open");
        PendingIntent pintOpen = PendingIntent.getActivity(getApplicationContext(), 579, openIntent, PendingIntent.FLAG_UPDATE_CURRENT);


        notificationBuilder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Running")
                .setContentText("Sensor recorder is active")
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText("Sensor recorder is active"))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setSmallIcon(R.drawable.preference_wrapped_icon)
                .addAction(R.drawable.action_item_background, "HandWash", pintHandWash)
                // .addAction(R.drawable.action_item_background, "Open", pintOpen);
                .setContentIntent(pintOpen);
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

    public void registerToManager(){
        // create buffers
        for(int i = 0; i < activeSensors.length; i++){
            sensorTimestampsBuffer[i] = new long[sensor_queue_size];
            sensorValuesBuffer[i] = new float[sensor_queue_size][sensorDimensions[i]];
            sensorPointers[i] = 0;
        }

        // due to the ffmpeg service can't run on the ui thread we have to initialize the sensor recorder which writes to the pipe also in an separate thread
        boolean batchMode = true;
        for(Sensor sensor: activeSensors) {
            HandlerThread t_sen = new HandlerThread(sensor.getName());
            t_sen.start();
            Handler h_sen = new Handler(t_sen.getLooper());
            batchMode &= sensorManager.registerListener(this, sensor, samplingRate, reportRate, h_sen);
        }
        if (!batchMode){
            Log.e("sensorinfo", "Could not register sensors to batch");
        } else {
            Log.e("sensorinfo", "Registered sensors to batch");
        }
    }

    private void unregisterFromManager(){
        sensorManager.unregisterListener(this);
        try {
            for(int i = 0; i < activeSensors.length; i++){
                WriteSensorData(i);
            }

            // we have to close the zip streams correctly to prevent corruptions
            if (useZIPStream) {
                dataProcessor.closeSensorStreams();
            }

        } catch (IOException e){
            e.printStackTrace();
        }
    }

    public void startRecording(){
        // we have to set a wakelock to prevent the system goes to doze mode and stops our recording
        wakeLock.acquire(5000*60*1000L /*5000 minutes*/);
        // show the foreground notification
        startForeground(1, notificationBuilder.build());

        // load config
        useZIPStream = configs.getBoolean(getString(R.string.conf_useZip), true);
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
        registerToManager();

        // update ui stuff
        isRunning = true;
        if (startStopButton != null)
            startStopButton.setText("Stop");
    }

    public void stopRecording(){
        // stop sensor manager
        unregisterFromManager();

        // stop ongoing mic recordings and close recorder
        if (mediaRecorder != null)
            stopMediaRecorder();

        // close ffmpeg process
        try {
            // mFFmpeg.waitFor();
            if (mFFmpeg != null)
                mFFmpeg.terminate();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        mFFmpeg = null;

        // there could be a lot of microphone files. That's why we collect them in a single zip
        try {
            dataProcessor.packMicFilesIntoZip();
        } catch (IOException e) {
            e.printStackTrace();
        }

        // write on disk
        dataProcessor.flushAllContainer();

        isRunning = false;
        if (startStopButton != null)
            startStopButton.setText("Start");
        stopForeground(true);
        if(wakeLock.isHeld())
            wakeLock.release();
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
        mediaRecorder.stop();
        mediaRecorder.release();
        mediaRecorder = null;
    }

    public void flushSensor(){
        sensorManager.flush(this);
    }

    public void prepareUpload(){
        flushSensor();
        // unregisterfromManager();
        stopRecording();
        setInfoText("Backup files");
        dataProcessor.backup_recording_files();
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
    public void onSensorChanged(SensorEvent event) {
        // write new values to buffer
        int sensorIndex = sensorMapping.get(event.sensor.getType());
        int sensorPointer = sensorPointers[sensorIndex];
        sensorTimestampsBuffer[sensorIndex][sensorPointer] = event.timestamp;
        for(int i = 0; i < event.values.length; i++){
            sensorValuesBuffer[sensorIndex][sensorPointer][i] = event.values[i];
        }


        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            // check if we have to start the microphone
            if (useMic && !ongoing_mic_record){
                checkForMicStart();
            }

            // if we currently record the microphone, check  if we have to stop it
            if (ongoing_mic_record && System.currentTimeMillis() > last_mic_record + 2000){
                ongoing_mic_record = false;
                last_mic_record = System.currentTimeMillis();
               pauseMediaRecorder();
            }


        }

        sensorPointers[sensorIndex]++;
        // check if our buffers are full and we have to write to disk
        if(sensorPointers[sensorIndex] == sensor_queue_size) {
            try {
                WriteSensorData(sensorIndex);
            } catch (IOException e) {
                e.printStackTrace();
            }
            // reset buffer
            sensorTimestampsBuffer[sensorIndex][0] = sensorTimestampsBuffer[sensorIndex][sensorPointers[sensorIndex]-1];
            sensorPointers[sensorIndex] = 1;
        }
    }

    private void checkForMicStart(){
        boolean activateMic = possibleHandWash();
        if (activateMic){
            resumeMediaRecorder();
            ongoing_mic_record = true;
            last_mic_record = System.currentTimeMillis();
            int accIndex = sensorMapping.get(Sensor.TYPE_ACCELEROMETER);
            String lineContent = sensorTimestampsBuffer[accIndex][sensorPointers[accIndex]] + "\n";
            try {
                dataProcessor.writeMicTS(lineContent);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private boolean possibleHandWash(){
        // simple approach to determine if the user is currently washing their hands
        // check if at on of the acceleration axes has been a certain impact
        int accIndex = sensorMapping.get(Sensor.TYPE_ACCELEROMETER);
        int pointerAcc = sensorPointers[accIndex];
        int offset = Math.max(1, pointerAcc-25);
        for (int axes = 0; axes < 3; axes++){
            if (sensorValuesBuffer[accIndex][pointerAcc][axes] > mic_activate_threshold){
                for (int i = offset; i <= pointerAcc; i++){
                    if (sensorValuesBuffer[accIndex][i][axes] < -mic_activate_threshold) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void WriteSensorData(int sensorIndex) throws IOException {
        StringBuilder data = new StringBuilder();
        long lastTimeStamp = sensorTimestampsBuffer[sensorIndex][1];
        long offset = 0;
        if (sensorPipeOutputs[sensorIndex] == null && useMKVStream)
            sensorPipeOutputs[sensorIndex] = mFFmpeg.getOutputStream(0);

        for(int i = 1; i < sensorPointers[sensorIndex]; i++) {
            if(useZIPStream) {
                data.append(sensorTimestampsBuffer[sensorIndex][i]).append("\t");
                data.append(sensorValuesBuffer[sensorIndex][i][0]).append("\t");
                data.append(sensorValuesBuffer[sensorIndex][i][1]).append("\t");
                data.append(sensorValuesBuffer[sensorIndex][i][2]).append("\n");
            }
            if (useMKVStream) {
                offset += (sensorTimestampsBuffer[sensorIndex][i] - lastTimeStamp);
                lastTimeStamp = sensorTimestampsBuffer[sensorIndex][i];

                if (offset >= samplingRate) {
                    offset -= samplingRate;
                    mBuf.clear();
                    for (int j = 0; j < 3; j++)
                        mBuf.putFloat(sensorValuesBuffer[sensorIndex][i][j]);
                    sensorPipeOutputs[sensorIndex].write(mBuf.array());
                }
            }
        }
        if(useMKVStream)
            sensorPipeOutputs[sensorIndex].flush();

        if(useZIPStream && ContextCompat.checkSelfPermission(this, WRITE_EXTERNAL_STORAGE) == PERMISSION_GRANTED) {
            dataProcessor.writeSensorData(activeSensors[sensorIndex].getStringType(), data.toString());
        }
    }

    /*
    private void WriteSensorDataGyro() throws IOException, InterruptedException {
        StringBuilder data = new StringBuilder();
        long lastTimeStamp = recording_timestamps_gyro[1];
        long offset = 0;
        if (pipe_output_gyro == null && useMKVStream)
            pipe_output_gyro = mFFmpeg.getOutputStream(1);

        for(int i = 1; i < pointer_gyro; i++){
            if(useZIPStream) {
                data.append(recording_timestamps_gyro[i]).append("\t");
                data.append(recording_values_gyro[i][0]).append("\t");
                data.append(recording_values_gyro[i][1]).append("\t");
                data.append(recording_values_gyro[i][2]).append("\n");
            }
            if(useMKVStream) {
                offset += (recording_timestamps_gyro[i] - lastTimeStamp);
                lastTimeStamp = recording_timestamps_gyro[i];

                if (offset >= samplingRate) {
                    offset -= samplingRate;
                    mBuf.clear();
                    for (float v : recording_values_gyro[i])
                        mBuf.putFloat(v);
                    pipe_output_gyro.write(mBuf.array());
                }
            }
        }
        if(useMKVStream)
            pipe_output_gyro.flush();
        if(useZIPStream && ContextCompat.checkSelfPermission(this, WRITE_EXTERNAL_STORAGE) == PERMISSION_GRANTED) {
            dataProcessor.writeSensorData(Sensor.STRING_TYPE_GYROSCOPE, data.toString());
        }
    }
    */


    private int getNumChannels(Sensor s) {
        /*
         * https://developer.android.com/reference/android/hardware/SensorEvent#sensor
         */
        switch (s.getType()) {
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

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

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
        this.unregisterFromManager();
    }
}
