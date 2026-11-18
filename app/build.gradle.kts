plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.micmax.cibo"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.micmax.cibo"
        minSdk = 26
        targetSdk = 34
        versionCode = System.getenv("CIBO_VERSION_CODE")?.toIntOrNull() ?: 1
        versionName = "1.3.0"
        resourceConfigurations += listOf("it", "en")
    }

    // Firma fissa: la chiave arriva dai secret di GitHub (CIBO_KEYSTORE_BASE64, CIBO_KEYSTORE_PASSWORD).
    val ksFile = System.getenv("CIBO_KEYSTORE_FILE")?.let { file(it) }?.takeIf { it.exists() }
    val ksPass = System.getenv("CIBO_KEYSTORE_PASSWORD")
    signingConfigs {
        if (ksFile != null && !ksPass.isNullOrEmpty()) create("fixed") {
            storeFile = ksFile
            storePassword = ksPass
            keyAlias = "weblauncher"
            keyPassword = ksPass
        }
    }
    val fixedSigning = signingConfigs.findByName("fixed")

    buildTypes {
        release {
            if (fixedSigning != null) signingConfig = fixedSigning
            isMinifyEnabled = false
        }
        debug {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (fixedSigning != null) signingConfig = fixedSigning
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.activity:activity-ktx:1.8.2")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("androidx.health.connect:connect-client:1.1.0-alpha07")   // passi, sonno e allenamenti dell'orologio (solo lettura)
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")   // codice a barre
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20231013")
}
