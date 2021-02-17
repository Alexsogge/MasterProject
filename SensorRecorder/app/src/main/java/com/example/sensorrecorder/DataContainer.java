package com.example.sensorrecorder;

import android.os.Environment;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

public class DataContainer {
    public static final File recordingFilePath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM + "/android_sensor_recorder/");
    public String name;
    public String extension;
    public String fileName;
    public File recordingFile;
    public boolean isActive = false;


    public DataContainer(String name, String extension){
        this.name = name;
        this.extension = extension;
        fileName = name + "." + extension;
        initFilePath();

        recordingFile = new File(recordingFilePath, fileName);
    }

    public void setActive() throws IOException {
        isActive = true;
        recordingFile.createNewFile();
    }

    public void deactivate(){
        isActive = false;
    }

    public void close() throws IOException {

    }

    private void initFilePath(){
        if(!recordingFilePath.exists())
        {
            recordingFilePath.mkdirs();
        }
    }

    public void backupFile(){
        // search next available index for file and rename current
        if (isActive) {
            File dst = null;
            for (int i = 0; i < 999; i++) {
                dst = new File(recordingFilePath, name + "_" + i + "." + extension);
                if (!dst.exists())
                    break;
            }
            recordingFile.renameTo(dst);
        }
    }

    public ArrayList<File> getAllVariants(){
        // search for all files which are created during backup process
        ArrayList<File> variants = new ArrayList<File>();

        File tmp_file = null;
        for (int i = 0; i < 999; i++) {
            tmp_file = new File(recordingFilePath, name + "_" + i + "." + extension);
            if (tmp_file.exists())
                variants.add(tmp_file);
        }
        return variants;
    }
}