package com.rom.gamandipo;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.session.MediaSession;
import android.media.session.MediaSessionManager;
import android.os.Binder;
import android.os.IBinder;
import android.provider.MediaStore;
import android.support.annotation.Nullable;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;

import java.util.ArrayList;

/**
 * Created by Parth on 05/12/2017.
 */

public class MediaPlayerService extends Service
                                implements  MediaPlayer.OnCompletionListener,
                                            MediaPlayer.OnPreparedListener,
                                            MediaPlayer.OnErrorListener,
                                            MediaPlayer.OnSeekCompleteListener,
                                            MediaPlayer.OnInfoListener,
                                            MediaPlayer.OnBufferingUpdateListener,
                                            AudioManager.OnAudioFocusChangeListener
{
    public static final String ACTION_PLAY = "com.rom.gamanDipo.ACTION_PLAY";
    public static final String ACTION_PAUSE = "com.rom.gamanDipo.ACTION_PAUSE";
    public static final String ACTION_PREVIOUS = "com.rom.gamanDipo.ACTION_PREVIOUS";
    public static final String ACTION_NEXT = "com.rom.gamanDipo.ACTION_NEXT";
    public static final String ACTION_STOP = "com.rom.gamanDipo.ACTION_STOP";

    private MediaPlayer mediaPlayer;

    //MediaSession
    private MediaSessionManager mediaSessionManager;
    private MediaSessionCompat mediaSession;
    private MediaControllerCompat.TransportControls transportControls;

    //AudioPlayer Notification ID
    private static final int NOTIFICATION_ID = 7;

    //For PAUSE-RESUME
    private int resumePostion;

    //AutoFOcus- for interaction OTher audio apps
    private AudioManager audioManager;

    //Binder for clients
    private final IBinder iBinder = new LocalBinder();

    //List of audio Files
    private ArrayList<MediaStore.Audio> audioArrayList;
    private int audioIndex = -1;
    private MediaStore.Audio currentAudio;

    //Handling Audio Calls
    private boolean onGoingCall = false;
    private PhoneStateListener phoneStateListener;
    private TelephonyManager telephonyManager;

    /**
     * Service LifeCycle Methods
     * @param intent
     * @return
     */

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return iBinder;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        // Perform one-time setup procedures

        // Manage incoming phone calls during playback.
        // Pause MediaPlayer on incoming call,
        // Resume on hangup.

        callStateListener();

        //ACTION_AUDIO_BECOMING_NOISY -- change in audio outputs -- BroadcastReceiver
        registerBecomingNoisyReceiver();

        //Listen for new Audio to play -- BroadcastReceiver
        register_playNewAudio();

    }

    // Called when Activity invokes startservice() , to start the service
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return super.onStartCommand(intent, flags, startId);

        try{
            //Load data from SharedPReference
            StoraegUtil storage = new StorageUtil(getApplicationContext());
            audioArrayList = storage.loadAudio();
            audioIndex = storage.loadAudioIndex();

            if(audioIndex != -1 && audioIndex < audioArrayList.size()) {
                //index is in valid range
                currentAudio = audioArrayList.get(audioIndex);
            }
            else {
                stopSelf();
            }
        }
        catch (NullPointerException e){
            stopSelf();
        }
    }




    /**
     * Binder
     */

    public class LocalBinder extends Binder {

        public MediaPlayerService getService() {
            return MediaPlayerService.this;
        }
    }

    /**
     * MediaPlayer and AudioManager Methods
     *
     */
    @Override
    public void onAudioFocusChange(int i) {

    }

    @Override
    public void onBufferingUpdate(MediaPlayer mediaPlayer, int i) {

    }

    @Override
    public void onCompletion(MediaPlayer mediaPlayer) {

    }

    @Override
    public boolean onError(MediaPlayer mediaPlayer, int i, int i1) {
        return false;
    }

    @Override
    public boolean onInfo(MediaPlayer mediaPlayer, int i, int i1) {
        return false;
    }

    @Override
    public void onPrepared(MediaPlayer mediaPlayer) {

    }

    @Override
    public void onSeekComplete(MediaPlayer mediaPlayer) {

    }
}
