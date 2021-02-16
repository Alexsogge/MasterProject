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
import android.hardware.SensorEventListener2;
import android.hardware.SensorManager;
import android.media.MediaRecorder;
import android.os.AsyncTask;
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
import android.util.Pair;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.CountDownLatch;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import de.uni_freiburg.ffmpeg.FFMpegProcess;




import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;

import com.arthenica.mobileffmpeg.Config;
import com.arthenica.mobileffmpeg.ExecuteCallback;
import com.arthenica.mobileffmpeg.FFmpeg;


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
    int sensor_queue_size = 1000;
    long[] recording_timestamps_acc = new long[sensor_queue_size];
    float[][] recording_values_acc = new float[sensor_queue_size][5];
    long[] recording_timestamps_gyro = new long[sensor_queue_size];
    float[][] recording_values_gyro = new float[sensor_queue_size][3];
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
    boolean isRunning = true;


    public static final String CHANNEL_ID = "ForegroundServiceChannel";
    public ArrayList<ArrayList<long[]>> handWashEvents;
    private NotificationCompat.Builder notificationBuilder;
    private PowerManager.WakeLock wakeLock;

    public TextView infoText;
    private Handler mainLoopHandler;
    private long lastDoubleCheckTimeStamp = 0;
    public int doubleTimeStamps = 0;

    private String ffmpegPipe;
    private String ffmpegCommand;
    File recording_pipe_acc;
    OutputStream pipe_output_acc;
    OutputStream pipe_output_gyro;
    ByteBuffer mBuf = ByteBuffer.allocate(4 * 3);

    private FFMpegProcess mFFmpeg;

    private Long mStartTimeNS = -1l;
    private CountDownLatch mSyncLatch = null;
    private MediaRecorder mediaRecorder;

    private long last_mic_record;
    private boolean ongoing_mic_record = false;
    private float mic_activate_threshold = 8;
    private Handler micHandler;
    private int micCounter = 0;

    public Button startStopButton;

    private SharedPreferences configs;
    private boolean useMKVStream = false;
    private boolean useZIPStream = true;
    private boolean useMic = true;
    private boolean useMultipleMic = true;

    public ArrayList<DataContainer> allDataContainers;
    private ArrayList<OutputStreamContainer> streamContainers;

    private ZipContainer containerSensorAcc;
    private ZipContainer containerSensorGyro;
    private DataContainer containerMKV;
    private OutputStreamContainer containerHandWashTimeStamps;
    private DataContainer containerMic;
    private OutputStreamContainer containerMicTimeStamps;
    private OutputStreamContainer containerBattery;


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d("sensorinfo", "Start service" + flags + " | " + startId + initialized);
        Log.d("sensorinfo", "Params: " + intent.getStringExtra("trigger"));



        if (!initialized) {
            initialized = true;
            this.intent = intent;

            createForeGroundNotification();
            // startForeground(1, notificationBuilder.build());

            sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
            acceleration_sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            gyro_sensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);

            handWashEvents = new ArrayList<>();
            mainLoopHandler = new Handler(Looper.getMainLooper());
            PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);

            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                    "SensorRecorder::WakelockTag");

            LoadDataContainers();

            Log.i("sensorinfo", String.valueOf(acceleration_sensor.getFifoMaxEventCount()));
            Log.i("sensorinfo", String.valueOf(acceleration_sensor.getFifoReservedEventCount()));
            Log.i("sensorinfo", String.valueOf(acceleration_sensor.getMaxDelay()));
            Log.i("sensorinfo", String.valueOf(acceleration_sensor.isWakeUpSensor()));
            //Log.e("sensorinfo", "Max delay: " + acceleration_sensor.getMaxDelay() + " - Fifo count" + acceleration_sensor.getFifoReservedEventCount());



            // setupFFMPEGfromPackage();
