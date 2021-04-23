package unifr.sensorrecorder.DataContainer;

import android.os.Environment;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

public class DataContainer {
    public static final File recordingFilePath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM + "/android_sensor_recorder/");
    public static final String recordSubDirectoryPrefix = "recording";

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

    public void delete(){
        recordingFile.delete();
        isActive = false;
    }

    private void initFilePath(){
        if(!recordingFilePath.exists())
            recordingFilePath.mkdirs();
    }

    public void backupFile(String subDirectory){
        // search next available index for file and rename current
        File subPath = new File(recordingFilePath, subDirectory + "/");
        if(!subPath.exists())
            subPath.mkdirs();
        if (isActive) {
            File dst = null;
            for (int i = 0; i < 999; i++) {
                dst = new File(subPath, name + "_" + i + "." + extension);
                if (!dst.exists())
                    break;
            }
            recordingFile.renameTo(dst);
        }
    }

    public ArrayList<File> getAllVariants(){
        // search for all files which are created during backup process
        ArrayList<File> variants = new ArrayList<File>();

        for(File subdirectory: getAllSubdirectories()) {
            variants.addAll(getAllVariantsInSubDirectory(subdirectory));
        }
        return variants;
    }

    public ArrayList<File> getAllVariantsInSubDirectory(File subdirectory){
        // search for all files which are created during backup process
        ArrayList<File> variants = new ArrayList<File>();
        File tmp_file = null;
        for (int i = 0; i < 999; i++) {
            tmp_file = new File(subdirectory, name + "_" + i + "." + extension);
            if (tmp_file.exists())
                variants.add(tmp_file);
        }

        return variants;
    }

    public static ArrayList<File> getAllSubdirectories(){
        ArrayList<File> subdirectories = new ArrayList<File>();
        File subdirectory;
        for (int i = 0; i < 999; i++){
            subdirectory = new File(recordingFilePath, recordSubDirectoryPrefix + "_" + i + "/");
            if(subdirectory.exists()){
                if(subdirectory.list().length >= 2) {
                    subdirectories.add(subdirectory);
                } else if(subdirectory.list().length == 1 && subdirectory.list()[0].charAt(0) != '.'){
                    subdirectories.add(subdirectory);
                }
            }
        }
        return subdirectories;
    }

    public static String getNewRecordSubdirectory(){
        String subdirectory = "";
        for (int i = 0; i < 999; i++) {
            subdirectory = recordSubDirectoryPrefix + "_" + i;
            File path = new File(DataContainer.recordingFilePath, subdirectory);
            if (!path.exists() || path.list().length == 0)
                break;
        }
        return subdirectory;
    }
}