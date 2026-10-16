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
    @Override protected void onStart(){super.onStart();getSharedPreferences(AlarmStore.PREF,0).registerOnSharedPreferenceChangeListener(alarmChanged);}
    @Override protected void onStop(){getSharedPreferences(AlarmStore.PREF,0).unregisterOnSharedPreferenceChangeListener(alarmChanged);updates.removeCallbacks(redraw);super.onStop();}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);if(intent.getBooleanExtra("show_alarms",false))page="alarms";render();}
    @Override public void onResume(){super.onResume();ReminderStore.reschedule(this);if(calendarBrowser!=null&&"calendar".equals(page))calendarBrowser.invalidate();if(content!=null)render();}
    @Override protected void onDestroy(){if(calendarBrowser!=null)calendarBrowser.close();updates.removeCallbacksAndMessages(null);super.onDestroy();}
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
        setContentView(frame);frame.requestApplyInsets();dateFormat.setTimeZone(TimeZone.getDefault());
        heading(content,"Junba 簡易鬧鐘 R9");text(content,"作者：峻爸",14);
        JSONObject current=AlarmStore.current(this);
        if(current!=null){LinearLayout box=card();heading(box,"目前 Junba 提醒");text(box,current.optString("title"),18);snoozeButtons(box,current);}
        if(!AlarmStore.notificationsVisible(this)){
            text(content,"Junba 通知尚未允許，日曆響鈴暫停；開啟後才能顯示停止與延後控制。",16);
            button(content,"開啟 Junba 通知",this::notificationSettings);
        }
        if("calendar".equals(page)){
            button(content,"‹ 返回記事",()->{page="notes";render();});
            if(calendarBrowser==null)calendarBrowser=new CalendarBrowser(this,this::render,()->{page="alarms";render();},()->{page="settings";render();},this::googleNotificationTest,this::testCalendarEvent,this::stockCalendarEvent,this::linkCalendarEvent);
            calendarBrowser.render(content);return;
        }
        if("alarms".equals(page)){button(content,"‹ 返回記事",()->{page="notes";render();});alarmHistory();return;}
        if("settings".equals(page)){button(content,"‹ 返回記事",()->{page="notes";render();});settings();return;}
        text(content,"填名稱、內容、日期與時間，儲存後到原廠時鐘完成核對。",16);
        button(content,"＋ 新增記事鬧鐘",()->editor(null));
        if(!getPreferences(0).getString("simple_draft_token","").isEmpty())button(content,"繼續未完成記事",this::resumeDraft);
        button(content,"Google 日曆月份／帳號",()->{page="calendar";render();});
        button(content,"開啟原廠時鐘",this::openClock);
        button(content,"日曆提醒紀錄／延後提醒",()->{page="alarms";render();});
        button(content,"日曆通知響鈴／設定",()->{page="settings";render();});
        JSONArray notes=ReminderStore.all(this);if(notes.length()==0)text(content,"尚無記事。",16);
        for(int i=notes.length()-1;i>=0;i--){
            JSONObject note=notes.optJSONObject(i);if(note==null)continue;
            int id=note.optInt("id");LinearLayout box=card();heading(box,note.optString("title","無訊息"));
            text(box,dateFormat.format(new Date(note.optLong("time")-note.optInt("ahead")*60000L)),18);
            String body=note.optString("content","無訊息");if(!body.equals(note.optString("title")))text(box,body,16);
            if(note.optBoolean("remind",true))text(box,"舊版 Junba 排程仍保留；編輯儲存後改交原廠核對。",14);
            else text(box,NativeLinkStore.has(this,"note:"+id)?"已送出原廠建立請求；日期及是否儲存仍需核對。":"記事已保存，尚未送交原廠。",14);
            button(box,"編輯記事",()->editor(note));
            button(box,"交給原廠設定",()->handoff(note,true));
            button(box,"開原廠核對／刪除鬧鐘",this::openClock);
            button(box,"刪除記事",()->new AlertDialog.Builder(this).setTitle("刪除這筆記事？")
                .setMessage("只刪除 Junba 記事與該筆舊版 Junba 排程。原廠時鐘的鬧鐘不會刪除，請另外在原廠處理。")
                .setPositiveButton("刪除記事",(d,w)->{try{ReminderStore.remove(this,id);render();}catch(Exception e){toast("記事未刪除，請重試；原資料保留");}})
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
        text(box,"將帶入原廠的名稱、內容與時分；Android 公開介面無法帶入完整日期，請在原廠畫面核對日期、時間並完成儲存。名稱內的日期只是供你核對的文字。",14);
        text(box,"修改或刪除記事不會修改／刪除原廠舊鬧鐘，請在原廠另外處理。",14);
        if(old!=null&&old.optBoolean("remind",true))text(box,"此筆舊版 Junba 排程會在儲存後停用，改由你核對原廠設定。",14);
        TextWatcher watcher=new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){persistDraft(old,draftToken,name,body,when);}
            public void afterTextChanged(Editable e){}
        };name.addTextChangedListener(watcher);body.addTextChangedListener(watcher);persistDraft(old,draftToken,name,body,when);
        ScrollView scroll=new ScrollView(this);scroll.addView(box);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(old==null?"新增記事鬧鐘":"編輯記事鬧鐘").setView(scroll)
            .setPositiveButton("儲存並開啟原廠",null).setNegativeButton("保留草稿",null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            final SimpleNote draft;
            try{draft=SimpleNote.create(name.getText().toString(),body.getText().toString(),when.get(Calendar.YEAR),when.get(Calendar.MONTH)+1,when.get(Calendar.DAY_OF_MONTH),
                when.get(Calendar.HOUR_OF_DAY),when.get(Calendar.MINUTE),System.currentTimeMillis(),ZoneId.systemDefault());}
            catch(Exception e){toast(e instanceof IllegalArgumentException?e.getMessage():"日期無效，請重新選擇");return;}
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            try{
                SimpleNote.Change saved=ReminderStore.saveForStock(this,old==null?null:old.optInt("id"),draftToken,draft);
                prefs.edit().remove("simple_draft_token").remove("simple_draft_name").remove("simple_draft_content").remove("simple_draft_time").remove("simple_draft_edit_id").apply();
                dialog.dismiss();render();
                if(saved.created)handoff(saved.note,true);else toast("這筆記事已儲存，沒有重複新增；請到原廠核對");
            }catch(Exception e){dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);toast("記事未完成儲存，草稿仍保留，請重試");}
        }));dialog.setOnDismissListener(d->render());dialog.show();
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
            case "reference":return "活動參考，未排 Junba 鬧鐘";
            case "stopped":return "已停止";case "timed_out":return "響鈴已自動結束";case "cancelled":return "已取消 Junba 提醒";
            case "superseded":return "另一筆提醒接續響鈴";case "blocked":return "通知未允許，未響鈴";
            case "missed":return "已過提醒時間";case "interrupted":return "響鈴因重新啟動中斷";default:return "未完成響鈴／排程";
        }
    }
    private void alarmHistory(){
        heading(content,"日曆通知與響鈴紀錄");
        text(content,"此處保留已接收的提醒與測試；月份頁可另外瀏覽手機日曆。上方停止鈕只控制 Junba，不會停止 Google 原通知或原廠鬧鐘。",15);
        JSONArray records=AlarmStore.all(this);if(records.length()==0)text(content,"尚無提醒紀錄；更新前的舊通知無法還原。",16);
        for(int n=records.length()-1;n>=0;n--){
            JSONObject r=records.optJSONObject(n);if(r==null)continue;String key=r.optString("key");
            LinearLayout box=card();heading(box,r.optString("title","無訊息"));
            text(box,("calendar".equals(r.optString("source"))?"Google 日曆通知":"calendar_test".equals(r.optString("source"))?"活動響鈴測試（非真實 Google 通知）":"calendar_setup".equals(r.optString("source"))?"活動原廠設定參考":"test".equals(r.optString("source"))?"音效測試":"舊版 Junba 記事提醒")+
                " · "+dateFormat.format(new Date(r.optLong("receivedAt"))),14);
            if(r.has("accountName"))text(box,r.optString("accountName")+" · "+r.optString("calendarName"),14);
            if("calendar_test".equals(r.optString("source"))){
                text(box,r.has("testConfirmedAt")?"測試：本人確認已聽見":"測試不代表 Google 已送出通知；請本人確認音效。",15);
                if(r.has("firedAt")&&!r.has("testConfirmedAt"))button(box,"我已聽見這筆測試",()->{try{AlarmStore.annotate(this,key,new JSONObject().put("testConfirmedAt",System.currentTimeMillis()));render();}catch(Exception e){toast("測試確認未寫入，請重試");}});
            }
            text(box,alarmStatus(r),17);if(!r.optString("error").isEmpty())text(box,r.optString("error"),15);
            button(box,"延後／再次提醒…",()->new AlertDialog.Builder(this).setTitle("多久後再提醒？")
                .setItems(new String[]{"10 分鐘","30 分鐘","1 小時"},(d,w)->snooze(r,new int[]{10,30,60}[w])).setNegativeButton("取消",null).show());
            if(AlarmRecord.pending(r)||"ringing".equals(r.optString("status")))button(box,"取消這筆 Junba 提醒",()->{try{AlarmStore.cancel(this,key);render();}catch(Exception e){toast("取消未完成；仍可按上方停止目前聲音");}});
            if(("calendar".equals(r.optString("source"))||"calendar_setup".equals(r.optString("source")))){
                button(box,"交給原廠建立鬧鐘…",()->stockCalendar(r));
                if(r.has("stockRequestedAt"))text(box,"曾送出原廠請求："+dateFormat.format(new Date(r.optLong("stockWhen")))+"；仍須原廠核對。",14);
                button(box,"開原廠核對／刪除鬧鐘",this::openClock);
            }
            button(box,"刪除這筆紀錄…",()->new AlertDialog.Builder(this).setTitle("刪除此筆紀錄？")
                .setMessage("同時取消這筆 Junba 延後提醒。Google 日曆活動、原通知及原廠鬧鐘不會刪除。")
                .setPositiveButton("刪除並取消",(d,w)->{try{AlarmStore.remove(this,key);render();}catch(Exception e){toast("紀錄未完整刪除，請重試");}}).setNegativeButton("保留",null).show());
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
    private void googleNotificationTest(){
        new AlertDialog.Builder(this).setTitle("以 Google 日曆送出真正測試通知")
            .setMessage("會開啟 Google 日曆的新增畫面，預填 3 分鐘後的測試活動。請核對帳號、設定 1 分鐘前通知並儲存。Google 真正送出通知後，Junba 才能接收；測試活動可在 Google 自行刪除。")
            .setPositiveButton("開啟 Google 建立",(d,w)->{
                long start=System.currentTimeMillis()+180000L;
                String title="Junba 連動測試 "+new SimpleDateFormat("HHmmss",Locale.TAIWAN).format(new Date())+"-"+UUID.randomUUID().toString().substring(0,4);
                Intent intent=new Intent(Intent.ACTION_INSERT).setData(android.provider.CalendarContract.Events.CONTENT_URI).setPackage(CalendarNotificationRule.GOOGLE_CALENDAR)
                    .putExtra(android.provider.CalendarContract.Events.TITLE,title).putExtra(android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME,start)
                    .putExtra(android.provider.CalendarContract.EXTRA_EVENT_END_TIME,start+300000L)
                    .putExtra(android.provider.CalendarContract.Events.DESCRIPTION,"請在 Google 日曆設定 1 分鐘前通知，核對帳號並儲存，再驗證 Junba 接收、響鈴與停止。");
                try{startActivity(intent);}catch(Exception e){toast("請手動在 Google 日曆建立 3 分鐘後活動，設定 1 分鐘前通知並儲存");}
            }).setNegativeButton("取消",null).show();
    }
    private void testCalendarEvent(CalendarModel.Event event){
        new AlertDialog.Builder(this).setTitle("測試這筆活動的 Junba 連動？")
            .setMessage(event.title+"\n"+event.calendar.label()+"\n3 秒後經相同系統排程響鈴 5 秒，可停止或延後。這是測試，Google 真實通知仍需另外送達，不會點紅燈。")
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
        JSONArray records=AlarmStore.all(this);java.util.List<JSONObject> unmatched=new ArrayList<>();
        for(int n=records.length()-1;n>=0;n--){JSONObject r=records.optJSONObject(n);
            if(r!=null&&"calendar".equals(r.optString("source"))&&!r.has("eventId"))unmatched.add(r);}
        if(unmatched.isEmpty()){toast("目前沒有尚未對應的真實 Google 通知；測試不能用來點紅燈");return;}
        String[] labels=new String[unmatched.size()];for(int i=0;i<labels.length;i++){JSONObject r=unmatched.get(i);labels[i]=dateFormat.format(new Date(r.optLong("receivedAt")))+"\n"+r.optString("title");}
        new AlertDialog.Builder(this).setTitle("選擇此活動已收到的通知").setItems(labels,(d,w)->new AlertDialog.Builder(this)
            .setTitle("確認這筆通知屬於此活動？").setMessage(event.title+"\n"+event.calendar.label()+"\n活動日期："+java.time.Instant.ofEpochMilli(event.begin).atZone(event.allDay?java.time.ZoneOffset.UTC:ZoneId.systemDefault()).toLocalDate()+"\n\n"+labels[w])
            .setPositiveButton("確認對應",(dialog,which)->{try{AlarmStore.annotate(this,unmatched.get(w).optString("key"),event.metadata("manual"));render();}catch(Exception error){toast("對應未寫入，請重試");}})
            .setNegativeButton("取消",null).show()).setNegativeButton("取消",null).show();
    }
    private void settings(){
        heading(content,"Google 日曆通知響鈴");
        text(content,"只在 Google 日曆原本的通知到達時，用 Junba 播放系統鬧鐘鈴聲。月份瀏覽需另外允許讀取手機日曆。Google 未送出通知時，不會自行補排。這不是寫入原廠時鐘。",16);
        CheckBox enabled=new CheckBox(this);enabled.setText("啟用 Google 日曆通知響鈴");enabled.setChecked(CalendarNotificationListener.enabled(this));content.addView(enabled);
        enabled.setOnCheckedChangeListener((v,on)->{
            if(on)new AlertDialog.Builder(this).setTitle("授權日曆通知連動？")
                .setMessage("Android 的通知存取授權範圍較廣；本程式只處理 Google 日曆，忽略其他 App，只把已連動通知的標題與文字保存在手機供你查看、延後或刪除，月份瀏覽另由日曆讀取權限控制，沒有網路權限。收到日曆通知後另響 Junba 鬧鈴，原日曆提示仍可能保留。")
                .setPositiveButton("啟用並設定",(d,w)->{getSharedPreferences(CalendarNotificationListener.PREF,0).edit().putBoolean("enabled",true).apply();
                    if(!CalendarNotificationListener.granted(this))listenerSettings();else render();})
                .setNegativeButton("取消",(d,w)->render()).setOnCancelListener(d->render()).show();
            else{CalendarNotificationListener.disable(this);render();}
        });
        text(content,"通知存取："+(CalendarNotificationListener.granted(this)?"已授權":"未授權")+
            "\n精確鬧鐘："+(ReminderStore.canExact(this)?"已授權":"未授權")+"\nJunba 通知："+(notificationsGranted()?"已授權":"未授權"),16);
        if(!CalendarNotificationListener.granted(this))button(content,"設定日曆通知存取",this::listenerSettings);
        if(!ReminderStore.canExact(this))button(content,"允許精確鬧鐘",this::exactSettings);
        if(!AlarmStore.notificationsVisible(this))button(content,"允許 Junba 通知",this::notificationSettings);
        String status=getSharedPreferences(CalendarNotificationListener.PREF,0).getString("status","尚未收到可連動的 Google 日曆通知");text(content,status,14);
        text(content,"原廠記事鬧鐘由你在原廠核對；這裡的權限只供日曆通知及舊版 Junba 排程使用。",14);
        button(content,"測試 Junba 響鈴 5 秒",()->{
            Intent test=new Intent(this,AlarmAudioService.class).setAction(AlarmAudioService.PLAY).putExtra("id",900001).putExtra("title","Junba 響鈴測試").putExtra("duration_ms",5000L);
            try{startForegroundService(test);toast("只測試 Junba 音效；日曆連動需等實際通知驗證");}catch(Exception e){toast("系統未允許啟動響鈴，請檢查通知與背景設定");}
        });
        button(content,"日曆提醒紀錄／延後提醒",()->{page="alarms";render();});
        button(content,"開啟 Google 日曆",()->{
            Intent intent=getPackageManager().getLaunchIntentForPackage(CalendarNotificationRule.GOOGLE_CALENDAR);
            if(intent==null){toast("請從手機桌面開啟 Google 日曆");return;}try{startActivity(intent);}catch(Exception e){toast("無法開啟 Google 日曆");}
        });
        heading(content,"資料與版本");text(content,"0.4.0-R9-calendar\n舊記事與原廠請求紀錄保留；App 不含 AI、API Key 設定或網路權限。Gemini 語音請直接使用手機上的 Gemini。",14);
        button(content,"匯出記事備份",this::exportBackup);
        text(content,"備份不含原廠時鐘資料或舊 API Key；不能單靠備份檔就假定可安全解除安裝。",14);
    }
    private boolean notificationsGranted(){return AlarmStore.notificationsVisible(this);}
    private void notificationSettings(){
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},101);return;}
        try{startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName()));}
        catch(Exception e){toast("請到系統設定允許 Junba 通知及鬧鐘通知頻道");}
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);if(calendarBrowser!=null)calendarBrowser.invalidate();render();}
    private void listenerSettings(){try{startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));}catch(Exception e){toast("請到系統設定的通知存取頁授權");}}
    private void exactSettings(){if(Build.VERSION.SDK_INT<31)return;try{startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:"+getPackageName())));}catch(Exception e){toast("請到系統設定允許 Junba 精確鬧鐘");}}
    private void exportBackup(){
        Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE,"Junba_R9_Backup.json");try{startActivityForResult(intent,EXPORT);}catch(Exception e){toast("無法開啟備份儲存視窗");}
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);if(request!=EXPORT||result!=RESULT_OK||data==null||data.getData()==null)return;
        try{
            JSONObject backup=new JSONObject().put("version",6).put("notes",new JSONArray(getSharedPreferences("junba",0).getString("notes","[]")))
                .put("native_links",new JSONArray(getSharedPreferences("junba",0).getString("native_links","[]")))
                .put("alarm_records",new JSONArray(getSharedPreferences(AlarmStore.PREF,0).getString("records","[]")))
                .put("calendar_view",new JSONObject(getSharedPreferences("junba_calendar_view",0).getAll()));
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
