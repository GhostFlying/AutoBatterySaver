package com.ghostflying.autobatterysaver.service;

import android.app.AlarmManager;
import android.app.IntentService;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.provider.Settings;

import com.ghostflying.autobatterysaver.BuildConfig;
import com.ghostflying.autobatterysaver.util.AirplaneModeUtil;
import com.ghostflying.autobatterysaver.util.BatterySaverModeUtil;
import com.ghostflying.autobatterysaver.util.SettingUtil;
import com.ghostflying.autobatterysaver.util.WorkingMode;

import java.util.Date;

public class WorkService extends IntentService {
    private static final String ACTION_START = BuildConfig.APPLICATION_ID + ".START_SLEEP";
    private static final String ACTION_STOP = BuildConfig.APPLICATION_ID + ".STOP_SLEEP";
    private static final long USER_INACTIVITY_THRESHOLD = 15L * 60L * 1000L;
    private static final long DELAYED_TIME_MILLISECONDS = 15L * 60L * 1000L;
    private static final int START_INTENT_REQUEST_CODE = 1;

    public WorkService() {
        super("WorkService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null) {
            if (ACTION_START.equals(intent.getAction())) {
                enableSleepModeIfNeeded();
            }
        }
    }

    private void enableSleepModeIfNeeded() {
        PowerManager manager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        Date lastScreenOffTime = UserDetectorService.lastScreenOffTime;
        if (manager.isInteractive()
                || lastScreenOffTime == null // this should not happen
                || (new Date().getTime() - lastScreenOffTime.getTime()) < USER_INACTIVITY_THRESHOLD) {
            // screen is on or the user use the device before little time..
            setDelayedAlarm();
            return;
        }

        WorkingMode mode = SettingUtil.getWorkingMode(this);
        switch (mode) {
            case BATTERY_SAVER:
                setBatterySaverMode(true);
                break;
            case AIRPLANE:
                setAirplaneMode(true);
                break;
            case BOTH:
                setBatterySaverMode(true);
                setAirplaneMode(true);
        }
        stopService(new Intent(this, UserDetectorService.class));
    }

    private void setDelayedAlarm() {
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        long runTime = new Date().getTime() + DELAYED_TIME_MILLISECONDS;
        Intent workIntent = new Intent(this, WorkService.class);
        workIntent.setAction(ACTION_START);
        alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                runTime,
                PendingIntent.getService(this, START_INTENT_REQUEST_CODE, workIntent, 0)
        );
    }

    private void setBatterySaverMode(boolean mode) {
        PowerManager manager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        boolean batterySaverModeNow = manager.isPowerSaveMode();
        if (batterySaverModeNow != mode) {
            if (mode)
                BatterySaverModeUtil.enable();
            else
                BatterySaverModeUtil.disable();
        }
    }

    private void setAirplaneMode(boolean mode) {
        if (isAirplaneMode() != mode) {
            if (mode)
                AirplaneModeUtil.enable();
            else
                AirplaneModeUtil.disable();
        }
    }

    private boolean isAirplaneMode() {
        return Settings.Global.getInt(getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0) != 0;
    }
}