package si.uni_lj.fri.pbd.miniapp2;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.Random;

public class MediaPlayerService extends Service {
    private static final String TAG = "MediaPlayerService";
    private static final String channelId = "MediaServicePlayer_background";
    private static final int NOTIFICATION_ID = 1;
    public static final String PLAYER_START = "Start MediaPlayerService";
    public static final String PLAYER_PLAY = "Play MediaPlayerService";
    public static final String PLAYER_PAUSE = "Pause MediaPlayerService";
    public static final String PLAYER_STOP = "Stop MediaPlayerService";
    public static final String PLAYER_EXIT = "Exit MediaPlayerService";

    private static final String[] fileNames = {
            "Rage Against The Machine - People Of The Sun.mp3",
            "Rage Against The Machine - Tire Me.mp3",
            "Rage Against The Machine - Voice of the Voiceless.mp3"
    };

    private boolean isMediaPlayerRunning;
    private boolean isPaused;
    private boolean isPlaying;
    private int fileIndex = -1;
    private MediaPlayer mp;


    private final IBinder serviceBinder = new RunServiceBinder();
    NotificationCompat.Builder builder;
    NotificationManager manager;

    private final Handler updateNotificationHandler = new NotificationUpdateHandler(this);
    private final static int MSG_UPDATE = 0;

    Intent playIntent, pauseIntent, stopIntent, exitIntent;
    PendingIntent playPendingIntent, pausePendingIntent, stopPendingIntent, exitPendingIntent;

    @Override
    public void onCreate() {
        Log.d(TAG, "Creating MediaPlayerService");

        isPaused = false;
        isPlaying = false;

        playIntent = new Intent(this, MediaPlayerService.class);
        playIntent.setAction(PLAYER_PLAY);
        playPendingIntent =
                PendingIntent.getService(this, 0, playIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        pauseIntent = new Intent(this, MediaPlayerService.class);
        pauseIntent.setAction(PLAYER_PAUSE);
        pausePendingIntent =
                PendingIntent.getService(this, 0, pauseIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        stopIntent = new Intent(this, MediaPlayerService.class);
        stopIntent.setAction(PLAYER_STOP);
        stopPendingIntent =
                PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        exitIntent = new Intent(this, MediaPlayerService.class);
        exitIntent.setAction(PLAYER_EXIT);
        exitPendingIntent =
                PendingIntent.getService(this, 0, exitIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Starting MediaPlayerService");

        switch (intent.getAction()) {
            case PLAYER_PLAY:
                play();
                updateNotificationButtons("PAUSE_STOP_EXIT");
                break;
            case PLAYER_PAUSE:
                pause();
                updateNotificationButtons("PLAY_STOP_EXIT");
                break;
            case PLAYER_STOP:
                stopPlayer();
                updateNotificationButtons("PLAY_STOP_EXIT");
                break;
            case PLAYER_EXIT:
                Intent exitApp = new Intent();
                exitApp.setAction("APP_EXIT");
                sendBroadcast(exitApp);
                if(isPlaying || !isPaused)
                    stopPlayer();
                stopForeground(true);
                stopSelf();
                break;
        }

        return Service.START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "Binding MediaPlayerService");

        return this.serviceBinder;
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "Destroying MediaPlayerService");
        super.onDestroy();
        if(mp != null)
            stopPlayer();
    }

    public void play() {
        if(!isPlaying) {
            try {
                if(mp == null) {
                    mp = new MediaPlayer();
                    //after song ends, stop player
                    mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                        @Override
                        public void onCompletion(MediaPlayer mp) {
                            stopPlayer();
                        }
                    });
                    isMediaPlayerRunning = true;
                }
                if(!isPaused) {
                    fileIndex = randomIndex();
                    AssetFileDescriptor descriptor = getAssets().openFd(fileNames[fileIndex]);
                    mp.setDataSource(descriptor.getFileDescriptor(), descriptor.getStartOffset(), descriptor.getLength());
                    descriptor.close();
                    mp.prepare();
                }

                mp.start();
                isPaused = false;
                isPlaying = true;
            } catch (Exception e) {
                Log.d(TAG, "play: Error");
                e.printStackTrace();
            }
        }
        else {
            mp.start();
            isPaused = false;
        }

