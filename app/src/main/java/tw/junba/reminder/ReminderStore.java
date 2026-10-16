package tw.junba.reminder;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.Calendar;

/** App-owned reminders. Never assumes they were created in vivo Clock. */
public final class ReminderStore {
    private ReminderStore() {}
    static JSONArray all(Context c) {
        try { return new JSONArray(c.getSharedPreferences("junba",0).getString("notes","[]")); }
        catch(Exception ex) { return new JSONArray(); }
    }
    static void save(Context c, JSONArray notes) {
        c.getSharedPreferences("junba",0).edit().putString("notes",notes.toString()).apply();
    }
    static int put(Context c, String title, long timestamp, int advanceMin, String repeat) throws JSONException {
        int id = c.getSharedPreferences("junba",0).getInt("next_note_id",10000);
        c.getSharedPreferences("junba",0).edit().putInt("next_note_id",id+1).apply();
        return putWithContentAndReminder(c,title,"無訊息",timestamp,advanceMin,repeat,true,id);
    }
    static int putWithContentAndReminder(Context c,String title,String body,long timestamp,int advanceMin,String repeat,boolean remind) throws JSONException {
        int id = c.getSharedPreferences("junba",0).getInt("next_note_id",10000);
        c.getSharedPreferences("junba",0).edit().putInt("next_note_id",id+1).apply();
        return putWithContentAndReminder(c,title,body,timestamp,advanceMin,repeat,remind,id);
    }
    private static int putWithContentAndReminder(Context c,String title,String body,long timestamp,int advanceMin,String repeat,boolean remind,int id) throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id",id);o.put("title",safe(title));o.put("content",safe(body));o.put("time",timestamp);
        o.put("ahead",advanceMin);o.put("repeat",repeat);o.put("remind",remind);
        JSONArray a=all(c);a.put(o);save(c,a);
        if(remind)schedule(c,o);return id;
    }
    static SimpleNote.Change saveForStock(Context c,Integer editingId,String token,SimpleNote draft) throws JSONException {
        android.content.SharedPreferences prefs=c.getSharedPreferences("junba",0);
        JSONArray existing=new JSONArray(prefs.getString("notes","[]"));
        SimpleNote.Change result=SimpleNote.prepare(existing,prefs.getInt("next_note_id",10000),editingId,token,draft);
        if(result.created){
            if(!prefs.edit().putString("notes",result.notes.toString()).putInt("next_note_id",result.nextId).commit())
                throw new IllegalStateException("記事尚未寫入，草稿已保留。");
            // Stored remind=false is also checked by the receiver if cancellation is refused.
            try{cancel(c,result.id);}catch(RuntimeException ignored){}
        }
        return result;
    }
    static void retireLegacy(Context c,int id) throws JSONException {
        android.content.SharedPreferences prefs=c.getSharedPreferences("junba",0);JSONArray notes=new JSONArray(prefs.getString("notes","[]"));boolean changed=false;
        for(int i=0;i<notes.length();i++){JSONObject note=notes.optJSONObject(i);if(note!=null&&note.optInt("id")==id&&note.optBoolean("remind",true)){note.put("remind",false);changed=true;}}
        if(changed&&!prefs.edit().putString("notes",notes.toString()).commit())throw new IllegalStateException("記事排程設定未寫入。");
        cancel(c,id);
    }
    private static String safe(String s){return s==null||s.trim().isEmpty()?"無訊息":s.trim();}
    static void updateFull(Context c,int id,String title,String body,long timestamp,int advanceMin,String repeat,boolean remind) throws JSONException {
        JSONArray a=all(c);
        for(int i=0;i<a.length();i++){
            JSONObject o=a.optJSONObject(i);
            if(o!=null&&o.optInt("id")==id){
                cancel(c,id);o.put("title",safe(title));o.put("content",safe(body));
                o.put("time",timestamp);o.put("ahead",advanceMin);o.put("repeat",repeat);o.put("remind",remind);
                save(c,a);if(remind)schedule(c,o);return;
            }
        }
        throw new JSONException("找不到記事");
    }
    static void update(Context c, int id, String title, long timestamp, int advanceMin, String repeat) throws JSONException {
        JSONArray a=all(c);
        for(int i=0;i<a.length();i++){
            JSONObject o=a.optJSONObject(i);
            if(o!=null&&o.optInt("id")==id){cancel(c,id);o.put("title",title);o.put("time",timestamp);o.put("ahead",advanceMin);o.put("repeat",repeat);save(c,a);schedule(c,o);return;}
        }
        throw new JSONException("找不到記事");
    }
    static void remove(Context c,int id) {
        try{
            android.content.SharedPreferences prefs=c.getSharedPreferences("junba",0);
            JSONArray a=new JSONArray(prefs.getString("notes","[]")),b=new JSONArray();
            for(int i=0;i<a.length();i++){
                JSONObject o=a.optJSONObject(i);
                if(o==null||o.optInt("id")!=id)b.put(a.get(i));
            }
            if(!prefs.edit().putString("notes",b.toString()).commit())throw new IllegalStateException();
            try{cancel(c,id);}catch(RuntimeException ignored){}
            AlarmStore.cancelNote(c,id,true);
        }catch(JSONException e){throw new IllegalStateException("記事資料無法讀取，未刪除。",e);}
    }
    static PendingIntent intent(Context c,JSONObject o) {
        int id=o.optInt("id");Intent i=new Intent(c,AlarmReceiver.class);
        i.putExtra("id",id);i.putExtra("title",o.optString("title"));
        return PendingIntent.getBroadcast(c,id,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    static void cancel(Context c,int id){
        Intent i=new Intent(c,AlarmReceiver.class);
        PendingIntent p=PendingIntent.getBroadcast(c,id,i,PendingIntent.FLAG_NO_CREATE|PendingIntent.FLAG_IMMUTABLE);
        if(p!=null){((AlarmManager)c.getSystemService(Context.ALARM_SERVICE)).cancel(p);p.cancel();}
    }
    static boolean canExact(Context c){
        AlarmManager a=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
        return Build.VERSION.SDK_INT<31 || a.canScheduleExactAlarms();
    }
    static long nextTrigger(JSONObject o,long now){
        long timestamp=o.optLong("time")-o.optInt("ahead")*60000L;
        String repeat=o.optString("repeat","不重複");
        if(timestamp>now)return timestamp;
        if("不重複".equals(repeat))return -1L;
        Calendar cal=Calendar.getInstance();cal.setTimeInMillis(timestamp);
        int field="每日".equals(repeat)?Calendar.DAY_OF_MONTH:
            "每週".equals(repeat)?Calendar.WEEK_OF_YEAR:
            "每月".equals(repeat)?Calendar.MONTH:0;
        if(field==0)return -1L;
        // Calendar.add respects local DST. Prevent runaway loops on corrupt data.
        for(int n=0;n<4000 && cal.getTimeInMillis()<=now;n++)cal.add(field,1);
        return cal.getTimeInMillis()>now?cal.getTimeInMillis():-1L;
    }
    static boolean schedule(Context c,JSONObject o){
        long when=nextTrigger(o,System.currentTimeMillis());
        if(!o.optBoolean("remind",true)||when<=0||!canExact(c))return false;
        try {
            ((AlarmManager)c.getSystemService(Context.ALARM_SERVICE)).setAlarmClock(
                new AlarmManager.AlarmClockInfo(when,AlarmAudioService.openPendingIntent(c)),intent(c,o));
            return true;
        }catch(SecurityException e){return false;}
    }
    static int reschedule(Context c){
        JSONArray a=all(c);int count=0;
        for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o!=null&&schedule(c,o))count++;}
        return count;
    }
}
