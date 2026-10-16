package tw.junba.reminder;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.provider.AlarmClock;
import android.provider.Settings;
import android.text.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.time.ZoneId;
import java.util.*;

/** Simple notes plus visible controls and local notification-delivery history. */
public class MainActivity extends Activity {
    private LinearLayout content;
    private String page="notes";
    private CalendarBrowser calendarBrowser;
    private ScrollView displayedScroll;
    private String renderedPage="";
    private int alarmPage;
    private boolean destroyed;
    private static final int EXPORT=401;
    private final Handler updates=new Handler(Looper.getMainLooper());
    private final Runnable redraw=()->{if(content!=null)render();};
    private final SharedPreferences.OnSharedPreferenceChangeListener alarmChanged=(prefs,key)->{
        updates.removeCallbacks(redraw);updates.postDelayed(redraw,100);
    };
    private final SimpleDateFormat dateFormat=new SimpleDateFormat("yyyy/MM/dd HH:mm",Locale.TAIWAN);
    private int dp(int value){return (int)(value*getResources().getDisplayMetrics().density+0.5f);}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);AlarmReceiver.ensureChannel(this);LegacyCalendarCleanup.stop(this);
        if(state!=null)page=state.getString("page","notes");
        if(getIntent().getBooleanExtra("show_alarms",false))page="alarms";render();
    }
    @Override protected void onStart(){super.onStart();getSharedPreferences(AlarmStore.PREF,0).registerOnSharedPreferenceChangeListener(alarmChanged);getSharedPreferences(LinkPolicy.PREF,0).registerOnSharedPreferenceChangeListener(alarmChanged);}
    @Override protected void onStop(){getSharedPreferences(AlarmStore.PREF,0).unregisterOnSharedPreferenceChangeListener(alarmChanged);getSharedPreferences(LinkPolicy.PREF,0).unregisterOnSharedPreferenceChangeListener(alarmChanged);updates.removeCallbacks(redraw);super.onStop();}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);if(intent.getBooleanExtra("show_alarms",false)){page="alarms";renderedPage="";}render();}
    @Override public void onResume(){super.onResume();if(permissionsReady())getPreferences(0).edit().putBoolean("fold_auth",false).apply();ReminderStore.reschedule(this);CalendarRefreshJob.register(this);refreshLinks(false);if(calendarBrowser!=null&&"calendar".equals(page))calendarBrowser.invalidate();if(content!=null)render();}
    @Override protected void onDestroy(){destroyed=true;if(calendarBrowser!=null)calendarBrowser.close();updates.removeCallbacksAndMessages(null);super.onDestroy();}
    @Override protected void onSaveInstanceState(Bundle state){state.putString("page",page);super.onSaveInstanceState(state);}
    private TextView text(LinearLayout parent,String value,int size){
        TextView view=new TextView(this);view.setText(value);view.setTextSize(size);view.setTextColor(0xffeeeeef);
        view.setPadding(0,dp(7),0,dp(7));parent.addView(view);return view;
    }
    private void heading(LinearLayout parent,String value){text(parent,value,21).setTypeface(null,Typeface.BOLD);}
    private Button button(LinearLayout parent,String label,Runnable action){
        Button button=new Button(this);button.setText(label);button.setTextSize(16);button.setAllCaps(false);
        parent.addView(button);button.setOnClickListener(v->action.run());return button;
    }
    private LinearLayout card(){
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(16),dp(10),dp(16),dp(14));
        GradientDrawable background=new GradientDrawable();background.setColor(0xff242326);background.setCornerRadius(dp(16));box.setBackground(background);
        LinearLayout.LayoutParams layout=new LinearLayout.LayoutParams(-1,-2);layout.setMargins(0,dp(12),0,dp(4));content.addView(box,layout);return box;
    }
    private void toast(String value){Toast.makeText(this,value,Toast.LENGTH_LONG).show();}
    private void render(){
        int keepY=displayedScroll!=null&&page.equals(renderedPage)?displayedScroll.getScrollY():0;renderedPage=page;
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(0xff101012);
        LinearLayout frame=new LinearLayout(this);frame.setOrientation(LinearLayout.VERTICAL);frame.setBackgroundColor(0xff101012);
        Button stop=button(frame,"■ 停止目前 Junba 響鈴",()->{AlarmStore.stopNow(this);toast("已要求停止 Junba 響鈴；Google 原通知及原廠鬧鐘需各自處理");render();});
        stop.setTextColor(Color.WHITE);stop.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff9a3034));
        content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(20),dp(12),dp(20),dp(24));scroll.addView(content);
        frame.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        frame.setOnApplyWindowInsetsListener((view,insets)->{
            if(Build.VERSION.SDK_INT>=30){android.graphics.Insets bars=insets.getInsets(android.view.WindowInsets.Type.systemBars()|android.view.WindowInsets.Type.displayCutout());view.setPadding(bars.left,bars.top,bars.right,bars.bottom);}
            else view.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());
            return insets;
        });
        setContentView(frame);displayedScroll=scroll;scroll.post(()->scroll.scrollTo(0,keepY));frame.requestApplyInsets();dateFormat.setTimeZone(TimeZone.getDefault());
        heading(content,"Junba 簡易鬧鐘 R10");text(content,"作者：峻爸",14);
        JSONObject current=AlarmStore.current(this);
        if(current!=null){LinearLayout box=card();box.setBackgroundColor(0xff623b10);heading(box,"🟠 目前正在響鈴｜"+(LinkModel.test(current)?"測試":LinkModel.calendar(current)?"Google 日曆":"記事"));text(box,current.optString("title"),18);snoozeButtons(box,current);}
        if(!permissionsReady()&&!"settings".equals(page)){
            button(content,"▶ 授權設定（尚未完成，可展開）",()->{page="settings";getPreferences(0).edit().putBoolean("fold_auth",true).apply();render();});
        }
        if("calendar".equals(page)){
            button(content,"‹ 返回記事",()->{page="notes";render();});
            if(calendarBrowser==null)calendarBrowser=new CalendarBrowser(this,this::render,()->{page="alarms";render();},()->{page="settings";render();},this::refreshCalendar,this::testCalendarEvent,this::stockCalendarEvent,this::linkCalendarEvent,this::unlinkCalendarEvent);
            calendarBrowser.render(content);return;
        }
        if("alarms".equals(page)){button(content,"‹ 返回記事",()->{page="notes";render();});alarmHistory();return;}
        if("settings".equals(page)){button(content,"‹ 返回記事",()->{page="notes";render();});settings();return;}
        text(content,"記事可連動 Junba 系統鬧鈴，或另開原廠核對。移除連動會保留 Google 原活動與通知。",16);
        button(content,"＋ 新增記事鬧鐘",()->editor(null));
        if(!getPreferences(0).getString("simple_draft_token","").isEmpty())button(content,"繼續未完成記事",this::resumeDraft);
        button(content,"Google 日曆月份／帳號",()->{page="calendar";render();});
        button(content,"開啟原廠時鐘",this::openClock);
        button(content,"所有系統鬧鈴連動／測試",()->{page="alarms";render();});
        button(content,"日曆通知響鈴／設定",()->{page="settings";render();});
        JSONArray notes=ReminderStore.all(this);if(notes.length()==0)text(content,"尚無記事。",16);
        for(int i=notes.length()-1;i>=0;i--){
            JSONObject note=notes.optJSONObject(i);if(note==null)continue;
            int id=note.optInt("id");LinearLayout box=card();
            int lamp=LinkModel.noteLamp(AlarmStore.all(this),id,System.currentTimeMillis(),AlarmStore.notificationsVisible(this)&&ReminderStore.canExact(this)&&LinkPolicy.noteEnabled(this,id,false));
            if(lamp==2)box.setBackgroundColor(0xff623b10);
            heading(box,(lamp==2?"🟠 正在響鈴｜":lamp==1?"🔴 已連動｜":"○ 未連動｜")+note.optString("title","無訊息"));
            text(box,dateFormat.format(new Date(note.optLong("time")-note.optInt("ahead")*60000L)),18);
            String body=note.optString("content","無訊息");if(!body.equals(note.optString("title")))text(box,body,16);
            if(lamp>0)text(box,"此筆已納入 Junba，可在連動清單測試與移除。",14);
            else if(note.optBoolean("remind",true))text(box,"舊版提醒設定保留，刷新時檢查未來排程。",14);
            else text(box,"記事保留，目前沒有已確認的 Junba 連動。",14);
            if(NativeLinkStore.has(this,"note:"+id))text(box,"另有原廠建立請求；原廠是否儲存仍須核對。",14);
            button(box,"連動 Junba 系統鬧鈴",()->{try{CalendarLinkSync.linkNote(this,note,true);toast("記事已連動；可在連動清單測試與移除");render();}catch(Exception e){toast("未完成連動，請檢查時間及授權；記事保留");}});
            button(box,"測試此記事響鈴",()->testNote(note));
            if(lamp>0)button(box,"移除記事的 Junba 連動",()->{AlarmStore.cancelNote(this,id,true);render();});
            button(box,"編輯記事",()->editor(note));
            button(box,"另開原廠設定（不取消 Junba）",()->handoff(note,true));
            button(box,"開原廠核對／刪除鬧鐘",this::openClock);
            button(box,"刪除 Junba 本機記事",()->new AlertDialog.Builder(this).setTitle("刪除 Junba 本機記事？")
                .setMessage("只刪除 Junba 記事與該筆舊版 Junba 排程。原廠時鐘的鬧鐘不會刪除，請另外在原廠處理。")
                .setPositiveButton("刪除 Junba 本機記事",(d,w)->{try{ReminderStore.remove(this,id);render();}catch(Exception e){toast("記事未刪除，請重試；原資料保留");}})
                .setNeutralButton("先開原廠",(d,w)->openClock()).setNegativeButton("保留",null).show());
        }
    }
    private void editor(JSONObject old){
        if(!getPreferences(0).getString("simple_draft_token","").isEmpty()){
            new AlertDialog.Builder(this).setTitle("還有未完成的草稿")
                .setMessage("可以繼續原草稿，或捨棄草稿後開啟這次選擇的記事。已儲存記事不受影響。")
                .setPositiveButton("繼續草稿",(d,w)->resumeDraft())
                .setNeutralButton("捨棄草稿並開啟",(d,w)->editor(old,false)).setNegativeButton("取消",null).show();
        }else editor(old,false);
    }
    private void resumeDraft(){
        int id=getPreferences(0).getInt("simple_draft_edit_id",-1);
        if(id<0){editor(null,true);return;}
        JSONArray notes=ReminderStore.all(this);
        for(int n=0;n<notes.length();n++){
            JSONObject old=notes.optJSONObject(n);
            if(old!=null&&old.optInt("id")==id){editor(old,true);return;}
        }
        new AlertDialog.Builder(this).setTitle("原記事已不存在")
            .setMessage("輸入的草稿仍保留。要以草稿另外新增記事嗎？")
            .setPositiveButton("改為新增記事",(d,w)->editor(null,true)).setNegativeButton("保留草稿",null).show();
    }
    private void editor(JSONObject old,boolean resume){
        android.content.SharedPreferences prefs=getPreferences(0);
        String token=resume?prefs.getString("simple_draft_token",""):UUID.randomUUID().toString();
        if(token.isEmpty())token=UUID.randomUUID().toString();final String draftToken=token;
        Calendar when=Calendar.getInstance();when.add(Calendar.MINUTE,5);when.set(Calendar.SECOND,0);when.set(Calendar.MILLISECOND,0);
        if(old!=null)when.setTimeInMillis(old.optLong("time")-old.optInt("ahead")*60000L);
        if(resume)when.setTimeInMillis(prefs.getLong("simple_draft_time",when.getTimeInMillis()));
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(18),0,dp(18),dp(8));
        text(box,"鬧鐘名稱",16);EditText name=new EditText(this);name.setSingleLine(true);name.setHint("例如：吃藥、開會");
        name.setText(resume?prefs.getString("simple_draft_name",""):old==null?"":old.optString("title",""));box.addView(name);
        text(box,"記事內容（選填）",16);EditText body=new EditText(this);body.setMinLines(2);body.setHint("要提醒自己的事情");
        body.setText(resume?prefs.getString("simple_draft_content",""):old==null?"":old.optString("content",""));box.addView(body);
        Button date=button(box,"",()->new DatePickerDialog(this,(v,y,m,d)->{when.set(y,m,d);persistDraft(old,draftToken,name,body,when);updatePickerLabels(box,when);},
            when.get(Calendar.YEAR),when.get(Calendar.MONTH),when.get(Calendar.DAY_OF_MONTH)).show());date.setTag("date");
        Button time=button(box,"",()->new TimePickerDialog(this,(v,h,m)->{when.set(Calendar.HOUR_OF_DAY,h);when.set(Calendar.MINUTE,m);when.set(Calendar.SECOND,0);when.set(Calendar.MILLISECOND,0);
            persistDraft(old,draftToken,name,body,when);updatePickerLabels(box,when);},when.get(Calendar.HOUR_OF_DAY),when.get(Calendar.MINUTE),true).show());time.setTag("time");
        updatePickerLabels(box,when);
        text(box,"儲存並連動 Junba 會依完整日期時間預排，可在 App 測試、取消。另選原廠時，完整日期仍由你核對；請擇一儲存方式；已建原廠鬧鐘需另外在原廠取消。",14);
        text(box,"修改或刪除記事不會修改／刪除原廠舊鬧鐘，請在原廠另外處理。",14);
        if(old!=null&&old.optBoolean("remind",true))text(box,"此筆舊排程會在儲存時替換，依你選的 Junba 或原廠方式處理。",14);
        TextWatcher watcher=new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){persistDraft(old,draftToken,name,body,when);}
            public void afterTextChanged(Editable e){}
        };name.addTextChangedListener(watcher);body.addTextChangedListener(watcher);persistDraft(old,draftToken,name,body,when);
        ScrollView scroll=new ScrollView(this);scroll.addView(box);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(old==null?"新增記事鬧鐘":"編輯記事鬧鐘").setView(scroll)
            .setPositiveButton("儲存並連動 Junba",null).setNeutralButton("儲存並開原廠",null).setNegativeButton("保留草稿",null).create();
        java.util.function.Consumer<Boolean> saveAction=stock->{
            final SimpleNote draft;
            try{draft=SimpleNote.create(name.getText().toString(),body.getText().toString(),when.get(Calendar.YEAR),when.get(Calendar.MONTH)+1,when.get(Calendar.DAY_OF_MONTH),
                when.get(Calendar.HOUR_OF_DAY),when.get(Calendar.MINUTE),System.currentTimeMillis(),ZoneId.systemDefault());}
            catch(Exception e){toast(e instanceof IllegalArgumentException?e.getMessage():"日期無效，請重新選擇");return;}
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            try{
                SimpleNote.Change saved=ReminderStore.saveForStock(this,old==null?null:old.optInt("id"),draftToken,draft);
                prefs.edit().remove("simple_draft_token").remove("simple_draft_name").remove("simple_draft_content").remove("simple_draft_time").remove("simple_draft_edit_id").apply();
                dialog.dismiss();render();
                if(saved.created){
                    try{AlarmStore.cancelNote(this,saved.id,true);
                        if(stock)handoff(saved.note,true);
                        else{CalendarLinkSync.linkNote(this,saved.note,true);toast("記事已保存並連動 Junba 系統鬧鈴");}
                    }catch(Exception error){toast("記事已保存，但連動變更未完成；請查授權與連動清單");}
                }else toast("記事已儲存，沒有重複新增；請檢查連動清單");
            }catch(Exception e){dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);toast("記事未完成儲存，草稿仍保留，請重試");}
        };
        dialog.setOnShowListener(d->{dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->saveAction.accept(false));dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->saveAction.accept(true));});
        dialog.setOnDismissListener(d->render());dialog.show();
    }
    private void updatePickerLabels(LinearLayout box,Calendar when){
        ((Button)box.findViewWithTag("date")).setText(String.format(Locale.TAIWAN,"日期　%04d／%02d／%02d　›",when.get(Calendar.YEAR),when.get(Calendar.MONTH)+1,when.get(Calendar.DAY_OF_MONTH)));
        ((Button)box.findViewWithTag("time")).setText(String.format(Locale.TAIWAN,"時間　%02d：%02d　›",when.get(Calendar.HOUR_OF_DAY),when.get(Calendar.MINUTE)));
    }
    private void persistDraft(JSONObject old,String token,EditText name,EditText body,Calendar when){
        getPreferences(0).edit().putString("simple_draft_token",token).putString("simple_draft_name",name.getText().toString())
            .putString("simple_draft_content",body.getText().toString()).putLong("simple_draft_time",when.getTimeInMillis())
            .putInt("simple_draft_edit_id",old==null?-1:old.optInt("id")).apply();
    }
    private void handoff(JSONObject note,boolean warnDuplicate){
        String key="note:"+note.optInt("id");
        if(warnDuplicate&&NativeLinkStore.has(this,key)){
            new AlertDialog.Builder(this).setTitle("已送過這筆原廠請求")
                .setMessage("再次送出可能多一個鬧鐘。你可以先到原廠核對，或確定要再建立。")
                .setPositiveButton("仍要送出",(d,w)->handoff(note,false)).setNeutralButton("先開原廠",(d,w)->openClock()).setNegativeButton("取消",null).show();return;
        }
        final SimpleNote request;
        try{request=SimpleNote.fromStored(note,System.currentTimeMillis(),ZoneId.systemDefault());}
        catch(Exception e){toast("記事已保留；此時間已過，請先編輯日期與時間");return;}
        Intent intent=new Intent(AlarmClock.ACTION_SET_ALARM).putExtra(AlarmClock.EXTRA_HOUR,request.hour)
            .putExtra(AlarmClock.EXTRA_MINUTES,request.minute).putExtra(AlarmClock.EXTRA_MESSAGE,request.label).putExtra(AlarmClock.EXTRA_SKIP_UI,false);
        try{startActivity(intent);}
        catch(Exception e){toast("記事已保存，但無法開啟原廠設定，請從原廠時鐘手動建立");return;}
        try{NativeLinkStore.requested(this,key,"R7 手動核對",request.title,request.time,"不重複");}
        catch(Exception ignored){toast("已送出原廠請求，連動紀錄未完成，請到原廠核對");return;}
        toast("記事已保存，請核對原廠日期與時間；尚未確認是否已儲存");
    }
    private void openClock(){
        try{startActivity(new Intent(AlarmClock.ACTION_SHOW_ALARMS));}
        catch(Exception e){toast("請從手機桌面開啟原廠時鐘");}
    }
    private void snoozeButtons(LinearLayout box,JSONObject record){
        button(box,"10 分鐘後提醒",()->snooze(record,10));
        button(box,"30 分鐘後提醒",()->snooze(record,30));
        button(box,"1 小時後提醒",()->snooze(record,60));
    }
    private void snooze(JSONObject record,int minutes){
        try{long when=AlarmStore.snooze(this,record.optString("key"),minutes);toast("已排定 "+dateFormat.format(new Date(when))+" 再提醒");render();}
        catch(Exception e){toast("延後未成功，請檢查 Junba 通知、精確鬧鐘及日曆連動開關；目前響鈴可按上方停止");}
    }
    private String alarmStatus(JSONObject r){
        switch(r.optString("status")){
            case "scheduled":return "已排定響鈴";case "snoozed":return "已延後至 "+dateFormat.format(new Date(r.optLong("when")));
            case "ringing":return r.optLong("activeUntil")>System.currentTimeMillis()?"正在響鈴":"響鈴已結束／中斷";
            case "received":return "已收到通知，沿用既有預排鬧鈴";case "unlinked":return "通知保留，連動已移除";
            case "reference":return "活動參考，未排 Junba 鬧鐘";
            case "stopped":return "已停止";case "timed_out":return "響鈴已自動結束";case "cancelled":return "已取消 Junba 提醒";
            case "superseded":return "另一筆提醒接續響鈴";case "blocked":return "通知未允許，未響鈴";
            case "missed":return "已過提醒時間";case "interrupted":return "響鈴因重新啟動中斷";default:return "未完成響鈴／排程";
        }
    }
    private void alarmHistory(){
        heading(content,"系統鬧鈴連動清單");
        text(content,"🔴 已連動　🟠 正在響鈴　○ 未連動／已移除　🔵 測試。連動及測試只控制 Junba 系統鬧鈴，不更改 Google 原活動與通知。",15);
        button(content,"刷新日曆與連動",this::refreshCalendar);
        boolean showAll=getPreferences(0).getBoolean("all_alarm_history",false);
        button(content,showAll?"只看正式連動":"展開測試與其他紀錄",()->{getPreferences(0).edit().putBoolean("all_alarm_history",!showAll).apply();alarmPage=0;render();});
        JSONArray all=AlarmStore.all(this);java.util.List<JSONObject> records=new ArrayList<>();
        for(int i=all.length()-1;i>=0;i--){JSONObject r=all.optJSONObject(i);if(r!=null&&(showAll||(r.has("linkId")&&!LinkModel.test(r)&&!"received".equals(r.optString("status")))))records.add(r);}
        if(records.isEmpty())text(content,"尚無連動。可從記事或月份活動加入；已過提醒不突然補響。",16);
        if(alarmPage*20>=records.size())alarmPage=0;int first=alarmPage*20,last=Math.min(records.size(),first+20);
        if(records.size()>20){text(content,"共 "+records.size()+" 筆，第 "+(alarmPage+1)+" 頁",15);if(alarmPage>0)button(content,"前 20 筆",()->{alarmPage--;render();});if(last<records.size())button(content,"後 20 筆",()->{alarmPage++;render();});}
        boolean capable=AlarmStore.notificationsVisible(this)&&ReminderStore.canExact(this);long now=System.currentTimeMillis();
        for(JSONObject r:records.subList(first,last)){
            String key=r.optString("key");boolean active=LinkModel.ringing(r,now),linked=LinkModel.linked(r,now,capable&&(!LinkModel.calendar(r)||CalendarNotificationListener.enabled(this))&&(!"note_link".equals(r.optString("source"))||LinkPolicy.noteEnabled(this,r.optInt("noteId"),false))),test=LinkModel.test(r);
            LinearLayout box=card();if(active)box.setBackgroundColor(0xff623b10);
            heading(box,(active?"🟠 正在響鈴｜":test?"🔵 測試｜":linked?"🔴 已連動｜":"○ 未連動｜")+r.optString("title","無訊息"));
            String source=r.optString("source");
            text(box,"calendar_link".equals(source)?"Google 日曆預先／手動連動":"note_link".equals(source)?"記事的 Junba 連動":"calendar".equals(source)?"Google 日曆實際通知":test?"Junba 排程／音效測試":"原廠參考或舊版提醒",14);
            if(r.has("accountName"))text(box,r.optString("accountName")+" · "+r.optString("calendarName"),14);
            text(box,"鬧鈴時間："+dateFormat.format(new Date(r.optLong("when"))),16);text(box,alarmStatus(r),16);
            if(!r.optString("error").isEmpty())text(box,r.optString("error"),14);
            if(!test)button(box,"測試此連動（5 秒）",()->testRecord(r));
            else if(r.has("firedAt")&&!r.has("testConfirmedAt"))button(box,"我已聽見此測試",()->{try{AlarmStore.annotate(this,key,new JSONObject().put("testConfirmedAt",System.currentTimeMillis()));render();}catch(Exception e){toast("確認未寫入，請重試");}});
            if(test&&r.has("testConfirmedAt"))text(box,"本人已確認聽見此測試",14);
            button(box,"延後／再次提醒…",()->new AlertDialog.Builder(this).setTitle("多久後提醒？").setItems(new String[]{"10 分鐘","30 分鐘","1 小時"},(d,w)->snooze(r,new int[]{10,30,60}[w])).setNegativeButton("取消",null).show());
            if(r.optBoolean("linkEnabled")||AlarmRecord.pending(r)||active)button(box,test?"取消此測試":"移除 Junba 連動（保留來源）",()->new AlertDialog.Builder(this)
                .setTitle("只移除這筆 Junba 連動？").setMessage("Google 原活動及通知不變。記事也保留；原廠已建鬧鐘須另外在原廠取消。")
                .setPositiveButton("移除連動",(d,w)->{try{if(r.has("noteId")&&!test)AlarmStore.cancelNote(this,r.optInt("noteId"),true);else AlarmStore.cancel(this,key);render();}catch(Exception e){toast("未完全移除，請重試；仍可按上方停止聲音");}}).setNegativeButton("保留",null).show());
            else if(r.has("linkId")&&r.optLong("when")>now)button(box,"重新連動這筆鬧鈴",()->{
                try{if(LinkModel.calendar(r)&&!CalendarNotificationListener.enabled(this)){toast("請先啟用日曆連動");return;}
                    if(r.has("noteId"))LinkPolicy.note(this,r.optInt("noteId"),true);
                    AlarmStore.upsertLink(this,r.optString("linkId"),r.optString("title"),source,r.optLong("when"),r,true);render();}
                catch(Exception e){toast("未排成功，請檢查時間及授權");}
            });
            if(r.has("stockRequestedAt")){text(box,"曾送出原廠請求，App 無法確認原廠是否已儲存。",14);button(box,"開原廠核對／取消鬧鐘",this::openClock);}
        }
    }
    private void stockCalendar(JSONObject record){
        if(record.has("stockRequestedAt")){
            new AlertDialog.Builder(this).setTitle("曾送過原廠建立請求")
                .setMessage("再次送出可能增加一個鬧鐘。可以先開原廠核對。")
                .setPositiveButton("仍要另建",(d,w)->stockCalendarEditor(record)).setNeutralButton("先開原廠",(d,w)->openClock()).setNegativeButton("取消",null).show();
        }else stockCalendarEditor(record);
    }
    private void stockCalendarEditor(JSONObject record){
        Calendar when=Calendar.getInstance();when.add(Calendar.MINUTE,10);when.set(Calendar.SECOND,0);when.set(Calendar.MILLISECOND,0);
        if("snoozed".equals(record.optString("status"))&&record.optLong("when")>System.currentTimeMillis()+60000)when.setTimeInMillis(record.optLong("when"));
        if(record.optLong("suggestedStockTime")>System.currentTimeMillis()+60000)when.setTimeInMillis(record.optLong("suggestedStockTime"));
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(18),0,dp(18),dp(8));
        text(box,"鬧鐘名稱",16);EditText name=new EditText(this);name.setSingleLine(true);name.setText(record.optString("eventTitle","Google 日曆提醒"));box.addView(name);
        text(box,record.optString("title"),16);
        Button date=button(box,"",()->new DatePickerDialog(this,(v,y,m,d)->{when.set(y,m,d);updatePickerLabels(box,when);},when.get(Calendar.YEAR),when.get(Calendar.MONTH),when.get(Calendar.DAY_OF_MONTH)).show());date.setTag("date");
        Button time=button(box,"",()->new TimePickerDialog(this,(v,h,m)->{when.set(Calendar.HOUR_OF_DAY,h);when.set(Calendar.MINUTE,m);updatePickerLabels(box,when);},when.get(Calendar.HOUR_OF_DAY),when.get(Calendar.MINUTE),true).show());time.setTag("time");updatePickerLabels(box,when);
        text(box,"請選擇要新增的鬧鐘日期時間；收到通知的時間不能當作活動開始時間。原廠只接收名稱與時分，完整日期仍由你核對。",14);
        text(box,"Junba 已排定的延後提醒仍保留；若只要原廠響鈴，請另外取消 Junba 提醒。",14);
        ScrollView scroll=new ScrollView(this);scroll.addView(box);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("建立原廠鬧鐘並核對").setView(scroll).setPositiveButton("送交原廠",null).setNegativeButton("取消",null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            try{
                SimpleNote request=SimpleNote.create(name.getText().toString(),record.optString("title"),when.get(Calendar.YEAR),when.get(Calendar.MONTH)+1,when.get(Calendar.DAY_OF_MONTH),when.get(Calendar.HOUR_OF_DAY),when.get(Calendar.MINUTE),System.currentTimeMillis(),ZoneId.systemDefault());
                startActivity(new Intent(AlarmClock.ACTION_SET_ALARM).putExtra(AlarmClock.EXTRA_HOUR,request.hour).putExtra(AlarmClock.EXTRA_MINUTES,request.minute).putExtra(AlarmClock.EXTRA_MESSAGE,request.label).putExtra(AlarmClock.EXTRA_SKIP_UI,false));
                dialog.dismiss();
                try{AlarmStore.requestedStock(this,record.optString("key"),request.time);toast("已送出請求，請在原廠核對日期時間並完成儲存");}
                catch(Exception error){toast("已送出原廠請求，但紀錄未寫入；請直接在原廠核對");}
            }catch(Exception error){toast("未送出原廠請求，請選未來日期時間或手動開啟時鐘");}
        }));dialog.show();
    }
    private void testCalendarEvent(CalendarModel.Event event){
        new AlertDialog.Builder(this).setTitle("測試這筆活動的 Junba 連動？")
            .setMessage(event.title+"\n"+event.calendar.label()+"\n3 秒後經同一系統排程響鈴 5 秒，可停止或延後。測試不建立正式連結、不改正式連動紅燈；仍須本人確認聽見。")
            .setPositiveButton("排定測試",(d,w)->{
                try{
                    JSONObject metadata=event.metadata("test").put("testDurationMs",5000);
                    JSONObject record=AlarmStore.create(this,"活動測試："+event.title,"calendar_test",event.key(),System.currentTimeMillis()+3000,metadata);
                    AlarmStore.scheduleNew(this,record);toast("測試已排定，3 秒後響鈴 5 秒；請本人確認聽見");
                }catch(Exception e){toast("測試未排成功，請確認 Junba 通知與精確鬧鐘權限");}
            }).setNegativeButton("取消",null).show();
    }
    private void stockCalendarEvent(CalendarModel.Event event){
        try{
            JSONArray records=AlarmStore.all(this);
            for(int n=records.length()-1;n>=0;n--){JSONObject r=records.optJSONObject(n);
                if(r!=null&&CalendarModel.belongs(r,event)&&r.has("stockRequestedAt")){stockCalendar(r);return;}}
            JSONObject metadata=event.metadata("reference").put("suggestedStockTime",CalendarModel.stockTime(event,System.currentTimeMillis(),ZoneId.systemDefault()));
            JSONObject record=AlarmStore.create(this,event.title+"\n"+event.calendar.label(),"calendar_setup",event.key(),System.currentTimeMillis(),metadata);
            stockCalendar(record);
        }catch(Exception e){toast("活動資料未保存，請重試；尚未送交原廠");}
    }
    private void linkCalendarEvent(CalendarModel.Event event){
        if(!CalendarNotificationListener.enabled(this)){toast("請先在設定啟用日曆連動");page="settings";render();return;}
        java.util.List<LinkModel.Request> options=LinkModel.plan(Collections.singletonList(event),System.currentTimeMillis(),Long.MAX_VALUE);
        String[] labels=new String[options.size()+2];labels[0]="連動全部可讀的未來提醒（"+options.size()+" 筆）";
        for(int i=0;i<options.size();i++)labels[i+1]=dateFormat.format(new Date(options.get(i).when));labels[labels.length-1]="自行指定 Junba 時間（不改 Google）";
        new AlertDialog.Builder(this).setTitle("連動此活動的系統鬧鈴").setItems(labels,(d,w)->{
            if(w==labels.length-1){manualCalendarTime(event);return;}
            int success=0;
            try{
                if(w==0){for(LinkModel.Request request:options){AlarmStore.upsertLink(this,request.id,event.title+"\n"+event.calendar.label(),"calendar_link",request.when,request.metadata().put("managed",false),true);success++;}}
                else{LinkModel.Request request=options.get(w-1);AlarmStore.upsertLink(this,request.id,event.title+"\n"+event.calendar.label(),"calendar_link",request.when,request.metadata().put("managed",false),true);success++;}
                toast(success>0?"已連動 "+success+" 筆；Google 原資料不變":"未提供未來提醒時間，請自行指定 Junba 時間");render();
            }catch(Exception e){toast("部分或全部未排成功，請查連動清單及授權；Google 原資料不變");render();}
        }).setNegativeButton("取消",null).show();
    }
    private void manualCalendarTime(CalendarModel.Event event){
        Calendar when=Calendar.getInstance();when.add(Calendar.MINUTE,10);when.set(Calendar.SECOND,0);when.set(Calendar.MILLISECOND,0);
        long suggested=CalendarModel.stockTime(event,System.currentTimeMillis(),ZoneId.systemDefault());if(suggested>System.currentTimeMillis())when.setTimeInMillis(suggested);
        new DatePickerDialog(this,(view,y,m,d)->{
            when.set(y,m,d);
            new TimePickerDialog(this,(clock,h,minute)->{
                when.set(Calendar.HOUR_OF_DAY,h);when.set(Calendar.MINUTE,minute);long time=when.getTimeInMillis();
                if(time<=System.currentTimeMillis()){toast("請選未來日期時間，不補響過期提醒");return;}
                try{String id="manual:"+event.key()+":"+time;AlarmStore.upsertLink(this,id,event.title+"\n"+event.calendar.label(),"calendar_link",time,event.metadata("manual_time").put("managed",false),true);toast("已建立 Junba 連動；Google 原活動及通知不變");render();}
                catch(Exception e){toast("連動未排成功，請查授權；Google 原資料不變");}
            },when.get(Calendar.HOUR_OF_DAY),when.get(Calendar.MINUTE),true).show();
        },when.get(Calendar.YEAR),when.get(Calendar.MONTH),when.get(Calendar.DAY_OF_MONTH)).show();
    }
    private void unlinkCalendarEvent(CalendarModel.Event event){
        new AlertDialog.Builder(this).setTitle("只移除此活動的 Junba 連動？").setMessage("Google 原活動及通知全部保留；原廠已建鬧鐘仍需在原廠取消。刷新不會重建你移除的同一筆連動。")
            .setPositiveButton("移除連動",(d,w)->{
                try{LinkPolicy.event(this,event.key(),true);}catch(Exception e){toast("移除選擇未保存，請重試");return;}
                JSONArray records=AlarmStore.all(this);for(int i=0;i<records.length();i++){JSONObject r=records.optJSONObject(i);
                    if(r!=null&&!LinkModel.test(r)&&CalendarModel.belongs(r,event)&&r.has("linkId"))try{AlarmStore.cancel(this,r.optString("key"));}catch(Exception e){toast("有連動未移除，請查清單");}}
                render();
            }).setNegativeButton("保留",null).show();
    }
    private boolean permissionsReady(){return AlarmStore.notificationsVisible(this)&&ReminderStore.canExact(this)&&(!CalendarNotificationListener.enabled(this)||(CalendarRepository.granted(this)&&CalendarNotificationListener.granted(this)));}
    private LinearLayout section(String key,String title,boolean initiallyOpen){
        boolean open=getPreferences(0).getBoolean("fold_"+key,initiallyOpen);
        button(content,(open?"▼ ":"▶ ")+title,()->{getPreferences(0).edit().putBoolean("fold_"+key,!open).apply();render();});
        return open?card():null;
    }
    private void refreshLinks(boolean reload){
        CalendarLinkSync.request(this,()->{if(destroyed)return;if(reload&&calendarBrowser!=null)calendarBrowser.invalidate();render();});
    }
    private void refreshCalendar(){if(calendarBrowser!=null)calendarBrowser.invalidate();toast("正在刷新手機日曆與 Junba 連動…");refreshLinks(true);render();}
    private void testRecord(JSONObject original){
        try{
            JSONObject metadata=new JSONObject(original.toString());metadata.remove("linkId");metadata.remove("managed");metadata.remove("originalWhen");
            metadata.put("testDurationMs",5000);
            JSONObject test=AlarmStore.create(this,"連動測試："+original.optString("title"),"link_test","",System.currentTimeMillis()+3000,metadata);
            AlarmStore.scheduleNew(this,test);toast("3 秒後測試同一排程／音效流程 5 秒；測試不改正式連動");
        }catch(Exception e){toast("測試未完成排程，請檢查通知與精確鬧鐘權限");}
    }
    private void testNote(JSONObject note){
        try{testRecord(new JSONObject().put("title",note.optString("title","無訊息")+"\n"+note.optString("content","無訊息")).put("noteId",note.optInt("id")));}
        catch(Exception e){toast("測試資料未準備完成");}
    }
    private void settings(){
        heading(content,"設定（可收折）");
        LinearLayout auth=section("auth","授權設定｜"+(permissionsReady()?"已完成":"尚需設定"),!permissionsReady());
        if(auth!=null){
            text(auth,"Junba 通知："+(AlarmStore.notificationsVisible(this)?"已允許":"未允許")+"\n精確鬧鐘："+(ReminderStore.canExact(this)?"已允許":"未允許")+
                "\n日曆讀取："+(CalendarRepository.granted(this)?"已允許":"未允許")+"\n通知存取："+(CalendarNotificationListener.granted(this)?"已允許":"未允許"),16);
            button(auth,"Junba 通知設定",this::notificationSettings);
            if(!ReminderStore.canExact(this))button(auth,"允許精確鬧鐘",this::exactSettings);
            if(!CalendarRepository.granted(this))button(auth,"允許讀取日曆",()->requestPermissions(new String[]{Manifest.permission.READ_CALENDAR},102));
            button(auth,"通知存取設定",this::listenerSettings);
            text(auth,"日曆唯讀，不修改 Google 原活動、生日、工作或通知。完成後可按上方標題收折。",14);
        }
        LinearLayout sync=section("calendar","日曆連動設定｜"+(CalendarNotificationListener.enabled(this)?"已啟用":"未啟用"),!CalendarNotificationListener.enabled(this));
        if(sync!=null){
            CheckBox enabled=new CheckBox(this);enabled.setText("啟用 Google 日曆的 Junba 鬧鈴連動");enabled.setChecked(CalendarNotificationListener.enabled(this));sync.addView(enabled);
            enabled.setOnCheckedChangeListener((v,on)->{
                if(on){getSharedPreferences(CalendarNotificationListener.PREF,0).edit().putBoolean("enabled",true).apply();CalendarRefreshJob.register(this);refreshLinks(true);}
                else{CalendarNotificationListener.disable(this);refreshLinks(false);}render();
            });
            CheckBox prelink=new CheckBox(this);prelink.setText("依所選日曆預先連動未來 90 天提醒");prelink.setChecked(LinkPolicy.prelink(this));sync.addView(prelink);
            prelink.setOnCheckedChangeListener((v,on)->{getSharedPreferences(LinkPolicy.PREF,0).edit().putBoolean("prelink",on).apply();refreshLinks(true);});
            text(sync,"預排最多 250 筆；來源未提供確切時間時可在活動卡自行指定 Junba 時間。已過期的提醒不補響。收到已預排的同筆通知時盡量沿用原鬧鈴。",14);
            button(sync,"月份與帳號選擇",()->{page="calendar";render();});button(sync,"立即刷新日曆與連動",this::refreshCalendar);
            text(sync,CalendarLinkSync.status(this),15);
            text(sync,"開 App／返回月份頁會刷新，背景也會嘗試跟隨手機日曆變動。雲端資料由 Google 日曆同步；強制停止或手機省電限制可能延遲背景刷新。",14);
        }
        LinearLayout tools=section("tools","測試與原廠時鐘",false);
        if(tools!=null){
            button(tools,"測試 Junba 響鈴 5 秒",()->{try{testRecord(new JSONObject().put("title","Junba 音效測試"));}catch(Exception ignored){}});
            button(tools,"查看全部連動與測試",()->{page="alarms";render();});button(tools,"開原廠時鐘核對",this::openClock);
            text(tools,"紅燈代表 Junba 系統排程；原廠已建鬧鐘仍需在原廠查看、取消，App 無法讀回或確認。",14);
        }
        LinearLayout data=section("data","資料與版本｜R10",false);
        if(data!=null){text(data,"0.5.0-R10-links\n作者：峻爸\n不含 AI、API Key 或網路權限。移除連動只取消 Junba 鬧鈴，Google 原活動與通知保留。",15);
            button(data,"匯出記事與連動備份",this::exportBackup);text(data,"備份不含 Google 原活動或原廠時鐘資料，尚無自動完整還原，不可據此直接解除安裝。",14);}
    }
    private boolean notificationsGranted(){return AlarmStore.notificationsVisible(this);}
    private void notificationSettings(){
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},101);return;}
        try{startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName()));}
        catch(Exception e){toast("請到系統設定允許 Junba 通知及鬧鐘通知頻道");}
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);if(permissionsReady())getPreferences(0).edit().putBoolean("fold_auth",false).apply();if(calendarBrowser!=null)calendarBrowser.invalidate();CalendarRefreshJob.register(this);refreshLinks(true);render();}
    private void listenerSettings(){try{startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));}catch(Exception e){toast("請到系統設定的通知存取頁授權");}}
    private void exactSettings(){if(Build.VERSION.SDK_INT<31)return;try{startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:"+getPackageName())));}catch(Exception e){toast("請到系統設定允許 Junba 精確鬧鐘");}}
    private void exportBackup(){
        Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE,"Junba_R10_Backup.json");try{startActivityForResult(intent,EXPORT);}catch(Exception e){toast("無法開啟備份儲存視窗");}
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);if(request!=EXPORT||result!=RESULT_OK||data==null||data.getData()==null)return;
        try{
            JSONObject backup=new JSONObject().put("version",7).put("notes",new JSONArray(getSharedPreferences("junba",0).getString("notes","[]")))
                .put("native_links",new JSONArray(getSharedPreferences("junba",0).getString("native_links","[]")))
                .put("alarm_records",new JSONArray(getSharedPreferences(AlarmStore.PREF,0).getString("records","[]")))
                .put("calendar_view",new JSONObject(getSharedPreferences("junba_calendar_view",0).getAll()))
                .put("link_policy",new JSONObject(getSharedPreferences(LinkPolicy.PREF,0).getAll()));
            byte[] bytes=backup.toString(2).getBytes(StandardCharsets.UTF_8);
            try(OutputStream out=getContentResolver().openOutputStream(data.getData(),"wt")){if(out==null)throw new IOException();out.write(bytes);}
            ByteArrayOutputStream copy=new ByteArrayOutputStream();
            try(InputStream input=getContentResolver().openInputStream(data.getData())){if(input==null)throw new IOException();byte[] buffer=new byte[4096];int n;
                while((n=input.read(buffer))!=-1){if(copy.size()+n>10000000)throw new IOException();copy.write(buffer,0,n);}}
            MessageDigest sha=MessageDigest.getInstance("SHA-256");if(!MessageDigest.isEqual(sha.digest(bytes),sha.digest(copy.toByteArray())))throw new IOException();
            toast("備份已寫入並重新讀取核對；不含原廠鬧鐘資料");
        }catch(Exception e){toast("備份未完成核對，請勿據此解除安裝；手機內記事保留");}
    }
}
