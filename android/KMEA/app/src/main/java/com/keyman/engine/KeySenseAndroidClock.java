
package com.keyman.engine;

import android.os.SystemClock;
import java.util.TimeZone;

/**
 * KEYSENSE Android production clock.
 *
 * Provides UTC timestamps and monotonic timestamps
 * without collecting typed content.
 */
public final class KeySenseAndroidClock
        implements KeySenseSessionManager.Clock {

    @Override
    public KeySenseSessionManager.EventTime capture() {

        long utcMs = System.currentTimeMillis();

        long monotonicNs =
                SystemClock.elapsedRealtimeNanos();

        int timezoneOffsetMinutes =
                TimeZone.getDefault().getOffset(utcMs)
                        / 60000;

        return new KeySenseSessionManager.EventTime(
                utcMs,
                monotonicNs,
                timezoneOffsetMinutes
        );
    }
}
