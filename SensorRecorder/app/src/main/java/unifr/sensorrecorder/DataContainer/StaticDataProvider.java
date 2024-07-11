package unifr.sensorrecorder.DataContainer;

import android.app.Application;
import android.os.Environment;

import java.io.File;

import unifr.sensorrecorder.Networking.NetworkManager;

/**
 * This class provides an instance of DataProcessor. It's acts like a singleton.
 * But singletons are evil in android. This is a work around.
 */
public class StaticDataProvider extends Application {
    private static DataProcessor dataProcessor;
    private static NetworkManager networkManager;
    private static int counterComplicationId;
    private static boolean isRunning;

    @Override
    public void onCreate() {
        super.onCreate();
        dataProcessor = new DataProcessor(
                new File(getExternalFilesDir(Environment.DIRECTORY_DCIM), "android_sensor_recorder"));
        networkManager = new NetworkManager();
        isRunning = false;
    }

    public static DataProcessor getProcessor() { return dataProcessor; }
    public static NetworkManager getNetworkManager(){
        return networkManager;
    }
    public static int getCounterComplicationId(){
        return counterComplicationId;
    }
    public static boolean getIsRunning(){return isRunning;}
    public static void setCounterComplicationId(int counterComplicationId){
        StaticDataProvider.counterComplicationId = counterComplicationId;
    }
    public static void setIsRunning(boolean isRunning){
        StaticDataProvider.isRunning = isRunning;
    }

}
