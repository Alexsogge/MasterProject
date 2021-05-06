package unifr.sensorrecorder.DataContainer;

import android.app.Application;
import android.util.Log;

/**
 * This class provides an instance of DataProcessor. It's acts like a singleton.
 * But singletons are evil in android. This is a work around.
 */
public class DataProcessorProvider extends Application {
    private static DataProcessor dataProcessor;

    @Override
    public void onCreate() {
        super.onCreate();
        dataProcessor = new DataProcessor();
    }

    public static DataProcessor getProcessor(){
        return dataProcessor;
    }
}
