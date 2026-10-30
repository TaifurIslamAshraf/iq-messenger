import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    // Reads app/google-services.json and generates the Firebase config resources.
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.iqsocial.iq_message"
    // androidx.core 1.19 / lifecycle 2.11 / activity 1.13 all require API 37 to compile against.
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.iqsocial.iq_message"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    /**
     * The key the phone already trusts.
     *
     * Android refuses to update an installed app with one signed by a different key,
     * and this app reached the test phone as a hand-signed APK rather than from
     * Gradle - so `assembleDebug` produced the generic "Android Debug" key, the phone
     * held a personal one, and installing over it failed with
     * INSTALL_FAILED_UPDATE_INCOMPATIBLE. Uninstalling first would work and would
     * also take the signed-in session and the cached chats with it.
     *
     * So the keystore is named in local.properties - which is already gitignored, and
     * is the one file in the project that is meant to hold machine-specific paths:
     *
     *     signing.storeFile=D:/app.jks
     *     signing.storePassword=...
     *     signing.keyAlias=...
     *     signing.keyPassword=...
     *
     * Forward slashes, because a backslash is an escape in a .properties file.
     * keyPassword may be left out when it matches the store's.
     *
     * Absent or pointing at nothing, this does nothing at all: debug keeps the
     * default debug key and release stays unsigned, exactly as before. That matters
     * for anyone else who checks the project out, who has neither the file nor the
     * passwords and should still be able to build.
     */
    val signingProps = Properties().apply {
        rootProject.file("local.properties")
            .takeIf { it.exists() }
            ?.inputStream()
            ?.use { load(it) }
    }
    val sharedStore = signingProps.getProperty("signing.storeFile")
        ?.takeIf { it.isNotBlank() }
        ?.let { file(it) }
        ?.takeIf { it.exists() }

    signingConfigs {
        if (sharedStore != null) {
            create("shared") {
                storeFile = sharedStore
                storePassword = signingProps.getProperty("signing.storePassword")
                keyAlias = signingProps.getProperty("signing.keyAlias")
                keyPassword = signingProps.getProperty("signing.keyPassword")
                    ?: signingProps.getProperty("signing.storePassword")
            }
        }
    }
    val shared = signingConfigs.findByName("shared")

    buildTypes {
        release {
            shared?.let { signingConfig = it }

            // On, because a release build with it off is not a release build in the
            // only sense that matters here: it is the configuration Compose is
            // measured in, and the first thing Google's own performance guide says
            // to check when an app feels slow. R8 inlines, strips and rewrites what
            // the debug build leaves standing, and Compose leans on that more than
            // most - debug composition carries bookkeeping that release does not.
            //
            // This is the project's first build with it enabled, so the first
            // release build may want keep rules for something that reaches for a
            // class by name. Nothing here parses by reflection - the models are
            // read field by field out of org.json - and Firebase, LiveKit and OkHttp
            // all ship their own rules, so there should be little to add.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }

        debug {
            // Over the top of whatever is on the phone, rather than beside it.
            shared?.let { signingConfig = it }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    // org.json ships with Android; the client's copy would duplicate it.
    implementation(libs.socket.io.client) { exclude(group = "org.json", module = "json") }
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.livekit.android)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}