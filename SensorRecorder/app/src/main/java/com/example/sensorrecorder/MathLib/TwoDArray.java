package com.example.sensorrecorder.MathLib;

import java.util.Arrays;

public class TwoDArray {

    public int[] shape;
    public float[][] data;
    public float[][] dataTransposed;
    public float[][] dataSorted;

    private float[] _mean = null;
    private float[] _var = null;

    public TwoDArray(int rows, int columns){
        shape = new int[]{rows, columns};
        data = new float[shape[0]][shape[1]];
    }

    public TwoDArray(int[] shape){
        this(shape[0], shape[1]);
    }

    public TwoDArray(float[][] input){
        if (input.length == 0){
            shape = new int[]{0,0};
            return;
        }

        shape = new int[]{input.length, input[0].length};
        data = new float[shape[0]][shape[1]];
        dataTransposed = new float[shape[1]][shape[0]];
        dataSorted = new float[shape[0]][shape[1]];
        init(input);
    }


    public void init(float[][] input){
        float[][] dataTransposedSorted = new float[shape[1]][shape[0]];
        for(int j = 0; j < shape[1]; j++){
            for(int i = 0; i < shape[0]; i++){
                data[i][j] = input[i][j];
                dataTransposed[j][i] = input[i][j];
                dataTransposedSorted[j][i] = input[i][j];
            }
        }
        for(int j=0; j < shape[1]; j++){
            Arrays.sort(dataTransposedSorted[j]);
        }
        for(int j = 0; j < shape[1]; j++){
            for(int i = 0; i < shape[0]; i++){
               dataSorted[i][j] = dataTransposedSorted[j][i];
            }
        }
    }

    public float get(int row, int col){
        return data[row][col];
    }

    public void put(int row, int col, float val){
        data[row][col] = val;
    }


    public float[] getMean(){
        if(_mean == null)
            _mean = mean();

        return _mean;
    }

    public float[] getVariance(){
        if(_var == null)
            _var = var();
        return _var;
    }

    public float[] mean(){
        return mean(data);
    }

    public float[] mean(float[][] data){
        float[] meanVal = new float[shape[1]];
        for (int i = 0; i < shape[0]; i++){
            for(int j = 0; j < shape[1]; j++){
                meanVal[j] += data[i][j];
            }
        }
        for(int j = 0; j < shape[1]; j++){
            meanVal[j] /= shape[0];
        }

        return meanVal;
    }

    public float[] var(){
        float[] variance = new float[shape[1]];
        float[] meanVal = mean();


        for(int i = 0; i < shape[0]; i++){
            for(int j = 0; j < shape[1]; j++){
                variance[j] += Math.pow(Math.abs(data[i][j] - meanVal[j]), 2 );
            }
        }

        for(int j = 0; j < shape[1]; j++){
            variance[j] /= shape[0];
        }

        return variance;
    }

    public float[] rootMeanSquare(){
        float[] rootMeanVal = new float[shape[1]];
        for (int i = 0; i < shape[0]; i++){
            for(int j = 0; j < shape[1]; j++){
                rootMeanVal[j] += Math.pow(data[i][j], 2);
            }
        }
        for(int j = 0; j < shape[1]; j++){
            rootMeanVal[j] = (float) Math.sqrt(rootMeanVal[j] / shape[0]);
        }
        return rootMeanVal;
    }

    public float[] median(){
        int middlePoint = (shape[0]-1)/2;
        float[] med = new float[shape[1]];

        for(int j = 0; j < shape[1]; j++){
            med[j] = dataSorted[middlePoint][j];
        }
        return med;
    }

    public float[] percentile(float q){
        float[] perc = new float[shape[1]];
        float p = q/100;
        float point = p * (shape[0]-1);
        if (point%1 == 0){
            for(int j = 0; j < shape[1]; j++){
                perc[j] = dataSorted[(int)point][j];
            }
        } else {
            int lowerPoint = (int)point;
            int upperPoint = lowerPoint + 1;
            float frac = point - lowerPoint;
            for(int j = 0; j < shape[1]; j++){
                float lowerVal = dataSorted[lowerPoint][j];
                float upperVal = dataSorted[upperPoint][j];
                perc[j] = ((upperVal - lowerVal)*frac) + lowerVal;
            }
        }

        return perc;
    }

