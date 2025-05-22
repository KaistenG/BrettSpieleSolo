package com.example.kaisoloapp;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import android.Manifest;
import android.content.pm.PackageManager;
import android.telephony.SmsManager;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;


import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.ValueEventListener;

public class HauptActivity extends BaseActivity {

    private TextView textViewDate;
    private TextView textViewTime;
    private TextView textViewLocation;
    private TextView textViewCountdown;
    private TextView textViewReminder;
    private TextView textViewWelcome;

    private Button btnBewertungStarten;

    private ValueEventListener hostStatusListener; // ✅ NEU: Für Live-Update des Host-Status


    // Firebase Listener Referenz für Nutzerdaten (damit man Listener später ggf. entfernen kann)
    private ValueEventListener userLocationListener; //EINGEFÜGT
    private DatabaseReference userRef; //EINGEFÜGT

    // Datum nächstes Treffen
    private Calendar nextMeetingDate = Calendar.getInstance(TimeZone.getTimeZone("Europe/Berlin"));



    private static final int SMS_PERMISSION_CODE = 123;

    // Handler und Runnable zum automatischen aktualisieren
    private final android.os.Handler handler = new android.os.Handler();
    private final Runnable updateRunnable = new Runnable() {
        @Override
        public void run() {
            setNextMeetingDetails(); // Aktualisiere UI
            handler.postDelayed(this, 60 * 1000); // alle 60 Sekunden wiederholen
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_haupt);

        // Initialisiere die TextViews
        textViewDate = findViewById(R.id.textViewDate);
        textViewTime = findViewById(R.id.textViewTime);
        textViewLocation = findViewById(R.id.textViewLocation);
        textViewCountdown = findViewById(R.id.textViewCountdown);
        textViewReminder = findViewById(R.id.textViewReminder);
        textViewWelcome = findViewById(R.id.textViewWelcome);

        //Initalisiere den Eventabschlussbutton
        btnBewertungStarten = findViewById(R.id.btnBewertungStarten);

        // Initialisieren der Toolbar
        Toolbar toolbar = findViewById(R.id.navigationToolbar);
        setSupportActionBar(toolbar);

        // Initialisieren des AddMeeting Buttons
        Button buttonSetMeeting = findViewById(R.id.buttonAddMeeting);
        buttonSetMeeting.setVisibility(View.GONE);  // ÄNDERUNG: Button erstmal verstecken

        //Automatisches aktualisieren der UI
        handler.post(updateRunnable);

        // Button für SMS-Versand; löst Methode zum Senden aus, wenn Berechtigung da ist
        Button smsButton = findViewById(R.id.btn_sms);
        smsButton.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.SEND_SMS}, SMS_PERMISSION_CODE);
            } else {
                sendeSmsAnAlleTeilnehmer();
            }
        });

        // Holen des aktuellen Benutzers
        FirebaseUser userWelcome = FirebaseAuth.getInstance().getCurrentUser();

        if (userWelcome != null) {
            String userId = userWelcome.getUid();
            userRef = FirebaseDatabase.getInstance().getReference("users").child(userId); //EINGEFÜGT

            // Hole den Benutzernamen aus der Firebase Realtime Database
            userRef.child("name").addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    String userName = dataSnapshot.getValue(String.class);
                    Log.d("Firebase", "Benutzername aus Firebase: " + userName);
                    if (userName != null) {
                        textViewWelcome.setText("Hallo, " + userName);
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Log.e("Firebase", "Fehler beim Abrufen des Benutzernamens", error.toException());
                }
            });

            // Prüfen, ob Nutzer Host ist, um buttonSetMeeting sichtbar zu machen und OnClickListener zu setzen
            hostStatusListener = new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    Nutzer aktuellerNutzer = snapshot.getValue(Nutzer.class);
                    if (aktuellerNutzer != null && aktuellerNutzer.isHost()) {
                        buttonSetMeeting.setVisibility(View.VISIBLE);
                        buttonSetMeeting.setOnClickListener(v -> meetingDateTimePicker());

                        btnBewertungStarten.setVisibility(View.VISIBLE);
                        btnBewertungStarten.setOnClickListener(v -> {
                            setzeNaechstenHostUndErstelleEvent(snapshot.getKey(), null);
                        });
                    } else {
                        buttonSetMeeting.setVisibility(View.GONE);
                        btnBewertungStarten.setVisibility(View.GONE);
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Log.e("Firebase", "Host-Status konnte nicht gelesen werden", error.toException());
                }
            };
            userRef.addValueEventListener(hostStatusListener); // ÄNDERUNG Ende

            //Listener für den Ort des Nutzers hinzufügen
            userLocationListener = new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    // Ort des aktuellen Nutzers lesen und anzeigen
                    String ort = snapshot.child("ort").getValue(String.class);
                    if (ort != null) {
                        textViewLocation.setText("Ort: " + ort); //Echtzeit Aktualisierung des Ortes
                    } else {
                        textViewLocation.setText("Ort: Nicht verfügbar");
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Log.e("Firebase", "Fehler beim Lesen des Orts", error.toException());
                }
            };
            userRef.addValueEventListener(userLocationListener);
        }

        DatabaseReference globalMeetingRef = FirebaseDatabase.getInstance().getReference("meeting");
        globalMeetingRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Long timestamp = snapshot.child("timestamp").getValue(Long.class);
                if (timestamp != null) {
                    Calendar globalMeeting = Calendar.getInstance(TimeZone.getTimeZone("Europe/Berlin"));
                    globalMeeting.setTimeInMillis(timestamp);
                    updateMeetingDetails(globalMeeting);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("Firebase", "Fehler beim Laden des globalen Termins", error.toException());
            }
        });

        setupSystemBarInsets(findViewById(R.id.hauptLayout));

        setNextMeetingDetails();
    }

    // Listener entfernen um Memory Leaks zu vermeiden
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (userRef != null && userLocationListener != null) {
            userRef.removeEventListener(userLocationListener);
            userRef.removeEventListener(hostStatusListener);
        }
        handler.removeCallbacks(updateRunnable);
    }

    private void updateMeetingDetails(Calendar newMeetingDate) {
        newMeetingDate.setTimeZone(TimeZone.getTimeZone("Europe/Berlin"));

        nextMeetingDate = newMeetingDate;
        setNextMeetingDetails();

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            DatabaseReference globalMeetingRef = FirebaseDatabase.getInstance().getReference("meeting");

            SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy", Locale.GERMAN);
            dateFormat.setTimeZone(TimeZone.getTimeZone("Europe/Berlin"));
            SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.GERMAN);
            timeFormat.setTimeZone(TimeZone.getTimeZone("Europe/Berlin"));
            String formattedDate = dateFormat.format(newMeetingDate.getTime());
            String formattedTime = timeFormat.format(newMeetingDate.getTime());

            globalMeetingRef.runTransaction(new com.google.firebase.database.Transaction.Handler() {
                @NonNull
                @Override
                public com.google.firebase.database.Transaction.Result doTransaction(@NonNull MutableData currentData) {
                    //Immer überschreiben, keine Prüfung mehr
                    currentData.child("timestamp").setValue(newMeetingDate.getTimeInMillis());
                    currentData.child("date").setValue(formattedDate);
                    currentData.child("time").setValue(formattedTime);
                    //currentData.child("location").setValue("Bei Richard");
                    // currentData.child("created_by").setValue(user.getUid());
                    return com.google.firebase.database.Transaction.success(currentData);
                }

                @Override
                public void onComplete(@Nullable DatabaseError error, boolean committed, @Nullable DataSnapshot currentData) {
                    if (committed) {
                        Log.d("MainActivity", "Meeting erfolgreich aktualisiert (Transaction).");
                    } else {
                        if (error != null) {
                            Log.e("MainActivity", "Transaction fehlgeschlagen: " + error.getMessage());
                        } else {
                            Log.d("MainActivity", "Transaction abgebrochen.");
                        }
                    }
                }
            });
        }
    }

    // Speichern und Countdown
    private void setNextMeetingDetails() {
        // Datum heute
        Calendar dateToday = Calendar.getInstance(TimeZone.getTimeZone("Europe/Berlin"));
        long millisUntilMeeting = nextMeetingDate.getTimeInMillis() - dateToday.getTimeInMillis();


        if (millisUntilMeeting > 0) {
            long seconds = millisUntilMeeting / 1000;
            long minutes = (seconds / 60) % 60;
            long hours = (seconds / 3600) % 24;
            long days = seconds / (3600 * 24);

            // richtiges anzeigen von Tag und Tage
            String daySingle = (days == 1) ? "Tag" : "Tage";
            String hourSingle = (hours == 1) ? "Stunde" : "Stunden";
            String minuteSingle = (minutes == 1) ? "Minute" : "Minuten";

            String countdown = "Noch " + days + " " + daySingle + ", " + hours + " " + hourSingle + ", " + minutes + " " + minuteSingle;
            textViewCountdown.setText(countdown);

            //reminder der nur angezeigt wird, wenn es weniger als 3 Tage sind
            if (days < 3) {
                textViewReminder.setText("Erinnerung: Das nächste Treffen ist bald!");
                textViewReminder.setVisibility(View.VISIBLE);
            } else {
                textViewReminder.setText("");
                textViewReminder.setVisibility(View.GONE);
            }

            btnBewertungStarten.setVisibility(View.GONE); //Button verstecken, da Termin noch nicht vorbei

        } else {
            textViewCountdown.setText("Das Treffen hat bereits stattgefunden.");
            textViewReminder.setVisibility(View.GONE);

            //Prüfen, ob aktueller Nutzer Host ist und Button anzeigen + Klicklistener setzen
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user != null) {
                String uid = user.getUid();
                DatabaseReference usersRef = FirebaseDatabase.getInstance().getReference("users");

                usersRef.child(uid).addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        Nutzer aktuellerNutzer = snapshot.getValue(Nutzer.class);
                        if (aktuellerNutzer != null && aktuellerNutzer.isHost()) {
                            btnBewertungStarten.setVisibility(View.VISIBLE);
                            btnBewertungStarten.setOnClickListener(v -> {
                                setzeNaechstenHostUndErstelleEvent(uid, null);
                            });
                        } else {
                            btnBewertungStarten.setVisibility(View.GONE);
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e("HOST", "Fehler beim Abrufen des Nutzerstatus", error.toException());
                        btnBewertungStarten.setVisibility(View.GONE);
                    }
                });
            } else {
                btnBewertungStarten.setVisibility(View.GONE);
            }
        }

        // dynamisch formatiertes Datum
        SimpleDateFormat sdf = new SimpleDateFormat("dd. MMMM yyyy", Locale.GERMAN);
        String sdfDate = sdf.format(nextMeetingDate.getTime());
        textViewDate.setText("Datum: " + sdfDate);

        // dynamisch formatierte Uhrzeit
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm 'Uhr'", Locale.GERMAN);
        timeFormat.setTimeZone(TimeZone.getTimeZone("Europe/Berlin")); // Zeitzone auf Berlin setzen
        String sdfTime = timeFormat.format(nextMeetingDate.getTime());
        textViewTime.setText("Uhrzeit: " + sdfTime);

        Log.d("DEBUG", "Aufruf ladeUndZeigeGastgeber");
        ladeUndZeigeGastgeber(textViewLocation);

    }


    private void meetingDateTimePicker() {
        Calendar calendar = Calendar.getInstance();

        // 1. DatePickerDialog öffnen
        DatePickerDialog datePickerDialog = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    // Monat in Calendar beginnt bei 0
                    calendar.set(Calendar.YEAR, year);
                    calendar.set(Calendar.MONTH, month);
                    calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);

                    // 2. TimePickerDialog öffnen
                    TimePickerDialog timePickerDialog = new TimePickerDialog(
                            this,
                            (timeView, hourOfDay, minute) -> {
                                calendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
                                calendar.set(Calendar.MINUTE, minute);

                                // Speicher das neue Datum und aktualisiere Anzeige
                                updateMeetingDetails(calendar);
                            },
                            calendar.get(Calendar.HOUR_OF_DAY),
                            calendar.get(Calendar.MINUTE),
                            true
                    );
                    timePickerDialog.show();
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
        );

        datePickerDialog.show();
    }

    // SMS an alle Telefonnummern in Firebase
    private void sendeSmsAnAlleTeilnehmer() {
        DatabaseReference usersRef = FirebaseDatabase.getInstance().getReference("users");

        usersRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                SmsManager smsManager = SmsManager.getDefault();
                int gesendet = 0;

                for (DataSnapshot userSnapshot : snapshot.getChildren()) {
                    String handynummer = userSnapshot.child("handynummer").getValue(String.class);
                    if (handynummer != null && !handynummer.isEmpty()) {
                        smsManager.sendTextMessage(handynummer, null, getString(R.string.sms_text_verspaetung), null, null);
                        gesendet++;
                    }
                }

                Toast.makeText(HauptActivity.this, "SMS an " + gesendet + " Teilnehmer gesendet", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(HauptActivity.this, "Fehler beim Laden der Telefonnummern", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // Ergebnis der Berechtigungsanfrage für SMS
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == SMS_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                sendeSmsAnAlleTeilnehmer();
            } else {
                Toast.makeText(this, "SMS-Berechtigung abgelehnt", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
