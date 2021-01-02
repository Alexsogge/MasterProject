package de.uni_freiburg.ffmpeg;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * This is a wrapper for FFMpeg that allows to run ffmpeg executable and returns Process
 * object to interact with the running process. Data with ffmpeg can be exchanged via named
 * pipes which are created with addPipedInput(). The connected OutputStream which writes to
 * ffmpeg can be obtained with getOutputStream().
 *
 * Created by phil on 8/26/16.
 */


public class FFMpegProcess {
    protected Process p;
    protected LinkedList<File> mFiles = new LinkedList<>();
    public HashMap<Integer,OutputStream> mStreams = new HashMap<>();
    protected FFMpegProcess.ExitCallback exit;
    protected static final ExecutorService THREAD_POOL_EXECUTOR = Executors.newCachedThreadPool();
    protected final AsyncTask<InputStream, Void, Void> verboseMonitor =
        new AsyncTask<InputStream, Void, Void>() {
        @Override
        protected Void doInBackground(InputStream... ps) {
            InputStream is = ps[0];

            try {
                byte buf[] = new byte[4096];

                while(!isCancelled()) {
                    int n = is.read(buf);
                    if (n < 0) break;
                    System.err.write(buf, 0, n);
                }
            } catch (IOException e) {}
            return null;
    }};
    protected final AsyncTask<Process, Void, Void> exitMonitor = new AsyncTask<Process, Void, Void>() {
        @Override
        protected Void doInBackground(Process... ps) {
            Process p = ps[0];
            try {
                p.waitFor();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if (exit != null)
                exit.processDone();
            return null;
    }};


    protected FFMpegProcess(ProcessBuilder b, LinkedList<File> files) throws IOException {

        // System.loadLibrary("ffmpegcustom");  // module name, without the trailing ".so" or leading "lib"

        p = b.start();
        mFiles = files;
        System.err.println("executing " + b.command().toString());
        verboseMonitor.executeOnExecutor(THREAD_POOL_EXECUTOR, p.getErrorStream());
        exitMonitor.executeOnExecutor(THREAD_POOL_EXECUTOR, p);
    }

    public int waitFor() throws InterruptedException {
        return p.waitFor();
    }

    public InputStream getErrorStream() { return p.getErrorStream();  }

    public int terminate() throws InterruptedException {
        for (OutputStream s : mStreams.values())
            try { s.close(); }
            catch (IOException e) {  }

        int i = p.waitFor();
        verboseMonitor.cancel(true);
        return i;
    }

    public InputStream getInputStream() {
        return p.getInputStream();
    }

    public void exitCallback(FFMpegProcess.ExitCallback cb) {
        this.exit = cb;
    }

    public OutputStream getOutputStream(int j) throws FileNotFoundException {
        Log.i("ffmpeg", "ask for pipe" + j);
        OutputStream s = mStreams.get(j);
        if (s == null) {
            Log.i("ffmpeg", "create pipe " + j);
            File f = mFiles.get(j);
            Log.i("ffmpeg", "got file " + f.getPath() + " w-> " + f.canWrite() + " r-> " + f.canRead() + " e-> " + f.exists());
            FileOutputStream fos = new FileOutputStream(f);
            Log.i("ffmpeg", "got stream " + fos.toString());

            f.delete();
            Log.i("ffmpeg", "deleted file");
            mStreams.put(j, new BufferedOutputStream(fos));
        }
        Log.i("ffmpeg", "return pipe " + j);
        return mStreams.get(j);
    }

    /** This is a helper class to build what my common usages for the FFMpeg tool will be, feel
     * free to add additional stuff here. You can always add your own command line switches with
     * the addSwitch() function.
     */
    public static class Builder {
        LinkedList<String> inputopts = new LinkedList<String>(),
                          outputopts = new LinkedList<String>();
        LinkedList<File> mInputPipes = new LinkedList<>();

        int numinputs  = 0;
        private String output_fmt;
        private String output;
        private Context mContext;

        public Builder(Context c) {
            mContext = c;
        }

        /** add an audio stream to the ffmpeg input
         * @param format sample format, list them with ffmpeg -formats or documentation
         * @param rate   sample rate in Hz
         * @param channels number of channels
         */
        public Builder addAudio(String format, double rate, int channels) throws IOException, InterruptedException {
            return
             addInputArgument("-f", format)
            .addInputArgument("-ar", new Double(rate).toString())
            .addInputArgument("-ac", new Double(channels).toString())
            .addPipedInput();
        }

        /** add a video stream to the ffmpeg input
         *
         * @param width   width of the input video
         * @param height  height of the input video
         * @param rate    input rate of the video stream
         * @param fmt     video format, e.g. raw
         * @param pixfmt  pixel format for the video stream, list the available ones with pix_fmt,
         *                set to null if specified by the input format. The default for Android is
         *                NV21.
         */
        public Builder addVideo(int width, int height, double rate, String fmt, String pixfmt) throws IOException, InterruptedException {
            String optarg = pixfmt == null ? "" : String.format("-pix_fmt %s", pixfmt);
            return
             addInputArgument("-r", new Double(rate).toString())
            .addInputArgument("-s", String.format("%d:%d", width, height))
            .addInputArgument("-f", fmt)
            .addInputArgument(pixfmt == null ? "" : "-pix_fmt", pixfmt == null ? "" : pixfmt)
            .addPipedInput();
        }

        /** set a metadata tag for the last defined input stream
         *
         * @param key name of tag to set
         * @param value value of the specified tag
         */
        public Builder setStreamTag(String key, String value) throws Exception {
            if (numinputs == 0)
                throw new Exception("no stream to apply tags to, please add one first");

            outputopts.add(String.format("-metadata:s:%d", numinputs-1));
            outputopts.add(String.format("%s=%s", key, value));

            return this;
        }

        /** set a metadata tag for the whole output file
         *
         * @param key name of the tag
         * @param value value of the tag
         */
        public Builder setTag(String key, String value) {
            outputopts.add("-metadata");
            outputopts.add(String.format("%s=%s", key, value));

            return this;
        }

        /** set the output codec for the current stream. In case this is not set the default
         * for the output format will be used.
         *
         * @param codec set the codec to encode the output with
         */
        public Builder setStreamCodec(String codec) throws Exception {
            if (numinputs == 0)
                throw new Exception("no stream to apply tags to, please add one first");

            outputopts.add(String.format("-c:%d", numinputs-1));
            outputopts.add(codec);

            return this;
        }

        public Builder setSubtitleFile(File subtitleFile) throws Exception{
            if(!subtitleFile.exists() || !subtitleFile.isFile())
                throw new Exception("something is wrong with the subtitle file");

            return addInputArgument("-i", subtitleFile.getAbsolutePath());
        }

        /** set the codec to use for a given stream specifier (or all stream if omitted), see the
         * ffmpeg manpage for details.
         *
         * @param stream stream specifier, optional, can be null
         * @param codec codec to use
         */
        public Builder setCodec(String stream, String codec) {
            if (stream != null && stream.length()>0)
                outputopts.add(String.format("-c:%s", stream));
            outputopts.add(codec);
            return this;
        }

        /** add a cmdline switch
         *
         * @param option include the leading dash to the command line option
         * @param value the value of this option
         */
        public Builder addOutputArgument(String option, String value) {
            outputopts.add(option);

            if (value != null)
                outputopts.add(value);

            return this;
        }


        public Builder addOutputArgument(String option) {
            return addOutputArgument(option, null);
        }

        /** add a cmdline switch
         *
         * @param option include the leading dash to the command line option
         * @param value the value of this option
         */
        public Builder addInputArgument(String option, String value) {
            inputopts.add(option);
            inputopts.add(value);

            if ("-i".equals(option))
                numinputs++;

            return this;
        }

        /** add a piped input, i.e. an OutputStream which writes into the ffmpeg process
         */
        public Builder addPipedInput() throws IOException, InterruptedException {
            File dir = mContext.getFilesDir().getParentFile();
            File f = File.createTempFile("ffmpeg", "", dir);

            inputopts.add("-i");
            inputopts.add("async:file:"+f.getAbsolutePath());

            /** create named pipe */
            f.delete();
            Process p = new ProcessBuilder().command("mknod", f.getAbsolutePath(), "p").start();
            int result = p.waitFor();

            if (result != 0)
                throw new IOException("mknod failed");

            /** open and store for later use */
            f = new File(f.getAbsolutePath());
            f.deleteOnExit();
            mInputPipes.add( f );
            numinputs ++;

            return this;
        }

        /** set the output, per default all inputs will be mapped into the output. If you need a
         * different behaviour, specify the "-map" and output option with the addSwitch method.
         *
         * @param output the output file, channel etc. that ffmpeg supports (defaults to overwriting)
         * @param format set to null if ffmpeg is to decide, otherwise choose a container
         */
        public Builder setOutput(String output, String format) throws Exception {
            if (output == null)
                throw new Exception("output must be non-null");
            if (format == null)
                throw new Exception("format must be non-null");
            this.output = new File(output).exists() && !output.startsWith("file:") ?
                          "file:"+output : output;
            this.output_fmt = format;

            Log.e("OUT", output + output_fmt);
            return this;
        }

        public Builder setLoglevel(String level) {
            outputopts.add("-loglevel");
            outputopts.add(level);
            return this;
        }

        public FFMpegProcess build() throws IOException {
            LinkedList<String> cmdline = new LinkedList<String>();
            File dir = mContext.getFilesDir().getParentFile();
            File path = new File(new File(dir, "lib"), "libffmpegcustom.so");


            boolean hasmap = false;
            for (String opt : inputopts)
                hasmap |= opt.equals("-map");

            if (!hasmap)
                for (int i=0; i<numinputs; i++) {
                    outputopts.add("-map");
                    outputopts.add(String.format("%d", i));
                }

            if (output_fmt != null) {
                outputopts.add("-f");
                outputopts.add(output_fmt);
                outputopts.add("-y");
                outputopts.add(output);
            }

            cmdline.add(path.toString());
            cmdline.addAll(inputopts);
            cmdline.add("-nostdin");
            cmdline.addAll(outputopts);

            Log.e("ffmpeg", cmdline.toString());

            ProcessBuilder pb = new ProcessBuilder(cmdline);

            pb.directory(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM));

            FFMpegProcess p = new FFMpegProcess(pb, mInputPipes);

            return p;
        }
    }

    public interface ExitCallback {
        public void processDone();
    }
}
