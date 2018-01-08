package com.simmasoftware.vna_bt;


import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import java.util.concurrent.TimeUnit;

public class checkService extends Service {
    String TAG = "djd";
    postlog postlog;

    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "VNA-BT checkService started");
        postlog=new postlog(this);

        //NotificationManager nm= (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        Notification notif = new Notification(R.drawable.ico, "VNA-BT Crash Helper", System.currentTimeMillis());
        Intent intentMain = new Intent(this, MainActivity.class);
        intentMain.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        intentMain.putExtra("fromCheckService",true);
        PendingIntent pIntent = PendingIntent.getActivity(this, 0, intentMain, 0);
        //notif.setLatestEventInfo(this, "Passio Tracking Service", "Click to launch Passio Transit", pIntent);
        startForeground(1, notif);

    }

    public IBinder onBind(Intent intent) {
        return null;
    }

    public int onStartCommand(Intent intent, int flags, int startId) {
        String s=intent.getStringExtra("postlog");
        Log.d(TAG, "VNA-BT checkService onStartCommand: s="+s);
        if (s!=null && !s.equalsIgnoreCase("")){
            postlog.post(s);
            new Thread(new Runnable() {
                public void run() {
                    try {
                        TimeUnit.SECONDS.sleep(1);
                    } catch (InterruptedException e) {
                        Log.d(TAG, "InterruptedException TimeUnit");
                        e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                    }

                    //postlog.post("watchdog");
                    Intent i = new Intent();
                    i.setAction(Intent.ACTION_MAIN);
                    i.addCategory(Intent.CATEGORY_LAUNCHER);
                    i.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY);
                    i.setComponent(new ComponentName(getApplicationContext().getPackageName(), MainActivity.class.getName()));
                    i.putExtra("fromCheckService",true);//doesn't work

                    //if (!BuildConfig.DEBUG)
                    //    startActivity(i);

                    //postlog.sendlogs(); //will send in main
                    try {
                        TimeUnit.SECONDS.sleep(3);
                    } catch (InterruptedException e) {
                        Log.d(TAG, "InterruptedException TimeUnit");
                        e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                    }
                    postlog.sendlogs();
                    stopSelf();
                }
            }).start();
        }
        return START_STICKY;
    }
}
