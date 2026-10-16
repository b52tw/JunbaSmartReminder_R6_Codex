package tw.junba.reminder;

import android.app.*;
import android.content.*;
import android.net.Uri;
import org.json.*;
import java.util.UUID;

/** Local user-visible delivery history, independent of notes and the vendor Clock. */
final class AlarmStore {
    static final String PREF="junba_alarm_controls",FIRE="tw.junba.reminder.DELIVER";
    static boolean notificationsVisible(Context c){
        NotificationManager manager=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel channel=manager.getNotificationChannel(AlarmReceiver.CHANNEL);
        return manager.areNotificationsEnabled()&&(channel==null||channel.getImportance()!=NotificationManager.IMPORTANCE_NONE);
    }
    static synchronized JSONArray all(Context c){
        try{return read(c);}catch(Exception e){return new JSONArray();}
    }
    private static JSONArray read(Context c) throws JSONException {return new JSONArray(c.getSharedPreferences(PREF,0).getString("records","[]"));}
    private static void write(Context c,JSONArray records) {
        if(!c.getSharedPreferences(PREF,0).edit().putString("records",records.toString()).commit())throw new IllegalStateException("提醒紀錄尚未寫入。");
    }
    private static int index(JSONArray records,String key){
        for(int i=0;i<records.length();i++){JSONObject r=records.optJSONObject(i);if(r!=null&&r.optString("key").equals(key))return i;}return -1;
    }
    static synchronized JSONObject find(Context c,String key){JSONArray a=all(c);int i=index(a,key);return i<0?null:a.optJSONObject(i);}
    static synchronized JSONObject current(Context c){
        JSONArray a=all(c);for(int i=a.length()-1;i>=0;i--){JSONObject r=a.optJSONObject(i);
            if(r!=null&&"ringing".equals(r.optString("status"))&&r.optLong("activeUntil")>System.currentTimeMillis())return r;}
        return null;
    }
    static synchronized JSONObject create(Context c,String title,String source,String sourceKey,long when) throws JSONException {
        JSONArray a=read(c);long now=System.currentTimeMillis();
        // Keep all pending/current reminders. Only old finished history may be trimmed.
        JSONArray trimmed=new JSONArray();int discard=Math.max(0,a.length()-99);
        for(int i=0;i<a.length();i++){
            JSONObject r=a.optJSONObject(i);
            if(discard>0&&r!=null&&!AlarmRecord.pending(r)&&!"ringing".equals(r.optString("status"))){discard--;continue;}
            trimmed.put(a.get(i));
        }
        JSONObject record=AlarmRecord.create(UUID.randomUUID().toString(),title,source,sourceKey,now,when);
        trimmed.put(record);write(c,trimmed);return record;
    }
    private static PendingIntent pending(Context c,JSONObject r,int flags){
        Intent intent=new Intent(c,AlarmReceiver.class).setAction(FIRE).setData(Uri.parse("junba://alarm/"+r.optString("key")))
            .putExtra("alarm_key",r.optString("key")).putExtra("generation",r.optInt("generation"));
        return PendingIntent.getBroadcast(c,0,intent,flags|PendingIntent.FLAG_IMMUTABLE);
    }
    private static void schedule(Context c,JSONObject r){
        if(!notificationsVisible(c))throw new IllegalStateException("請先允許 Junba 通知，才有可操作的響鈴控制。");
        if(!ReminderStore.canExact(c))throw new IllegalStateException("未取得精確鬧鐘權限。");
        ((AlarmManager)c.getSystemService(Context.ALARM_SERVICE)).setAlarmClock(
            new AlarmManager.AlarmClockInfo(r.optLong("when"),AlarmAudioService.openPendingIntent(c)),pending(c,r,PendingIntent.FLAG_UPDATE_CURRENT));
    }
    static synchronized void scheduleNew(Context c,JSONObject r) throws JSONException {
        try{schedule(c,r);}catch(RuntimeException e){status(c,r.optString("key"),"failed","系統未接受排程，請檢查 Junba 通知及精確鬧鐘權限。");throw e;}
    }
    static synchronized JSONObject begin(Context c,String key,int generation,long duration) throws JSONException {
        JSONArray a=read(c);int i=index(a,key);if(i<0)return null;long now=System.currentTimeMillis();
        JSONObject started=AlarmRecord.ringing(a.getJSONObject(i),generation,now,duration);if(started==null)return null;
        for(int n=0;n<a.length();n++){
            JSONObject r=a.optJSONObject(n);if(n!=i&&r!=null&&"ringing".equals(r.optString("status")))a.put(n,AlarmRecord.finished(r,"superseded",now));
        }
        a.put(i,started);write(c,a);return started;
    }
    static synchronized void finish(Context c,String key,String state){
        try{JSONArray a=read(c);int i=index(a,key);if(i<0)return;JSONObject old=a.getJSONObject(i);
            if(!"ringing".equals(old.optString("status")))return;
            a.put(i,AlarmRecord.finished(old,state,System.currentTimeMillis()));write(c,a);
        }catch(Exception ignored){}
    }
    static synchronized void status(Context c,String key,String state,String error) throws JSONException {
        JSONArray a=read(c);int i=index(a,key);if(i<0)return;
        a.getJSONObject(i).put("status",state).put("error",error).put("changedAt",System.currentTimeMillis());write(c,a);
    }
    static void stopNow(Context c){
        // Always ask Android to stop the actual service, even if metadata cannot be read.
        c.stopService(new Intent(c,AlarmAudioService.class));
        JSONArray a=all(c);for(int n=0;n<a.length();n++){JSONObject r=a.optJSONObject(n);
            if(r!=null)finish(c,r.optString("key"),"stopped");}
    }
    static synchronized long snooze(Context c,String key,int minutes) throws JSONException {
        JSONArray a=read(c);int i=index(a,key);if(i<0)throw new IllegalStateException("找不到這筆提醒。");
        JSONObject old=a.getJSONObject(i),next=AlarmRecord.snoozed(old,minutes,System.currentTimeMillis());
        if("calendar".equals(old.optString("source"))&&!CalendarNotificationListener.enabled(c))throw new IllegalStateException("請先啟用日曆通知響鈴。");
        if(!notificationsVisible(c)||!ReminderStore.canExact(c))throw new IllegalStateException("請先允許 Junba 通知與精確鬧鐘。");
        a.put(i,next);write(c,a);
        try{schedule(c,next);}catch(RuntimeException error){
            a.put(i,old);old.put("error","延後排程未成功，請檢查通知及精確鬧鐘權限。");
            if(AlarmRecord.pending(old)){
                try{schedule(c,old);}catch(RuntimeException restoreError){old.put("status","failed");}
            }
            write(c,a);throw error;
        }
        if("ringing".equals(old.optString("status")))c.stopService(new Intent(c,AlarmAudioService.class));
        return next.optLong("when");
    }
    static synchronized void cancel(Context c,String key) throws JSONException {
        JSONArray a=read(c);int i=index(a,key);if(i<0)return;JSONObject old=a.getJSONObject(i);
        boolean playing="ringing".equals(old.optString("status"));
        old.put("status","cancelled").put("changedAt",System.currentTimeMillis());write(c,a);
        if(playing)c.stopService(new Intent(c,AlarmAudioService.class));
        PendingIntent p=pending(c,old,PendingIntent.FLAG_NO_CREATE);
        if(p!=null){((AlarmManager)c.getSystemService(Context.ALARM_SERVICE)).cancel(p);p.cancel();}
    }
    static synchronized void remove(Context c,String key) throws JSONException {
        cancel(c,key);JSONArray a=read(c),remaining=new JSONArray();
        for(int n=0;n<a.length();n++){
            JSONObject r=a.optJSONObject(n);if(r==null||!key.equals(r.optString("key")))remaining.put(a.get(n));
        }
        write(c,remaining);
    }
    static synchronized void sourceRemoved(Context c,String sourceKey){
        JSONArray a=all(c);for(int n=0;n<a.length();n++){JSONObject r=a.optJSONObject(n);
            if(r!=null&&sourceKey.equals(r.optString("sourceKey"))&&"scheduled".equals(r.optString("status"))&&r.optInt("snoozeCount")==0)
                try{cancel(c,r.optString("key"));}catch(Exception ignored){}
        }
    }
    static void disableCalendar(Context c){
        JSONArray a=all(c);for(int n=0;n<a.length();n++){JSONObject r=a.optJSONObject(n);
            if(r!=null&&"calendar".equals(r.optString("source"))&&(AlarmRecord.pending(r)||"ringing".equals(r.optString("status"))))
                try{cancel(c,r.optString("key"));}catch(Exception ignored){}
        }
    }
    static synchronized void requestedStock(Context c,String key,long when) throws JSONException {
        JSONArray a=read(c);int i=index(a,key);if(i<0)return;
        a.getJSONObject(i).put("stockWhen",when).put("stockRequestedAt",System.currentTimeMillis());write(c,a);
    }
    static synchronized void restore(Context c,boolean afterRestart){
        JSONArray a=all(c);long now=System.currentTimeMillis();
        for(int i=0;i<a.length();i++){
            JSONObject r=a.optJSONObject(i);if(r==null)continue;
            try{
                if(afterRestart&&"ringing".equals(r.optString("status")))finish(c,r.optString("key"),"interrupted");
                else if(AlarmRecord.pending(r)){
                    if("calendar".equals(r.optString("source"))&&!CalendarNotificationListener.enabled(c))cancel(c,r.optString("key"));
                    else if(r.optLong("when")<=now)status(c,r.optString("key"),"missed","重新開機後已過提醒時間，未突然補響。");
                    else scheduleNew(c,r);
                }
            }catch(Exception ignored){}
        }
    }
}
