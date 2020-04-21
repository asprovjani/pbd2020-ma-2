package si.uni_lj.fri.pbd.miniapp2;

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
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.util.Random;

public class MediaPlayerService extends Service {
    private static final String TAG = "MediaPlayerService";
    private static final String channelId = "MediaServicePlayer_background";
    private static final int NOTIFICATION_ID = 1;
    public static final String PLAYER_START = "Start MediaPlayerService";
    public static final String PLAYER_STOP = "Stop MediaPlayerService";

    private static final String[] fileNames = {
            "Rage Against The Machine - People Of The Sun.mp3",
            "Rage Against The Machine - Tire Me.mp3",
            "Rage Against The Machine - Voice of the Voiceless.mp3"
    };

    private boolean isMediaPlayerRunning;
    private boolean isPaused;
    private int fileIndex = -1;
    private MediaPlayer mp;


    private final IBinder serviceBinder = new RunServiceBinder();

    @Override
    public void onCreate() {
        Log.d(TAG, "Creating MediaPlayerService");

        isMediaPlayerRunning = false;

        //createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Starting MediaPlayerService");

        if(intent.getAction() == PLAYER_STOP) {
            stopForeground(true);
            stopSelf();
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
            stop();
    }

    public void play() {
        if(!isMediaPlayerRunning) {
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
                isMediaPlayerRunning = true;
            } catch (Exception e) {
                Log.d(TAG, "play: Error");
                e.printStackTrace();
            }
        }
        else {
            Log.e(TAG, "already playing song");
        }
    }

    public void pause() {
        if(mp != null) {
            isPaused = true;
            isMediaPlayerRunning = false;
            mp.pause();
        }
    }

    public void stop() {
        stopPlayer();
    }

    public void stopPlayer() {
        if(mp != null) {
            mp.release();
            mp = null;
            isPaused = false;
            isMediaPlayerRunning = false;
            Toast.makeText(this, "Player Stopped", Toast.LENGTH_LONG).show();
        }
    }

    /*
    private Notification createNotification() {
        Intent actionIntent = new Intent(this, MediaPlayerService.class);
        actionIntent.setAction(PLAYER_STOP);
        PendingIntent actionPendingIntent =
                PendingIntent.getService(this, 0, actionIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelId)
                .setContentTitle(getString(R.string.song))
                .setContentText(getString(R.string.songDuration))
                .setSmallIcon(R.mipmap.ic_launcher)
                .setChannelId(channelId)
                .addAction(android.R.drawable.ic_media_pause, "Stop", actionPendingIntent);

        Intent resultIntent = new Intent(this, MainActivity.class);
        PendingIntent resultPendingIntent =
                PendingIntent.getActivity(this, 0, resultIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        builder.setContentIntent(resultPendingIntent);

        return builder.build();
    }

    private void createNotificationChannel() {
        if(Build.VERSION.SDK_INT < 26) {
            return;
        }
        else {
            NotificationChannel channel = new NotificationChannel(MediaPlayerService.channelId,
                    "Foreground service channel", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription(getString(R.string.channelDescription));

            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            manager.createNotificationChannel(channel);
        }
    }

    public void foreground() {
        startForeground(NOTIFICATION_ID, createNotification());
    }


    public void background() {
        stopForeground(true);
    }
     */

    public boolean isMediaPlayerRunning() {
        return isMediaPlayerRunning;
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
            duration += (int) Math.floor(elapsedTimeSeconds/60) + ":" + elapsedTimeSeconds%60 + "/";
        }

        duration += durationMinutes + ":";
        if((mp.getDuration()/1000)%60 == 0)
            duration += "00";
        else
            duration += (mp.getDuration()/1000)%60;

        return duration;
    }

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
}
