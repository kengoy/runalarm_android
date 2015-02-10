package com.sunoo.runalarm;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.DetectedActivity;
import com.sunoo.runalarm.util.SystemUiHider;
import com.google.android.gms.location.ActivityRecognition;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 *
 * @see SystemUiHider
 */
public class WakeupActivity extends Activity implements ActivityRecognitionEventListener {

    private static final String TAG = WakeupActivity.class.getSimpleName();

    /**
     * Whether or not the system UI should be auto-hidden after
     * {@link #AUTO_HIDE_DELAY_MILLIS} milliseconds.
     */
    private static final boolean AUTO_HIDE = true;

    /**
     * If {@link #AUTO_HIDE} is set, the number of milliseconds to wait after
     * user interaction before hiding the system UI.
     */
    private static final int AUTO_HIDE_DELAY_MILLIS = 3000;

    /**
     * If set, will toggle the system UI visibility upon interaction. Otherwise,
     * will show the system UI visibility upon interaction.
     */
    private static final boolean TOGGLE_ON_CLICK = true;

    /**
     * The flags to pass to {@link SystemUiHider#getInstance}.
     */
    private static final int HIDER_FLAGS = SystemUiHider.FLAG_HIDE_NAVIGATION;

    /**
     * The instance of the {@link SystemUiHider} for this activity.
     */
    private SystemUiHider mSystemUiHider;

    private final WakeupActivity self = this;
    private GoogleApiClient googleApiClient;
    private ActivityRecognition mClient;
    private AlarmBroadcastReceiver mReceiver;
    private MediaPlayer mPlayer;

    private TextView mTVActivity = null;

    private LinearLayout mLayoutAd;
    private AdView mViewAd;

