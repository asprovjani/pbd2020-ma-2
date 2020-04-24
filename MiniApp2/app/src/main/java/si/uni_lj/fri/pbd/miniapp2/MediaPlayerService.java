package si.uni_lj.fri.pbd.miniapp2;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.res.AssetFileDescriptor;
import android.media.MediaPlayer;
import android.net.ConnectivityManager;
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

import static android.content.ContentValues.TAG;

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

    public AccelerationService accSerivce;
    boolean accelerationServiceBound;
    private BroadcastReceiver receiver;

    @Override
    public void onCreate() {
        Log.d(TAG, "Creating MediaPlayerService");

        isPaused = false;
        isPlaying = false;

        registerBroadcastReceiver();

        //Initialize intents for notification
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
                //send broadcast to MainActivity to exit the App
                Intent exitApp = new Intent();
                exitApp.setAction("APP_EXIT");
                sendBroadcast(exitApp);
                //Stop MediaPlayerService
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
        if(accSerivce != null)
            stopAccelerationService();
        if(receiver != null) {
            unregisterReceiver(receiver);
            receiver = null;
        }
    }

    public void foreground() {
        startForeground(NOTIFICATION_ID, createNotification());
    }

    /**
     * BroadcastReceiver for receiving commands from AccelerationService
     */
    private void registerBroadcastReceiver() {
        IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        filter.addAction("VERTICAL");
        filter.addAction("HORIZONTAL");

        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if(intent.getAction().equals("VERTICAL")) {
                    play();
                }
                if(intent.getAction().equalsIgnoreCase("HORIZONTAL")) {
                    pause();
                }
            }
        };

        registerReceiver(receiver, filter);
    }


    public class RunServiceBinder extends Binder {
        MediaPlayerService getService() {
            return MediaPlayerService.this;
        }
    }

    //=============================== MEDIA PLAYER ===========================================//

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
                }

                //if the player was stopped start new random song
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
        Toast.makeText(this, "Player Started", Toast.LENGTH_LONG).show();
        updateNotificationButtons("PAUSE_STOP_EXIT");
        updateNotificationHandler.sendEmptyMessage(MSG_UPDATE);
    }

    public void pause() {
        if(mp != null) {
            isPaused = true;
            mp.pause();
            Toast.makeText(this, "Player Paused", Toast.LENGTH_LONG).show();
            updateNotificationButtons("PLAY_STOP_EXIT");
        }
    }

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

    //========================================================================================//

    //============================== ACCELERATION SERVICE ====================================//

    public void startAccelerationService() {
        Log.d(TAG, "startAccelerationService: Starting and binding AccelerationService");
        Intent i = new Intent(this, AccelerationService.class);
        startService(i);
        bindService(i, connection, 0);
    }

    public void stopAccelerationService() {
        if(accelerationServiceBound) {
            stopService(new Intent(this, AccelerationService.class));
            unbindService(connection);
            accelerationServiceBound = false;
        }
    }

    /**
     * ServiceConnection for binding to AccelerationService
     */
    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG, "AccelerationService bound");

            AccelerationService.RunServiceBinder binder = (AccelerationService.RunServiceBinder) service;
            accSerivce = binder.getService();
            accelerationServiceBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "AccelerationService disconnect");

            accelerationServiceBound = false;
        }
    };

    //========================================================================================//

    //================================== NOTIFICATION ========================================//

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

    /**
     * Update notification buttons based on current MediaPlayer state
     *
     * @param state
     */
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

    /**
     * Handler for updating notification text
     */
    static class NotificationUpdateHandler extends Handler {
        private final static int UPDATE_RATE_MS = 1000;
        private final WeakReference<MediaPlayerService> service;

        NotificationUpdateHandler(MediaPlayerService service) {
            this.service = new WeakReference<>(service);
        }

        @Override
        public void handleMessage(Message msg) {
            if(MSG_UPDATE == msg.what) {
                //Log.d(TAG, "updating time");

                service.get().updateNotification();
                sendEmptyMessageDelayed(MSG_UPDATE, UPDATE_RATE_MS);
            }
        }
    }
}
