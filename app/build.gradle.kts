import com.android.build.api.dsl.ApplicationExtension

apply(plugin = "com.android.application")
apply(plugin = "org.jetbrains.kotlin.plugin.compose")

configure<ApplicationExtension> {
    namespace = "com.michis.reader"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.michis.reader"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
    }

}

dependencies {
    add("implementation", platform("androidx.compose:compose-bom:2025.02.00"))
    add("implementation", "androidx.compose.material3:material3")
    add("implementation", "androidx.activity:activity-compose:1.10.1")
    add("implementation", "androidx.compose.material:material-icons-extended")
    add("debugImplementation", "androidx.compose.ui:ui-tooling")
    add("implementation", "androidx.compose.ui:ui-tooling-preview")
    add("implementation", "com.github.KvColorPalette:KvColorPicker-Android:3.0.1")
    add("implementation", "androidx.activity:activity-ktx:1.10.1")
    add("implementation", "androidx.credentials:credentials:1.7.0-alpha02")
    add("implementation", "androidx.credentials:credentials-play-services-auth:1.7.0-alpha02")
    add("implementation", "com.google.android.libraries.identity.googleid:googleid:1.2.0")
    add("implementation", "com.google.android.gms:play-services-auth:21.6.0")
    add("implementation", "androidx.work:work-runtime-ktx:2.11.2")
    add("implementation", fileTree("libs") { include("*.jar") })
    add("implementation", "org.readium.kotlin-toolkit:readium-shared:3.2.0")
    add("implementation", "org.readium.kotlin-toolkit:readium-streamer:3.2.0")
    add("implementation", "org.readium.kotlin-toolkit:readium-navigator:3.2.0")
    add("implementation", "androidx.fragment:fragment-ktx:1.8.9")
    add("implementation", "androidx.lifecycle:lifecycle-runtime-ktx:2.9.3")
    add("coreLibraryDesugaring", "com.android.tools:desugar_jdk_libs:2.1.5")
    add("testImplementation", "junit:junit:4.13.2")
    add("testImplementation", "androidx.test:core:1.6.1")
    add("testImplementation", "org.robolectric:robolectric:4.14.1")
}
