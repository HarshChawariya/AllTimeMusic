package com.example.alltimemusic;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class CustomPopupMenu {
    private final Context context;
    private final PopupWindow popupWindow;
    private final LinearLayout container;
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onClick(String title);
    }

    public CustomPopupMenu(Context context, View anchor) {
        this.context = context;
        
        View view = LayoutInflater.from(context).inflate(R.layout.custom_popup_container, null);
        container = view.findViewById(R.id.menu_items_container);
        
        popupWindow = new PopupWindow(view, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popupWindow.setElevation(25); // Set elevation for shadow on the popup window itself
        popupWindow.setAnimationStyle(android.R.style.Animation_Dialog); // Smooth entry/exit
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popupWindow.setOutsideTouchable(true);
    }

    public void addMenuItem(String title) {
        // Add divider if not the first item
        if (container.getChildCount() > 0) {
            View divider = LayoutInflater.from(context).inflate(R.layout.custom_menu_divider, container, false);
            container.addView(divider);
        }

        TextView textView = (TextView) LayoutInflater.from(context).inflate(R.layout.custom_menu_item, container, false);
        textView.setText(title);
        textView.setOnClickListener(v -> {
            if (listener != null) listener.onClick(title);
            popupWindow.dismiss();
        });
        container.addView(textView);
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void show(View anchor) {
        // Measure rootView (the MaterialCardView) for accurate total width including shadows
        View rootView = (View) container.getParent();
        rootView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int totalWidthPx = rootView.getMeasuredWidth();
        
        // Force the popupWindow to use the exact measured width of its content
        popupWindow.setWidth(totalWidthPx);

        float density = context.getResources().getDisplayMetrics().density;
        int marginEndPx = (int) (10 * density);
        int screenWidth = context.getResources().getDisplayMetrics().widthPixels;

        int[] location = new int[2];
        anchor.getLocationOnScreen(location);
        int anchorX = location[0];

        // Target: Position the popup so its right edge is exactly 10dp from screen edge
        int targetLeft = screenWidth - totalWidthPx - marginEndPx;
        int xOffset = targetLeft - anchorX;

        popupWindow.showAsDropDown(anchor, xOffset, 5);
    }
}
