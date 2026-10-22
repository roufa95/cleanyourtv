package com.gathin.tvhome.calendar;

import android.app.Activity;
import android.text.SpannableString;
import android.text.Spanned;
import android.view.View;
import android.widget.TextView;

import com.gathin.tvhome.R;
import java.util.List;
import java.util.Locale;

/** The race-weekend panel on Home (a theme's race_calendar): the next or live session and the weekend's timetable. */
public final class RacePanel {

    private static final int RACE_RED = 0xFFFF3B2F;

    private final Activity activity;
    private final List<RaceWeekend.Race> raceCalendar;
    private final View racePanel;
    private final TextView raceKicker, raceHeadline, raceSessions;

    public RacePanel(Activity activity, List<RaceWeekend.Race> raceCalendar) {
        this.activity = activity;
        this.raceCalendar = raceCalendar;
        racePanel = activity.findViewById(R.id.racePanel);
        raceKicker = activity.findViewById(R.id.raceKicker);
        raceHeadline = activity.findViewById(R.id.raceHeadline);
        raceSessions = activity.findViewById(R.id.raceSessions);
    }

    public View view() {
        return racePanel;
    }

    public boolean isShown() {
        return racePanel.getVisibility() == View.VISIBLE;
    }

    /**
     * Shows or hides the race-weekend panel for this minute. Text only, set once a minute with the clock:
     * nothing animates, so the panel costs the GPU nothing between ticks.
     */
    public void update() {
        if (raceCalendar.isEmpty()) return;
        RaceWeekend.State s = RaceWeekend.at(raceCalendar, java.time.Instant.now());
        if (s == null) {
            racePanel.setVisibility(View.GONE);
            return;
        }
        RaceWeekend.Race race = s.race;
        raceKicker.setText(race.place.isEmpty()
                ? activity.getString(R.string.race_kicker_no_place, race.round, race.shortName())
                : activity.getString(R.string.race_kicker, race.round, race.shortName(), race.place));

        if (s.live != null) {
            SpannableString live = new SpannableString("●  " + activity.getString(R.string.race_live, s.live.title(activity.getResources())));
            live.setSpan(new android.text.style.ForegroundColorSpan(RACE_RED), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            raceHeadline.setText(live);
        } else if (s.next != null) {
            String in = RaceWeekend.countdown(activity.getResources(), s.untilNext);
            raceHeadline.setText(s.next.isRace() ? activity.getString(R.string.race_lights_out, in)
                    : activity.getString(R.string.race_next, s.next.title(activity.getResources()), in));
        } else {
            raceHeadline.setText(R.string.race_finished);
        }

        // FP1 FRI 11:00 PM   FP2 SAT 2:30 AM ...: done sessions fade, the live one is red, the next one bright.
        java.time.format.DateTimeFormatter when = java.time.format.DateTimeFormatter.ofPattern(
                android.text.format.DateFormat.is24HourFormat(activity) ? "EEE HH:mm" : "EEE h:mm a", Locale.getDefault())
                .withZone(java.time.ZoneId.systemDefault());
        android.text.SpannableStringBuilder row = new android.text.SpannableStringBuilder();
        for (int i = 0; i < race.sessions.size(); i++) {
            RaceWeekend.Session session = race.sessions.get(i);
            if (i > 0) row.append("     ");
            int start = row.length();
            row.append(session.shortName(activity.getResources())).append("  ").append(when.format(session.start).toUpperCase(Locale.getDefault()));
            int color;
            switch (s.statuses.get(i)) {
                case DONE: color = 0x47FFFFFF; break;
                case LIVE: color = RACE_RED; break;
                case NEXT: color = 0xFFFFFFFF; break;
                default: color = 0x99FFFFFF;
            }
            row.setSpan(new android.text.style.ForegroundColorSpan(color), start, row.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        raceSessions.setText(row);
        racePanel.setVisibility(View.VISIBLE);
    }
}
