package org.rm3l.router_companion.widgets;

import android.content.Context;
import android.preference.SwitchPreference;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Switch;

/**
 * Created by rm3l on 01/12/15.
 *
 * Workaround for https://code.google.com/p/android/issues/detail?id=26194
 */
public class MySwitchPreference extends SwitchPreference {

  /**
   * Construct a new SwitchPreference with the given style options.
   *
   * @param context The Context that will style this preference
   * @param attrs Style attributes that differ from the default
   * @param defStyle Theme attribute defining the default style options
   */
  public MySwitchPreference(final Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
  }

  /**
   * Construct a new SwitchPreference with the given style options.
   *
   * @param context The Context that will style this preference
   * @param attrs Style attributes that differ from the default
   */
  public MySwitchPreference(final Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  /**
   * Construct a new SwitchPreference with default style options.
   *
   * @param context The Context that will style this preference
   */
  public MySwitchPreference(final Context context) {
    super(context, null);
  }

  @Override protected void onBindView(final View view) {
    // Clean listener before invoke SwitchPreference.onBindView
    final ViewGroup viewGroup = (ViewGroup) view;
    clearListenerInViewGroup(viewGroup);
    super.onBindView(view);
  }

  /**
   * Clear listener in Switch for specify ViewGroup.
   *
   * @param viewGroup The ViewGroup that will need to clear the listener.
   */
  private void clearListenerInViewGroup(final ViewGroup viewGroup) {
    if (null == viewGroup) {
      return;
    }

    final int count = viewGroup.getChildCount();
    for (int n = 0; n < count; ++n) {
      final View childView = viewGroup.getChildAt(n);
      if (childView instanceof Switch) {
        final Switch switchView = (Switch) childView;
        switchView.setOnCheckedChangeListener(null);
        return;
      } else if (childView instanceof ViewGroup) {
        final ViewGroup childGroup = (ViewGroup) childView;
        clearListenerInViewGroup(childGroup);
      }
    }
  }
}
