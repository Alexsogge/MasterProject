package com.example.sensorrecorder;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.wearable.activity.WearableActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

public class ConfActivity extends WearableActivity {

    private static double FACTOR = 0.2; // c = a * sqrt(2)
    private SharedPreferences configs;
    private EditText serverNameInput;
    private EditText userIdentifierInput;
    private Button deleteTokenButton;
    private Networking networking;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_conf);

        adjustInset();

        configs = this.getSharedPreferences(
                getString(R.string.configs), Context.MODE_PRIVATE);

        networking = new Networking(this, null, configs);

        serverNameInput = (EditText)findViewById(R.id.editTextServerName);
        userIdentifierInput = (EditText)findViewById(R.id.editTextUserIdentifier);
        userIdentifierInput.setText(android.os.Build.MODEL);
        deleteTokenButton = (Button)findViewById(R.id.buttonDeleteToken);

        if (configs.contains(getString(R.string.conf_serverName)))
            serverNameInput.setText(configs.getString(getString(R.string.conf_serverName), ""));
        if (configs.contains(getString(R.string.conf_userIdentifier)))
            userIdentifierInput.setText(configs.getString(getString(R.string.conf_userIdentifier), ""));
        if(configs.contains(getString(R.string.conf_serverToken)))
            deleteTokenButton.setVisibility(View.VISIBLE);


        Button applyButton = (Button)findViewById(R.id.buttonApply);
        applyButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SharedPreferences.Editor configEditor = configs.edit();
                String serverName = serverNameInput.getText().toString();
                String userIdentifier = userIdentifierInput.getText().toString();
                if (!serverName.equals("")) {
                    if (serverName.length() < 4 || !serverName.substring(0, 4).equals("http"))
                        serverName = "https://" + serverName;
                    configEditor.putString(getString(R.string.conf_serverName), serverName);
                }
                Log.i("sensorrecorder", "Set servername to " + serverNameInput.getText().toString());
                if (!userIdentifier.equals(""))
                    configEditor.putString(getString(R.string.conf_userIdentifier), userIdentifier);
                Log.i("sensorrecorder", "Set user to " + userIdentifierInput.getText().toString());


                configEditor.apply();

                if (configs.getString(getString(R.string.conf_serverToken), "").equals("")){
                    networking.requestServerToken();
                }

                finish();
            }
        });

        deleteTokenButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SharedPreferences.Editor configEditor = configs.edit();
                configEditor.remove(getString(R.string.conf_serverToken));
                configEditor.apply();
                deleteTokenButton.setVisibility(View.INVISIBLE);
            }
        });



    }

    private void adjustInset() {
        if (getResources().getConfiguration().isScreenRound()) {
            int inset = (int)(FACTOR * getResources().getConfiguration().screenWidthDp);
            View layout = (View) findViewById(R.id.mainview);
            layout.setPadding(inset, inset, inset, inset);
        }
    }
}