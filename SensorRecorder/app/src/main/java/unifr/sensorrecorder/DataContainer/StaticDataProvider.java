package unifr.sensorrecorder.DataContainer;

import android.app.Application;
import android.util.Log;

import unifr.sensorrecorder.Networking.NetworkManager;

/**
 * This class provides an instance of DataProcessor. It's acts like a singleton.
 * But singletons are evil in android. This is a work around.
 */
public class StaticDataProvider extends Application {
    private static DataProcessor dataProcessor;
    private static NetworkManager networkManager;

    @Override
    public void onCreate() {
        super.onCreate();
        dataProcessor = new DataProcessor();
        networkManager = new NetworkManager();
    }

    public static DataProcessor getProcessor(){
        return dataProcessor;
    }
    public static NetworkManager getNetworkManager(){
        return networkManager;
    }

}