    public float[] min(){
        float[] mins = new float[shape[1]];
        for(int j = 0; j < shape[1]; j++){
            mins[j] = dataSorted[0][j];
        }
        return mins;
    }

    public float[] max(){
        float[] maxs = new float[shape[1]];
        for(int j = 0; j < shape[1]; j++){
            maxs[j] = dataSorted[shape[0]-1][j];
        }
        return maxs;
    }


    public float[] skewness(){
        float[] skew = new float[shape[1]];
        float[] means = getMean();
        float[] vars = getVariance();

        for(int j = 0; j < shape[1]; j++){
            float m2 = vars[j];
            float m3 = 0;
            for (int i = 0; i < shape[0]; i++){
                //m2 += Math.pow((data[i][j]-means[j]), 2);
                m3 += Math.pow((data[i][j]-means[j]), 3);
            }
            //m2 /= shape[0];
            m3 /= shape[0];
            skew[j] = (float)(m3/Math.pow(m2, 3.0/2.0));
        }
        return skew;
    }

    public float[] kurtosis(){
        float[] kurt = new float[shape[1]];
        float[] means = mean();
        float[] vars = getVariance();

        for(int j = 0; j < shape[1]; j++){
            float m4 = 0;
            float m2 = vars[j];
            for (int i = 0; i < shape[0]; i++){
                // m4 += data[i][j]- means[j];
                m4 += Math.pow((data[i][j]-means[j]), 4);
                //m2 += Math.pow((data[i][j]-means[j]), 2);
            }
            // m4 = (float)Math.pow(m4, 4)/shape[0];
            m4 /= shape[0];
            //m2 /= shape[0];
            kurt[j] = (float)(m4/Math.pow(m2, 2)) - 3;
        }
        return kurt;
    }

    public float[][] covariance(){
        float[][] covMat = new float[shape[1]][shape[1]];
        float[] means = getMean();

        for(int r = 0; r < shape[1]; r++){
            for(int c = r; c < shape[1]; c++){
                float val = 0;
                for(int i = 0; i < shape[0]; i++){
                    val += (data[i][r] - means[r]) * (data[i][c] - means[c]);
                }
                val /= shape[0];
                covMat[r][c] = val;
                covMat[c][r] = val;
            }
        }
        return covMat;
    }

    public float[] allCovariance(){
        float[][] covMat = covariance();
        return new float[]{covMat[0][1], covMat[1][2], covMat[2][0]};
    }

    public float[] allFeatures(){
        float[] means = getMean();
        float[] var = getVariance();
        float[] rms = rootMeanSquare();
        float[] median = median();
        float[] firstQuartile = percentile(25);
        float[] thirdQuartile = percentile(75);
        float[] minimum = min();
        float[] maximum = max();
        float[] skewness = skewness();
        float[] kurtosis = kurtosis();
        float[] covAll = allCovariance();

        int numFeatures = 11;
        float[] features = new float[numFeatures * shape[1]];
        for (int j = 0; j < shape[1]; j++){
            features[j] = means[j];
            features[j + shape[1]] = var[j];
            features[j + shape[1]*2] = rms[j];
            features[j + shape[1]*3] = median[j];
            features[j + shape[1]*4] = firstQuartile[j];
            features[j + shape[1]*5] = thirdQuartile[j];
            features[j + shape[1]*6] = minimum[j];
            features[j + shape[1]*7] = maximum[j];
            features[j + shape[1]*8] = skewness[j];
            features[j + shape[1]*9] = kurtosis[j];
            features[j + shape[1]*10] = covAll[j];
        }

        return features;
    }

    public String featuresToText(){
        return featuresToText(allFeatures());
    }

    public String featuresToText(float[] features){
        StringBuilder text = new StringBuilder();
        for (float feature : features) {
            text.append(feature).append(", ");
        }
        return text.toString();
    }

}
