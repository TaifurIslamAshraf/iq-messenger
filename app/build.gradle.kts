import io.sentry.android.gradle.extensions.InstrumentationFeature
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    // Reads app/google-services.json and generates the Firebase config resources.
    alias(libs.plugins.google.services)
    // Generates the Room DAOs. The only code generator in the project, and it earns
    // its place: the alternative is hand-written SQL for every read and write.
    alias(libs.plugins.ksp)
    // Crash and performance reporting. The plugin also adds the SDK itself, so
    // there is no sentry-android line among the dependencies below.
    alias(libs.plugins.sentry.android)
}

/**
 * Sentry's settings, read from the environment or from local.properties.
 *
 * The same file the signing config below uses, for the same reason: it is already
 * gitignored and already meant for what belongs to one machine. Nothing here is
 * written into the repository.
 *
 *     sentry.dsn=https://...@o....ingest.sentry.io/...
 *     sentry.org=your-org-slug
 *     sentry.project=your-project-slug
 *
 * and the auth token in the environment as SENTRY_AUTH_TOKEN, which is where the
 * Sentry docs say it belongs - or as sentry.authToken in local.properties.
 *
 * Every one of them may be missing. Without a DSN the SDK starts disabled and
 * sends nothing; without a token the mapping file is not uploaded. Somebody
 * checking the project out builds exactly as before.
 */
val sentryLocal = Properties().apply {
    rootProject.file("local.properties")
        .takeIf { it.exists() }
        ?.inputStream()
        ?.use { load(it) }
}

fun sentrySetting(env: String, key: String): String? =
    System.getenv(env)?.takeIf { it.isNotBlank() }
        ?: sentryLocal.getProperty(key)?.takeIf { it.isNotBlank() }

val sentryDsn = sentrySetting("SENTRY_DSN", "sentry.dsn").orEmpty()
val sentryOrg = sentrySetting("SENTRY_ORG", "sentry.org")
val sentryProject = sentrySetting("SENTRY_PROJECT", "sentry.project")
val sentryToken = sentrySetting("SENTRY_AUTH_TOKEN", "sentry.authToken")
val sentryCanUpload = sentryOrg != null && sentryProject != null && sentryToken != null

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

        // Read by the <meta-data> in AndroidManifest.xml. Empty is allowed and
        // means "off": the SDK starts, finds no DSN, and sends nothing.
        manifestPlaceholders["sentryDsn"] = sentryDsn
        manifestPlaceholders["sentryEnvironment"] = "release"
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

            // Crashes from a phone on the desk are not crashes in the wild, and
            // mixing them makes both harder to read. Sentry filters on this.
            manifestPlaceholders["sentryEnvironment"] = "debug"
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
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.ui)
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
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.work.runtime)
    implementation(libs.zxing.core)
    implementation(libs.play.services.code.scanner)
    testImplementation(libs.junit)
    // The JVM test classpath gets android.jar's org.json, whose methods all throw.
    testImplementation(libs.org.json)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

/**
 * What the Sentry Gradle plugin does at build time.
 *
 * Mostly its defaults. The departures, and why:
 *
 * - **The mapping file is uploaded only when it can be.** Release builds run R8,
 *   so their stack traces are obfuscated, and the mapping is what turns them back
 *   into names. Uploading it needs an org, a project and a token; without all
 *   three the plugin still embeds the mapping's id in the app, and the file can be
 *   uploaded by hand with sentry-cli, rather than the build failing.
 * - **No OkHttp instrumentation.** The app's own requests go through
 *   HttpURLConnection, which this would not see anyway - but LiveKit uses OkHttp
 *   underneath, and it opens its room connection with the access token in the
 *   URL. Request URLs end up in breadcrumbs and spans. That is not something to
 *   send to a third party for every call.
 * - **No build telemetry.** The plugin reports on the build itself to Sentry by
 *   default. Nothing about the app depends on it.
 */
sentry {
    org.set(sentryOrg)
    projectName.set(sentryProject)
    authToken.set(sentryToken)

    includeProguardMapping.set(true)
    autoUploadProguardMapping.set(sentryCanUpload)
    includeSourceContext.set(false)

    tracingInstrumentation {
        enabled.set(true)
        features.set(
            setOf(
                InstrumentationFeature.DATABASE,
                InstrumentationFeature.FILE_IO,
                InstrumentationFeature.COMPOSE
            )
        )
    }

    autoInstallation {
        enabled.set(true)
    }

    telemetry.set(false)
}

/**
 * The mapping upload, switched off entirely when there is nothing to upload with.
 *
 * Turning off `autoUploadProguardMapping` is not enough on its own: the task still
 * runs, as a dry run through sentry-cli. On Windows the plugin starts sentry-cli
 * through `cmd /c` without quoting its path, and this project lives under
 * "D:\IQ SOCIAL WEB" - so cmd reads "D:\IQ" as the command, fails to find it, and
 * the release build fails with it. A dry run produces nothing anyway; the mapping's
 * id, which is what ties a crash back to its mapping, is written by a separate task
 * that does not call sentry-cli at all.
 *
 * With a token present the task runs as normal - and on this machine it will hit
 * the same space until the project is moved to a path without one, or the mapping
 * is uploaded by hand. That failure is left loud on purpose: somebody who asked for
 * uploads should find out they are not happening.
 */
tasks.configureEach {
    if (name.startsWith("uploadSentryProguardMappings")) enabled = sentryCanUpload
}
