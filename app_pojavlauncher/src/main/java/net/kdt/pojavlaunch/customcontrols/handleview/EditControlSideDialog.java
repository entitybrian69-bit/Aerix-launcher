package net.kdt.pojavlaunch.customcontrols.handleview;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.BitmapFactory;
import android.graphics.Outline;
import android.graphics.drawable.GradientDrawable;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewOutlineProvider;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.widget.SwitchCompat;

import com.kdt.SideDialogView;

import net.kdt.pojavlaunch.CustomControlsActivity;
import net.kdt.pojavlaunch.utils.KeycodeUtils;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.colorselector.ColorSelector;
import net.kdt.pojavlaunch.customcontrols.ControlData;
import net.kdt.pojavlaunch.customcontrols.ControlDrawerData;
import net.kdt.pojavlaunch.customcontrols.ControlJoystickData;
import net.kdt.pojavlaunch.customcontrols.ControlLayout;
import net.kdt.pojavlaunch.customcontrols.LayoutBitmaps;
import net.kdt.pojavlaunch.customcontrols.buttons.ControlButton;
import net.kdt.pojavlaunch.customcontrols.buttons.ControlDrawer;
import net.kdt.pojavlaunch.customcontrols.buttons.ControlInterface;
import net.kdt.pojavlaunch.customcontrols.theme.ControlTheme;
import net.kdt.pojavlaunch.customcontrols.theme.ControlThemes;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.utils.CropperUtils;
import net.kdt.pojavlaunch.utils.interfaces.SimpleItemSelectedListener;
import net.kdt.pojavlaunch.utils.interfaces.SimpleSeekBarListener;
import net.kdt.pojavlaunch.utils.interfaces.SimpleTextWatcher;

import java.util.ArrayList;
import java.util.List;

public class EditControlSideDialog extends SideDialogView {

