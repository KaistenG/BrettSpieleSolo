package com.example.kaisoloapp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.ValueEventListener;


public class BaseActivity extends AppCompatActivity {

    protected void setupSystemBarInsets(View rootView) {
        ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    private void clearPreferencesAndRedirect() {
        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        prefs.edit().clear().apply();
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

private void proceedLogout(String uid) {
    DatabaseReference dbRef = FirebaseDatabase.getInstance().getReference();
    DatabaseReference userVotesRef = dbRef.child("user_votes").child(uid);
    DatabaseReference spieleRef = dbRef.child("spiele");

    userVotesRef.addListenerForSingleValueEvent(new ValueEventListener() {
        @Override
        public void onDataChange(@NonNull DataSnapshot snapshot) {
            for (DataSnapshot voteSnapshot : snapshot.getChildren()) {
                String key = voteSnapshot.getKey();
                Object value = voteSnapshot.getValue();

                // Stimme für ein Spiel (nicht votes_left)
                if (!"votes_left".equals(key) && value instanceof Boolean && (Boolean) value) {
                    // Votes Count für Spiel um 1 reduzieren
                    spieleRef.child(key).child("votes_count").runTransaction(new Transaction.Handler() {
                        @NonNull
                        @Override
                        public Transaction.Result doTransaction(@NonNull MutableData currentData) {
                            Integer currentVotes = currentData.getValue(Integer.class);
                            if (currentVotes == null) currentVotes = 0;
                            currentData.setValue(Math.max(0, currentVotes - 1));
                            return Transaction.success(currentData);
                        }

                        @Override
                        public void onComplete(@Nullable DatabaseError error, boolean committed, @Nullable DataSnapshot currentData) {
                            if (error != null) {
                                Log.e("Logout", "Transaction fehlgeschlagen für Spiel " + key, error.toException());
                            } else if (!committed) {
                                Log.w("Logout", "Transaction wurde nicht committed für Spiel " + key);
                            }
                        }
                    });
                }
            }

            // user_votes + users löschen
            userVotesRef.removeValue();
            dbRef.child("users").child(uid).removeValue()
                    .addOnCompleteListener(task -> {
                        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                        if (user != null && user.isAnonymous()) {
                            user.delete().addOnCompleteListener(deleteTask -> {
                                FirebaseAuth.getInstance().signOut();
                                clearPreferencesAndRedirect();
                            });
                        } else {
                            FirebaseAuth.getInstance().signOut();
                            clearPreferencesAndRedirect();
                        }
                    });
        }

        @Override
        public void onCancelled(@NonNull DatabaseError error) {
            Log.e("Logout", "Fehler beim Abrufen der Votes", error.toException());
            FirebaseAuth.getInstance().signOut();
            clearPreferencesAndRedirect();
        }
    });
}

    protected void handleLogout() {
    FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

    if (user != null) {
        String uid = user.getUid();
        DatabaseReference dbRef = FirebaseDatabase.getInstance().getReference();
        DatabaseReference usersRef = dbRef.child("users");

        usersRef.child(uid).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Nutzer aktuellerNutzer = snapshot.getValue(Nutzer.class);
                if (aktuellerNutzer != null && aktuellerNutzer.isHost()) {
                    usersRef.addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot allUsersSnapshot) {
                            for (DataSnapshot userSnapshot : allUsersSnapshot.getChildren()) {
                                String otherUid = userSnapshot.getKey();
                                if (!otherUid.equals(uid)) {
                                    // Alten Host zurücksetzen falls netzwerk shenanigans
                                    usersRef.child(uid).child("host").setValue(false);
                                    // Neuen Host setzen
                                    usersRef.child(otherUid).child("host").setValue(true);
                                    break;
                                }
                            }
                            proceedLogout(uid);  // Methode außerhalb aufrufen
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {
                            Log.e("Logout", "Fehler beim Suchen eines neuen Hosts", error.toException());
                            proceedLogout(uid);
                        }
                    });
                } else {
                    proceedLogout(uid);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("Logout", "Fehler beim Abrufen Nutzer-Daten", error.toException());
                proceedLogout(uid);
            }
        });
    } else {
        clearPreferencesAndRedirect();
    }
}



    //Zum auswerten des nächsten Gastgebers zur Anzeige
    protected void ladeUndZeigeGastgeber(TextView zielTextView) {
        Log.d("DEBUG", "ladeUndZeigeGastgeber wird aufgerufen");
        DatabaseReference usersRef = FirebaseDatabase.getInstance().getReference("users");
        usersRef.addValueEventListener(new ValueEventListener() { // EINGEFÜGT: Echtzeitlistener
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                for (DataSnapshot userSnapshot : snapshot.getChildren()) {
                    Nutzer nutzer = userSnapshot.getValue(Nutzer.class);
                    if (nutzer != null && nutzer.isHost()) {
                        String nextMeetingLocation = nutzer.getOrt();
                        String hostName = nutzer.getName();
                        zielTextView.setText("Ort: Bei " + hostName + ", " + nextMeetingLocation);
                        return; // Sobald ein Host gefunden wurde, abbrechen
                    }
                }

                // Falls kein Host gefunden wird
                zielTextView.setText("Ort: Noch kein Gastgeber festgelegt");
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("Firebase", "Fehler beim Laden des Gastgebers", error.toException());
                zielTextView.setText("Fehler beim Laden des Ortes");
            }
        });
    }

    // Navigation in der Toolbar
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.navigation_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.menu_haupt) {
            startActivity(new Intent(this, HauptActivity.class));
            return true;
        } else if (id == R.id.menu_rating) {
            startActivity(new Intent(this, RatingActivity.class));
            return true;
        } else if (id == R.id.menu_gruppe) {
            startActivity(new Intent(this, GruppeActivity.class));
            return true;
        } else if (id == R.id.menu_spiele) {
            startActivity(new Intent(this, SpieleActivity.class));
            return true;
        } else if (id == R.id.menu_logout) {
            handleLogout();
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }
}