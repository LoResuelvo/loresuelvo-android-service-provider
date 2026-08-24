import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    // Added in Fase 1
    // Hilt and KSP are loaded from buildscript classpath in the root
    // build.gradle.kts so we can force com.squareup:javapoet:1.13.0
    // on the plugin classpath. Both plugins are applied here with
    // `id(...)` to share the classloader. See the comment in
    // build.gradle.kts (root) for context.
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.navigation.safeargs)
    // Fix: Hilt 2.50 + KSP 2.0.21-1.0.28 incompatibility. KAPT is used
    // for the Hilt annotation processor instead of KSP, even though
    // the KSP plugin is applied. The combination works because Hilt's
    // processor runs through KAPT (kapt configuration) while KSP is
    // used for kotlinx-serialization and any future code generators.
    id("org.jetbrains.kotlin.kapt")
}

// ==========================================
// Lectura segura de variables de entorno
// Prioridad: local.properties (dev) > gradle.properties global (CI) > default
// ==========================================
val localProperties = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

fun envVar(name: String, default: String = ""): String {
    return localProperties.getProperty(name)
        ?: (project.findProperty(name) as String?)
        ?: System.getenv(name)
        ?: default
}

android {
    flavorDimensions += "environment"
    namespace = "com.loresuelvo.serviceprovider"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.loresuelvo.serviceprovider"
        minSdk = 24
        targetSdk = 35

        // Added in Fase 1: HiltTestRunner for instrumented tests with Hilt
        testInstrumentationRunner = "com.loresuelvo.serviceprovider.HiltTestRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
            all {
                it.systemProperty("cucumber.filter.tags", "not @wip")
            }
        }
    }

    productFlavors {

        create("dev") {
            dimension = "environment"
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"

            val auth0Domain = envVar("AUTH0_DOMAIN", "loresuelvo-dev.auth0.com")
            val auth0ClientId = envVar("AUTH0_CLIENT_ID")
            val auth0Scheme = envVar("AUTH0_SCHEME", "com.loresuelvo.provider")
            val auth0Audience = envVar("AUTH0_AUDIENCE", "http://localhost:8080")
            val apiUrl = envVar("API_URL", "http://10.0.2.2:8080")

            buildConfigField("String", "API_URL", "\"$apiUrl\"")
            buildConfigField("String", "AUTH0_DOMAIN", "\"$auth0Domain\"")
            buildConfigField("String", "AUTH0_CLIENT_ID", "\"$auth0ClientId\"")
            buildConfigField("String", "AUTH0_SCHEME", "\"$auth0Scheme\"")
            buildConfigField("String", "AUTH0_AUDIENCE", "\"$auth0Audience\"")

            manifestPlaceholders["auth0Domain"] = auth0Domain
            manifestPlaceholders["auth0Scheme"] = auth0Scheme
        }

        create("staging") {
            dimension = "environment"
            applicationIdSuffix = ".staging"
            versionNameSuffix = "-staging"

            val auth0Domain = envVar("AUTH0_DOMAIN_STAGING")
            val auth0ClientId = envVar("AUTH0_CLIENT_ID_STAGING")
            val auth0Scheme = envVar("AUTH0_SCHEME_STAGING", "com.loresuelvo.provider.staging")
            val auth0Audience = envVar("AUTH0_AUDIENCE_STAGING")
            val apiUrl = envVar("API_URL_STAGING")

            buildConfigField("String", "API_URL", "\"$apiUrl\"")
            buildConfigField("String", "AUTH0_DOMAIN", "\"$auth0Domain\"")
            buildConfigField("String", "AUTH0_CLIENT_ID", "\"$auth0ClientId\"")
            buildConfigField("String", "AUTH0_SCHEME", "\"$auth0Scheme\"")
            buildConfigField("String", "AUTH0_AUDIENCE", "\"$auth0Audience\"")

            manifestPlaceholders["auth0Domain"] = auth0Domain
            manifestPlaceholders["auth0Scheme"] = auth0Scheme
        }

        create("prod") {
            dimension = "environment"
            // sin suffix: este va a Play Store

            val auth0Domain = envVar("AUTH0_DOMAIN_PROD")
            val auth0ClientId = envVar("AUTH0_CLIENT_ID_PROD")
            val auth0Scheme = envVar("AUTH0_SCHEME_PROD", "com.loresuelvo.provider.prod")
            val auth0Audience = envVar("AUTH0_AUDIENCE_PROD")
            val apiUrl = envVar("API_URL_PROD")

            buildConfigField("String", "API_URL", "\"$apiUrl\"")
            buildConfigField("String", "AUTH0_DOMAIN", "\"$auth0Domain\"")
            buildConfigField("String", "AUTH0_CLIENT_ID", "\"$auth0ClientId\"")
            buildConfigField("String", "AUTH0_SCHEME", "\"$auth0Scheme\"")
            buildConfigField("String", "AUTH0_AUDIENCE", "\"$auth0Audience\"")

            manifestPlaceholders["auth0Domain"] = auth0Domain
            manifestPlaceholders["auth0Scheme"] = auth0Scheme
        }
    }
}

// ==========================================
// Validación fail-fast: solo exige las vars
// del flavor que realmente se está compilando
// ==========================================
gradle.taskGraph.whenReady {
    val runningTasks = allTasks.map { it.name }

    val requiredForStaging = listOf(
        "AUTH0_DOMAIN_STAGING", "AUTH0_CLIENT_ID_STAGING",
        "AUTH0_SCHEME_STAGING", "AUTH0_AUDIENCE_STAGING", "API_URL_STAGING"
    )
    val requiredForProd = listOf(
        "AUTH0_DOMAIN_PROD", "AUTH0_CLIENT_ID_PROD",
        "AUTH0_SCHEME_PROD", "AUTH0_AUDIENCE_PROD", "API_URL_PROD"
    )

    if (runningTasks.any { it.contains("Staging") }) {
        requiredForStaging.forEach {
            check(envVar(it).isNotBlank()) { "Falta la variable $it para build de STAGING" }
        }
    }
    if (runningTasks.any { it.contains("Prod") }) {
        requiredForProd.forEach {
            check(envVar(it).isNotBlank()) { "Falta la variable $it para build de PROD" }
        }
    }
}

dependencies {
    // Icons & Core
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // Compose BOM (Bill of Materials) - Controla las versiones de todas las librerías de Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.junit.ktx)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.security.crypto)

    // Hilt (added in Fase 1)
    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    kapt(libs.hilt.android.compiler)

    // Networking (added in Fase 1)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization.converter)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.kotlinx.serialization.json)

    // Unit Testing (Capa de Dominio - src/test)
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    // Compose UI testing in src/test/ via Robolectric.
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.compose.ui.test.manifest)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.cucumber.java)
    testImplementation(libs.cucumber.junit)

    // UI Testing (Capa de Aceptación/UI - src/androidTest)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.espresso.intents)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    // Hilt testing (added in Fase 1)
    androidTestImplementation(libs.hilt.android.testing)
    kaptAndroidTest(libs.hilt.android.compiler)

    // Debugging (Previews y Manifest para tests)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Auth0
    implementation("com.auth0.android:auth0:2.11.0")
    testImplementation(kotlin("test"))
}