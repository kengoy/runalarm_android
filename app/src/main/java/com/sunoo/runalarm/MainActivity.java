package com.sunoo.runalarm;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TimePicker;
import android.widget.Toast;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;

import java.util.Calendar;
import java.util.Timer;
import java.util.TimerTask;


public class MainActivity extends ActionBarActivity implements CompoundButton.OnCheckedChangeListener, TimePicker.OnTimeChangedListener {

    private Context mContext = null;
    public static PendingIntent mAlarmIntent = null;
    private Switch mAlarmSwitch = null;
    private TimePicker mAlarmTimePicker = null;
    private boolean mIsAlarmSet = false;
    private int mHourOfDay = 0;
    private int mMinute = 0;
    SharedPreferences mSharedPreferences = null;
    AlarmManager mAlarmManager = null;
    Timer mTimer = null;
    public final static String SHPR_KEY_ALARM_SET = "ALARM_SET";
    public final static String SHPR_KEY_ALARM_HOUR = "ALARM_HOUR";
    public final static String SHPR_KEY_ALARM_MINUTE = "ALARM_MINUTE";
    public final static String SHPR_KEY_LAUNCH_COUNT = "LAUNCH_COUNT";
    public static final String AD_UNIT_ID = "ca-app-pub-2622423706255892/9601933969";
    private final int AD_FREE_LAUNCH_COUNT = 5;

    private LinearLayout mLayoutAd;
    private AdView mViewAd;

    // for pro
    private Button mAddAlarmButton = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mContext = this;
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        mAlarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);

        if(mAlarmIntent == null) {
            Intent bootIntent = new Intent(this, AlarmBroadcastReceiver.class);
            mAlarmIntent = PendingIntent.getBroadcast(this, 0, bootIntent, PendingIntent.FLAG_CANCEL_CURRENT);
        }

        mAlarmSwitch = (Switch) findViewById(R.id.switch1);
        mIsAlarmSet = mSharedPreferences.getBoolean(SHPR_KEY_ALARM_SET, false);
        mAlarmSwitch.setChecked(mIsAlarmSet);
        mAlarmSwitch.setOnCheckedChangeListener(this);

        mAlarmTimePicker = (TimePicker) findViewById(R.id.timePicker1);
        mAlarmTimePicker.setIs24HourView(true);
        int hour = mSharedPreferences.getInt(SHPR_KEY_ALARM_HOUR, Calendar.getInstance().get(Calendar.HOUR_OF_DAY));
        int minute = mSharedPreferences.getInt(SHPR_KEY_ALARM_MINUTE, Calendar.getInstance().get(Calendar.MINUTE));
        mAlarmTimePicker.setCurrentHour(hour);
        mAlarmTimePicker.setCurrentMinute(minute);
        mHourOfDay = hour;
        mMinute = minute;
        mAlarmTimePicker.setOnTimeChangedListener(this);

        // for free
        int launchCount = mSharedPreferences.getInt(SHPR_KEY_LAUNCH_COUNT, 0);
        mLayoutAd = (LinearLayout) findViewById(R.id.layout_ad);
        if(BuildConfig.FLAVOR.equals("free") && launchCount > AD_FREE_LAUNCH_COUNT) {
            mViewAd = new AdView(this);
            mViewAd.setAdUnitId(AD_UNIT_ID);
            mViewAd.setAdSize(AdSize.BANNER);
            mLayoutAd.addView(mViewAd);

          AdRequest adRequest = new AdRequest.Builder().build();
          mViewAd.loadAd(adRequest);
        } else {
            mLayoutAd.setVisibility(View.INVISIBLE);
        }

        // for pro
        mAddAlarmButton = (Button) findViewById(R.id.addAlarmButton);
        mAddAlarmButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Intent i = new Intent(mContext, SettingsActivity.class);
                startActivity(i);
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        SharedPreferences.Editor editor = mSharedPreferences.edit();
        if (isChecked) {
            setAlarm(mHourOfDay, mMinute);
            Toast.makeText(this, "Alarm is set for " + String.format("%1$02d", mHourOfDay)
                    + ":" + String.format("%1$02d", mMinute) + ". Good night.", Toast.LENGTH_LONG).show();

            mIsAlarmSet = true;
            editor.putInt(SHPR_KEY_ALARM_HOUR, mHourOfDay);
            editor.putInt(SHPR_KEY_ALARM_MINUTE, mMinute);
        } else {
            mAlarmManager.cancel(mAlarmIntent);
            Toast.makeText(this, "Unset alarm.", Toast.LENGTH_LONG).show();
            mIsAlarmSet = false;
        }
        editor.putBoolean(SHPR_KEY_ALARM_SET, mIsAlarmSet);
        editor.commit();
    }

    @Override
    public void onTimeChanged(TimePicker view, int hourOfDay, int minute) {
        mHourOfDay = hourOfDay;
        mMinute = minute;

        if(mIsAlarmSet) {
            mAlarmManager.cancel(mAlarmIntent);

            if(mTimer != null) {
                mTimer.cancel();
                mTimer = null;
            }
            mTimer = new Timer();
            mTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    setAlarm(mHourOfDay, mMinute);
                    updateUI.sendEmptyMessage(0);
                    mTimer = null;
                }
            }, 1000); // set alarm after 1 sec that means after user finish adjusting.
        }
    }

    private void setAlarm(int hourOfDay, int minute) {
        Calendar startTime = Calendar.getInstance();
        startTime.set(Calendar.HOUR_OF_DAY, hourOfDay);
        startTime.set(Calendar.MINUTE, minute);
        startTime.set(Calendar.SECOND, 0);

        // if time is past, set for tomorrow.
        if (startTime.getTimeInMillis() < System.currentTimeMillis()) {
            startTime.add(Calendar.DAY_OF_YEAR, 1);
        }

        // Toast.makeText(mContext, "Device Build.VERSION.SDK_INT : " + Build.VERSION.SDK_INT, Toast.LENGTH_LONG).show();
        if(Build.VERSION.SDK_INT < 19) {
            mAlarmManager.set(AlarmManager.RTC_WAKEUP, startTime.getTimeInMillis(), mAlarmIntent);
        } else {
            mAlarmManager.setExact(AlarmManager.RTC_WAKEUP, startTime.getTimeInMillis(), mAlarmIntent);
        }
    }

    private Handler updateUI = new Handler(){
        @Override
        public void dispatchMessage(Message msg) {
            super.dispatchMessage(msg);

            SharedPreferences.Editor editor = mSharedPreferences.edit();
            editor.putInt(SHPR_KEY_ALARM_HOUR, mHourOfDay);
            editor.putInt(SHPR_KEY_ALARM_MINUTE, mMinute);
            editor.commit();
            Toast.makeText(mContext, "Alarm is reset for " + String.format("%1$02d", mHourOfDay)
                    + ":" + String.format("%1$02d", mMinute) + ". Good night.", Toast.LENGTH_LONG).show();
        }
    };
}