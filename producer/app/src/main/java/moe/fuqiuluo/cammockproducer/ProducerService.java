package moe.fuqiuluo.cammockproducer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

/**
 * 让 ProducerActivity 的 MediaCodec 解码线程保持前台进程优先级。
 *
 * Activity 会 moveTaskToBack，系统会把它降成 cached（实测解码从 12.5fps
 * 掉到约 1.5fps）；Service.startForeground 只负责把进程拉到 foreground，
 * 实际解码仍在 ProducerActivity 的线程里。
 */
public class ProducerService extends Service {
    static final String CHANNEL = "cammock-producer";
    static final int ID = 0xCA11;

    @Override
    public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL, "cammock producer", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
        Notification.Builder b = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL)
                : new Notification.Builder(this);
        b.setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle("PortalEX cammock")
                .setContentText("视频帧生产者运行中")
                .setOngoing(true);
        startForeground(ID, b.build());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
