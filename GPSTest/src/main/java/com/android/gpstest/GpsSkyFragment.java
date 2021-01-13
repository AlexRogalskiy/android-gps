/*
 * Copyright (C) 2008-2013 The Android Open Source Project,
 * Sean J. Barbeau
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.gpstest;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.location.GnssMeasurementsEvent;
import android.location.GnssStatus;
import android.location.GpsStatus;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.Transformation;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;

import com.android.gpstest.util.MathUtils;
import com.android.gpstest.util.UIUtils;
import com.android.gpstest.view.GpsSkyView;

import java.util.LinkedList;
import java.util.List;

public class GpsSkyFragment extends Fragment implements GpsTestListener {

    public final static String TAG = "GpsSkyFragment";

    private GpsSkyView mSkyView;

    private List<View> mLegendLines;

    private List<ImageView> mLegendShapes;

    private TextView mLegendCn0Title, mLegendCn0Units, mLegendCn0LeftText, mLegendCn0LeftCenterText,
            mLegendCn0CenterText, mLegendCn0RightCenterText, mLegendCn0RightText, mSnrCn0InViewAvgText, mSnrCn0UsedAvgText;

    private ImageView mSnrCn0InViewAvg, mSnrCn0UsedAvg, lock, circleUsedInFix;

    Animation mSnrCn0InViewAvgAnimation, mSnrCn0UsedAvgAnimation, mSnrCn0InViewAvgAnimationTextView, mSnrCn0UsedAvgAnimationTextView;

    private boolean mUseLegacyGnssApi = false;

    // Default light theme values
    int usedCn0Background = R.drawable.cn0_round_corner_background_used;
    int usedCn0IndicatorColor = Color.BLACK;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.gps_sky, container,false);

        mSkyView = v.findViewById(R.id.sky_view);

        initLegendViews(v);

        mSnrCn0InViewAvg = v.findViewById(R.id.cn0_indicator_in_view);
        mSnrCn0UsedAvg = v.findViewById(R.id.cn0_indicator_used);
        lock = v.findViewById(R.id.sky_lock);
        circleUsedInFix = v.findViewById(R.id.sky_legend_used_in_fix);

        GpsTestActivity.getInstance().addListener(this);
        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        int color;
        if (Application.getPrefs().getBoolean(getString(R.string.pref_key_dark_theme), false)) {
            // Dark theme
            color = getResources().getColor(android.R.color.secondary_text_dark);
            circleUsedInFix.setImageResource(R.drawable.circle_used_in_fix_dark);
            usedCn0Background = R.drawable.cn0_round_corner_background_used_dark;
            usedCn0IndicatorColor = getResources().getColor(android.R.color.darker_gray);;
        } else {
            // Light theme
            color = getResources().getColor(R.color.body_text_2_light);
            circleUsedInFix.setImageResource(R.drawable.circle_used_in_fix);
            usedCn0Background =  R.drawable.cn0_round_corner_background_used;
            usedCn0IndicatorColor = Color.BLACK;
        }
        for (View v : mLegendLines) {
            v.setBackgroundColor(color);
        }
        for (ImageView v : mLegendShapes) {
            v.setColorFilter(color);
        }
    }

    public void onLocationChanged(Location loc) {
    }

    public void onStatusChanged(String provider, int status, Bundle extras) {
    }

    public void onProviderEnabled(String provider) {
    }

    public void onProviderDisabled(String provider) {
    }

    public void gpsStart() {
    }

    public void gpsStop() {
    }

    @Override
    public void onGnssFirstFix(int ttffMillis) {

    }

    @Override
    public void onGnssFixAcquired() {
        showHaveFix();
    }

    @Override
    public void onGnssFixLost() {
        showLostFix();
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    public void onSatelliteStatusChanged(GnssStatus status) {
        mSkyView.setGnssStatus(status);
        mUseLegacyGnssApi = false;
        updateSnrCn0AvgMeterText();
        updateSnrCn0Avgs();
    }

    @Override
    public void onGnssStarted() {
        mSkyView.setStarted();
    }

    @Override
    public void onGnssStopped() {
        mSkyView.setStopped();
        if (lock != null) {
            lock.setVisibility(View.GONE);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    public void onGnssMeasurementsReceived(GnssMeasurementsEvent event) {
        mSkyView.setGnssMeasurementEvent(event);
    }

    @Deprecated
    public void onGpsStatusChanged(int event, GpsStatus status) {
        switch (event) {
            case GpsStatus.GPS_EVENT_STARTED:
                mSkyView.setStarted();
                break;

            case GpsStatus.GPS_EVENT_STOPPED:
                mSkyView.setStopped();
                break;

            case GpsStatus.GPS_EVENT_SATELLITE_STATUS:
                mSkyView.setSats(status);
                mUseLegacyGnssApi = true;
                updateSnrCn0AvgMeterText();
                updateSnrCn0Avgs();
                break;
        }
    }

    @Override
    public void onOrientationChanged(double orientation, double tilt) {
        // For performance reasons, only proceed if this fragment is visible
        if (!getUserVisibleHint()) {
            return;
        }

        if (mSkyView != null) {
            mSkyView.onOrientationChanged(orientation, tilt);
        }
    }

    @Override
    public void onNmeaMessage(String message, long timestamp) {
    }

    /**
     * Initialize the views in the C/N0 and Shape legends
     * @param v view in which the legend view IDs can be found via view.findViewById()
     */
    private void initLegendViews(View v) {
        if (mLegendLines == null) {
            mLegendLines = new LinkedList<>();
        } else {
            mLegendLines.clear();
        }

        if (mLegendShapes == null) {
            mLegendShapes = new LinkedList<>();
        } else {
            mLegendShapes.clear();
        }

        // Avg C/N0 indicator lines
        mLegendLines.add(v.findViewById(R.id.sky_legend_cn0_left_line4));
        mLegendLines.add(v.findViewById(R.id.sky_legend_cn0_left_line3));
        mLegendLines.add(v.findViewById(R.id.sky_legend_cn0_left_line2));
        mLegendLines.add(v.findViewById(R.id.sky_legend_cn0_left_line1));
        mLegendLines.add(v.findViewById(R.id.sky_legend_cn0_center_line));
        mLegendLines.add(v.findViewById(R.id.sky_legend_cn0_right_line1));
        mLegendLines.add(v.findViewById(R.id.sky_legend_cn0_right_line2));
        mLegendLines.add(v.findViewById(R.id.sky_legend_cn0_right_line3));
        mLegendLines.add(v.findViewById(R.id.sky_legend_cn0_right_line4));

        // Shape Legend lines
        mLegendLines.add(v.findViewById(R.id.sky_legend_shape_line1a));
        mLegendLines.add(v.findViewById(R.id.sky_legend_shape_line1b));
        mLegendLines.add(v.findViewById(R.id.sky_legend_shape_line2a));
        mLegendLines.add(v.findViewById(R.id.sky_legend_shape_line2b));
        mLegendLines.add(v.findViewById(R.id.sky_legend_shape_line3a));
        mLegendLines.add(v.findViewById(R.id.sky_legend_shape_line3b));
        mLegendLines.add(v.findViewById(R.id.sky_legend_shape_line4a));
        mLegendLines.add(v.findViewById(R.id.sky_legend_shape_line4b));
        mLegendLines.add(v.findViewById(R.id.sky_legend_shape_line5a));
        mLegendLines.add(v.findViewById(R.id.sky_legend_shape_line5b));
        mLegendLines.add(v.findViewById(R.id.sky_legend_shape_line6a));
        mLegendLines.add(v.findViewById(R.id.sky_legend_shape_line6b));
        mLegendLines.add(v.findViewById(R.id.sky_legend_shape_line7a));
        mLegendLines.add(v.findViewById(R.id.sky_legend_shape_line7b));
        mLegendLines.add(v.findViewById(R.id.sky_legend_shape_line8a));
        mLegendLines.add(v.findViewById(R.id.sky_legend_shape_line8b));
        mLegendLines.add(v.findViewById(R.id.sky_legend_shape_line9a));
        mLegendLines.add(v.findViewById(R.id.sky_legend_shape_line9b));
        mLegendLines.add(v.findViewById(R.id.sky_legend_shape_line10a));
        mLegendLines.add(v.findViewById(R.id.sky_legend_shape_line10b));
        mLegendLines.add(v.findViewById(R.id.sky_legend_shape_line11a));
        mLegendLines.add(v.findViewById(R.id.sky_legend_shape_line12a));
        mLegendLines.add(v.findViewById(R.id.sky_legend_shape_line13a));
        mLegendLines.add(v.findViewById(R.id.sky_legend_shape_line14a));
        mLegendLines.add(v.findViewById(R.id.sky_legend_shape_line14b));
        mLegendLines.add(v.findViewById(R.id.sky_legend_shape_line15a));
        mLegendLines.add(v.findViewById(R.id.sky_legend_shape_line15b));
        mLegendLines.add(v.findViewById(R.id.sky_legend_shape_line16a));
        mLegendLines.add(v.findViewById(R.id.sky_legend_shape_line16b));

        // Shape Legend shapes
        mLegendShapes.add((ImageView) v.findViewById(R.id.sky_legend_circle));
        mLegendShapes.add((ImageView) v.findViewById(R.id.sky_legend_square));
        mLegendShapes.add((ImageView) v.findViewById(R.id.sky_legend_pentagon));
        mLegendShapes.add((ImageView) v.findViewById(R.id.sky_legend_triangle));
        mLegendShapes.add((ImageView) v.findViewById(R.id.sky_legend_hexagon1));
        mLegendShapes.add((ImageView) v.findViewById(R.id.sky_legend_oval));
        mLegendShapes.add((ImageView) v.findViewById(R.id.sky_legend_diamond1));
        mLegendShapes.add((ImageView) v.findViewById(R.id.sky_legend_diamond2));
        mLegendShapes.add((ImageView) v.findViewById(R.id.sky_legend_diamond3));
        mLegendShapes.add((ImageView) v.findViewById(R.id.sky_legend_diamond4));
        mLegendShapes.add((ImageView) v.findViewById(R.id.sky_legend_diamond5));
        mLegendShapes.add((ImageView) v.findViewById(R.id.sky_legend_diamond6));
        mLegendShapes.add((ImageView) v.findViewById(R.id.sky_legend_diamond7));

        // C/N0 Legend text
        mLegendCn0Title = v.findViewById(R.id.sky_legend_cn0_title);
        mLegendCn0Units = v.findViewById(R.id.sky_legend_cn0_units);
        mLegendCn0LeftText = v.findViewById(R.id.sky_legend_cn0_left_text);
        mLegendCn0LeftCenterText = v.findViewById(R.id.sky_legend_cn0_left_center_text);
        mLegendCn0CenterText = v.findViewById(R.id.sky_legend_cn0_center_text);
        mLegendCn0RightCenterText = v.findViewById(R.id.sky_legend_cn0_right_center_text);
        mLegendCn0RightText = v.findViewById(R.id.sky_legend_cn0_right_text);
        mSnrCn0InViewAvgText = v.findViewById(R.id.cn0_text_in_view);
        mSnrCn0UsedAvgText = v.findViewById(R.id.cn0_text_used);
    }

    private void updateSnrCn0AvgMeterText() {
        if (!mUseLegacyGnssApi || (mSkyView != null && mSkyView.isSnrBad())) {
            // C/N0
            mLegendCn0Title.setText(R.string.gps_cn0_column_label);
            mLegendCn0Units.setText(R.string.sky_legend_cn0_units);
            mLegendCn0LeftText.setText(R.string.sky_legend_cn0_low);
            mLegendCn0LeftCenterText.setText(R.string.sky_legend_cn0_low_middle);
            mLegendCn0CenterText.setText(R.string.sky_legend_cn0_middle);
            mLegendCn0RightCenterText.setText(R.string.sky_legend_cn0_middle_high);
            mLegendCn0RightText.setText(R.string.sky_legend_cn0_high);
        } else {
            // SNR for Android 6.0 and lower (or if user unchecked "Use GNSS APIs" setting and values conform to SNR range)
            mLegendCn0Title.setText(R.string.gps_snr_column_label);
            mLegendCn0Units.setText(R.string.sky_legend_snr_units);
            mLegendCn0LeftText.setText(R.string.sky_legend_snr_low);
            mLegendCn0LeftCenterText.setText(R.string.sky_legend_snr_low_middle);
            mLegendCn0CenterText.setText(R.string.sky_legend_snr_middle);
            mLegendCn0RightCenterText.setText(R.string.sky_legend_snr_middle_high);
            mLegendCn0RightText.setText(R.string.sky_legend_snr_high);
        }
    }

    private void updateSnrCn0Avgs() {
        if (mSkyView == null) {
            return;
        }
        // Based on the avg SNR or C/N0 for "in view" and "used" satellites the left margins need to be adjusted accordingly
        int meterWidthPx = (int) Application.get().getResources().getDimension(R.dimen.cn0_meter_width)
                - UIUtils.dpToPixels(Application.get(), 7.0f); // Reduce width for padding
        int minIndicatorMarginPx = (int) Application.get().getResources().getDimension(R.dimen.cn0_indicator_min_left_margin);
        int maxIndicatorMarginPx = meterWidthPx + minIndicatorMarginPx;
        int minTextViewMarginPx = (int) Application.get().getResources().getDimension(R.dimen.cn0_textview_min_left_margin);
        int maxTextViewMarginPx = meterWidthPx + minTextViewMarginPx;

        // When both "in view" and "used" indicators and TextViews are shown, slide the "in view" TextView by this amount to the left to avoid overlap
        float TEXTVIEW_NON_OVERLAP_OFFSET_DP = -16.0f;

        // Calculate normal offsets for avg in view satellite SNR or C/N0 value TextViews
        Integer leftInViewTextViewMarginPx = null;
        if (MathUtils.isValidFloat(mSkyView.getSnrCn0InViewAvg())) {
            if (!mSkyView.isUsingLegacyGpsApi() || mSkyView.isSnrBad()) {
                // C/N0
                leftInViewTextViewMarginPx = UIUtils.cn0ToTextViewLeftMarginPx(mSkyView.getSnrCn0InViewAvg(),
                        minTextViewMarginPx, maxTextViewMarginPx);
            } else {
                // SNR
                leftInViewTextViewMarginPx = UIUtils.snrToTextViewLeftMarginPx(mSkyView.getSnrCn0InViewAvg(),
                        minTextViewMarginPx, maxTextViewMarginPx);
            }
        }

        // Calculate normal offsets for avg used satellite C/N0 value TextViews
        Integer leftUsedTextViewMarginPx = null;
        if (MathUtils.isValidFloat(mSkyView.getSnrCn0UsedAvg())) {
            if (!mSkyView.isUsingLegacyGpsApi() || mSkyView.isSnrBad()) {
                // C/N0
                leftUsedTextViewMarginPx = UIUtils.cn0ToTextViewLeftMarginPx(mSkyView.getSnrCn0UsedAvg(),
                        minTextViewMarginPx, maxTextViewMarginPx);
            } else {
                // SNR
                leftUsedTextViewMarginPx = UIUtils.snrToTextViewLeftMarginPx(mSkyView.getSnrCn0UsedAvg(),
                        minTextViewMarginPx, maxTextViewMarginPx);
            }
        }

        // See if we need to apply the offset margin to try and keep the two TextViews from overlapping by shifting one of the two left
        if (leftInViewTextViewMarginPx != null && leftUsedTextViewMarginPx != null) {
            int offset = UIUtils.dpToPixels(Application.get(), TEXTVIEW_NON_OVERLAP_OFFSET_DP);
            if (leftInViewTextViewMarginPx <= leftUsedTextViewMarginPx) {
                leftInViewTextViewMarginPx += offset;
            } else {
                leftUsedTextViewMarginPx += offset;
            }
        }

        // Define paddings used for TextViews
        int pSides = UIUtils.dpToPixels(Application.get(), 7);
        int pTopBottom = UIUtils.dpToPixels(Application.get(), 4);

        // Set avg SNR or C/N0 of satellites in view of device
        if (MathUtils.isValidFloat(mSkyView.getSnrCn0InViewAvg())) {
            mSnrCn0InViewAvgText.setText(String.format("%.1f", mSkyView.getSnrCn0InViewAvg()));

            // Set color of TextView
            int color = mSkyView.getSatelliteColor(mSkyView.getSnrCn0InViewAvg());
            LayerDrawable background = (LayerDrawable) ContextCompat.getDrawable(Application.get(), R.drawable.cn0_round_corner_background_in_view);

            // Fill
            GradientDrawable backgroundGradient = (GradientDrawable) background.findDrawableByLayerId(R.id.cn0_avg_in_view_fill);
            backgroundGradient.setColor(color);

            // Stroke
            GradientDrawable borderGradient = (GradientDrawable) background.findDrawableByLayerId(R.id.cn0_avg_in_view_border);
            borderGradient.setColor(color);

            mSnrCn0InViewAvgText.setBackground(background);

            // Set padding
            mSnrCn0InViewAvgText.setPadding(pSides, pTopBottom, pSides, pTopBottom);

            // Set color of indicator
            mSnrCn0InViewAvg.setColorFilter(color);

            // Set position and visibility of TextView
            if (mSnrCn0InViewAvgText.getVisibility() == View.VISIBLE) {
                animateSnrCn0Indicator(mSnrCn0InViewAvgText, leftInViewTextViewMarginPx, mSnrCn0InViewAvgAnimationTextView);
            } else {
                RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) mSnrCn0InViewAvgText.getLayoutParams();
                lp.setMargins(leftInViewTextViewMarginPx, lp.topMargin, lp.rightMargin, lp.bottomMargin);
                mSnrCn0InViewAvgText.setLayoutParams(lp);
                mSnrCn0InViewAvgText.setVisibility(View.VISIBLE);
            }

            // Set position and visibility of indicator
            int leftIndicatorMarginPx;
            if (!mSkyView.isUsingLegacyGpsApi() || mSkyView.isSnrBad()) {
                // C/N0
                leftIndicatorMarginPx = UIUtils.cn0ToIndicatorLeftMarginPx(mSkyView.getSnrCn0InViewAvg(),
                        minIndicatorMarginPx, maxIndicatorMarginPx);
            } else {
                // SNR
                leftIndicatorMarginPx = UIUtils.snrToIndicatorLeftMarginPx(mSkyView.getSnrCn0InViewAvg(),
                        minIndicatorMarginPx, maxIndicatorMarginPx);
            }

            // If the view is already visible, animate to the new position.  Otherwise just set the position and make it visible
            if (mSnrCn0InViewAvg.getVisibility() == View.VISIBLE) {
                animateSnrCn0Indicator(mSnrCn0InViewAvg, leftIndicatorMarginPx, mSnrCn0InViewAvgAnimation);
            } else {
                RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) mSnrCn0InViewAvg.getLayoutParams();
                lp.setMargins(leftIndicatorMarginPx, lp.topMargin, lp.rightMargin, lp.bottomMargin);
                mSnrCn0InViewAvg.setLayoutParams(lp);
                mSnrCn0InViewAvg.setVisibility(View.VISIBLE);
            }
        } else {
            mSnrCn0InViewAvgText.setText("");
            mSnrCn0InViewAvgText.setVisibility(View.INVISIBLE);
            mSnrCn0InViewAvg.setVisibility(View.INVISIBLE);
        }

        // Set avg SNR or C/N0 of satellites used in fix
        if (MathUtils.isValidFloat(mSkyView.getSnrCn0UsedAvg())) {
            mSnrCn0UsedAvgText.setText(String.format("%.1f", mSkyView.getSnrCn0UsedAvg()));
            // Set color of TextView
            int color = mSkyView.getSatelliteColor(mSkyView.getSnrCn0UsedAvg());
            LayerDrawable background = (LayerDrawable) ContextCompat.getDrawable(Application.get(), usedCn0Background);

            // Fill
            GradientDrawable backgroundGradient = (GradientDrawable) background.findDrawableByLayerId(R.id.cn0_avg_used_fill);
            backgroundGradient.setColor(color);

            mSnrCn0UsedAvgText.setBackground(background);

            // Set padding
            mSnrCn0UsedAvgText.setPadding(pSides, pTopBottom, pSides, pTopBottom);

            // Set color of indicator
            mSnrCn0UsedAvg.setColorFilter(usedCn0IndicatorColor);

            // Set position and visibility of TextView
            if (mSnrCn0UsedAvgText.getVisibility() == View.VISIBLE) {
                animateSnrCn0Indicator(mSnrCn0UsedAvgText, leftUsedTextViewMarginPx, mSnrCn0UsedAvgAnimationTextView);
            } else {
                RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) mSnrCn0UsedAvgText.getLayoutParams();
                lp.setMargins(leftUsedTextViewMarginPx, lp.topMargin, lp.rightMargin, lp.bottomMargin);
                mSnrCn0UsedAvgText.setLayoutParams(lp);
                mSnrCn0UsedAvgText.setVisibility(View.VISIBLE);
            }

            // Set position and visibility of indicator
            int leftMarginPx;
            if (!mSkyView.isUsingLegacyGpsApi() || mSkyView.isSnrBad()) {
                // C/N0
                leftMarginPx = UIUtils.cn0ToIndicatorLeftMarginPx(mSkyView.getSnrCn0UsedAvg(),
                        minIndicatorMarginPx, maxIndicatorMarginPx);
            } else {
                // SNR
                leftMarginPx = UIUtils.snrToIndicatorLeftMarginPx(mSkyView.getSnrCn0UsedAvg(),
                        minIndicatorMarginPx, maxIndicatorMarginPx);
            }

            // If the view is already visible, animate to the new position.  Otherwise just set the position and make it visible
            if (mSnrCn0UsedAvg.getVisibility() == View.VISIBLE) {
                animateSnrCn0Indicator(mSnrCn0UsedAvg, leftMarginPx, mSnrCn0UsedAvgAnimation);
            } else {
                RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) mSnrCn0UsedAvg.getLayoutParams();
                lp.setMargins(leftMarginPx, lp.topMargin, lp.rightMargin, lp.bottomMargin);
                mSnrCn0UsedAvg.setLayoutParams(lp);
                mSnrCn0UsedAvg.setVisibility(View.VISIBLE);
            }
        } else {
            mSnrCn0UsedAvgText.setText("");
            mSnrCn0UsedAvgText.setVisibility(View.INVISIBLE);
            mSnrCn0UsedAvg.setVisibility(View.INVISIBLE);
        }
    }

    /**
     * Animates a SNR or C/N0 indicator view from it's current location to the provided left margin location (in pixels)
     * @param v view to animate
     * @param goalLeftMarginPx the new left margin for the view that the view should animate to in pixels
     * @param animation Animation to use for the animation
     */
    private void animateSnrCn0Indicator(final View v, final int goalLeftMarginPx, Animation animation) {
        if (v == null) {
            return;
        }

        if (animation != null) {
            animation.reset();
        }

        final ViewGroup.MarginLayoutParams p = (ViewGroup.MarginLayoutParams) v.getLayoutParams();

        final int currentMargin = p.leftMargin;

        animation = new Animation() {
            @Override
            protected void applyTransformation(float interpolatedTime, Transformation t) {
                int newLeft;
                if (goalLeftMarginPx > currentMargin) {
                    newLeft = currentMargin + (int) (Math.abs(currentMargin - goalLeftMarginPx)
                            * interpolatedTime);
                } else {
                    newLeft = currentMargin - (int) (Math.abs(currentMargin - goalLeftMarginPx)
                            * interpolatedTime);
                }
                UIUtils.setMargins(v,
                        newLeft,
                        p.topMargin,
                        p.rightMargin,
                        p.bottomMargin);
            }
        };
        // C/N0 updates every second, so animation of 300ms (https://material.io/guidelines/motion/duration-easing.html#duration-easing-common-durations)
        // wit FastOutSlowInInterpolator recommended by Material Design spec easily finishes in time for next C/N0 update
        animation.setDuration(300);
        animation.setInterpolator(new FastOutSlowInInterpolator());
        v.startAnimation(animation);
    }

    private void showHaveFix() {
        if (lock != null) {
            UIUtils.showViewWithAnimation(lock, UIUtils.ANIMATION_DURATION_SHORT_MS);
        }
    }

    private void showLostFix() {
        if (lock != null) {
            UIUtils.hideViewWithAnimation(lock, UIUtils.ANIMATION_DURATION_SHORT_MS);
        }
    }
}
