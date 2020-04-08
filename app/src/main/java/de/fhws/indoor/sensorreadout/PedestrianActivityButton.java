package de.fhws.indoor.sensorreadout;

import android.content.Context;
import android.graphics.Color;
import androidx.annotation.DrawableRes;

import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import de.fhws.indoor.sensorreadout.R;
import de.fhws.indoor.sensorreadout.sensors.PedestrianActivity;

/**
 * Button used in the UI to represent a specific activity.
 * <p>
 *     These buttons are used by the user to live-tag the recording with information about the
 *     currently performed physical activity.
 * </p>
 *
 * @author Toni Fetzer
 * @author Markus Ebner
 */
public class PedestrianActivityButton extends LinearLayout {

    private LinearLayout innerBtn;
    private boolean isActive = false;
    private PedestrianActivity activity;

    public PedestrianActivityButton(Context context, PedestrianActivity activity, @DrawableRes int imageId) {
        super(context);
        inflate(getContext(), R.layout.pedestrian_activity_button, this);
        this.activity = activity;
        // setup ui
        ImageView imageView = (ImageView)this.findViewById(R.id.activityButtonImage);
        imageView.setImageResource(imageId);
        TextView textView = (TextView)this.findViewById(R.id.activityButtonText);
        textView.setText(activity.toString());
        innerBtn = (LinearLayout)getChildAt(0);
        setActivity(false);
    }

    public void setActivity(boolean active) {
        this.isActive = active;
        innerBtn.setBackgroundColor(Color.parseColor(
                (isActive) ? "#F9D737" : "#B2B2B2"
        ));
    }

    public PedestrianActivity getPedestrianActivity() { return this.activity; }
}
