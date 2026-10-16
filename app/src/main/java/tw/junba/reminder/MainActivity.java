package tw.junba.reminder;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.provider.AlarmClock;
import android.provider.CalendarContract;
import android.provider.Settings;
import android.text.InputType;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.*;
import android.widget.*;
import android.speech.RecognizerIntent;
import android.text.TextWatcher;
import android.text.Editable;
import org.json.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.net.*;
import java.io.*;

/** Junba R6: confirmed Gemini voice creation, blank-safe note drafts and alarm audio. */
public class MainActivity extends Activity {
    private LinearLayout root, content;
    private TextView status;
    private ScrollView scroll;
    private final SimpleDateFormat fmt=new SimpleDateFormat("yyyy/MM/dd HH:mm",Locale.TAIWAN);
    private final SimpleDateFormat dateFmt=new SimpleDateFormat("yyyy/MM/dd",Locale.TAIWAN);
    private final SimpleDateFormat timeFmt=new SimpleDateFormat("HH:mm",Locale.TAIWAN);
    private final Calendar displayed=Calendar.getInstance();
    private final Calendar chosenDay=Calendar.getInstance();
    private String page="總覽";
    private boolean keyExpanded=false;
    private int calendarCount=0;
    private int scheduleCount=0;
    private int calendarMirrorCount=0;
    private static final int BLUE=Color.rgb(31,104,199), RED=Color.rgb(197,49,48), GRAY=Color.rgb(132,138,146);
    private static final int EXPORT_REQUEST=401,IMPORT_REQUEST=402,VOICE_REQUEST=403;

