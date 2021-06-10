package unifr.sensorrecorder;

interface SensorManagerInterface {

    void flushBuffer(int sensorIndex, float[][] buffer, long[] timestamps);
    void startMicRecording(long timestamp);
}
