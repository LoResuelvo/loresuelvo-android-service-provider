// Top-level build file where you can add configuration options common to all sub-modules.
//
// Fase 0 (setup): solo Compose + JVM testing. Sin Hilt, sin Retrofit, sin Auth0.
// Esas dependencias se agregan en Fase 1 junto con el walking skeleton BDD/TDD.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
