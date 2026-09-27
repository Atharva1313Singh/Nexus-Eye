plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {

    namespace =
        "com.thirdeye.app"

    compileSdk =
        35

    defaultConfig {

        applicationId =
            "com.thirdeye.app"

        minSdk =
            23

        targetSdk =
            35

        versionCode =
            1

        versionName =
            "1.0"
    }

    buildTypes {

        release {

            isMinifyEnabled =
                false

            proguardFiles(
                getDefaultProguardFile(
                    "proguard-android-optimize.txt"
                ),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {

        sourceCompatibility =
            JavaVersion.VERSION_17

        targetCompatibility =
            JavaVersion.VERSION_17
    }

    buildFeatures {

        compose =
            true

        buildConfig =
            true

        aidl =
            true
    }

    androidResources {

        noCompress +=
            "tflite"
    }

    packaging {

        resources {

            excludes +=
                "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {

    implementation(
        "androidx.core:core-ktx:1.15.0"
    )

    implementation(
        "androidx.activity:activity-compose:1.10.0"
    )

    implementation(
        "androidx.lifecycle:lifecycle-runtime-compose:2.8.7"
    )

    implementation(
        "androidx.navigation:navigation-compose:2.8.5"
    )

    implementation(
        platform(
            "androidx.compose:compose-bom:2024.12.01"
        )
    )

    implementation(
        "androidx.compose.ui:ui"
    )

    implementation(
        "androidx.compose.ui:ui-tooling-preview"
    )

    implementation(
        "androidx.compose.material3:material3"
    )

    implementation(
        "org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0"
    )

    implementation(
        "com.google.android.gms:play-services-location:21.3.0"
    )


    implementation(
        "org.osmdroid:osmdroid-android:6.1.20"
    )

    implementation(
        "com.google.mlkit:face-detection:16.1.7"
    )

    implementation(
        "com.google.mlkit:image-labeling:17.0.9"
    )

    implementation(
        "com.google.mlkit:text-recognition:16.0.1"
    )

    implementation(
        "com.google.mlkit:text-recognition-devanagari:16.0.1"
    )

    implementation(
        "com.google.mlkit:text-recognition-chinese:16.0.1"
    )

    implementation(
        "com.google.mlkit:text-recognition-japanese:16.0.1"
    )

    implementation(
        "com.google.mlkit:text-recognition-korean:16.0.1"
    )

    implementation(
        "org.tensorflow:tensorflow-lite:2.17.0"
    )

    debugImplementation(
        "androidx.compose.ui:ui-tooling"
    )
}