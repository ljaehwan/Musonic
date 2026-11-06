plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "app.musonic"
    compileSdk = 36
    bundle { language { enableSplit = false } }
    defaultConfig {
        applicationId = "app.musonic"
        minSdk = 26
        targetSdk = 36
        versionCode = providers.gradleProperty("musonicVersionCode").orNull?.toInt() ?: 19
        versionName = providers.gradleProperty("musonicVersionName").orNull ?: "0.4.1"
        buildConfigField("boolean", "ALLOW_HTTP", "true")
        manifestPlaceholders["allowCleartext"] = "true"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    signingConfigs {
        create("playUpload") {
            providers.environmentVariable("MUSONIC_UPLOAD_STORE").orNull?.let {
                storeFile = file(it)
                storePassword = providers.environmentVariable("MUSONIC_UPLOAD_STORE_PASSWORD").get()
                keyAlias = "upload"
                keyPassword = providers.environmentVariable("MUSONIC_UPLOAD_KEY_PASSWORD").get()
            }
        }

    }
    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        create("play") {
            initWith(getByName("release"))
            signingConfig = if (providers.environmentVariable("MUSONIC_UPLOAD_STORE").isPresent) signingConfigs.getByName("playUpload") else null
            buildConfigField("boolean", "ALLOW_HTTP", "false")
            manifestPlaceholders["allowCleartext"] = "false"
            matchingFallbacks += listOf("release")
        }
    }
    sourceSets {
        getByName("debug") { java.srcDir("src/direct/java"); manifest.srcFile("src/direct/AndroidManifest.xml") }
        getByName("release") { java.srcDir("src/direct/java"); manifest.srcFile("src/direct/AndroidManifest.xml") }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.04.01"))
    debugImplementation("androidx.work:work-runtime-ktx:2.10.1")
    releaseImplementation("androidx.work:work-runtime-ktx:2.10.1")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.media3:media3-exoplayer:1.6.1")
    implementation("androidx.media3:media3-session:1.6.1")
    implementation("androidx.media3:media3-datasource-okhttp:1.6.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.json:json:20240303")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.04.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
