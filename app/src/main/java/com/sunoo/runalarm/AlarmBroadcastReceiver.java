package com.sunoo.runalarm;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.preference.PreferenceManager;
import android.support.v4.content.WakefulBroadcastReceiver;
import android.util.Log;

import java.util.ArrayList;
import java.util.Calendar;

public class AlarmBroadcastReceiver extends WakefulBroadcastReceiver {
    private Context mContext = null;

    private ArrayList<ActivityRecognitionEventListener> mListenerList = new ArrayList<ActivityRecognitionEventListener>();

    public AlarmBroadcastReceiver() {
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        mContext = context;

        Log.d("AlarmBroadcastReceiver", "ACTION=" + intent.getAction());

       String action = intent.getAction();
        if(action != null && action.equals("android.intent.action.BOOT_COMPLETED")) {
            setAlarmForRepeat();
        } else if(action != null && action.equals(ActivityRecognitionIntentService.ACTION)) {
            int activityType = intent.getIntExtra(ActivityRecognitionIntentService.ACTIVITY_TYPE, 4); // 4:UNKNOWN
            for (int i = 0; i < mListenerList.size(); i++) {
                mListenerList.get(i).onActivityRecognize(activityType);
            }
        } else {
            setAlarmForRepeat();
            intent.setClass(context, WakeupActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        }
    }

    public void addActivityRecognitionEventListener(ActivityRecognitionEventListener listener) {
        if(!mListenerList.contains(listener)) {
            mListenerList.add(listener);
        }
    }

    public void removeActivityRecognitionEventListener(ActivityRecognitionEventListener listener) {
        if(mListenerList.contains(listener)) {
            mListenerList.remove(listener);
        }
    }

    private void setAlarmForRepeat() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(mContext);
        boolean isSetAlarm = sharedPreferences.getBoolean(MainActivity.SHPR_KEY_ALARM_SET, false);
        if(isSetAlarm) {
            AlarmManager alarmManager = (AlarmManager) mContext.getSystemService(Context.ALARM_SERVICE);
            int hour = sharedPreferences.getInt(MainActivity.SHPR_KEY_ALARM_HOUR, Calendar.getInstance().get(Calendar.HOUR_OF_DAY));
            int minute = sharedPreferences.getInt(MainActivity.SHPR_KEY_ALARM_MINUTE, Calendar.getInstance().get(Calendar.MINUTE));
            Calendar startTime = Calendar.getInstance();
            startTime.set(Calendar.HOUR_OF_DAY, hour);
            startTime.set(Calendar.MINUTE, minute);
            startTime.set(Calendar.SECOND, 0);

            // if time is past, set for tomorrow.
            if (startTime.getTimeInMillis() < System.currentTimeMillis()) {
                startTime.add(Calendar.DAY_OF_YEAR, 1);
            }

            if(MainActivity.mAlarmIntent == null) {
                Intent bootIntent = new Intent(mContext, AlarmBroadcastReceiver.class);
                MainActivity.mAlarmIntent = PendingIntent.getBroadcast(mContext, 0, bootIntent, PendingIntent.FLAG_CANCEL_CURRENT);
            }
            if(Build.VERSION.SDK_INT < 19) {
                alarmManager.set(AlarmManager.RTC_WAKEUP, startTime.getTimeInMillis(), MainActivity.mAlarmIntent);
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, startTime.getTimeInMillis(), MainActivity.mAlarmIntent);
            }
        }
    }
}
