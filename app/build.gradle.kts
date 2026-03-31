plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    // id("kotlin-kapt") // REMOVED
    // id("com.google.dagger.hilt.android") // Manual DI requested, skipping Hilt
}

android {
    namespace = "com.serkantken.secuasist"
    compileSdk = 35 // Stuck to stable for now

    defaultConfig {
        applicationId = "com.serkantken.secuasist"
        minSdk = 26 // Compose requires min 21, but 26 is safer for modern APIs
        targetSdk = 35
        versionCode = 1
        versionName = "0.7.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
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
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        viewBinding = false // No need for ViewBinding anymore
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    
    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // Lifecycle Service (For Compose WindowManager)
    implementation("androidx.lifecycle:lifecycle-service:2.8.7")

    // Navigation
    implementation(libs.androidx.navigation.compose)
    
    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    
    // Networking
    implementation(libs.okhttp)
    implementation(libs.gson)
    
    // Image Loading
    implementation("io.coil-kt:coil-compose:2.6.0")
    
    // ML Kit
    implementation(libs.mlkit.text.recognition)

    // Icons
    implementation(libs.androidx.material.icons.extended)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}