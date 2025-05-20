package com.example.kaisoloapp;

public class Nutzer {
    private String name;
    private String ort;

    private String handynummer;
    private boolean host;
    private long createdAt;

    public Nutzer() {
    }

    public Nutzer(String name, String ort, String handynummer, boolean host, Long createdAt) {
        this.name = name;
        this.ort = ort;
        this.handynummer = handynummer;
        this.host = host;
        this.createdAt = createdAt;
    }


    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getOrt() {
        return ort;
    }

    public void setOrt(String ort) {
        this.ort = ort;
    }

    public String getHandynummer() {
        return handynummer;
    }

    public void setHandynummer(String handynummer) {
        this.handynummer = handynummer;
    }

    //Zum bestimmen des nächsten Gastgebers
    public boolean isHost() {
        return host;
    }

    public void setHost(boolean host) {
        this.host = host;
    }

    //Zeitstempel zum Auswählen des nächsten Hosts
    public Long getCreatedAt() { //ÄNDERUNG
        return createdAt;
    }

    public void setCreatedAt(Long createdAt) { //ÄNDERUNG
        this.createdAt = createdAt;
    }
}