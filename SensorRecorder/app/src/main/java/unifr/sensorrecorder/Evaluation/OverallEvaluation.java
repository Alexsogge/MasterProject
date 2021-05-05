package unifr.sensorrecorder.Evaluation;

import android.app.NotificationManager;
import android.content.Context;
import android.os.Bundle;
import android.support.wearable.activity.WearableActivity;
import android.view.View;
import android.widget.Button;
import android.widget.RatingBar;
import android.widget.TextView;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import unifr.sensorrecorder.DataProcessor;
import unifr.sensorrecorder.NotificationSpawner;
import unifr.sensorrecorder.R;
import unifr.sensorrecorder.SensorRecordingManager;

public class OverallEvaluation extends WearableActivity {

    private TextView mTextView;
    private  RatingBar ratingBar;
    private Button answer;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_overall_evaluation);

        mTextView = (TextView) findViewById(R.id.text);

        ratingBar = (RatingBar) findViewById(R.id.ovEvRatingBar);
        answer = (Button) findViewById(R.id.ovEvRateButton);

        answer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int rating = Math.round(ratingBar.getRating());
                TimeZone tz = TimeZone.getTimeZone("UTC");
                DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mmZ");
                df.setTimeZone(tz);
                String timeStamp = df.format(new Date());
                StringBuilder line = new StringBuilder(timeStamp);
                line.append("\t").append((new Date()).getTime());
                line.append("\t").append(rating);
                line.append("\n");

                try {
                    SensorRecordingManager.dataProcessor.writeOverallEvaluation(line.toString());
                } catch (IOException e) {
                    e.printStackTrace();
                }

                NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                mNotificationManager.cancel(NotificationSpawner.DAILY_REMINDER_REQUEST_CODE);

                moveTaskToBack(true);
                finish();
            }
        });

        // Enables Always-on
        setAmbientEnabled();
    }

}