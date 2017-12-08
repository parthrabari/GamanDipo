package com.rom.gamandipo;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.session.MediaSession;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Binder;
import android.os.IBinder;
import android.os.RemoteException;
import android.provider.MediaStore;
import android.support.annotation.Nullable;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v7.app.NotificationCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;

import java.io.IOException;
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
    private ArrayList<Audio> audioArrayList;
    private int audioIndex = -1;
    private Audio currentAudio;

    //Handling Audio Calls
    private boolean onGoingCall = false;
    private PhoneStateListener phoneStateListener;
    private TelephonyManager telephonyManager;

    /**
     * Binder
     */

    public class LocalBinder extends Binder {

        public MediaPlayerService getService() {
            return MediaPlayerService.this;
        }
    }

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

        try{
            //Load data from SharedPReference
            StorageUtil storage = new StorageUtil(getApplicationContext());
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

        //Request Audio Focus
        if(requestAudioFocus() == false) {
            stopSelf();
        }

        if(mediaSessionManager == null) {
            try {
                initMediaSession();
                initMediaPlayer();
            }
            catch (RemoteException e) {
                e.printStackTrace();
                stopSelf();
            }

            buildNotification(PlaybackState.STATE_PLAYING);
        }

        // Handle Intent From MediaSession.TrnsCtl
        handleIncomingActions(intent);
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        mediaSession.release();
        removeNotification();
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if(mediaPlayer != null) {
            stopMedia();
            mediaPlayer.release();
        }
        removeAudioFocus();

        //Disable PhoneListener
        if(phoneStateListener != null) {
            telephonyManager.listen(phoneStateListener,PhoneStateListener.LISTEN_NONE);
        }

        removeNotification();

        //Unregister BroadCast Receivers
        unregisterReceiver(becomingNoisyReceiver);
        unregisterReceiver(playNewAudio);

        //clear cached PlayList
        new StorageUtil(getApplicationContext()).clearCachedAudioPlaylist();
    }

    /**
     * MediaPlayer and AudioManager Methods
     *
     */

    @Override
    public void onBufferingUpdate(MediaPlayer mediaPlayer, int i) {
        //Invoked indicating buffering status of
        //a media resource being streamed over the network.

    }

    @Override
    public void onCompletion(MediaPlayer mediaPlayer) {
        //Invoked when playback of a media source has completed.
        stopMedia();
        removeNotification();
        stopSelf(); //stop service

    }

    @Override
    public boolean onError(MediaPlayer mediaPlayer, int i, int i1) {
        //Invoked when there has been an error during an asynchronous operation

        switch (i) {
            case MediaPlayer.MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK:
                Log.d("MediaPlayer Error", "MEDIA ERROR NOT VALID FOR PROGRESSIVE PLAYBACK " + i1);
                break;
            case MediaPlayer.MEDIA_ERROR_SERVER_DIED:
                Log.d("MediaPlayer Error", "MEDIA ERROR SERVER DIED " + i1);
                break;
            case MediaPlayer.MEDIA_ERROR_UNKNOWN:
                Log.d("MediaPlayer Error", "MEDIA ERROR UNKNOWN " + i1);
                break;
        }
        return false;
    }

    @Override
    public boolean onInfo(MediaPlayer mediaPlayer, int i, int i1) {
        //Invoked to communicate some info
        return false;
    }

    @Override
    public void onPrepared(MediaPlayer mediaPlayer) {
        //Invoked when the media source is ready for playback.
        playMedia();
    }

    @Override
    public void onSeekComplete(MediaPlayer mediaPlayer) {
        //Invoked indicating the completion of a seek operation.
    }

    @Override
    public void onAudioFocusChange(int i) {
        //Invoked when the audio focus of the system is updated.

        switch (i) {
            case AudioManager.AUDIOFOCUS_GAIN:
                //resume PlayBack
                if(mediaPlayer != null) {
                    initMediaPlayer();
                }
                else if(!mediaPlayer.isPlaying()){
                    mediaPlayer.start();
                }
                mediaPlayer.setVolume(1.0f,1.0f);
                break;
            case AudioManager.AUDIOFOCUS_LOSS:
                // Lost focus for an unbounded amount of time: stop playback and release media player
                if(mediaPlayer.isPlaying()) { mediaPlayer.stop();}
                mediaPlayer.release();
                mediaPlayer = null;
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                // Lost focus for a short time, but we have to stop
                // playback. We don't release the media player because playback
                // is likely to resume
                if(mediaPlayer.isPlaying()) {mediaPlayer.pause();}
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                if(mediaPlayer.isPlaying()) { mediaPlayer.setVolume(0.1f ,0.1f);}
                break;
        }
    }

    /**
     * AudioFocus
     */
    public boolean requestAudioFocus(){
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        int result = audioManager.requestAudioFocus(this,AudioManager.STREAM_MUSIC,AudioManager.AUDIOFOCUS_GAIN);

        if(result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            //focus gained
            return true;
        }
        // Could not gain Focus
        return false;
    }

    public boolean removeAudioFocus() {
        return AudioManager.AUDIOFOCUS_REQUEST_GRANTED == audioManager.abandonAudioFocus(this);
    }

    /**
     * MediaPlayer Actions
     */
    private void initMediaPlayer() {
        if(mediaPlayer != null) {
            mediaPlayer = new MediaPlayer(); // new instance
        }

        // Event Listeners
        mediaPlayer.setOnCompletionListener(this);
        mediaPlayer.setOnErrorListener(this);
        mediaPlayer.setOnPreparedListener(this);
        mediaPlayer.setOnBufferingUpdateListener(this);
        mediaPlayer.setOnSeekCompleteListener(this);
        mediaPlayer.setOnInfoListener(this);

        //reset so that media player is not pointing to another datasource
        mediaPlayer.reset();

        mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);

        try {
            // Set the data source to the mediaFile location
            mediaPlayer.setDataSource(currentAudio.getData());
        } catch (IOException e) {
            e.printStackTrace();
            stopSelf();
        }
        mediaPlayer.prepareAsync();
    }

    private void playMedia() {
        if(!mediaPlayer.isPlaying()) {
            mediaPlayer.start();
        }
    }

    private void stopMedia() {
        if(mediaPlayer == null) {
            return;
        }
        if (mediaPlayer.isPlaying()) {
            mediaPlayer.stop();
        }
    }

    private void pauseMedia() {
        if (mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            resumePostion = mediaPlayer.getCurrentPosition();
        }
    }

    private void resumeMedia() {
        if(!mediaPlayer.isPlaying()) {
            mediaPlayer.seekTo(resumePostion);
            mediaPlayer.start();
        }
    }

    private void skipToNext() {

        if(audioIndex == audioArrayList.size() -1) {
            audioIndex = 0;
            currentAudio = audioArrayList.get(audioIndex);
        } else {
            currentAudio = audioArrayList.get(++audioIndex);
        }
        //Update stored index
        new StorageUtil(getApplicationContext()).storeAudioIndex(audioIndex);

        stopMedia();
        //resetMedia Player
        mediaPlayer.reset();
        initMediaPlayer();
    }

    private void skipToPrevious() {

        if (audioIndex == 0) {
            //if first in playlist
            //set index to the last of audioList
            audioIndex = audioArrayList.size() - 1;
            currentAudio = audioArrayList.get(audioIndex);
        } else {
            //get previous in playlist
            currentAudio = audioArrayList.get(--audioIndex);
        }

        //Update stored index
        new StorageUtil(getApplicationContext()).storeAudioIndex(audioIndex);

        stopMedia();
        //reset mediaPlayer
        mediaPlayer.reset();
        initMediaPlayer();
    }

    /**
     * ACTION_AUDIO_BECOMING_NOISY -- change in audio outputs
     */

    private BroadcastReceiver becomingNoisyReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            //Pause audio on becoming noisy
            pauseMedia();
            buildNotification(PlaybackState.STATE_PAUSED);
        }
    };

    private void registerBecomingNoisyReceiver(){
        //register after getting audio focus
        IntentFilter intentFilter = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        registerReceiver(becomingNoisyReceiver,intentFilter);
    }

    /** handle phonestate change
     *
     */
    private void callStateListener() {
        //telephony manager
        telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        //start listening for phone state change

        phoneStateListener = new PhoneStateListener() {
            @Override
            public void onCallStateChanged(int state, String incomingNumber) {

                switch (state) {
                    //if at least one call exists or the phone is ringing
                    //pause the MediaPlayer
                    case TelephonyManager.CALL_STATE_RINGING:
                    case TelephonyManager.CALL_STATE_OFFHOOK:
                        if (mediaPlayer != null) {
                            pauseMedia();
                            onGoingCall = true;
                        }
                        break;
                    case TelephonyManager.CALL_STATE_IDLE:
                        //phone idle -- start my mix so i can jam on
                        if (mediaPlayer != null) {
                            if (onGoingCall) {
                                onGoingCall = false;
                                resumeMedia();
                            }
                        }
                        break;
                }
            }
        };

        //Register the listener with telephony manager so it can listen to the call state
        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
    }

    /**
     *  MediaSession and notification actions
     */

    private void initMediaSession() throws RemoteException {

        if(mediaSession != null) { return; }

        mediaSessionManager = (MediaSessionManager) getSystemService(Context.MEDIA_SESSION_SERVICE);

        //create a new media session
        mediaSession = new MediaSessionCompat(getApplicationContext(),"AudioPlayer");
        // get transport controls
        transportControls = mediaSession.getController().getTransportControls();
        //set media session -- ready to receive media commands
        mediaSession.setActive(true);
        // indicate that media session will handle the transport controls
        mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);

        //set mediaSessions meta data
        updateMetaData();

        //Attach callback to recieve mediasession updates
        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override
            public void onPlay() {
                super.onPlay();

                resumeMedia();
                buildNotification(PlaybackState.STATE_PLAYING);
            }

            @Override
            public void onPause() {
                super.onPause();
                pauseMedia();
                buildNotification(PlaybackState.STATE_PAUSED);
            }

            @Override
            public void onSkipToNext() {
                super.onSkipToNext();

                skipToNext();
                updateMetaData();
                buildNotification(PlaybackState.STATE_PLAYING);
            }

            @Override
            public void onSkipToPrevious() {
                super.onSkipToPrevious();
                skipToPrevious();
                updateMetaData();
                buildNotification(PlaybackState.STATE_PLAYING);
            }

            @Override
            public void onStop() {
                super.onStop();
                removeNotification();
                //stop service
                stopSelf();
            }

            @Override
            public void onSeekTo(long pos) {
                super.onSeekTo(pos);
            }
        });
    }

    private void updateMetaData(){
        Bitmap albumArt = BitmapFactory.decodeResource(getResources(),R.drawable.ic_launcher_background); // to replace with album art
        //update the current metadata
        mediaSession.setMetadata( new MediaMetadataCompat.Builder()
                                    .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART,albumArt)
                                    .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, currentAudio.getArtist())
                                    .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, currentAudio.getAlbum())
                                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, currentAudio.getTitle())
                                    .build());
    }

    private void buildNotification(int playbackState) {
        int notificationAction = android.R.drawable.ic_media_pause;//needs to be initialized
        PendingIntent play_pauseAction = null;

        //Build notification based on playstate

        if(playbackState == PlaybackState.STATE_PLAYING) {
            notificationAction = android.R.drawable.ic_media_pause;
            //create the pause action
            play_pauseAction = playbackAction(1);
        }
        else if (playbackState == PlaybackState.STATE_PAUSED) {
            notificationAction = android.R.drawable.ic_media_play;
            //create the play action
            play_pauseAction = playbackAction(0);
        }

        Bitmap largeIcon = BitmapFactory.decodeResource(getResources(),
                R.drawable.ic_launcher_background); //replace with your own image

        //create a new notification

        NotificationCompat.Builder notificationBuilder = (NotificationCompat.Builder)  new NotificationCompat.Builder(this)
                //hide the timestamp
                .setShowWhen(false)
                //set the notification style
                .setStyle(new NotificationCompat.MediaStyle()
                        //Attach our media session
                        .setMediaSession(mediaSession.getSessionToken())
                        //Show our controls
                        .setShowActionsInCompactView(0,1,2))
                //set notification color
                .setColor(getResources().getColor(R.color.colorAccent))
                //set large and small icons
                .setLargeIcon(largeIcon)
                .setSmallIcon(android.R.drawable.stat_sys_headset)
                //set Notification content information
                .setContentText(currentAudio.getArtist())
                .setContentTitle(currentAudio.getAlbum())
                .setContentInfo(currentAudio.getTitle())
                // Add playback actions
                .addAction(android.R.drawable.ic_media_previous, "previous", playbackAction(3))
                .addAction(notificationAction, "pause", play_pauseAction)
                .addAction(android.R.drawable.ic_media_next, "next", playbackAction(2));

        ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).notify(NOTIFICATION_ID, notificationBuilder.build());
    }


    private PendingIntent playbackAction(int actionNumber) {
        Intent playbackAction = new Intent(this, MediaPlayerService.class);
        switch (actionNumber) {
            case 0:
                // Play
                playbackAction.setAction(ACTION_PLAY);
                return PendingIntent.getService(this, actionNumber, playbackAction, 0);
            case 1:
                // Pause
                playbackAction.setAction(ACTION_PAUSE);
                return PendingIntent.getService(this, actionNumber, playbackAction, 0);
            case 2:
                // Next track
                playbackAction.setAction(ACTION_NEXT);
                return PendingIntent.getService(this, actionNumber, playbackAction, 0);
            case 3:
                // Previous track
                playbackAction.setAction(ACTION_PREVIOUS);
                return PendingIntent.getService(this, actionNumber, playbackAction, 0);
            default:
                break;
        }
        return null;
    }

    private void removeNotification() {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(NOTIFICATION_ID);
    }

    private void handleIncomingActions(Intent playBackAction) {

        if(playBackAction == null || playBackAction.getAction() == null) {
            return;
        }

        String stringAction = playBackAction.getAction();
        if(stringAction.equalsIgnoreCase(ACTION_PLAY)) {
            transportControls.play();
        } else if (stringAction.equalsIgnoreCase(ACTION_PAUSE)) {
            transportControls.pause();
        } else if (stringAction.equalsIgnoreCase(ACTION_NEXT)) {
            transportControls.skipToNext();
        } else if (stringAction.equalsIgnoreCase(ACTION_PREVIOUS)) {
            transportControls.skipToPrevious();
        } else if (stringAction.equalsIgnoreCase(ACTION_STOP)) {
            transportControls.stop();
        }
    }

    /**
     * Play new Audio
     */

    private BroadcastReceiver playNewAudio = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            //get new media from sharedPreference
            audioIndex = new StorageUtil(getApplicationContext()).loadAudioIndex();

            if(audioIndex != -1 && audioIndex < audioArrayList.size()) {
                //valid range
                currentAudio = audioArrayList.get(audioIndex);
            } else {
                stopSelf();
            }

            //A PLAY_NEW_AUDIO action received
            //reset mediaPlayer to play the new Audio
            stopMedia();
            mediaPlayer.reset();
            initMediaPlayer();
            updateMetaData();
            buildNotification(PlaybackState.STATE_PLAYING);
        }
    };


    private void register_playNewAudio() {
        // Register new media player
        IntentFilter intentFilter = new IntentFilter(MainActivity.Broadcast_PLAY_NEW_AUDIO);
        registerReceiver(playNewAudio,intentFilter);
    }

}