    private final Spinner[] mKeycodeSpinners = new Spinner[4];
    public boolean internalChanges = false; // True when we programmatically change stuff.
    private final View.OnLayoutChangeListener mLayoutChangedListener = new View.OnLayoutChangeListener() {
        @Override
        public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
            if (internalChanges) return;

            internalChanges = true;
            int width = (int) (safeParseFloat(mWidthEditText.getText().toString()));

            if (width >= 0 && Math.abs(right - width) > 1) {
                mWidthEditText.setText(String.valueOf(right - left));
            }
            int height = (int) (safeParseFloat(mHeightEditText.getText().toString()));
            if (height >= 0 && Math.abs(bottom - height) > 1) {
                mHeightEditText.setText(String.valueOf(bottom - top));
            }

            internalChanges = false;
        }
    };
    private EditText mNameEditText, mWidthEditText, mHeightEditText;
    private SwitchCompat mToggleSwitch, mPassthroughSwitch, mSwipeableSwitch, mForwardLockSwitch, mAbsoluteTrackingSwitch;
    private Spinner mOrientationSpinner;
    private final TextView[] mKeycodeTextviews = new TextView[4];
    private SeekBar mStrokeWidthSeekbar, mCornerRadiusSeekbar, mAlphaSeekbar;
    private TextView mStrokePercentTextView, mCornerRadiusPercentTextView, mAlphaPercentTextView;
    private TextView mSelectBackgroundBitmap, mSelectBackgroundColor, mSelectStrokeColor;
    private ArrayAdapter<String> mAdapter;
    private List<String> mSpecialArray;
    private CheckBox mDisplayInGameCheckbox, mDisplayInMenuCheckbox;
    private ControlInterface mCurrentlyEditedButton;
    // Decorative textviews
    private TextView mOrientationTextView, mMappingTextView, mNameTextView,
            mCornerRadiusTextView, mVisibilityTextView, mSizeTextview,
            mSizeXTextView, mStrokeWidthTextView, mColorSelectWarningTextView;

    // Color selector related stuff
    private ColorSelector mColorSelector;
    private final ViewGroup mParent;

    // Control themes
    private LinearLayout mThemeChipRow;
    private Button mThemeApplyAllButton;
    private androidx.appcompat.widget.SwitchCompat mThemeAnimateSwitch, mThemeHapticsSwitch, mThemeSoundSwitch;
    /** Theme thumbnails, decoded once for the whole editor. */
    private static final java.util.HashMap<Integer, android.graphics.Bitmap> THEME_THUMBS = new java.util.HashMap<>();
    private final List<ThemeChip> mThemeChips = new ArrayList<>();

    public EditControlSideDialog(Context context, ViewGroup parent) {
        super(context, parent, R.layout.dialog_control_button_setting);
        mParent = parent;
    }

    @Override
    protected void onInflate() {
        bindLayout();
        buildColorSelector();
        loadAdapter();
        setupRealTimeListeners();
        buildThemeSection();
    }

    @Override
    protected void onDestroy() {
        mColorSelector.disappear(true);
    }

    private void buildColorSelector() {
        mColorSelector = new ColorSelector(mParent.getContext(), mParent, null);
    }

    /**
     * Slide the layout into the visible screen area
     */
    public void appearColor(boolean fromRight, int color) {
        mColorSelector.show(fromRight, color == -1 ? Color.WHITE : color);
    }

    /**
     * Slide out the layout
     */
    public void disappearColor() {
        mColorSelector.disappear(false);
    }

    /**
     * Slide out the first visible layer.
     *
     * @return True if the last layer is disappearing
     */
    public boolean disappearLayer() {
        if (mColorSelector.isDisplaying()) {
            disappearColor();
            return false;
        } else {
            disappear(false);
            return true;
        }
    }

    /**
     * Switch the panels position if needed
     */
    public void adaptPanelPosition() {
        if(!mDisplaying) return;
        if(mCurrentlyEditedButton == null) return;
        ControlLayout parent = mCurrentlyEditedButton.getControlLayoutParent();
        if(parent == null) return;

        boolean isAtRight = mCurrentlyEditedButton.getControlView().getX() + mCurrentlyEditedButton.getControlView().getWidth() / 2f < mCurrentlyEditedButton.getControlLayoutParent().getWidth() / 2f;
        appear(isAtRight);
        if (mColorSelector.isDisplaying()) {
            Tools.runOnUiThread(() -> appearColor(isAtRight, mCurrentlyEditedButton.getProperties().bgColor));
        }
    }

    public static void setPercentageText(TextView textView, int progress) {
        textView.setText(textView.getContext().getString(R.string.percent_format, progress));
    }

    /* LOADING VALUES */

    /**
     * Load values for basic control data
     */
    public void loadValues(ControlData data) {
        setDefaultVisibilitySetting();
        mOrientationTextView.setVisibility(GONE);
        mOrientationSpinner.setVisibility(GONE);
        mForwardLockSwitch.setVisibility(GONE);
        mAbsoluteTrackingSwitch.setVisibility(GONE);

        mNameEditText.setText(data.name);
        mWidthEditText.setText(String.valueOf(data.getWidth()));
        mHeightEditText.setText(String.valueOf(data.getHeight()));

        mAlphaSeekbar.setProgress((int) (data.opacity * 100));
        mStrokeWidthSeekbar.setProgress((int) data.strokeWidth * 10);
        mCornerRadiusSeekbar.setProgress((int) data.cornerRadius);

        setPercentageText(mAlphaPercentTextView, (int) (data.opacity * 100));
        setPercentageText(mStrokePercentTextView, (int) data.strokeWidth * 10);
        setPercentageText(mCornerRadiusPercentTextView, (int) data.cornerRadius);

        mToggleSwitch.setChecked(data.isToggle);
        mPassthroughSwitch.setChecked(data.passThruEnabled);
        mSwipeableSwitch.setChecked(data.isSwipeable);

        mDisplayInGameCheckbox.setChecked(data.displayInGame);
        mDisplayInMenuCheckbox.setChecked(data.displayInMenu);

        // Which look this control is wearing right now.
        refreshThemeChips(data.theme);

        for (int i = 0; i < data.keycodes.length; i++) {
            if (data.keycodes[i] < 0) {
                mKeycodeSpinners[i].setSelection(data.keycodes[i] + mSpecialArray.size());
            } else {
                mKeycodeSpinners[i].setSelection(KeycodeUtils.getIndexByValue(data.keycodes[i]) + mSpecialArray.size());
            }
        }

        setHasBitmap(Tools.isValidString(data.bitmapTag));

        Context viewContext = mCurrentlyEditedButton.getControlView().getContext();

        // Don't allow editing the bitmap in-game (i don't want to bother with implementing that,
        // and it has potential to kill the game during icon selection)
        if(!(viewContext instanceof CustomControlsActivity))
            mSelectBackgroundBitmap.setVisibility(GONE);
    }

    /**
     * Load values for extended control data
     */
    public void loadValues(ControlDrawerData data) {
        loadValues(data.properties);

        mOrientationSpinner.setSelection(
                ControlDrawerData.orientationToInt(data.orientation));

        mMappingTextView.setVisibility(GONE);
        for (int i = 0; i < mKeycodeSpinners.length; i++) {
            mKeycodeSpinners[i].setVisibility(GONE);
            mKeycodeTextviews[i].setVisibility(GONE);
        }

        mOrientationTextView.setVisibility(VISIBLE);
        mOrientationSpinner.setVisibility(VISIBLE);

        mSwipeableSwitch.setVisibility(View.GONE);
        mPassthroughSwitch.setVisibility(View.GONE);
        mToggleSwitch.setVisibility(View.GONE);
    }

    /**
     * Load values for the joystick
     */
    public void loadJoystickValues(ControlJoystickData data) {
        loadValues(data);

        mMappingTextView.setVisibility(GONE);
        for (int i = 0; i < mKeycodeSpinners.length; i++) {
            mKeycodeSpinners[i].setVisibility(GONE);
            mKeycodeTextviews[i].setVisibility(GONE);
        }

        mNameTextView.setVisibility(GONE);
        mNameEditText.setVisibility(GONE);

        mCornerRadiusTextView.setVisibility(GONE);
        mCornerRadiusSeekbar.setVisibility(GONE);
        mCornerRadiusPercentTextView.setVisibility(GONE);

        mSwipeableSwitch.setVisibility(View.GONE);
        mPassthroughSwitch.setVisibility(View.GONE);
        mToggleSwitch.setVisibility(View.GONE);

        mForwardLockSwitch.setVisibility(VISIBLE);
        mForwardLockSwitch.setChecked(data.forwardLock);

        mAbsoluteTrackingSwitch.setVisibility(VISIBLE);
        mAbsoluteTrackingSwitch.setChecked(data.absolute);

        mSelectBackgroundBitmap.setVisibility(GONE);
    }

    /**
     * Load values for sub buttons
     */
    public void loadSubButtonValues(ControlData data, ControlDrawerData.Orientation drawerOrientation) {
        loadValues(data);

        // Size linked to the parent drawer depending on the drawer settings
        if(drawerOrientation != ControlDrawerData.Orientation.FREE){
            mSizeTextview.setVisibility(GONE);
            mSizeXTextView.setVisibility(GONE);
            mWidthEditText.setVisibility(GONE);
            mHeightEditText.setVisibility(GONE);
        }

        // No conditional, already depends on the parent drawer visibility
        mVisibilityTextView.setVisibility(GONE);
        mDisplayInMenuCheckbox.setVisibility(GONE);
        mDisplayInGameCheckbox.setVisibility(GONE);
    }

    private void loadAdapter() {
        //Initialize adapter for keycodes
        mAdapter = new ArrayAdapter<>(mDialogContent.getContext(), R.layout.item_centered_textview);
        mSpecialArray = ControlData.buildSpecialButtonArray();

        mAdapter.addAll(mSpecialArray);
        mAdapter.addAll(KeycodeUtils.generateKeyName());
        mAdapter.setDropDownViewResource(android.R.layout.simple_list_item_single_choice);

        for (Spinner spinner : mKeycodeSpinners) {
            spinner.setAdapter(mAdapter);
        }

        // Orientation spinner
        ArrayAdapter<ControlDrawerData.Orientation> adapter = new ArrayAdapter<>(mDialogContent.getContext(), android.R.layout.simple_spinner_item);
        adapter.addAll(ControlDrawerData.getOrientations());
        adapter.setDropDownViewResource(android.R.layout.simple_list_item_single_choice);

        mOrientationSpinner.setAdapter(adapter);
    }

    private void setDefaultVisibilitySetting() {
        for (int i = 0; i < ((ViewGroup)mDialogContent).getChildCount(); ++i) {
            ((ViewGroup)mDialogContent).getChildAt(i).setVisibility(VISIBLE);
        }
        for(Spinner s : mKeycodeSpinners) {
            s.setVisibility(View.INVISIBLE);
        }
        mColorSelectWarningTextView.setVisibility(GONE);
    }

    private void setHasBitmap(boolean hasBitmap) {
        int visibility = (!hasBitmap) ? VISIBLE : GONE;
        int visibilityOpposite = hasBitmap ? VISIBLE : GONE;

        // Disable all settings not available in bitmap background mode
        mSelectStrokeColor.setVisibility(visibility);
        mStrokePercentTextView.setVisibility(visibility);
        mStrokeWidthSeekbar.setVisibility(visibility);
        mCornerRadiusSeekbar.setVisibility(visibility);
        mCornerRadiusPercentTextView.setVisibility(visibility);
        mCornerRadiusTextView.setVisibility(visibility);
        mStrokeWidthTextView.setVisibility(visibility);

        // Show the warning that will notify the user that color selection will reset the bitmap
        mColorSelectWarningTextView.setVisibility(visibilityOpposite);
    }

    private void bindLayout() {
        mNameEditText = mDialogContent.findViewById(R.id.editName_editText);
        mWidthEditText = mDialogContent.findViewById(R.id.editSize_editTextX);
        mHeightEditText = mDialogContent.findViewById(R.id.editSize_editTextY);
        mToggleSwitch = mDialogContent.findViewById(R.id.checkboxToggle);
        mPassthroughSwitch = mDialogContent.findViewById(R.id.checkboxPassThrough);
        mSwipeableSwitch = mDialogContent.findViewById(R.id.checkboxSwipeable);
        mForwardLockSwitch = mDialogContent.findViewById(R.id.checkboxForwardLock);
        mAbsoluteTrackingSwitch = mDialogContent.findViewById(R.id.checkboxAbsoluteFingerTracking);
        mKeycodeSpinners[0] = mDialogContent.findViewById(R.id.editMapping_spinner_1);
        mKeycodeSpinners[1] = mDialogContent.findViewById(R.id.editMapping_spinner_2);
        mKeycodeSpinners[2] = mDialogContent.findViewById(R.id.editMapping_spinner_3);
        mKeycodeSpinners[3] = mDialogContent.findViewById(R.id.editMapping_spinner_4);
        mKeycodeTextviews[0] = mDialogContent.findViewById(R.id.mapping_1_textview);
        mKeycodeTextviews[1] = mDialogContent.findViewById(R.id.mapping_2_textview);
        mKeycodeTextviews[2] = mDialogContent.findViewById(R.id.mapping_3_textview);
        mKeycodeTextviews[3] = mDialogContent.findViewById(R.id.mapping_4_textview);
        mOrientationSpinner = mDialogContent.findViewById(R.id.editOrientation_spinner);
        mStrokeWidthSeekbar = mDialogContent.findViewById(R.id.editStrokeWidth_seekbar);
        mCornerRadiusSeekbar = mDialogContent.findViewById(R.id.editCornerRadius_seekbar);
        mAlphaSeekbar = mDialogContent.findViewById(R.id.editButtonOpacity_seekbar);
        mSelectBackgroundBitmap = mDialogContent.findViewById(R.id.setBackgroundBitmap_textView);
        mSelectBackgroundColor = mDialogContent.findViewById(R.id.editBackgroundColor_textView);
        mSelectStrokeColor = mDialogContent.findViewById(R.id.editStrokeColor_textView);
        mStrokePercentTextView = mDialogContent.findViewById(R.id.editStrokeWidth_textView_percent);
        mAlphaPercentTextView = mDialogContent.findViewById(R.id.editButtonOpacity_textView_percent);
        mCornerRadiusPercentTextView = mDialogContent.findViewById(R.id.editCornerRadius_textView_percent);
        mDisplayInGameCheckbox = mDialogContent.findViewById(R.id.visibility_game_checkbox);
        mDisplayInMenuCheckbox = mDialogContent.findViewById(R.id.visibility_menu_checkbox);

        //Decorative stuff
        mMappingTextView = mDialogContent.findViewById(R.id.editMapping_textView);
        mOrientationTextView = mDialogContent.findViewById(R.id.editOrientation_textView);
        mNameTextView = mDialogContent.findViewById(R.id.editName_textView);
        mCornerRadiusTextView = mDialogContent.findViewById(R.id.editCornerRadius_textView);
        mVisibilityTextView = mDialogContent.findViewById(R.id.visibility_textview);
        mSizeTextview = mDialogContent.findViewById(R.id.editSize_textView);
        mSizeXTextView = mDialogContent.findViewById(R.id.editSize_x_textView);
        mStrokeWidthTextView = mDialogContent.findViewById(R.id.editStrokeWidth_textView);
        mColorSelectWarningTextView = mDialogContent.findViewById(R.id.editBackgroundColorWarning_textView);

        mThemeChipRow = mDialogContent.findViewById(R.id.theme_chip_row);
        mThemeApplyAllButton = mDialogContent.findViewById(R.id.theme_apply_all_button);
        mThemeAnimateSwitch = mDialogContent.findViewById(R.id.theme_animate_switch);
        mThemeHapticsSwitch = mDialogContent.findViewById(R.id.theme_haptics_switch);
        mThemeSoundSwitch = mDialogContent.findViewById(R.id.theme_sound_switch);
    }

    private void removeBitmap(ControlInterface button) {
        LayoutBitmaps storage = button.getControlLayoutParent().getBitmaps();
        ControlData properties = button.getProperties();
        storage.putBitmap(null, properties.bitmapTag);
        properties.bitmapTag = null;
        setHasBitmap(false);
    }

    /**
     * A long function linking all the displayed data on the popup and,
     * the currently edited mCurrentlyEditedButton
     * @noinspection SuspiciousNameCombination
     */
    private void setupRealTimeListeners() {
        mNameEditText.addTextChangedListener((SimpleTextWatcher) s -> {
            if (internalChanges) return;

            mCurrentlyEditedButton.getProperties().name = s.toString();

            // Cheap and unoptimized, doesn't break the abstraction layer
            mCurrentlyEditedButton.setProperties(mCurrentlyEditedButton.getProperties(), false);
        });

        mWidthEditText.addTextChangedListener((SimpleTextWatcher) s -> {
            if (internalChanges) return;

            internalChanges = true;
            float width = safeParseFloat(s.toString());
            if (width >= 0) {
                mCurrentlyEditedButton.getProperties().setWidth(width);
                if (mCurrentlyEditedButton.getProperties() instanceof ControlJoystickData) {
                    // Joysticks are square
                     mCurrentlyEditedButton.getProperties().setHeight(width);
                }
                mCurrentlyEditedButton.updateProperties();
            }
            // Unset after the layout pass, to avoid resetting the text in the edittext
            mCurrentlyEditedButton.getControlView().post(()->internalChanges = false);
        });

        mHeightEditText.addTextChangedListener((SimpleTextWatcher) s -> {
            if (internalChanges) return;

            internalChanges = true;
            float height = safeParseFloat(s.toString());
            if (height >= 0) {
                mCurrentlyEditedButton.getProperties().setHeight(height);
                if (mCurrentlyEditedButton.getProperties() instanceof ControlJoystickData) {
                    // Joysticks are square
                    mCurrentlyEditedButton.getProperties().setWidth(height);
                }
                mCurrentlyEditedButton.updateProperties();
            }
            mCurrentlyEditedButton.getControlView().post(()->internalChanges = false);
        });

        mSwipeableSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (internalChanges) return;
            mCurrentlyEditedButton.getProperties().isSwipeable = isChecked;
        });
        mToggleSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (internalChanges) return;
            mCurrentlyEditedButton.getProperties().isToggle = isChecked;
        });
        mPassthroughSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (internalChanges) return;
            mCurrentlyEditedButton.getProperties().passThruEnabled = isChecked;
        });
        mForwardLockSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (internalChanges) return;
            if(mCurrentlyEditedButton.getProperties() instanceof ControlJoystickData){
                ((ControlJoystickData) mCurrentlyEditedButton.getProperties()).forwardLock = isChecked;
            }
        });
        mAbsoluteTrackingSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (internalChanges) return;
            if(mCurrentlyEditedButton.getProperties() instanceof ControlJoystickData){
                ((ControlJoystickData) mCurrentlyEditedButton.getProperties()).absolute = isChecked;
            }
        });

        mAlphaSeekbar.setOnSeekBarChangeListener((SimpleSeekBarListener) (seekBar, progress, fromUser) -> {
            if (internalChanges) return;
            mCurrentlyEditedButton.getProperties().opacity = mAlphaSeekbar.getProgress() / 100f;
            mCurrentlyEditedButton.getControlView().setAlpha(mAlphaSeekbar.getProgress() / 100f);
            setPercentageText(mAlphaPercentTextView, progress);
        });

        mStrokeWidthSeekbar.setOnSeekBarChangeListener((SimpleSeekBarListener) (seekBar, progress, fromUser) -> {
            if (internalChanges) return;
            mCurrentlyEditedButton.getProperties().strokeWidth = mStrokeWidthSeekbar.getProgress() / 10F;
            mCurrentlyEditedButton.setBackground();
            setPercentageText(mStrokePercentTextView, progress);
        });

        mCornerRadiusSeekbar.setOnSeekBarChangeListener((SimpleSeekBarListener) (seekBar, progress, fromUser) -> {
            if (internalChanges) return;
            mCurrentlyEditedButton.getProperties().cornerRadius = mCornerRadiusSeekbar.getProgress();
            mCurrentlyEditedButton.setBackground();
            setPercentageText(mCornerRadiusPercentTextView, progress);
        });


        for (int i = 0; i < mKeycodeSpinners.length; ++i) {
            int finalI = i;
            mKeycodeTextviews[i].setOnClickListener(v -> mKeycodeSpinners[finalI].performClick());

            mKeycodeSpinners[i].setOnItemSelectedListener((SimpleItemSelectedListener) (parent, view, position, id) -> {
                // Side note, spinner listeners are fired later than all the other ones.
                // Meaning the internalChanges bool is useless here.
                if (position < mSpecialArray.size()) {
                    mCurrentlyEditedButton.getProperties().keycodes[finalI] = mKeycodeSpinners[finalI].getSelectedItemPosition() - mSpecialArray.size();
                } else {
                    mCurrentlyEditedButton.getProperties().keycodes[finalI] = KeycodeUtils.getValueByIndex(mKeycodeSpinners[finalI].getSelectedItemPosition() - mSpecialArray.size());
                }
                mKeycodeTextviews[finalI].setText((String) mKeycodeSpinners[finalI].getSelectedItem());
            });
        }


        mOrientationSpinner.setOnItemSelectedListener((SimpleItemSelectedListener) (parent, view, position, id) -> {
            // Side note, spinner listeners are fired later than all the other ones.
            // Meaning the internalChanges bool is useless here.

            if (mCurrentlyEditedButton instanceof ControlDrawer) {
                ((ControlDrawer) mCurrentlyEditedButton).drawerData.orientation = ControlDrawerData.intToOrientation(mOrientationSpinner.getSelectedItemPosition());
                ((ControlDrawer) mCurrentlyEditedButton).syncButtons();
            }
        });

        mDisplayInGameCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (internalChanges) return;
            mCurrentlyEditedButton.getProperties().displayInGame = isChecked;
        });

        mDisplayInMenuCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (internalChanges) return;
            mCurrentlyEditedButton.getProperties().displayInMenu = isChecked;
        });

        mSelectStrokeColor.setOnClickListener(v -> {
            mColorSelector.setAlphaEnabled(false);
            mColorSelector.setColorSelectionListener(color -> {
                removeBitmap(mCurrentlyEditedButton);
                mCurrentlyEditedButton.getProperties().strokeColor = color;
                mCurrentlyEditedButton.setBackground();
            });
            appearColor(isAtRight(), mCurrentlyEditedButton.getProperties().strokeColor);
        });

        mSelectBackgroundBitmap.setOnClickListener(v ->  {
            final View mTargetView = mCurrentlyEditedButton.getControlView();
            CropperUtils.CropperReceiver receiver = new CropperUtils.CropperReceiver() {
                @Override
                public float getAspectRatio() {
                    return (float) mTargetView.getWidth() / mTargetView.getHeight();
                }

                @Override
                public int getTargetMaxSide() {
                    return Math.max(mTargetView.getWidth(), mTargetView.getHeight());
                }

                @Override
                public void onCropped(Bitmap contentBitmap) {
                    ControlData buttonProperties = mCurrentlyEditedButton.getProperties();
                    LayoutBitmaps storage = mCurrentlyEditedButton.getControlLayoutParent().getBitmaps();
                    String oldTag = buttonProperties.bitmapTag;
                    buttonProperties.bitmapTag = storage.putBitmap(contentBitmap, oldTag);
                    setHasBitmap(true);
                    mCurrentlyEditedButton.setBackground();
                }

                @Override
                public void onFailed(Exception exception) {
                    Tools.showError(mTargetView.getContext(), exception);
                }
            };
            Context context = mTargetView.getContext();
            if(context instanceof CustomControlsActivity) {
                ((CustomControlsActivity)context).startCropping(receiver);
            }
        });

        mSelectBackgroundColor.setOnClickListener(v -> {
            mColorSelector.setAlphaEnabled(true);
            mColorSelector.setColorSelectionListener(color -> {
                removeBitmap(mCurrentlyEditedButton);
                mCurrentlyEditedButton.getProperties().bgColor = color;
                mCurrentlyEditedButton.setBackground();
            });
            appearColor(isAtRight(), mCurrentlyEditedButton.getProperties().bgColor);
        });
    }

    private float safeParseFloat(String string) {
        float out = -1; // -1
        try {
            out = Float.parseFloat(string);
        } catch (NumberFormatException e) {
            Log.e("EditControlPopup", e.toString());
        }
        return out;
    }

    // ------------------------------------------------------------------ control themes

    /**
     * Builds the Control themes strip: Off first, then every theme in the launcher.
     *
     * <p>Each chip is drawn in its own two colours, so the strip is the preview: what a player sees
     * before tapping is what the control will wear. Picking one paints the control immediately and
     * plays the press animation once, so the choice can be judged without leaving the editor.
     */
    private void buildThemeSection() {
        if (mThemeChipRow == null) return;
        mThemeChipRow.removeAllViews();
        mThemeChips.clear();

        addThemeChip(null);
        for (ControlTheme theme : ControlThemes.ALL) addThemeChip(theme);

        mThemeApplyAllButton.setOnClickListener(v -> applyThemeToAllControls());

        mThemeAnimateSwitch.setChecked(LauncherPreferences.PREF_CONTROL_THEME_ANIMATED);
        mThemeAnimateSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            LauncherPreferences.PREF_CONTROL_THEME_ANIMATED = isChecked;
            LauncherPreferences.DEFAULT_PREF.edit()
                    .putBoolean("controlThemeAnimated", isChecked).apply();
            // Redraw every control, so the change is visible immediately rather than at next launch.
            if (mCurrentlyEditedButton != null) {
                ControlLayout layout = mCurrentlyEditedButton.getControlLayoutParent();
                if (layout != null) {
                    for (ControlInterface button : layout.getButtonChildren()) {
                        button.getControlView().invalidate();
                    }
                }
            }
        });

        mThemeHapticsSwitch.setChecked(LauncherPreferences.PREF_CONTROL_THEME_HAPTICS);
        mThemeHapticsSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            LauncherPreferences.PREF_CONTROL_THEME_HAPTICS = isChecked;
            LauncherPreferences.DEFAULT_PREF.edit()
                    .putBoolean("controlThemeHaptics", isChecked).apply();
            if (isChecked) previewThemeFeedback();
        });

        mThemeSoundSwitch.setChecked(LauncherPreferences.PREF_CONTROL_THEME_SOUND);
        mThemeSoundSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            LauncherPreferences.PREF_CONTROL_THEME_SOUND = isChecked;
            LauncherPreferences.DEFAULT_PREF.edit()
                    .putBoolean("controlThemeSound", isChecked).apply();
            if (isChecked) previewThemeFeedback();
        });
    }

    private void addThemeChip(@androidx.annotation.Nullable ControlTheme theme) {
        Context context = mDialogContent.getContext();
        int size = dp(58);

        // The chip is the picture itself, so the strip is the preview: what is tapped is what the
        // control will wear.
        LinearLayout chip = new LinearLayout(context);
        chip.setOrientation(LinearLayout.VERTICAL);
        chip.setGravity(Gravity.CENTER_HORIZONTAL);
        chip.setPadding(dp(4), dp(4), dp(4), dp(4));

        GradientDrawable frame = new GradientDrawable();
        frame.setCornerRadius(dp(12));
        frame.setColor(0x33101820);
        chip.setBackground(frame);

        ImageView art = new ImageView(context);
        art.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        art.setClipToOutline(true);
        final float corner = dp(9);
        art.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), corner);
            }
        });
        art.setImageBitmap(theme == null ? null : thumbnail(context, theme.art, size));
        if (theme == null) {
            // "Off" has no picture: a plain grey swatch stands for the untouched control.
            GradientDrawable plain = new GradientDrawable();
            plain.setCornerRadius(corner);
            plain.setColor(0xFF39424C);
            art.setBackground(plain);
        }
        chip.addView(art);

        TextView label = new TextView(context);
        label.setText(theme == null ? R.string.ctrl_theme_off : theme.nameRes);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f);
        label.setGravity(Gravity.CENTER);
        label.setMaxLines(1);
        label.setPadding(0, dp(3), 0, 0);
        chip.addView(label);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.rightMargin = dp(8);
        chip.setLayoutParams(params);

        chip.setOnClickListener(v -> selectTheme(theme));
        mThemeChipRow.addView(chip);
        mThemeChips.add(new ThemeChip(theme, frame, label));
    }

    /** Decodes a theme's art down to a thumbnail; each one is decoded once for the whole editor. */
    private android.graphics.Bitmap thumbnail(Context context, int res, int size) {
        android.graphics.Bitmap cached = THEME_THUMBS.get(res);
        if (cached != null) return cached;
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeResource(context.getResources(), res, bounds);
        int sample = 1;
        int shortest = Math.min(bounds.outWidth, bounds.outHeight);
        while (shortest / (sample * 2) >= size) sample *= 2;

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        options.inScaled = false;
        android.graphics.Bitmap bitmap =
                BitmapFactory.decodeResource(context.getResources(), res, options);
        if (bitmap != null) THEME_THUMBS.put(res, bitmap);
        return bitmap;
    }

    /** Puts [theme] on the control being edited, and previews it once. */
    private void selectTheme(@androidx.annotation.Nullable ControlTheme theme) {
        if (mCurrentlyEditedButton == null) return;
        mCurrentlyEditedButton.getProperties().theme = theme == null ? null : theme.id;
        mCurrentlyEditedButton.setProperties(mCurrentlyEditedButton.getProperties(), false);
        refreshThemeChips(theme == null ? null : theme.id);
        previewEditedTheme();
    }

    private void refreshThemeChips(@androidx.annotation.Nullable String themeId) {
        if (mThemeChips.isEmpty()) return;
        for (ThemeChip chip : mThemeChips) {
            boolean selected = chip.theme == null
                    ? themeId == null || themeId.isEmpty()
                    : themeId != null && themeId.equals(chip.theme.id);
            chip.frame.setStroke(dp(selected ? 2 : 1), selected ? Color.WHITE : 0x33FFFFFF);
            chip.frame.setColor(selected ? 0x44FFFFFF : 0x33101820);
            chip.label.setTextColor(selected ? Color.WHITE : 0xFFB9C6D0);
        }
    }

    /** Runs one press and release on the edited control, so the look can be seen in motion. */
    private void previewEditedTheme() {
        if (mCurrentlyEditedButton == null) return;
        View view = mCurrentlyEditedButton.getControlView();
        if (view instanceof ControlButton) {
            ((ControlButton) view).getThemePainter().preview(view);
        }
    }

    /** Plays the haptic and the click for the theme being edited, from the feedback switches. */
    private void previewThemeFeedback() {
        if (mCurrentlyEditedButton == null) return;
        View view = mCurrentlyEditedButton.getControlView();
        if (view instanceof ControlButton) {
            ((ControlButton) view).getThemePainter().playFeedback(view);
        }
    }

    /** Gives every control in the layout the theme the edited one is wearing. */
    private void applyThemeToAllControls() {
        if (mCurrentlyEditedButton == null) return;
        ControlLayout layout = mCurrentlyEditedButton.getControlLayoutParent();
        if (layout == null) return;
        String themeId = mCurrentlyEditedButton.getProperties().theme;
        int applied = 0;
        for (ControlInterface button : layout.getButtonChildren()) {
            if (button == mCurrentlyEditedButton) continue;
            button.getProperties().theme = themeId;
            button.updateProperties();
            applied++;
        }
        Toast.makeText(mDialogContent.getContext(),
                mDialogContent.getContext().getString(R.string.ctrl_themes_applied, applied),
                Toast.LENGTH_SHORT).show();
    }

    private int dp(int value) {
        return (int) (value * mDialogContent.getResources().getDisplayMetrics().density);
    }

    /** One chip in the strip: the theme it stands for, its frame and its label. */
    private static final class ThemeChip {
        @androidx.annotation.Nullable final ControlTheme theme;
        final GradientDrawable frame;
        final TextView label;

        ThemeChip(@androidx.annotation.Nullable ControlTheme theme, GradientDrawable frame,
                  TextView label) {
            this.theme = theme;
            this.frame = frame;
            this.label = label;
        }
    }

    public void setCurrentlyEditedButton(ControlInterface button) {
        if (mCurrentlyEditedButton != null)
            mCurrentlyEditedButton.getControlView().removeOnLayoutChangeListener(mLayoutChangedListener);
        mCurrentlyEditedButton = button;
        mCurrentlyEditedButton.getControlView().addOnLayoutChangeListener(mLayoutChangedListener);
    }

}
