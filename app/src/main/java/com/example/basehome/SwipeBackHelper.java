package com.example.basehome;

import android.app.Activity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

public class SwipeBackHelper {

    public static void attach(Activity activity) {
        View decorView = activity.getWindow().getDecorView();
        if (decorView instanceof ViewGroup) {
            ViewGroup rootView = decorView.findViewById(android.R.id.content);
            if (rootView != null) {
                rootView.setOnTouchListener(new View.OnTouchListener() {
                    private float startX;
                    private float startY;
                    private boolean isTracking = false;

                    @Override
                    public boolean onTouch(View v, MotionEvent event) {
                        switch (event.getAction()) {
                            case MotionEvent.ACTION_DOWN:
                                startX = event.getX();
                                startY = event.getY();
                                // Track if touch starts near the left edge (within first 100 pixels)
                                isTracking = startX < 100f;
                                break;
                            case MotionEvent.ACTION_MOVE:
                                if (isTracking) {
                                    float deltaX = event.getX() - startX;
                                    float deltaY = Math.abs(event.getY() - startY);
                                    if (deltaY > deltaX && deltaY > 20f) {
                                        isTracking = false;
                                    }
                                }
                                break;
                            case MotionEvent.ACTION_UP:
                            case MotionEvent.ACTION_CANCEL:
                                if (isTracking) {
                                    float endX = event.getX();
                                    float deltaX = endX - startX;
                                    float deltaY = Math.abs(event.getY() - startY);
                                    // If swiped right by more than 120px with dominant horizontal movement
                                    if (deltaX > 120f && deltaX > deltaY) {
                                        activity.onBackPressed();
                                        return true;
                                    }
                                }
                                isTracking = false;
                                break;
                        }
                        return false;
                    }
                });
            }
        }
    }
}
