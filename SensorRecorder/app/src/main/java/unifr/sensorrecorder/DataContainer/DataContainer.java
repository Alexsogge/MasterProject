package unifr.sensorrecorder.DataContainer;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;

import unifr.sensorrecorder.R;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.TimeZone;

public class DataContainer {
    public static final String recordSubDirectoryPrefix = "recording";
    public static String fileNameSuffix = "";
    public static int currentRun;

    public String name;
    public String extension;
    public String fileName;
    public File recordingFile;
    public boolean isActive = false;

    public DataContainer(File dir, String name, String extension){
        this.name = name;
        this.extension = extension;
        fileName = name + "." + extension;

        recordingFile = new File(dir, name);
    }


    public static void generateFileNameSuffix(Context context){
        TimeZone tz = TimeZone.getTimeZone("UTC");
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mmZ");
        df.setTimeZone(tz);
        String aid = Settings.Secure.getString(context.getContentResolver(),
                Settings.Secure.ANDROID_ID);
        SharedPreferences configs = context.getSharedPreferences(context.getString(R.string.configs), Context.MODE_PRIVATE);
        currentRun = configs.getInt(context.getString(R.string.val_current_run), 0);
        SharedPreferences.Editor configEditor = configs.edit();
        configEditor.putInt(context.getString(R.string.val_current_run), currentRun+1);
        configEditor.apply();

        fileNameSuffix = "_" + currentRun + "_" + df.format(new Date()) + "_" + aid;
    }

    private String getCurrentDateAsIso() {
        // see https://stackoverflow.com/questions/3914404/how-to-get-current-moment-in-iso-8601-format
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'");
        df.setTimeZone(TimeZone.getTimeZone("UTC"));
        return df.format(new Date());
    }



    public void setActive() throws IOException {
        isActive = true;
        Log.d("wtf", recordingFile.getAbsolutePath());
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

    public void backupFile(String subDirectory){
        // search next available index for file and rename current
        File subPath = new File(recordingFile.getParentFile(), subDirectory + "/");
        if(!subPath.exists())
            subPath.mkdirs();
        if (isActive) {
            File dst = new File(subPath, name + fileNameSuffix + "." + extension);;
            /*
            for (int i = 0; i < 999; i++) {

                dst = new File(subPath, name + "_" + i + "." + extension);
                if (!dst.exists())
                    break;
            }
            */
            recordingFile.renameTo(dst);
        }
    }

    public static ArrayList<File> getAllSubdirectories(){
        ArrayList<File> subdirectories = new ArrayList<File>();
        File subdirectory;
        for (int i = 0; i < 999; i++){
            subdirectory = new File(StaticDataProvider.getProcessor().recordingDirectory,
                               recordSubDirectoryPrefix + "_" + i + "/");
            if(subdirectory.exists() && subdirectory.list() != null){
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
            File path = new File(StaticDataProvider.getProcessor().recordingDirectory,
                    subdirectory);
            if (!path.exists() || path.list().length == 0)
                break;
        }
        return subdirectory;
    }
}