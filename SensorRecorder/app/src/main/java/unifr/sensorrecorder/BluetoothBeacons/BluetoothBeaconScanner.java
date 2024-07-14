package unifr.sensorrecorder.BluetoothBeacons;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.os.ParcelUuid;
import android.os.SystemClock;
import android.util.Log;

import com.google.android.gms.common.util.Hex;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import unifr.sensorrecorder.DataContainer.StaticDataProvider;

/*
Resources:
- Making Android BLE work (https://medium.com/@martijn.van.welie/making-android-ble-work-part-1-a736dcd53b02)
- Beacon scanning in background (https://stackoverflow.com/questions/51371372/beacon-scanning-in-background-android-o)
- Ruuvi beacon protocol information (https://github.com/ruuvi/ruuvi-sensor-protocols/blob/master/broadcast_formats.md)
- Calculate distance from RSSI (https://iotandelectronics.wordpress.com/2016/10/07/how-to-calculate-distance-from-the-rssi-value-of-the-ble-beacon/)
*/

public class BluetoothBeaconScanner<uniqueNames> {
    /*
    BEACON_TX_POWER:
    The Ruuvi beacon data protocol states that tx power level is passed in broadcast data.
    However together with the distance formula these values gave unreasonable distances.
    The value below was measured using the app nRF Connect together with 6 Ruuvi beacons. The rssi
    graphs were observed over some time. The value range was roughly from -70 to -50.
    */
    private static final double BEACON_TX_POWER = -73;
    // private static final double BEACON_TX_POWER = 127; // for estimote beacons
    /*
    BEACON_ENVIRONMENTAL:
    Environmental factor for rssi-based beacon distance calculation. Range 2 - 4.
    Free space: 2
    Urban area: 2.7 to 3.5
    Indoor with obstacles: 3 to 4

    */
    private static final double BEACON_ENVIRONMENTAL = 3.0;
    private static final double BEACON_MAX_DIST = 4.0;
    protected static UUID serviceUUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E");
    protected static UUID estimoteServiceID = UUID.fromString("0000FE9A-0000-1000-8000-00805F9B34FB");
    protected static MacAddressManager manager = new MacAddressManager();
    static List<String> uniqueNames = new ArrayList<>();

    static List<UUID> serviceUUIDsList        = new ArrayList<>();
    List<UUID> characteristicUUIDsList = new ArrayList<>();
    List<UUID> descriptorUUIDsList     = new ArrayList<>();

    public static void start() {
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        byte[] data = new byte[24];
        byte[] mask = new byte[24];

        initalizeEstimoteBeacons(manager);

        if ((bluetoothAdapter == null) || !bluetoothAdapter.isEnabled()) {
            Log.d("", "no BLE enabled");
            return;
        }

        // Scan filter to only get callback calls for Ruuvi beacons
//        Arrays.fill(data, (byte)0);
//        data[0] = (byte)5;
//        Arrays.fill(mask, (byte)0);
//        mask[0] = (byte)1;
//        ScanFilter scanFilter = new ScanFilter.Builder()
//                .setManufacturerData(1177, data, mask)
//                .build();

        List<ScanFilter> filters = new ArrayList<>();
        ScanFilter filter = new ScanFilter.Builder().setServiceUuid(new ParcelUuid(estimoteServiceID)).build();
        filters.add(filter);

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
        //bluetoothAdapter.getBluetoothLeScanner().startScan(null, scanSettings, scanCallback);
    }

    public static void stop() {
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if ((bluetoothAdapter == null) || !bluetoothAdapter.isEnabled()) {
            return;
        }

        Log.d("beacons", "Stop scanning");
        bluetoothAdapter.getBluetoothLeScanner().stopScan(scanCallback);
        Log.d("beacons", "Found " + uniqueNames.size() +" beacons!");
    }

    private static double getBeaconDistance(double rssi) {
        return Math.pow(10.0, ((BEACON_TX_POWER - rssi) / (10.0 * BEACON_ENVIRONMENTAL)));
    }

    private static void initalizeEstimoteBeacons(MacAddressManager manager){
        manager.addMacAddress("FA:0D:95:46:03:95", "Coffee 1"); // light brown
        manager.addMacAddress("FB:41:70:6A:5E:5D", "Coffee 2");

        manager.addMacAddress("D2:4C:CA:39:05:73", "Bee 1"); // yellow
        manager.addMacAddress("E5:CE:4B:AB:FC:EB", "Bee 2");

        manager.addMacAddress("E8:F9:34:0A:03:49", "Snow 1"); // white
        manager.addMacAddress("DF:C2:8F:DD:F7:46", "Snow 2");
        manager.addMacAddress("CE:9F:C5:7D:4C:FE", "Snow 3"); // (not reliable)

        manager.addMacAddress("E6:30:38:43:7E:F1", "Mint 1"); // green

        manager.addMacAddress("CD:9E:C4:7C:4B:FD", "Sky 1"); // light blue

        manager.addMacAddress("CA:9B:C1:79:48:FA", "Storm 1"); // dark blue (no reliable)
    }

    public static String byteArrayToHex(byte[] a) {
        StringBuilder sb = new StringBuilder(a.length * 2);
        for(byte b: a)
            sb.append(String.format("%02x", b));
        return sb.toString();
    }


    private static final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            double distance = getBeaconDistance(result.getRssi());
            long timestamp = SystemClock.elapsedRealtimeNanos();

            String distanceRounded = String.format(Locale.US,"%.2f", distance);
            String record = timestamp + "\t" + result.getRssi() + "\t"
                    + distanceRounded + "\t" + result.getDevice().getAddress() + "\n";
            byte[] r = result.getScanRecord().getServiceData(new ParcelUuid(estimoteServiceID));

            Log.d("beacons", "service data: " + byteArrayToHex(r));


            /*if (distance > BEACON_MAX_DIST) {
                Log.d("beacons", "Too far away!");
                return;
            }*/
            if  (manager.macAddressExists(result.getDevice().getAddress())){
                String name = manager.getName(result.getDevice().getAddress());
                if (!uniqueNames.contains(name)) {
                    uniqueNames.add(name);
                }
                String log = name + " found (" + result.getDevice().getAddress() + ")  "
                        + " => RSSI: " + result.getRssi()
                        + " & Distance: " + distanceRounded;

                Log.d("beacons", log);
            } else {
                String log = "New beacon found (" + result.getDevice().getAddress() + ")  "
                        + " => RSSI: " + result.getRssi()
                        + " & Distance: " + distanceRounded;

                Log.d("beacons", log);
            }
            try {
                StaticDataProvider.getProcessor().writeBluetoothBeaconTimestamp(record);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    };
}