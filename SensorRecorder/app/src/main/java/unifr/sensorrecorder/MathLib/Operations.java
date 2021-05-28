package unifr.sensorrecorder.MathLib;

import java.util.Collection;

public class Operations {

    public static float mean(Collection<Float> values){
        float sum = 0;
        for (float value : values) {
            sum += value;
        }
        return sum / values.size();
    }

}
