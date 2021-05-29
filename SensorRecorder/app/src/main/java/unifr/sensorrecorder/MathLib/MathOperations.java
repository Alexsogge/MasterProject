package unifr.sensorrecorder.MathLib;

import android.util.Log;

import java.util.Collection;

public class MathOperations {

    public static float mean(Collection<Float> values){
        float sum = 0;
        for (float value : values) {
            sum += value;
        }
        return sum / values.size();
    }

}
