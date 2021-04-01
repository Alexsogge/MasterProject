package com.example.sensorrecorder;

interface SensorManagerInterface {

    public void flushBuffer(int sensorIndex, float[][] buffer, long[] timestamps);
    public void startMicRecording(long timestamp);
}
