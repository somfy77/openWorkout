/*
 * Copyright (C) 2020 olie.xdev <olie.xdev@googlemail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 */

package com.health.openworkout.gui.workout;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.preference.PreferenceManager;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.health.openworkout.R;
import com.health.openworkout.core.OpenWorkout;
import com.health.openworkout.core.datatypes.WorkoutItem;
import com.health.openworkout.core.datatypes.WorkoutSession;
import com.health.openworkout.gui.utils.SoundUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Calendar;

import timber.log.Timber;

public class WorkoutSlideFragment extends Fragment {
    private enum WORKOUT_STATE {INIT, PREPARE, START, BREAK, FINISH};
    private ConstraintLayout constraintLayout;
    private TextView nameView;
    private CardView videoCardView;
    private VideoView videoView;
    private ImageView videoImageView;
    private ImageView infoView;
    private TextView descriptionView;
    private TextView stateInfoView;
    private TextView stateInfoDetailView;
    private ImageView playResumeView;
    private ScrollView scrollView;
    private TableLayout workoutOverviewView;
    private TextView countdownView;
    private ProgressBar progressView;
    private FloatingActionButton nextWorkoutStepView;

    private CountDownTimer countDownTimer;
    private boolean isCountdownTimerStopped;
    private Calendar startTime;
    private int remainingSec;
    private int halftimeSec;
    private SoundUtils soundUtils;
    private boolean isSpeechCountdown;
    private boolean isSpeechWorkoutState;

