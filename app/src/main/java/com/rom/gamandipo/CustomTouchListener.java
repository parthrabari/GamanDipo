package com.rom.gamandipo;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

/**
 * Created by Parth on 08/12/2017.
 */

public class CustomTouchListener implements RecyclerView.OnItemTouchListener {

    //Gesture Detector to intercept the touch events
    GestureDetector gestureDetector;
    private onItemClickListener onItemClickListener;

    public CustomTouchListener(Context context,final onItemClickListener onItemClickListener) {
        this.onItemClickListener = onItemClickListener;

        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener(){
            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                return true;
            }
        });

    }

    @Override
    public boolean onInterceptTouchEvent(RecyclerView rv, MotionEvent e) {

        View child = rv.findChildViewUnder(e.getX(),e.getY());

        if(child != null && onItemClickListener != null && gestureDetector.onTouchEvent(e)) {
            onItemClickListener.onClick(child,rv.getChildLayoutPosition(child));
        }

        return false;
    }

    @Override
    public void onTouchEvent(RecyclerView rv, MotionEvent e) {

    }

    @Override
    public void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept) {

    }
}
