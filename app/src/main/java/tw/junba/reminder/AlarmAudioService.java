package tw.junba.reminder;

import android.app.*;
import android.content.*;
import android.media.*;
import android.net.Uri;
import android.os.*;
import org.json.JSONObject;

/** A single Junba sound player. Its controls also live in MainActivity. */
public final class AlarmAudioService extends Service {
    static final String PLAY="tw.junba.reminder.RING",STOP="tw.junba.reminder.STOP",SNOOZE="tw.junba.reminder.SNOOZE";
    private Ringtone ringtone;
    private String activeKey="";
    private final Handler timeout=new Handler(Looper.getMainLooper());
    private final Runnable stopLater=()->{AlarmStore.finish(this,activeKey,"timed_out");stopSelf();};
    static PendingIntent openPendingIntent(Context c){
        Intent i=new Intent(c,MainActivity.class).putExtra("show_alarms",true);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(c,40001,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    private PendingIntent action(String command,int minutes){
        Intent i=new Intent(this,AlarmActionReceiver.class).setAction(command).setData(Uri.parse("junba://control/"+activeKey+"/"+minutes))
            .putExtra("alarm_key",activeKey).putExtra("minutes",minutes);
        return PendingIntent.getBroadcast(this,0,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent==null){stopSelf();return START_NOT_STICKY;}
        if(STOP.equals(intent.getAction())){AlarmStore.finish(this,activeKey,"stopped");stopRing();stopSelf();return START_NOT_STICKY;}
        if(SNOOZE.equals(intent.getAction())){
            try{AlarmStore.snooze(this,activeKey,10);}catch(Exception e){android.widget.Toast.makeText(this,"延後未成功，請在 App 檢查權限",android.widget.Toast.LENGTH_LONG).show();}
            return START_NOT_STICKY;
        }
        long duration=Math.max(1000,Math.min(120000,intent.getLongExtra("duration_ms",120000)));
        String key=intent.getStringExtra("alarm_key");int generation=intent.getIntExtra("generation",0);
        try{
            if(key==null){JSONObject test=AlarmStore.create(this,intent.getStringExtra("title"),"test","",System.currentTimeMillis());key=test.getString("key");}
            JSONObject record=AlarmStore.begin(this,key,generation,duration);
            if(record==null){if(activeKey.isEmpty())stopSelf();return START_NOT_STICKY;}
            stopRing();activeKey=key;AlarmReceiver.ensureChannel(this);
            Notification note=new Notification.Builder(this,AlarmReceiver.CHANNEL)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentTitle("Junba 鬧鐘正在響鈴")
                .setContentText(record.optString("title")).setStyle(new Notification.BigTextStyle().bigText(record.optString("title")+"\n若未顯示按鈕，點此開啟 App 停止或延後。"))
                .setContentIntent(openPendingIntent(this)).setOngoing(true).setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PRIVATE).setOnlyAlertOnce(true)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel,"停止響鈴",action(STOP,0))
                .addAction(android.R.drawable.ic_lock_idle_alarm,"延後 10 分鐘",action(SNOOZE,10))
                .addAction(android.R.drawable.ic_menu_more,"30 分鐘／1 小時",openPendingIntent(this)).build();
            startForeground(800000,note);
            if(!AlarmStore.notificationsVisible(this)){
                AlarmStore.status(this,key,"blocked","Junba 通知未允許，已停止響鈴；請在 App 開啟通知。");stopSelf();return START_NOT_STICKY;
            }
            Uri uri=RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if(uri==null)uri=RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            ringtone=RingtoneManager.getRingtone(this,uri);
            if(ringtone==null)throw new IllegalStateException("未設定系統鈴聲");
            ringtone.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
            if(Build.VERSION.SDK_INT>=28)ringtone.setLooping(true);
            ringtone.play();
            timeout.removeCallbacks(stopLater);timeout.postDelayed(stopLater,duration);
        }catch(Exception error){
            if(key!=null)try{AlarmStore.status(this,key,"failed","系統未允許播放；請檢查通知、鈴聲與背景設定。");}catch(Exception ignored){}
            stopRing();stopSelf();
        }
        return START_NOT_STICKY;
    }
    private void stopRing(){if(ringtone!=null){try{ringtone.stop();}catch(Exception ignored){}ringtone=null;}}
    @Override public void onDestroy(){
        timeout.removeCallbacks(stopLater);stopRing();AlarmStore.finish(this,activeKey,"stopped");
        stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy();
    }
    @Override public IBinder onBind(Intent i){return null;}
}
