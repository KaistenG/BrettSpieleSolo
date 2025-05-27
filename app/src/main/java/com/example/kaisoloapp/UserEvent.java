package com.example.kaisoloapp;

public class UserEvent {
    public String host_name;
    public String date;
    public String timestamp;  // für die ID
    public boolean userHasRated = false;
    public float averageRating = 0;

    public UserEvent() {}

    public UserEvent(String host_name, String date, String timestamp) {
        this.host_name = host_name;
        this.date = date;
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return host_name + " am " + date;
    }
}