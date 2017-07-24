package com.example.xyzreader;

import android.content.Context;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.widget.NestedScrollView;
import android.util.AttributeSet;
import android.view.View;

public class ScrollAwareFabBehavior extends CoordinatorLayout.Behavior<FloatingActionButton> {
    private static final int FAB_HIDE_THRESHOLD = 200;
    private static final int FAB_SHOW_THRESHOLD = FAB_HIDE_THRESHOLD + 100;

    public ScrollAwareFabBehavior(Context ctx, AttributeSet attr) {
        super(ctx, attr);
    }

    @Override
    public boolean layoutDependsOn(CoordinatorLayout parent, FloatingActionButton child, View dependency) {
        return (dependency instanceof NestedScrollView);
    }

    @Override
    public boolean onDependentViewChanged(CoordinatorLayout parent, FloatingActionButton child, View dependency) {
        int depTop = child.getTop();

        if (depTop < FAB_HIDE_THRESHOLD && child.isShown()) {
            child.hide();
        } else if (depTop > FAB_SHOW_THRESHOLD && child.isShown() == false) {
            child.show();
        }

        return super.onDependentViewChanged(parent, child, dependency);
    }
}
