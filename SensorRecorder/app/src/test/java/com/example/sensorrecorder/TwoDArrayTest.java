package unifr.sensorrecorder;




import unifr.sensorrecorder.MathLib.TwoDArray;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;


public class TwoDArrayTest {


    @Test
    public void mean_isCorrect() throws Exception {
        TwoDArray array = getTestArray();
        float[] mean = array.mean();
        float[] actuals = new float[]{4.194017f, 0.32526981f, 7.34461078f};
        assertArrayEquals(mean, actuals, 0.00001f);
    }

    @Test
    public void var_isCorrect() throws Exception {
        TwoDArray array = getTestArray();
        float[] var = array.var();
        float[] actuals = new float[]{14.88969009f,  0.13474844f, 10.94082235f};
        assertArrayEquals(var, actuals, 0.00001f);
    }

    @Test
    public void rootMeanSquare_isCorrect() throws Exception {
        TwoDArray array = getTestArray();
        float[] rms = array.rootMeanSquare();
        float[] actuals = new float[]{5.69907612f, 0.49045784f, 8.05506858f};
        assertArrayEquals(rms, actuals, 0.00001f);
    }

    @Test
    public void median_isCorrect() throws Exception {
        TwoDArray array = getTestArray();
        float[] med = array.median();
        float[] actuals = new float[]{3.113922f, 0.2353363f, 9.042572f};
        assertArrayEquals(med, actuals, 0.00001f);
    }

    @Test
    public void percentile_isCorrect() throws Exception {
        TwoDArray array = getTestArray();
        float[] firstQuart = array.percentile(25);
        float[] actuals = new float[]{0.77612305f, 0.11569214f, 4.26650235f};
        assertArrayEquals(firstQuart, actuals, 0.00001f);
        float[] thirdQuart = array.percentile(75);
        actuals = new float[]{7.1039428f, 0.35736084f, 10.1217345f};
        assertArrayEquals(thirdQuart, actuals, 0.00001f);
    }

    @Test
    public void min_isCorrect() throws Exception {
        TwoDArray array = getTestArray();
        float[] min = array.min();
        float[] actuals = new float[]{-0.16903687f, -0.12359619f, 1.7540436f};
        assertArrayEquals(min, actuals, 0.00001f);
    }

    @Test
    public void max_isCorrect() throws Exception {
        TwoDArray array = getTestArray();
        float[] max = array.max();
        float[] actuals = new float[]{11.955399f, 1.623169f, 11.02623f};
        assertArrayEquals(max, actuals, 0.00001f);
    }

    @Test
    public void skewness_isCorrect() throws Exception {
        TwoDArray array = getTestArray();
        float[] skew = array.skewness();
        float[] actuals = new float[]{ 0.50277814f, 2.00582121f, -0.62062751f};
        assertArrayEquals(skew, actuals, 0.00001f);
    }

    @Test
    public void kurtosis_isCorrect() throws Exception {
        TwoDArray array = getTestArray();
        float[] kurt = array.kurtosis();
        float[] actuals = new float[]{-1.0606044f, 3.88086207f, -1.25967141f};
        assertArrayEquals(kurt, actuals, 0.00001f);
    }

    @Test
    public void covariance_isCorrect() throws Exception {
        TwoDArray array = getTestArray();
        float[][] cov = array.covariance();
        float[][] actuals = new float[][]{{14.88969009f, 0.66667964f, -11.98120594f},
                                            {0.66667964f, 0.13474844f, -0.60241036f},
                                            {-11.98120594f, -0.60241036f, 10.94082235f}};
        assertArrayEquals(cov[0], actuals[0], 0.00001f);
        assertArrayEquals(cov[1], actuals[1], 0.00001f);
        assertArrayEquals(cov[2], actuals[2], 0.00001f);
    }

    @Test
    public void allCovariance_isCorrect() throws Exception {
        TwoDArray array = getTestArray();
        float[] cov_all = array.allCovariance();
        float[] actuals = new float[]{0.66667964f, -0.60241036f, -11.98120594f};
        assertArrayEquals(cov_all, actuals, 0.00001f);
    }

    @Test
    public void allFeatures_isCorrect() throws Exception {
        TwoDArray array = getTestArray();
        System.out.println(array.featuresToText());
    }


    public TwoDArray getTestArray(){

        float[][] rawArr = new float[][]{{-0.11160278f, 0.16593933f, 9.650345f},
                {-0.12835693f, 0.18507385f, 9.6527405f},
                {-0.11639404f, 0.20422363f, 9.63121f},
                {-0.12358093f, 0.17790222f, 9.643173f},
                {-0.12835693f, 0.22335815f, 9.659927f},
                {-0.07810974f, 0.14918518f, 9.604889f},
                {-0.15467834f, 0.13005066f, 9.707779f},
                {-0.16903687f, 0.13722229f, 9.791534f},
                {-0.1211853f, 0.11569214f, 10.014069f},
                {0.15638733f, 0.048690796f, 10.45195f},
                {0.5320587f, -0.12359619f, 10.274872f},
                {0.66845703f, -0.011138916f, 10.53569f},
                {0.9268799f, 0.3669281f, 11.02623f},
                {0.9603729f, 0.11090088f, 10.892227f},
                {0.7737274f, 0.29275513f, 10.880264f},
                {0.7785187f, 0.06782532f, 10.45195f},
                {0.7928772f, -0.1116333f, 10.282059f},
                {1.1589813f, 0.11569214f, 10.157623f},
                {1.4628601f, 0.14439392f, 10.55484f},
                {1.6327515f, 0.28796387f, 10.363419f},
                {1.3623657f, 0.26643372f, 10.4328f},
                {1.5490112f, 0.32626343f, 10.296417f},
                {2.4630737f, 0.08457947f, 10.085846f},
                {2.788498f, -0.032669067f, 9.829819f},
                {2.735855f, 0.10610962f, 9.451752f},
                {3.113922f, 0.055862427f, 9.042572f},
                {3.6140137f, 0.20422363f, 8.765015f},
                {3.7121277f, 0.2401123f, 8.592728f},
                {5.032959f, 0.012802124f, 7.9705963f},
                {5.8560944f, 0.24490356f, 7.8820496f},
                {5.5689545f, 0.31907654f, 7.611664f},
                {5.1047363f, 0.3932495f, 7.255142f},
                {6.5308685f, 0.30471802f, 6.4056854f},
                {6.7988586f, 0.9531708f, 6.329117f},
                {6.1001587f, 1.623169f, 6.3195496f},
                {6.5523987f, 0.2353363f, 5.532303f},
                {7.409027f, 0.6923523f, 4.484253f},
                {7.4712524f, 1.204422f, 4.3023987f},
                {6.16716f, 0.6181793f, 4.230606f},
                {5.348816f, 0.31428528f, 3.0940247f},
                {7.5262756f, 0.24250793f, 1.9406738f},
                {8.564758f, 1.5394135f, 2.4694977f},
                {8.2608795f, 1.1326447f, 2.7279205f},
                {8.976334f, 0.3238678f, 1.7636108f},
                {9.718109f, 0.34779358f, 1.7540436f},
                {10.067459f, 0.5128937f, 2.0220337f},
                {10.6441345f, 0.51049805f, 1.9957123f},
                {11.438538f, 0.44589233f, 2.1536407f},
                {11.955399f, 0.47460938f, 2.3211365f},
                {11.622787f, 0.12286377f, 2.0914307f},
                {11.127472f, 0.09176636f, 2.1943207f}};



        TwoDArray arr = new TwoDArray(rawArr);
        return arr;
    }

}
