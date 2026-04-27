plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")

    // 🔥 Firebase
    id("com.google.gms.google-services")
}

android {
    namespace = "com.saimega.vinayakacablenetwork"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.saimega.vinayakacablenetwork"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        viewBinding = true
    }
}


dependencies {

    implementation("com.google.firebase:firebase-firestore-ktx:24.10.3")

    implementation("com.google.android.material:material:1.11.0")

    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")

    implementation("androidx.cardview:cardview:1.0.0")
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)

}