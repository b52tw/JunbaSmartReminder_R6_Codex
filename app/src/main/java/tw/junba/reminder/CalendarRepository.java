package tw.junba.reminder;

import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** Read-only system CalendarProvider. Never queries accounts or Google servers directly. */
final class CalendarRepository {
    private static final ExecutorService MATCHES=Executors.newSingleThreadExecutor();
    static boolean granted(Context c){return c.checkSelfPermission(Manifest.permission.READ_CALENDAR)==PackageManager.PERMISSION_GRANTED;}
    static final class Snapshot {
        final List<CalendarModel.Source> sources;final List<CalendarModel.Event> events;final boolean truncated;
        Snapshot(List<CalendarModel.Source> sources,List<CalendarModel.Event> events,boolean truncated){this.sources=sources;this.events=events;this.truncated=truncated;}
    }
    static List<CalendarModel.Source> sources(Context c){
        List<CalendarModel.Source> out=new ArrayList<>();
        String[] cols={"_id","account_name","calendar_displayName","account_type"};
        try(Cursor cursor=c.getContentResolver().query(CalendarContract.Calendars.CONTENT_URI,cols,null,null,"account_name ASC")){
            if(cursor==null)throw new IllegalStateException("手機未提供日曆清單。");
            while(cursor.moveToNext())out.add(new CalendarModel.Source(cursor.getLong(0),cursor.getString(1),cursor.getString(2),cursor.getString(3)));
        }
        return out;
    }
    static Snapshot load(Context c,LocalDate first,LocalDate end,Set<Long> selected){
        List<CalendarModel.Source> sources=sources(c);Map<Long,CalendarModel.Source> byId=new HashMap<>();
        for(CalendarModel.Source source:sources)byId.put(source.id,source);
        ZoneId zone=ZoneId.systemDefault();
        long start=Math.min(first.atStartOfDay(zone).toInstant().toEpochMilli(),first.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli());
        long finish=Math.max(end.atStartOfDay(zone).toInstant().toEpochMilli(),end.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli());
        Uri.Builder uri=CalendarContract.Instances.CONTENT_URI.buildUpon();ContentUris.appendId(uri,start);ContentUris.appendId(uri,finish);
        List<CalendarModel.Event> events=new ArrayList<>();boolean truncated=false;
        String[] cols={"event_id","begin","end","calendar_id","title","allDay","eventStatus"};
        try(Cursor cursor=c.getContentResolver().query(uri.build(),cols,null,null,"begin ASC")){
            if(cursor==null)throw new IllegalStateException("手機未提供活動清單。");
            while(cursor.moveToNext()){
                long calendarId=cursor.getLong(3);CalendarModel.Source source=byId.get(calendarId);
                if(source==null||(selected!=null&&!selected.contains(calendarId))||cursor.getInt(6)==CalendarContract.Events.STATUS_CANCELED)continue;
                if(events.size()>=5000){truncated=true;break;}
                events.add(new CalendarModel.Event(cursor.getLong(0),cursor.getLong(1),cursor.getLong(2),source,cursor.getString(4),cursor.getInt(5)!=0));
            }
        }
        Map<Long,List<CalendarModel.Event>> byEvent=new HashMap<>();
        for(CalendarModel.Event event:events)byEvent.computeIfAbsent(event.id,k->new ArrayList<>()).add(event);
        List<Long> ids=new ArrayList<>(byEvent.keySet());
        for(int startAt=0;startAt<ids.size();startAt+=400){
            List<Long> batch=ids.subList(startAt,Math.min(ids.size(),startAt+400));String[] args=new String[batch.size()];
            for(int i=0;i<batch.size();i++)args[i]=Long.toString(batch.get(i));
            String where="event_id IN ("+String.join(",",Collections.nCopies(batch.size(),"?"))+")";
            try(Cursor cursor=c.getContentResolver().query(CalendarContract.Reminders.CONTENT_URI,new String[]{"event_id","minutes","method"},where,args,null)){
                if(cursor==null)continue;
                while(cursor.moveToNext()){
                    int method=cursor.getInt(2);if(method!=0&&method!=1&&method!=4)continue;
                    for(CalendarModel.Event event:byEvent.get(cursor.getLong(0)))event.minutes.add(cursor.getInt(1));
                }
            }
        }
        return new Snapshot(sources,events,truncated);
    }
    static boolean selected(Context c,CalendarModel.Source source){
        SharedPreferences view=c.getSharedPreferences("junba_calendar_view",0),old=c.getSharedPreferences("MainActivity",0);
        String key="cal_"+source.id;
        return CalendarModel.selected(source,view.contains(key)?view.getBoolean(key,false):null,old.contains(key)?old.getBoolean(key,false):null);
    }
    static void matchLater(Context context,String recordKey,String title,long postTime){
        Context c=context.getApplicationContext();if(!granted(c))return;
        MATCHES.execute(()->{
            try{
                List<CalendarModel.Event> alerts=fromAlerts(c,title,postTime);
                CalendarModel.Event match=CalendarModel.match(alerts,Collections.emptyList(),title,postTime);String kind="provider_alert";
                if(!CalendarModel.hasTitle(alerts,title)){
                    LocalDate date=Instant.ofEpochMilli(postTime).atZone(ZoneId.systemDefault()).toLocalDate();
                    Snapshot snapshot=load(c,date.minusDays(1),date.plusDays(32),null);
                    if(!snapshot.truncated)match=CalendarModel.match(alerts,snapshot.events,title,postTime);
                    kind="title_reminder";
                }
                if(match!=null)AlarmStore.annotate(c,recordKey,match.metadata(kind));
            }catch(Exception ignored){/* Keep the real receipt unassigned; never invent a match. */}
        });
    }
    private static List<CalendarModel.Event> fromAlerts(Context c,String title,long postTime){
        List<CalendarModel.Source> sources=sources(c);Map<Long,CalendarModel.Source> byId=new HashMap<>();
        for(CalendarModel.Source source:sources)byId.put(source.id,source);
        List<CalendarModel.Event> candidates=new ArrayList<>();
        String[] cols={"event_id","begin","end","calendar_id","title","allDay","state","receivedTime","alarmTime"};
        try(Cursor cursor=c.getContentResolver().query(CalendarContract.CalendarAlerts.CONTENT_URI,cols,
            "(receivedTime BETWEEN ? AND ? OR alarmTime BETWEEN ? AND ?)",
            new String[]{""+(postTime-120000),""+(postTime+120000),""+(postTime-120000),""+(postTime+120000)},null)){
            if(cursor==null)return candidates;
            while(cursor.moveToNext()){
                if(cursor.getInt(6)==CalendarContract.CalendarAlerts.STATE_SCHEDULED)continue;
                CalendarModel.Source source=byId.get(cursor.getLong(3));if(source==null)continue;
                candidates.add(new CalendarModel.Event(cursor.getLong(0),cursor.getLong(1),cursor.getLong(2),source,cursor.getString(4),cursor.getInt(5)!=0));
            }
        }catch(RuntimeException ignored){return new ArrayList<>();}
        return candidates;
    }
}
