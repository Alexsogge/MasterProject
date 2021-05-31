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
    private static int counterComplicationId;
    private static int overallReminderCalls;

    @Override
    public void onCreate() {
        super.onCreate();
        dataProcessor = new DataProcessor();
        networkManager = new NetworkManager();
        overallReminderCalls = 0;
    }

    public static DataProcessor getProcessor(){
        return dataProcessor;
    }
    public static NetworkManager getNetworkManager(){
        return networkManager;
    }
    public static int getCounterComplicationId(){
        return counterComplicationId;
    }
    public static void setCounterComplicationId(int counterComplicationId){
        StaticDataProvider.counterComplicationId = counterComplicationId;
    }

    public static int getOverallReminderCalls(){
        return overallReminderCalls;
    }
    public static void setOverallReminderCalls(int overallReminderCalls){
        StaticDataProvider.overallReminderCalls = overallReminderCalls;
    }

}