//            if (useMKVStream)
//                setupFFMPEGfromLocal();

            /*
            List<String> supportedCameraIds = Config.getSupportedCameraIds(this);
            Log.d("sensorrecorder", "Supported cameras" + supportedCameraIds.toString());


            ffmpegCommand = "-f alsa -i hw:0 -t 30 " + recording_file_path.getPath() + "/out.wav";
            ffmpegCommand = "-f avfoundation -list_devices true -i \"\"";
            ffmpegCommand = "-f pulse -i default -t 30 " + recording_file_path.getPath() + "/out.wav";
            ffmpegCommand = "-devices true -f dshow -i dummy";
            // ffmpegCommand = "-y -f android_camera -i 0:0 -r 30 -t 00:00:05 " + recording_file_path.getPath() + "/out.wav";
            Log.i("sensorrecorder", "Start ffmpeeg " + ffmpegCommand);
//            FFmpeg.executeAsync(ffmpegCommand, new ExecuteCallback(){
//                @Override
//                public void apply(final long executionId, final int returnCode) {
//                    Log.i(Config.TAG, "Async command execution completed.");
//                }
//            });

            int rc = FFmpeg.execute(ffmpegCommand);


            Log.i(Config.TAG, String.format("Command execution failed with rc=%d and the output below.", rc));
            Config.printLastCommandOutput(Log.INFO);
            */

            configs = this.getSharedPreferences(getString(R.string.configs), Context.MODE_PRIVATE);

            micHandler = new Handler();

            startRecording();

        } else {
            if (intent.getStringExtra("trigger") != null){
                if (intent.getStringExtra("trigger").equals("testCall"))
                    TestCall();
                if (intent.getStringExtra("trigger").equals("handWash")) {
                    addHandWashEventNow();
                }
                if (intent.getStringExtra("trigger").equals("open")){
                    Intent mainIntent = new Intent(this, MainActivity.class);
                    startActivity(mainIntent);
                }
            }
        }
        return START_STICKY;
    }

    private void LoadDataContainers(){
        allDataContainers = new ArrayList<DataContainer>();
        streamContainers = new ArrayList<OutputStreamContainer>();
        try {
            containerSensorAcc = new ZipContainer("sensor_recording_android_acc", "csv");
            streamContainers.add(containerSensorAcc);
            containerSensorGyro = new ZipContainer("sensor_recording_android_gyro", "csv");
            streamContainers.add(containerSensorGyro);

            containerMKV = new DataContainer("sensor_recording_android", "mkv");
            allDataContainers.add(containerMKV);

            containerHandWashTimeStamps = new OutputStreamContainer("sensor_recording_hand_wash_time_stamps", "csv");
            streamContainers.add(containerHandWashTimeStamps);

            containerMicTimeStamps = new OutputStreamContainer("sensor_recording_mic_time_stamps", "csv");
            streamContainers.add(containerMicTimeStamps);

            containerMic = new ZipContainer("sensor_recording_mic", "zip");
            allDataContainers.add(containerMic);

            containerBattery = new OutputStreamContainer("sensor_recording_battery", "csv");
            streamContainers.add(containerBattery);

            //recording_file_acc = new File(String.valueOf(this.openFileOutput("sensor_recording_android.csv", Context.MODE_PRIVATE)));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        allDataContainers.addAll(streamContainers);
    }

    private void setupFFMPEGfromLocal(){
        String platform = Build.BOARD + " " + Build.DEVICE + " " + Build.VERSION.SDK_INT,
                output = getDefaultOutputPath(getApplicationContext()),
                output2 = containerMKV.recordingFile.getAbsolutePath(),
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


            sensorManager.registerListener(new SensorEventListener() {
                @Override
                public void onSensorChanged(SensorEvent event) {
                    mStartTimeNS = Long.max(event.timestamp, mStartTimeNS);
                    mSyncLatch.countDown();
                    sensorManager.unregisterListener(this);
                }

                @Override
                public void onAccuracyChanged(Sensor sensor, int accuracy) {}
            }, acceleration_sensor, us);

            sensorManager.registerListener(new SensorEventListener() {
                @Override
                public void onSensorChanged(SensorEvent event) {
                    mStartTimeNS = Long.max(event.timestamp, mStartTimeNS);
                    mSyncLatch.countDown();
                    sensorManager.unregisterListener(this);
                }

                @Override
                public void onAccuracyChanged(Sensor sensor, int accuracy) {}
            }, gyro_sensor, us);

//            pipe_output_acc = mFFmpeg.getOutputStream(0);
//            pipe_output_gyro = mFFmpeg.getOutputStream(1);

        } catch (Exception e) {

            e.printStackTrace();
        }
//        try {
//            Log.i("sensorrecorder", "Num Streams " + mFFmpeg.mStreams.size());
//            pipe_output_acc = mFFmpeg.getOutputStream(0);
//            Log.i("sensorrecorder", "Num Streams " + mFFmpeg.mStreams.size());
//            pipe_output_gyro = mFFmpeg.getOutputStream(1);
//            Log.i("sensorrecorder", "Num Streams " + mFFmpeg.mStreams.size());
//        } catch (FileNotFoundException e){
//            Log.e("Sensorrecorder", "Stream error");
//            e.printStackTrace();
//        }
        pipe_output_acc = null;
        pipe_output_gyro = null;
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

    private void TestCall(){
        Log.d("fgservice", "Test call");
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

    public void registerToManager(){
        recording_timestamps_acc = new long[sensor_queue_size];
        recording_values_acc = new float[sensor_queue_size][5];
        recording_timestamps_gyro = new long[sensor_queue_size];
        recording_values_gyro = new float[sensor_queue_size][3];
        pointer_acc = 0;
        pointer_gyro = 0;

        handWashEvents.add(new ArrayList<long[]>());


        HandlerThread t_acc = new HandlerThread(acceleration_sensor.getName());
        t_acc.start();
        Handler h_acc = new Handler(t_acc.getLooper());
        CopyListener acc_listener = new CopyListener(0, 50., acceleration_sensor.getName());

        HandlerThread t_gyro = new HandlerThread(gyro_sensor.getName());
        t_gyro.start();
        Handler h_gyro = new Handler(t_gyro.getLooper());
        CopyListener gyro_listener = new CopyListener(1, 50., gyro_sensor.getName());
        boolean batchMode = sensorManager.registerListener(this, gyro_sensor, samplingRate, reportRate, h_gyro);
        batchMode = batchMode && sensorManager.registerListener(this, acceleration_sensor, samplingRate, reportRate, h_acc);
        if (!batchMode){
            Log.e("sensorinfo", "Could not register sensors to batch");
        } else {
            Log.e("sensorinfo", "Registered sensors to batch");
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

    private void unregisterfromManager(){
        Log.i("sensorinfo", "unregister listener");
        sensorManager.unregisterListener(this);
        try {
            SendSensorDataAcc();
            SendSensorDataGyro();

            if (useZIPStream) {
                containerSensorAcc.Close();
                containerSensorGyro.Close();
            }

        } catch (IOException | InterruptedException e){
            e.printStackTrace();
        }


        // wakeLock.release();
    }

    public void startRecording(){
        wakeLock.acquire(5000*60*1000L /*5000 minutes*/);

        startForeground(1, notificationBuilder.build());


        useZIPStream = configs.getBoolean(getString(R.string.conf_useZip), true);
        useMKVStream = configs.getBoolean(getString(R.string.conf_useMKV), false);
        useMic= configs.getBoolean(getString(R.string.conf_useMic), true);
        useMultipleMic = configs.getBoolean(getString(R.string.conf_multipleMic), true);
        DeactivateAllContainer();
        try {
            ActivateUsedContainer();
        } catch (IOException e) {
            e.printStackTrace();
        }

        micCounter = 0;
        if(useMic && !useMultipleMic) {
            startMediaRecorder();
            mediaRecorder.pause();
        }
        // micTriggerStart();

        // Log.i("sensorrecorder", "Initialized mic recorder " + mediaRecorder.toString());

        if (useMKVStream)
            setupFFMPEGfromLocal();

        openFileStream();
        registerToManager();
        isRunning = true;
        if (startStopButton != null)
            startStopButton.setText("Stop");

        // mediaRecorder.start();

    }

    public void stopRecording(){
        unregisterfromManager();
        //Config.closeFFmpegPipe(ffmpegPipe);
        //FFmpeg.cancel();

        if (mediaRecorder != null)
            stopMediaRecorder();

        try {
            // mFFmpeg.waitFor();
            if (mFFmpeg != null)
                mFFmpeg.terminate();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        mFFmpeg = null;

        try {
            packMicFilesIntoZip();
        } catch (IOException e) {
            e.printStackTrace();
        }

        FlushAllContainer();

        isRunning = false;
        if (startStopButton != null)
            startStopButton.setText("Start");
        stopForeground(true);
        if(wakeLock.isHeld())
            wakeLock.release();
    }


    private void ActivateUsedContainer() throws IOException {
        if(useZIPStream) {
            containerSensorAcc.SetActive();
            containerSensorGyro.SetActive();
        }
        if(useMKVStream)
            containerMKV.SetActive();

        if(useMic) {
            containerMic.SetActive();
            containerMicTimeStamps.SetActive();
        }

        containerHandWashTimeStamps.SetActive();
        containerBattery.SetActive();
    }

    private void DeactivateAllContainer(){
        for(DataContainer container: allDataContainers){
            container.Deactivate();
        }
    }

    private void FlushAllContainer(){
        for(OutputStreamContainer container: streamContainers){
            try {
                container.Flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void initMediaRecorder(){
        File new_recording_mic_file;
        try {
            // String file_name =  recording_file_mic.getName().replaceFirst("[.][^.]+$", "");
            new_recording_mic_file = new File(DataContainer.recordingFilePath, containerMic.name + "_" + micCounter + ".3gp");
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

        float max_axis = 0f;

        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            // Log.d("sensor", "[" + event.timestamp + "]" + event.values[0] + " " + event.values[1] + " " + event.values[2]);
            recording_timestamps_acc[pointer_acc] = event.timestamp;

            recording_values_acc[pointer_acc][0] = event.values[0];
            recording_values_acc[pointer_acc][1] = event.values[1];
            recording_values_acc[pointer_acc][2] = event.values[2];

            // recording_values_acc[pointer_acc][3] = Math.min(event.values[0], Math.min(event.values[1], event.values[2]));
            // recording_values_acc[pointer_acc][4] = Math.max(event.values[0], Math.max(event.values[1], event.values[2]));

            if (useMic && !ongoing_mic_record){
                checkForMicStart();
            }

            if (ongoing_mic_record && System.currentTimeMillis() > last_mic_record + 2000){
                ongoing_mic_record = false;
                last_mic_record = System.currentTimeMillis();
               pauseMediaRecorder();
            }

            pointer_acc++;
            // Log.d("sensor", ""+pointer_acc);
            if(pointer_acc == sensor_queue_size) {
                try {
                    SendSensorDataAcc();
                } catch (IOException | InterruptedException e) {
                    e.printStackTrace();
                }
                recording_timestamps_acc[0] = recording_timestamps_acc[pointer_acc-1];
                pointer_acc = 1;
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
            if(pointer_gyro == sensor_queue_size) {
                try {
                    SendSensorDataGyro();
                } catch (IOException | InterruptedException e) {
                    e.printStackTrace();
                }
                recording_timestamps_gyro[0] = recording_timestamps_gyro[pointer_gyro-1];
                pointer_gyro = 1;
            }
        }

        /*
        if (max_axis > mic_activate_threshold && !ongoing_mic_record && System.currentTimeMillis() > last_mic_record + 10000){
            ongoing_mic_record = true;
            last_mic_record = System.currentTimeMillis();
            mediaRecorder.resume();
            String lineContent = event.timestamp + "\n";
            try {
                file_output_mic_time_stamps.write(lineContent.getBytes());
                file_output_mic_time_stamps.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
            Log.i("sensorrecorder", "start mic");
        }

         */
    }


    private void checkForMicStart(){
        boolean activateMic = possibleHandWash();

        // Log.i("sensorrecorder", "Test: " + (max_val - min_val));
        if (activateMic){
            resumeMediaRecorder();
            // mediaRecorder.resume();
            ongoing_mic_record = true;
            last_mic_record = System.currentTimeMillis();
            String lineContent = recording_timestamps_acc[pointer_acc] + "\n";
            try {
                containerMicTimeStamps.WriteData(lineContent);
                // containerMic.Flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
            Log.i("sensorrecorder", "start mic");
        }
    }

    private boolean possibleHandWash(){
        int offset = Math.max(1, pointer_acc-25);
        for (int axes = 0; axes < 3; axes++){
            if (recording_values_acc[pointer_acc][axes] > mic_activate_threshold){
                for (int i = offset; i <= pointer_acc; i++){
                    if (recording_values_acc[i][axes] < -mic_activate_threshold) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public void addBatteryState(float percent) throws IOException {
        long timestamp = SystemClock.elapsedRealtimeNanos();
        String line = timestamp + "\t"+ percent + "\n";
        containerBattery.WriteData(line);
        // containerBattery.Flush();
    }


    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    private void openFileStream(){
        try {
            for (OutputStreamContainer container: streamContainers) {
                container.OpenStream();
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void SendSensorDataAcc() throws IOException, InterruptedException {
        Log.e("write", "Write File");
        StringBuilder data = new StringBuilder();
        long lastTimeStamp = recording_timestamps_acc[1];
        long offset = 0;
        // mSyncLatch.await();
        if (pipe_output_acc == null && useMKVStream)
            pipe_output_acc = mFFmpeg.getOutputStream(0);

        for(int i = 1; i < pointer_acc; i++) {
            // Log.i("sensor", "RecordingService: " + timeStamp);

            if(useZIPStream) {
                data.append(recording_timestamps_acc[i]).append("\t");
                data.append(recording_values_acc[i][0]).append("\t");
                data.append(recording_values_acc[i][1]).append("\t");
                data.append(recording_values_acc[i][2]).append("\n");
            }
            if (useMKVStream) {
                offset += (recording_timestamps_acc[i] - lastTimeStamp);
                lastTimeStamp = recording_timestamps_acc[i];

                if (offset >= samplingRate) {
                    offset -= samplingRate;
                    mBuf.clear();
                    for (int j = 0; j < 3; j++)
                        mBuf.putFloat(recording_values_acc[i][j]);
                    pipe_output_acc.write(mBuf.array());
                }
            }
        }
        if(useMKVStream)
            pipe_output_acc.flush();
        // Log.i("sensorrecorder", "current time:" + recording_timestamps_acc[pointer_acc-1]);
        if(useZIPStream && ContextCompat.checkSelfPermission(this, WRITE_EXTERNAL_STORAGE) == PERMISSION_GRANTED) {
            // Runtime.getRuntime().exec(new String[]{"sh", "-c", "cat <image path> > " + pipe1});
            //file_output_acc.write(data.toString().getBytes());
            //file_output_acc.flush();
            containerSensorAcc.WriteData(data.toString());
            // containerSensorAcc.Flush();
            // pipe_output_acc.write(data.toString().getBytes());
            // Runtime.getRuntime().exec(new String[]{"sh", "-c", "cat " + recording_file_acc.getPath() + " > " + ffmpegPipe});
            Log.e("write", "Flushed File acc");
        }
    }

    private void SendSensorDataGyro() throws IOException, InterruptedException {
        Log.e("write", "Write File");
        StringBuilder data = new StringBuilder();
        long lastTimeStamp = recording_timestamps_gyro[1];
        long offset = 0;
        // mSyncLatch.await();
        if (pipe_output_gyro == null && useMKVStream)
            pipe_output_gyro = mFFmpeg.getOutputStream(1);

        for(int i = 1; i < pointer_gyro; i++){
            // Log.i("sensor", "RecordingService: " + timeStamp);
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
            containerSensorGyro.WriteData(data.toString());
            // containerSensorGyro.Flush();
            //pipe_output_acc.write(data.toString().getBytes());
            Log.e("write", "Flushed File gyro");
        }
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
        // Log.i("sensorrecorder", "Add handwash:" + time_stamp);
        String lineContent = time_stamp + "\n";
        containerHandWashTimeStamps.WriteData(lineContent);
        // containerHandWashTimeStamps.Flush();
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

    private void packMicFilesIntoZip() throws IOException {
        FileOutputStream fileOut = new FileOutputStream(containerMic.recordingFile);
        ZipOutputStream zipOut = new ZipOutputStream(fileOut);

        for (int i = 0; i < 9999; i++) {
            File tmp_file = new File(DataContainer.recordingFilePath, containerMic.name + "_" + i + ".3gp");
            if (!tmp_file.exists())
                continue;
            FileInputStream fis = new FileInputStream(tmp_file);
            ZipEntry zipEntry = new ZipEntry(tmp_file.getName());
            zipOut.putNextEntry(zipEntry);

            byte[] bytes = new byte[1024];
            int length;
            while((length = fis.read(bytes)) >= 0) {
                zipOut.write(bytes, 0, length);
            }
            fis.close();
            tmp_file.delete();
        }
        zipOut.close();
        fileOut.close();
    }


    public void prepareUpload(){
        flushSensor();
        // unregisterfromManager();
        stopRecording();
        setInfoText("Backup files");
        Log.d("sensorrecorder", "Backup files");
        backup_recording_files();
    }


    public void backup_recording_files(){

       for(DataContainer container: allDataContainers){
           container.BackupFile();
       }
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

    private void makeToast(final String text){
        mainLoopHandler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getBaseContext(), text, Toast.LENGTH_LONG).show();
            }
        });
    }




    private void micTriggerStart(){
        mediaRecorder.resume();
        ongoing_mic_record = true;
        last_mic_record = System.currentTimeMillis();

        Log.i("sensorrecorder", "started mic");
        micHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                micTriggerStop();
            }
        }, 2000); //the time you want to delay in milliseconds
    }

    private void micTriggerStop(){
        ongoing_mic_record = false;
        last_mic_record = System.currentTimeMillis();
        mediaRecorder.pause();

        Log.i("sensorrecorder", "stopped mic");

        micHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                micTriggerStart();
            }
        }, 5000); //the time you want to delay in milliseconds
    }

    private class CopyListener implements SensorEventListener, SensorEventListener2 {
        private final int index;
        private final long mDelayUS;
        private long mSampleCount;
        private long mOffsetUS;
        private final String mName;

        private OutputStream mOut;
        private ByteBuffer mBuf;
        private long mLastTimestamp = -1;
        private boolean mFlushCompleted = false;

        /**
         * @param i
         * @param rate
         * @param name
         */
        public CopyListener(int i, double rate, String name) {
            index = i;
            mOut = null;
            mName = name;
            mDelayUS = (long) (1e6 / rate);
            mSampleCount = 0;
            mOffsetUS = 0;
            Log.e("sensorrecorder", "new Sensor copy listener");
        }

        @Override
        public void onSensorChanged(SensorEvent sensorEvent) {
            // Log.e("sensorrecorder", "Sensor copy listener changed");
            try {
                /*
                 * wait until the mStartTimeNS is cleared. This will be done by the SyncLockListener
                 */
                // Log.e("sensorrecorder", "wait for latch");
                // mSyncLatch.await();
                // Log.e("sensorrecorder", "finished wait for latch");

                /*
                 * if a flush was completed, the sensor process is done, and the recording
                 * will be stopped. Hence the output channel is closed to let ffmpeg know,
                 * that the recording is finished. This will then lead to an IOException,
                 * which cleanly exits the whole process.
                 */
                if (mFlushCompleted)
                    mOut.close();

                /**
                 *  multiple stream synchronization, wait until a global timestamp was set,
                 *  and only start pushing events after this timestamp.
                 */
                if (sensorEvent.timestamp < mStartTimeNS)
                    return;

                if (mLastTimestamp != -1)
                    mOffsetUS += (sensorEvent.timestamp - mLastTimestamp) / 1000;
                mLastTimestamp = sensorEvent.timestamp;


                /*
                 * create an output buffer, once created only delete the last sample. Insert
                 * values afterwards.
                 */
                if (mBuf == null) {
                    mBuf = ByteBuffer.allocate(4 * sensorEvent.values.length);
                    mBuf.order(ByteOrder.nativeOrder());
                    Log.e("bgrec", String.format("%s started at %d", mName, sensorEvent.timestamp));
                } else
                    mBuf.clear();

                /**
                 * see https://stackoverflow.com/questions/30279065/how-to-get-the-euler-angles-from-the-rotation-vector-sensor-type-rotation-vecto
                 * https://developer.android.com/reference/android/hardware/SensorEvent#sensor
                 */

                for (float v : sensorEvent.values)
                    mBuf.putFloat(v);

                /**
                 * check whether or not interpolation is required
                 */
                if (Math.abs(mOffsetUS) - mDelayUS > mDelayUS)
                    Log.e("bgrec", String.format(
                            "sample delay too large %.4f %s", mOffsetUS / 1e6, mName));

                if (mOut == null)
                    //Log.i("sensorrecorder", "get new pipe " + index);
                    mOut = mFFmpeg.getOutputStream(index);

                if (mOffsetUS < mDelayUS)      // too fast -> remove
                    return;
                // Log.i("sensorrecorder", "write to pipe " + index);
                while (mOffsetUS > mDelayUS) { // add new samples, might be too slow
                    mOut.write(mBuf.array());
                    mOffsetUS -= mDelayUS;
                    mSampleCount++;
                }
            } catch (Exception e) {
                e.printStackTrace();
                SensorManager sm = (SensorManager) getSystemService(SENSOR_SERVICE);
                sm.unregisterListener(this);
                Log.e("bgrec", String.format("%d samples written %s", mSampleCount, mName));
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int i) {
        }

        @Override
        public void onFlushCompleted(Sensor sensor) {
            mFlushCompleted = true;
        }
    }


}
