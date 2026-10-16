package tw.junba.reminder;

import android.app.*;
import android.content.*;
import android.os.Build;

/** A system alarm-clock schedule triggers a Junba-owned audible alarm.
 *  It does not edit or control the vendor vivo Clock database. */
public class AlarmReceiver extends BroadcastReceiver {
    static final String CHANNEL="junba_alarm_r6";
    static void ensureChannel(Context c){
        NotificationManager n=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
        if(Build.VERSION.SDK_INT>=26){
            NotificationChannel ch=new NotificationChannel(CHANNEL,"Junba 鬧鐘響鈴",NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("由 Junba 排程、播放系統預設鬧鐘鈴聲，可停止或稍後提醒；非 vivo 原廠時鐘項目");
            ch.enableVibration(true);
            ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            // The foreground audio service plays the alarm, avoiding double playback from channel sound.
            ch.setSound(null,null);
            n.createNotificationChannel(ch);
        }
    }
    @Override public void onReceive(Context c,Intent event){
        if(event==null)return;
        if("tw.junba.reminder.CALENDAR_REMINDER".equals(event.getAction()))return; // Retired mirror.
        boolean relay=CalendarNotificationListener.ACTION.equals(event.getAction());
        if(relay&&!CalendarNotificationListener.enabled(c))return;
        if(event.getAction()==null){
            boolean active=false;
            org.json.JSONArray notes=ReminderStore.all(c);
            for(int n=0;n<notes.length();n++){
                org.json.JSONObject note=notes.optJSONObject(n);
                if(note!=null&&note.optInt("id")==event.getIntExtra("id",7000)&&note.optBoolean("remind",true)){active=true;break;}
            }
            if(!active)return;
        }
        ensureChannel(c);
        String title=event.getStringExtra("title");
        if(title==null||title.trim().isEmpty())title="Junba 提醒";
        int id=event.getIntExtra("id",7000);
        Intent audio=new Intent(c,AlarmAudioService.class).setAction(AlarmAudioService.PLAY);
        audio.putExtra("title",title);audio.putExtra("id",id);audio.putExtra("calendar_relay",relay);
        try {
            if(Build.VERSION.SDK_INT>=26)c.startForegroundService(audio);
            else c.startService(audio);
        }catch(RuntimeException ex){
            // Do not claim that sound played when the operating system declined to start the service.
            Intent open=new Intent(c,MainActivity.class);
            PendingIntent pi=PendingIntent.getActivity(c,id,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            Notification notification=new Notification.Builder(c,CHANNEL)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("提醒時間到了（響鈴服務遭系統限制）").setContentText(title)
                .setContentIntent(pi).setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC).setPriority(Notification.PRIORITY_HIGH).build();
            try{((NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE)).notify(id,notification);}
            catch(SecurityException ignored){}
        }
        if(event.getAction()!=null)return;
        org.json.JSONArray notes=ReminderStore.all(c);
        for(int j=0;j<notes.length();j++){
            org.json.JSONObject o=notes.optJSONObject(j);
            if(o!=null&&o.optInt("id")==id){
                String r=o.optString("repeat");
                if("每日".equals(r)||"每週".equals(r)||"每月".equals(r))ReminderStore.schedule(c,o);
                break;
            }
        }
    }
}
