package com.example.kaisoloapp;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Log;

import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.*;

import java.util.List;

import android.app.AlertDialog;

public class SpieleAdapter extends RecyclerView.Adapter<SpieleAdapter.ViewHolder> {

    private List<Spiel> spieleList;
    private FirebaseUser currentUser;
    private DatabaseReference userVotesRef;
    private DatabaseReference spieleRef;

    public SpieleAdapter(List<Spiel> spieleList, DatabaseReference spieleRef, DatabaseReference userVotesRef, FirebaseUser currentUser) {
        this.spieleList = spieleList;
        this.spieleRef = spieleRef;
        this.userVotesRef = userVotesRef;
        this.currentUser = currentUser;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.spiel_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        Spiel spiel = spieleList.get(position);
        holder.spielName.setText(spiel.getName());

        // Stimmenanzahl für das Spiel anzeigen
        spieleRef.child(spiel.getId()).child("votes_count").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                int votes = snapshot.exists() ? snapshot.getValue(Integer.class) : 0;
                holder.voteCount.setText("Votes: " + votes);
            }

            @Override
            public void onCancelled(DatabaseError error) {}
        });

        // Vorherigen Listener entfernen
        holder.voteCheckBox.setOnCheckedChangeListener(null);

        // Checkbox-Zustand laden
        userVotesRef.child(currentUser.getUid()).child(spiel.getId())
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        boolean isChecked = snapshot.exists() && Boolean.TRUE.equals(snapshot.getValue(Boolean.class));
                        holder.voteCheckBox.setChecked(isChecked);

                        // Neuen Listener setzen
                        holder.voteCheckBox.setOnCheckedChangeListener((buttonView, checked) -> {
                            DatabaseReference votesLeftRef = userVotesRef.child(currentUser.getUid()).child("votes_left");

                            if (checked && !isChecked) {
                                // Stimmentransaktion für Hinzufügen
                                votesLeftRef.runTransaction(new Transaction.Handler() {
                                    @Override
                                    public Transaction.Result doTransaction(MutableData currentData) {
                                        Integer votesLeft = currentData.getValue(Integer.class);
                                        if (votesLeft == null) votesLeft = 3;

                                        if (votesLeft > 0) {
                                            currentData.setValue(votesLeft - 1);
                                            return Transaction.success(currentData);
                                        } else {
                                            return Transaction.abort();
                                        }
                                    }

                                    @Override
                                    public void onComplete(DatabaseError error, boolean committed, DataSnapshot snapshot) {
                                        if (committed) {
                                            // Spiel upvoten
                                            updateSpielVote(spiel.getId(), 1);
                                            userVotesRef.child(currentUser.getUid()).child(spiel.getId()).setValue(true);
                                        } else {
                                            holder.voteCheckBox.setChecked(false);
                                            Toast.makeText(buttonView.getContext(), "Keine Stimmen mehr übrig!", Toast.LENGTH_SHORT).show();
                                        }
                                    }
                                });
                            }

                            if (!checked && isChecked) {
                                // Stimmentransaktion für Rücknahme
                                votesLeftRef.runTransaction(new Transaction.Handler() {
                                    @Override
                                    public Transaction.Result doTransaction(MutableData currentData) {
                                        Integer votesLeft = currentData.getValue(Integer.class);
                                        if (votesLeft == null) votesLeft = 3;
                                        currentData.setValue(votesLeft + 1);
                                        return Transaction.success(currentData);
                                    }

                                    @Override
                                    public void onComplete(DatabaseError error, boolean committed, DataSnapshot snapshot) {
                                        if (committed) {
                                            updateSpielVote(spiel.getId(), -1);
                                            userVotesRef.child(currentUser.getUid()).child(spiel.getId()).removeValue();
                                        }
                                    }
                                });
                            }
                        });
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {}
                });
        // 🗑️ Spiel löschen
        holder.btnDelete.setOnClickListener(v -> {
            new AlertDialog.Builder(v.getContext())
                    .setTitle("Spiel löschen")
                    .setMessage("Möchtest du \"" + spiel.getName() + "\" wirklich löschen?")
                    .setPositiveButton("Löschen", (dialog, which) -> {
                        spieleRef.child(spiel.getId()).removeValue()
                                .addOnSuccessListener(aVoid ->
                                        Toast.makeText(v.getContext(), "Spiel gelöscht", Toast.LENGTH_SHORT).show())
                                .addOnFailureListener(e ->
                                        Toast.makeText(v.getContext(), "Fehler beim Löschen", Toast.LENGTH_SHORT).show());
                    })
                    .setNegativeButton("Abbrechen", null)
                    .show();
        });

        switch (spiel.getName().toLowerCase()) {
            case "cluedo":
                holder.spielBild.setImageResource(R.drawable.cluedo);
                break;
            case "monopoly":
                holder.spielBild.setImageResource(R.drawable.monopoly);
                break;
            case "uno":
                holder.spielBild.setImageResource(R.drawable.uno);
                break;
            case "scrabble":
                holder.spielBild.setImageResource(R.drawable.scrabble);
                break;
            default:
                holder.spielBild.setImageResource(R.drawable.placeholder);
                break;
        }
    }

    @Override
    public int getItemCount() {
        return spieleList.size();
    }

    private void updateSpielVote(String spielId, int delta) {
        spieleRef.child(spielId).child("votes_count").runTransaction(new Transaction.Handler() {
            @Override
            public Transaction.Result doTransaction(MutableData currentData) {
                Integer currentVotes = currentData.getValue(Integer.class);
                if (currentVotes == null) currentVotes = 0;
                currentData.setValue(currentVotes + delta);
                return Transaction.success(currentData);
            }

            @Override
            public void onComplete(DatabaseError error, boolean committed, DataSnapshot snapshot) {}
        });
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        TextView spielName, voteCount;
        CheckBox voteCheckBox;
        ImageButton btnDelete;
        ImageView spielBild;

        public ViewHolder(View itemView) {
            super(itemView);
            spielName = itemView.findViewById(R.id.spielName);
            voteCount = itemView.findViewById(R.id.voteCount);
            voteCheckBox = itemView.findViewById(R.id.checkBoxVote);
            btnDelete = itemView.findViewById(R.id.btnDelete);
            spielBild = itemView.findViewById(R.id.spielBild);
        }
    }
}
