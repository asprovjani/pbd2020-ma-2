package si.uni_lj.fri.pbd.miniapp2;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

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
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.lang.ref.WeakReference;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private MediaPlayerService mpService;
    private boolean serviceBound;

    private final Handler updateTimeHandler = new UIUpdateHandler(this);
    private final static int MSG_UPDATE = 0;

    Button btnPlay, btnPause, btnStop, btngOn, btngOff, btnExit;
    TextView songTitle, songDuration;
    BroadcastReceiver receiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnPlay = (Button) findViewById(R.id.btnPlay);
        btnPause = (Button) findViewById(R.id.btnPause);
        btnStop = (Button) findViewById(R.id.btnStop);
        btngOn = (Button) findViewById(R.id.btngOn);
        btngOff = (Button) findViewById(R.id.btngOff);
        btnExit = (Button) findViewById(R.id.btnExit);
        songTitle = (TextView) findViewById(R.id.songTitle);
        songDuration = (TextView) findViewById(R.id.songDuration);

        registerBroadcastReceiver();
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    protected void onStart() {
        super.onStart();

        Log.d(TAG, "Starting and binding MediaPlayerService");

        Intent i = new Intent(this, MediaPlayerService.class);
        i.setAction(MediaPlayerService.PLAYER_START);
        startForegroundService(i);

        bindService(i, connection, 0);
    }

    @Override
    protected void onStop() {
        super.onStop();
        updateUIStopRun();

        if(serviceBound) {
            if(!mpService.isPlaying() && !mpService.isPaused()) {
                stopService(new Intent(this, MediaPlayerService.class));
            }

            unbindService(connection);
            serviceBound = false;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(receiver != null) {
            unregisterReceiver(receiver);
            receiver = null;
        }
    }

    /**
     * BroadcastReceiver for receiving commands from MediaPlayerService
     */
    private void registerBroadcastReceiver() {
        IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        filter.addAction("APP_EXIT");

        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if(intent.getAction() == "APP_EXIT") {
                    exit(null);
                }
            }
        };

        registerReceiver(receiver, filter);
    }

    /**
     * ServiceConnection for binding to MediaPlayerService
     */
    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG, "MediaPlayerService bound");

            MediaPlayerService.RunServiceBinder binder = (MediaPlayerService.RunServiceBinder) service;
            mpService = binder.getService();
            mpService.foreground();
            serviceBound = true;
            if(mpService.isPlaying())
                updateUIStartRun();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "MediaPlayerService disconnect");

            serviceBound = false;
        }
    };

    //==================================== GESTURES ==========================================//

    public void gesturesOn(View v) {
        Toast.makeText(this, "Gestures On", Toast.LENGTH_LONG).show();
        mpService.startAccelerationService();
    }

    public void gesturesOff(View v) {
        Toast.makeText(this, "Gestures Off", Toast.LENGTH_LONG).show();
        mpService.stopAccelerationService();
    }

    //========================================================================================//

    //================================== MEDIA PLAYER ========================================//

    public void play(View v) {
        if(serviceBound) {
            mpService.play();
            updateUIStartRun();
        }
    }

    public void pause(View v) {
        if(serviceBound) {
            mpService.pause();
        }
    }

    public void stop(View v) {
        if(serviceBound) {
            mpService.stopPlayer();
            updateUIStopRun();
        }
    }

    public void exit(View v) {

        if(mpService.accSerivce != null)
            mpService.stopAccelerationService();

        mpService.stopForeground(true);
        stopService(new Intent(this, MediaPlayerService.class));
        if(serviceBound) {
            unbindService(connection);
            serviceBound = false;
        }

        finishAndRemoveTask();
        System.exit(0);
    }

    //========================================================================================//

    //===================================== UI ===============================================//

    private void updateUIStartRun() {
        updateTimeHandler.sendEmptyMessage(MSG_UPDATE);
    }

    private void updateUIStopRun() {
        updateTimeHandler.removeMessages(MSG_UPDATE);
        songTitle.setText(R.string.song);
        songDuration.setText(R.string.songDuration);
    }

    private void updateSongTimer() {
        if(serviceBound) {
            if(mpService.isPlaying()) {
                songDuration.setText(mpService.songDuration());
                songTitle.setText(mpService.songTitle());
            }
        }
    }

    static class UIUpdateHandler extends Handler {
            private final static int UPDATE_RATE_MS = 1000;
            private final WeakReference<MainActivity> activity;

            UIUpdateHandler(MainActivity activity) {
                this.activity = new WeakReference<>(activity);
            }

        @Override
        public void handleMessage(Message msg) {
            if(MSG_UPDATE == msg.what) {
                //Log.d(TAG, "updating time");

                activity.get().updateSongTimer();
                activity.get().mpService.updateNotification();
                sendEmptyMessageDelayed(MSG_UPDATE, UPDATE_RATE_MS);
            }
        }
    }

}
