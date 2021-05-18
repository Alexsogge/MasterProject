package unifr.sensorrecorder.BluetoothBeacons;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.os.SystemClock;
import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import unifr.sensorrecorder.DataContainer.StaticDataProvider;

/*
Resources:
- Making Android BLE work (https://medium.com/@martijn.van.welie/making-android-ble-work-part-1-a736dcd53b02)
- Beacon scanning in background (https://stackoverflow.com/questions/51371372/beacon-scanning-in-background-android-o)
- Ruuvi beacon protocol information (https://github.com/ruuvi/ruuvi-sensor-protocols/blob/master/broadcast_formats.md)
- Calculate distance from RSSI (https://iotandelectronics.wordpress.com/2016/10/07/how-to-calculate-distance-from-the-rssi-value-of-the-ble-beacon/)
*/

public class BluetoothBeaconScanner {
    private static final BluetoothBeaconScanner mInstance = new BluetoothBeaconScanner();

    private BluetoothLeScanner mLeScanner;
    private boolean mIsScanning;

    private BluetoothBeaconScanner() {
        mLeScanner = BluetoothAdapter.getDefaultAdapter().getBluetoothLeScanner();
        mIsScanning = false;
    }

    public static BluetoothBeaconScanner getInstance() {
        return mInstance;
    }

    public void start() {
        byte[] data = new byte[24];
        byte[] mask = new byte[24];

        if (mIsScanning) {
            return;
        }
        Log.d("beacons", "Start scanning");

        // Scan filter to only get callback calls for Ruuvi beacons
        Arrays.fill(data, (byte)0);
        data[0] = (byte)5;
        Arrays.fill(mask, (byte)0);
        mask[0] = (byte)1;
        ScanFilter scanFilter = new ScanFilter.Builder()
                .setManufacturerData(1177, data, mask)
                .build();
        List<ScanFilter> filters = new ArrayList<>();
        filters.add(scanFilter);

        // Scan settings
        ScanSettings scanSettings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
                .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
                .setNumOfMatches(ScanSettings.MATCH_NUM_ONE_ADVERTISEMENT)
                .setReportDelay(0L)
                .build();

        mLeScanner.startScan(filters, scanSettings, scanCallback);
        mIsScanning = true;
    }

    public void stop() {
        if (!mIsScanning) {
            return;
        }
        Log.d("beacons", "Stop scanning");
        mLeScanner.stopScan(scanCallback);
        mIsScanning = false;
    }

    private double distanceFromRssi(int rssi) {
        double measuredPower = -69.0;
        double n = 2.0;
        return Math.pow(10.0, ((measuredPower - rssi) / (10.0 * n)));
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            double distance = distanceFromRssi(result.getRssi());
            long timestamp = SystemClock.elapsedRealtimeNanos();
            String record = timestamp + "\t" + result.getDevice().getAddress() + "\n";
            String log = "Found " + result.getDevice().getAddress()
                    + " (RSSI: " + result.getRssi()
                    + " -> Distance: " + distance + ")";
            Log.d("beacons", log);
            try {
                StaticDataProvider.getProcessor().writeBluetoothBeaconTimestamp(record);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    };
}
