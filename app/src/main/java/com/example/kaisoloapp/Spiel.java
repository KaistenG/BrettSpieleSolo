package com.example.kaisoloapp;

public class Spiel {
    private String id;
    private String name;
    private int votes_count;

    // Leerer Konstruktor (notwendig für Firebase)
    public Spiel() {
    }

    // Konstruktor mit allen Feldern
    public Spiel(String id, String name, int votes_count) {
        this.id = id;
        this.name = name;
        this.votes_count = votes_count;
    }

    // Getter und Setter für alle Felder

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getVotes_count() {
        return votes_count;
    }

    public void setVotes_count(int votes_count) {
        this.votes_count = votes_count;
    }
}