    private WorkoutSession workoutSession;
    private long workoutItemOrderNr;
    private WorkoutItem nextWorkoutItem;
    private WORKOUT_STATE workoutState;
    private long workoutItemIdFromFragment;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             final ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_workoutslide, container, false);

        constraintLayout = root.findViewById(R.id.constraintLayout);
        nameView = root.findViewById(R.id.nameView);
        videoCardView = root.findViewById(R.id.videoCardView);
        videoView = root.findViewById(R.id.videoView);
        videoImageView = root.findViewById(R.id.videoImageView);
        infoView = root.findViewById(R.id.infoView);
        descriptionView = root.findViewById(R.id.descriptionView);
        stateInfoView = root.findViewById(R.id.stateInfoView);
        stateInfoDetailView = root.findViewById(R.id.stateInfoDetailView);
        playResumeView = root.findViewById(R.id.playPauseView);
        scrollView = root.findViewById(R.id.scrollView);
        workoutOverviewView = root.findViewById(R.id.workoutOverviewView);
        countdownView = root.findViewById(R.id.countdownView);
        progressView = root.findViewById(R.id.progressView);
        nextWorkoutStepView = root.findViewById(R.id.nextWorkoutStepView);

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
        isSpeechCountdown = sharedPreferences.getBoolean("speechCountdown", false);
        isSpeechWorkoutState = sharedPreferences.getBoolean("speechWorkoutState", false);

        int orientation = this.getResources().getConfiguration().orientation;
        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            ViewGroup.LayoutParams layoutParams = videoCardView.getLayoutParams();
            layoutParams.width = 0;
            layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            videoCardView.setLayoutParams(layoutParams);
        } else {
            ViewGroup.LayoutParams layoutParams = videoCardView.getLayoutParams();
            layoutParams.width = ViewGroup.LayoutParams.WRAP_CONTENT;
            layoutParams.height = 0;
            videoCardView.setLayoutParams(layoutParams);
        }

        videoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                mp.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING);
                mp.setLooping(true);
            }
        });

        infoView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (descriptionView.getVisibility() == View.GONE) {
                    descriptionView.setVisibility(View.VISIBLE);
                } else {
                    descriptionView.setVisibility(View.GONE);
                }
            }
        });

        nextWorkoutStepView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                nextWorkoutState();
            }
        });

        playResumeView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // if rep mode and in running workout state then ignore any clicks
                if (!nextWorkoutItem.isTimeMode() && workoutState == WORKOUT_STATE.START) {
                    return;
                }

                if (countDownTimer != null) {
                    if (isCountdownTimerStopped) {
                        resumeCountdownTimer();
                    } else {
                        pauseCountdownTimer();
                    }
                }
            }
        });

        soundUtils = OpenWorkout.getInstance().getSoundUtils();
        initWorkout();

        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
    }

    private void initWorkout() {
        isCountdownTimerStopped = false;
        long workoutSessionId = WorkoutSlideFragmentArgs.fromBundle(getArguments()).getSessionWorkoutId();
        workoutItemIdFromFragment = WorkoutSlideFragmentArgs.fromBundle(getArguments()).getWorkoutItemId();
        workoutSession = OpenWorkout.getInstance().getWorkoutSession(workoutSessionId);

        workoutState = WORKOUT_STATE.INIT;
        nextWorkoutState();
    }

    private void nextWorkoutState() {
        isCountdownTimerStopped = false;
        playResumeView.setImageResource(R.drawable.ic_play);
        soundUtils.flush();

        switch (workoutState) {
            case INIT:
                nextWorkout();
                prepareWorkout();
                break;
            case PREPARE:
                startWorkout();
                break;
            case START:
                onFinishWorkoutItem();
                breakWorkout();
                nextWorkout();
                showWorkoutOverview();
                break;
            case BREAK:
                prepareWorkout();
                break;
            case FINISH:
                break;
        }
    }

    private void nextWorkout() {
        // if no workout item was selected use the next not finished workout item in the session list
        if (workoutItemIdFromFragment == -1L) {
            // Get current orderNr before updating to nextWorkoutItem
            if (nextWorkoutItem == null) {
                workoutItemOrderNr = 0;
            } else {
                workoutItemOrderNr = nextWorkoutItem.getOrderNr();
            }
            if (workoutSession.getNextWorkoutItem(workoutItemOrderNr) == null) {
                onFinishSession();
                return;
            }

            nextWorkoutItem = workoutSession.getNextWorkoutItem(workoutItemOrderNr);
        } else {
            // otherwise use the workout item as a starting point which was selected in the workout fragment
            for (WorkoutItem workoutItem : workoutSession.getWorkoutItems()) {
                if (workoutItem.getWorkoutItemId() == workoutItemIdFromFragment) {
                    nextWorkoutItem = workoutItem;
                    workoutItemIdFromFragment = -1L;
                    break;
                }
            }
        }

        int workoutItemPos = workoutSession.getWorkoutItems().indexOf(nextWorkoutItem) + 1;
        startTime = Calendar.getInstance();

        nameView.setText(nextWorkoutItem.getName() + " (" + workoutItemPos + "/" + workoutSession.getWorkoutItems().size() + ")");

        if (isSpeechWorkoutState && workoutState != WORKOUT_STATE.INIT) {
            soundUtils.textToSpeech(getContext().getString(R.string.speak_next_workout) + " " + nextWorkoutItem.getName());
        }

        if (nextWorkoutItem.isVideoMode()) {
            videoView.setVisibility(View.VISIBLE);
            videoImageView.setVisibility(View.GONE);

            try {
                if (nextWorkoutItem.isVideoPathExternal()) {
                    videoView.setVideoURI(Uri.parse(nextWorkoutItem.getVideoPath()));
                } else {
                    if (OpenWorkout.getInstance().getCurrentUser().isMale()) {
                        videoView.setVideoPath("content://com.health.openworkout.videoprovider/video/male/" + nextWorkoutItem.getVideoPath());
                    } else {
                        videoView.setVideoPath("content://com.health.openworkout.videoprovider/video/female/" + nextWorkoutItem.getVideoPath());
                    }
                }
            } catch (SecurityException ex) {
                videoView.setVideoURI(null);
                Toast.makeText(getContext(), getContext().getString(R.string.error_no_access_to_file) + " " + nextWorkoutItem.getVideoPath(), Toast.LENGTH_SHORT).show();
                Timber.e(ex);
            }

            videoCardView.animate().alpha(1.0f);
            videoView.seekTo(100);
        } else {
            videoView.setVisibility(View.GONE);
            videoImageView.setVisibility(View.VISIBLE);

            try {
                if (nextWorkoutItem.isImagePathExternal()) {
                    videoImageView.setImageURI(Uri.parse(nextWorkoutItem.getImagePath()));
                } else {
                    String subFolder;
                    if (OpenWorkout.getInstance().getCurrentUser().isMale()) {
                        subFolder = "male";
                    } else {
                        subFolder = "female";
                    }

                    InputStream ims = getContext().getAssets().open("image/" + subFolder + "/" + nextWorkoutItem.getImagePath());
                    videoImageView.setImageDrawable(Drawable.createFromStream(ims, null));

                    ims.close();
                }
            } catch (IOException ex) {
                Timber.e(ex);
            } catch (SecurityException ex) {
                videoImageView.setImageResource(R.drawable.ic_no_file);
                Toast.makeText(getContext(), getContext().getString(R.string.error_no_access_to_file) + " " + nextWorkoutItem.getImagePath(), Toast.LENGTH_SHORT).show();
                Timber.e(ex);
            }
        }

        descriptionView.setText(nextWorkoutItem.getDescription());
    }

    private void prepareWorkout() {
        workoutState = WORKOUT_STATE.PREPARE;
        hideWorkoutOverview();

        stateInfoView.setText(R.string.label_prepare);
        stateInfoView.setTextColor(getContext().getResources().getColor(R.color.colorRed));
            if (nextWorkoutItem.isTimeMode()) {
                stateInfoDetailView.setText(nextWorkoutItem.getName() + " (" + nextWorkoutItem.getWorkoutTime() + getContext().getString(R.string.seconds_unit) + ")");
            } else {
                stateInfoDetailView.setText(nextWorkoutItem.getName() + " (" + Integer.toString(nextWorkoutItem.getRepetitionCount()) + "x" + ")");
            }
        stateInfoDetailView.setTextColor(getContext().getResources().getColor(R.color.colorRed));
        countdownView.setTextColor(getContext().getResources().getColor(R.color.colorRed));
        progressView.setProgressTintList(ColorStateList.valueOf(getContext().getResources().getColor(R.color.colorRed)));
        nextWorkoutStepView.setBackgroundTintList(ColorStateList.valueOf(getContext().getResources().getColor(R.color.colorRed)));
        playResumeView.setColorFilter(getContext().getResources().getColor(R.color.colorRed));

        if (isSpeechWorkoutState) {
            soundUtils.textToSpeech(getContext().getString(R.string.label_prepare) + " " + nextWorkoutItem.getName());
        }

        prepareCountdownTimer(nextWorkoutItem.getPrepTime());
        activateCountdownTimer(nextWorkoutItem.getPrepTime());
    }

    private void startWorkout() {
        workoutState = WORKOUT_STATE.START;

        stateInfoView.setText(R.string.label_workout);
        stateInfoView.setTextColor(getContext().getResources().getColor(R.color.colorLightBlue));
        stateInfoDetailView.setTextColor(getContext().getResources().getColor(R.color.colorLightBlue));
        countdownView.setTextColor(getContext().getResources().getColor(R.color.colorLightBlue));
        progressView.setProgressTintList(ColorStateList.valueOf(getContext().getResources().getColor(R.color.colorLightBlue)));
        nextWorkoutStepView.setBackgroundTintList(ColorStateList.valueOf(getContext().getResources().getColor(R.color.colorLightBlue)));
        playResumeView.setColorFilter(getContext().getResources().getColor(R.color.colorLightBlue));

        videoView.start();

        if (isSpeechWorkoutState) {
            soundUtils.textToSpeech(getContext().getString(R.string.label_workout) + " " + nextWorkoutItem.getName());
        }

        if (nextWorkoutItem.isTimeMode()) {
            prepareCountdownTimer(nextWorkoutItem.getWorkoutTime());
            activateCountdownTimer(nextWorkoutItem.getWorkoutTime());
        } else {
            pauseCountdownTimer();
            countdownView.setText("");
            //countdownView.setText(String.format(getString(R.string.label_repetition_info), nextWorkoutItem.getRepetitionCount(), nextWorkoutItem.getName()));
            progressView.setVisibility(View.INVISIBLE);
        }
    }

    private void breakWorkout() {
        workoutState = WORKOUT_STATE.BREAK;

        stateInfoView.setText("");
        stateInfoView.setTextColor(getContext().getResources().getColor(R.color.colorGreen));
        stateInfoView.setTextColor(getContext().getResources().getColor(R.color.colorGreen));
        stateInfoDetailView.setText(R.string.label_break);
        stateInfoDetailView.setTextColor(getContext().getResources().getColor(R.color.colorGreen));
        countdownView.setTextColor(getContext().getResources().getColor(R.color.colorGreen));
        progressView.setProgressTintList(ColorStateList.valueOf(getContext().getResources().getColor(R.color.colorGreen)));
        nextWorkoutStepView.setBackgroundTintList(ColorStateList.valueOf(getContext().getResources().getColor(R.color.colorGreen)));
        playResumeView.setColorFilter(getContext().getResources().getColor(R.color.colorGreen));

        if (isSpeechWorkoutState) {
            soundUtils.textToSpeech(getContext().getString(R.string.label_break));
        }

        prepareCountdownTimer(nextWorkoutItem.getBreakTime());
        activateCountdownTimer(nextWorkoutItem.getBreakTime());
    }

    private void onFinishWorkoutItem() {
        Calendar stopTime = Calendar.getInstance();

        long diffTimeInSec = (stopTime.getTimeInMillis() - startTime.getTimeInMillis()) / 1000L;
        nextWorkoutItem.setElapsedTime(diffTimeInSec);

        nextWorkoutItem.setFinished(true);
        OpenWorkout.getInstance().updateWorkoutItem(nextWorkoutItem);
    }

    private void onFinishSession() {
        workoutSession.setFinished(true);
        OpenWorkout.getInstance().updateWorkoutSession(workoutSession);

        WorkoutSlideFragmentDirections.ActionNavWorkoutSlideFragmentToTrophyFragment action = WorkoutSlideFragmentDirections.actionNavWorkoutSlideFragmentToTrophyFragment();
        action.setSessionWorkoutId(workoutSession.getWorkoutSessionId());
        Navigation.findNavController(getActivity(), R.id.nav_host_fragment).navigate(action);
    }


    private void showWorkoutOverview() {
        descriptionView.setVisibility(View.GONE);
        workoutOverviewView.setVisibility(View.VISIBLE);

        workoutOverviewView.removeAllViews();

        for (WorkoutItem workoutItem : workoutSession.getWorkoutItems()) {
            final OverviewWorkoutItemEntry overviewWorkoutItemEntry = new OverviewWorkoutItemEntry(getContext(), workoutItem);
            workoutOverviewView.addView(overviewWorkoutItemEntry);

            if (workoutItem.getWorkoutItemId() == nextWorkoutItem.getWorkoutItemId()) {
                overviewWorkoutItemEntry.setHighlight();
                new Handler().post(new Runnable() {
                    @Override
                    public void run() {
                        scrollView.smoothScrollTo(0, overviewWorkoutItemEntry.getTop()-50);
                    }
                });
            }
        }
    }

    private void hideWorkoutOverview() {
        descriptionView.setVisibility(View.GONE);
        workoutOverviewView.setVisibility(View.GONE);
    }

    private class OverviewWorkoutItemEntry extends TableRow {
        private ImageView status;
        private TextView reps;
        private TextView name;

        public OverviewWorkoutItemEntry(Context context, WorkoutItem workoutItem) {
            super(context);

            status = new ImageView(context);
            reps = new TextView(context);
            name = new TextView(context);

            name.setText(workoutItem.getName());

            if (workoutItem.isTimeMode()) {
                reps.setText(workoutItem.getWorkoutTime() + context.getString(R.string.seconds_unit));
            } else {
                reps.setText(Integer.toString(workoutItem.getRepetitionCount()) + "x");
            }
            status.setPadding(0, 0, 20, 0);
            reps.setPadding(0, 0, 20, 0);

            if (workoutItem.isFinished()) {
                status.setImageResource(R.drawable.ic_workout_done);
            }

            addView(status);
            addView(reps);
            addView(name);

            setPadding(10, 10, 10, 10);
        }

        public void setHighlight() {
            name.setTypeface(null, Typeface.BOLD);
            status.setImageResource(R.drawable.ic_workout_select);
        }
    }

    private void prepareCountdownTimer(int sec) {
        remainingSec = sec;
        progressView.setMax(remainingSec);
        progressView.setProgress(remainingSec);
        progressView.setVisibility(View.VISIBLE);
        countdownView.setText(remainingSec + getString(R.string.seconds_unit));

        halftimeSec = remainingSec / 2;
    }

    private void pauseCountdownTimer() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }

        isCountdownTimerStopped = true;
        playResumeView.setImageResource(R.drawable.ic_pause);
    }

    private void resumeCountdownTimer() {
        isCountdownTimerStopped = false;
        activateCountdownTimer(remainingSec);
        playResumeView.setImageResource(R.drawable.ic_play);
    }

    private void activateCountdownTimer(int sec) {
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }

        countDownTimer = new CountDownTimer(remainingSec * 1000, 1000) {

            public void onTick(long millisUntilFinished) {
                remainingSec = (int)(millisUntilFinished / 1000);
                countdownView.setText(remainingSec + getString(R.string.seconds_unit));
                progressView.setProgress(remainingSec);

                switch (workoutState) {
                    case PREPARE:
                        if (isSpeechCountdown) {
                            switch (remainingSec) {
                                case 5:
                                    soundUtils.textToSpeechNoInterrupt(getContext().getString(R.string.speak_five));
                                    break;
                                case 4:
                                    soundUtils.textToSpeechNoInterrupt(getContext().getString(R.string.speak_four));
                                    break;
                                case 3:
                                    soundUtils.textToSpeechNoInterrupt(getContext().getString(R.string.speak_three));
                                    break;
                                case 2:
                                    soundUtils.textToSpeechNoInterrupt(getContext().getString(R.string.speak_two));
                                    break;
                                case 1:
                                    soundUtils.textToSpeechNoInterrupt(getContext().getString(R.string.speak_one));
                                    break;
                            }
                        } else {
                            if ((remainingSec == 3) || (remainingSec == 2) || (remainingSec == 1)) {
                                soundUtils.playSound(SoundUtils.SOUND.WORKOUT_COUNT_BEFORE_START);
                            }
                        }
                        break;
                    case START:
                        if (remainingSec == halftimeSec) {
                            soundUtils.textToSpeech(getContext().getString(R.string.speak_halftime));
                        } else if (isSpeechCountdown) {
                                switch (remainingSec) {
                                    case 5:
                                        soundUtils.textToSpeechNoInterrupt(getContext().getString(R.string.speak_five));
                                        break;
                                    case 4:
                                        soundUtils.textToSpeechNoInterrupt(getContext().getString(R.string.speak_four));
                                        break;
                                    case 3:
                                        soundUtils.textToSpeechNoInterrupt(getContext().getString(R.string.speak_three));
                                        break;
                                    case 2:
                                        soundUtils.textToSpeechNoInterrupt(getContext().getString(R.string.speak_two));
                                        break;
                                    case 1:
                                        soundUtils.textToSpeechNoInterrupt(getContext().getString(R.string.speak_one));
                                        break;
                                }
                            } else {
                                if ((remainingSec == 3) || (remainingSec == 2) || (remainingSec == 1))
                                    soundUtils.playSound(SoundUtils.SOUND.WORKOUT_COUNT_BEFORE_START);
                            }
                        break;
                }
            }

            public void onFinish() {
                switch (workoutState) {
                    case PREPARE:
                        soundUtils.playSound(SoundUtils.SOUND.WORKOUT_START);
                        break;
                    case START:
                        soundUtils.playSound(SoundUtils.SOUND.WORKOUT_STOP);
                        break;
                }

                nextWorkoutState();
            }
        };

        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                countDownTimer.start();
            }
        });
    }
}
