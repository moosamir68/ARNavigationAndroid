package com.univ.team12.navar.ar;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.beyondar.android.world.BeyondarObject;
import com.univ.team12.navar.R;

public class CustomBeyondarViewAdapter extends BeyondarViewAdapter {

    LayoutInflater inflater;

    public CustomBeyondarViewAdapter(Context context) {
        super(context);
        inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    @Override
    public View getView(BeyondarObject beyondarObject, View recycledView, ViewGroup parent) {
        if (recycledView == null) {
            recycledView = inflater.inflate(R.layout.beyondar_object_view, null);
        }

        TextView nameTextView = (TextView) recycledView.findViewById(R.id.nameTextView);
        nameTextView.setText(beyondarObject.getName());

        TextView distantTextView = (TextView) recycledView.findViewById(R.id.distantTextView);
        distantTextView.setText(String.valueOf((int)beyondarObject.getDistanceFromUser()) + "M");

        // Once the view is ready we specify the position
        setPosition(beyondarObject.getScreenPositionTopRight());

        return recycledView;
    }
}
