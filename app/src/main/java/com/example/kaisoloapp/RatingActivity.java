package com.example.kaisoloapp;

import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;

import java.util.ArrayList;
import java.util.List;

public class RatingActivity extends BaseActivity {

    private List<UserEvent> eventList = new ArrayList<>();
    private UserEventAdapter adapter;
    private ValueEventListener eventsListener;
    private DatabaseReference eventsRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rating);

        Toolbar toolbar = findViewById(R.id.navigationToolbar);
        setSupportActionBar(toolbar);
        setupSystemBarInsets(findViewById(R.id.ratingLayout));

        ListView listView = findViewById(R.id.listViewEvents);

        adapter = new UserEventAdapter(this, eventList);
        listView.setAdapter(adapter);

        //Live-Daten laden
        ladeEventsInListView();

        listView.setOnItemClickListener((parent, view, position, id) -> {
            UserEvent selectedEvent = eventList.get(position);
            if (!selectedEvent.userHasRated) {
                showRatingDialog(selectedEvent);
            } else {
                Toast.makeText(this, "Du hast dieses Event bereits bewertet", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void ladeEventsInListView() {
        eventsRef = FirebaseDatabase.getInstance().getReference("events");
        String currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        //Echtzeit-Listener zur direkt aktualisierung
        eventsListener = eventsRef.orderByChild("timestamp").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                eventList.clear();
                for (DataSnapshot eventSnap : snapshot.getChildren()) {
                    String name = eventSnap.child("host_name").getValue(String.class);
                    //String date = eventSnap.child("date").getValue(String.class);
                    String rawDate = eventSnap.child("date").getValue(String.class);
                    String formattedDate = rawDate;

                    if (rawDate != null && rawDate.contains(".")) {
                        String[] parts = rawDate.split("\\.");
                        if (parts.length == 3) {
                            formattedDate = parts[0] + "." + parts[1] + "\n" + parts[2];
                        }
                    }
                    String id = eventSnap.getKey();

                    if (name != null && formattedDate != null && id != null) {
                        UserEvent userEvent = new UserEvent(name, formattedDate, id);

                        DataSnapshot ratingsSnap = eventSnap.child("ratings");
                        int sum = 0, count = 0;

                        for (DataSnapshot rating : ratingsSnap.getChildren()) {
                            Integer r = rating.getValue(Integer.class);
                            if (r != null) {
                                sum += r;
                                count++;
                                if (rating.getKey().equals(currentUserId)) {
                                    userEvent.userHasRated = true;
                                }
                            }
                        }

                        if (count > 0) {
                            userEvent.averageRating = (float) sum / count;
                        }

                        eventList.add(userEvent);
                    }
                }
                adapter.notifyDataSetChanged(); //Liste aktualisieren
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(RatingActivity.this, "Fehler beim Laden der Events", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showRatingDialog(UserEvent event) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Event bewerten");

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_rating, null);
        RatingBar ratingBar = dialogView.findViewById(R.id.dialogRatingBar);
        builder.setView(dialogView);

        builder.setPositiveButton("Bewerten", (dialog, which) -> {
            int bewertung = (int) ratingBar.getRating();
            speichereBewertung(event, bewertung);
        });

        builder.setNegativeButton("Abbrechen", null);
        builder.show();
    }

    private void speichereBewertung(UserEvent event, int bewertung) {
        String eventId = event.timestamp;
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        DatabaseReference ratingRef = FirebaseDatabase.getInstance()
                .getReference("events").child(eventId).child("ratings").child(userId);

        ratingRef.setValue(bewertung)
                .addOnSuccessListener(aVoid ->
                        Toast.makeText(this, "Bewertung gespeichert", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Fehler beim Speichern", Toast.LENGTH_SHORT).show());
    }

    @Override
    protected void onStop() {
        super.onStop();
        //Listener entfernen um Speicherlecks zu vermeiden
        if (eventsListener != null && eventsRef != null) {
            eventsRef.removeEventListener(eventsListener);
        }
    }
}
