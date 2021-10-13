package unifr.sensorrecorder;

import android.content.Context;
import android.content.SharedPreferences;
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

import com.google.common.collect.EvictingQueue;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.tensorflow.lite.Interpreter;


import java.io.BufferedInputStream;
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
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;


import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtLoggingLevel;
import ai.onnxruntime.OrtSession;
import unifr.sensorrecorder.DataContainer.DataProcessor;
import unifr.sensorrecorder.DataContainer.StaticDataProvider;
import unifr.sensorrecorder.MathLib.MathOperations;

import static android.content.Context.VIBRATOR_SERVICE;

public class HandWashDetection {
    public static final File modelFilePath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM + "/hand_wash_prediction/");
    public static final String modelName = "predictionModel";
    public static final String ortModelName = "predictionModel.ort";
    public static final String modelSettingsName = "predictionModel";
    private final Executor executor = Executors.newSingleThreadExecutor(); // change according to your requirements
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final int[] initialRequiredSensors = new int[]{Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_GYROSCOPE};
    private final int[] hasToBeTranslatedSensors = new int[]{Sensor.TYPE_ROTATION_VECTOR, Sensor.TYPE_MAGNETIC_FIELD};
    private final int initialFrameSize = 50;

    private final long initialPositivePredictedTimeFrame = (long) 3e9; // 3 seconds
    private final int initialRequiredPositivePredictions = 3;
    private final long initialNotificationCoolDown = (long) 40e9; // 40 seconds

    // running mean
    private final float initialMeanThreshold = 0.65f;
    private final int initialMeanKernelWidth = 10;


    private int[] requiredSensors;
    private int frameSize;
    private long positivePredictedTimeFrame;
    private int requiredPositivePredictions;
    private float meanThreshold;
    private int meanKernelWidth;

    private int inputShape;
    private int inputBufferShape;
    private long notificationCoolDown;
    private long lastNotificationTS;
    private boolean useONNXModel = false;

    private Interpreter tfInterpreter;
    private List<String> labelList;
    private Context context;
    private Vibrator vibrator;
    private DataProcessor dataProcessor;
    private ReentrantLock queueBufferLock = new ReentrantLock();

    private OrtSession session;
    private OrtEnvironment env;

    private long lastPositivePrediction;
    private int positivePredictedCounter;
    private boolean lastPrediction;
    private EvictingQueue<Float> predictionKernel;

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

    private boolean initialized = false;
    private String loadedModelName = "base_model.tflite";

    private boolean debugAutoTrue = false;
    private boolean discardDoubleFalsePredicted = true;


    protected HandWashDetection(Context context) throws IOException {
        // tfInterpreter = new Interpreter(loadModelFile(activity));
        this.context = context;
        vibrator = (Vibrator) context.getSystemService(VIBRATOR_SERVICE);
        initModel();

        Log.d("Tensorflow", "Created a Tensorflow Lite");
    }

    public void initModel()  {
        SharedPreferences configs = context.getSharedPreferences(
                context.getString(R.string.configs), Context.MODE_PRIVATE);
        String currentModelName = configs.getString(context.getApplicationContext().getString(R.string.val_current_tf_model), "base_model.tflite");
        String[] currentModelNameParts = currentModelName.split("\\.(?=[^\\.]+$)");
        String modelExtension = currentModelNameParts[1];

        Log.d("pred", "try load model " + currentModelName + "with extension " + modelExtension);

        if (modelExtension.equals("tflite")){
            useONNXModel = false;
            try {
                loadTFModel();
            } catch (IOException e){
                e.printStackTrace();
                makeToast(context.getString(R.string.toast_couldnt_load_tf));
            }
        } else {
            try {
                loadORTModel();
                useONNXModel = true;
            } catch (OrtException | IOException e) {
                e.printStackTrace();
            }
        }


        initialized = true;
        makeToast(this.context.getString(R.string.toast_use_dl_tf) + loadedModelName);

        labelList = new ArrayList<String>();
        labelList.add("0");
        labelList.add("1");
    }

    public void initModelFallback(){
        try {
            tfliteModel = loadBaseTFModelFile(context);
        } catch (IOException e){
            e.printStackTrace();
            makeToast(context.getString(R.string.toast_couldnt_load_tf));
        }
    }


    /** Memory-map the model file in Assets. */
    private MappedByteBuffer loadTFModelFile(Context context) throws IOException {
        try {
            MappedByteBuffer model = loadDownloadedTFModelFile(context);
            if (model != null)
                return model;
        } catch (FileNotFoundException e){
            e.printStackTrace();
        }
        return loadBaseTFModelFile(context);
    }

    private MappedByteBuffer loadBaseTFModelFile(Context context) throws IOException {
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
        AssetFileDescriptor fileDescriptor = assetManager.openFd(modelName + ".tflite");
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        loadedModelName = "base_model.tflite";
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    private MappedByteBuffer loadDownloadedTFModelFile(Context context) throws IOException, FileNotFoundException{
        File modelFile = new File(modelFilePath, modelName + ".tflite");
        if (modelFile.exists() && modelFile.canRead() && modelFile.length() > 1) {
            FileInputStream inputStream = new FileInputStream(modelFile);
            FileChannel fileChannel = inputStream.getChannel();
            SharedPreferences configs = context.getSharedPreferences(
                    context.getString(R.string.configs), Context.MODE_PRIVATE);
            loadedModelName = configs.getString(context.getApplicationContext().getString(R.string.val_current_tf_model), "base_model.tflite");
            return fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, modelFile.length());
        }
        return null;
    }


    private void loadORTModel() throws OrtException, IOException {
        Log.d("pred", "Load ONNX model");
        File modelFile = new File(modelFilePath, ortModelName);
        long fileLength = modelFile.length();
        byte[] model = new byte[(int)fileLength];

        BufferedInputStream buf = new BufferedInputStream(new FileInputStream(modelFile));
        buf.read(model, 0, model.length);
        buf.close();
        //OrtSession.SessionOptions session_options = new OrtSession.SessionOptions();
        //session_options.addConfigEntry("session.load_model_format", "ORT");

        env = OrtEnvironment.getEnvironment(OrtLoggingLevel.ORT_LOGGING_LEVEL_ERROR);
        session = env.createSession(model);
        SharedPreferences configs = context.getSharedPreferences(
                context.getString(R.string.configs), Context.MODE_PRIVATE);
        loadedModelName = configs.getString(context.getApplicationContext().getString(R.string.val_current_tf_model), "base_model.tflite");
    }

    private void loadTFModel() throws IOException {
        Log.d("pred", "Load TF model");
        tfliteModel = loadTFModelFile(context);
        if (tfliteModel == null) {
            initModelFallback();
        }
        try {
            tfInterpreter = new Interpreter(tfliteModel, tfliteOptions);
        } catch (IllegalArgumentException | NullPointerException e){
            Log.e("Tensorflow", "Error during load tf model. Use fallback...");
            e.printStackTrace();
            initModelFallback();
            tfInterpreter = new Interpreter(tfliteModel, tfliteOptions);
        }
    }

    private void runSample() throws OrtException {
        Log.d("pred", "Run model");
        float[][] sourceArray = new float[1][900];
        OnnxTensor tensor = OnnxTensor.createTensor(env,sourceArray);
        try{
            OrtSession.Result output = session.run(Collections.singletonMap("input", tensor));
            Log.d("pred", "Reslut size:" + output.size());
            Log.d("pred", "Reslut info:" + output.get(0).getInfo().toString());
            float[][] values = (float[][]) output.get(0).getValue();
            Log.d("pred", "Reslut values:" + values[0][0] + " | " + values[0][1]);
        } catch (Exception e){
            e.printStackTrace();
        }
    }


    public float[][] RunInference(float[][][] window){
        // create byte buffer as required
        ByteBuffer frameBuffer = ByteBuffer.allocateDirect(inputBufferShape);
        frameBuffer.order(ByteOrder.nativeOrder());

        // since model needs sensor values we probably don't have,
        // we have to fill the frameBuffer with dummy values for these sensors
        for(int x = 0; x < frameSize; x++) {
            for (int i = 0; i < requiredSensors.length; i++) {
                int activeSensorIndex = getActiveSensorIndexOfType(requiredSensors[i]);
                // if index == -1 we don't have actual values -> insert dummy else value from buffer
                for (int axes = 0; axes < requiredSensorsDimensions[i]; axes++) {
                    if (activeSensorIndex == -1) {
                        frameBuffer.putFloat(0);
                    }else {
                        frameBuffer.putFloat(window[activeSensorIndex][x][axes]);
                    }
                }
            }
        }

        float[][] output =  new float[1][2];
        if (tfInterpreter != null)
            tfInterpreter.run(frameBuffer, output);
        return output;
    }

    public float[][] RunONNXInference(float[][][] window){
        // create buffer as required
        float[][][] frameBuffer = new float[1][frameSize][6];
        int bufferPos = 0;

        // since model needs sensor values we probably don't have,
        // we have to fill the frameBuffer with dummy values for these sensors
        for(int x = 0; x < frameSize; x++) {
            int axesIndex = 0;
            for (int i = 0; i < requiredSensors.length; i++) {
                int activeSensorIndex = getActiveSensorIndexOfType(requiredSensors[i]);
                // if index == -1 we don't have actual values -> insert dummy else value from buffer
                for (int axes = 0; axes < requiredSensorsDimensions[i]; axes++) {
                    if (activeSensorIndex == -1) {
                        frameBuffer[0][x][axesIndex] = 0;
                    }else {
                        frameBuffer[0][x][axesIndex] = window[activeSensorIndex][x][axes];
                    }
                    bufferPos++;
                    axesIndex++;
                }
            }
        }

        float[][] output =  new float[1][2];
        try {
            OnnxTensor tensor = OnnxTensor.createTensor(env, frameBuffer);
            OrtSession.Result result = session.run(Collections.singletonMap("input", tensor));
            output = (float[][]) result.get(0).getValue();

        } catch (OrtException e) {
            e.printStackTrace();
        }

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

        meanThreshold = initialMeanThreshold;
        meanKernelWidth = initialMeanKernelWidth;

        inputShape = 0;
        inputBufferShape = 0;

        try {
            loadSettingsFromFile();
        } catch (JSONException | IOException e) {
            e.printStackTrace();
        }

        this.requiredSensorsDimensions = new int[requiredSensors.length];
        for(int i = 0; i < requiredSensors.length; i++){
            this.requiredSensorsDimensions[i] = SensorRecordingManager.getNumChannels(requiredSensors[i]);
            inputShape += frameSize * requiredSensorsDimensions[i];
        }
        inputBufferShape = 4 * inputShape;

        sensorBuffers = new float[sensorDimensions.length][][];
        sensorTimeStamps = new long[sensorDimensions.length][bufferSize];
        waitForSensor = new boolean[sensorDimensions.length];
        predictionKernel = EvictingQueue.create(meanKernelWidth);
        for(int i = 0; i < meanKernelWidth; i++){
            predictionKernel.add(0f);
        }

        overallSensorDimensions = 0;
        for(int i = 0; i < sensorDimensions.length; i++){
            sensorBuffers[i] = new float[sensorDimensions[i]][bufferSize];
            waitForSensor[i] = true;
            overallSensorDimensions += sensorDimensions[i];
        }
    }


    private void loadSettingsFromFile() throws IOException, JSONException {
        JSONObject jsonObject = readModelSettingsFile();
        if (jsonObject == null)
            return;
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

        if(jsonObject.has("mean_threshold"))
            meanThreshold = (float)jsonObject.getDouble("mean_threshold");
        if(jsonObject.has("mean_kernel_size"))
            meanKernelWidth = jsonObject.getInt("mean_kernel_size");

    }


    public static JSONObject readModelSettingsFile()throws IOException, JSONException{
            File path = modelFilePath;
            if(!path.exists())
                return null;
            File settingsFile = new File(path, modelSettingsName + ".json");
            if(!settingsFile.exists() || !settingsFile.canRead() || settingsFile.length() <= 1)
                return null;

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
            return  jsonObject;
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
            try {
                StaticDataProvider.getProcessor().queueEvaluation(lastPositivePrediction);
            } catch (IOException e) {
                e.printStackTrace();
            }
            // makeToast(context.getString(R.string.toast_pred_hw));
            // vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.EFFECT_TICK));
            // vibrator.vibrate(VibrationEffect.createWaveform(new long[]{1000, 1000, 700, 500}, -1));
            vibrator.vibrate(VibrationEffect.createOneShot(1000, VibrationEffect.DEFAULT_AMPLITUDE));
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

    private void addPrediction(String gesture, float[] predValues, long timestamp, float runningMean) throws IOException {
        // long timestamp = SystemClock.elapsedRealtimeNanos();
        String line = timestamp + "\t";
        for(int i = 0; i < predValues.length; i++){
            line += Math.round(predValues[i] * 100000.0)/100000.0 + "\t";
        }
        line += runningMean + "\t" + gesture + "\n";
        dataProcessor.writePrediction(line);
    }


    private boolean doPredictions(){
        // if we find a certain impact on a sensor axis we want to predict the current window
        // furthermore we want to predict the next x windows
        int fadeOutCounter = 0;
        long ts = -1;
        long lastTruePrediction = -1;
        boolean foundHandWash = false;
        for(int i = frameSize + 1; i < sensorTimeStamps[0].length; i+=frameSize/2){
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
                float[] prediction = doHandWashPrediction(i, timestamp);
                predictionKernel.add(prediction[1]);
                float runningMean = MathOperations.mean(predictionKernel);
                String gesture = "Noise";
                if (runningMean > meanThreshold){
                    foundHandWash = true;
                    gesture = "Handwash";
                    lastPositivePrediction = timestamp;
                }
                try {
                    addPrediction(gesture, prediction, timestamp, runningMean);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                // Log.d("pred", "p: " + prediction + "  rm: " + runningMean);
                /*
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
                */

            } else {
                predictionKernel.add(0f);
            }
            if (fadeOutCounter > 0)
                fadeOutCounter--;
        }
        return foundHandWash;
    }

    private float[] doHandWashPrediction(int sensorPointer, long timestamp){
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

        float[][] labelProbArray;
        if(!useONNXModel) {
            // use tflite model to determine hand wash
            labelProbArray = RunInference(window);
            // Log.d("Pred", "Predicted TF: " + labelProbArray[0][0] + " " + labelProbArray[0][1]);
        } else {
            // use onnx model to determine hand wash
            labelProbArray = RunONNXInference(window);
            // Log.d("Pred", "Predicted ONnX: " + labelProbArray[0][0] + " " + labelProbArray[0][1]);
        }

        if(debugAutoTrue) {
            // lastPositivePrediction = timestamp;
            return new float[]{0, 1};
        }
        return labelProbArray[0];
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
        int offset = Math.max(0, pointer - (frameSize/2));
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
