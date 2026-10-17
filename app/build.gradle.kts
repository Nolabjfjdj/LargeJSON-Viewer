plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "fr.nolabjfjdj.largejsonviewer"
    compileSdk = 35

    defaultConfig {
        applicationId = "fr.nolabjfjdj.largejsonviewer"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("com.fasterxml.jackson.core:jackson-core:2.18.2")
}
