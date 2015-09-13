package com.animbus.music.media;

import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.drm.DrmStore;
import android.media.AudioManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.SystemClock;
import android.support.annotation.Nullable;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.Toast;

import com.animbus.music.media.objects.Song;

import java.util.List;

import static android.support.v4.media.session.PlaybackStateCompat.ACTION_PAUSE;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_PLAY;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_PLAY_PAUSE;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_SEEK_TO;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_SKIP_TO_NEXT;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_SKIP_TO_QUEUE_ITEM;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_STOP;
import static android.support.v4.media.session.PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_NONE;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_PAUSED;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_PLAYING;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_SKIPPING_TO_NEXT;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_SKIPPING_TO_PREVIOUS;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_SKIPPING_TO_QUEUE_ITEM;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_STOPPED;

/**
 * WARNING: This class is extremely experimental. This file was created on 7/18/2015 by Adrian.
 */
public class MediaService extends Service {
    private final static String TAG = "MediaService:";
    private final IBinder mBinder = new MusicBinder();
    //The media session
    MediaSessionCompat mSession;

    //The playback state
    PlaybackStateCompat mState;

    AudioManager mAudioManager;
    ComponentName mButtonReceiver;
    PendingIntent mButtonReceivedIntent;
    //The notification
    private MediaNotification mNotification;
    // Indicates whether the service was started.
    private boolean mServiceStarted;
    private Bundle mSessionExtras;
    private PlaybackManager mPlayback;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    public class MusicBinder extends Binder {
        public MediaService getService(){
            return MediaService.this;
        }
    }

    public void stopService() {
        stopSelf();
    }

    public void setAsForeground() {
        startForeground(MediaNotification.NOTIFICATION_ID, mNotification.getNotification());
    }

    public void setUp(){
        mPlayback = PlaybackManager.from(this);
        mNotification = new MediaNotification(this);
        mPlayback.mNotification = mNotification;

        mButtonReceiver = new ComponentName(getPackageName(), MediaButtonReceiver.class.getName());
        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        mButtonReceivedIntent = PendingIntent.getBroadcast(
                this,
                0,
                new Intent(Intent.ACTION_MEDIA_BUTTON),
                PendingIntent.FLAG_UPDATE_CURRENT
        );
        mSession = new MediaSessionCompat(this, TAG, mButtonReceiver, mButtonReceivedIntent);
        mSession.setPlaybackToLocal(AudioManager.STREAM_MUSIC);
        mSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS |
                MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
        mSession.setCallback(new MediaSessionCallback());
        setState(STATE_NONE);
        mSession.setPlaybackState(mState);
        mSession.setMediaButtonReceiver(mButtonReceivedIntent);
        mSession.setActive(true);
    }

    public void updateMetadata(MediaMetadataCompat metadata){
        mSession.setMetadata(metadata);
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");

        // Service is being killed, so make sure we release our resources


        // Always release the MediaSession to clean up resources
        // and notify associated MediaController(s).
        mSession.release();
    }

    public void updateQueue() {
        mSession.setQueue(QueueManager.get().getCurrentQueueAsQueueItem());
    }

    public void setState(int state) {
        mState = new PlaybackStateCompat.Builder()
                .setActions(
                        ACTION_PLAY |
                                ACTION_PAUSE |
                                ACTION_PLAY_PAUSE |
                                ACTION_SKIP_TO_NEXT |
                                ACTION_SKIP_TO_PREVIOUS |
                                ACTION_STOP |
                                ACTION_PLAY_FROM_MEDIA_ID |
                                ACTION_PLAY_FROM_SEARCH |
                                ACTION_SKIP_TO_QUEUE_ITEM |
                                ACTION_SEEK_TO)
                .setState(state, PLAYBACK_POSITION_UNKNOWN, 1.0f, SystemClock.elapsedRealtime())
                .build();
        for (PlaybackManager.OnChangedListener l : mPlayback.listeners){
            l.onPlaybackStateChanged(mState);
        }
    }

    public class MediaSessionCallback extends MediaSessionCompat.Callback {

        @Override
        public void onPlay() {
            Log.d(TAG, "Play");
            mPlayback.resume();
            setState(STATE_PLAYING);
        }

        @Override
        public void onPause() {
            Log.d(TAG, "Pause");
            mPlayback.pause();
            setState(STATE_PAUSED);
        }

        @Override
        public void onSkipToNext() {
            super.onSkipToNext();
            mPlayback.playNext();
            setState(STATE_SKIPPING_TO_NEXT);
        }

        @Override
        public void onSkipToPrevious() {
            super.onSkipToPrevious();
            mPlayback.playPrev(true);
            setState(STATE_SKIPPING_TO_PREVIOUS);
        }

        @Override
        public void onSeekTo(long pos) {
            Log.d(TAG, "onSeekTo" + pos);
            mPlayback.seekTo((int) pos);
            Toast.makeText(MediaService.this, "SeekTo " + pos, Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onSkipToQueueItem(long id) {
            super.onSkipToQueueItem(id);
            mPlayback.play(Song.getFromID(id));
            setState(STATE_SKIPPING_TO_QUEUE_ITEM);
        }

        public void onTogglePlay() {
            if (mState.getState() == STATE_PLAYING) {
                onPause();
            } else {
                onPlay();
            }
        }

        @Override
        public void onStop() {
            super.onStop();
            mPlayback.stop();
            setState(STATE_STOPPED);
        }

        @Override
        public boolean onMediaButtonEvent(Intent mediaButtonEvent) {
            boolean handled = false;
            if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(mediaButtonEvent.getAction())) {
                Log.d(TAG, "Headphones disconnected.");
                if (mState.getState() == STATE_PLAYING) {
                    onPause();
                    handled = true;
                }
            } else if (Intent.ACTION_MEDIA_BUTTON.equals(mediaButtonEvent.getAction())) {
                KeyEvent keyEvent = (KeyEvent) mediaButtonEvent.getExtras().get(Intent.EXTRA_KEY_EVENT);
                switch (keyEvent.getKeyCode()) {
                    case KeyEvent.KEYCODE_MEDIA_PLAY:
                        onPlay();
                        handled = true;
                        break;
                    case KeyEvent.KEYCODE_MEDIA_PAUSE:
                        onPause();
                        handled = true;
                        break;
                    case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                        onTogglePlay();
                        handled = true;
                        break;
                    case KeyEvent.KEYCODE_MEDIA_NEXT:
                        onSkipToNext();
                        handled = true;
                        break;
                    case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                        onSkipToPrevious();
                        handled = true;
                        break;
                    case KeyEvent.KEYCODE_MEDIA_STOP:
                        onStop();
                        handled = true;
                        break;
                }
            }
            return handled;
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////

    public MediaSessionCompat getSession(){
        return mSession;
    }

    public PlaybackStateCompat getStateObj(){
        return mState;
    }

    public int getState(){
        return mState.getState();
    }

    public MediaSessionCallback getCallback() {
        return new MediaSessionCallback();
    }

    public PlaybackManager getPlaybackManager(){
        return mPlayback;
    }

}
