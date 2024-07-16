package unifr.sensorrecorder;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorEventListener2;
import android.util.Log;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import unifr.sensorrecorder.DataContainer.DataProcessor;

public class SensorListenerService implements SensorEventListener, SensorEventListener2 {
    private final Executor executor = Executors.newCachedThreadPool();
    public boolean flushed = false;
    public volatile boolean closed = false;
    private volatile boolean stopped = false;

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

    private ByteBuffer mBuf;

    private boolean useZIPStream;
    private DataProcessor dataProcessor;


    public SensorListenerService(SensorManagerInterface managerInterface, Sensor sensor,
                                 int sensorIndex, int bufferSize, long sensorStartTime,
                                 long sensorDelay, int sensorDimension,
                                 float handWashActivationThreshold,
                                 boolean useZIPStream,
                                 DataProcessor dataProcessor){
        this.managerInterface = managerInterface;
        this.mySensor = sensor;
        this.sensorIndex = sensorIndex;
        this.sensorStartTime = sensorStartTime;
        this.sensorDelay = sensorDelay;
        this.sensorDimension = sensorDimension;
        this.handWashActivationThreshold = handWashActivationThreshold;
        this.useZIPStream = useZIPStream;
        this.dataProcessor = dataProcessor;
        sensorTimestampsBuffer = new long[bufferSize];
        sensorValuesBuffer = new float[bufferSize][sensorDimension];
        mBuf = ByteBuffer.allocate(4 * sensorDimension);
        mBuf.order(ByteOrder.nativeOrder());
        sensorLastTimeStamp = -1;
    }

    public void close(){
        stopped = true;
        onSensorChanged(null);
    }

    private void callFlushBuffer(){
        Log.d("wtf", "scheduling flusshing " + mySensor.getName());
        executor.execute(new FlushBufferTask());
    }

    private void callStartMic(long timeStamp){
        managerInterface.startMicRecording(timeStamp);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event == null) {
            callFlushBuffer();
            return;
        }

        if(stopped && flushed){
            return;
        }

        // reject old sensor events
        if(event.timestamp < sensorStartTime)
            return;

        // write new values to buffer
        // determine offset between previous and current event
        if (sensorLastTimeStamp != -1)
            sensorOffset += event.timestamp - sensorLastTimeStamp;
        sensorLastTimeStamp = event.timestamp;

        // reject event if there have been too many events than specified by rate
        if(sensorOffset < sensorDelay)
            return;

        // write event as often as required to fill the rate
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

                sensorTimestampsBuffer[0] = sensorTimestampsBuffer[sensorPointer-1];
                sensorPointer = 1;
            }
            sensorOffset -= sensorDelay;
        }

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

    /**
     * Simple approach to determine if the user is currently washing their hands
     * check if one of the axes has been a certain impact.
     * @return time stamp where impact happened
     */
    private long possibleHandWash() {
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

        /**
         * Create a copy of all sensor buffers to use them in background while sensor service
         * already writes new ones
         */
        public FlushBufferTask(){
            flushedSensorPointer = sensorPointer;
            bufferValues = new float[sensorValuesBuffer.length][sensorValuesBuffer[0].length];
            for(int i = 0; i < bufferValues.length; i++){
                System.arraycopy(sensorValuesBuffer[i], 0, bufferValues[i], 0, bufferValues[0].length);
            }
            timestamps = new long[sensorTimestampsBuffer.length];
            System.arraycopy(sensorTimestampsBuffer, 0, timestamps, 0, timestamps.length);
        }

        /**
         * Execute async work
         */
        @Override
        public void run() {
            Log.d("wtf", "flusshing " + mySensor.getName() + " stopped: " + stopped);
            try {
                writeSensorData();
            } catch (IOException e) {
                e.printStackTrace();
            }
            Log.d("wtf", "written " + mySensor.getName() + " stopped: " + stopped);

            if(stopped)
                closed = true;

            managerInterface.flushBuffer(sensorIndex, bufferValues, timestamps);
        }

        /**
         * Write sensor data into its container
         * @throws IOException
         */
        private void writeSensorData() throws IOException {
            // Log.d("sensor", "Write data " + closed);
            StringBuilder data = new StringBuilder();

            for (int i = 1; i < flushedSensorPointer; i++) {
                if(useZIPStream) {
                    data.append(timestamps[i]).append("\t");
                    for(int axe = 0; axe < sensorDimension - 1; axe++){
                        data.append(bufferValues[i][axe]).append("\t");
                    }
                    data.append(bufferValues[i][sensorDimension-1]).append("\n");
                }

            }

            if (useZIPStream) {
                dataProcessor.writeSensorData(mySensor.getStringType(), data.toString());
            }
        }

    }

}
