package tw.junba.reminder;

import android.app.job.*;
import android.content.*;
import android.provider.CalendarContract;

/** Best-effort local refresh on provider changes and every six hours, no network service. */
public final class CalendarRefreshJob extends JobService {
    private static final int PERIODIC=73011,CHANGES=73012;
    static void register(Context c){
        JobScheduler jobs=(JobScheduler)c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        ComponentName service=new ComponentName(c,CalendarRefreshJob.class);
        try{
            if(jobs.getPendingJob(PERIODIC)==null)jobs.schedule(new JobInfo.Builder(PERIODIC,service).setPeriodic(6*3600000L).setPersisted(true).build());
            if(CalendarRepository.granted(c)&&jobs.getPendingJob(CHANGES)==null)jobs.schedule(new JobInfo.Builder(CHANGES,service)
                .addTriggerContentUri(new JobInfo.TriggerContentUri(CalendarContract.Events.CONTENT_URI,JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS))
                .addTriggerContentUri(new JobInfo.TriggerContentUri(CalendarContract.Reminders.CONTENT_URI,JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS))
                .addTriggerContentUri(new JobInfo.TriggerContentUri(CalendarContract.Calendars.CONTENT_URI,JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS))
                .addTriggerContentUri(new JobInfo.TriggerContentUri(CalendarContract.CalendarAlerts.CONTENT_URI,JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS))
                .setTriggerContentUpdateDelay(2000).setTriggerContentMaxDelay(10000).build());
        }catch(RuntimeException ignored){/* Manual and app-entry refresh always remain available. */}
    }
    @Override public boolean onStartJob(JobParameters parameters){
        CalendarLinkSync.request(this,()->{
            jobFinished(parameters,false);
            if(parameters.getJobId()==CHANGES){((JobScheduler)getSystemService(JOB_SCHEDULER_SERVICE)).cancel(CHANGES);register(this);}
        });return true;
    }
    @Override public boolean onStopJob(JobParameters parameters){return true;}
}
