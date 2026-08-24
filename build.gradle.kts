// Top-level build file where you can add configuration options common to all sub-modules.
//
// Hilt 2.50 + Kotlin 2.0.21 + KSP 2.0.21-1.0.28 has two known conflicts:
//   1. Hilt's AggregateDepsTask throws NoSuchMethodError for
//      ClassName.canonicalName() when the plugin classpath has the
//      wrong JavaPoet. Fixed by forcing com.squareup:javapoet:1.13.0
//      on the buildscript classpath below.
//   2. Hilt and KSP need to share a classloader; declaring one in
//      buildscript and the other via plugins { } in the subproject
//      breaks. Fixed by loading both via buildscript and applying
//      both with `id(...)` in the subproject.
//
// Keep this block until Hilt 2.51+ ships a fix for the JavaPoet ABI
// break, and consider migrating KSP back to `alias(libs.plugins.ksp)`
// once Hilt stops fighting KSP.
buildscript {
    configurations.all {
        resolutionStrategy {
            force("com.squareup:javapoet:1.13.0")
        }
    }
    dependencies {
        classpath("com.google.dagger:hilt-android-gradle-plugin:2.50")
        classpath("com.google.devtools.ksp:com.google.devtools.ksp.gradle.plugin:2.0.21-1.0.28")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.navigation.safeargs) apply false
}