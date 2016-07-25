package com.bestforce.testmp4parser;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.widget.Toast;

import com.bestforce.utils.SimpleThreadFactory;

import com.coremedia.iso.boxes.Container;
import com.googlecode.mp4parser.authoring.Movie;
import com.googlecode.mp4parser.authoring.Track;
import com.googlecode.mp4parser.authoring.builder.DefaultMp4Builder;
import com.googlecode.mp4parser.authoring.container.mp4.MovieCreator;
import com.googlecode.mp4parser.authoring.tracks.AppendTrack;
import com.googlecode.mp4parser.authoring.tracks.CroppedTrack;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.bestforce.utils.Ut;

public class AppendExample {

    private static final String TAG = "AppendExample";
    private List<String> filePaths;
    private final Context mCxt;
    private ExecutorService mThreadExecutor = null;
    private SimpleInvalidationHandler mHandler;
    private ProgressDialog mProgressDialog;

    private class SimpleInvalidationHandler extends Handler {

        @Override
        public void handleMessage(final Message msg) {
            switch (msg.what) {
                case com.bestforce.testmp4parser.R.id.shorten:

                    break;
                case com.bestforce.testmp4parser.R.id.append:
                    mProgressDialog.dismiss();

                    if (msg.arg1 == 0)
                        Toast.makeText(mCxt,
                                mCxt.getString(com.bestforce.testmp4parser.R.string.message_error) + " " + (String) msg.obj,
                                Toast.LENGTH_LONG).show();
                    else
                        Toast.makeText(mCxt,
                                mCxt.getString(com.bestforce.testmp4parser.R.string.message_appended) + " " + (String) msg.obj,
                                Toast.LENGTH_LONG).show();
                    break;
            }
        }
    }

    public AppendExample(Context context, List<String> filePaths) {
        mCxt = context;
        this.filePaths = filePaths;
        mHandler = new SimpleInvalidationHandler();
    }

    public void append() {
        doAppend();
    }

    private void doAppend() {
        mProgressDialog = Ut.ShowWaitDialog(mCxt, 0);

        if (mThreadExecutor == null)
            mThreadExecutor = Executors.newSingleThreadExecutor(new SimpleThreadFactory("DoAppend"));

        this.mThreadExecutor.execute(new Runnable() {
            public void run() {
                try {
                    File folder = new File(Environment.getExternalStorageDirectory() + File.separator + "Download/");

                    List<File> fileList = new ArrayList<File>();
                    List<Movie> movieList = new ArrayList<Movie>();
                    for (int i = 0; i < filePaths.size(); i++) {
                        fileList.add(new File(filePaths.get(i)));
                        movieList.add(MovieCreator.build(new File(filePaths.get(i)).getAbsolutePath()));
                    }

                    // Create a media file name
                    String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
                    String filename = folder.getPath() + File.separator + "TMP4_APP_OUT_" + timeStamp + ".mp4";


                    LinkedList<Track> videoTracks = new LinkedList<Track>();
                    LinkedList<Track> audioTracks = new LinkedList<Track>();
                    long[] audioDuration = {0}, videoDuration = {0};
                    for (Movie m : movieList) {
                        for (Track t : m.getTracks()) {
                            if (t.getHandler().equals("soun")) {
                                for (long a : t.getSampleDurations()) audioDuration[0] += a;
                                audioTracks.add(t);
                            } else if (t.getHandler().equals("vide")) {
                                for (long v : t.getSampleDurations()) videoDuration[0] += v;
                                videoTracks.add(t);
                            }
                        }

                        adjustDurations(videoTracks, audioTracks, videoDuration, audioDuration);
                    }

                    //Result movie from putting the audio and video together from the two clips
                    Movie result = new Movie();

                    //Append all audio and video
                    if (videoTracks.size() > 0)
                        result.addTrack(new AppendTrack(videoTracks.toArray(new Track[videoTracks.size()])));

                    if (audioTracks.size() > 0)
                        result.addTrack(new AppendTrack(audioTracks.toArray(new Track[audioTracks.size()])));


                    Container out = new DefaultMp4Builder().build(result);
                    FileChannel fc = new RandomAccessFile(String.format(filename), "rw").getChannel();
                    out.writeContainer(fc);
                    fc.close();


                    Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                    File f = new File("file://" + Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS));
                    Uri contentUri = Uri.fromFile(f);
                    mediaScanIntent.setData(contentUri);
                    mCxt.sendBroadcast(mediaScanIntent);


                    Message.obtain(mHandler, com.bestforce.testmp4parser.R.id.append, 1, 0, filename).sendToTarget();
                } catch (FileNotFoundException e) {
                    Message.obtain(mHandler, com.bestforce.testmp4parser.R.id.append, 0, 0, e.getMessage()).sendToTarget();
                    e.printStackTrace();
                } catch (IOException e) {
                    Message.obtain(mHandler, com.bestforce.testmp4parser.R.id.append, 0, 0, e.getMessage()).sendToTarget();
                    e.printStackTrace();
                } catch (NullPointerException e) {
                    Message.obtain(mHandler, com.bestforce.testmp4parser.R.id.append, 0, 0, e.getMessage()).sendToTarget();
                    e.printStackTrace();
                }

            }
        });
    }

    private void adjustDurations(LinkedList<Track> videoTracks, LinkedList<Track> audioTracks, long[] videoDuration, long[] audioDuration) {
        long diff = audioDuration[0] - videoDuration[0];

        //nothing to do
        if (diff == 0) {
            return;
        }

        //audio is longer
        LinkedList<Track> tracks = audioTracks;

        //video is longer
        if (diff < 0) {
            tracks = videoTracks;
            diff *= -1;
        }

        Track track = tracks.getLast();
        long[] sampleDurations = track.getSampleDurations();
        long counter = 0;
        for (int i = sampleDurations.length - 1; i > -1; i--) {
            if (sampleDurations[i] > diff) {
                break;
            }
            diff -= sampleDurations[i];
            audioDuration[0] -= sampleDurations[i];
            counter++;
        }

        if (counter == 0) {
            return;
        }

        track = new CroppedTrack(track, 0, track.getSamples().size() - counter);

        //update the original reference
        tracks.removeLast();
        tracks.addLast(track);
    }
}
