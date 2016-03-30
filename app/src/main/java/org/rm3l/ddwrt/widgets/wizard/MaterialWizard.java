package org.rm3l.ddwrt.widgets.wizard;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.CollapsingToolbarLayout;
import android.support.v4.content.ContextCompat;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.crashlytics.android.Crashlytics;
import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.squareup.otto.Bus;
import com.squareup.otto.Subscribe;

import org.codepond.wizardroid.WizardFlow;
import org.codepond.wizardroid.WizardFragment;
import org.codepond.wizardroid.WizardStep;
import org.rm3l.ddwrt.R;
import org.rm3l.ddwrt.events.bus.BusSingleton;
import org.rm3l.ddwrt.events.wizard.WizardStepVisibleToUserEvent;
import org.rm3l.ddwrt.utils.DDWRTCompanionConstants;
import org.rm3l.ddwrt.widgets.ViewPagerWithAllowedSwipeDirection;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import me.relex.circleindicator.CircleIndicator;

/**
 * Created by rm3l on 14/03/16.
 */
public abstract class MaterialWizard extends WizardFragment implements View.OnClickListener {

    private static final String LOG_TAG = MaterialWizard.class.getSimpleName();

    public static final GsonBuilder GSON_BUILDER = new GsonBuilder();
    public static final String CURRENT_WIZARD_CONTEXT_PREF_KEY = \"fake-key\";

    private ViewPagerWithAllowedSwipeDirection mPager;

    private Button nextButton;
    private Button previousButton;
    private Button cancelButton;

    private CollapsingToolbarLayout collapsingToolbarLayout;
    private TextView wizardSubTitle;
    private CircleIndicator indicator;

    //You must have an empty constructor according to Fragment documentation
    public MaterialWizard() {
    }

    @NonNull
    protected abstract String getWizardTitle();

    /**
     * Binding the layout and setting buttons hooks
     */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final Context context = getContext();

        final View mWizardLayout = inflater.inflate(R.layout.wizard, container, false);

        wizardSubTitle = (TextView) mWizardLayout.findViewById(R.id.wizard_subtitle);

        collapsingToolbarLayout = (CollapsingToolbarLayout) mWizardLayout.findViewById(R.id.htab_collapse_toolbar);
        collapsingToolbarLayout.setTitleEnabled(true);
        collapsingToolbarLayout.setCollapsedTitleTextColor(
                ContextCompat.getColor(getContext(), R.color.white));
        collapsingToolbarLayout.setExpandedTitleColor(
                ContextCompat.getColor(getContext(), R.color.white));
        String wizardTitle = getWizardTitle();
        if (TextUtils.isEmpty(wizardTitle)) {
            wizardTitle = "Wizard";
        }
        collapsingToolbarLayout.setTitle(wizardTitle);

        mPager = (ViewPagerWithAllowedSwipeDirection) mWizardLayout.findViewById(R.id.step_container);
        mPager.setAllowedSwipeDirection(ViewPagerWithAllowedSwipeDirection.SwipeDirection.NONE);
        mPager.setOffscreenPageLimit(1);

        indicator = (CircleIndicator) mWizardLayout.findViewById(R.id.indicator);

        nextButton = (Button) mWizardLayout.findViewById(R.id.wizard_next_button);
        if (!Strings.isNullOrEmpty(getNextButtonLabel())) {
            nextButton.setText(getNextButtonLabel());
        }
        nextButton.setOnClickListener(this);

        previousButton = (Button) mWizardLayout.findViewById(R.id.wizard_previous_button);
        if (!Strings.isNullOrEmpty(getPreviousButtonLabel())) {
            previousButton.setText(getPreviousButtonLabel());
        }
        previousButton.setOnClickListener(this);

        cancelButton = (Button) mWizardLayout.findViewById(R.id.wizard_cancel_button);
        cancelButton.setOnClickListener(this);
        this.cancelButton.setEnabled(true);

