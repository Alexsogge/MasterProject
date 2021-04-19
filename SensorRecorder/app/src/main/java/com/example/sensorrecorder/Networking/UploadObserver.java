package com.example.sensorrecorder.Networking;

import android.content.Context;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.lifecycle.Observer;
import androidx.work.Data;
import androidx.work.WorkInfo;

import com.example.sensorrecorder.R;

import java.util.List;

public class UploadObserver implements Observer<List<WorkInfo>> {
    private TextView infoText;
    private ProgressBar uploadProgressBar;
    private Context context;

    public UploadObserver(TextView infoText, ProgressBar uploadProgressBar, Context context){
        this.infoText = infoText;
        this.uploadProgressBar = uploadProgressBar;
        this.context = context;
    }

    @Override
    public void onChanged(List<WorkInfo> workInfos) {
        for (WorkInfo workInfo : workInfos) {
            // Log.d("worker", "new info" + workInfo.toString());
            if (workInfo != null) {
                Data progress = workInfo.getProgress();
                int status = progress.getInt(UploadWorker.STATUS, -2);
                if(status == UploadWorker.STATUS_ERROR){
                    infoText.setText(context.getResources().getString(R.string.it_error));
                    // Log.d("worker", "error");
                }
                if(status == UploadWorker.STATUS_PENDING){
                    infoText.setText(context.getResources().getString(R.string.it_uploading));
                    uploadProgressBar.setVisibility(View.VISIBLE);
                    // Log.d("worker", "pending ");
                }
                if(status == UploadWorker.STATUS_PROGRESS){
                    infoText.setText(context.getResources().getString(R.string.it_uploading));
                    int value = progress.getInt(UploadWorker.PROGRESS, 0);
                    // Log.d("worker", "progress: "+ value);
                    uploadProgressBar.setProgress(value);
                }
                if(workInfo.getState().isFinished()){
                    int state = workInfo.getOutputData().getInt(UploadWorker.STATUS, -2);
                    if(state == UploadWorker.STATUS_FINISHED) {
                        infoText.setText(context.getResources().getString(R.string.it_upload_fin));
                        uploadProgressBar.setVisibility(View.INVISIBLE);
                        // Log.d("worker", "finish ");
                    }
                }
                else if(workInfo.getState().equals(WorkInfo.State.ENQUEUED)){
                    infoText.setText(context.getResources().getString(R.string.it_start_upload));
                    // Log.d("worker", "enqued ");
                }
            }
        }
    }
}
