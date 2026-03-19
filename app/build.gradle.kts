plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

val mapKitApiKey = providers.gradleProperty("MAPKIT_API_KEY").orElse("")
val recommendationBaseUrl = providers.gradleProperty("RECOMMENDATION_BASE_URL").orElse("")

android {
    namespace = "com.example.map"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.map"
        minSdk = 25
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "MAPKIT_API_KEY", "\"${mapKitApiKey.get()}\"")
        buildConfigField("String", "RECOMMENDATION_BASE_URL", "\"${recommendationBaseUrl.get()}\"")
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
        buildConfig = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp.logging)
    implementation ("com.yandex.div:div:30.0.0")
    implementation("com.yandex.div:div-core:30.0.0")
    implementation("com.yandex.div:div-json:30.0.0")
    implementation ("com.yandex.div:coil:30.0.0")
    implementation("com.yandex.android:maps.mobile:4.5.0-lite")
}
