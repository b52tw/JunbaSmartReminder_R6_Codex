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
        return create(c,title,source,sourceKey,when,new JSONObject());
    }
    static synchronized JSONObject create(Context c,String title,String source,String sourceKey,long when,JSONObject metadata) throws JSONException {
        JSONArray a=read(c);long now=System.currentTimeMillis();
        // Keep all pending/current reminders. Only old finished history may be trimmed.
        JSONArray trimmed=new JSONArray();int discard=Math.max(0,a.length()-99);
        for(int i=0;i<a.length();i++){
            JSONObject r=a.optJSONObject(i);
            if(discard>0&&r!=null&&!AlarmRecord.pending(r)&&!"ringing".equals(r.optString("status"))&&!r.has("linkId")){discard--;continue;}
            trimmed.put(a.get(i));
        }
        JSONObject record=AlarmRecord.create(UUID.randomUUID().toString(),title,source,sourceKey,now,when);
        applyMetadata(record,metadata);trimmed.put(record);write(c,trimmed);return record;
    }
    private static void applyMetadata(JSONObject record,JSONObject metadata) throws JSONException {
        for(String key:new String[]{"eventId","eventBegin","eventEnd","calendarId","accountName","accountType","calendarName","eventTitle","allDay","matchKind","notificationTitle","notificationText","notificationPostTime","testDurationMs","testConfirmedAt","suggestedStockTime","linkId","managed","noteId","originalWhen","notificationHash","coveredBy","providerTime"})
            if(metadata.has(key))record.put(key,metadata.get(key));
    }
    static synchronized void annotate(Context c,String key,JSONObject metadata) throws JSONException {
        JSONArray records=read(c);int i=index(records,key);if(i<0)return;
        JSONObject record=records.getJSONObject(i);
        if(record.has("eventId")&&"manual".equals(record.optString("matchKind"))&&!"manual".equals(metadata.optString("matchKind")))return;
        applyMetadata(record,metadata);write(c,records);
        if("calendar".equals(record.optString("source"))&&LinkPolicy.recordDisabled(c,record))cancel(c,key,false);
    }
    private static PendingIntent pending(Context c,JSONObject r,int flags){
        Intent intent=new Intent(c,AlarmReceiver.class).setAction(FIRE).setData(Uri.parse("junba://alarm/"+r.optString("key")))
            .putExtra("alarm_key",r.optString("key")).putExtra("generation",r.optInt("generation"));
        return PendingIntent.getBroadcast(c,0,intent,flags|PendingIntent.FLAG_IMMUTABLE);
    }
    private static void schedule(Context c,JSONObject r){
        if(LinkModel.calendar(r)&&!CalendarNotificationListener.enabled(c))throw new IllegalStateException("日曆連動已關閉。");
        if(LinkModel.managed(r)&&!LinkPolicy.prelink(c))throw new IllegalStateException("預先連動已關閉。");
        if("note_link".equals(r.optString("source"))&&!LinkPolicy.noteEnabled(c,r.optInt("noteId"),false))throw new IllegalStateException("記事連動已關閉。");
        if(!notificationsVisible(c))throw new IllegalStateException("請先允許 Junba 通知，才有可操作的響鈴控制。");
        if(!ReminderStore.canExact(c))throw new IllegalStateException("未取得精確鬧鐘權限。");
        ((AlarmManager)c.getSystemService(Context.ALARM_SERVICE)).setAlarmClock(
            new AlarmManager.AlarmClockInfo(r.optLong("when"),AlarmAudioService.openPendingIntent(c)),pending(c,r,PendingIntent.FLAG_UPDATE_CURRENT));
    }
    static synchronized void scheduleNew(Context c,JSONObject r) throws JSONException {
        try{
            if(LinkPolicy.recordDisabled(c,r))throw new IllegalStateException("此連動已移除。");
            schedule(c,r);
            JSONArray records=read(c);int at=index(records,r.optString("key"));if(at<0)throw new IllegalStateException("連動紀錄已移除。");
            records.getJSONObject(at).put("systemScheduled",true).put("scheduleAccepted",true).put("linkEnabled",true);write(c,records);
        }catch(Exception error){
            try{PendingIntent pi=pending(c,r,PendingIntent.FLAG_NO_CREATE);if(pi!=null){((AlarmManager)c.getSystemService(Context.ALARM_SERVICE)).cancel(pi);pi.cancel();}}catch(Exception ignored){}
            status(c,r.optString("key"),"failed","未完成系統排程，請檢查通知與精確鬧鐘權限。");
            if(error instanceof JSONException)throw (JSONException)error;throw new IllegalStateException(error);
        }
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
        JSONObject record=a.getJSONObject(i);record.put("status",state).put("error",error).put("changedAt",System.currentTimeMillis());
        if(!"scheduled".equals(state)&&!"snoozed".equals(state))record.put("systemScheduled",false);
        if("failed".equals(state)||"blocked".equals(state)||"cancelled".equals(state))record.put("linkEnabled",false);
        write(c,a);
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
        if(LinkModel.calendar(old)&&!CalendarNotificationListener.enabled(c))throw new IllegalStateException("請先啟用日曆通知響鈴。");
        if(!notificationsVisible(c)||!ReminderStore.canExact(c))throw new IllegalStateException("請先允許 Junba 通知與精確鬧鐘。");
        if(old.has("linkId"))LinkPolicy.set(c,old.optString("linkId"),false);
        if("note_link".equals(old.optString("source")))LinkPolicy.note(c,old.optInt("noteId"),true);
        if(LinkModel.calendar(old))LinkPolicy.event(c,CalendarModel.recordEventKey(old),false);
        if("calendar_link".equals(old.optString("source")))next.put("managed",false);
        next.put("linkEnabled",true).put("systemScheduled",true).put("scheduleAccepted",true);
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
    static synchronized void cancel(Context c,String key) throws JSONException {cancel(c,key,true);}
    static synchronized void cancel(Context c,String key,boolean remember) throws JSONException {
        JSONArray a=read(c);int i=index(a,key);if(i<0)return;JSONObject old=a.getJSONObject(i);
        boolean playing="ringing".equals(old.optString("status"));
        if(playing)c.stopService(new Intent(c,AlarmAudioService.class));
        if(remember){LinkPolicy.set(c,old.optString("linkId"),true);if(old.has("noteId")&&!LinkModel.test(old))LinkPolicy.note(c,old.optInt("noteId"),false);}
        old.put("status","cancelled").put("linkEnabled",false).put("systemScheduled",false).put("changedAt",System.currentTimeMillis());write(c,a);
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
                try{cancel(c,r.optString("key"),false);}catch(Exception ignored){}
        }
    }
    static void disableCalendar(Context c){
        JSONArray a=all(c);for(int n=0;n<a.length();n++){JSONObject r=a.optJSONObject(n);
            if(r!=null&&LinkModel.calendar(r)&&(r.optBoolean("linkEnabled")||AlarmRecord.pending(r)||"ringing".equals(r.optString("status"))))
                try{cancel(c,r.optString("key"),false);}catch(Exception ignored){}
        }
    }
    static synchronized JSONObject findLink(Context c,String id){
        JSONArray records=all(c);for(int i=records.length()-1;i>=0;i--){JSONObject record=records.optJSONObject(i);if(record!=null&&id.equals(record.optString("linkId")))return record;}return null;
    }
    static synchronized JSONObject upsertLink(Context c,String id,String title,String source,long when,JSONObject metadata,boolean force) throws JSONException {
        JSONObject record=findLink(c,id);
        if(force)LinkPolicy.set(c,id,false);
        if(record==null){metadata=new JSONObject(metadata.toString()).put("linkId",id).put("originalWhen",when);record=create(c,title,source,id,when,metadata);}
        String key=record.optString("key");
        if(force)LinkPolicy.event(c,CalendarModel.recordEventKey(record),false);
        if(LinkPolicy.recordDisabled(c,record)){if(AlarmRecord.pending(record)||record.optBoolean("linkEnabled"))cancel(c,key,false);return find(c,key);}
        JSONArray records=read(c);int i=index(records,key);JSONObject current=records.getJSONObject(i);
        String before=current.toString();applyMetadata(current,metadata);current.put("title",title);
        boolean alreadyPending=AlarmRecord.pending(current)&&current.optBoolean("systemScheduled");
        if(!force&&(alreadyPending||LinkModel.ringing(current,System.currentTimeMillis()))){
            if(!before.equals(current.toString()))write(c,records);
            if(!notificationsVisible(c)||!ReminderStore.canExact(c))cancel(c,key,false);
            return find(c,key);
        }
        int generation=current.optInt("generation");if(generation==Integer.MAX_VALUE)throw new IllegalStateException("排程編號已達上限。");
        current.put("when",when).put("status","scheduled").put("generation",generation+1).put("snoozeCount",0)
            .put("linkEnabled",true).put("systemScheduled",false).put("error","");write(c,records);
        scheduleNew(c,current);return find(c,key);
    }
    static synchronized void cancelNote(Context c,int id,boolean disable){
        if(disable)LinkPolicy.note(c,id,false);
        JSONArray records=all(c);for(int i=0;i<records.length();i++){JSONObject record=records.optJSONObject(i);
            if(record!=null&&!LinkModel.test(record)&&record.optInt("noteId",-1)==id)try{cancel(c,record.optString("key"),false);}catch(Exception ignored){}
        }
    }
    static int notificationRoute(Context c,String title,long postTime){
        JSONArray records=all(c);java.util.Set<String> disabled=new java.util.HashSet<>();
        for(int i=0;i<records.length();i++){JSONObject r=records.optJSONObject(i);if(r!=null&&LinkPolicy.recordDisabled(c,r))disabled.add(r.optString("linkId"));}
        return LinkModel.notificationRoute(records,title,postTime,disabled);
    }
    static synchronized void requestedStock(Context c,String key,long when) throws JSONException {
        JSONArray a=read(c);int i=index(a,key);if(i<0)return;
        a.getJSONObject(i).put("stockWhen",when).put("stockRequestedAt",System.currentTimeMillis());write(c,a);
    }
    static void restore(Context c,boolean afterRestart){
        JSONArray records=all(c);
        for(int i=0;i<records.length();i++){JSONObject record=records.optJSONObject(i);if(record!=null)restoreOne(c,record.optString("key"),afterRestart);}
    }
    private static synchronized void restoreOne(Context c,String key,boolean afterRestart){
        JSONObject r=find(c,key);if(r==null)return;long now=System.currentTimeMillis();
        try{
            if((afterRestart||!AlarmAudioService.alive())&&"ringing".equals(r.optString("status")))finish(c,key,"interrupted");
            else if(AlarmRecord.pending(r)){
                if((LinkModel.calendar(r)&&!CalendarNotificationListener.enabled(c))||LinkPolicy.recordDisabled(c,r))cancel(c,key,false);
                else if(LinkModel.missed(r,now))status(c,key,"missed","已過提醒時間，不突然補響。");
                else if(r.optLong("when")>now)scheduleNew(c,r);
            }
        }catch(Exception ignored){}
    }
    static synchronized void retireIfStale(Context c,String key,java.util.Set<Long> selected,java.util.Set<String> desired,java.util.Set<String> liveEvents,long now,long until,boolean alertsComplete) throws JSONException {
        JSONObject current=find(c,key);if(current==null||!LinkModel.managed(current))return;
        if(LinkModel.missed(current,now)){status(c,key,"missed","已過提醒時間，不突然補響。");return;}
        if(LinkModel.retire(current,selected,desired,liveEvents,now,until,alertsComplete))cancel(c,key,false);
    }
    static synchronized void cancelManaged(Context c,String key) throws JSONException {
        if(CalendarNotificationListener.enabled(c)&&LinkPolicy.prelink(c)&&CalendarRepository.granted(c))return;
        JSONObject current=find(c,key);if(current!=null&&LinkModel.managed(current))cancel(c,key,false);
    }
}
