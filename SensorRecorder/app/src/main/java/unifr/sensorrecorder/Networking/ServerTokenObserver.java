package unifr.sensorrecorder.Networking;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.lifecycle.Observer;
import androidx.work.Data;
import androidx.work.WorkInfo;

import unifr.sensorrecorder.R;

import java.util.List;

public class ServerTokenObserver implements Observer<List<WorkInfo>> {
    private TextView infoText;
    private Context context;

    public ServerTokenObserver(TextView infoText, Context context){
        this.infoText = infoText;
        this.context = context;
    }

    @Override
    public void onChanged(List<WorkInfo> workInfos) {
        for (WorkInfo workInfo : workInfos) {

            if (workInfo != null) {
                Data progress = workInfo.getProgress();
                int status = progress.getInt(UploadWorker.STATUS, -2);
                if(status == UploadWorker.STATUS_ERROR){
                    setInfoText(context.getResources().getString(R.string.it_error));
                }
                if(status == UploadWorker.STATUS_PENDING){
                    setInfoText(context.getResources().getString(R.string.it_token_pending));
                }
                if(status == UploadWorker.STATUS_SUCCESS){
                    setInfoText(context.getResources().getString(R.string.toast_auth_granted));
                }
                if(workInfo.getState().isFinished()){
                    int state = workInfo.getOutputData().getInt(UploadWorker.STATUS, -2);
                    if(state == UploadWorker.STATUS_FINISHED) {
                        setInfoText(context.getResources().getString(R.string.toast_auth_granted));
                    }
                    if(state == UploadWorker.STATUS_ERROR) {
                        setInfoText(context.getResources().getString(R.string.toast_no_server_name));
                    }
                }
            }
        }
    }

    private void setInfoText(final String text){
        if(infoText != null) {
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                public void run() {
                    infoText.setVisibility(View.VISIBLE);
                    infoText.setText(text);
                    infoText.invalidate();
                }
            });
        }
    }
}
