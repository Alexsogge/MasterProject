package unifr.sensorrecorder.DataContainer;

import android.icu.text.Edits;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class MultyEntryZipContainer extends OutputStreamContainer{
    public HashMap<String, ZipOutputStream> zipOutputStreams;
    public HashMap<String, String> entryFileNames;
    public String innerFileExtension;

    public MultyEntryZipContainer(String name, String extension) throws IOException {
        super(name, "zip");
        innerFileExtension = extension;
        zipOutputStreams = new HashMap<>();
        entryFileNames = new HashMap<>();
    }

    public void addEntry(String entryName, String entryFileName){
        entryFileNames.put(entryName, entryFileName);
    }

    public void openStream() throws IOException {
        if(isActive) {
            super.openStream();
            for(String entryName: entryFileNames.keySet()) {
                ZipOutputStream newStream = new ZipOutputStream(outputStream);
                newStream.putNextEntry(new ZipEntry(entryFileNames.get(entryName) + fileNameSuffix + "." + innerFileExtension));
                zipOutputStreams.put(entryName, newStream);
            }
        }
    }

    public void writeData(String entryName, String data) throws IOException {
        if(isActive)
            zipOutputStreams.get(entryName).write(data.getBytes());
    }

    public void flush() throws IOException {
        if(isActive){
           for(ZipOutputStream stream: zipOutputStreams.values()){
               stream.flush();
           }
        }
    }

    public void close() throws IOException {
        for(ZipOutputStream stream: zipOutputStreams.values()){
            stream.closeEntry();
            stream.close();
        }
        outputStream.close();
    }
}