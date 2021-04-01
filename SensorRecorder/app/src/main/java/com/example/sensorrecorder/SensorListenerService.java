package com.example.sensorrecorder;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorEventListener2;
import android.util.Log;

import androidx.core.content.ContextCompat;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import de.uni_freiburg.ffmpeg.FFMpegProcess;

import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;

public class SensorListenerService implements SensorEventListener, SensorEventListener2 {
    private final Executor executor = Executors.newSingleThreadExecutor();
    public boolean flushed = false;
    public boolean closed = false;
    private  boolean stopped = false;

    private SensorManagerInterface managerInterface;
    private Sensor mySensor;
    private int sensorIndex;
    private long sensorStartTime;
    private int sensorPointer;
    private long sensorLastTimeStamp;
    private long sensorOffset;
    private long sensorDelay;
    private int sensorDimension;
    private float handWashActivationThreshold;

    private long[] sensorTimestampsBuffer;
    private float[][] sensorValuesBuffer;


    private boolean useMKVStream;
    private OutputStream sensorPipeOutput;
    private FFMpegProcess mFFmpeg;
    private ByteBuffer mBuf;

    private boolean useZIPStream;
    private DataProcessor dataProcessor;


    public SensorListenerService(SensorManagerInterface managerInterface, Sensor sensor,
                                 int sensorIndex, int bufferSize, long sensorStartTime,
                                 long sensorDelay, int sensorDimension,
                                 float handWashActivationThreshold, boolean useMKVStream,
                                 FFMpegProcess ffMpegProcess, boolean useZIPStream,
                                 DataProcessor dataProcessor){
        this.managerInterface = managerInterface;
        this.mySensor = sensor;
        this.sensorIndex = sensorIndex;
        this.sensorStartTime = sensorStartTime;
        this.sensorDelay = sensorDelay;
        this.sensorDimension = sensorDimension;
        this.handWashActivationThreshold = handWashActivationThreshold;
        this.useMKVStream = useMKVStream;
        this.mFFmpeg = ffMpegProcess;
        this.useZIPStream = useZIPStream;
        this.dataProcessor = dataProcessor;
        sensorTimestampsBuffer = new long[bufferSize];
        sensorValuesBuffer = new float[bufferSize][sensorDimension];
        mBuf = ByteBuffer.allocate(4 * sensorDimension);
        mBuf.order(ByteOrder.nativeOrder());
        sensorLastTimeStamp = -1;
        Log.d("sensor", "New listener " + sensor.getStringType());
    }

    public void close(){
        stopped = true;
        /*
        try {
            writeSensorData();
        } catch (IOException e) {
            e.printStackTrace();
        }
        */
        //callFlushBuffer();

    }

    private void callFlushBuffer(){
        executor.execute(new FlushBufferTask());
    }

