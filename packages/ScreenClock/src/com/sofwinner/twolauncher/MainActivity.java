package com.sofwinner.twolauncher;

/*
 * Copyright (C) 2012 The Android Open Source Project
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

// Need the following import to get access to the app resources, since this
// class is in a sub-package.
//import com.sofwinner.twolauncher.R;

import com.sofwinner.twolauncher.widget.LEDView;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Presentation;
import android.app.Service;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Point;
import android.graphics.drawable.GradientDrawable;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.Parcelable.Creator;
import android.util.Log;
import android.util.SparseArray;
import android.view.Display;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import java.lang.ref.WeakReference;

//BEGIN_INCLUDE(activity)
/**
 * <h3>Presentation Activity</h3>
 *
 * <p>
 * This demonstrates how to create an activity that shows some content
 * on a secondary display using a {@link Presentation}.
 * </p><p>
 * The activity uses the {@link DisplayManager} API to enumerate displays.
 * When the user selects a display, the activity opens a {@link Presentation}
 * on that display.  We show a different photograph in each presentation
 * on a unique background along with a label describing the display.
 * We also write information about displays and display-related events to
 * the Android log which you can read using <code>adb logcat</code>.
 * </p><p>
 * You can try this out using an HDMI or Wifi display or by using the
 * "Simulate secondary displays" feature in Development Settings to create a few
 * simulated secondary displays.  Each display will appear in the list along with a
 * checkbox to show a presentation on that display.
 * </p><p>
 * See also the {@link PresentationWithMediaRouterActivity} sample which
 * uses the media router to automatically select a secondary display
 * on which to show content based on the currently selected route.
 * </p>
 */
