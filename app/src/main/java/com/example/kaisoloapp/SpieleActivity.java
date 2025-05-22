package com.example.kaisoloapp;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class SpieleActivity extends BaseActivity {
    private RecyclerView recyclerView;
    private SpieleAdapter adapter;
    private List<Spiel> spieleListe = new ArrayList<>();
    private DatabaseReference spieleRef, userVotesRef;
    private FirebaseUser currentUser;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_spiele);

        // Initialisieren der Toolbar
        Toolbar toolbar = findViewById(R.id.navigationToolbar);
        setSupportActionBar(toolbar);

        // Firebase-Referenzen
        spieleRef = FirebaseDatabase.getInstance().getReference("spiele");
        userVotesRef = FirebaseDatabase.getInstance().getReference("user_votes");

        // Firebase User
        currentUser = FirebaseAuth.getInstance().getCurrentUser();

        //Liste der Spiele
        recyclerView = findViewById(R.id.spieleRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SpieleAdapter(spieleListe, spieleRef, userVotesRef, currentUser);
        recyclerView.setAdapter(adapter);

        findViewById(R.id.fab_add).setOnClickListener(v -> zeigeSpielHinzufuegenDialog());

        ladeSpieleAusFirebase();
    }

    private void ladeSpieleAusFirebase() {
        spieleRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                spieleListe.clear();
                for (DataSnapshot spielSnapshot : snapshot.getChildren()) {
                    Spiel spiel = spielSnapshot.getValue(Spiel.class);
                    spieleListe.add(spiel);
                }
                adapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Toast.makeText(SpieleActivity.this, "Fehler beim Laden", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // Fenster das sich öffnet beim anklicken des + buttons
    private void zeigeSpielHinzufuegenDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Neues Spiel hinzufügen");

        final EditText input = new EditText(this);
        input.setHint("Spielname");
        input.setSingleLine(true);
        input.setImeOptions(EditorInfo.IME_ACTION_DONE); // Enter = Done
        builder.setView(input);

        // Wichtig: zuerst POSITIV setzen
        builder.setPositiveButton("Hinzufügen", (dialog, which) -> {

            String name = input.getText().toString().trim();
            if (!name.isEmpty()) {
                String id = spieleRef.push().getKey();
                Spiel neuesSpiel = new Spiel(id, name, 0);
                spieleRef.child(id).setValue(neuesSpiel);
            }
        });

        // Danach NEGATIV
        builder.setNegativeButton("Abbrechen", (dialog, which) -> dialog.cancel());

        builder.show(); // Zeigt den Dialog an
    }
}