        updateNotificationButtons("PAUSE_STOP_EXIT");
        updateNotificationHandler.sendEmptyMessage(MSG_UPDATE);
    }

    public void pause() {
        if(mp != null) {
            isPaused = true;
            mp.pause();
            updateNotificationButtons("PLAY_STOP_EXIT");
        }
    }

    /*public void stop() {
        stopPlayer();
    }*/

    public void stopPlayer() {
        if(mp != null) {
            mp.release();
            mp = null;
            isPaused = false;
            isPlaying = false;
            Toast.makeText(this, "Player Stopped", Toast.LENGTH_LONG).show();
            builder.setContentTitle(getString(R.string.song))
                   .setContentText(getString(R.string.songDuration));
            manager.notify(NOTIFICATION_ID, builder.build());
            updateNotificationButtons("PLAY_STOP_EXIT");
            updateNotificationHandler.removeMessages(MSG_UPDATE);
        }
    }

    private Notification createNotification() {
        builder = new NotificationCompat.Builder(this, channelId)
                .setContentTitle(getString(R.string.song))
                .setContentText(getString(R.string.songDuration))
                .setSmallIcon(R.mipmap.ic_launcher)
                .setChannelId(channelId)
                .addAction(android.R.drawable.ic_media_play, "Play", playPendingIntent)
                .addAction(android.R.drawable.btn_default, "Stop", stopPendingIntent)
                .addAction(android.R.drawable.ic_delete, "Exit", exitPendingIntent);

        Intent resultIntent = new Intent(this, MainActivity.class);
        PendingIntent resultPendingIntent =
                PendingIntent.getActivity(this, 0, resultIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        builder.setContentIntent(resultPendingIntent);

        return builder.build();
    }

    public void updateNotification() {
        if(isPlaying) {
            builder.setContentTitle(songTitle())
                   .setContentText(songDuration());

            manager.notify(NOTIFICATION_ID, builder.build());
        }
    }

    @SuppressLint("RestrictedApi")
    private void updateNotificationButtons(String state) {
        if(state.equals("PLAY_STOP_EXIT")) {
            builder.mActions.clear();
            builder.addAction(android.R.drawable.ic_media_play, "Play", playPendingIntent)
                    .addAction(android.R.drawable.btn_default, "Stop", stopPendingIntent)
                    .addAction(android.R.drawable.ic_delete, "Exit", exitPendingIntent);
        }
        else {
            builder.mActions.clear();
            builder.addAction(android.R.drawable.ic_media_pause, "Pause", pausePendingIntent)
                    .addAction(android.R.drawable.btn_default, "Stop", stopPendingIntent)
                    .addAction(android.R.drawable.ic_delete, "Exit", exitPendingIntent);
        }

        manager.notify(NOTIFICATION_ID, builder.build());
    }

    private void createNotificationChannel() {
        if(Build.VERSION.SDK_INT < 26) {
            return;
        }
        else {
            NotificationChannel channel = new NotificationChannel(MediaPlayerService.channelId,
                    "Foreground service channel", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription(getString(R.string.channelDescription));

            manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            manager.createNotificationChannel(channel);
        }
    }

    public void foreground() {
        startForeground(NOTIFICATION_ID, createNotification());
    }

    public boolean isMediaPlayerRunning() {
        return isMediaPlayerRunning;
    }

    public boolean isPaused() {
        return isPaused;
    }

    public boolean isPlaying() {
        return isPlaying;
    }

    /**
     * Returns random index to read file from fileNames[]
     * @return random index
     */
    private int randomIndex() {
        return new Random().nextInt(3);
    }

    /**
     * Returns String for the duration of the current song
     * @return song duration
     */
    public String songDuration() {
        int elapsedTimeSeconds = mp.getCurrentPosition()/1000;
        int durationMinutes = (int) Math.floor(mp.getDuration()/1000/60);

        String duration = "";
        if(elapsedTimeSeconds < 60) {
            if(elapsedTimeSeconds < 10)
                duration += "00:0" + elapsedTimeSeconds + "/";
            else
                duration += "00:" + elapsedTimeSeconds + "/";
        }
        else {
            if(elapsedTimeSeconds%60 < 10)
                duration += (int) Math.floor(elapsedTimeSeconds/60) + ":0" + elapsedTimeSeconds%60 + "/";
            else
                duration += (int) Math.floor(elapsedTimeSeconds/60) + ":" + elapsedTimeSeconds%60 + "/";
        }

        duration += durationMinutes + ":";
        if((mp.getDuration()/1000)%60 == 0)
            duration += "00";
        else
            duration += (mp.getDuration()/1000)%60;

        return duration;
    }

    /**
     * Returns title of currently playing song
     * @return songTitle
     */
    public String songTitle() {
        if(fileIndex != -1)
            return fileNames[fileIndex];

        return  null;
    }

    public class RunServiceBinder extends Binder {
        MediaPlayerService getService() {
            return MediaPlayerService.this;
        }
    }

    static class NotificationUpdateHandler extends Handler {
        private final static int UPDATE_RATE_MS = 1000;
        private final WeakReference<MediaPlayerService> serivce;

        NotificationUpdateHandler(MediaPlayerService service) {
            this.serivce = new WeakReference<>(service);
        }

        @Override
        public void handleMessage(Message msg) {
            if(MSG_UPDATE == msg.what) {
                Log.d(TAG, "updating time");

                serivce.get().updateNotification();
                sendEmptyMessageDelayed(MSG_UPDATE, UPDATE_RATE_MS);
            }
        }
    }
}
