package tw.junba.reminder;

import android.app.*;
import android.content.*;
import org.json.*;

/** Stop retired local calendar mirrors without reading or editing any calendar. */
final class LegacyCalendarCleanup {
    static void stop(Context c){
        android.content.SharedPreferences prefs=c.getSharedPreferences("MainActivity",0);
        if(prefs.getBoolean("simple_calendar_stopped",false)&&!prefs.getBoolean("calendar_mirror_enabled",false))return;
        try{
            JSONArray old=new JSONArray(c.getSharedPreferences("junba_calendar_mirror",0).getString("entries","[]"));
            for(int n=0;n<old.length();n++){
                JSONObject item=old.optJSONObject(n);if(item==null)continue;
                Intent intent=new Intent(c,AlarmReceiver.class).setAction("tw.junba.reminder.CALENDAR_REMINDER");
                PendingIntent pending=PendingIntent.getBroadcast(c,item.optInt("id",1),intent,PendingIntent.FLAG_NO_CREATE|PendingIntent.FLAG_IMMUTABLE);
                if(pending!=null){((AlarmManager)c.getSystemService(Context.ALARM_SERVICE)).cancel(pending);pending.cancel();}
            }
            prefs.edit().putBoolean("calendar_mirror_enabled",false).putBoolean("simple_calendar_stopped",true).apply();
        }catch(Exception ignored){/* Preserve the cache and retry instead of declaring migration complete. */}
    }
}
