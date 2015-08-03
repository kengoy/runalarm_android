package com.sunoo.runalarm;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;

import hotchemi.android.rate.AppRate;
import hotchemi.android.rate.OnClickButtonListener;


public class GoodMorningActivity extends ActionBarActivity {

    private LinearLayout mLayoutAd;
    private AdView mViewAd;

    public final static String SHPR_KEY_LAUNCH_COUNT = "LAUNCH_COUNT";
    private final int AD_FREE_LAUNCH_COUNT = 0;
    private int mOriginalVol;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_good_morning);

        MediaPlayer mp = MediaPlayer.create(this, R.raw.good_morning);
        try {
            AudioManager manager = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
            mOriginalVol = manager.getStreamVolume(AudioManager.STREAM_MUSIC);
            int volume_max = manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            int volume = PreferenceManager.getDefaultSharedPreferences(this).getInt(MainActivity.SHPR_KEY_ALARM_VOLUME, 5);

            manager.setStreamVolume(AudioManager.STREAM_MUSIC, (int)Math.ceil(volume_max * (volume / 20.0f)), 0);
            manager.setStreamMute(AudioManager.STREAM_MUSIC, false);

            mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                                           @Override
                                           public void onCompletion(MediaPlayer mp) {
                                               AudioManager manager = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
                                               manager.setStreamVolume(AudioManager.STREAM_MUSIC, mOriginalVol, 0);
                                           }
                                       });
            mp.start();
        } catch (Exception e) {
        }

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        int launchCount = sharedPreferences.getInt(SHPR_KEY_LAUNCH_COUNT, 0);
        mLayoutAd = (LinearLayout) findViewById(R.id.layout_ad);
        if(BuildConfig.FLAVOR.equals("free") && launchCount > AD_FREE_LAUNCH_COUNT) {
            mViewAd = new AdView(this);
            mViewAd.setAdUnitId(MainActivity.AD_UNIT_ID);
            mViewAd.setAdSize(AdSize.BANNER);
            mLayoutAd.addView(mViewAd);

          AdRequest adRequest = new AdRequest.Builder().build();
          mViewAd.loadAd(adRequest);
        } else {
            mLayoutAd.setVisibility(View.INVISIBLE);
        }
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt(SHPR_KEY_LAUNCH_COUNT, launchCount + 1);
        editor.commit();

        AppRate.with(this)
                .setInstallDays(1)
                .setLaunchTimes(2) // default 10
                .setRemindInterval(2) // default 1 day.
                .setShowNeutralButton(true) // default true.
                .setDebug(false) // default false.
//                .setShowTitle(false) // default true
                .setOnClickButtonListener(new OnClickButtonListener() { // callback listener.
                    @Override
                    public void onClickButton(int which) {
                    }
                })
                .monitor();

        // Show a dialog if meets conditions.
        AppRate.showRateDialogIfMeetsConditions(this);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_good_morning, menu);
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
}
