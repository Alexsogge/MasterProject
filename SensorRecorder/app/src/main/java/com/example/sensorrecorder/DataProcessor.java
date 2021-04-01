package com.example.sensorrecorder;

import android.util.Log;

import com.example.sensorrecorder.dataContainer.DataContainer;
import com.example.sensorrecorder.dataContainer.OutputStreamContainer;
import com.example.sensorrecorder.dataContainer.ZipContainer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipOutputStream;

public class DataProcessor {


    public HashMap<String, ZipContainer> sensorContainers;
    public DataContainer containerMKV;
    public OutputStreamContainer containerHandWashTimeStamps;
    public DataContainer containerMic;
    public OutputStreamContainer containerMicTimeStamps;
    public OutputStreamContainer containerBattery;
    public OutputStreamContainer containerPrediction;

    public ArrayList<DataContainer> allDataContainers;
    public ArrayList<OutputStreamContainer> streamContainers;

    public DataProcessor(){
        sensorContainers = new HashMap<>();
        allDataContainers = new ArrayList<DataContainer>();
        streamContainers = new ArrayList<OutputStreamContainer>();
    }

    public void loadDefaultContainers() throws IOException {
        sensorContainers.clear();
        allDataContainers.clear();
        streamContainers.clear();

        containerMKV = new DataContainer("sensor_recording_android", "mkv");
        allDataContainers.add(containerMKV);

        containerHandWashTimeStamps = new OutputStreamContainer("sensor_recording_hand_wash_time_stamps", "csv");
        streamContainers.add(containerHandWashTimeStamps);

        containerMicTimeStamps = new OutputStreamContainer("sensor_recording_mic_time_stamps", "csv");
        streamContainers.add(containerMicTimeStamps);

        containerMic = new ZipContainer("sensor_recording_mic", "zip");
        allDataContainers.add(containerMic);

        containerBattery = new OutputStreamContainer("sensor_recording_battery", "csv");
        streamContainers.add(containerBattery);

        containerPrediction = new OutputStreamContainer("sensor_recording_prediction", "csv");
        streamContainers.add(containerPrediction);
        // add stream containers to all
        allDataContainers.addAll(streamContainers);
    }

    public void addSensorContainer(String sensorName) throws IOException {
        String filename = sensorName.replace('.', '_');
        ZipContainer sensorContainer = new ZipContainer(filename, "csv");
        sensorContainers.put(sensorName, sensorContainer);
        streamContainers.add(sensorContainer);
        allDataContainers.add(sensorContainer);
    }

    public void closeSensorStreams() throws IOException {
        for(ZipContainer container: sensorContainers.values()){
            container.close();
        }
    }

    public void activateSensorContainers() throws IOException {
        for(ZipContainer container: sensorContainers.values()){
            container.setActive();
        }
    }

    public void activateDefaultContainer(boolean useMKVStream, boolean useMic) throws IOException {
        if(useMKVStream)
            containerMKV.setActive();

        if(useMic) {
            containerMic.setActive();
            containerMicTimeStamps.setActive();
        }

        containerHandWashTimeStamps.setActive();
        containerBattery.setActive();
        containerPrediction.setActive();
    }

    public void deactivateAllContainer(){
        for(DataContainer container: allDataContainers){
            container.deactivate();
        }
    }

    public void flushAllContainer(){
        for(OutputStreamContainer container: streamContainers){
            try {
                container.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void backup_recording_files(){
        String subDirectory = DataContainer.getNewRecordSubdirectory();
        for(DataContainer container: allDataContainers){
            container.backupFile(subDirectory);
        }
    }

    public void openFileStream(){
        try {
            for (OutputStreamContainer container: streamContainers) {
                container.openStream();
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void writeBatteryTS(String line) throws IOException {
        containerBattery.writeData(line);
    }

    public void writeHandWashTS(String line) throws IOException {
        containerHandWashTimeStamps.writeData(line);
    }

    public void writePrediction(String line) throws IOException {
        containerPrediction.writeData(line);
    }

    public void writeMicTS(String line) throws IOException {
        Log.d("dp", "new mic ts " + line);
        containerMicTimeStamps.writeData(line);
    }

    public void writeSensorData(String sensorName, String line) throws IOException {
        sensorContainers.get(sensorName).writeData(line);
    }

    public void packMicFilesIntoZip() throws IOException {
        FileOutputStream fileOut = new FileOutputStream(containerMic.recordingFile);
        ZipOutputStream zipOut = new ZipOutputStream(fileOut);

        for (int i = 0; i < 9999; i++) {
            File tmp_file = new File(containerMic.recordingFilePath, containerMic.name + "_" + i + ".3gp");
            if (!tmp_file.exists())
                continue;
            FileInputStream fis = new FileInputStream(tmp_file);
            ZipEntry zipEntry = new ZipEntry(tmp_file.getName());
            zipOut.putNextEntry(zipEntry);

            byte[] bytes = new byte[1024];
            int length;
            while((length = fis.read(bytes)) >= 0) {
                zipOut.write(bytes, 0, length);
            }
            fis.close();
            tmp_file.delete();
        }

        try {zipOut.close();}
        catch (ZipException e){
            fileOut.close();
            containerMic.delete();
        }
        fileOut.close();
    }

}
