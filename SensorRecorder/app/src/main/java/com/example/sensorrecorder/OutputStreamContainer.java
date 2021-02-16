package com.example.sensorrecorder;

import java.io.FileOutputStream;
import java.io.IOException;

public class OutputStreamContainer extends DataContainer{
    public FileOutputStream outputStream;

    public OutputStreamContainer(String name, String extension) throws IOException {
        super(name, extension);
    }

    public void OpenStream() throws IOException {
        if(isActive)
            outputStream = new FileOutputStream(recordingFile);
    }

    public void WriteData(String data) throws IOException {
        if (isActive)
            outputStream.write(data.getBytes());
    }

    public void Flush() throws IOException {
        if(isActive)
            outputStream.flush();
    }

    public void Close() throws IOException {
        outputStream.close();
    }
}
