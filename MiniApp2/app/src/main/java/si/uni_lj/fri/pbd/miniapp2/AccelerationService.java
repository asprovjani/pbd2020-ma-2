package si.uni_lj.fri.pbd.miniapp2;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.nsd.NsdManager;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;

import java.lang.ref.WeakReference;

import static android.content.ContentValues.TAG;

public class AccelerationService extends Service implements SensorEventListener {

    private final IBinder serviceBinder = new RunServiceBinder();
    private SensorManager sensorManager;
    private Sensor accSensor;
    private String command = "IDLE";

    private float Xprev = 0;
    private float Yprev = 0;
    private float Zprev = 0;

    private final Handler stateHandler = new StateHandler(this);
    private final static int MSG_UPDATE = 0;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand: Starting AccelerationService");

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        sensorManager.registerListener(this, accSensor, SensorManager.SENSOR_DELAY_NORMAL);

        stateHandler.sendEmptyMessage(MSG_UPDATE);

        return Service.START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind: Binding AccelerationService");

        return this.serviceBinder;
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy: Destroying AccelerationService");

        sensorManager.unregisterListener(this);
        stateHandler.removeMessages(MSG_UPDATE);

        super.onDestroy();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {

        float noiseThreshold = 5;

        float Xt = event.values[0];
        float Yt = event.values[1];
        float Zt = event.values[2];

        float dX = Math.abs(Xprev - Xt);
        float dY = Math.abs(Yprev - Yt);
        float dZ = Math.abs(Zprev - Zt);

        Xprev = Xt;
        Yprev = Yt;
        Zprev = Zt;


        if(dX <= noiseThreshold)
            dX = 0;
        if(dY <= noiseThreshold)
            dY = 0;
        if(dZ <= noiseThreshold)
            dZ = 0;

        if(dX > dZ)
            command = "HORIZONTAL";
        else if(dZ > dX)
            command = "VERTICAL";
        else
            command = "IDLE";

    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }


    public class RunServiceBinder extends Binder {
        AccelerationService getService() {
            return AccelerationService.this;
        }
    }

    /**
     * Handler for sending commands to MediaPlayerService
     */
    class StateHandler extends Handler {
        private final static int UPDATE_RATE_MS = 500;
        private final WeakReference<AccelerationService> service;

        StateHandler(AccelerationService service) {
            this.service = new WeakReference<>(service);
        }

        @Override
        public void handleMessage(Message msg) {
            if(MSG_UPDATE == msg.what) {
                //Log.d(TAG, "sending command to MediaPlayerService");

                if(command.equals("VERTICAL") || command.equals("HORIZONTAL")) {
                    Intent i = new Intent();
                    i.setAction(command);
                    sendBroadcast(i);
                }

                sendEmptyMessageDelayed(MSG_UPDATE, UPDATE_RATE_MS);
            }
        }
    }
}
