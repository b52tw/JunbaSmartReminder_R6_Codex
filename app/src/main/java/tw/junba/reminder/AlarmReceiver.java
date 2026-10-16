package tw.junba.reminder;

import android.app.*;
import android.content.*;
import org.json.*;

/** Delivers Junba alarms. Never edits the vendor Clock. */
public class AlarmReceiver extends BroadcastReceiver {
    static final String CHANNEL="junba_alarm_r6";
    static void ensureChannel(Context c){
        NotificationManager manager=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel channel=new NotificationChannel(CHANNEL,"Junba 鬧鐘響鈴",NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("Junba 的停止與延後控制；音效由響鈴服務播放，非原廠時鐘項目");
        channel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);channel.enableVibration(false);channel.setSound(null,null);
        manager.createNotificationChannel(channel);
    }
    @Override public void onReceive(Context c,Intent event){
        if(event==null||"tw.junba.reminder.CALENDAR_REMINDER".equals(event.getAction()))return;
        JSONObject record=null;
        try{
            boolean relay=CalendarNotificationListener.ACTION.equals(event.getAction());
            if(relay&&!CalendarNotificationListener.enabled(c))return;
            if(AlarmStore.FIRE.equals(event.getAction())){
                record=AlarmStore.find(c,event.getStringExtra("alarm_key"));if(record==null)return;
                if("calendar".equals(record.optString("source"))&&!CalendarNotificationListener.enabled(c)){AlarmStore.cancel(c,record.optString("key"));return;}
                if(!AlarmRecord.pending(record)||record.optInt("generation")!=event.getIntExtra("generation",-1))return;
            }else{
                if(event.getAction()==null){
                    boolean active=false;JSONArray notes=ReminderStore.all(c);
                    for(int n=0;n<notes.length();n++){JSONObject note=notes.optJSONObject(n);
                        if(note!=null&&note.optInt("id")==event.getIntExtra("id",7000)&&note.optBoolean("remind",true)){active=true;break;}}
                    if(!active)return;
                }
                record=AlarmStore.create(c,event.getStringExtra("title"),relay?"calendar":"note","",System.currentTimeMillis());
            }
            ensureChannel(c);
            if(!AlarmStore.notificationsVisible(c)){
                AlarmStore.status(c,record.optString("key"),"blocked","Junba 通知已關閉，未響鈴；請開啟通知以取得停止與延後按鈕。");return;
            }
            Intent audio=new Intent(c,AlarmAudioService.class).setAction(AlarmAudioService.PLAY)
                .putExtra("alarm_key",record.optString("key")).putExtra("generation",record.optInt("generation"));
            c.startForegroundService(audio);
        }catch(Exception ex){
            if(record!=null)try{AlarmStore.status(c,record.optString("key"),"failed","響鈴服務遭系統限制，請檢查背景權限。");}catch(Exception ignored){}
        }finally{
            if(event.getAction()==null){
                JSONArray notes=ReminderStore.all(c);
                for(int n=0;n<notes.length();n++){JSONObject note=notes.optJSONObject(n);
                    if(note!=null&&note.optInt("id")==event.getIntExtra("id",7000)){ReminderStore.schedule(c,note);break;}}
            }
        }
    }
}
