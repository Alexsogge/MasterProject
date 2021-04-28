package com.example.sensorrecorder.Evaluation;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;

import com.example.sensorrecorder.DataProcessor;
import com.example.sensorrecorder.SensorRecordingManager;

import java.io.IOException;


public class EvaluationService extends Service {
    private final IBinder binder = new EvalServiceBinder();


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d("pred", "created evaluation service");

        if (intent.getStringExtra("trigger") != null) {
            if (intent.getStringExtra("trigger").equals("handWashConfirm")) {
                if (SensorRecordingManager.isRunning) {
                    long timestamp = intent.getLongExtra("timestamp", -1);
                    if (timestamp > -1) {
                        DataProcessor.lastEvaluationTS = timestamp;
                        Intent startEvalIntent = new Intent(this, HandwashEvaluation.class);
                        startEvalIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startEvalIntent.putExtra("timestamp", timestamp);
                        startActivity(startEvalIntent);
                    }
                }
            }

            if (intent.getStringExtra("trigger").equals("handWashDecline")) {
                if (SensorRecordingManager.isRunning) {
                    long timestamp = intent.getLongExtra("timestamp", -1);
                    if (timestamp > -1) {
                        try {
                            String line = timestamp + "\t" + 0 + "\n";
                            SensorRecordingManager.dataProcessor.writeEvaluation(line, false, 0);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
            if (intent.getStringExtra("trigger").equals("close")) {
                long timestamp = intent.getLongExtra("timestamp", -1);
                Log.d("eval", "Close evaluation: " + timestamp);
                if (timestamp > -1) {
                    try {
                        String line = timestamp + "\t" + -1 + "\n";
                        SensorRecordingManager.dataProcessor.writeEvaluation(line, true, timestamp);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        return START_STICKY;
    }


    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }



    public class EvalServiceBinder extends Binder {
        public EvaluationService getService() {
            // Return this instance of LocalService so clients can call public methods
            return EvaluationService.this;
        }
    }
}
