buildscript {
    repositories {
        google()  // Hiermit werden Firebase-Dienste geladen
        mavenCentral()
    }
    dependencies {
        classpath("com.google.gms:google-services:4.4.2") // Firebase Google Services Plugin
    }
}
// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.androidApplication) apply false
}