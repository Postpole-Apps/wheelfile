import java.nio.file.Files
import java.util.Base64
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
}

// Release signing: KEYSTORE_BASE64 (CI) or KEYSTORE_FILE (local path), plus KEYSTORE_PASSWORD,
// KEY_ALIAS, KEY_PASSWORD. Each is read from the environment first, then local.properties.
// Never commit keys. Without all of them the release build is left unsigned.
val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

fun signingValue(name: String): String? =
    (System.getenv(name) ?: localProps.getProperty(name))?.takeIf { it.isNotBlank() }

val signingSecrets = listOf("KEYSTORE_PASSWORD", "KEY_ALIAS", "KEY_PASSWORD")
val keystoreConfigured = signingValue("KEYSTORE_BASE64") != null || signingValue("KEYSTORE_FILE") != null
val signingAnySet = keystoreConfigured || signingSecrets.any { signingValue(it) != null }
val signingSecretsSet = signingSecrets.all { signingValue(it) != null }

// Only materialise a keystore when everything else is present. KEYSTORE_BASE64 is decoded to a
// private (owner-only, 0600) temp file outside the project that is deleted when the JVM exits.
// In CI prefer KEYSTORE_FILE (see ci-templates/release.yml).
val signingKeystore: File? = if (!signingSecretsSet) null else run {
    val b64 = signingValue("KEYSTORE_BASE64")
    val path = signingValue("KEYSTORE_FILE")
    when {
        b64 != null -> Files.createTempFile("release-", ".keystore").toFile().also {
            it.deleteOnExit()
            it.writeBytes(Base64.getMimeDecoder().decode(b64))
        }
        path != null -> rootProject.file(path).takeIf { it.exists() }
        else -> null
    }
}
val signingComplete = signingKeystore != null
if (signingAnySet && !signingComplete) {
    logger.warn(
        "WARNING: release signing is only partially configured (need KEYSTORE_BASE64 or KEYSTORE_FILE " +
            "plus KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD); the release build will be UNSIGNED.",
    )
}

android {
    namespace = "com.postpole.template"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.postpole.template"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // One-time in-app product id for the Pro unlock. Override with -PproProductId=... if needed.
        val proProductId = providers.gradleProperty("proProductId").getOrElse("lifetime_full_access")
        buildConfigField("String", "PRO_PRODUCT_ID", "\"$proProductId\"")
    }

    signingConfigs {
        if (signingComplete) {
            create("release") {
                storeFile = signingKeystore
                storePassword = signingValue("KEYSTORE_PASSWORD")
                keyAlias = signingValue("KEY_ALIAS")
                keyPassword = signingValue("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            if (signingComplete) signingConfig = signingConfigs.getByName("release")
            // Native symbols for Crashlytics / Play Console (no-op when the app has no native code).
            ndk.debugSymbolLevel = "FULL"
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

kotlin {
    jvmToolchain(21)
}

// Firebase plugins are applied only when this app has its own google-services.json, so the bare
// template (and CI) builds without it. Analytics falls back to a no-op in that case.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
    apply(plugin = "com.google.firebase.crashlytics")
    // Upload the R8 mapping file (and native symbols) for release builds.
    android.buildTypes.named("release") {
        (this as ExtensionAware).extensions.configure<com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsExtension>(
            "firebaseCrashlytics",
        ) {
            mappingFileUploadEnabled = true
            nativeSymbolUploadEnabled = true
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.billing.ktx)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)
    implementation(libs.play.review)
    implementation(libs.play.app.update)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
}
