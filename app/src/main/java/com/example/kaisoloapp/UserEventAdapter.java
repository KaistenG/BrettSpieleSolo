package com.example.kaisoloapp;

import android.content.Context;
import android.view.*;
import android.widget.*;
import java.util.List;
import java.util.Locale;

public class UserEventAdapter extends ArrayAdapter<UserEvent> {

    public UserEventAdapter(Context context, List<UserEvent> events) {
        super(context, 0, events);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        UserEvent event = getItem(position);

        if (convertView == null) {
            convertView = LayoutInflater.from(getContext())
                    .inflate(R.layout.item_user_event, parent, false);
        }

        TextView textHost = convertView.findViewById(R.id.textHostName);
        TextView textDate = convertView.findViewById(R.id.textDate);
        RatingBar ratingBar = convertView.findViewById(R.id.ratingBarAvg);
        TextView textAvgValue = convertView.findViewById(R.id.textAverageValue); //Durchschnittsanzeige

        textHost.setText(event.host_name);
        textDate.setText(event.date);
        ratingBar.setRating(event.averageRating); // ⭐ Durchschnitt setzen
        textAvgValue.setText(String.format(Locale.US, "%.1f", event.averageRating)); //Nachkommastelle

        // ✅ Ausgrauen wenn bewertet
        convertView.setAlpha(event.userHasRated ? 0.3f : 1.0f);

        return convertView;
    }
}