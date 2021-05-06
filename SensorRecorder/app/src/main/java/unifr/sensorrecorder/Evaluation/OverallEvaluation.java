package unifr.sensorrecorder.Evaluation;

import android.app.NotificationManager;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Point;
import android.os.Bundle;
import android.support.wearable.activity.WearableActivity;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.Button;
import android.widget.RatingBar;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import unifr.sensorrecorder.DataContainer.DataProcessorProvider;
import unifr.sensorrecorder.NotificationSpawner;
import unifr.sensorrecorder.R;
import unifr.sensorrecorder.SensorRecordingManager;

public class OverallEvaluation extends WearableActivity {

    private TextView mTextView;
    private  RatingBar ratingBar;
    private SeekBar seekBar;
    private RelativeLayout rlMarker;
    private Button answer;
    private Animation fadeOut;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_overall_evaluation);

        mTextView = (TextView) findViewById(R.id.text);

        ratingBar = (RatingBar) findViewById(R.id.ovEvRatingBar);
        seekBar = (SeekBar) findViewById(R.id.ovEvSeekBar);
        answer = (Button) findViewById(R.id.ovEvRateButton);
        rlMarker = (RelativeLayout) findViewById(R.id.rlMarker);

        fadeOut = new AlphaAnimation(1, 0);
        fadeOut.setInterpolator(new AccelerateInterpolator()); //and this
        fadeOut.setStartOffset(1000);
        fadeOut.setDuration(1000);
        fadeOut.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {

            }

            @Override
            public void onAnimationEnd(Animation animation) {
                rlMarker.setVisibility(View.GONE);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateMarker(seekBar, rlMarker, (String.valueOf(progress)));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                rlMarker.setVisibility(View.VISIBLE);
                rlMarker.clearAnimation();
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                rlMarker.startAnimation(fadeOut);
            }
        });

        answer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int rating = Math.round(seekBar.getProgress());
                TimeZone tz = TimeZone.getTimeZone("UTC");
                DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mmZ");
                df.setTimeZone(tz);
                String timeStamp = df.format(new Date());
                StringBuilder line = new StringBuilder(timeStamp);
                line.append("\t").append((new Date()).getTime());
                line.append("\t").append(rating);
                line.append("\n");

                try {
                    DataProcessorProvider.getProcessor().writeOverallEvaluation(line.toString());
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

    private void updateMarker(final SeekBar sb,
                              View rlMarker,
                              String message) {

        final TextView tvProgress = (TextView) rlMarker.findViewById(R.id.tvProgress);
        final View vArrow = (View) rlMarker.findViewById(R.id.vArrow);
        /**
         * According to this question:
         * https://stackoverflow.com/questions/20493577/android-seekbar-thumb-position-in-pixel
         * one can find the SeekBar thumb location in pixels using:
         */
        int width = sb.getWidth()
                - sb.getPaddingLeft()
                - sb.getPaddingRight();
        final int thumbPos = sb.getPaddingLeft()
                + width
                * sb.getProgress()
                / sb.getMax() +
                //take into consideration the margin added (in this case it is 10dp)
                Math.round(convertDpToPixel(10, this));

        tvProgress.setText(message);

        tvProgress.post(new Runnable() {
            @Override
            public void run() {

                final Display display = ((WindowManager) getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
                final Point deviceDisplay = new Point();
                display.getSize(deviceDisplay);

                //vArrow always follow seekBar thumb location
                vArrow.setX(thumbPos - sb.getThumbOffset());

                //unlike vArrow, tvProgress will not always follow seekBar thumb location
                if ((thumbPos - tvProgress.getWidth() / 2 - sb.getPaddingLeft()) < 0) {
                    //part of the tvProgress is to the left of 0 bound
                    tvProgress.setX(vArrow.getX() - 20);
                } else if ((thumbPos + tvProgress.getWidth() / 2 + sb.getPaddingRight()) > deviceDisplay.x) {
                    //part of the tvProgress is to the right of screen width bound
                    tvProgress.setX(vArrow.getX() - tvProgress.getWidth() + 20 + vArrow.getWidth());
                } else {
                    //tvProgress is between 0 and screen width bounds
                    tvProgress.setX(thumbPos - tvProgress.getWidth() / 2f);
                }
            }
        });
    }

    public static float convertDpToPixel(float dp, Context context) {
        Resources resources = context.getResources();
        DisplayMetrics metrics = resources.getDisplayMetrics();
        float px = dp * ((float) metrics.densityDpi / DisplayMetrics.DENSITY_DEFAULT);
        return px;
    }

}