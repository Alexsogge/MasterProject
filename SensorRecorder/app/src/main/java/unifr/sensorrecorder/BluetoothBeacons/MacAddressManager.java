package unifr.sensorrecorder.BluetoothBeacons;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class MacAddressManager {
    // Map to store MAC address and name associations
    private final Map<String, String> macAddressMap = new HashMap<>();

    // Pattern to validate MAC address format
    private static final Pattern MAC_ADDRESS_PATTERN =
            Pattern.compile("^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$");

    // Method to add a MAC address and name association
    public void addMacAddress(String macAddress, String name) {
        if (isValidMacAddress(macAddress)) {
            macAddressMap.put(macAddress, name);
        } else {
            throw new IllegalArgumentException("Invalid MAC address format.");
        }
    }

    // Method to get the name associated with a MAC address
    public String getName(String macAddress) {
        return macAddressMap.get(macAddress);
    }

    // Method to remove a MAC address association
    public void removeMacAddress(String macAddress) {
        macAddressMap.remove(macAddress);
    }

    // Method to check if a MAC address is valid
    private boolean isValidMacAddress(String macAddress) {
        return MAC_ADDRESS_PATTERN.matcher(macAddress).matches();
    }

    // Method to list all MAC addresses and their associated names
    public Map<String, String> listAll() {
        return new HashMap<>(macAddressMap);
    }

    // Method to check if a MAC address exists in the map
    public boolean macAddressExists(String macAddress) {
        return macAddressMap.containsKey(macAddress);
    }


    public static void main(String[] args) {
        MacAddressManager manager = new MacAddressManager();

        // Adding MAC addresses and names
        manager.addMacAddress("FA:0D:95:46:03:95", "Coffee 1"); // light brown
        manager.addMacAddress("FB:41:70:6A:5E:5D", "Coffee 2");

        manager.addMacAddress("D2:4C:CA:39:05:73", "Bee 1"); // yellow
        manager.addMacAddress("E5:CE:4B:AB:FC:EB", "Bee 2");

        manager.addMacAddress("E8:F9:34:0A:03:49", "Snow 1"); // white
        manager.addMacAddress("DF:C2:8F:DD:F7:46", "Snow 2");

        manager.addMacAddress("E6:30:38:43:7E:F1", "Mint 1"); // green

    }
}

