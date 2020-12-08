package com.example.sensorrecorder;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;




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
    long[] recording_timestamps_acc = new long[1000];
    float[][] recording_values_acc = new float[1000][3];
    long[] recording_timestamps_gyro = new long[1000];
    float[][] recording_values_gyro = new float[1000][3];
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
    FileOutputStream pipe_output_acc;



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

            /*
            ffmpegPipe = Config.registerNewFFmpegPipe(this);
            Log.d("sensorrecorder", "Created pipe at " + ffmpegPipe);
            ffmpegCommand = "-y -i " + ffmpegPipe + " -filter:v loop=loop=25*3:size=1 -c:v mpeg4 -r 50 " + recording_file_acc.getPath()+"/recording.mp4";
            FFmpeg.executeAsync(ffmpegCommand, new ExecuteCallback(){
                @Override
                public void apply(final long executionId, final int returnCode) {
                    Log.i(Config.TAG, "Async command execution completed.");
                }
            });
            */
            /*
            recording_pipe_acc = new File(ffmpegPipe);
            try {
                pipe_output_acc = new FileOutputStream(ffmpegPipe);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            */
            startRecording();

        } else {
            if (intent.getStringExtra("trigger") != null){
                if (intent.getStringExtra("trigger").equals("testCall"))
                    TestCall();
                if (intent.getStringExtra("trigger").equals("handWash"))
                    AddHandWashEvent();
                if (intent.getStringExtra("trigger").equals("open")){
                    Intent mainIntent = new Intent(this, MainActivity.class);
                    startActivity(mainIntent);
                }
            }
        }
        return START_STICKY;
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
        recording_timestamps_acc = new long[1000];
        recording_values_acc = new float[1000][3];
        recording_timestamps_gyro = new long[1000];
        recording_values_gyro = new float[1000][3];
        pointer_acc = 0;
        pointer_gyro = 0;

        handWashEvents.add(new ArrayList<long[]>());
        OpenFileStream();

        boolean batchMode = sensorManager.registerListener(this, acceleration_sensor, samplingRate, reportRate);
        batchMode = batchMode && sensorManager.registerListener(this, gyro_sensor, samplingRate, reportRate);
        if (!batchMode){
            Log.e("sensorinfo", "Could not register sensors to batch");
        } else {
            Log.e("sensorinfo", "Registered sensors to batch");
        }
        isRunning = true;
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

        } catch (IOException e){
            e.printStackTrace();
        }

        isRunning = false;
        // wakeLock.release();
    }

    public void startRecording(){
        wakeLock.acquire(5000*60*1000L /*5000 minutes*/);

        startForeground(1, notificationBuilder.build());
        registerToManager();
    }

    public void stopRecording(){
        unregisterfromManager();
        stopForeground(true);
        wakeLock.release();
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

        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            // Log.d("sensor", "[" + event.timestamp + "]" + event.values[0] + " " + event.values[1] + " " + event.values[2]);
            recording_timestamps_acc[pointer_acc] = event.timestamp;
            recording_values_acc[pointer_acc][0] = event.values[0];
            recording_values_acc[pointer_acc][1] = event.values[1];
            recording_values_acc[pointer_acc][2] = event.values[2];
            if (event.timestamp == lastDoubleCheckTimeStamp){
                doubleTimeStamps++;
            }
            lastDoubleCheckTimeStamp = event.timestamp;
            pointer_acc++;
            // Log.d("sensor", ""+pointer_acc);
            if(pointer_acc == 1000) {
                try {
                    SendSensorDataAcc();
                } catch (IOException e) {
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
            if(pointer_gyro == 1000) {
                try {
                    SendSensorDataGyro();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                recording_timestamps_gyro[0] = recording_timestamps_gyro[pointer_gyro-1];
                pointer_gyro = 1;
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    private void OpenFileStream(){
        try {
            file_output_acc = new FileOutputStream(recording_file_acc);
            zip_output_acc = new ZipOutputStream(file_output_acc);
            file_output_gyro = new FileOutputStream(recording_file_gyro);
            zip_output_gyro = new ZipOutputStream(file_output_gyro);
            String fileName = recording_file_acc.getName().replaceFirst("[.][^.]+$", "");
            zip_output_acc.putNextEntry(new ZipEntry(fileName + ".csv"));

            fileName = recording_file_gyro.getName().replaceFirst("[.][^.]+$", "");
            zip_output_gyro.putNextEntry(new ZipEntry(fileName + ".csv"));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void SendSensorDataAcc() throws IOException {
        Log.e("write", "Write File");
        StringBuilder data = new StringBuilder();
        for(int i = 1; i < pointer_acc; i++){
            // Log.i("sensor", "RecordingService: " + timeStamp);
            data.append(recording_timestamps_acc[i]).append("\t");
            data.append(recording_values_acc[i][0]).append("\t");
            data.append(recording_values_acc[i][1]).append("\t");
            data.append(recording_values_acc[i][2]).append("\n");
        }
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

    private void SendSensorDataGyro() throws IOException {
        Log.e("write", "Write File");
        StringBuilder data = new StringBuilder();
        for(int i = 1; i < pointer_gyro; i++){
            // Log.i("sensor", "RecordingService: " + timeStamp);
            data.append(recording_timestamps_gyro[i]).append("\t");
            data.append(recording_values_gyro[i][0]).append("\t");
            data.append(recording_values_gyro[i][1]).append("\t");
            data.append(recording_values_gyro[i][2]).append("\n");
        }
        if(ContextCompat.checkSelfPermission(this, WRITE_EXTERNAL_STORAGE) == PERMISSION_GRANTED) {
            zip_output_gyro.write(data.toString().getBytes());
            zip_output_gyro.flush();
            Log.e("write", "Flushed File gyro");
        } else {
            Log.e("write", "Can't write file");
        }
    }

    private void AddHandWashEvent(){
        Log.d("fgservice", "New handwash at " + (pointer_acc - 1) + "   " + (pointer_gyro - 1));
        flushSensor();
        if(pointer_acc == 0 || pointer_gyro == 0)
            return;
        long[] newEvent = {recording_timestamps_acc[pointer_acc - 1], recording_timestamps_gyro[pointer_gyro - 1]};
        handWashEvents.get(handWashEvents.size()-1).add(newEvent);
        Log.d("fgservice", "New handwash at " + newEvent[0] + "   " + newEvent[1]);

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
        unregisterfromManager();
        setInfoText("Backup files");
        Log.d("sensorrecorder", "Backup files");
        backup_recording_files();
    }

    public File[] backup_recording_files(){
        File[] files = new File[2];
        networking.toBackupFiles.add(recording_file_acc.getName());
        networking.toBackupFiles.add(recording_file_gyro.getName());
        new FileBackupTask().execute(recording_file_acc);
        new FileBackupTask().execute(recording_file_gyro);

        return files;
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

    protected final class FileBackupTask extends AsyncTask<File, File, String>{
        String filename;

        public File backup_file(File src) throws IOException {
            String backup_name = src.getName().replaceFirst("[.][^.]+$", "");
            File dst = null;
            for (int i = 0; i < 99; i++){
                dst = new File(recording_file_path, backup_name + "_" + i + ".zip");
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
}
