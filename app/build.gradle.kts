plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

import java.util.Properties

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

        val localProperties = Properties()
        rootProject.file("local.properties").takeIf { it.exists() }?.reader()?.use { localProperties.load(it) }
        val sosBackendUrl: String =
            localProperties.getProperty("SOS_BACKEND_URL")?.trim()?.takeIf { it.isNotEmpty() }
                ?: "http://10.0.2.2:8080/"
        buildConfigField("String", "SOS_BACKEND_URL", "\"${sosBackendUrl.replace("\"", "\\\"")}\"")
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
        buildConfig = true
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
    implementation(libs.firebase.database)
    ksp(libs.androidx.room.compiler)
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // ── Lifecycle (lifecycleScope dans ProfileActivity) ───────────────────────
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.0")

    implementation(platform("com.google.firebase:firebase-bom:34.12.0"))
    implementation("com.google.firebase:firebase-messaging")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-analytics")
    implementation(libs.kotlinx.coroutines.play.services)

    debugImplementation(libs.androidx.compose.ui.tooling)

}

tasks.withType<Test>().configureEach {
    enabled = false
}

// Only when google-services.json is present (download from Firebase Console into app/).
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}