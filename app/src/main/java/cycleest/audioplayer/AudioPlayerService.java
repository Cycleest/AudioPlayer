package cycleest.audioplayer;

import android.app.Notification;
import android.app.Service;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.Process;
import android.os.RemoteException;
import android.util.Log;
import android.widget.Toast;

import com.commonsware.cwac.provider.StreamProvider;

import java.io.FileDescriptor;
import java.io.IOException;


public class AudioPlayerService extends Service
        implements
        Handler.Callback,
        MediaPlayer.OnPreparedListener,
        MediaPlayer.OnSeekCompleteListener,
        MediaPlayer.OnCompletionListener,
        MediaPlayer.OnErrorListener {

    private static final String PACKAGE_PREFIX = "cycleest.audioplayer.";
    public static final String PLAYBACK_STATE_DATA_TAG = PACKAGE_PREFIX + "data.playback_state";
    public static final String CURRENT_TRACK_DATA_TAG = PACKAGE_PREFIX + "data.current_track";

    public static final int ACTION_UNREGISTER = 0;
    public static final int ACTION_REGISTER = 1;

    public static final int ACTION_SET_TRACK = 2;

    public static final int ACTION_TOGGLE = 3;

    public static final int PLAYBACK_STATE_IDLE = -2;
    public static final int PLAYBACK_STATE_READY = -1;
    public static final int PLAYBACK_STATE_PAUSED = 0;
    public static final int PLAYBACK_STATE_PLAYING = 1;

    public static final int WHOLE_STATE_NOTIFICATION = 0;
    public static final int PLAYBACK_STATE_NOTIFICATION = 1;

    private int currentPlaybackState = PLAYBACK_STATE_IDLE;
    private String currentTrack;

    private Messenger serviceMessenger;
    private Messenger clientMessenger;

    private MediaPlayer mediaPlayer;

    @Override
    public boolean handleMessage(Message msg) {
        switch (msg.what) {
            case ACTION_TOGGLE:
                handleToggleAction();
                break;
            case ACTION_SET_TRACK:
                if (msg.getData() != null) {
                    handleTracksAction(msg.getData());
                }
                break;
            case ACTION_REGISTER:
                handleRegisterAction(msg);
                break;
            case ACTION_UNREGISTER:
                handleUnregisterAction(msg);
                break;
        }
        return false;
    }

    private void handleToggleAction() {
        if (currentPlaybackState != PLAYBACK_STATE_IDLE) {
            if (currentPlaybackState == PLAYBACK_STATE_PLAYING) {
                mediaPlayer.pause();
                currentPlaybackState = PLAYBACK_STATE_PAUSED;
            } else {
                mediaPlayer.start();
                currentPlaybackState = PLAYBACK_STATE_PLAYING;
            }
        }
    }

    private void handleTracksAction(Bundle data) {
        if (data.containsKey(CURRENT_TRACK_DATA_TAG)) {
            Uri track = (Uri) data.getParcelable(CURRENT_TRACK_DATA_TAG);
            StreamProvider provider = new StreamProvider();
            Log.d("uri", track.toString());
            try {
                AssetManager assets = getAssets();
                AssetFileDescriptor trackAsset = assets.openFd("20kHz.mp3");
                //AssetFileDescriptor trackAsset = provider.openAssetFile(track, "r"); //not working idk why
                FileDescriptor trackFile = trackAsset.getFileDescriptor();
                iniPlayer(trackFile);
                //iniPlayer("file:///android_asset/20kHz.mp3"); //not working idk why
                currentTrack = track.getLastPathSegment();
                notifyAboutWholeState(clientMessenger);
            } catch (Exception e) {
                Log.d("io", "fileNotFound in service");
            }
        }
    }

    private void handleRegisterAction(Message msg) {
        //only one client at a time currently
        clientMessenger = msg.replyTo;
        notifyAboutWholeState(clientMessenger);
        Toast.makeText(this, "registered", Toast.LENGTH_SHORT).show();
        Log.d("messenger ser", String.valueOf(clientMessenger.hashCode()));
    }

    private void handleUnregisterAction(Message msg) {
        clientMessenger = null;
        Toast.makeText(this, "unregistered", Toast.LENGTH_SHORT).show();
    }

    private void iniPlayer(FileDescriptor trackFile) {
        if (currentPlaybackState != PLAYBACK_STATE_IDLE) {
            mediaPlayer.reset();
        }
        try {
            mediaPlayer.setDataSource(trackFile);
            mediaPlayer.prepare();
            currentPlaybackState = PLAYBACK_STATE_READY;
        } catch (IOException e) {
            Log.d("io", "open failed in service");
            currentPlaybackState = PLAYBACK_STATE_IDLE;
        }
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        currentPlaybackState = PLAYBACK_STATE_READY;
    }

    @Override
    public void onSeekComplete(MediaPlayer mp) {

    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        currentPlaybackState = PLAYBACK_STATE_READY;
        Toast.makeText(this, "completed"+String.valueOf(currentPlaybackState), Toast.LENGTH_SHORT).show();
        notifyAboutPlaybackState();
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        mediaPlayer.reset();
        //mediaPlayer.setDataSource();
        currentPlaybackState = PLAYBACK_STATE_IDLE;
        notifyAboutPlaybackState();
        return false;
    }

    @Override
    public void onCreate() {
        HandlerThread thread = new HandlerThread("worker thread", Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();
        Looper serviceLooper = thread.getLooper();
        Handler serviceHandler = new Handler(serviceLooper, this);
        serviceMessenger = new Messenger(serviceHandler);
        mediaPlayer = new MediaPlayer();
        mediaPlayer.setOnCompletionListener(this);
        mediaPlayer.setOnErrorListener(this);

        startForeground(132, new Notification());
        //restore logic needed
        //currentPlaybackState = ;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Toast.makeText(this, "service starting", Toast.LENGTH_SHORT).show();
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        Toast.makeText(this, "binded", Toast.LENGTH_SHORT).show();
        return serviceMessenger.getBinder();
    }

    @Override
    public void onDestroy() {
        Toast.makeText(this, "service destroyed", Toast.LENGTH_SHORT).show();
    }

    private void notifyAboutWholeState(Messenger receiver) {
        Message stateMsg = getWholeStateMessage();
        try {
            receiver.send(stateMsg);
        } catch (RemoteException e) {
            Log.d("message", "deliver failed");
        }
    }

    private void notifyAboutPlaybackState() {
        Message playbackStateMsg = getPlaybackStateMessage();
        try {
            clientMessenger.send(playbackStateMsg);
        } catch (RemoteException e) {
            Log.d("message", "deliver failed");
        }
    }

    private Message getWholeStateMessage() {
        Message msg = Message.obtain();
        Bundle state = new Bundle();
        state.putInt(PLAYBACK_STATE_DATA_TAG, currentPlaybackState);
        if (currentTrack != null) {
            state.putString(CURRENT_TRACK_DATA_TAG, currentTrack);
        }
        msg.setData(state);
        return msg;
    }

    private Message getPlaybackStateMessage() {
        Message msg = Message.obtain();
        msg.what = PLAYBACK_STATE_NOTIFICATION;
        msg.arg1 = currentPlaybackState;

        return msg;
    }

}
