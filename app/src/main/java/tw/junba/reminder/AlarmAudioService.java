package tw.junba.reminder;

import android.app.*;
import android.content.*;
import android.media.AudioAttributes;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.*;

/** Foreground ringing service using the Android default ALARM ringtone.
 * Not a request to vivo Clock, and not a guarantee of bypassing Do Not Disturb. */
public final class AlarmAudioService extends Service {
    static final String PLAY="tw.junba.reminder.RING";
    static final String STOP="tw.junba.reminder.STOP";
    static final String SNOOZE="tw.junba.reminder.SNOOZE";
    private Ringtone ringtone;
    private int notificationId=800000;
    private String currentTitle="Junba 提醒";
    private boolean calendarRelay;
    private final Handler timeout=new Handler(Looper.getMainLooper());
    private final Runnable stopLater=this::stopSelf;
    static PendingIntent openPendingIntent(Context c){
        Intent i=new Intent(c,MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(c,40001,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    private PendingIntent action(Context c,String command,int request){
        Intent i=new Intent(c,AlarmAudioService.class).setAction(command);
        return PendingIntent.getService(c,request,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    @Override public int onStartCommand(Intent i,int flags,int startId){
        String action=i==null?PLAY:i.getAction();
        if(STOP.equals(action)){stopSelf();return START_NOT_STICKY;}
        if(SNOOZE.equals(action)){
            stopRing();
            try{
                AlarmManager a=(AlarmManager)getSystemService(ALARM_SERVICE);
                Intent alarm=new Intent(this,AlarmReceiver.class).setAction(calendarRelay?CalendarNotificationListener.ACTION:"tw.junba.reminder.SNOOZED");
                alarm.putExtra("id",notificationId);alarm.putExtra("title",currentTitle);
                PendingIntent p=PendingIntent.getBroadcast(this,notificationId,alarm,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
                long t=System.currentTimeMillis()+5*60000L;
                a.setAlarmClock(new AlarmManager.AlarmClockInfo(t,openPendingIntent(this)),p);
            }catch(Exception ignored){}
            stopSelf();return START_NOT_STICKY;
        }
        if(i!=null){notificationId=i.getIntExtra("id",800000);currentTitle=i.getStringExtra("title");calendarRelay=i.getBooleanExtra("calendar_relay",false);}
        if(currentTitle==null||currentTitle.isEmpty())currentTitle="Junba 提醒";
        AlarmReceiver.ensureChannel(this);
        Notification note=new Notification.Builder(this,AlarmReceiver.CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Junba 鬧鐘正在響鈴")
            .setContentText(currentTitle)
            .setContentIntent(openPendingIntent(this))
            .setOngoing(true).setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel,"停止響鈴",action(this,STOP,800101))
            .addAction(android.R.drawable.ic_lock_idle_alarm,"5 分鐘後提醒",action(this,SNOOZE,800102))
            .build();
        startForeground(800000,note);
        stopRing();
        try{
            Uri uri=RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if(uri==null)uri=RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            ringtone=RingtoneManager.getRingtone(this,uri);
            if(ringtone!=null){
                ringtone.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
                if(Build.VERSION.SDK_INT>=28)ringtone.setLooping(true);
                ringtone.play();
            }
        }catch(Exception ignored){}
        timeout.removeCallbacks(stopLater);timeout.postDelayed(stopLater,i==null?120000:Math.max(1000,Math.min(120000,i.getLongExtra("duration_ms",120000))));
        return START_NOT_STICKY;
    }
    private void stopRing(){
        if(ringtone!=null){try{if(ringtone.isPlaying())ringtone.stop();}catch(Exception ignored){}ringtone=null;}
    }
    @Override public void onDestroy(){timeout.removeCallbacks(stopLater);stopRing();super.onDestroy();}
    @Override public IBinder onBind(Intent i){return null;}
}
