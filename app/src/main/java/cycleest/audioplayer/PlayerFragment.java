package cycleest.audioplayer;

import android.app.Fragment;
import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

public class PlayerFragment extends Fragment implements Handler.Callback, ServiceConnection, View.OnClickListener {

    private TextView titleLabel;
    private TextView statusLabel;
    private Button playbackButton;
    private Button setTracksButton;

    private Messenger serviceMessenger;
    private Messenger clientMessenger;

    private int playbackStatus;
    private String currentTrack;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        playbackStatus = AudioPlayerService.PLAYBACK_STATE_IDLE;
        setRetainInstance(true);
        startService();
        initializeClientMessenger();
        getActivity().bindService(getServiceIntent(), this, Service.BIND_IMPORTANT);//need to check it
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        View rootView = inflater.inflate(R.layout.fragment_player, container, false);
        return rootView;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        initializeUI();
    }

    @Override
    public void onResume() {
        super.onResume();
        if(playbackStatus != AudioPlayerService.PLAYBACK_STATE_IDLE) {
            initState(playbackStatus);
        }
        if(currentTrack != null){
            titleLabel.setText(currentTrack);
        }
        else{
            titleLabel.setText(getString(R.string.no_tracks_choosen));
        }
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    private void initializeUI() {
        View root = getView();
        titleLabel = (TextView) root.findViewById(R.id.titleLabel);
        statusLabel = (TextView) root.findViewById(R.id.statusLabel);
        playbackButton = (Button) root.findViewById(R.id.playbackButton);
        setTracksButton = (Button) root.findViewById(R.id.setTracksButton);

        playbackButton.setOnClickListener(this);
        setTracksButton.setOnClickListener(this);

        titleLabel.setSelected(true);
    }

    @Override
    public void onClick(View v) {
        if (v == playbackButton) {
            onPlaybackButtonClick();
        }
        if (v == setTracksButton) {
            onSetTracksButtonClick();
        }
    }

    private void onPlaybackButtonClick(){
        Message msg = Message.obtain();
        msg.what = AudioPlayerService.ACTION_TOGGLE;
        if(playbackStatus == AudioPlayerService.PLAYBACK_STATE_PLAYING){
            initState(AudioPlayerService.PLAYBACK_STATE_PAUSED);
        }
        else{
            initState(AudioPlayerService.PLAYBACK_STATE_PLAYING);
        }
        try {
            serviceMessenger.send(msg);
        } catch (RemoteException e) {
            Log.d("messaging", "remote exception");
        }
    }

    private void onSetTracksButtonClick(){
        //dummy currently
        playbackStatus = AudioPlayerService.PLAYBACK_STATE_PAUSED;
        playbackButton.setText(getString(R.string.play));
        Message msg = Message.obtain();
        msg.what = AudioPlayerService.ACTION_SET_TRACK;
        Bundle data = new Bundle();
        //StreamProvider resourceProvider = new StreamProvider();
        //resourceProvider.ass
        //Uri content = Uri.parse("content://cycleest.audioplayer/20kHz.mp3");
        Uri content = Uri.parse("content://cycleest.audioplayer/20kHz.mp3");
        data.putParcelable(AudioPlayerService.CURRENT_TRACK_DATA_TAG, content);
        msg.setData(data);
        try {
            serviceMessenger.send(msg);
        } catch (RemoteException e) {
            Log.d("messaging", "remote exception");
        }
    }

    private void initializeClientMessenger() {
        Handler uiHandler = new Handler(Looper.getMainLooper(), this);
        clientMessenger = new Messenger(uiHandler);
    }

    private void startService() {
        Intent startIntent = getServiceIntent();
        getActivity().startService(startIntent);
    }

    private Intent getServiceIntent() {
        Intent toServiceIntent = new Intent(getActivity(), AudioPlayerService.class);
        return toServiceIntent;
    }

    private void unregisterInService(){
        Message msg = Message.obtain();
        msg.what = AudioPlayerService.ACTION_UNREGISTER;
        try {
            serviceMessenger.send(msg);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    private void registerInService(){
        Message msg = Message.obtain();
        msg.what = AudioPlayerService.ACTION_REGISTER;
        msg.replyTo = clientMessenger;
        try {
            serviceMessenger.send(msg);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        serviceMessenger = new Messenger(service);
        registerInService();
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        serviceMessenger = null;
    }

    @Override
    public boolean handleMessage(Message msg) {
        switch (msg.what) {
            case AudioPlayerService.WHOLE_STATE_NOTIFICATION:
                handleWholeStateMessage(msg);
                break;
            case AudioPlayerService.PLAYBACK_STATE_NOTIFICATION:
                handlePlaybackStateMessage(msg);
                break;

        }
        return false;
    }

    private void handleWholeStateMessage(Message msg) {
        Bundle data = msg.getData();
        if (data.containsKey(AudioPlayerService.PLAYBACK_STATE_DATA_TAG)) {
            int playbackState = data.getInt(AudioPlayerService.PLAYBACK_STATE_DATA_TAG);
            initState(playbackState);
        }
        if (data.containsKey(AudioPlayerService.CURRENT_TRACK_DATA_TAG)) {
            currentTrack = data.getString(AudioPlayerService.CURRENT_TRACK_DATA_TAG);
            setTitle(currentTrack);
        }
        else{
            setTitle(getString(R.string.no_tracks_choosen));
        }
    }

    private void handlePlaybackStateMessage(Message msg) {
        initState(msg.arg1);
    }

    private void initState(int state){
        switch (state) {
            case AudioPlayerService.PLAYBACK_STATE_IDLE:
                playbackButton.setVisibility(Button.INVISIBLE);
                statusLabel.setText(getString(R.string.idle));
                break;
            case AudioPlayerService.PLAYBACK_STATE_PAUSED:
                playbackStatus = AudioPlayerService.PLAYBACK_STATE_PAUSED;
                playbackButton.setVisibility(Button.VISIBLE);
                playbackButton.setText(getString(R.string.play));
                statusLabel.setText(getString(R.string.paused));
                break;
            case AudioPlayerService.PLAYBACK_STATE_PLAYING:
                playbackStatus = AudioPlayerService.PLAYBACK_STATE_PLAYING;
                playbackButton.setVisibility(Button.VISIBLE);
                playbackButton.setText(getString(R.string.pause));
                statusLabel.setText(getString(R.string.playing));
                break;
        }
    }

    private void setTitle(String title){
        titleLabel.setText(title);
    }
}
