plugins {
    id("com.android.application")
    id("com.google.devtools.ksp")
    id("androidx.room")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jlleitschuh.gradle.ktlint")
}

android {
    namespace = "io.github.kamui2040.vectorint"
    compileSdk = 37
    buildToolsVersion = "37.0.0"

    defaultConfig {
        applicationId = "io.github.kamui2040.vectorint"
        minSdk = 23
        targetSdk = 37
        versionCode = 16
        versionName = "0.0.12"
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            optimization {
                enable = true
            }
            vcsInfo {
                include = false
            }
        }
        create("playDebug") {
            initWith(getByName("debug"))
            matchingFallbacks += listOf("debug")
        }
        create("playRelease") {
            initWith(getByName("release"))
            matchingFallbacks += listOf("release")
        }
    }

    sourceSets {
        getByName("playDebug").kotlin.srcDir("src/play/kotlin")
        getByName("playRelease").kotlin.srcDir("src/play/kotlin")
    }

    androidResources {
        generateLocaleConfig = true
        localeFilters += listOf("en", "de", "pt", "es", "it", "fr")
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.08.00")

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.appcompat:appcompat:1.8.0")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.datastore:datastore-preferences:1.2.1")
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    add("playDebugImplementation", "com.google.android.gms:play-services-mlkit-document-scanner:16.0.0")
    add("playDebugImplementation", "com.google.android.gms:play-services-mlkit-text-recognition:19.0.1")
    add("playDebugImplementation", "com.google.android.play:app-update:2.1.0")
    add("playReleaseImplementation", "com.google.android.gms:play-services-mlkit-document-scanner:16.0.0")
    add("playReleaseImplementation", "com.google.android.gms:play-services-mlkit-text-recognition:19.0.1")
    add("playReleaseImplementation", "com.google.android.play:app-update:2.1.0")

    debugImplementation("androidx.compose.ui:ui-test-manifest")
    add("playDebugImplementation", "androidx.compose.ui:ui-test-manifest")
    ksp("androidx.room:room-compiler:2.8.4")
    testImplementation(composeBom)
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16.1")
}
