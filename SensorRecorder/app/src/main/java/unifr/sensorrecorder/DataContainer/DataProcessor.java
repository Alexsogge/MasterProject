package unifr.sensorrecorder.DataContainer;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.SystemClock;
import android.provider.Settings;

import org.json.JSONException;
import org.json.JSONObject;

import unifr.sensorrecorder.DataContainer.DataContainer;
import unifr.sensorrecorder.DataContainer.MultyEntryZipContainer;
import unifr.sensorrecorder.DataContainer.OutputStreamContainer;
import unifr.sensorrecorder.DataContainer.ZipContainer;
import unifr.sensorrecorder.HandWashDetection;
import unifr.sensorrecorder.R;
import unifr.sensorrecorder.SensorListenerService;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.TimeZone;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipOutputStream;

public class DataProcessor {
    public HashMap<String, ZipContainer> sensorContainers;
    //public MultyEntryZipContainer sensorContainer;
    public DataContainer containerMKV;
    public OutputStreamContainer containerHandWashTimeStamps;
    public OutputStreamContainer containerMarkerTimeStamps;
    public DataContainer containerMic;
    public OutputStreamContainer containerMicTimeStamps;
    public OutputStreamContainer containerBattery;
    public OutputStreamContainer containerPrediction;

    public OutputStreamContainer containerEvaluation;
    public OutputStreamContainer containerOverallEvaluation;

    public OutputStreamContainer containerBluetoothBeacons;

    public OutputStreamContainer containerMetaInfo;

    public ArrayList<DataContainer> allDataContainers;
    public ArrayList<OutputStreamContainer> streamContainers;

    public long lastEvaluationTS;
    public long lastPredictionTS;
    private long queuedPredictionTS = 0;
    public int predictions = 0;
    public int handWashes = 0;

    public DataProcessor(){
        sensorContainers = new HashMap<>();
        allDataContainers = new ArrayList<DataContainer>();
        streamContainers = new ArrayList<OutputStreamContainer>();
    }

    public void loadDefaultContainers() throws IOException {
        sensorContainers.clear();
        allDataContainers.clear();
        streamContainers.clear();

        //sensorContainer = new MultyEntryZipContainer("sensors", "csv");
        //streamContainers.add(sensorContainer);

        containerMKV = new DataContainer("ffmpeg", "mkv");
        allDataContainers.add(containerMKV);

        containerHandWashTimeStamps = new OutputStreamContainer("hand_wash_time_stamps", "csv");
        streamContainers.add(containerHandWashTimeStamps);

        containerMarkerTimeStamps = new OutputStreamContainer("marker_time_stamps", "csv");
        streamContainers.add(containerMarkerTimeStamps);

        containerMicTimeStamps = new OutputStreamContainer("mic_time_stamps", "csv");
        streamContainers.add(containerMicTimeStamps);

        containerMic = new ZipContainer("recording_mic", "zip");
        allDataContainers.add(containerMic);

        containerBattery = new OutputStreamContainer("battery", "csv");
        streamContainers.add(containerBattery);

        containerPrediction = new OutputStreamContainer("prediction", "csv");
        streamContainers.add(containerPrediction);

        containerEvaluation = new OutputStreamContainer("evaluation", "csv");
        streamContainers.add(containerEvaluation);

        containerOverallEvaluation = new OutputStreamContainer("overallEvaluation", "csv");
        allDataContainers.add(containerOverallEvaluation);

        containerBluetoothBeacons = new OutputStreamContainer("bluetoothBeacons", "csv");
        streamContainers.add(containerBluetoothBeacons);

        containerMetaInfo = new OutputStreamContainer("metaInfo", "json");
        streamContainers.add(containerMetaInfo);

        // add stream containers to all
        allDataContainers.addAll(streamContainers);
    }

    public void addSensorContainer(String sensorName) throws IOException {
        String filename = sensorName.replace('.', '_');
        ZipContainer sensorContainer = new ZipContainer(filename, "csv");
        sensorContainers.put(sensorName, sensorContainer);
        streamContainers.add(sensorContainer);
        allDataContainers.add(sensorContainer);
        //sensorContainer.addEntry(sensorName, filename);
    }

    public void closeSensorStreams() throws IOException {
        for(ZipContainer container: sensorContainers.values()){
            container.close();
        }

        //sensorContainer.close();
    }

    public void activateSensorContainers() throws IOException {
        for(ZipContainer container: sensorContainers.values()){
            container.setActive();
        }

        //sensorContainer.setActive();
    }

