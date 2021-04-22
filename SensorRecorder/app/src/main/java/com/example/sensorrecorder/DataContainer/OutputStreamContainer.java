package com.example.sensorrecorder.DataContainer;

import java.io.FileOutputStream;
import java.io.IOException;

public class OutputStreamContainer extends DataContainer{
    public FileOutputStream outputStream;

    public OutputStreamContainer(String name, String extension) throws IOException {
        super(name, extension);
    }

    public void openStream() throws IOException {
        if(isActive)
            outputStream = new FileOutputStream(recordingFile);
    }

    public void writeData(String data) throws IOException {
        if (isActive)
            outputStream.write(data.getBytes());
    }

    public void flush() throws IOException {
        if(isActive)
            outputStream.flush();
    }

    public void close() throws IOException {
        outputStream.close();
    }
}
