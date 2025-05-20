package com.example.kaisoloapp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private EditText loginName, loginOrt, loginHandynummer;
    private Button loginButton;
    private DatabaseReference databaseReference;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        loginName = findViewById(R.id.login_name);
        loginOrt = findViewById(R.id.login_ort);
        loginHandynummer = findViewById(R.id.login_handynummer);
        loginButton = findViewById(R.id.login_button);

        databaseReference = FirebaseDatabase.getInstance().getReference("users");

        // Loginprüfung direkt nach dem Start
        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        boolean isRegistered = prefs.getBoolean("isRegistered", false);
        String uid = prefs.getString("uid", null);

        if (isRegistered && uid != null) {
            databaseReference.child(uid).addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (snapshot.exists()) {
                        startActivity(new Intent(MainActivity.this, HauptActivity.class));
                    } else {
                        prefs.edit().clear().apply(); // UID gelöscht → lokale Daten löschen
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Log.e("Firebase", "Fehler beim Lesen: ", error.toException());
                }
            });
        }

        // Button zum Einloggen
        loginButton.setOnClickListener(v -> loginSpeichern());

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.mainLayout), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    private void loginSpeichern() {
        String name = loginName.getText().toString().trim();
        String ort = loginOrt.getText().toString().trim();
        String handynummer = loginHandynummer.getText().toString().trim();

        if (name.isEmpty() || ort.isEmpty() || handynummer.isEmpty()) {
            Toast.makeText(this, "Bitte alle Felder ausfüllen", Toast.LENGTH_SHORT).show();
            return;
        }

        // Ohne Logindaten bei Firebase einloggen
        FirebaseAuth.getInstance().signInAnonymously()
                .addOnSuccessListener(authResult -> {
                    String uid = authResult.getUser().getUid();

                    //Prüfen, ob bereits Nutzer existieren
                    databaseReference.addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            boolean isFirstUser = !snapshot.exists();

                            //Nutzer-Objekt mit isHost setzen
                            Nutzer neuerNutzer = new Nutzer(name, ort, handynummer, isFirstUser);

                            //Nutzer speichern
                            databaseReference.child(uid).setValue(neuerNutzer)
                                    .addOnSuccessListener(unused -> {
                                        SharedPreferences prefsUid = getSharedPreferences("AppPrefs", MODE_PRIVATE);
                                        prefsUid.edit()
                                                .putBoolean("isRegistered", true)
                                                .putString("uid", uid)
                                                .apply();

                                        Toast.makeText(MainActivity.this, "Eingeloggt!", Toast.LENGTH_SHORT).show();
                                        startActivity(new Intent(MainActivity.this, HauptActivity.class));
                                        finish();
                                    })
                                    .addOnFailureListener(e -> {
                                        Log.e("Firebase", "Fehler beim Speichern: ", e);
                                        Toast.makeText(MainActivity.this, "Fehler beim Speichern", Toast.LENGTH_SHORT).show();
                                    });
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {
                            Log.e("Firebase", "Fehler beim Prüfen auf erste Registrierung: ", error.toException());
                        }
                    });

                })
                .addOnFailureListener(e -> {
                    Log.e("Firebase", "Fehler beim Login: ", e);
                    Toast.makeText(MainActivity.this, "Fehler beim Login", Toast.LENGTH_SHORT).show();
                });
    }
}

/* ALTE NUTZERDATEN ZUSAMMENSTELLEN
                //Nutzerdaten zusammenstellen
                Map<String, Object> nutzerDaten = new HashMap<>();
                nutzerDaten.put("name", name);
                nutzerDaten.put("ort", ort);
                nutzerDaten.put("handynummer", handynummer);

                databaseReference.child(uid).setValue(nutzerDaten)
                        .addOnSuccessListener(unused -> {
                            SharedPreferences prefsUid = getSharedPreferences("AppPrefs", MODE_PRIVATE);
                            prefsUid.edit()
                                    .putBoolean("isRegistered", true)
                                    .putString("uid", uid)
                                    .apply();

                            Toast.makeText(MainActivity.this, "Eingeloggt!", Toast.LENGTH_SHORT).show();
                            startActivity(new Intent(MainActivity.this, HauptActivity.class));
                            finish();
                        })
                        .addOnFailureListener(e -> {
                            Log.e("Firebase", "Fehler beim Speichern: ", e);
                            Toast.makeText(MainActivity.this, "Fehler beim Speichern", Toast.LENGTH_SHORT).show();
                        });

                    }).addOnFailureListener(e -> {
                        Log.e("Firebase", "Fehler beim Login: ", e);
                        Toast.makeText(MainActivity.this, "Fehler beim Login", Toast.LENGTH_SHORT).show();
                    });

        }
} */



