package com.example.sensorrecorder;

import android.app.Activity;
import android.content.res.AssetFileDescriptor;
import android.os.Environment;
import android.util.Log;

import org.tensorflow.lite.Interpreter;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

public class HandWashDetection {
    public static final File modelFilePath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM + "/hand_wash_prediction/");
    public static final String modelName = "model_save.tflite";
    private Interpreter tfInterpreter;
    private List<String> labelList;
    private Activity mainActivity;

    /** Options for configuring the Interpreter. */
    private final Interpreter.Options tfliteOptions = new Interpreter.Options();

    /** The loaded TensorFlow Lite model. */
    private MappedByteBuffer tfliteModel;


    protected HandWashDetection(Activity activity) throws IOException {
        // tfInterpreter = new Interpreter(loadModelFile(activity));
        mainActivity = activity;
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



}
