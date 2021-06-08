package unifr.sensorrecorder.BluetoothBeacons;

import android.bluetooth.BluetoothAdapter;
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
    /*
    BEACON_TX_POWER:
    The Ruuvi beacon data protocol states that tx power level is passed in broadcast data.
    However together with the distance formula these values gave unreasonable distances.
    The value below was measured using the app nRF Connect together with 6 Ruuvi beacons. The rssi
    graphs were observed over some time. The value range was roughly from -70 to -50.
    */
    private static final double BEACON_TX_POWER = -60;
    /*
    BEACON_ENVIRONMENTAL:
    Environmental factor for rssi-based beacon distance calculation. Range 2 - 4.
    */
    private static final double BEACON_ENVIRONMENTAL = 2.0;
    private static final double BEACON_MAX_DIST = 1.0;

    public static void start() {
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        byte[] data = new byte[24];
        byte[] mask = new byte[24];

        if ((bluetoothAdapter == null) || !bluetoothAdapter.isEnabled()) {
            return;
        }

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

        Log.d("beacons", "Start scanning");
        bluetoothAdapter.getBluetoothLeScanner().startScan(filters, scanSettings, scanCallback);
    }

    public static void stop() {
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if ((bluetoothAdapter == null) || !bluetoothAdapter.isEnabled()) {
            return;
        }

        Log.d("beacons", "Stop scanning");
        bluetoothAdapter.getBluetoothLeScanner().stopScan(scanCallback);
    }

    private static double getBeaconDistance(double rssi) {
        return Math.pow(10.0, ((BEACON_TX_POWER - rssi) / (10.0 * BEACON_ENVIRONMENTAL)));
    }

    private static final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            double distance = getBeaconDistance(result.getRssi());
            long timestamp = SystemClock.elapsedRealtimeNanos();
            String record = timestamp + "\t" + result.getRssi() + "\t" + result.getDevice().getAddress() + "\n";
            String log = "Found " + result.getDevice().getAddress()
                    + " (RSSI: " + result.getRssi()
                    + " -> Distance: " + distance + ")";
            if (distance > BEACON_MAX_DIST) {
                return;
            }
            Log.d("beacons", log);
            try {
                StaticDataProvider.getProcessor().writeBluetoothBeaconTimestamp(record);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    };
}