        return mWizardLayout;
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        indicator.setViewPager(mPager);
    }

    @Override
    public void onStart() {
        super.onStart();
        final Bus busInstance = BusSingleton.getBusInstance();
        Crashlytics.log(Log.DEBUG, LOG_TAG, "onStart: Register on bus (" + busInstance + ")");
        busInstance.register(this);
    }

    @Override
    public void onStop() {
        final Bus busInstance = BusSingleton.getBusInstance();
        Crashlytics.log(Log.DEBUG, LOG_TAG, "onStop: Unregister from bus (" + busInstance + ")");
        busInstance.unregister(this);
        super.onStop();
    }

    //You must override this method and create a wizard flow by
    //using WizardFlow.Builder as shown in this example
    @SuppressWarnings("unchecked")
    @Override
    public final WizardFlow onSetup() {

        final WizardFlow.Builder wizardFlowBuilder = new WizardFlow.Builder();
        final List<?> stepClasses = getStepClasses();
        if (stepClasses != null) {
            for (final Object stepClass : stepClasses) {
                if (stepClass == null ||
                        !Class.class.isAssignableFrom(stepClass.getClass())) {
                    continue;
                }
                wizardFlowBuilder.addStep((Class<? extends WizardStep>) stepClass, false);
            }
        }
        return wizardFlowBuilder.create();

    }

    public static Map getWizardContext(@Nullable final Context context) {
        if (context == null) {
            return Collections.emptyMap();
        }

        final SharedPreferences globalPrefs = context
                .getSharedPreferences(DDWRTCompanionConstants.DEFAULT_SHARED_PREFERENCES_KEY,
                        Context.MODE_PRIVATE);

        final String wizardContextJson = globalPrefs.getString(CURRENT_WIZARD_CONTEXT_PREF_KEY,
                "{}");
        final Gson gson = GSON_BUILDER.create();
        final Map wizardContextMap = gson.fromJson(wizardContextJson, Map.class);
        return Collections.unmodifiableMap(wizardContextMap);
    }

    /**
     * Triggered when the wizard is completed.
     * Overriding this method is optional.
     */
    @Override
    public void onWizardComplete() {
        super.onWizardComplete();
        final SharedPreferences globalPrefs = this.getContext()
                .getSharedPreferences(DDWRTCompanionConstants.DEFAULT_SHARED_PREFERENCES_KEY,
                        Context.MODE_PRIVATE);
        globalPrefs.edit().remove(CURRENT_WIZARD_CONTEXT_PREF_KEY).apply();
//        //Do whatever you want to do once the Wizard is complete
//        //in this case I just close the activity, which causes Android
//        //to go back to the previous activity.
//        getActivity().finish();
    }

    @Override
    public void onResume() {
        super.onResume();
        this.updateWizardControls();
    }

    @Override
    public final void onClick(View v) {
        Crashlytics.log("onclick");
        final MaterialWizardStep currentStep = (MaterialWizardStep) wizard.getCurrentStep();
        switch(v.getId()) {
            case R.id.wizard_next_button:
                //Tell the wizard to go to next step
                final boolean stepValidated = currentStep.validateStep();
                Crashlytics.log("stepValidated: " + stepValidated);
                if (stepValidated) {
                    currentStep.onExitSynchronous(WizardStep.EXIT_NEXT);
                    wizard.goNext();
                }
                //Maybe provide a 'denial' animation
                break;
            case R.id.wizard_previous_button:
                //Tell the wizard to go back one step
                currentStep.onExitSynchronous(WizardStep.EXIT_PREVIOUS);
                wizard.goBack();
                break;
            case R.id.wizard_cancel_button:
                final SharedPreferences globalPrefs = this.getContext()
                        .getSharedPreferences(DDWRTCompanionConstants.DEFAULT_SHARED_PREFERENCES_KEY,
                                Context.MODE_PRIVATE);
                globalPrefs.edit().remove(CURRENT_WIZARD_CONTEXT_PREF_KEY).apply();
                //Close wizard
                getActivity().finish();
                break;
        }
    }

    @Subscribe
    public void wizardStepVisibleToUser(final WizardStepVisibleToUserEvent event) {
        final String eventStepTitle = event.getStepTitle();
        Crashlytics.log(Log.DEBUG, LOG_TAG, "wizardStepVisibleToUser(" + eventStepTitle + ")");
        wizardSubTitle.setText(eventStepTitle);
    }

    @Override
    public void onStepChanged() {
        super.onStepChanged();
        final MaterialWizardStep currentStep = (MaterialWizardStep) wizard.getCurrentStep();
        wizardSubTitle.setText(currentStep.getWizardStepTitle());
        this.updateWizardControls();
    }

    /**
     * Updates the UI according to current step position
     */
    private void updateWizardControls() {
        this.cancelButton.setEnabled(true);

        final boolean previousButtonEnabled = !this.wizard.isFirstStep();
        this.previousButton.setEnabled(previousButtonEnabled);
        this.previousButton.setVisibility(previousButtonEnabled ?
            View.VISIBLE : View.INVISIBLE);

        if (!Strings.isNullOrEmpty(this.getPreviousButtonLabel())) {
            this.previousButton.setText(this.getPreviousButtonLabel());
        } else {
            this.previousButton.setText(R.string.wizard_previous);
        }

        this.nextButton.setEnabled(this.wizard.canGoNext());

        if (this.wizard.isLastStep()) {
            if (!Strings.isNullOrEmpty(this.getFinishButtonLabel())) {
                this.nextButton.setText(this.getFinishButtonLabel());
            } else {
                this.nextButton.setText(R.string.action_finish);
            }
        } else {
            if (!Strings.isNullOrEmpty(this.getNextButtonLabel())) {
                this.nextButton.setText(this.getNextButtonLabel());
            } else {
                this.nextButton.setText(R.string.wizard_next);
            }
        }
    }

    @Nullable
    protected abstract <T extends WizardStep & WizardStepVerifiable> List<Class<T>> getStepClasses();


    //Override to define custom labels

    @Nullable
    protected String getPreviousButtonLabel() {
        return null;
    }

    @Nullable
    protected String getNextButtonLabel() {
        return null;
    }

    @Nullable
    protected String getFinishButtonLabel() {
        return null;
    }

}