    public void activateDefaultContainer(boolean useMic) throws IOException {
        if(useMic) {
            containerMic.setActive();
            containerMicTimeStamps.setActive();
        }

        containerHandWashTimeStamps.setActive();
        containerMarkerTimeStamps.setActive();
        containerBattery.setActive();
        containerPrediction.setActive();
        containerEvaluation.setActive();
        containerBluetoothBeacons.setActive();
        containerMetaInfo.setActive();

        lastEvaluationTS = 0;
        lastPredictionTS = 0;
        predictions = 0;
        handWashes = 0;
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
        handWashes++;
    }

    public void writeMarker(String line) throws IOException {
        containerMarkerTimeStamps.writeData(line);
    }

    public void writePrediction(String line) throws IOException {
        containerPrediction.writeData(line);
    }

    public void writeMicTS(String line) throws IOException {
        containerMicTimeStamps.writeData(line);
    }

    public void writeSensorData(String sensorName, String line) throws IOException {
        sensorContainers.get(sensorName).writeData(line);
        //sensorContainer.writeData(sensorName, line);
    }

    public void queueEvaluation(long predictionTS) throws IOException{
        if (queuedPredictionTS != 0 && predictionTS != queuedPredictionTS){
            containerEvaluation.writeData(queuedPredictionTS + "\t" + -1 + "\n");
        }
        queuedPredictionTS = predictionTS;
    }

    public void writeEvaluation(String line, boolean isPrediction, long predictionTS) throws IOException {
        containerEvaluation.writeData(line);
        if(isPrediction) {
            if (lastPredictionTS != predictionTS)
                predictions++;
            lastPredictionTS = predictionTS;
        }
        queuedPredictionTS = 0;
    }

    public void writeEvaluation(String line, long timestamp) throws IOException {
        containerEvaluation.writeData(line);
        lastEvaluationTS = timestamp;
    }

    public void writeOverallEvaluation(String line) throws IOException {
        containerOverallEvaluation.setActive();
        containerOverallEvaluation.openStream();
        containerOverallEvaluation.writeData(line);
        containerOverallEvaluation.flush();
        containerOverallEvaluation.close();
    }

    public void writeBluetoothBeaconTimestamp(String line) throws IOException {
        containerBluetoothBeacons.writeData(line);
    }


    public void writeMetaInfo(Context context){
        SharedPreferences configs = context.getSharedPreferences(context.getString(R.string.configs), Context.MODE_PRIVATE);
        JSONObject metaInfo = new JSONObject();
        try {
            metaInfo.put("tf_model", configs.getString(context.getApplicationContext().getString(R.string.val_current_tf_model), "default.tflite"));
            JSONObject tfSettings = HandWashDetection.readModelSettingsFile();
            if (tfSettings != null)
                metaInfo.put("tf_settings", tfSettings.toString());

            PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            String version = pInfo.versionName;
            int verCode = pInfo.versionCode;
            metaInfo.put("app_version", version);
            metaInfo.put("version_code", verCode);
            metaInfo.put("date", getCurrentDateAsIso());
            metaInfo.put("start_time_stamp", SystemClock.elapsedRealtimeNanos());
            metaInfo.put("run_number", DataContainer.currentRun);

            metaInfo.put("build_board", Build.BOARD);
            metaInfo.put("build_device", Build.DEVICE);
            metaInfo.put("build_sdk", Build.VERSION.SDK_INT);
            metaInfo.put("android_id", Settings.Secure.getString( context.getContentResolver(), Settings.Secure.ANDROID_ID));

            containerMetaInfo.writeData(metaInfo.toString(4));

        } catch (JSONException | PackageManager.NameNotFoundException | IOException e) {
            e.printStackTrace();
        }
    }

    private String getCurrentDateAsIso() {
        // see https://stackoverflow.com/questions/3914404/how-to-get-current-moment-in-iso-8601-format
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        df.setTimeZone(TimeZone.getTimeZone("UTC"));
        return df.format(new Date());
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
        zipOut.flush();


        try {zipOut.close();}
        catch (ZipException e){
            fileOut.close();
            containerMic.delete();
        }
        fileOut.close();
    }

    public static ArrayList<File> getAllFilesInSubdirectory(String subDirectory){
        File directory = new File(subDirectory);
        ArrayList<File> files = new ArrayList<>();
        for(File file: directory.listFiles()){
            if(file.getName().charAt(0) != '.')
                files.add(file);
        }
        return files;
    }

}