    private void callStartMic(long timeStamp){
        managerInterface.startMicRecording(timeStamp);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        // reject old sensor events
        if(event.timestamp < sensorStartTime || stopped)
            return;

        if (flushed && sensorPipeOutput != null) {
            try {
                sensorPipeOutput.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // write new values to buffer
        // determine offset between previous and current event
        if (sensorLastTimeStamp != -1)
            sensorOffset += event.timestamp - sensorLastTimeStamp;
        sensorLastTimeStamp = event.timestamp;

        // reject event if there have been too many events than specified by rate
        if(sensorOffset < sensorDelay)
            return;

        // write event as often as required to fill the rate
        // Log.d("sensor", "pointer: " + sensorPointer);
        while (sensorOffset > sensorDelay) {
            sensorTimestampsBuffer[sensorPointer] = event.timestamp - sensorOffset;
            for (int i = 0; i < event.values.length; i++) {
                sensorValuesBuffer[sensorPointer][i] = event.values[i];
            }
            sensorPointer++;
            // check if our buffers are full and we have to write to disk
            if(sensorPointer == sensorTimestampsBuffer.length) {
                // callFlushBuffer();
                // reset buffer

                callFlushBuffer();

                /*
                try {
                    callFlushBuffer();
                    writeSensorData();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                */


                sensorTimestampsBuffer[0] = sensorTimestampsBuffer[sensorPointer-1];
                sensorPointer = 1;
            }
            sensorOffset -= sensorDelay;
        }

        /*
        sensorTimestampsBuffer[sensorIndex][sensorPointer] = event.timestamp;
        for (int i = 0; i < event.values.length; i++) {
            sensorValuesBuffer[sensorIndex][sensorPointer][i] = event.values[i];
        }
        sensorPointers[sensorIndex]++;

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
        */

        // rough estimate off possible hand wash using accelerometer values
        if (handWashActivationThreshold > -1 && sensorPointer%25 == 0 && sensorPointer > 30) {
            long posTs = possibleHandWash();
            if(posTs > -1){
                // vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.EFFECT_TICK));
                // check if we have to start the microphone
                callStartMic(posTs);
            }
        }
    }

    private long possibleHandWash() {
        // simple approach to determine if the user is currently washing their hands
        // check if one of the acceleration or gyroscope axes has been a certain impact

        /*int offset = Math.max(1, sensorPointer - 25);
        for (int axes = 0; axes < sensorDimension; axes++) {
            if (sensorValuesBuffer[sensorPointer][axes] > handWashActivationThreshold) {
                for (int i = offset; i <= sensorPointer; i++) {
                    if (sensorValuesBuffer[i][axes] < -handWashActivationThreshold) {
                        Log.d("sen", "hwacc: " + sensorValuesBuffer[sensorPointer][axes]);
                        return sensorTimestampsBuffer[sensorPointer];
                    }
                }
            }
        }*/
        long timeStamp = sensorTimestampsBuffer[sensorPointer-1];
        int offset = Math.max(1, sensorPointer - 25);
        for (int axes = 0; axes < sensorDimension; axes++) {
            float min = Float.MAX_VALUE;
            float max = Float.MIN_VALUE;

            for (int i = offset; i <= sensorPointer; i++) {
                if(sensorValuesBuffer[i][axes] < min)
                    min = sensorValuesBuffer[i][axes];
                if(sensorValuesBuffer[i][axes] > max) {
                    max = sensorValuesBuffer[i][axes];
                }
            }
            float diff = Math.abs(max-min);
            if (diff > handWashActivationThreshold) {
                Log.d("pred", mySensor.getStringType() + " thr: " + diff);
                return timeStamp;
            }
        }
        return -1;
    }



    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    @Override
    public void onFlushCompleted(Sensor sensor) {
        if(stopped) {
            flushed = true;
            callFlushBuffer();
        }

    }


    class FlushBufferTask implements Runnable{
        float[][] bufferValues;
        long[] timestamps;
        int flushedSensorPointer;

        public FlushBufferTask(){
            flushedSensorPointer = sensorPointer;
            bufferValues = new float[sensorValuesBuffer.length][sensorValuesBuffer[0].length];
            for(int i = 0; i < bufferValues.length; i++){
                System.arraycopy(sensorValuesBuffer[i], 0, bufferValues[i], 0, bufferValues[0].length);
            }
            timestamps = new long[sensorTimestampsBuffer.length];
            System.arraycopy(sensorTimestampsBuffer, 0, timestamps, 0, timestamps.length);
        }

        @Override
        public void run() {
            try {
                writeSensorData();
            } catch (IOException e) {
                e.printStackTrace();
            }
            managerInterface.flushBuffer(sensorIndex, bufferValues, timestamps);
            if(stopped)
                closed = true;
        }

        private void writeSensorData() throws IOException {
            Log.d("sensor", "Write data " + closed);
            StringBuilder data = new StringBuilder();
            if (useMKVStream && sensorPipeOutput == null)
                sensorPipeOutput = mFFmpeg.getOutputStream(sensorIndex);

            for (int i = 1; i < flushedSensorPointer; i++) {
                if(useZIPStream) {
                    data.append(timestamps[i]).append("\t");
                    for(int axe = 0; axe < sensorDimension - 1; axe++){
                        data.append(bufferValues[i][axe]).append("\t");
                    }
                    data.append(bufferValues[i][sensorDimension-1]).append("\n");
                }
                if (useMKVStream) {
                    mBuf.clear();
                    for(int axis = 0; axis < sensorDimension; axis++)
                        mBuf.putFloat(bufferValues[i][axis]);
                    sensorPipeOutput.write(mBuf.array());
                }
            }
            if (useMKVStream)
                sensorPipeOutput.flush();

            if (useZIPStream) {
                dataProcessor.writeSensorData(mySensor.getStringType(), data.toString());
            }
        }

    }

}