public class MainActivity extends Activity
        implements OnCheckedChangeListener, OnClickListener,ListView.OnItemSelectedListener {
    private final String TAG = "PresentationActivity";

    // Key for storing saved instance state.
    private static final String PRESENTATION_KEY = "presentation";

    // The content that we want to show on the presentation.
    private static final int[] PHOTOS = new int[] {
        R.drawable.frantic,
        R.drawable.photo1, R.drawable.photo2, R.drawable.photo3,
        R.drawable.photo4, R.drawable.photo5, R.drawable.photo6,
        R.drawable.sample_4,
    };

    private DisplayManager mDisplayManager;
    private DisplayListAdapter mDisplayListAdapter;
    private CheckBox mShowAllDisplaysCheckbox;
    private ListView mListView;
    private int mNextImageNumber;

    // List of presentation contents indexed by displayId.
    // This state persists so that we can restore the old presentation
    // contents when the activity is paused or resumed.
    private SparseArray<PresentationContents> mSavedPresentationContents;

    // List of all currently visible presentations indexed by display id.
    private final SparseArray<DemoPresentation> mActivePresentations =
            new SparseArray<DemoPresentation>();

    /**
     * Initialization of the Activity after it is first created.  Must at least
     * call {@link android.app.Activity#setContentView setContentView()} to
     * describe what is to be displayed in the screen.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Be sure to call the super class.
        super.onCreate(savedInstanceState);

        // Restore saved instance state.
        if (savedInstanceState != null) {
            mSavedPresentationContents =
                    savedInstanceState.getSparseParcelableArray(PRESENTATION_KEY);
        } else {
            mSavedPresentationContents = new SparseArray<PresentationContents>();
        }

        // Get the display manager service.
        mDisplayManager = (DisplayManager)getSystemService(Context.DISPLAY_SERVICE);

        // See assets/res/any/layout/presentation_activity.xml for this
        // view layout definition, which is being set here as
        // the content of our screen.
        setContentView(R.layout.activity_main);

        // Set up checkbox to toggle between showing all displays or only presentation displays.
        mShowAllDisplaysCheckbox = (CheckBox)findViewById(R.id.show_all_displays);
        mShowAllDisplaysCheckbox.setOnCheckedChangeListener(this);
        

        // Set up the list of displays.
        mDisplayListAdapter = new DisplayListAdapter(this);
        mListView = (ListView)findViewById(R.id.display_list);
        mListView.setAdapter(mDisplayListAdapter);
        mListView.setOnItemSelectedListener(this);
        //mDisplayManager.setDisplayOutputMode(Display.TYPE_HDMI, DisplayManager.DISPLAY_OUTPUT_TYPE_LCD, 0);
        
        
        
		//Intent intent =  new Intent(MainActivity.this,BgService.class);
		//startService(intent); 
    }

    @Override
    protected void onResume() {
        // Be sure to call the super class.
        super.onResume();

        // Update our list of displays on resume.
        mDisplayListAdapter.updateContents();

        // Restore presentations from before the activity was paused.
        final int numDisplays = mDisplayListAdapter.getCount();
        for (int i = 0; i < numDisplays; i++) {
            final Display display = mDisplayListAdapter.getItem(i);
            final PresentationContents contents =
                    mSavedPresentationContents.get(display.getDisplayId());
            if (contents != null) {
                showPresentation(display, contents);
            }
        }
        mSavedPresentationContents.clear();

        // Register to receive events from the display manager.
        mDisplayManager.registerDisplayListener(mDisplayListener, null);
        //mDisplayManager.setDisplayOutputMode(Display.TYPE_HDMI, DisplayManager.DISPLAY_OUTPUT_TYPE_LCD, 0);
        mShowAllDisplaysCheckbox.setChecked(true);
    }

    @Override
    protected void onPause() {
        // Be sure to call the super class.
        super.onPause();

        // Unregister from the display manager.
        mDisplayManager.unregisterDisplayListener(mDisplayListener);

        // Dismiss all of our presentations but remember their contents.
        Log.d(TAG, "Activity is being paused.  Dismissing all active presentation.");
        for (int i = 0; i < mActivePresentations.size(); i++) {
            DemoPresentation presentation = mActivePresentations.valueAt(i);
            int displayId = mActivePresentations.keyAt(i);
            mSavedPresentationContents.put(displayId, presentation.mContents);
            presentation.dismiss();
        }
        mActivePresentations.clear();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        // Be sure to call the super class.
        super.onSaveInstanceState(outState);
        outState.putSparseParcelableArray(PRESENTATION_KEY, mSavedPresentationContents);
    }

    /**
     * Shows a {@link Presentation} on the specified display.
     */
    private void showPresentation(Display display, PresentationContents contents) {
        final int displayId = display.getDisplayId();
        if (mActivePresentations.get(displayId) != null) {
            return;
        }

        Log.d(TAG, "Showing presentation photo #" + contents.photo
                + " on display #" + displayId + ".");

        DemoPresentation presentation = new DemoPresentation(this, display, contents);
        presentation.show();
        presentation.setOnDismissListener(mOnDismissListener);
        mActivePresentations.put(displayId, presentation);
    }

    /**
     * Hides a {@link Presentation} on the specified display.
     */
    private void hidePresentation(Display display) {
        final int displayId = display.getDisplayId();
        DemoPresentation presentation = mActivePresentations.get(displayId);
        if (presentation == null) {
            return;
        }

        Log.d(TAG, "Dismissing presentation on display #" + displayId + ".");

        presentation.dismiss();
        mActivePresentations.delete(displayId);
    }

    private int getNextPhoto() {
        final int photo = mNextImageNumber;
        mNextImageNumber = (mNextImageNumber + 1) % PHOTOS.length;
        return photo;
    }

    /**
     * Called when the show all displays checkbox is toggled or when
     * an item in the list of displays is checked or unchecked.
     */
    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if (buttonView == mShowAllDisplaysCheckbox) {
            // Show all displays checkbox was toggled.
            mDisplayListAdapter.updateContents();
        } else {
            // Display item checkbox was toggled.
            final Display display = (Display)buttonView.getTag();
			Intent intent =  new Intent(MainActivity.this,BgService.class);
			
            if (isChecked) {
            	
                //PresentationContents contents = new PresentationContents(getNextPhoto());
                //showPresentation(display, contents);
				intent.putExtra("DISPLAY_ID", display.getDisplayId());
				
				startService(intent); 
            } else {
				stopService(intent);
                //hidePresentation(display);
            }
        }
    }

    /**
     * Called when the Info button next to a display is clicked to show information
     * about the display.
     */
    @Override
    public void onClick(View v) {
        Context context = v.getContext();
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        final Display display = (Display)v.getTag();
        Resources r = context.getResources();
        AlertDialog alert = builder
                .setTitle(r.getString(
                        R.string.presentation_alert_info_text, display.getDisplayId()))
                .setMessage(display.toString())
                .setNeutralButton(R.string.presentation_alert_dismiss_text,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                            }
                    })
                .create();
        alert.show();
    }

    /**
     * Listens for displays to be added, changed or removed.
     * We use it to update the list and show a new {@link Presentation} when a
     * display is connected.
     *
     * Note that we don't bother dismissing the {@link Presentation} when a
     * display is removed, although we could.  The presentation API takes care
     * of doing that automatically for us.
     */
    private final DisplayManager.DisplayListener mDisplayListener =
            new DisplayManager.DisplayListener() {
        @Override
        public void onDisplayAdded(int displayId) {
            Log.d(TAG, "Display #" + displayId + " added.");
            mDisplayListAdapter.updateContents();
        }

        @Override
        public void onDisplayChanged(int displayId) {
            Log.d(TAG, "Display #" + displayId + " changed.");
            mDisplayListAdapter.updateContents();
        }

        @Override
        public void onDisplayRemoved(int displayId) {
            Log.d(TAG, "Display #" + displayId + " removed.");
            mDisplayListAdapter.updateContents();
        }
    };

    /**
     * Listens for when presentations are dismissed.
     */
    private final DialogInterface.OnDismissListener mOnDismissListener =
            new DialogInterface.OnDismissListener() {
        @Override
        public void onDismiss(DialogInterface dialog) {
            DemoPresentation presentation = (DemoPresentation)dialog;
            int displayId = presentation.getDisplay().getDisplayId();
            Log.d(TAG, "Presentation on display #" + displayId + " was dismissed.");
            mActivePresentations.delete(displayId);
            mDisplayListAdapter.notifyDataSetChanged();
        }
    };

    /**
     * List adapter.
     * Shows information about all displays.
     */
    private final class DisplayListAdapter extends ArrayAdapter<Display> {
        final Context mContext;

        public DisplayListAdapter(Context context) {
            super(context, R.layout.presentation_list_item);
            mContext = context;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final View v;
            if (convertView == null) {
                v = ((Activity) mContext).getLayoutInflater().inflate(
                        R.layout.presentation_list_item, null);
            } else {
                v = convertView;
            }

            final Display display = getItem(position);
            final int displayId = display.getDisplayId();

            CheckBox cb = (CheckBox)v.findViewById(R.id.checkbox_presentation);
            cb.setTag(display);
            cb.setOnCheckedChangeListener(MainActivity.this);
            cb.setChecked(mActivePresentations.indexOfKey(displayId) >= 0
                    || mSavedPresentationContents.indexOfKey(displayId) >= 0);
            cb.setChecked(displayId>0?true:false);
            cb.setFocusable(true);

            TextView tv = (TextView)v.findViewById(R.id.display_id);
            tv.setText(v.getContext().getResources().getString(
                    R.string.presentation_display_id_text, displayId, display.getName()));

            Button b = (Button)v.findViewById(R.id.info);
            b.setTag(display);
            b.setOnClickListener(MainActivity.this);

            return v;
        }

        /**
         * Update the contents of the display list adapter to show
         * information about all current displays.
         */
        public void updateContents() {
            clear();

            String displayCategory = getDisplayCategory();
            Display[] displays = mDisplayManager.getDisplays(displayCategory);
            addAll(displays);

            Log.d(TAG, "There are currently " + displays.length + " displays connected.");
            for (Display display : displays) {
                Log.d(TAG, "  " + display);
            }
        }

        private String getDisplayCategory() {
            return mShowAllDisplaysCheckbox.isChecked() ? null :
                DisplayManager.DISPLAY_CATEGORY_PRESENTATION;
        }
    }

	@Override
	public void onItemSelected(AdapterView<?> arg0, View arg1, int arg2,
			long arg3) {
		// TODO Auto-generated method stub
		arg0.setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS); 
	}

	@Override
	public void onNothingSelected(AdapterView<?> arg0) {
		// TODO Auto-generated method stub
		arg0.setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS);
	}


}
//END_INCLUDE(activity)