    public final static String SHPR_KEY_LAUNCH_COUNT = "LAUNCH_COUNT";
    private final int AD_FREE_LAUNCH_COUNT = 5;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_wakeup);

        final View contentView = findViewById(R.id.fullscreen_content);

        // Set up an instance of SystemUiHider to control the system UI for
        // this activity.
        mSystemUiHider = SystemUiHider.getInstance(this, contentView, HIDER_FLAGS);
        mSystemUiHider.setup();
        mSystemUiHider
                .setOnVisibilityChangeListener(new SystemUiHider.OnVisibilityChangeListener() {
                    // Cached values.
                    int mControlsHeight;
                    int mShortAnimTime;

                    @Override
                    @TargetApi(Build.VERSION_CODES.HONEYCOMB_MR2)
                    public void onVisibilityChange(boolean visible) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR2) {
                            // If the ViewPropertyAnimator API is available
                            // (Honeycomb MR2 and later), use it to animate the
                            // in-layout UI controls at the bottom of the
                            // screen.
                            if (mShortAnimTime == 0) {
                                mShortAnimTime = getResources().getInteger(
                                        android.R.integer.config_shortAnimTime);
                            }
                        }

                        if (visible && AUTO_HIDE) {
                            // Schedule a hide().
                            delayedHide(AUTO_HIDE_DELAY_MILLIS);
                        }
                    }
                });

        // Set up the user interaction to manually show or hide the system UI.
        contentView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (TOGGLE_ON_CLICK) {
                    mSystemUiHider.toggle();
                } else {
                    mSystemUiHider.show();
                }
            }
        });

        createGoogleLocationServiceClient();
        if (servicesConnected() && !googleApiClient.isConnected()) {
//            Log.d(TAG, "GoogleApiClient Connection Initiated: connect() Called");
            googleApiClient.connect();

            mReceiver = new AlarmBroadcastReceiver();
            IntentFilter filter = new IntentFilter();
            filter.addAction(ActivityRecognitionIntentService.ACTION);
            registerReceiver(mReceiver, filter);

        } else {
//            Log.e(TAG, "GoogleApiClient already connected or is unavailable");
        }
        mReceiver.addActivityRecognitionEventListener(this);

        mTVActivity = (TextView) findViewById(R.id.your_activity);

        int launchCount = PreferenceManager.getDefaultSharedPreferences(this).getInt(SHPR_KEY_LAUNCH_COUNT, 0);
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
    }

    @Override
    protected void onDestroy() {
        if (servicesConnected() && googleApiClient.isConnected()) {
            unregisterReceiver(mReceiver);
            ActivityRecognition.ActivityRecognitionApi.removeActivityUpdates(googleApiClient, getPendingIntent());
//            Toast.makeText(self, "GoogleApiClient Disconnected.", Toast.LENGTH_LONG).show();
        } else {
            Log.e(TAG, "GoogleApiClient already disconnected.");
        }

        mReceiver.removeActivityRecognitionEventListener(this);
        super.onDestroy();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction()== KeyEvent.ACTION_DOWN) {
            switch (event.getKeyCode()) {
                case KeyEvent.KEYCODE_BACK:
                    // Do not exit current activity.
                    return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private void createGoogleLocationServiceClient() {
        googleApiClient = new GoogleApiClient.Builder(this).addApi(ActivityRecognition.API)
                .addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {

                    @Override
                    public void onConnectionSuspended(int arg0) {
                        Log.d(TAG, "GoogleApiClient Suspended");
                    }

                    @Override
                    public void onConnected(Bundle arg0) {
                        Log.d(TAG, "GoogleApiClient Connected Now");
                        ActivityRecognition.ActivityRecognitionApi.requestActivityUpdates(googleApiClient,
                                0, getPendingIntent()).setResultCallback(
                                new ResultCallback<Status>() {

                                    @Override
                                    public void onResult(Status arg0) {
                                        if (arg0.isSuccess()) {
                                            Log.d(TAG, "Updates Requested Successfully");
                                        } else {
                                            Log.d(TAG, "Updates could not be requested");
                                        }
                                    }
                                });
                    }
                }).addOnConnectionFailedListener(new GoogleApiClient.OnConnectionFailedListener() {
                    @Override
                    public void onConnectionFailed(ConnectionResult arg0) {
                        Log.d(TAG, "GoogleApiClient Connection Failed");
                    }
                }).build();

        Intent intent = new Intent(ActivityRecognitionIntentService.ACTION_START);
        startService(intent);
    }

    private PendingIntent getPendingIntent() {
        Intent intent = new Intent(this, ActivityRecognitionIntentService.class);
        return PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private boolean servicesConnected() {
        int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(self);
        if (ConnectionResult.SUCCESS == resultCode) {
            return true;
        } else {
            Log.e(TAG, "play_services_unavailable!");
            return false;
        }
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        // Trigger the initial hide() shortly after the activity has been
        // created, to briefly hint to the user that UI controls
        // are available.
        delayedHide(100);
    }


    Handler mHideHandler = new Handler();
    Runnable mHideRunnable = new Runnable() {
        @Override
        public void run() {
            mSystemUiHider.hide();
        }
    };

    /**
     * Schedules a call to hide() in [delay] milliseconds, canceling any
     * previously scheduled calls.
     */
    private void delayedHide(int delayMillis) {
        mHideHandler.removeCallbacks(mHideRunnable);
        mHideHandler.postDelayed(mHideRunnable, delayMillis);
    }

    public void onActivityRecognize(int activityType) {
        Log.v(TAG, "onActivityRecognize type=" + activityType);

        String activity;
        switch (activityType) {
            case DetectedActivity.IN_VEHICLE:
                activity = "IN CAR.";
                break;
            case DetectedActivity.ON_BICYCLE:
                activity = "ON BICYCLE.";
                break;
            case DetectedActivity.ON_FOOT:
                activity = "ON FOOT.";
                break;
            case DetectedActivity.WALKING:
                activity = "WALKING.";
                break;
            case DetectedActivity.RUNNING:
                activity = "RUNNING.";
                finish();
                break;
            case DetectedActivity.STILL:
                /* FALL THROUGH */
            case DetectedActivity.TILTING:
                /* FALL THROUGH */
            case DetectedActivity.UNKNOWN:
                /* FALL THROUGH */
            default:
                activity = "UNKNOWN ACTIVITY.";
                break;
        }
        mTVActivity.setText(activity);
    }
}
