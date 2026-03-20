plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.example.safefnow2"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.safefnow2"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    buildFeatures {
        compose = true
        viewBinding = true   // ← active ViewBinding pour activity_profile.xml
    }
}

dependencies {
    // ── Core ────────────────────────────────────────────────────────────────
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // ── AppCompat (nécessaire pour AppCompatActivity + thèmes XML) ──────────
    implementation("androidx.appcompat:appcompat:1.7.0")

    // ── Activity ─────────────────────────────────────────────────────────────
    implementation(libs.androidx.activity.compose)
    implementation("androidx.activity:activity-ktx:1.9.0")

    // ── Compose ──────────────────────────────────────────────────────────────
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.recyclerview)

    // ── Material (nécessaire pour Button avec cornerRadius dans XML) ─────────
    implementation("com.google.android.material:material:1.12.0")

    // ── CardView (nécessaire pour androidx.cardview.widget.CardView) ─────────
    implementation("androidx.cardview:cardview:1.0.0")

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // ── Lifecycle (lifecycleScope dans ProfileActivity) ───────────────────────
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.0")

    // ── Tests ────────────────────────────────────────────────────────────────
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}