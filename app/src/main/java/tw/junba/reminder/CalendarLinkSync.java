package tw.junba.reminder;

import android.content.*;
import android.os.*;
import org.json.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

/** Rolling read-only reconciliation into Junba-owned AlarmManager entries. */
final class CalendarLinkSync {
    private static final ExecutorService WORK=Executors.newSingleThreadExecutor();
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    static void request(Context context,Runnable done){
        Context c=context.getApplicationContext();
        WORK.execute(()->{try{run(c);}catch(Exception e){result(c,"刷新未完成，既有來源資料保留；請檢查日曆授權並重試。");}finally{if(done!=null)MAIN.post(done);}});
    }
    private static void result(Context c,String text){c.getSharedPreferences(LinkPolicy.PREF,0).edit().putString("sync_status",text).putLong("sync_time",System.currentTimeMillis()).apply();}
    static String status(Context c){return c.getSharedPreferences(LinkPolicy.PREF,0).getString("sync_status","尚未刷新連動");}
    static void run(Context c) throws Exception {
        // Re-submit future schedules after app restart; never rely only on a saved red-lamp flag.
        AlarmStore.restore(c,false);syncNotes(c);
        if(!CalendarNotificationListener.enabled(c)||!LinkPolicy.prelink(c)){
            cancelManaged(c);result(c,"日曆預先連動已關閉；Google 原活動與通知保留。");return;
        }
        if(!CalendarRepository.granted(c)){cancelManaged(c);result(c,"請允許讀取日曆後刷新；Google 原資料不變。");return;}
        LocalDate today=LocalDate.now();long now=System.currentTimeMillis(),until=today.plusDays(91).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        Set<Long> selected=new HashSet<>();for(CalendarModel.Source source:CalendarRepository.sources(c))if(CalendarRepository.selected(c,source))selected.add(source.id);
        CalendarRepository.Snapshot snapshot=CalendarRepository.load(c,today.minusDays(1),today.plusDays(91),selected);
        if(snapshot.truncated||!snapshot.remindersComplete){result(c,"來源資料不完整，未以部分清單覆寫連動；請減少日曆或重試。");return;}
        List<LinkModel.Request> requests=LinkModel.plan(snapshot.events,now,until);Set<String> desired=new HashSet<>();for(LinkModel.Request r:requests)desired.add(r.id);
        Set<String> liveEvents=new HashSet<>();for(CalendarModel.Event event:snapshot.events)liveEvents.add(event.key());
        Set<String> disabled=new HashSet<>();for(LinkModel.Request r:requests)if(LinkPolicy.disabled(c,r.id)||LinkPolicy.eventDisabled(c,r.event.key()))disabled.add(r.id);
        Set<String> allowed=LinkModel.schedulable(requests,disabled,250);
        JSONArray old=AlarmStore.all(c);
        for(int i=0;i<old.length();i++){
            JSONObject record=old.optJSONObject(i);if(record==null||!LinkModel.managed(record))continue;
            AlarmStore.retireIfStale(c,record.optString("key"),selected,desired,liveEvents,now,until,snapshot.alertsComplete);
        }
        int accepted=0,off=0,failed=0;
        for(LinkModel.Request request:requests){
            if(!disabled.contains(request.id)&&!allowed.contains(request.id))continue;
            if(!CalendarNotificationListener.enabled(c)||!LinkPolicy.prelink(c))break;
            try{
                JSONObject record=AlarmStore.upsertLink(c,request.id,request.event.title+"\n"+request.event.calendar.label(),"calendar_link",request.when,request.metadata(),false);
                if(LinkPolicy.disabled(c,request.id)||LinkPolicy.eventDisabled(c,request.event.key()))off++;
                else if(record!=null&&LinkModel.linked(record,System.currentTimeMillis(),AlarmStore.notificationsVisible(c)&&ReminderStore.canExact(c)))accepted++;
                else failed++;
            }catch(Exception e){failed++;}
        }
        result(c,"已刷新未來 90 天：連動 "+accepted+" 筆、本人移除 "+off+" 筆、未排成功 "+failed+" 筆"+(requests.size()>250?"；本次上限 250 筆，其餘未亮燈":"")+"。Google 原資料不變。");
    }
    private static void cancelManaged(Context c){
        JSONArray records=AlarmStore.all(c);for(int i=0;i<records.length();i++){JSONObject r=records.optJSONObject(i);
            if(r!=null&&LinkModel.managed(r)&&(r.optBoolean("linkEnabled")||AlarmRecord.pending(r)))try{AlarmStore.cancelManaged(c,r.optString("key"));}catch(Exception ignored){}
        }
    }
    static void syncNotes(Context c){
        JSONArray notes=ReminderStore.all(c);for(int i=0;i<notes.length();i++){JSONObject note=notes.optJSONObject(i);if(note==null)continue;
            int id=note.optInt("id");if(!LinkPolicy.noteEnabled(c,id,note.optBoolean("remind",true)))continue;
            try{linkNote(c,note,false);}catch(Exception ignored){}
        }
    }
    static JSONObject linkNote(Context c,JSONObject note,boolean force) throws Exception {
        int id=note.optInt("id");long when=ReminderStore.nextTrigger(note,System.currentTimeMillis());
        if(when<=System.currentTimeMillis())throw new IllegalStateException("此記事時間已過，請編輯時間後連動。");
        if(force||note.optBoolean("remind",true))LinkPolicy.note(c,id,true);
        String linkId="note:"+id+":"+when;
        JSONObject metadata=new JSONObject().put("noteId",id).put("linkId",linkId);
        JSONObject record=AlarmStore.upsertLink(c,linkId,note.optString("title","無訊息")+"\n"+note.optString("content","無訊息"),"note_link",when,metadata,force);
        if(record!=null&&record.optBoolean("systemScheduled"))ReminderStore.retireLegacy(c,id);
        return record;
    }
}
