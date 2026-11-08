package sl.icf.smsgateway;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;

/**
 * Background upload. Android runs this job only when the phone has internet,
 * so messages received while offline go out as soon as a connection returns.
 * A second job checks every 15 minutes as a safety net.
 */
public class UploadJob extends JobService {

    private static final int JOB_NOW = 4101;
    private static final int JOB_PERIODIC = 4102;

    public static void schedule(Context c) {
        JobScheduler js = (JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (js == null) return;
        JobInfo job = new JobInfo.Builder(JOB_NOW, new ComponentName(c, UploadJob.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setBackoffCriteria(30000, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
                .setPersisted(true)
                .build();
        js.schedule(job);
        schedulePeriodic(c);
    }

    public static void schedulePeriodic(Context c) {
        JobScheduler js = (JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (js == null || js.getPendingJob(JOB_PERIODIC) != null) return;
        JobInfo job = new JobInfo.Builder(JOB_PERIODIC, new ComponentName(c, UploadJob.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(15 * 60 * 1000L)
                .setPersisted(true)
                .build();
        js.schedule(job);
    }

    @Override
    public boolean onStartJob(final JobParameters params) {
        final Context c = getApplicationContext();
        new Thread(new Runnable() {
            @Override
            public void run() {
                String problem = Uploader.run(c);
                boolean retry = problem != null
                        && params.getJobId() == JOB_NOW
                        && Db.get(c).count(Db.PENDING) > 0;
                jobFinished(params, retry);
            }
        }).start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true;
    }
}
