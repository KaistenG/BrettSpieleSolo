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

import java.util.HashMap;
import java.util.Map;

// ACTIVITY ZUM METHODEN AUSLAGERN
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

    //METHODE DIE DEN NUTZER DANN TATSÄCHLICH AUSLOGGT (Wird innerhalb der handelLogout Methode aufgerufen)
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
                        //Transaktion, damit alle Änderungen atomar sind (falls mehrere Nutzer gleichzeitig voten)
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

    //Methode beim Logout (Prüfen ob Host
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
                // Setzen eines neuen Hosts, falls der ausgeloggte Nutzer Host ist.
                if (aktuellerNutzer != null && aktuellerNutzer.isHost()) {
                    usersRef.addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot allUsersSnapshot) {
                            String newHostUid = null;
                            long minCreatedAt = Long.MAX_VALUE;

                            for (DataSnapshot userSnapshot : allUsersSnapshot.getChildren()) {
                                String otherUid = userSnapshot.getKey();
                                if (otherUid.equals(uid)) continue; // ausgeloggter Nutzer überspringen

                                Nutzer otherUser = userSnapshot.getValue(Nutzer.class);
                                if (otherUser != null && otherUser.getCreatedAt() < minCreatedAt) {
                                    minCreatedAt = otherUser.getCreatedAt();
                                    newHostUid = otherUid;
                                }
                            }

                            // Alten Host zurücksetzen
                            usersRef.child(uid).child("host").setValue(false);

                            if (newHostUid != null) {
                                // Neuen Host setzen (nächster in createdAt Reihenfolge)
                                usersRef.child(newHostUid).child("host").setValue(true)
                                        .addOnCompleteListener(task -> {
                                            // Nach Setzen des Hosts normal ausloggen
                                            proceedLogout(uid);
                                        });
                            } else {
                                // Kein anderer Nutzer gefunden -> einfach logout
                                proceedLogout(uid);
                            }
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {
                            Log.e("Logout", "Fehler beim Suchen eines neuen Hosts", error.toException());
                            proceedLogout(uid);
                        }
                    });
                } else {
                    // Wenn aktueller Nutzer kein Host ist, einfach logout
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
        usersRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Nutzer aeltesterHost = null;
                long earliestTimestamp = Long.MAX_VALUE;

                for (DataSnapshot userSnapshot : snapshot.getChildren()) {
                    Nutzer nutzer = userSnapshot.getValue(Nutzer.class);
                    if (nutzer != null && nutzer.isHost()) {
                        // Vergleiche createdAt, falls vorhanden
                        if (nutzer.getCreatedAt() != null && nutzer.getCreatedAt() < earliestTimestamp) {
                            earliestTimestamp = nutzer.getCreatedAt();
                            aeltesterHost = nutzer;
                        }
                    }
                }

                if (aeltesterHost != null) {
                    String nextMeetingLocation = aeltesterHost.getOrt();
                    String hostName = aeltesterHost.getName();
                    zielTextView.setText("Ort: Bei " + hostName + ", " + nextMeetingLocation);
                } else {
                    zielTextView.setText("Ort: Noch kein Gastgeber festgelegt");
                }
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
    // Navigation innerhalb der toolbar, zwischen den Activites
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


   // Methode zum Setzen des nächsten Hosts + Erstellen eines neuen Events
   protected void setzeNaechstenHostUndErstelleEvent(String aktuellerHostUid, Runnable onSuccess) {
       DatabaseReference usersRef = FirebaseDatabase.getInstance().getReference("users");

       usersRef.addListenerForSingleValueEvent(new ValueEventListener() {
           @Override
           public void onDataChange(@NonNull DataSnapshot allUsersSnapshot) {
               Nutzer aktuellerNutzer = allUsersSnapshot.child(aktuellerHostUid).getValue(Nutzer.class);
               if (aktuellerNutzer == null || aktuellerNutzer.getCreatedAt() == null) return;

               long currentHostTime = aktuellerNutzer.getCreatedAt();
               String nextHostUid = null;
               long minTimestamp = Long.MAX_VALUE;

               String fallbackHostUid = null;
               long earliestTimestamp = Long.MAX_VALUE;

               for (DataSnapshot userSnapshot : allUsersSnapshot.getChildren()) {
                   Nutzer nutzer = userSnapshot.getValue(Nutzer.class);
                   String otherUid = userSnapshot.getKey();
                   if (nutzer == null || nutzer.getCreatedAt() == null || otherUid == null) continue;

                   long userTime = nutzer.getCreatedAt();

                   // Nächster Host-Kandidat mit größerem Timestamp als der aktuelle
                   if (!otherUid.equals(aktuellerHostUid) && userTime > currentHostTime && userTime < minTimestamp) {
                       nextHostUid = otherUid;
                       minTimestamp = userTime;
                   }

                   // Fallback: frühester Nutzer (unabhängig vom aktuellen Host)
                   if (!otherUid.equals(aktuellerHostUid) && userTime < earliestTimestamp) {
                       fallbackHostUid = otherUid;
                       earliestTimestamp = userTime;
                   }
               }

               // Wenn kein nächster Host mit größerem Timestamp, dann nimm den frühesten Nutzer
               if (nextHostUid == null) {
                   nextHostUid = fallbackHostUid;
               }

               if (nextHostUid != null) {
                   usersRef.child(aktuellerHostUid).child("host").setValue(false);
                   usersRef.child(nextHostUid).child("host").setValue(true);

                   DatabaseReference eventRef = FirebaseDatabase.getInstance().getReference("events");
                   long eventTimestamp = System.currentTimeMillis();
                   Map<String, Object> event = new HashMap<>();
                   event.put("created_by", aktuellerHostUid);
                   event.put("timestamp", eventTimestamp);
                   // event.put("status", "pending"); // Optional
                   eventRef.child(String.valueOf(eventTimestamp)).setValue(event);

                   if (onSuccess != null) onSuccess.run();
               }
           }

           @Override
           public void onCancelled(@NonNull DatabaseError error) {
               Log.e("HOST", "Fehler beim Setzen des neuen Hosts", error.toException());
           }
       });
   }

   // Methode zum Anzeigen des höchstgevoteten Spiels auf der HauptActivity
   protected void zeigeSpielMitMeistenVotes(final TextView textViewTopGame) {
       DatabaseReference spieleRef = FirebaseDatabase.getInstance().getReference("spiele");

       ValueEventListener topGameListener = new ValueEventListener() {
           @Override
           public void onDataChange(@NonNull DataSnapshot snapshot) {
               String topSpielName = "Noch keine Spiele vorhanden";
               int maxVotes = -1;

               for (DataSnapshot spielSnapshot : snapshot.getChildren()) {
                   String name = spielSnapshot.child("name").getValue(String.class);
                   Integer votes = spielSnapshot.child("votes_count").getValue(Integer.class);

                   if (name != null && votes != null && votes > maxVotes) {
                       maxVotes = votes;
                       topSpielName = name + " (" + votes + " Stimmen)";
                   }
               }

               textViewTopGame.setText("Top-Spiel: " + topSpielName);
           }

           @Override
           public void onCancelled(@NonNull DatabaseError error) {
               textViewTopGame.setText("Fehler beim Laden des Top-Spiels");
               Log.e("Firebase", "Fehler beim Laden der Spiele", error.toException());
           }
       };

       spieleRef.addValueEventListener(topGameListener);
   }
}