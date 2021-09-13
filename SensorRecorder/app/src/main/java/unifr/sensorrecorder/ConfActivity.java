package unifr.sensorrecorder;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.support.wearable.activity.WearableActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import unifr.sensorrecorder.DataContainer.StaticDataProvider;
import unifr.sensorrecorder.Networking.NetworkManager;

//import static android.Manifest.permission.RECORD_AUDIO;
//import static android.content.pm.PackageManager.PERMISSION_DENIED;

public class ConfActivity extends WearableActivity {

    private static double FACTOR = 0.146467f; // c = a * sqrt(2)
    private ConfActivity confActivity;
    private SharedPreferences configs;
    private EditText serverNameInput;
    private EditText userIdentifierInput;
    private CheckBox useZipsCheckbox;
    private CheckBox useMKVCheckbox;
    private CheckBox useMicCheckbox;
    private CheckBox autoUpdateTFCheckbox;
    private CheckBox updateTFCheckbox;
    private CheckBox scanBluetoothBeaconsCheckbox;
    private Switch multipleMicSwitch;
    private Button downloadTFModelButton;
    private Button deleteTokenButton;
    private Button applyButton;
    private NetworkManager networkManager;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_conf);
        confActivity = this;
        Log.d("sensorrecorderevent", "Show config activity");

        adjustInset();

        configs = this.getSharedPreferences(
                getString(R.string.configs), Context.MODE_PRIVATE);

        networkManager = StaticDataProvider.getNetworkManager();

        serverNameInput = (EditText)findViewById(R.id.editTextServerName);
        userIdentifierInput = (EditText)findViewById(R.id.editTextUserIdentifier);


        useZipsCheckbox = (CheckBox) findViewById(R.id.useZipCheckbox);
        useMKVCheckbox = (CheckBox) findViewById(R.id.useMKVCheckbox);
        //useMicCheckbox = (CheckBox) findViewById(R.id.useMicCheckbox);
        autoUpdateTFCheckbox = (CheckBox) findViewById(R.id.autoUpdateTFCheckbox);
        updateTFCheckbox = (CheckBox) findViewById(R.id.updateTFCheckbox);
        //multipleMicSwitch = (Switch) findViewById(R.id.multipleMicSwitch);
        scanBluetoothBeaconsCheckbox = (CheckBox) findViewById(R.id.scanBluetoothBeacons);

        downloadTFModelButton = (Button)findViewById(R.id.buttonGetTFModel);
        deleteTokenButton = (Button)findViewById(R.id.buttonDeleteToken);

        TextView androidIdView = findViewById(R.id.textViewAndroidId);
        String androidId = Settings.Secure.getString( getContentResolver(), Settings.Secure.ANDROID_ID);
        androidIdView.setText("Android ID: " + androidId);
        userIdentifierInput.setText(android.os.Build.MODEL + '[' + androidId + ']');

        if (configs.contains(getString(R.string.conf_serverName))) {
            String serverName = configs.getString(getString(R.string.conf_serverName), getString(R.string.predefined_serverName));
            serverNameInput.setText(serverName);
            if(serverName.equals(""))
                downloadTFModelButton.setEnabled(false);
        } else
            downloadTFModelButton.setEnabled(false);
        if (configs.contains(getString(R.string.conf_userIdentifier)))
            userIdentifierInput.setText(configs.getString(getString(R.string.conf_userIdentifier), ""));
        if(configs.contains(getString(R.string.conf_serverToken)))
            deleteTokenButton.setVisibility(View.VISIBLE);

        if(configs.contains(getString(R.string.conf_useZip)))
            useZipsCheckbox.setChecked(configs.getBoolean(getString(R.string.conf_useZip), true));
        if(configs.contains(getString(R.string.conf_useMKV)))
            useMKVCheckbox.setChecked(configs.getBoolean(getString(R.string.conf_useMKV), false));
        if(configs.contains(getString(R.string.conf_auto_update_tf))) {
            autoUpdateTFCheckbox.setChecked(configs.getBoolean(getString(R.string.conf_auto_update_tf), true));
            if(configs.getBoolean(getString(R.string.conf_auto_update_tf), false))
                updateTFCheckbox.setEnabled(false);
        }
        if(configs.contains(getString(R.string.conf_check_for_tf_update)))
            updateTFCheckbox.setChecked(configs.getBoolean(getString(R.string.conf_check_for_tf_update), true));
        if(configs.contains(getString(R.string.conf_scan_bluetooth_beacons)))
            scanBluetoothBeaconsCheckbox.setChecked(configs.getBoolean(getString(R.string.conf_scan_bluetooth_beacons), false));

        /*
        if(ContextCompat.checkSelfPermission(this, RECORD_AUDIO) == PERMISSION_DENIED){
            useMicCheckbox.setChecked(false);
            SharedPreferences.Editor configEditor = configs.edit();
            configEditor.putBoolean(getString(R.string.conf_useMic), false);
            configEditor.apply();
        }

        if(configs.contains(getString(R.string.conf_useMic))) {
            boolean useMic = configs.getBoolean(getString(R.string.conf_useMic), true);
            useMicCheckbox.setChecked(useMic);
            if (!useMic)
                multipleMicSwitch.setEnabled(false);
        }
        if(configs.contains(getString(R.string.conf_multipleMic)))
            multipleMicSwitch.setChecked(configs.getBoolean(getString(R.string.conf_multipleMic), true));


        useMicCheckbox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener(){
            @Override
            public void onCheckedChanged(CompoundButton buttonView,boolean isChecked) {
                if(isChecked){
                    if(ContextCompat.checkSelfPermission(confActivity, RECORD_AUDIO) == PERMISSION_DENIED){
                        useMicCheckbox.setChecked(false);
                        ActivityCompat.requestPermissions(confActivity,
                                new String[]{RECORD_AUDIO},
                                1);
                    } else {
                        multipleMicSwitch.setEnabled(true);
                    }
                } else {
                    multipleMicSwitch.setEnabled(false);
                }
            }
        });
        */

        autoUpdateTFCheckbox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener(){
            @Override
            public void onCheckedChanged(CompoundButton buttonView,boolean isChecked) {
                if(isChecked){
                    updateTFCheckbox.setEnabled(false);
                    checkWritePermission();
                } else {
                    updateTFCheckbox.setEnabled(true);
                }
            }
        });

        scanBluetoothBeaconsCheckbox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if(isChecked){
                   checkBluetoothPermission();
                }
            }
        });

        applyButton = (Button)findViewById(R.id.buttonApply);
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
                } else{
                    configEditor.putString(getString(R.string.conf_serverName), "");
                }
                Log.i("sensorrecorder", "Set servername to " + serverNameInput.getText().toString());
                if (!userIdentifier.equals(""))
                    configEditor.putString(getString(R.string.conf_userIdentifier), userIdentifier);
                Log.i("sensorrecorder", "Set user to " + userIdentifierInput.getText().toString());

                configEditor.putBoolean(getString(R.string.conf_useZip), useZipsCheckbox.isChecked());
                configEditor.putBoolean(getString(R.string.conf_useMKV), useMKVCheckbox.isChecked());
                //configEditor.putBoolean(getString(R.string.conf_useMic), useMicCheckbox.isChecked());
                //configEditor.putBoolean(getString(R.string.conf_multipleMic), multipleMicSwitch.isChecked());
                configEditor.putBoolean(getString(R.string.conf_auto_update_tf), autoUpdateTFCheckbox.isChecked());
                configEditor.putBoolean(getString(R.string.conf_check_for_tf_update), updateTFCheckbox.isChecked());
                configEditor.putBoolean(getString(R.string.conf_scan_bluetooth_beacons), scanBluetoothBeaconsCheckbox.isChecked());
                Log.d("conf", "save settings");
                configEditor.apply();
                // configEditor.commit();

                if (configs.getString(getString(R.string.conf_serverToken), "").equals("") &&
                        !configs.getString(getString(R.string.conf_serverName), "").equals("")){
                    networkManager.requestServerToken();
                }

                if (autoUpdateTFCheckbox.isChecked()){
                    final Handler handler = new Handler(Looper.getMainLooper());
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            NetworkManager.checkForTFModelUpdate(getApplicationContext());
                        }
                    }, 1000*5);
                }

                finish();

            }
        });

        downloadTFModelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                NetworkManager.downloadTFModel(getApplicationContext());
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

        adjustInset();

    }

    @Override
    public void onResume() {
        if(autoUpdateTFCheckbox.isChecked())
            checkWritePermission();
        if(scanBluetoothBeaconsCheckbox.isChecked())
            checkBluetoothPermission();
        super.onResume();
    }

    private void checkWritePermission(){
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(confActivity,
                    new String[]{
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                    },
                    1);
        }
    }

    private void checkBluetoothPermission(){
        boolean bluetooth = (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED);
        boolean bluetoothAdmin = (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED);
        boolean coarseLocation = (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED);
        if  (!bluetooth || !bluetoothAdmin || !coarseLocation){
            applyButton.setEnabled(false);
            ActivityCompat.requestPermissions(confActivity,
                    new String[]{
                            Manifest.permission.BLUETOOTH,
                            Manifest.permission.BLUETOOTH_ADMIN,
                            Manifest.permission.ACCESS_FINE_LOCATION
                    },
                    1);

        }
    }

    private void adjustInset() {
        if (getResources().getConfiguration().isScreenRound()) {
            int inset = (int)(FACTOR * getResources().getDisplayMetrics().widthPixels);
            View layout = (View) findViewById(R.id.confMainview);
            Log.d("conf", "set inset to " + inset);
            // inset*=2;
            layout.setPadding(inset, inset, inset, inset);
        }
    }

     @Override
     public void onRequestPermissionsResult(int requestCode,
                                            String permissions[], int[] grantResults) {
         Log.d("Sensorrecorder", "rc: " + requestCode +  "length: "+permissions.length + " gr: " + grantResults.length);

         if (requestCode == 1) {
             if (grantResults.length == 3) {
                 boolean bluetooth = grantResults[0] == PackageManager.PERMISSION_GRANTED;
                 boolean bluetoothAdmin = grantResults[1] == PackageManager.PERMISSION_GRANTED;
                 boolean fineLocation = grantResults[2] == PackageManager.PERMISSION_GRANTED;

                 if(bluetooth && bluetoothAdmin && fineLocation) {
                     scanBluetoothBeaconsCheckbox.setChecked(true);
                 } else {
                     scanBluetoothBeaconsCheckbox.setChecked(false);
                     // permission denied
                     Toast.makeText(this, getResources().getString(R.string.toast_permission_den), Toast.LENGTH_SHORT).show();
                 }
             } else if(grantResults.length == 1){
                 if (grantResults[0] == PackageManager.PERMISSION_DENIED){
                     autoUpdateTFCheckbox.setChecked(false);
                 }
             }
         }
         applyButton.setEnabled(true);
     }
}
