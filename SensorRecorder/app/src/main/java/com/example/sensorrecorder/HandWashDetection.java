package com.example.sensorrecorder;

import android.app.Activity;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.widget.Toast;

import com.example.sensorrecorder.MathLib.TwoDArray;

import org.tensorflow.lite.Interpreter;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static android.content.Context.VIBRATOR_SERVICE;

public class HandWashDetection {
    public static final File modelFilePath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM + "/hand_wash_prediction/");
    public static final String modelName = "model_save.tflite";
    private final Executor executor = Executors.newSingleThreadExecutor(); // change according to your requirements
    private final Handler handler = new Handler(Looper.getMainLooper());

    private Interpreter tfInterpreter;
    private List<String> labelList;
    private Activity mainActivity;
    private Vibrator vibrator;
    private DataProcessor dataProcessor;

    private int[] sensorDimensions;
    private float[] activationThresholds;
    private float[][][] sensorBuffers;
    private long[][] sensorTimeStamps;

    private boolean[] waitForSensor;
    private int overallSensorDimensions;

    /** Options for configuring the Interpreter. */
    private final Interpreter.Options tfliteOptions = new Interpreter.Options();

    /** The loaded TensorFlow Lite model. */
    private MappedByteBuffer tfliteModel;


    protected HandWashDetection(Activity activity) throws IOException {
        // tfInterpreter = new Interpreter(loadModelFile(activity));
        mainActivity = activity;
        vibrator = (Vibrator) mainActivity.getSystemService(VIBRATOR_SERVICE);
        initModel();

        Log.d("Tensorflow", "Created a Tensorflow Lite");
    }

    public void initModel() throws IOException {
        tfliteModel = loadModelFile(mainActivity);
        tfInterpreter = new Interpreter(tfliteModel, tfliteOptions);
        labelList = new ArrayList<String>();
        labelList.add("0");
        labelList.add("1");
    }


    /** Memory-map the model file in Assets. */
    private MappedByteBuffer loadModelFile(Activity activity) throws IOException {

        File modelFile = new File(modelFilePath, modelName);
        if(modelFile.exists()){
            FileInputStream inputStream = new FileInputStream(modelFile);
            FileChannel fileChannel = inputStream.getChannel();
            return fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, modelFile.length());
        }
        AssetFileDescriptor fileDescriptor = activity.getAssets().openFd("model_save.tflite");
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    public float[][] RunInference(ByteBuffer sensorData){
        float[][] output =  new float[1][2];
        tfInterpreter.run(sensorData, output);
        return output;
    }


    public void setup(DataProcessor dataProcessor, int[] sensorDimensions, float[] activationThresholds, int bufferSize){
        this.dataProcessor = dataProcessor;
        this.sensorDimensions = sensorDimensions;
        this.activationThresholds = activationThresholds;
        sensorBuffers = new float[sensorDimensions.length][][];
        sensorTimeStamps = new long[sensorDimensions.length][bufferSize];
        waitForSensor = new boolean[sensorDimensions.length];
        overallSensorDimensions = 0;
        for(int i = 0; i < sensorDimensions.length; i++){
            sensorBuffers[i] = new float[sensorDimensions[i]][bufferSize];
            waitForSensor[i] = true;
            overallSensorDimensions += sensorDimensions[i];
        }
    }

    public void queueBuffer(int sensorIndex, float[][] buffer, long[] timestamps) {
        sensorBuffers[sensorIndex] = buffer;
        sensorTimeStamps[sensorIndex] = timestamps;
        waitForSensor[sensorIndex] = false;
        if(!stillWaitingForSensor()) {
            for (int i = 0; i < sensorTimeStamps.length; i++){
                waitForSensor[i] = true;
            }
            // executeNewPredictionTask();
            final boolean predictedHandwash = doPrediction();
            if(predictedHandwash){
                showHandWashNotification();
            }
        }
    }

    /*
    private void executeNewPredictionTask() {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final boolean predictedHandwash = new PredictionTask(sensorDimensions, activationThresholds, sensorBuffers, sensorTimeStamps).call();
                    if(predictedHandwash) {
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                showHandWashNotification();
                            }
                        });
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }
    */

    private void showHandWashNotification(){
        makeToast("Handwash");
        vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.EFFECT_TICK));
    }

    private boolean stillWaitingForSensor(){
        for(boolean sensorState: waitForSensor){
            if(sensorState)
                return true;
        }
        return false;
    }



    private void makeToast(final String text){
        mainActivity.runOnUiThread(new Runnable() {
            public void run() {
                Toast.makeText(mainActivity, text, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void addPrediction(String gesture, float[] predValues, long timestamp) throws IOException {
        // long timestamp = SystemClock.elapsedRealtimeNanos();
        String line = timestamp + "\t";
        for(int i = 0; i < predValues.length; i++){
            line += Math.round(predValues[i] * 100000.0)/100000.0 + "\t";
        }
        line += gesture + "\n";
        dataProcessor.writePrediction(line);
    }


    private boolean doPrediction(){
        boolean foundHandWash = false;
        for(int i = 51; i < sensorTimeStamps[0].length; i+=25){
            for (int sensorIndex = 0; sensorIndex < sensorTimeStamps.length; sensorIndex++){
                long ts = possibleHandWash(sensorIndex, i);
                if (ts > -1){
                    Log.d("pred", "add pred at: " + ts);
                    foundHandWash |= doHandWashPrediction(i, ts);
                    i += 50;
                    break;
                }
            }
        }
        return foundHandWash;
    }

    private boolean doHandWashPrediction(int sensorPointer, long timestamp){
        boolean foundHandWash = false;
        // create tmp array of right dimension for TF model
        float[][] vals = new float[50][overallSensorDimensions];

        int dimOffset = 0;
        for (int sensorIndex = 0; sensorIndex < sensorTimeStamps.length; sensorIndex++) {
            for(int i = 49; i >= 0; i--){
                for(int axes = 0; axes < sensorDimensions[sensorIndex]; axes++){
                    vals[i][dimOffset + axes] = sensorBuffers[sensorIndex][sensorPointer-i][axes];
                }
            }
            dimOffset += sensorDimensions[sensorIndex];
        }

        // System.arraycopy(sensorBuffers[sensorIndex][sensorPointer - i], 0, vals[i], 0, vals[0].length);

        // create 2D-Array out of tmp array
        TwoDArray tdArray = new TwoDArray(vals);
        // get all required features
        float[] features = tdArray.allFeatures();
        // create byte buffer out of features
        ByteBuffer frame = ByteBuffer.allocateDirect(4*features.length);
        frame.order(ByteOrder.nativeOrder());
        for(int i = 0; i < features.length; i++){
            frame.putFloat(features[i]);
        }
        // use tflite model to determine hand wash
        float[][] labelProbArray = RunInference(frame);

        // Log.d("Pred", "Predicted: " + labelProbArray[0][0] + " " + labelProbArray[0][1]);

        // observe prediction and write to disk
        float max_pred = labelProbArray[0][1];
        String gesture = "Noise";
        if (labelProbArray[0][0] > max_pred && labelProbArray[0][0] > 0.9){
            gesture = "Handwash";
            max_pred = labelProbArray[0][0];
            foundHandWash = true;
        }
        // Log.d("Pred", "Results in " + gesture + " to " + max_pred * 100 + "%");
        try {
            addPrediction(gesture, labelProbArray[0], timestamp);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return foundHandWash;
    }


    private long possibleHandWash(int sensorIndex, int pointer) {
        // simple approach to determine if the user is currently washing their hands
        // check if one of the acceleration or gyroscope axes has been a certain impact

        long timeStamp = sensorTimeStamps[sensorIndex][pointer];
        int offset = pointer - 25;
        for (int axes = 0; axes < sensorDimensions[sensorIndex]; axes++) {
            float min = Float.MAX_VALUE;
            float max = Float.MIN_VALUE;

            for (int i = offset; i <= pointer; i++) {
                if(sensorBuffers[sensorIndex][i][axes] < min)
                    min = sensorBuffers[sensorIndex][i][axes];
                if(sensorBuffers[sensorIndex][i][axes] > max) {
                    max = sensorBuffers[sensorIndex][i][axes];
                }
            }
            float diff = Math.abs(max-min);
            if (diff > activationThresholds[sensorIndex]) {
                Log.d("pred", sensorIndex + " thr: " + diff);
                return timeStamp;
            }
        }
        return -1;
    }


    /*
    class PredictionTask implements Callable<Boolean>{
        private int[] sensorDimensions;
        private float[] activationThresholds;
        private float[][][] sensorBuffers;
        private long[][] sensorTimeStamps;

        public PredictionTask(int[] sensorDimensions, float[]activationThresholds, float[][][] sensorBuffers, long[][] sensorTimeStamps){
            this.sensorDimensions = sensorDimensions;
            this.activationThresholds = activationThresholds;
            this.sensorBuffers = sensorBuffers;
            this.sensorTimeStamps = sensorTimeStamps;
        }

        @Override
        public Boolean call() throws Exception {
            boolean foundHandWash = doPrediction();
            return foundHandWash;
        }


        private boolean doPrediction(){
            boolean foundHandWash = false;
            for(int i = 51; i < sensorTimeStamps[0].length; i+=25){
                for (int sensorIndex = 0; sensorIndex < sensorTimeStamps.length; sensorIndex++){
                    long ts = possibleHandWash(sensorIndex, i);
                    if (ts > -1){
                        Log.d("pred", "add pred at: " + ts);
                        foundHandWash |= doHandWashPrediction(i, ts);
                        i += 50;
                        break;
                    }
                }
            }
            return foundHandWash;
        }

        private boolean doHandWashPrediction(int sensorPointer, long timestamp){
            boolean foundHandWash = false;
            // create tmp array of right dimension for TF model
            float[][] vals = new float[50][overallSensorDimensions];

            int dimOffset = 0;
            for (int sensorIndex = 0; sensorIndex < sensorTimeStamps.length; sensorIndex++) {
                for(int i = 49; i >= 0; i--){
                    for(int axes = 0; axes < sensorDimensions[sensorIndex]; axes++){
                        vals[i][dimOffset + axes] = sensorBuffers[sensorIndex][sensorPointer-i][axes];
                    }
                }
                dimOffset += sensorDimensions[sensorIndex];
            }

            // System.arraycopy(sensorBuffers[sensorIndex][sensorPointer - i], 0, vals[i], 0, vals[0].length);

            // create 2D-Array out of tmp array
            TwoDArray tdArray = new TwoDArray(vals);
            // get all required features
            float[] features = tdArray.allFeatures();
            // create byte buffer out of features
            ByteBuffer frame = ByteBuffer.allocateDirect(4*features.length);
            frame.order(ByteOrder.nativeOrder());
            for(int i = 0; i < features.length; i++){
                frame.putFloat(features[i]);
            }
            // use tflite model to determine hand wash
            float[][] labelProbArray = RunInference(frame);

            // Log.d("Pred", "Predicted: " + labelProbArray[0][0] + " " + labelProbArray[0][1]);

            // observe prediction and write to disk
            float max_pred = labelProbArray[0][1];
            String gesture = "Noise";
            if (labelProbArray[0][0] > max_pred && labelProbArray[0][0] > 0.9){
                gesture = "Handwash";
                max_pred = labelProbArray[0][0];
                foundHandWash = true;
            }
            // Log.d("Pred", "Results in " + gesture + " to " + max_pred * 100 + "%");
            try {
                addPrediction(gesture, labelProbArray[0], timestamp);
            } catch (IOException e) {
                e.printStackTrace();
            }
            return foundHandWash;
        }


        private long possibleHandWash(int sensorIndex, int pointer) {
            // simple approach to determine if the user is currently washing their hands
            // check if one of the acceleration or gyroscope axes has been a certain impact

            long timeStamp = sensorTimeStamps[sensorIndex][pointer];
            int offset = pointer - 25;
            for (int axes = 0; axes < sensorDimensions[sensorIndex]; axes++) {
                float min = Float.MAX_VALUE;
                float max = Float.MIN_VALUE;

                for (int i = offset; i <= pointer; i++) {
                    if(sensorBuffers[sensorIndex][i][axes] < min)
                        min = sensorBuffers[sensorIndex][i][axes];
                    if(sensorBuffers[sensorIndex][i][axes] > max) {
                        max = sensorBuffers[sensorIndex][i][axes];
                    }
                }
                float diff = Math.abs(max-min);
                if (diff > activationThresholds[sensorIndex]) {
                    Log.d("pred", sensorIndex + " thr: " + diff);
                    return timeStamp;
                }
            }
            return -1;
        }

    }

     */

}
