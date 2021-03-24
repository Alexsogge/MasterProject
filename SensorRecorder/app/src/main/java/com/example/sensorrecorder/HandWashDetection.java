package com.example.sensorrecorder;

import android.app.Activity;
import android.content.res.AssetFileDescriptor;
import android.util.Log;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

public class HandWashDetection {

    private Interpreter tfInterpreter;
    private List<String> labelList;

    /** Options for configuring the Interpreter. */
    private final Interpreter.Options tfliteOptions = new Interpreter.Options();

    /** The loaded TensorFlow Lite model. */
    private MappedByteBuffer tfliteModel;

    protected HandWashDetection(Activity activity) throws IOException {
        // tfInterpreter = new Interpreter(loadModelFile(activity));
        tfliteModel = loadModelFile(activity);
        tfInterpreter = new Interpreter(tfliteModel, tfliteOptions);
        labelList = new ArrayList<String>();
        labelList.add("0");
        labelList.add("1");

        Log.d("Tensorflow", "Created a Tensorflow Lite");
    }


    /** Memory-map the model file in Assets. */
    private MappedByteBuffer loadModelFile(Activity activity) throws IOException {
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
