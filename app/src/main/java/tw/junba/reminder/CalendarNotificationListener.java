package tw.junba.reminder;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.Bundle;
import android.service.notification.*;
import org.json.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Opt-in relay. Other packages are discarded before reading their notification text. */
public final class CalendarNotificationListener extends NotificationListenerService {
    static final String ACTION="tw.junba.reminder.CALENDAR_NOTIFICATION";
    static final String PREF="junba_notification_relay";
    static boolean enabled(Context c){return c.getSharedPreferences(PREF,0).getBoolean("enabled",false);}
    static void disable(Context c){
        android.content.SharedPreferences prefs=c.getSharedPreferences(PREF,0);
        prefs.edit().putBoolean("enabled",false).apply();
        AlarmStore.disableCalendar(c);
        try{
            JSONObject history=new JSONObject(prefs.getString("history","{}"));
            Iterator<String> keys=history.keys();
            while(keys.hasNext()){
                PendingIntent p=pending(c,keys.next(),"",PendingIntent.FLAG_NO_CREATE);
                if(p!=null){((AlarmManager)c.getSystemService(ALARM_SERVICE)).cancel(p);p.cancel();}
            }
        }catch(Exception ignored){/* Receiver also checks enabled before any sound. */}
    }
    static boolean granted(Context c){
        ComponentName component=new ComponentName(c,CalendarNotificationListener.class);
        if(android.os.Build.VERSION.SDK_INT>=27)
            return ((NotificationManager)c.getSystemService(NOTIFICATION_SERVICE)).isNotificationListenerAccessGranted(component);
        String value=android.provider.Settings.Secure.getString(c.getContentResolver(),"enabled_notification_listeners");
        if(value!=null)for(String name:value.split(":"))if(component.equals(ComponentName.unflattenFromString(name)))return true;
        return false;
    }
    private static String key(String raw){
        try{byte[] digest=MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder out=new StringBuilder();for(byte b:digest)out.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return out.toString();}
        catch(Exception e){throw new IllegalStateException(e);}
    }
    private static PendingIntent pending(Context c,String key,String title,int flags){
        Intent intent=new Intent(c,AlarmReceiver.class).setAction(ACTION).setData(Uri.parse("junba://calendar-notification/"+key));
        intent.putExtra("id",1000000+(key.hashCode()&0x3fffffff));intent.putExtra("title",title);
        return PendingIntent.getBroadcast(c,0,intent,flags|PendingIntent.FLAG_IMMUTABLE);
    }
    private void result(String status){getSharedPreferences(PREF,0).edit().putString("status",status).putLong("last_seen",System.currentTimeMillis()).apply();}
    @Override public void onListenerConnected(){
        getSharedPreferences(PREF,0).edit().putBoolean("connected",true).apply();
        // Do not replay existing notifications when access is granted or after a reboot.
    }
    @Override public void onListenerDisconnected(){
        getSharedPreferences(PREF,0).edit().putBoolean("connected",false).apply();
        if(enabled(this))requestRebind(new ComponentName(this,CalendarNotificationListener.class));
    }
    @Override public void onNotificationPosted(StatusBarNotification event){
        if(event==null||!enabled(this)||!CalendarNotificationRule.GOOGLE_CALENDAR.equals(event.getPackageName()))return;
        Notification note=event.getNotification();if(note==null)return;
        long now=System.currentTimeMillis();
        if(!CalendarNotificationRule.accepts(event.getPackageName(),note.category,
            (note.flags&Notification.FLAG_GROUP_SUMMARY)!=0,(note.flags&Notification.FLAG_ONGOING_EVENT)!=0,event.getPostTime(),now))return;
        String key=key(event.getKey());
        android.content.SharedPreferences prefs=getSharedPreferences(PREF,0);
        try{
            JSONObject history=new JSONObject(prefs.getString("history","{}"));JSONObject prior=history.optJSONObject(key);
            Bundle extras=note.extras;CharSequence title=extras==null?null:extras.getCharSequence(Notification.EXTRA_TITLE);
            CharSequence body=extras==null?null:extras.getCharSequence(Notification.EXTRA_TEXT);
            String text="Google 日曆提醒"+(title==null?"":"："+title)+(body==null?"":"\n"+body);
            String contentHash=key(text);
            if(prior!=null&&(CalendarNotificationRule.sameContent(contentHash,prior.optString("contentHash"))||
                CalendarNotificationRule.duplicate((note.flags&Notification.FLAG_ONLY_ALERT_ONCE)!=0,
                    event.getPostTime(),now,prior.optLong("post"),prior.optLong("delivered"))))return;
            JSONObject metadata=new JSONObject().put("notificationTitle",title==null?"":title.toString())
                .put("notificationText",body==null?"":body.toString()).put("notificationPostTime",event.getPostTime())
                .put("notificationHash",contentHash).put("linkId","notification:"+key+":"+contentHash);
            JSONObject record=AlarmStore.create(this,text,"calendar",key,now+1000L,metadata);
            // Matching is optional and asynchronous; it never delays or gates the proven alarm path.
            CalendarRepository.matchLater(this,record.optString("key"),title==null?"":title.toString(),event.getPostTime());
            int route=AlarmStore.notificationRoute(this,title==null?"":title.toString(),event.getPostTime());
            if(route==2||LinkPolicy.disabled(this,record.optString("linkId")))AlarmStore.status(this,record.optString("key"),"unlinked","已移除此連動；Google 原通知保留。");
            else if(route==1)AlarmStore.status(this,record.optString("key"),"received","通知已收到，已有預排鬧鈴，不重複響鈴。");
            else AlarmStore.scheduleNew(this,record);
            JSONObject trimmed=new JSONObject();Iterator<String> names=history.keys();int count=0;
            while(names.hasNext()&&count<63){String old=names.next();JSONObject item=history.optJSONObject(old);
                if(item!=null&&now-item.optLong("delivered")<7L*86400000L){trimmed.put(old,item);count++;}}
            trimmed.put(key,new JSONObject().put("post",event.getPostTime()).put("delivered",now).put("contentHash",contentHash));
            prefs.edit().putString("history",trimmed.toString()).apply();
            result("收到日曆通知，紀錄已保留；可在 App 停止或延後");
        }catch(Exception error){result("收到日曆通知，但系統未接受響鈴排程；請檢查權限");}
    }
    @Override public void onNotificationRemoved(StatusBarNotification event){
        if(event==null||!CalendarNotificationRule.GOOGLE_CALENDAR.equals(event.getPackageName()))return;
        String key=key(event.getKey());
        AlarmStore.sourceRemoved(this,key);
        try{
            JSONObject history=new JSONObject(getSharedPreferences(PREF,0).getString("history","{}"));history.remove(key);
            getSharedPreferences(PREF,0).edit().putString("history",history.toString()).apply();
            PendingIntent alarm=pending(this,key,"",PendingIntent.FLAG_NO_CREATE);
            if(alarm!=null){((AlarmManager)getSystemService(ALARM_SERVICE)).cancel(alarm);alarm.cancel();}
        }catch(Exception ignored){}
    }
}
