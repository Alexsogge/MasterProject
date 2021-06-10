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
import android.widget.Toast;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.TimeZone;

import unifr.sensorrecorder.DataContainer.StaticDataProvider;
import unifr.sensorrecorder.NotificationSpawner;
import unifr.sensorrecorder.R;

public class OverallEvaluation extends WearableActivity {

    private TextView textViewQuestion;
    private RatingBar ratingBar;
    private SeekBar seekBar;
    private RelativeLayout rlMarker;
    private Button answer;
    private Animation fadeOut;
    private TextView textViewMarkerL;
    private TextView textViewMarkerR;

    private HashMap<Integer, Integer> questions;
    private HashMap<Integer, Integer> answers;
    private HashMap<Integer, Integer[]> markerTexts;
    private int currentQuestion = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_overall_evaluation);

        textViewQuestion = (TextView) findViewById(R.id.textViewOeQuestion);
        textViewMarkerL = (TextView) findViewById(R.id.textViewOeMarkerL);
        textViewMarkerR = (TextView) findViewById(R.id.textViewOeMarkerR);

        ratingBar = (RatingBar) findViewById(R.id.ovEvRatingBar);
        seekBar = (SeekBar) findViewById(R.id.ovEvSeekBar);
        answer = (Button) findViewById(R.id.ovEvRateButton);
        rlMarker = (RelativeLayout) findViewById(R.id.rlMarker);

        questions = new HashMap<>();
        questions.put(0, R.string.str_oar_question_1);
        questions.put(1, R.string.str_oar_question_2);
        questions.put(2, R.string.str_oar_question_3);

        markerTexts = new HashMap<>();
        markerTexts.put(0, new Integer[]{R.string.str_one, R.string.str_five});
        markerTexts.put(1, new Integer[]{R.string.str_one, R.string.str_five});
        markerTexts.put(2, new Integer[]{R.string.str_never, R.string.str_always});

        answers = new HashMap<>();
        textViewQuestion.setText(questions.get(0));
        textViewMarkerL.setText(markerTexts.get(0)[0]);
        textViewMarkerR.setText(markerTexts.get(0)[1]);

        // initSeekBar();
        initRatingBar();

        // Enables Always-on
        setAmbientEnabled();
    }

    private void initRatingBar(){
        ratingBar.setVisibility(View.VISIBLE);
        ratingBar.setRating(0);
        answer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int rating = Math.round(ratingBar.getProgress());
                if (rating > 0) {
                    setAnswer(rating);
                    ratingBar.setRating(0);
                } else {
                    Toast.makeText(getApplicationContext(), getString(R.string.toast_give_rating), Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void setAnswer(int value){
        answers.put(currentQuestion, value);
        currentQuestion++;
        if(currentQuestion >= questions.size()) {
            saveRating();
        } else {
            textViewQuestion.setText(questions.get(currentQuestion));
            textViewMarkerL.setText(markerTexts.get(currentQuestion)[0]);
            textViewMarkerR.setText(markerTexts.get(currentQuestion)[1]);
        }
    }

    private void initSeekBar(){
        seekBar.setVisibility(View.VISIBLE);
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
                setAnswer(rating);
            }
        });
    }

    private void saveRating(){
        TimeZone tz = TimeZone.getTimeZone("UTC");
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mmZ");
        df.setTimeZone(tz);
        String timeStamp = df.format(new Date());
        StringBuilder line = new StringBuilder(timeStamp);
        line.append("\t").append((new Date()).getTime());
        for(int i = 0; i < answers.size(); i++){
            line.append("\t").append(answers.get(i));
        }
        line.append("\n");

        try {
            StaticDataProvider.getProcessor().writeOverallEvaluation(line.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }

        NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mNotificationManager.cancel(NotificationSpawner.DAILY_REMINDER_REQUEST_CODE);

        Toast.makeText(getApplicationContext(), getString(R.string.toast_eval_finished), Toast.LENGTH_LONG).show();

        // cancel repetitive alarm
        NotificationSpawner.stopRepeatingOverallEvaluationReminder(this);

        NotificationSpawner.closeOverallEvaluationNotification(this);

        moveTaskToBack(true);
        finish();
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