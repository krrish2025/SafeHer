// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
<<<<<<< HEAD
    id("com.google.gms.google-services") version "4.4.2" apply false
}
=======
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.navigation.safeargs) apply false
}
>>>>>>> 3e7c211b67d978360a912324f7cb5173604ee75c
