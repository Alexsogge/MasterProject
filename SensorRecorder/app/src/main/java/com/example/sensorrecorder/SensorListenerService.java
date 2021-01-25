package com.example.sensorrecorder;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
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
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import java.io.BufferedOutputStream;
import java.io.File;
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


    public Networking networking;

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

    File recording_file_acc;
    FileOutputStream file_output_acc;
    ZipOutputStream zip_output_acc;
    File recording_file_gyro;
    FileOutputStream file_output_gyro;
    ZipOutputStream zip_output_gyro;
    File recording_file_mkv;
    File recording_file_time_stamps;
    FileOutputStream file_output_time_stamps;
    File recording_file_mic_time_stamps;
    FileOutputStream file_output_mic_time_stamps;
    File recording_file_battery;
    FileOutputStream file_output_battery;
    public File recording_file_mic;
    public static final String CHANNEL_ID = "ForegroundServiceChannel";
    public ArrayList<ArrayList<long[]>> handWashEvents;
    private NotificationCompat.Builder notificationBuilder;
    private PowerManager.WakeLock wakeLock;
    public final File recording_file_path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM + "/android_sensor_recorder/");
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


    private boolean useMKVStream = false;


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

            try {

                if(!recording_file_path.exists())
                {
                    // Make it, if it doesn't exit
                    recording_file_path.mkdirs();
                }
                recording_file_acc = new File(recording_file_path, "sensor_recording_android_acc.zip");
                recording_file_acc.createNewFile();
                recording_file_gyro = new File(recording_file_path, "sensor_recording_android_gyro.zip");
                recording_file_gyro.createNewFile();
                recording_file_mkv = new File(recording_file_path, "sensor_recording_android.mkv");
                recording_file_mkv.createNewFile();
                recording_file_time_stamps = new File(recording_file_path, "sensor_recording_hand_wash_time_stamps.csv");
                recording_file_time_stamps.createNewFile();
                recording_file_mic_time_stamps = new File(recording_file_path, "sensor_recording_mic_time_stamps.csv");
                recording_file_mic_time_stamps.createNewFile();
                recording_file_mic = new File(recording_file_path, "sensor_recording_mic.3gp");
                recording_file_mic.createNewFile();
                recording_file_battery = new File(recording_file_path, "sensor_recording_battery.csv");
                recording_file_battery.createNewFile();


                //recording_file_acc = new File(String.valueOf(this.openFileOutput("sensor_recording_android.csv", Context.MODE_PRIVATE)));
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }

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

    public String addPipedInput() throws IOException, InterruptedException {
        File dir = this.getFilesDir().getParentFile();
        File f = File.createTempFile("ffmpeg", "", dir);

        String pipename = f.getAbsolutePath();


        /** create named pipe */
        f.delete();
        Process p = new ProcessBuilder().command("mknod", f.getAbsolutePath(), "p").start();
        int result = p.waitFor();

        if (result != 0)
            throw new IOException("mknod failed");

        /** open and store for later use */
        f = new File(f.getAbsolutePath());
        f.deleteOnExit();

        return pipename;
    }

    private void setupFFMPEGfromPackage(){
        ffmpegPipe = Config.registerNewFFmpegPipe(this);
        /*
        try {
            ffmpegPipe = addPipedInput();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        */
        Log.d("sensorrecorder", "Created pipe at " + ffmpegPipe);
        // -f, f32le, -ar, 50.0, -ac, 3.0, -i, async:file:/data/user/0/de.uni_freiburg.automotion/ffmpeg3167575985090850597, -f, f32le, -ar, 50.0, -ac, 3.0, -i, async:file:/data/user/0/de.uni_freiburg.automotion/ffmpeg5454277153717897251, -nostdin, -c:a, wavpack, -shortest, -metadata, recorder=automotion 1.21, -metadata, android_id=68a002a0eadad863, -metadata, platform=skipjack skipjack 28, -metadata, fingerprint=mobvoi/skipjack/skipjack:9/PWDS.190618.001.C5/6826145:user/release-keys, -metadata, beginning=2020-12-09T14:20Z, -metadata:s:0, name=LSM6DS3 Accelerometer -Wakeup Secondary, -metadata:s:1, name=LSM6DS3 Gyroscope -Wakeup Secondary, -map, 0, -map, 1, -f, matroska, -y, /storage/emulated/0/DCIM/2020-12-09T14:20+0000_68a002a0eadad863.mkv
        ffmpegCommand = "-f f32le -ar 50.0 -ac 3.0 -i async:file:" + ffmpegPipe + " -nostdin -c:a wavpack -shortest -map 0 -f matroska -y " + recording_file_path+"/recording.mkv";
        Log.i("sensorrecorder", "Start ffmpeeg " + ffmpegCommand);
        FFmpeg.executeAsync(ffmpegCommand, new ExecuteCallback(){
            @Override
            public void apply(final long executionId, final int returnCode) {
                Log.i(Config.TAG, "Async command execution completed.");
            }
        });

        recording_pipe_acc = new File(ffmpegPipe);
        try {
            FileOutputStream fout = new FileOutputStream(ffmpegPipe);
            recording_pipe_acc.delete();
            pipe_output_acc = new BufferedOutputStream(fout);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }


    private void setupFFMPEGfromLocal(){
        String platform = Build.BOARD + " " + Build.DEVICE + " " + Build.VERSION.SDK_INT,
                output = getDefaultOutputPath(getApplicationContext()),
                output2 = recording_file_mkv.getAbsolutePath(),
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
        openFileStream();

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

            zip_output_acc.closeEntry();
            zip_output_acc.close();
            file_output_acc.close();

            zip_output_gyro.closeEntry();
            zip_output_gyro.close();
            file_output_gyro.close();

        } catch (IOException | InterruptedException e){
            e.printStackTrace();
        }


        // wakeLock.release();
    }

    public void startRecording(){
        wakeLock.acquire(5000*60*1000L /*5000 minutes*/);

        startForeground(1, notificationBuilder.build());

        startMediaRecorder();
        mediaRecorder.pause();
        // micTriggerStart();

        // Log.i("sensorrecorder", "Initialized mic recorder " + mediaRecorder.toString());

        if (useMKVStream)
            setupFFMPEGfromLocal();

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

        isRunning = false;
        if (startStopButton != null)
            startStopButton.setText("Start");
        stopForeground(true);
        if(wakeLock.isHeld())
            wakeLock.release();
    }


    private void initMediaRecorder(){
        /*
        try {
            recording_file_mic = new File(recording_file_path, "sensor_recording_mic_" + micCounter + ".3gp");
            recording_file_mic.createNewFile();
            micCounter++;
        } catch (IOException e) {
            e.printStackTrace();
        }
        */
        mediaRecorder = new MediaRecorder();
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        mediaRecorder.setOutputFile(recording_file_mic);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
        mediaRecorder.setAudioEncodingBitRate(8000);
        try {
            mediaRecorder.prepare();
        } catch (IOException e) {
            Log.e("sensorrecorder", "prepare() failed");
        }
    }

    private void startMediaRecorder(){
        initMediaRecorder();
        mediaRecorder.start();
    }

    private void stopMediaRecorder(){
        // mediaRecorder.stop();
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

            if (!ongoing_mic_record){
                checkForMicStart();
            }

            if (ongoing_mic_record && System.currentTimeMillis() > last_mic_record + 2000){
                ongoing_mic_record = false;
                last_mic_record = System.currentTimeMillis();
                mediaRecorder.pause();
                //stopMediaRecorder();
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
            // startMediaRecorder();
            mediaRecorder.resume();
            ongoing_mic_record = true;
            last_mic_record = System.currentTimeMillis();
            String lineContent = recording_timestamps_acc[pointer_acc] + "\n";
            try {
                file_output_mic_time_stamps.write(lineContent.getBytes());
                file_output_mic_time_stamps.flush();
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
        file_output_battery.write(line.getBytes());
        file_output_battery.flush();
    }


    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    private void openFileStream(){
        try {
            file_output_acc = new FileOutputStream(recording_file_acc);
            zip_output_acc = new ZipOutputStream(file_output_acc);
            file_output_gyro = new FileOutputStream(recording_file_gyro);
            zip_output_gyro = new ZipOutputStream(file_output_gyro);
            String fileName = recording_file_acc.getName().replaceFirst("[.][^.]+$", "");
            zip_output_acc.putNextEntry(new ZipEntry(fileName + ".csv"));

            fileName = recording_file_gyro.getName().replaceFirst("[.][^.]+$", "");
            zip_output_gyro.putNextEntry(new ZipEntry(fileName + ".csv"));

            file_output_time_stamps = new FileOutputStream(recording_file_time_stamps);
            file_output_mic_time_stamps = new FileOutputStream(recording_file_mic_time_stamps);
            file_output_battery = new FileOutputStream(recording_file_battery);

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
            data.append(recording_timestamps_acc[i]).append("\t");
            data.append(recording_values_acc[i][0]).append("\t");
            data.append(recording_values_acc[i][1]).append("\t");
            data.append(recording_values_acc[i][2]).append("\n");

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
        if(ContextCompat.checkSelfPermission(this, WRITE_EXTERNAL_STORAGE) == PERMISSION_GRANTED) {
            // Runtime.getRuntime().exec(new String[]{"sh", "-c", "cat <image path> > " + pipe1});
            //file_output_acc.write(data.toString().getBytes());
            //file_output_acc.flush();
            zip_output_acc.write(data.toString().getBytes());
            zip_output_acc.flush();
            // pipe_output_acc.write(data.toString().getBytes());
            // Runtime.getRuntime().exec(new String[]{"sh", "-c", "cat " + recording_file_acc.getPath() + " > " + ffmpegPipe});
            Log.e("write", "Flushed File acc");
        } else {
            Log.e("write", "Can't write file");
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
            data.append(recording_timestamps_gyro[i]).append("\t");
            data.append(recording_values_gyro[i][0]).append("\t");
            data.append(recording_values_gyro[i][1]).append("\t");
            data.append(recording_values_gyro[i][2]).append("\n");

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
        if(ContextCompat.checkSelfPermission(this, WRITE_EXTERNAL_STORAGE) == PERMISSION_GRANTED) {
            zip_output_gyro.write(data.toString().getBytes());
            zip_output_gyro.flush();
            //pipe_output_acc.write(data.toString().getBytes());
            Log.e("write", "Flushed File gyro");
        } else {
            Log.e("write", "Can't write file");
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
        file_output_time_stamps.write(lineContent.getBytes());
        file_output_time_stamps.flush();
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

    public void prepareUpload(){
        flushSensor();
        // unregisterfromManager();
        stopRecording();
        setInfoText("Backup files");
        Log.d("sensorrecorder", "Backup files");
        queue_backup_files();
        backup_recording_files();
    }

    private void queue_backup_files(){
        networking.toBackupFiles.add(recording_file_acc.getName());
        networking.toBackupFiles.add(recording_file_gyro.getName());
        if(useMKVStream)
            networking.toBackupFiles.add(recording_file_mkv.getName());
        networking.toBackupFiles.add(recording_file_time_stamps.getName());
        networking.toBackupFiles.add(recording_file_mic.getName());
        networking.toBackupFiles.add(recording_file_mic_time_stamps.getName());
        networking.toBackupFiles.add(recording_file_battery.getName());
    }

    public void backup_recording_files(){
        new FileBackupTask().execute(recording_file_acc);
        new FileBackupTask().execute(recording_file_gyro);
        if(useMKVStream)
            new FileBackupTask().execute(recording_file_mkv);
        new FileBackupTask().execute(recording_file_time_stamps);
        new FileBackupTask().execute(recording_file_mic);
        new FileBackupTask().execute(recording_file_mic_time_stamps);
        new FileBackupTask().execute(recording_file_battery);
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

    protected final class FileBackupTask extends AsyncTask<File, File, String>{
        String filename;

        public File backup_file(File src) throws IOException {
            String backup_name = src.getName().replaceFirst("[.][^.]+$", "");
            Log.e("sensorrecorder", "Backup file:" + src.getName());
            String extension = src.getName().split("\\.")[1];
            Log.e("sensorrecorder", "Extension: " + extension);
            File dst = null;
            for (int i = 0; i < 99; i++){
                dst = new File(recording_file_path, backup_name + "_" + i + "." + extension);
                if (!dst.exists())
                    break;
            }
            Log.d("sensorrecorder", "Backup " + dst.getName());
            //dst.createNewFile();

            src.renameTo(dst);

            /*
            try (InputStream in = new FileInputStream(src)) {
                try (OutputStream out = new FileOutputStream(dst)) {
                    // Transfer bytes from in to out
                    byte[] buf = new byte[1024];
                    int len;
                    while ((len = in.read(buf)) > 0) {
                        out.write(buf, 0, len);
                    }
                }
            }
            */
            return dst;
        }

        @Override
        protected String doInBackground(File... files) {
            try {
                filename = files[0].getName();
                backup_file(files[0]);
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(String result) {
            networking.finishedFileBackup(filename);
        }
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
