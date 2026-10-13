package com.example.basehome;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

public class SwipeBackHelper {

    public static void attach(Activity activity) {
        ViewGroup rootView = activity.findViewById(android.R.id.content);
        if (rootView == null || rootView.getChildCount() == 0) return;

        View contentChild = rootView.getChildAt(0);
        if (contentChild instanceof SwipeBackLayout) return; // Already attached

        rootView.removeView(contentChild);

        SwipeBackLayout swipeBackLayout = new SwipeBackLayout(activity);
        swipeBackLayout.addView(contentChild, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        rootView.addView(swipeBackLayout, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
    }

    @SuppressLint("ViewConstructor")
    private static class SwipeBackLayout extends FrameLayout {
        private final Activity activity;
        private float startX;
        private float startY;
        private boolean isTracking = false;

        public SwipeBackLayout(Activity activity) {
            super(activity);
            this.activity = activity;
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent ev) {
            switch (ev.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    startX = ev.getX();
                    startY = ev.getY();
                    isTracking = startX < 100f; // within first 100px from left edge
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (isTracking) {
                        float deltaX = ev.getX() - startX;
                        float currentDeltaY = Math.abs(ev.getY() - startY);
                        if (currentDeltaY > deltaX && currentDeltaY > 20f) {
                            isTracking = false;
                        } else if (deltaX > 20f) {
                            return true; // intercept horizontal swipe from left edge
                        }
                    }
                    break;
            }
            return super.onInterceptTouchEvent(ev);
        }

        @SuppressLint("ClickableViewAccessibility")
        @Override
        public boolean onTouchEvent(MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_MOVE:
                    if (isTracking) {
                        float deltaX = event.getX() - startX;
                        float deltaY = Math.abs(event.getY() - startY);
                        if (deltaY > deltaX && deltaY > 20f) {
                            isTracking = false;
                            return false;
                        }
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (isTracking) {
                        float endX = event.getX();
                        float deltaX = endX - startX;
                        float deltaY = Math.abs(event.getY() - startY);
                        if (deltaX > 120f && deltaX > deltaY) {
                            activity.onBackPressed();
                            return true;
                        }
                    }
                    isTracking = false;
                    break;
            }
            return super.onTouchEvent(event);
        }
    }
}
