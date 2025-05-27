package com.example.kaisoloapp;

import android.os.Bundle;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;


import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class GruppeActivity extends BaseActivity {
    private ListView userListView;
    private ArrayAdapter<String> userAdapter;
    private List<String> userNames = new ArrayList<>();
    private DatabaseReference usersRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gruppe);


        // Initialisieren der Toolbar
        Toolbar toolbar = findViewById(R.id.navigationToolbar);
        setSupportActionBar(toolbar);
        // ListView setup
        userListView = findViewById(R.id.userListView);
        userAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, userNames);
        userListView.setAdapter(userAdapter);

        // Firebase Referenz auf "users"
        usersRef = FirebaseDatabase.getInstance().getReference("users");

        // Daten aus Firebase laden
        usersRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                userNames.clear();
                for (DataSnapshot userSnapshot : snapshot.getChildren()) {
                    String name = userSnapshot.child("name").getValue(String.class);
                    String ort = userSnapshot.child("ort").getValue(String.class);
                    String handynummer = userSnapshot.child("handynummer").getValue(String.class);
                    Boolean host = userSnapshot.child("host").getValue(Boolean.class);

                    if (name != null && ort != null) {
                        String displayName = name + " / " + ort + " / " + handynummer;
                        if (host != null && host) {
                            displayName += " (Host)";
                        }
                        userNames.add(displayName);
                    }
                }
                userAdapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("Firebase", "Fehler beim Laden der Nutzer", error.toException());
            }
        });

        setupSystemBarInsets(findViewById(R.id.gruppeLayout));

    }

}