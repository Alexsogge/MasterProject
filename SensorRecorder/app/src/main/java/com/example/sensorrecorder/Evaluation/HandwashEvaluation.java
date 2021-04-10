package com.example.sensorrecorder.Evaluation;

import android.content.Intent;
import android.os.Bundle;
import android.support.wearable.activity.WearableActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.example.sensorrecorder.R;
import com.example.sensorrecorder.SensorManager;

import java.io.IOException;
import java.util.HashMap;

public class HandwashEvaluation extends WearableActivity {

    private TextView questionText;
    private Button evalButtonYes;
    private Button evalButtonNo;

    private long timestamp;

    private HashMap<Integer, Integer> questions;
    private HashMap<Integer, Integer> answers;
    private int currentQuestion;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_handwash_evaluation);
        Log.d("eval", "Created eval activity");

        Intent intent = getIntent();
        timestamp = intent.getLongExtra("timestamp", -1);

        questionText = (TextView) findViewById(R.id.textQuestionText);
        evalButtonYes = (Button) findViewById(R.id.buttonEvalYes);
        evalButtonNo = (Button) findViewById(R.id.buttonEvalNo);

        questions = new HashMap<>();
        questions.put(0, R.string.not_just_washed_hands);
        questions.put(1, R.string.str_eval_question_1);
        questions.put(2, R.string.str_eval_question_2);

        answers = new HashMap<>();
        answers.put(0, 1);
        currentQuestion = 1;
        initUi();

        // Enables Always-on
        setAmbientEnabled();
    }

    private void initUi(){
        questionText.setText(questions.get(currentQuestion));
        evalButtonYes.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setAnswer(1);
            }
        });
        evalButtonNo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setAnswer(0);
            }
        });
    }

    private void setAnswer(int value){
        answers.put(currentQuestion, value);
        currentQuestion++;
        if(currentQuestion >= questions.size()) {
            saveEvaluation();
        } else {
            questionText.setText(questions.get(currentQuestion));
        }

    }

    private void saveEvaluation(){
        StringBuilder line = new StringBuilder(String.valueOf(timestamp));
        for(int i = 0; i < answers.size(); i++){
            line.append("\t").append(answers.get(i));
        }
        line.append("\n");
        try {
            SensorManager.dataProcessor.writeEvaluation(line.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
        moveTaskToBack(true);
        finish();
    }
}