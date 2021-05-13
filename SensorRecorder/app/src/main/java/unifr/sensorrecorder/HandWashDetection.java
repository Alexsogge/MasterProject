package unifr.sensorrecorder;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.hardware.Sensor;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.tensorflow.lite.Interpreter;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

import unifr.sensorrecorder.DataContainer.DataProcessor;
import unifr.sensorrecorder.DataContainer.StaticDataProvider;

import static android.content.Context.VIBRATOR_SERVICE;

public class HandWashDetection {
    public static final File modelFilePath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM + "/hand_wash_prediction/");
    public static final String modelName = "predictionModel.tflite";
    public static final String modelSettingsName = "predictionModel.json";
    private final Executor executor = Executors.newSingleThreadExecutor(); // change according to your requirements
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final int[] initialRequiredSensors = new int[]{Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_GYROSCOPE};
    private final int[] hasToBeTranslatedSensors = new int[]{Sensor.TYPE_ROTATION_VECTOR, Sensor.TYPE_MAGNETIC_FIELD};
    private final int initialFrameSize = 50;

    private final long initialPositivePredictedTimeFrame = (long) 3e9; // 3 seconds
    private final int initialRequiredPositivePredictions = 3;
    private final long initialNotificationCoolDown = (long) 40e9; // 40 seconds

    private int[] requiredSensors;
    private int frameSize;
    private long positivePredictedTimeFrame;
    private int requiredPositivePredictions;
    private int inputShape;
    private long notificationCoolDown;
    private long lastNotificationTS;

    private Interpreter tfInterpreter;
    private List<String> labelList;
    private Context context;
    private Vibrator vibrator;
    private DataProcessor dataProcessor;
    private ReentrantLock queueBufferLock = new ReentrantLock();

    private long lastPositivePrediction;
    private int positivePredictedCounter;
    private boolean lastPrediction;

    private int[] activeSensorTypes;
    private int[] sensorDimensions;
    private int[] requiredSensorsDimensions;
    private float[] activationThresholds;
    private float[][][] sensorBuffers;
    private long[][] sensorTimeStamps;

    private boolean[] waitForSensor;
    private int overallSensorDimensions;

    /** Options for configuring the Interpreter. */
    private final Interpreter.Options tfliteOptions = new Interpreter.Options();

    /** The loaded TensorFlow Lite model. */
    private MappedByteBuffer tfliteModel;

    private boolean debugAutoTrue = false;
    private boolean discardDoubleFalsePredicted = false;


    protected HandWashDetection(Context context) throws IOException {
        // tfInterpreter = new Interpreter(loadModelFile(activity));
        this.context = context;
        vibrator = (Vibrator) context.getSystemService(VIBRATOR_SERVICE);
        initModel();

        Log.d("Tensorflow", "Created a Tensorflow Lite");
    }

    public void initModel()  {
        try {
            tfliteModel = loadModelFile(context);
        } catch (IOException e){
            e.printStackTrace();
            makeToast(context.getString(R.string.toast_couldnt_load_tf));
        }
        if(tfliteModel != null)
            tfInterpreter = new Interpreter(tfliteModel, tfliteOptions);


        labelList = new ArrayList<String>();
        labelList.add("0");
        labelList.add("1");
    }


    /** Memory-map the model file in Assets. */
    private MappedByteBuffer loadModelFile(Context context) throws IOException {

        File modelFile = new File(modelFilePath, modelName);
        try {
            if (modelFile.exists()) {
                FileInputStream inputStream = new FileInputStream(modelFile);
                FileChannel fileChannel = inputStream.getChannel();
                makeToast(this.context.getString(R.string.toast_use_dl_tf));
                return fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, modelFile.length());
            }
        } catch (FileNotFoundException e){
            e.printStackTrace();
        }
        AssetManager assetManager = context.getAssets();
        String[] assets = assetManager.list("");
        String assetModel = "";
        for(String asset: assets){
            Log.d("pred", "asset: " + asset);
            if(asset.contains("tflite")) {
                assetModel = asset;
                break;
            }
        }
        AssetFileDescriptor fileDescriptor = assetManager.openFd(modelName);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    public float[][] RunInference(ByteBuffer sensorData){
        float[][] output =  new float[1][2];
        if (tfInterpreter != null)
            tfInterpreter.run(sensorData, output);
        return output;
    }


    public void setup(DataProcessor dataProcessor, int[] sensorTypes, int[] sensorDimensions, float[] activationThresholds, int bufferSize){
        this.dataProcessor = dataProcessor;
        this.activeSensorTypes = sensorTypes;
        this.sensorDimensions = sensorDimensions;
        this.activationThresholds = activationThresholds;
        this.notificationCoolDown = initialNotificationCoolDown;

        requiredSensors = initialRequiredSensors;
        frameSize = initialFrameSize;
        positivePredictedTimeFrame = initialPositivePredictedTimeFrame;
        requiredPositivePredictions = initialRequiredPositivePredictions;
        inputShape = 0;

        try {
            loadSettingsFromFile();
        } catch (JSONException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        this.requiredSensorsDimensions = new int[requiredSensors.length];
        for(int i = 0; i < requiredSensors.length; i++){
            this.requiredSensorsDimensions[i] = SensorRecordingManager.getNumChannels(requiredSensors[i]);
            inputShape += 4 * frameSize * requiredSensorsDimensions[i];
        }

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


    private void loadSettingsFromFile() throws IOException, JSONException {
        File path = modelFilePath;
        if(!path.exists())
            return;
        File settingsFile = new File(path, modelSettingsName);
        if(!settingsFile.exists())
            return;

        FileReader fileReader = new FileReader(settingsFile);
        BufferedReader bufferedReader = new BufferedReader(fileReader);
        StringBuilder stringBuilder = new StringBuilder();
        String line = bufferedReader.readLine();
        while (line != null){
            stringBuilder.append(line).append("\n");
            line = bufferedReader.readLine();
        }
        bufferedReader.close();
        String jsonString = stringBuilder.toString();

        JSONObject jsonObject = new JSONObject(jsonString);
        if(jsonObject.has("required_sensors")) {
            JSONArray jsonSensors = jsonObject.getJSONArray("required_sensors");
            requiredSensors = new int[jsonObject.length()];
            for (int i = 0; i < jsonSensors.length(); i++) {
                requiredSensors[i] = jsonSensors.getInt(i);
            }
        }
        if(jsonObject.has("frame_size"))
            frameSize = jsonObject.getInt("frame_size");
        if(jsonObject.has("positive_prediction_time"))
            positivePredictedTimeFrame = (long)(jsonObject.getInt("positive_prediction_time")) * (long) 1e9;
        if(jsonObject.has("positive_prediction_counter"))
            requiredPositivePredictions = jsonObject.getInt("positive_prediction_counter");
        if(jsonObject.has("notification_cool_down"))
            notificationCoolDown = (long)jsonObject.getInt("notification_cool_down") * (long) 1e9;
    }

    public void queueBuffer(int sensorIndex, float[][] buffer, long[] timestamps) {
        queueBufferLock.lock();
        try {
            sensorBuffers[sensorIndex] = buffer;
            sensorTimeStamps[sensorIndex] = timestamps;
            waitForSensor[sensorIndex] = false;
            if (sensorHasToBeTranslated(activeSensorTypes[sensorIndex]))
                sensorBuffers[sensorIndex] = distanceTranslation(buffer);

            if (!stillWaitingForSensor()) {
                for (int i = 0; i < sensorTimeStamps.length; i++) {
                    waitForSensor[i] = true;
                }
                // executeNewPredictionTask();
                final boolean predictedHandwash = doPredictions();
                if (predictedHandwash) {
                    showHandWashNotification();
                }
            }
        } finally {
            queueBufferLock.unlock();
        }
    }


    public float[][] distanceTranslation(float[][] data){
        float[][] outData = new float[data.length][data[0].length];

        for (int i = 1; i < data.length; i++){
            for(int axis = 0; axis < data[0].length; axis++){
                outData[i][axis] = data[i][axis] - data[i-1][axis];
            }
        }
        for(int axis = 0; axis < data[0].length; axis++){
            outData[outData.length-1][axis] = 0;
        }

        return outData;
    }


    private void showHandWashNotification(){
        if(lastPositivePrediction > StaticDataProvider.getProcessor().lastEvaluationTS + notificationCoolDown) {
            lastNotificationTS = lastPositivePrediction;
            makeToast(context.getString(R.string.toast_pred_hw));
            vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.EFFECT_TICK));
            NotificationSpawner.spawnHandWashPredictionNotification(context.getApplicationContext(), lastPositivePrediction);
            Log.d("pred", "spawn notification");
        } else {
            Log.d("pred", "ignore notification");
        }
    }

    private boolean stillWaitingForSensor(){
        for(boolean sensorState: waitForSensor){
            if(sensorState)
                return true;
        }
        return false;
    }



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
                Toast.makeText(context, text, Toast.LENGTH_LONG).show();
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


    private boolean doPredictions(){
        // if we find a certain impact on a sensor axis we want to predict the current window
        // furthermore we want to predict the next x windows
        int fadeOutCounter = 0;
        long ts = -1;
        long lastTruePrediction = -1;
        boolean foundHandWash = false;
        for(int i = frameSize + 1; i < sensorTimeStamps[0].length; i+=25){
            for (int sensorIndex = 0; sensorIndex < sensorTimeStamps.length; sensorIndex++){
                ts = possibleHandWash(sensorIndex, i);
                if (ts > -1) {
                    fadeOutCounter = 3;
                    break;
                }
            }
            if(fadeOutCounter > 0) {
                // Log.d("pred", "add pred at: " + ts);
                long timestamp = sensorTimeStamps[0][i];
                boolean prediction = doHandWashPrediction(i, timestamp);
                // Log.d("pred", "p: " + prediction);
                if (prediction) {
                    if (timestamp < lastPositivePrediction + positivePredictedTimeFrame) {
                        positivePredictedCounter++;
                        // Log.d("pred", "count to " + positivePredictedCounter);
                        if (positivePredictedCounter >= requiredPositivePredictions) {
                            positivePredictedCounter = 0;
                            foundHandWash = true;
                        }
                    } else {
                        positivePredictedCounter = 1;
                    }
                    lastPositivePrediction = timestamp;
                } else if(!lastPrediction && discardDoubleFalsePredicted){
                    positivePredictedCounter = 0;
                }
                lastPrediction = prediction;
                i += (frameSize / 1.5) - 25;
            }
            if (fadeOutCounter > 0)
                fadeOutCounter--;
        }
        return foundHandWash;
    }

    private boolean doHandWashPrediction(int sensorPointer, long timestamp){
        boolean foundHandWash = false;
        // create tmp array of right dimension for TF model
        // float[][] window = new float[frameSize][overallSensorDimensions];
        float[][][] window = new float[activeSensorTypes.length][][];
        /*
        int dimOffset = 0;
        for (int sensorIndex = 0; sensorIndex < sensorTimeStamps.length; sensorIndex++) {
            for(int i = 49; i >= 0; i--){
                for(int axes = 0; axes < sensorDimensions[sensorIndex]; axes++){
                    window[i][dimOffset + axes] = sensorBuffers[sensorIndex][sensorPointer-i][axes];
                }
            }
            dimOffset += sensorDimensions[sensorIndex];
        }*/
        for (int sensorIndex = 0; sensorIndex < activeSensorTypes.length; sensorIndex++) {
            window[sensorIndex] = new float[frameSize][sensorDimensions[sensorIndex]];
            for(int i = 0; i < frameSize; i++){
                if (sensorDimensions[sensorIndex] >= 0)
                    System.arraycopy(sensorBuffers[sensorIndex][sensorPointer - frameSize + i], 0, window[sensorIndex][i], 0, sensorDimensions[sensorIndex]);
            }
        }

        // System.arraycopy(sensorBuffers[sensorIndex][sensorPointer - i], 0, window[i], 0, window[0].length);


        // create byte buffer as required
        ByteBuffer frameBuffer = ByteBuffer.allocateDirect(inputShape);
        frameBuffer.order(ByteOrder.nativeOrder());

        // since model needs sensor values we probably don't have,
        // we have to fill the frameBuffer with dummy values for these sensors

        for(int x = 0; x < frameSize; x++) {
            for (int i = 0; i < requiredSensors.length; i++) {
                int activeSensorIndex = getActiveSensorIndexOfType(requiredSensors[i]);
                // if index == -1 we don't have actual values -> insert dummy else value from buffer
                for (int axes = 0; axes < requiredSensorsDimensions[i]; axes++) {
                    if (activeSensorIndex == -1)
                        frameBuffer.putFloat(0);
                    else
                        frameBuffer.putFloat(window[activeSensorIndex][x][axes]);
                }
            }
        }


        // use tflite model to determine hand wash
        float[][] labelProbArray = RunInference(frameBuffer);

        // Log.d("Pred", "Predicted: " + labelProbArray[0][0] + " " + labelProbArray[0][1]);

        // observe prediction and write to disk
        float max_pred = labelProbArray[0][0];
        String gesture = "Noise";
        if (labelProbArray[0][1] > max_pred && labelProbArray[0][1] > 0.95){
            gesture = "Handwash";
            max_pred = labelProbArray[0][1];
            // test if there are multiple positive predictions within given time frameBuffer
            foundHandWash = true;
        }
        // Log.d("Pred", "Results in " + gesture + " to " + max_pred * 100 + "%");
        try {
            addPrediction(gesture, labelProbArray[0], timestamp);
        } catch (IOException e) {
            e.printStackTrace();
        }
        if(debugAutoTrue) {
            lastPositivePrediction = timestamp;
            foundHandWash = true;
        }
        return foundHandWash;
    }

    private int getActiveSensorIndexOfType(int sensorType){
        for(int i = 0; i < activeSensorTypes.length; i++){
            if(activeSensorTypes[i] == sensorType)
                return i;
        }
        return -1;
    }

    private boolean sensorHasToBeTranslated(int sensorType){
        for(int type: hasToBeTranslatedSensors){
            if(type == sensorType)
                return true;
        }
        return false;
    }

    private long possibleHandWash(int sensorIndex, int pointer) {
        // simple approach to determine if the user is currently washing their hands
        // check if one of the acceleration or gyroscope axes has been a certain impact

        long timeStamp = sensorTimeStamps[sensorIndex][pointer];
        int offset = Math.max(0, pointer - 25);
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
                // Log.d("pred", sensorIndex + " thr: " + diff);
                return timeStamp;
            }
        }
        return -1;
    }
}
