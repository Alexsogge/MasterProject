package unifr.sensorrecorder.DataContainer;

import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ZipContainer extends OutputStreamContainer{
    public ZipOutputStream zipOutputStream;
    public String innerFileExtension;

    public ZipContainer(String name, String extension) throws IOException {
        super(name, "zip");
        innerFileExtension = extension;
    }

    public void openStream() throws IOException {
        if(isActive) {
            super.openStream();
            zipOutputStream = new ZipOutputStream(outputStream);
            zipOutputStream.putNextEntry(new ZipEntry(name + fileNameSuffix + "." + innerFileExtension));
        }
    }

    public void writeData(String data) throws IOException {
        if(isActive)
            zipOutputStream.write(data.getBytes());
    }

    public void flush() throws IOException {
        if(isActive)
            zipOutputStream.flush();
    }

    public void close() throws IOException {
        zipOutputStream.closeEntry();
        zipOutputStream.close();
        outputStream.close();
    }
}