    @Override public void onCreate(Bundle b){
        super.onCreate(b);AlarmReceiver.ensureChannel(this);makeLayout();askPermissions();sync();
    }
    @Override public void onResume(){super.onResume();if(status!=null){sync();render();}}
    @Override public void onRequestPermissionsResult(int request,String[] p,int[] r){
        super.onRequestPermissionsResult(request,p,r);sync();render();
    }
    private int dp(int n){return (int)(getResources().getDisplayMetrics().density*n+0.5f);}
    private TextView label(String s,int sp){
        TextView t=new TextView(this);t.setText(s);t.setTextSize(TypedValue.COMPLEX_UNIT_SP,sp);
        t.setTextColor(Color.rgb(30,41,58));t.setPadding(dp(5),dp(6),dp(5),dp(6));return t;
    }
    private void line(LinearLayout l,String s){l.addView(label(s,15));}
    private void heading(LinearLayout l,String s){TextView t=label(s,18);t.setTypeface(null,Typeface.BOLD);l.addView(t);}
    private Button button(LinearLayout l,String s,Runnable r){
        Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(TypedValue.COMPLEX_UNIT_SP,14);
        l.addView(b);b.setOnClickListener(v->r.run());return b;
    }
    private void rowButtons(LinearLayout l,String[] names,Runnable[] actions){
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);l.addView(row);
        for(int i=0;i<names.length;i++){
            final int p=i;Button b=new Button(this);b.setText(names[i]);b.setAllCaps(false);
            b.setTextSize(TypedValue.COMPLEX_UNIT_SP,13);b.setMinWidth(0);b.setMinimumWidth(0);
            row.addView(b,new LinearLayout.LayoutParams(0,dp(51),1));b.setOnClickListener(v->actions[p].run());
        }
    }
    private void card(LinearLayout l){View v=new View(this);v.setBackgroundColor(0xffe7eaef);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(1));p.setMargins(0,dp(9),0,dp(9));l.addView(v,p);}
    private void makeLayout(){
        scroll=new ScrollView(this);root=new LinearLayout(this);root.setOrientation(1);
        root.setPadding(dp(12),dp(14),dp(12),dp(22));scroll.addView(root);setContentView(scroll);
        TextView title=label("Junba 智慧提醒 R6",24);title.setTypeface(null,Typeface.BOLD);root.addView(title);
        status=label("準備同步…",14);root.addView(status);
        rowButtons(root,new String[]{"同步","總覽","記事","日曆"},new Runnable[]{this::syncAndRender,
            ()->go("總覽"),()->go("記事"),()->go("日曆")});
        rowButtons(root,new String[]{"連動紀錄","AI 查詢","設定"},new Runnable[]{
            ()->go("連動紀錄"),()->go("AI 查詢"),()->go("設定")});
        content=new LinearLayout(this);content.setOrientation(1);root.addView(content);
        render();
    }
    private void askPermissions(){
        ArrayList<String> req=new ArrayList<>();
        if(checkSelfPermission(Manifest.permission.READ_CALENDAR)!=PackageManager.PERMISSION_GRANTED)
            req.add(Manifest.permission.READ_CALENDAR);
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            req.add(Manifest.permission.POST_NOTIFICATIONS);
        if(!req.isEmpty())requestPermissions(req.toArray(new String[0]),101);
        else new AlertDialog.Builder(this).setTitle("權限已核准")
            .setMessage("日曆讀取與一般通知權限都已開啟。這不代表通知一定有聲音；請另檢查『Junba 鬧鐘音效 R5』通知類別是否為警示且有聲音、精確鬧鐘權限，以及省電設定。")
            .setPositiveButton("查看通知設定",(d,w)->openNotificationSettings()).setNegativeButton("關閉",null).show();
    }
    private void syncAndRender(){sync();render();toast("已重新讀取已授權來源，請查看同步結果");}
    private void sync(){
        scheduleCount=ReminderStore.reschedule(this);calendarCount=0;
        if(canReadCalendar())try(Cursor c=getContentResolver().query(CalendarContract.Calendars.CONTENT_URI,
            new String[]{CalendarContract.Calendars._ID},null,null,null)){
            if(c!=null)calendarCount=c.getCount();
        }catch(Exception ignored){}
        if(canReadCalendar() && getPreferences(0).getBoolean("calendar_mirror_enabled",false)) {
            calendarMirrorCount=CalendarMirrorStore.refresh(this,selectedCalendarIds());
        } else calendarMirrorCount=CalendarMirrorStore.count(this);
        AlarmManager am=(AlarmManager)getSystemService(ALARM_SERVICE);
        AlarmManager.AlarmClockInfo next=am.getNextAlarmClock();
        String clock=next==null?"系統未提供下一次鬧鐘":fmt.format(new Date(next.getTriggerTime()));
        status.setText("更新："+fmt.format(new Date())+"\n手機日曆："+calendarCount+" 個｜記事："+
            ReminderStore.all(this).length()+" 筆｜App 已排程："+scheduleCount+
            " 筆｜日曆本機提醒："+calendarMirrorCount+" 筆\n下一次系統鬧鐘："+clock);
    }
    private boolean canReadCalendar(){return checkSelfPermission(Manifest.permission.READ_CALENDAR)==PackageManager.PERMISSION_GRANTED;}
    private void go(String s){page=s;render();scroll.smoothScrollTo(0,0);}
    private void render(){
        if(content==null)return;content.removeAllViews();
        switch(page){
            case "記事":notesPage();break;
            case "日曆":calendarPage();break;
            case "連動紀錄":historyPage();break;
            case "AI 查詢":queryPage();break;
            case "設定":settingsPage();break;
            default:homePage();
        }
    }
    private void homePage(){
        heading(content,"今天與下一次提醒");
        button(content,"語音建立記事／鬧鐘（Gemini 辨識）",this::startVoiceNote);
        button(content,"查看今天日曆／本月月曆",()->{chosenDay.setTimeInMillis(System.currentTimeMillis());
            displayed.setTimeInMillis(System.currentTimeMillis());go("日曆");});
        line(content,"整合來源：App 記事 "+ReminderStore.all(this).length()+" 筆、手機日曆 "+calendarCount+
            " 個、已送交原廠時鐘的連動紀錄 "+NativeLinkStore.all(this).length()+" 筆。\n"+
            "Google／Gemini 語音建立且已同步至手機的日曆活動，將出現在日曆頁。原廠完整鬧鐘清單無法透過公開 API 匯入。");
        card(content);
        heading(content,"vivo 原廠鬧鐘");
        button(content,"開啟 vivo 原廠時鐘核對",this::openNativeClock);
        button(content,"建立／測試 vivo 原廠鬧鐘（2 分鐘後）",()->{
            long t=System.currentTimeMillis()+120000L;
            nativeAlarm("test:"+t,"App 測試","Junba R5 原廠響鈴測試",t,"不重複");
        });
        button(content,"手動建立原廠鬧鐘",this::manualNativeAlarm);
        card(content);
        heading(content,"App 自有提醒測試");
        button(content,"測試 1 分鐘後 App 通知（非原廠鈴聲）",()->{
            try{ReminderStore.put(this,"Junba App 通知測試",System.currentTimeMillis()+60000L,0,"不重複");
                syncAndRender();toast(ReminderStore.canExact(this)?"已提交 App 排程，請等待實測":"記事已儲存，尚未獲得精確鬧鐘權限");}
            catch(Exception e){toast("建立失敗："+e.getMessage());}
        });
        line(content,"【重要】R6 本機鬧鐘用 Android 排程及系統預設鬧鐘鈴聲，但並非 vivo 原廠時鐘資料；原廠建立請求仍需核對。");
        button(content,"測試 1 分鐘後本機有聲提醒（非原廠）",()->{
            try{ReminderStore.put(this,"Junba R6 有聲提醒測試",System.currentTimeMillis()+60000L,0,"不重複");
                syncAndRender();toast("已新增提醒；請鎖定畫面測試音效。若沒有聲音，檢查通知頻道及精確鬧鐘權限。");}
            catch(Exception e){toast("測試建立失敗："+e.getMessage());}
        });
        button(content,"檢查 App 精確鬧鐘權限",this::exactSettings);
    }
    private void openNativeClock(){
        try{startActivity(new Intent(AlarmClock.ACTION_SHOW_ALARMS));}
        catch(Exception e){toast("無法開啟時鐘，請手動開啟 vivo 原廠時鐘");}
    }
    private void manualNativeAlarm(){
        final EditText name=new EditText(this);name.setHint("鬧鐘名稱／提醒內容");
        new AlertDialog.Builder(this).setTitle("新增 vivo 原廠鬧鐘")
            .setView(name).setPositiveButton("選擇時間",(d,w)->{
                Calendar c=Calendar.getInstance();new TimePickerDialog(this,(picker,h,m)->{
                    Calendar t=Calendar.getInstance();t.set(Calendar.HOUR_OF_DAY,h);t.set(Calendar.MINUTE,m);
                    t.set(Calendar.SECOND,0);if(t.getTimeInMillis()<=System.currentTimeMillis())t.add(Calendar.DAY_OF_MONTH,1);
                    nativeAlarm("manual:"+System.currentTimeMillis(),"手動建立",name.getText().toString().trim(),t.getTimeInMillis(),"不重複");
                },c.get(Calendar.HOUR_OF_DAY),c.get(Calendar.MINUTE),true).show();
            }).setNegativeButton("取消",null).show();
    }
    private void nativeAlarm(String key,String source,String title,long timestamp,String repeat){
        // Stock Android Clock creation supports time-of-day only. A distant one-off
        // reminder would mistakenly ring on the next matching day: do not send it.
        if(timestamp<=System.currentTimeMillis()){
            new AlertDialog.Builder(this).setTitle("提醒時間已過")
                .setMessage("這筆日曆提前提醒時間已經過去，不能建立過期的原廠鬧鐘。")
                .setPositiveButton("知道了",null).show();return;
        }
        if(timestamp-System.currentTimeMillis()>24L*3600000L &&
            !"每日".equals(repeat) && !"每週".equals(repeat)){
            new AlertDialog.Builder(this).setTitle("原廠時鐘無法指定日期")
                .setMessage("這筆提醒不是未來 24 小時內的鬧鐘。Android 公開原廠時鐘介面僅能設定時、分，若現在送出，可能會提早在明天響。\n\n請在日曆頁啟用『日曆有聲提醒自動同步』，改由 Junba 以完整年月日排程；或等接近提醒日期時再建立原廠鬧鐘。")
                .setPositiveButton("前往日曆同步",(d,w)->go("日曆"))
                .setNegativeButton("取消",null).show();return;
        }
        final String name=title.isEmpty()?"Junba 提醒":title;
        // A Clock intent has clock hour, minute and optional weekdays, but no absolute calendar date.
        final String warning="將呼叫 vivo 時鐘建立鬧鐘，需在原廠畫面完成確認。\n\n"+
            "重要：Android 公開原廠鬧鐘介面無法指定完整年月日；名稱中的日期只是文字。\n"+
            "原廠時鐘可能設為最近的相同時間，而非活動當日；每月規則也需要原廠自行核對。\n\n"+
            "提醒："+fmt.format(new Date(timestamp))+"｜"+name;
        new AlertDialog.Builder(this).setTitle("送往原廠時鐘／需核對").setMessage(warning)
            .setPositiveButton("開啟 vivo 時鐘",(d,w)->{
                Calendar time=Calendar.getInstance();time.setTimeInMillis(timestamp);
                Intent intent=new Intent(AlarmClock.ACTION_SET_ALARM);
                intent.putExtra(AlarmClock.EXTRA_HOUR,time.get(Calendar.HOUR_OF_DAY));
                intent.putExtra(AlarmClock.EXTRA_MINUTES,time.get(Calendar.MINUTE));
                intent.putExtra(AlarmClock.EXTRA_MESSAGE,dateFmt.format(new Date(timestamp))+" "+name);
                intent.putExtra(AlarmClock.EXTRA_SKIP_UI,false);
                if("每日".equals(repeat)||"每週".equals(repeat)){
                    ArrayList<Integer> days=new ArrayList<>();
                    if("每日".equals(repeat)){for(int i=Calendar.SUNDAY;i<=Calendar.SATURDAY;i++)days.add(i);}
                    else days.add(time.get(Calendar.DAY_OF_WEEK));
                    intent.putIntegerArrayListExtra(AlarmClock.EXTRA_DAYS,days);
                }
                try{
                    startActivity(intent);
                    NativeLinkStore.requested(this,key,source,name,timestamp);
                    toast("已送交建立請求；狀態為待核對，非已建立成功");
                }catch(Exception e){toast("vivo 時鐘沒有接受此建立請求；未記錄為已連動");}
            }).setNegativeButton("取消",null).show();
    }
    private void exactSettings(){
        if(Build.VERSION.SDK_INT<31){toast("本機 Android 版本無須額外開啟此權限");return;}
        try{startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
            Uri.parse("package:"+getPackageName())));}
        catch(Exception e){toast("系統無法開啟精確鬧鐘權限頁");}
    }
    private void notesPage(){
        heading(content,"手機記事／提醒管理");
        button(content,"語音新增記事／鬧鐘",this::startVoiceNote);
        button(content,"新增記事＋指定提醒",()->noteEditor(null));
        JSONArray a=ReminderStore.all(this);
        if(a.length()==0)line(content,"尚無 App 記事。由日曆／Gemini 建立的活動請至「日曆」頁讀取。");
        for(int i=0;i<a.length();i++){
            JSONObject o=a.optJSONObject(i);if(o==null)continue;
            final int id=o.optInt("id");final String key="note:"+id;
            final String title=o.optString("title","無訊息"),repeat=o.optString("repeat","不重複");
            final long when=o.optLong("time");final int ahead=o.optInt("ahead");
            heading(content,title);
            line(content,"內容："+o.optString("content","無訊息"));
            line(content,(o.optBoolean("remind",true)?"活動："+fmt.format(new Date(when))+"｜提前 "+ahead+" 分鐘｜"+repeat:"純記事（不設定提醒）")+
                "\n原廠連動："+NativeLinkStore.status(this,key));
            rowButtons(content,new String[]{"編輯","原廠鬧鐘","加入日曆"},new Runnable[]{
                ()->noteEditor(o),()->nativeAlarm(key,"手機記事",title,when-ahead*60000L,repeat),
                ()->insertCalendar(title,when)
            });
            button(content,"刪除此 App 記事（不會刪原廠鬧鐘）",()->{
                new AlertDialog.Builder(this).setMessage("只刪 App 自有記事及排程。若已在原廠建立鬧鐘，請到 vivo 時鐘另外刪除。")
                    .setPositiveButton("刪除",(d,w)->{ReminderStore.remove(this,id);sync();render();})
                    .setNegativeButton("取消",null).show();
            });
            card(content);
        }
    }
    private void insertCalendar(String title,long time){
        try{
            Intent i=new Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI);
            i.putExtra(CalendarContract.Events.TITLE,title);
            i.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME,time);
            i.putExtra(CalendarContract.EXTRA_EVENT_END_TIME,time+3600000L);
            startActivity(i);
            toast("已開啟日曆編輯；需在日曆中自行儲存才會建立");
        }catch(Exception e){toast("未找到可新增活動的日曆 App");}
    }
    private void noteEditor(JSONObject old){noteEditor(old,null,null,null,null);}
    private void noteEditor(JSONObject old,String presetTitle,String presetBody,Long presetTime,Boolean presetRemind){
        LinearLayout box=new LinearLayout(this);box.setOrientation(1);box.setPadding(dp(18),0,dp(18),0);
        EditText title=new EditText(this);title.setSingleLine(true);title.setHint("主題（空白自動填無訊息）");
        String initialTitle=old==null?(presetTitle!=null?presetTitle:getPreferences(0).getString("draft_title","")):old.optString("title","");
        title.setText(initialTitle);box.addView(title);
        EditText body=new EditText(this);body.setHint("內容（空白自動填無訊息）");body.setMinLines(2);
        String initialBody=old==null?(presetBody!=null?presetBody:getPreferences(0).getString("draft_content","")):old.optString("content","");
        body.setText(initialBody);box.addView(body);
        // Draft stays on disk before user taps Save, so failed permissions never erase text.
        TextWatcher watcher=new TextWatcher(){
            @Override public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            @Override public void onTextChanged(CharSequence s,int start,int before,int count){
                getPreferences(0).edit().putString("draft_title",title.getText().toString())
                    .putString("draft_content",body.getText().toString()).apply();
            }
            @Override public void afterTextChanged(Editable e){}
        };
        title.addTextChangedListener(watcher);body.addTextChangedListener(watcher);
        Calendar c=Calendar.getInstance();
        if(old!=null)c.setTimeInMillis(old.optLong("time"));
        else if(presetTime!=null)c.setTimeInMillis(presetTime);
        else c.add(Calendar.DAY_OF_MONTH,1);
        TextView at=label("活動時間："+fmt.format(c.getTime()),15);box.addView(at);
        button(box,"選擇年月日",()->new DatePickerDialog(this,(view,y,m,d)->{
            c.set(y,m,d);at.setText("活動時間："+fmt.format(c.getTime()));
        },c.get(Calendar.YEAR),c.get(Calendar.MONTH),c.get(Calendar.DAY_OF_MONTH)).show());
        button(box,"選擇時分",()->new TimePickerDialog(this,(view,h,m)->{
            c.set(Calendar.HOUR_OF_DAY,h);c.set(Calendar.MINUTE,m);c.set(Calendar.SECOND,0);
            at.setText("活動時間："+fmt.format(c.getTime()));
        },c.get(Calendar.HOUR_OF_DAY),c.get(Calendar.MINUTE),true).show());
        CheckBox remind=new CheckBox(this);remind.setText("啟用時間提醒（取消勾選也能儲存記事）");
        remind.setChecked(old!=null?old.optBoolean("remind",true):(presetRemind==null||presetRemind));box.addView(remind);
        String[] advOptions={"準時","1 分鐘前","5 分鐘前","10 分鐘前","30 分鐘前","60 分鐘前","1 天前","5 天前"};
        int[] advValues={0,1,5,10,30,60,1440,7200};
        line(box,"提前提醒");Spinner adv=new Spinner(this);
        adv.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,advOptions));box.addView(adv);
        if(old!=null){for(int i=0;i<advValues.length;i++)if(advValues[i]==old.optInt("ahead"))adv.setSelection(i);}
        line(box,"App 重複排程");Spinner rep=new Spinner(this);
        String[] repeats={"不重複","每日","每週","每月"};
        rep.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,repeats));box.addView(rep);
        if(old!=null){for(int i=0;i<repeats.length;i++)if(repeats[i].equals(old.optString("repeat")))rep.setSelection(i);}
        CheckBox createNative=new CheckBox(this);createNative.setText("儲存後另開 vivo 原廠時鐘建立（需核對，無法指定完整日期）");box.addView(createNative);
        line(box,"會先儲存完整記事，再嘗試鬧鐘排程；權限不足也不會刪掉你已輸入的文字。");
        ScrollView view=new ScrollView(this);view.addView(box);
        new AlertDialog.Builder(this).setTitle(old==null?"新增記事":"編輯記事")
            .setView(view).setPositiveButton("先存記事，再設定提醒",(dialog,which)->{
                String headline=title.getText().toString().trim();if(headline.isEmpty())headline="無訊息";
                String text=body.getText().toString().trim();if(text.isEmpty())text="無訊息";
                int a=advValues[adv.getSelectedItemPosition()];String r=repeats[rep.getSelectedItemPosition()];
                try{
                    int id;if(old==null)id=ReminderStore.putWithContentAndReminder(this,headline,text,c.getTimeInMillis(),a,r,remind.isChecked());
                    else{id=old.optInt("id");ReminderStore.updateFull(this,id,headline,text,c.getTimeInMillis(),a,r,remind.isChecked());}
                    getPreferences(0).edit().remove("draft_title").remove("draft_content").remove("voice_draft").apply();
                    sync();render();
                    if(!remind.isChecked())toast("記事已儲存（未設定提醒）");
                    else if(!ReminderStore.canExact(this))toast("記事已儲存；請開啟精確鬧鐘權限，排程尚未成功");
                    else toast("記事已儲存並提交鬧鐘排程；請實測聲音");
                    if(createNative.isChecked()&&remind.isChecked())nativeAlarm("note:"+id,"手機記事",headline,c.getTimeInMillis()-a*60000L,r);
                }catch(Exception ex){toast("儲存未完成，已保留草稿，請重新進入記事："+ex.getMessage());}
            }).setNegativeButton("保留草稿並關閉",(d,w)->{
                getPreferences(0).edit().putString("draft_title",title.getText().toString())
                    .putString("draft_content",body.getText().toString()).apply();
            }).show();
    }
    private void startVoiceNote(){
        Intent i=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"zh-TW");
        i.putExtra(RecognizerIntent.EXTRA_PROMPT,"說出記事或提醒，例如：明天下午兩點提醒我開會");
        try{startActivityForResult(i,VOICE_REQUEST);}
        catch(Exception ex){
            toast("手機未提供語音辨識服務，可改用文字輸入");
            voiceReview(getPreferences(0).getString("voice_draft",""));
        }
    }
    private void voiceReview(String raw){
        getPreferences(0).edit().putString("voice_draft",raw).apply();
        EditText input=new EditText(this);input.setText(raw);input.setMinLines(3);
        new AlertDialog.Builder(this).setTitle("語音文字已保留，確認要如何處理")
            .setMessage("語音先由手機的語音辨識服務轉成文字。選擇 Gemini 解析才會把這段文字送到 Gemini API；不會自行建立鬧鐘。")
            .setView(input).setPositiveButton("Gemini 辨識時間",(d,w)->{
                String transcript=input.getText().toString().trim();
                getPreferences(0).edit().putString("voice_draft",transcript).apply();
                if(transcript.isEmpty()){noteEditor(null,"無訊息","無訊息",null,false);return;}
                String key=SecretStore.load(this);
                if(key.isEmpty()){
                    toast("尚未儲存 Gemini Key，先保留原文讓你手動編輯記事");
                    noteEditor(null,"無訊息",transcript,null,false);return;
                }
                toast("Gemini 正在辨識，語音原文已保留");
                new Thread(()->{
                    try{
                        String model=getPreferences(0).getString("gemini_model","gemini-3.8-flash");
                        JSONObject parsed=GeminiNoteParser.parse(key,model,transcript);
                        runOnUiThread(()->confirmVoiceParsed(parsed,transcript));
                    }catch(Exception ex){
                        runOnUiThread(()->{
                            toast("辨識失敗，原文已保留："+ex.getMessage());
                            noteEditor(null,"無訊息",transcript,null,false);
                        });
                    }
                }).start();
            }).setNeutralButton("直接存記事",(d,w)->noteEditor(null,"無訊息",input.getText().toString(),null,false))
            .setNegativeButton("先保留草稿",(d,w)->getPreferences(0).edit()
                .putString("voice_draft",input.getText().toString()).apply()).show();
    }
    private void confirmVoiceParsed(JSONObject parsed,String transcript){
        String title=parsed.optString("title","無訊息");if(title.trim().isEmpty())title="無訊息";
        String body=parsed.optString("content",transcript);if(body.trim().isEmpty())body="無訊息";
        String date=parsed.optString("dateTime","");long time=0L;
        if(parsed.optBoolean("hasReminder",false)&&!date.isEmpty())try{
            SimpleDateFormat parser=new SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.TAIWAN);
            parser.setLenient(false);time=parser.parse(date).getTime();
            if(time<=System.currentTimeMillis())time=0L;
        }catch(Exception ignored){}
        final String t=title,b=body;final Long when=time>0?time:null;
        final boolean hasReminder=when!=null;
        new AlertDialog.Builder(this).setTitle("Gemini 辨識完成，請確認")
            .setMessage("主題："+t+"\n內容："+b+"\n提醒："+(hasReminder?fmt.format(new Date(when)):"無明確未來時間，只存記事")+
                "\n\n只有確認後才會寫入 Junba；不是直接寫入 vivo 原廠時鐘。")
            .setPositiveButton("進入編輯／儲存",(d,w)->noteEditor(null,t,b,when,hasReminder))
            .setNeutralButton("只存記事",(d,w)->noteEditor(null,t,b,null,false))
            .setNegativeButton("保留語音草稿",(d,w)->{}).show();
    }
    private ArrayList<Long> selectedCalendarIds(){
        ArrayList<Long> ids=new ArrayList<>();if(!canReadCalendar())return ids;
        try(Cursor c=getContentResolver().query(CalendarContract.Calendars.CONTENT_URI,
            new String[]{CalendarContract.Calendars._ID},null,null,null)){
            if(c!=null)while(c.moveToNext()){
                long id=c.getLong(0);if(getPreferences(0).getBoolean("cal_"+id,true))ids.add(id);
            }
        }catch(Exception ignored){}
        return ids;
    }
    private static class EventItem{
        long eventId,when,calendarId;
        String title,rrule;
        EventItem(long id,long w,long c,String t,String r){eventId=id;when=w;calendarId=c;title=t;rrule=r;}
        String key(){return "calendar:"+eventId+":"+when;}
    }
    private ArrayList<EventItem> events(long begin,long end){
        ArrayList<EventItem> result=new ArrayList<>();if(!canReadCalendar())return result;
        ArrayList<Long> ids=selectedCalendarIds();if(ids.isEmpty())return result;
        Uri.Builder path=CalendarContract.Instances.CONTENT_URI.buildUpon();
        android.content.ContentUris.appendId(path,begin);android.content.ContentUris.appendId(path,end);
        String[] projection={CalendarContract.Instances.EVENT_ID,CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,CalendarContract.Instances.CALENDAR_ID,CalendarContract.Instances.RRULE};
        try(Cursor c=getContentResolver().query(path.build(),projection,null,null,CalendarContract.Instances.BEGIN+" ASC")){
            if(c!=null)while(c.moveToNext()&&result.size()<400){
                long cal=c.getLong(3);if(!ids.contains(cal))continue;
                result.add(new EventItem(c.getLong(0),c.getLong(2),cal,c.getString(1),c.getString(4)));
            }
        }catch(Exception e){line(content,"日曆讀取失敗，請重新檢查權限或同步狀態");}
        return result;
    }
    private String reminderInfo(long eventId){
        ArrayList<String> times=new ArrayList<>();
        try(Cursor c=getContentResolver().query(CalendarContract.Reminders.CONTENT_URI,
            new String[]{CalendarContract.Reminders.MINUTES},CalendarContract.Reminders.EVENT_ID+"=?",
            new String[]{String.valueOf(eventId)},null)){
            if(c!=null)while(c.moveToNext()){
                int v=c.getInt(0);times.add(v>=0?v+" 分鐘前":"依日曆預設");
            }
        }catch(Exception ignored){}
        return times.isEmpty()?"未設定／由日曆預設":"提醒 "+android.text.TextUtils.join("、",times);
    }
    private void calendarPage(){
        heading(content,"手機日曆｜今日／本月");
        if(!canReadCalendar()){
            line(content,"尚未授權 Android 讀取手機日曆，請先授權。");
            button(content,"開啟日曆授權",this::askPermissions);return;
        }
        line(content,"來源包含已同步至手機的 Google 日曆與 Gemini／語音建立的日曆活動。手機未同步的資料不會出現。");
        heading(content,"日曆提醒同步至 Junba 有聲提示");
        line(content,"勾選的日曆會讀取 45 天內每筆已設定的提前時間（例如 1、30、7200 分鐘），最多 250 筆；由 Android AlarmManager 排程，觸發時使用手機預設鬧鐘鈴聲並顯示停止／5 分鐘稍後提醒。這不是將項目寫入 vivo 原廠時鐘；權限與省電限制仍需真機測試。");
        CheckBox mirror=new CheckBox(this);mirror.setText("啟用日曆有聲提醒自動同步（目前 "+calendarMirrorCount+" 筆）");
        mirror.setChecked(getPreferences(0).getBoolean("calendar_mirror_enabled",false));content.addView(mirror);
        mirror.setOnCheckedChangeListener((v,enabled)->{
            getPreferences(0).edit().putBoolean("calendar_mirror_enabled",enabled).apply();
            if(!enabled){CalendarMirrorStore.clear(this);calendarMirrorCount=0;render();}
            else if(!ReminderStore.canExact(this))new AlertDialog.Builder(this)
                .setMessage("需要先在 Android 設定允許 Junba 使用精確鬧鐘，否則不會準時觸發。")
                .setPositiveButton("開啟設定",(d,w)->exactSettings()).setNegativeButton("稍後",null).show();
            else syncAndRender();
        });
        button(content,"立即重新同步日曆提醒",this::syncAndRender);
        button(content,"檢查有聲通知設定",this::openNotificationSettings);
        button(content,"展開／收合帳號與日曆來源",()->{
            boolean v=getPreferences(0).getBoolean("show_cal_accounts",false);
            getPreferences(0).edit().putBoolean("show_cal_accounts",!v).apply();render();
        });
        if(getPreferences(0).getBoolean("show_cal_accounts",false))calendarAccounts();
        rowButtons(content,new String[]{"◀ 上個月","回到今天","下個月 ▶"},new Runnable[]{
            ()->{displayed.add(Calendar.MONTH,-1);render();},
            ()->{displayed.setTimeInMillis(System.currentTimeMillis());chosenDay.setTimeInMillis(System.currentTimeMillis());render();},
            ()->{displayed.add(Calendar.MONTH,1);render();}
        });
        drawMonth();
        card(content);
        heading(content,"選取日期："+dateFmt.format(chosenDay.getTime()));
        rowButtons(content,new String[]{"◀ 前一天","後一天 ▶"},new Runnable[]{
            ()->{chosenDay.add(Calendar.DAY_OF_MONTH,-1);displayed.setTimeInMillis(chosenDay.getTimeInMillis());render();},
            ()->{chosenDay.add(Calendar.DAY_OF_MONTH,1);displayed.setTimeInMillis(chosenDay.getTimeInMillis());render();}
        });
        drawDay();
    }
    private void calendarAccounts(){
        heading(content,"勾選完整帳號與日曆");
        String[] projection={CalendarContract.Calendars._ID,CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,CalendarContract.Calendars.ACCOUNT_TYPE};
        try(Cursor c=getContentResolver().query(CalendarContract.Calendars.CONTENT_URI,projection,null,null,
            CalendarContract.Calendars.ACCOUNT_NAME+" ASC")){
            if(c!=null)while(c.moveToNext()){
                long id=c.getLong(0);String email=c.getString(1),name=c.getString(2),type=c.getString(3);
                CheckBox box=new CheckBox(this);box.setText((email==null?"未知帳號":email)+"\n"+name+"（"+type+"）");
                box.setChecked(getPreferences(0).getBoolean("cal_"+id,true));content.addView(box);
                box.setOnCheckedChangeListener((v,checked)->{
                    getPreferences(0).edit().putBoolean("cal_"+id,checked).apply();render();
                });
            }
        }catch(Exception e){line(content,"無法取得日曆帳號清單");}
    }
    private static boolean sameDay(long a,long b){
        Calendar x=Calendar.getInstance(),y=Calendar.getInstance();x.setTimeInMillis(a);y.setTimeInMillis(b);
        return x.get(Calendar.YEAR)==y.get(Calendar.YEAR)&&x.get(Calendar.DAY_OF_YEAR)==y.get(Calendar.DAY_OF_YEAR);
    }
    private static boolean noteOnDay(JSONObject note,long day){
        Calendar when=Calendar.getInstance(),target=Calendar.getInstance();
        when.setTimeInMillis(note.optLong("time"));target.setTimeInMillis(day);
        int first=when.get(Calendar.YEAR)*1000+when.get(Calendar.DAY_OF_YEAR);
        int current=target.get(Calendar.YEAR)*1000+target.get(Calendar.DAY_OF_YEAR);
        if(current<first)return false;
        String r=note.optString("repeat","不重複");
        if("每日".equals(r))return true;
        if("每週".equals(r))return when.get(Calendar.DAY_OF_WEEK)==target.get(Calendar.DAY_OF_WEEK);
        if("每月".equals(r))return when.get(Calendar.DAY_OF_MONTH)==target.get(Calendar.DAY_OF_MONTH);
        return when.get(Calendar.YEAR)==target.get(Calendar.YEAR)&&when.get(Calendar.DAY_OF_YEAR)==target.get(Calendar.DAY_OF_YEAR);
    }
    private static long onDateAtOriginalTime(long day,long original){
        Calendar date=Calendar.getInstance(),time=Calendar.getInstance();
        date.setTimeInMillis(day);time.setTimeInMillis(original);
        date.set(Calendar.HOUR_OF_DAY,time.get(Calendar.HOUR_OF_DAY));
        date.set(Calendar.MINUTE,time.get(Calendar.MINUTE));date.set(Calendar.SECOND,0);
        return date.getTimeInMillis();
    }
    private int flag(String key){
        JSONObject obj=NativeLinkStore.find(this,key);
        if(obj==null)return 1;
        return "manual-confirmed".equals(obj.optString("status"))?3:2;
    }
    private void drawMonth(){
        Calendar first=(Calendar)displayed.clone();first.set(Calendar.DAY_OF_MONTH,1);
        first.set(Calendar.HOUR_OF_DAY,0);first.set(Calendar.MINUTE,0);first.set(Calendar.SECOND,0);first.set(Calendar.MILLISECOND,0);
        Calendar end=(Calendar)first.clone();end.add(Calendar.MONTH,1);
        int count=first.getActualMaximum(Calendar.DAY_OF_MONTH);
        int[] flags=new int[count+1];
        for(EventItem e:events(first.getTimeInMillis(),end.getTimeInMillis())){
            Calendar c=Calendar.getInstance();c.setTimeInMillis(e.when);
            if(c.get(Calendar.MONTH)==first.get(Calendar.MONTH))flags[c.get(Calendar.DAY_OF_MONTH)]=Math.max(flags[c.get(Calendar.DAY_OF_MONTH)],flag(e.key()));
        }
        JSONArray notes=ReminderStore.all(this);
        for(int d=1;d<=count;d++){
            Calendar date=(Calendar)first.clone();date.set(Calendar.DAY_OF_MONTH,d);
            for(int i=0;i<notes.length();i++){
                JSONObject n=notes.optJSONObject(i);
                if(n!=null&&noteOnDay(n,date.getTimeInMillis()))
                    flags[d]=Math.max(flags[d],flag("note:"+n.optInt("id")));
            }
        }
        heading(content,first.get(Calendar.YEAR)+" 年 "+(first.get(Calendar.MONTH)+1)+" 月");
        line(content,"● 藍：本人已核對原廠｜● 紅：已送交待核對｜● 灰：尚未連動｜黃底：選取日期");
        LinearLayout weekdays=new LinearLayout(this);content.addView(weekdays);
        for(String text:new String[]{"日","一","二","三","四","五","六"}){
            TextView t=label(text,13);t.setGravity(Gravity.CENTER);
            weekdays.addView(t,new LinearLayout.LayoutParams(0,dp(32),1));
        }
        int offset=first.get(Calendar.DAY_OF_WEEK)-1;
        int cells=((offset+count+6)/7)*7;
        for(int w=0;w<cells/7;w++){
            LinearLayout row=new LinearLayout(this);content.addView(row);
            for(int col=0;col<7;col++){
                int day=w*7+col-offset+1;
                if(day<1||day>count){View empty=new View(this);row.addView(empty,new LinearLayout.LayoutParams(0,dp(54),1));continue;}
                Calendar date=(Calendar)first.clone();date.set(Calendar.DAY_OF_MONTH,day);
                boolean selected=sameDay(date.getTimeInMillis(),chosenDay.getTimeInMillis());
                TextView cell=label(String.valueOf(day)+(flags[day]==0?"":"\n●"),14);
                cell.setGravity(Gravity.CENTER);cell.setClickable(true);
                int bg=selected?0xffffe69b:0xfff4f5f7;
                GradientDrawable shape=new GradientDrawable();shape.setColor(bg);shape.setCornerRadius(dp(6));
                cell.setBackground(shape);
                int fg=flags[day]==3?BLUE:flags[day]==2?RED:GRAY;
                if(flags[day]>0){SpannableString mark=new SpannableString(cell.getText());
                    mark.setSpan(new ForegroundColorSpan(fg),mark.length()-1,mark.length(),0);cell.setText(mark);}
                LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,dp(54),1);
                cp.setMargins(dp(1),dp(1),dp(1),dp(1));row.addView(cell,cp);
                cell.setOnClickListener(v->{chosenDay.setTimeInMillis(date.getTimeInMillis());render();});
            }
        }
    }
    private void drawDay(){
        Calendar begin=(Calendar)chosenDay.clone();begin.set(Calendar.HOUR_OF_DAY,0);
        begin.set(Calendar.MINUTE,0);begin.set(Calendar.SECOND,0);begin.set(Calendar.MILLISECOND,0);
        Calendar end=(Calendar)begin.clone();end.add(Calendar.DAY_OF_MONTH,1);
        int shown=0;
        JSONArray notes=ReminderStore.all(this);
        for(int i=0;i<notes.length();i++){
            JSONObject o=notes.optJSONObject(i);if(o==null||!noteOnDay(o,begin.getTimeInMillis()))continue;
            shown++;int id=o.optInt("id");String t=o.optString("title"),repeat=o.optString("repeat","不重複");
            long time=onDateAtOriginalTime(begin.getTimeInMillis(),o.optLong("time"));int ahead=o.optInt("ahead");
            heading(content,"記事｜"+timeFmt.format(new Date(time))+" "+t);
            line(content,"提前 "+ahead+" 分鐘｜"+repeat+"｜"+NativeLinkStore.status(this,"note:"+id));
            rowButtons(content,new String[]{"編輯","建立原廠鬧鐘"},new Runnable[]{
                ()->noteEditor(o),()->nativeAlarm("note:"+id,"手機記事",t,time-ahead*60000L,repeat)
            });card(content);
        }
        for(EventItem e:events(begin.getTimeInMillis(),end.getTimeInMillis())){
            shown++;String title=e.title==null?"（未命名活動）":e.title;
            heading(content,"日曆｜"+timeFmt.format(new Date(e.when))+" "+title);
            line(content,reminderInfo(e.eventId)+"｜"+(e.rrule==null||e.rrule.isEmpty()?"未重複":e.rrule)+
                "\n原廠連動："+NativeLinkStore.status(this,e.key()));
            button(content,"由這筆日曆活動建立 vivo 原廠鬧鐘（核對）",()->{
                // Account reminders can be several minutes. Prompt explicitly rather than silently guessing.
                chooseCalendarNativeTime(e,title);
            });card(content);
        }
        if(shown==0)line(content,"這一天沒有來自已勾選日曆的活動或本機記事。");
    }
    private ArrayList<Integer> eventReminderMinutes(long eventId){
        TreeSet<Integer> vals=new TreeSet<>();
        try(Cursor c=getContentResolver().query(CalendarContract.Reminders.CONTENT_URI,
            new String[]{CalendarContract.Reminders.MINUTES,CalendarContract.Reminders.METHOD},
            CalendarContract.Reminders.EVENT_ID+"=?",new String[]{String.valueOf(eventId)},null)){
            if(c!=null)while(c.moveToNext()){
                int min=c.getInt(0),method=c.getInt(1);
                if(min>=0 && (method==CalendarContract.Reminders.METHOD_ALERT ||
                    method==CalendarContract.Reminders.METHOD_DEFAULT || method==CalendarContract.Reminders.METHOD_ALARM)) vals.add(min);
            }
        }catch(Exception ignored){}
        return new ArrayList<>(vals);
    }
    private void chooseCalendarNativeTime(EventItem item,String title){
        ArrayList<Integer> minutes=eventReminderMinutes(item.eventId);
        if(minutes.isEmpty()){
            new AlertDialog.Builder(this).setTitle("沒有可辨識的提醒時間")
                .setMessage("此活動沒有明確的提前提醒分鐘數（可能採用日曆預設）。請先在 Google 日曆設定個別活動提醒，再重新同步。")
                .setPositiveButton("知道了",null).show();return;
        }
        String[] labels=new String[minutes.size()];
        for(int i=0;i<minutes.size();i++){
            int m=minutes.get(i);labels[i]= "提前 "+(m>=1440?(m/1440)+" 天"+(m%1440==0?"":" "+m%1440+" 分鐘"):m+" 分鐘")+
                "（"+fmt.format(new Date(item.when-m*60000L))+"）";
        }
        new AlertDialog.Builder(this).setTitle("選擇原日曆提醒時間")
            .setMessage("依日曆實際設定列出；每按一次只會送出一個原廠鬧鐘建立請求。原廠時鐘無法透過公開 API 指定年月日，需親自核對。")
            .setItems(labels,(d,pos)->nativeAlarm(item.key()+":"+minutes.get(pos),"Google／手機日曆",title,
                item.when-minutes.get(pos)*60000L,"不重複"))
            .setNegativeButton("取消",null).show();
    }
    private void historyPage(){
        heading(content,"送交 vivo 原廠時鐘紀錄");
        line(content,"這是由本 App 送出的建立請求及你的手動核對紀錄，不是 vivo 時鐘完整鬧鐘清單。");
        button(content,"開啟 vivo 原廠時鐘",this::openNativeClock);
        JSONArray a=NativeLinkStore.all(this);if(a.length()==0)line(content,"尚未有送交原廠時鐘的紀錄。");
        for(int i=a.length()-1;i>=0;i--){JSONObject o=a.optJSONObject(i);if(o==null)continue;
            String key=o.optString("key"),title=o.optString("title");
            heading(content,title);
            line(content,o.optString("source")+"｜"+fmt.format(new Date(o.optLong("when")))+"\n"+
                NativeLinkStore.status(this,key)+"（由使用者自行確認）");
            rowButtons(content,new String[]{"原廠時鐘","我已核對","改為待核對"},new Runnable[]{
                this::openNativeClock,
                ()->{NativeLinkStore.confirmed(this,key);render();},
                ()->{NativeLinkStore.unconfirm(this,key);render();}
            });card(content);
        }
    }
    private void settingsPage(){
        heading(content,"授權與提醒狀態");
        line(content,"日曆唯讀："+(canReadCalendar()?"已授權":"尚未授權")+"｜通知："+
            (Build.VERSION.SDK_INT<33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED?"已授權":"尚未授權")+
            "｜App 精確鬧鐘："+
            (ReminderStore.canExact(this)?"已授權":"尚未授權")+
            "\n手機原廠鬧鐘的完整清單及稍後提醒次數無法透過公開 API 取得。");
        button(content,"申請日曆／通知權限（若已授權將顯示說明）",this::askPermissions);
        button(content,"檢查精確鬧鐘權限",this::exactSettings);
        button(content,"開啟 Junba 通知設定（選警示和聲音）",this::openNotificationSettings);
        button(content,"開啟 Junba 應用程式資訊（電池及背景設定）",()->{
            try{startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:"+getPackageName())));}catch(Exception e){toast("請從手機設定手動尋找 Junba");}
        });
        line(content,"R6 響鈴改由前景服務播放系統預設鬧鐘音效，通知提供『停止／5 分鐘後提醒』。需要允許通知、精確鬧鐘及背景執行，並在鎖定畫面實測。勿擾模式由系統控制；這不是 vivo 原廠時鐘資料。");
        card(content);
        button(content,keyExpanded?"收合 Gemini API Key 設定 ▲":"展開 Gemini API Key 設定 ▼",()->{
            keyExpanded=!keyExpanded;render();
        });
        if(keyExpanded)geminiSettings();
        card(content);
        heading(content,"本機資料備份／還原");
        line(content,"可備份 App 記事與原廠連動紀錄，方便日後搬移資料。API Key 不會寫進備份檔。\n"+
            "注意：重新安裝前請先備份；不同簽章的 APK 可能無法直接覆蓋安裝。");
        rowButtons(content,new String[]{"匯出 JSON 備份","匯入備份"},new Runnable[]{
            this::exportBackup,()->new AlertDialog.Builder(this).setTitle("確定匯入？")
                .setMessage("匯入會以備份的記事與連動紀錄取代本機現有紀錄，原廠鬧鐘不會跟著重新建立。")
                .setPositiveButton("選擇備份檔",(d,w)->importBackup())
                .setNegativeButton("取消",null).show()
        });
        card(content);
        heading(content,"響鈴驗證");
        line(content,"vivo 原廠的響鈴、貪睡設定必須在原廠時鐘確認；App 無法讀取每次稍後提醒的剩餘次數。\n"+
            "省電管理：請在 vivo 系統設定允許 Junba 背景執行、自動啟動，並實際測試。設定方式依手機韌體版本而異。");
    }
    private void openNotificationSettings(){
        try{
            Intent i=new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS);
            i.putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName());
            i.putExtra(Settings.EXTRA_CHANNEL_ID,AlarmReceiver.CHANNEL);
            startActivity(i);
        }catch(Exception e){try{
            Intent i=new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
            i.putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName());startActivity(i);
        }catch(Exception ex){toast("請從設定 → 應用程式 → Junba → 通知開啟提醒聲音");}}
    }
    private void geminiSettings(){
        heading(content,"Gemini API Key（本機加密儲存）");
        button(content,"一鍵前往 Google AI Studio 取得／查詢 API Key",()->{
            try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://aistudio.google.com/apikey")));}
            catch(Exception e){toast("無法開啟瀏覽器，請至 aistudio.google.com/apikey");}
        });
        String saved=SecretStore.load(this);
        line(content,saved.isEmpty()?"目前沒有已儲存的 Key":"已儲存加密 Key（不顯示明碼）；可更新或刪除");
        EditText field=new EditText(this);field.setSingleLine(true);field.setHint("貼上 Gemini API Key");
        field.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        content.addView(field);
        button(content,"儲存 Key（加密）",()->{
            String key=field.getText().toString().trim();if(key.isEmpty()){toast("請貼上 Key");return;}
            try{SecretStore.save(this,key);field.setText("");toast("Key 已加密儲存在此手機");render();}
            catch(Exception e){toast("無法儲存 Key："+e.getClass().getSimpleName());}
        });
        String current=getPreferences(0).getString("gemini_model","gemini-3.8-flash");
        line(content,"目前使用的模型："+current);
        button(content,"一鍵查詢可用 Gemini 模型／選用",()->{
            String key=field.getText().toString().trim();if(key.isEmpty())key=SecretStore.load(this);
            if(key.isEmpty()){toast("請先輸入或儲存 API Key");return;}
            fetchGeminiModels(key);
        });
        button(content,"測試 Gemini API 連線",()->{
            String key=field.getText().toString().trim();if(key.isEmpty())key=SecretStore.load(this);
            if(key.isEmpty()){toast("請先取得並儲存 API Key");return;}
            callGemini(key,"只回答：連線正常。",true);
        });
        button(content,"刪除此手機儲存的 Key",()->{
            try{SecretStore.save(this,"");toast("已清除儲存 Key");render();}
            catch(Exception e){toast("清除失敗");}
        });
        line(content,"Gemini 可解析在 Junba 中按麥克風錄入的語音文字，經你確認後建立記事與提醒。它不會自動取得其他語音助理歷史、原廠鬧鐘或手機日曆授權。請在 Google AI Studio 自行建立／複製 Key。");
    }
    private void queryPage(){
        heading(content,"Gemini 智慧查詢");
        button(content,"語音建立記事／鬧鐘",this::startVoiceNote);
        line(content,"可使用自己輸入的問題，搭配已授權讀取的近期日曆活動與 App 記事。\n"+
            "按下查詢才會將當次問題及摘要傳給 Gemini API，不會傳送 API Key 或取得語音歷史。");
        EditText input=new EditText(this);input.setHint("例如：未來一週有哪些會議或記事？");
        input.setMinLines(2);content.addView(input);
        CheckBox include=new CheckBox(this);include.setText("這次查詢包含近期授權日曆與 App 記事摘要");content.addView(include);
        button(content,"開始 Gemini 查詢",()->{
            String q=input.getText().toString().trim();if(q.isEmpty()){toast("請輸入問題");return;}
            String key=SecretStore.load(this);if(key.isEmpty()){toast("請先到設定取得並儲存 Gemini API Key");go("設定");return;}
            if(include.isChecked())q=q+"\n以下為經使用者勾選同意的個人提醒摘要，僅根據這些資料回答：\n"+summaryForAI();
            callGemini(key,q,false);
        });
    }
    private String summaryForAI(){
        StringBuilder s=new StringBuilder();
        JSONArray a=ReminderStore.all(this);for(int i=0;i<a.length()&&i<50;i++){
            JSONObject n=a.optJSONObject(i);if(n!=null)s.append("記事 ").append(fmt.format(new Date(n.optLong("time"))))
                .append(" ").append(n.optString("title")).append("\n");
        }
        long now=System.currentTimeMillis();int count=0;
        for(EventItem e:events(now,now+14L*86400000L)){
            if(count++>=50)break;s.append("日曆 ").append(fmt.format(new Date(e.when)))
                .append(" ").append(e.title).append("\n");
        }
        return s.toString();
    }
    private void fetchGeminiModels(String key){
        toast("正在查詢這組金鑰可用的模型…");
        new Thread(()->{
            HttpURLConnection c=null;String error=null;ArrayList<String> names=new ArrayList<>();
            try{
                URL url=new URL("https://generativelanguage.googleapis.com/v1beta/models?pageSize=1000");
                c=(HttpURLConnection)url.openConnection();c.setConnectTimeout(12000);c.setReadTimeout(20000);
                c.setRequestProperty("x-goog-api-key",key);
                int statusCode=c.getResponseCode();
                InputStream in=statusCode>=400?c.getErrorStream():c.getInputStream();
                StringBuilder sb=new StringBuilder();
                if(in!=null)try(BufferedReader r=new BufferedReader(new InputStreamReader(in,"UTF-8"))){
                    String line;while((line=r.readLine())!=null&&sb.length()<300000)sb.append(line);
                }
                if(statusCode!=200)error="HTTP "+statusCode+"："+sb.toString().substring(0,Math.min(350,sb.length()));
                else{
                    JSONArray a=new JSONObject(sb.toString()).optJSONArray("models");
                    if(a!=null)for(int i=0;i<a.length();i++){
                        JSONObject o=a.optJSONObject(i);if(o==null)continue;
                        String name=o.optString("name","");JSONArray methods=o.optJSONArray("supportedGenerationMethods");
                        boolean supported=false;if(methods!=null)for(int j=0;j<methods.length();j++)
                            if("generateContent".equals(methods.optString(j)))supported=true;
                        if(supported && name.startsWith("models/") && !name.contains("tts") && !name.contains("image"))
                            names.add(name.substring(7));
                    }
                    if(names.isEmpty())error="金鑰已回應，但找不到支援文字生成的模型。";
                }
            }catch(Exception ex){error="查詢失敗："+ex.getClass().getSimpleName();}
            finally{if(c!=null)c.disconnect();}
            final String finalError=error;
            runOnUiThread(()->{
                if(finalError!=null)new AlertDialog.Builder(this).setTitle("模型清單讀取失敗")
                    .setMessage(finalError).setPositiveButton("確定",null).show();
                else new AlertDialog.Builder(this).setTitle("選擇此 Key 可用模型")
                    .setItems(names.toArray(new String[0]),(d,which)->{
                        getPreferences(0).edit().putString("gemini_model",names.get(which)).apply();
                        toast("已選："+names.get(which));render();
                    }).setNegativeButton("取消",null).show();
            });
        }).start();
    }
    private void callGemini(String key,String prompt,boolean test){
        toast(test?"正在測試連線":"正在查詢，稍候顯示結果");
        new Thread(()->{
            String outcome;
            HttpURLConnection c=null;
            try{
                String model=getPreferences(0).getString("gemini_model","gemini-3.8-flash");
                URL url=new URL("https://generativelanguage.googleapis.com/v1beta/models/"+
                    java.net.URLEncoder.encode(model,"UTF-8")+":generateContent");
                c=(HttpURLConnection)url.openConnection();c.setRequestMethod("POST");
                c.setRequestProperty("x-goog-api-key",key);
                c.setRequestProperty("Content-Type","application/json; charset=utf-8");
                c.setConnectTimeout(12000);c.setReadTimeout(25000);c.setDoOutput(true);
                JSONObject request=new JSONObject();JSONObject part=new JSONObject().put("text",prompt);
                request.put("contents",new JSONArray().put(new JSONObject().put("parts",new JSONArray().put(part))));
                try(OutputStream os=c.getOutputStream()){os.write(request.toString().getBytes("UTF-8"));}
                int code=c.getResponseCode();InputStream in=code>=400?c.getErrorStream():c.getInputStream();
                String data="";if(in!=null)try(BufferedReader r=new BufferedReader(new InputStreamReader(in,"UTF-8"))){
                    String line;StringBuilder sb=new StringBuilder();while((line=r.readLine())!=null&&sb.length()<20000)sb.append(line);data=sb.toString();
                }
                if(code==200){
                    JSONObject response=new JSONObject(data);JSONArray candidates=response.optJSONArray("candidates");
                    String answer="";
                    if(candidates!=null&&candidates.length()>0){JSONObject first=candidates.optJSONObject(0);
                        JSONObject contentObj=first==null?null:first.optJSONObject("content");
                        JSONArray parts=contentObj==null?null:contentObj.optJSONArray("parts");
                        if(parts!=null)for(int i=0;i<parts.length();i++){
                            JSONObject p=parts.optJSONObject(i);
                            if(p!=null&&!p.optBoolean("thought",false))answer+=p.optString("text","");
                        }
                    }
                    outcome=test?"Gemini 連線成功 (HTTP 200)\n"+answer:answer.isEmpty()?"Gemini 回覆沒有文字內容":answer;
                }else{
                    JSONObject error=new JSONObject(data);JSONObject detail=error.optJSONObject("error");
                    String message=detail==null?"請檢查 Key、模型及配額":detail.optString("message","請檢查金鑰或配額");
                    String hint=code==404?"\n請按『查詢可用 Gemini 模型』選擇此 Key 可用的模型。":
                        code==400?"\n模型或要求格式不相容。":
                        code==401||code==403?"\n請檢查 API Key 或所屬專案的權限。":
                        code==429?"\n已達額度或速率限制，請稍後重試。":"";
                    outcome="連線失敗 (HTTP "+code+")："+message+hint;
                }
            }catch(Exception e){outcome="連線失敗："+e.getClass().getSimpleName()+"（請檢查網路／Key）";}
            finally{if(c!=null)c.disconnect();}
            final String result=outcome;
            runOnUiThread(()->new AlertDialog.Builder(this).setTitle(test?"Gemini API 連線測試":"Gemini 查詢結果")
                .setMessage(result).setPositiveButton("確定",null).show());
        }).start();
    }
    private void exportBackup(){
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/json");
        i.putExtra(Intent.EXTRA_TITLE,"Junba_R5_Reminders_Backup.json");
        try{startActivityForResult(i,EXPORT_REQUEST);}catch(Exception e){toast("無法開啟檔案儲存視窗");}
    }
    private void importBackup(){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");
        try{startActivityForResult(i,IMPORT_REQUEST);}catch(Exception e){toast("無法開啟檔案選擇器");}
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==VOICE_REQUEST){
            if(result==RESULT_OK&&data!=null){
                ArrayList<String> results=data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                if(results!=null&&!results.isEmpty()){voiceReview(results.get(0));return;}
            }
            toast("沒有收到語音文字，既有記事不受影響");return;
        }
        if(result!=RESULT_OK||data==null||data.getData()==null)return;
        Uri uri=data.getData();
        try{
            if(request==EXPORT_REQUEST){
                JSONObject json=new JSONObject().put("version",4).put("notes",ReminderStore.all(this))
                    .put("native_links",NativeLinkStore.all(this));
                try(OutputStream out=getContentResolver().openOutputStream(uri)){
                    if(out==null)throw new IOException("無法寫入備份");
                    out.write(json.toString(2).getBytes("UTF-8"));
                }
                toast("已匯出記事及連動紀錄（不含 API Key）");
            }else if(request==IMPORT_REQUEST){
                StringBuilder sb=new StringBuilder();
                try(InputStream in=getContentResolver().openInputStream(uri)){
                    if(in==null)throw new IOException("無法開啟備份");
                    BufferedReader r=new BufferedReader(new InputStreamReader(in,"UTF-8"));String line;
                    while((line=r.readLine())!=null&&sb.length()<2000000)sb.append(line).append('\n');
                }
                JSONObject json=new JSONObject(sb.toString());
                JSONArray notes=json.optJSONArray("notes"), links=json.optJSONArray("native_links");
                if(notes==null||links==null)throw new JSONException("非 Junba JSON 備份格式");
                ReminderStore.save(this,notes);NativeLinkStore.save(this,links);
                sync();render();toast("已匯入備份；請核對 App 提醒權限與 vivo 原廠鬧鐘");
            }
        }catch(Exception e){toast("備份／還原失敗："+e.getMessage());}
    }
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
}
