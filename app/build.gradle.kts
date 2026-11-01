plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "com.m3browser"
    compileSdk = 37
    buildToolsVersion = "37.0.0"
    defaultConfig {
        applicationId = "com.m3browser"
        minSdk = 26
        targetSdk = 37
        versionCode = 3
        versionName = "1.0.2"
        val oauthId = (project.findProperty("GITHUB_OAUTH_CLIENT_ID") as? String).orEmpty()
            .replace("\\", "").replace("\"", "")
        buildConfigField("String", "GITHUB_OAUTH_CLIENT_ID", "\"$oauthId\"")
    }
    signingConfigs {
        // The keystore is committed on purpose. It is not a production secret:
        // it only exists so every GitHub Release is signed with the same key
        // and in-app updates can install over the previous build.
        create("release") {
            storeFile = rootProject.file("keystore/m3browser-release.keystore")
            storePassword = "m3browser"
            keyAlias = "m3browser"
            keyPassword = "m3browser"
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.material3:material3:1.5.0-beta02")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("androidx.datastore:datastore-preferences:1.1.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.webkit:webkit:1.14.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("com.google.mlkit:translate:17.0.3")
    implementation("com.google.mlkit:language-id:17.0.6")
    testImplementation("junit:junit:4.13.2")
}
