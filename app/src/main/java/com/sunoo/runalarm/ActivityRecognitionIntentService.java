package com.sunoo.runalarm;

import android.app.IntentService;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.google.android.gms.location.ActivityRecognitionResult;
import com.google.android.gms.location.DetectedActivity;

import java.util.List;

/**
 * An {@link IntentService} subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 * <p/>
 * helper methods.
 */
public class ActivityRecognitionIntentService extends IntentService {

    public static final String ACTION_START = "com.sunoo.runalarm.ActivityRecognitionIntentService.ACTION_START";
    public static final String ACTION = "ACTION.ACTIVITY.RECOGNITION.TYPE";
    public static final String ACTIVITY_TYPE = "activityType";

    private static final String TAG = ActivityRecognitionIntentService.class.getSimpleName();
    private final ActivityRecognitionIntentService self = this;
    private static MediaPlayer mPlayer = null;
    private static Vibrator mVibrator = null;
    private static final int ALARM_VIBRATOR_TIME = 3 * 60 * 1000; // 3 mins
    private int mOriginalVol;

    private int remainingSec = 3;
    private boolean isIgnoreEvent = false;

    public ActivityRecognitionIntentService() {
        super("ActivityRecognitionIntentService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {

        if (intent != null) {
            if(ACTION_START.equals(intent.getAction())){
                startAlarm();
            }

            if (ActivityRecognitionResult.hasResult(intent)) {
                if(isIgnoreEvent) {
                    return;
                }
                resumeAlarm();

                ActivityRecognitionResult result = ActivityRecognitionResult.extractResult(intent);
                DetectedActivity mostProbableActivity = result.getMostProbableActivity();

                int activityType = mostProbableActivity.getType();
                int confidence = mostProbableActivity.getConfidence();

                Log.i(TAG, "confidence   = " + mostProbableActivity.getConfidence());
                Log.i(TAG, ACTIVITY_TYPE +  " = " + activityType);

                if (confidence >= 50) {
                    if (activityType == DetectedActivity.ON_FOOT) {
                        DetectedActivity betterActivity = walkingOrRunning(result.getProbableActivities());
                        if (betterActivity != null) {
                            activityType = betterActivity.getType();
                        }
                    }
                    if(activityType == DetectedActivity.RUNNING) {
                        stopAlarm();
                        Intent goodMorningActivity = new Intent(this, GoodMorningActivity.class);
                        goodMorningActivity.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(goodMorningActivity);
                    } else if (activityType == DetectedActivity.WALKING) {
                        remainingSec -= 1;
                        if(remainingSec <= 0) {
                            stopAlarm();
                            Intent goodMorningActivity = new Intent(this, GoodMorningActivity.class);
                            goodMorningActivity.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(goodMorningActivity);
                        } else {
                            /*
                            isIgnoreEvent = true;
                            try {
                                Thread.sleep(1000);
                            } catch (InterruptedException e) {
                            }
                            isIgnoreEvent = false;
                            */
                        }
                    }

                    Intent i = new Intent(ACTION);
                    i.putExtra("activityType", activityType);
                    sendBroadcast(i);

                    /* for debug
                    notification(getTypeName(activityType));
                    */
                }
            }
        }
    }
    private DetectedActivity walkingOrRunning(List<DetectedActivity> probableActivities) {
        DetectedActivity myActivity = null;
        int confidence = 0;
        for (DetectedActivity activity : probableActivities) {
            if (activity.getType() != DetectedActivity.RUNNING && activity.getType() != DetectedActivity.WALKING)
                continue;

            if (activity.getConfidence() > confidence)
                myActivity = activity;
        }

        return myActivity;
    }

    private void notification(String message) {
        Intent intent = new Intent(self, MainActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(self, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(self);
        builder.setContentIntent(contentIntent);
        builder.setTicker(message);
        builder.setSmallIcon(R.drawable.ic_runalarm);
        builder.setContentTitle("ActivityRecognition");
        builder.setContentText(message);
        builder.setWhen(System.currentTimeMillis());
        builder.setAutoCancel(true);
        NotificationManager manager = (NotificationManager) getSystemService(Service.NOTIFICATION_SERVICE);
        manager.notify(0, builder.build());
    }

    private String getTypeName(int activityType) {
        switch (activityType) {
            case DetectedActivity.IN_VEHICLE:
                return "車で移動中";
            case DetectedActivity.ON_BICYCLE:
                return "自転車で移動中";
            case DetectedActivity.ON_FOOT:
                return "徒歩で移動中";
            case DetectedActivity.WALKING:
                return "歩いている";
            case DetectedActivity.RUNNING:
                return "走っている";
            case DetectedActivity.STILL:
                return "待機中";
            case DetectedActivity.UNKNOWN:
                return "不明";
            case DetectedActivity.TILTING:
                return "デバイスが傾き中";
        }
        return null;
    }

    private void startAlarm() {
        if(mPlayer == null) {
            mPlayer = MediaPlayer.create(this, R.raw.wakeup_03);
            try {
                mPlayer.setLooping(true);
                mPlayer.start();

                AudioManager manager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                mOriginalVol = manager.getStreamVolume(AudioManager.STREAM_MUSIC);
                int volume_max = manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                int volume = PreferenceManager.getDefaultSharedPreferences(this).getInt(MainActivity.SHPR_KEY_ALARM_VOLUME, 5);

                manager.setStreamVolume(AudioManager.STREAM_MUSIC, (int)Math.ceil(volume_max * (volume / 20.0f)), 0);
                manager.setStreamMute(AudioManager.STREAM_MUSIC, false);
            } catch (IllegalStateException e) {
                e.printStackTrace();
            }

            if(mVibrator == null) {
                mVibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                long[] pattern = {0, 300, 100, 300, 5000}; // OFF/ON/OFF/ON...
                mVibrator.vibrate(pattern, 0);
                //mVibrator.vibrate(ALARM_VIBRATOR_TIME); // 3 min
            }
        }
    }

    private void resumeAlarm() {
        if(mPlayer == null) {
            // resume wakeup activity
            Intent i = new Intent(this, WakeupActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        }
    }

    private void stopAlarm() {
        if (mPlayer != null) {
            mPlayer.stop();
            mPlayer.reset();
            mPlayer.release();
            mPlayer = null;
            AudioManager manager = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
            manager.setStreamVolume(AudioManager.STREAM_ALARM, mOriginalVol, 0);

            if(mVibrator != null) {
                mVibrator.cancel();
                mVibrator = null;
            }
        }
    }
}
