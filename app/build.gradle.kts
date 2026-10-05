plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "com.aura.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.aura.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 164
        versionName = "1.6.4"
    }
    buildTypes { release { isMinifyEnabled = true; proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro") } }
}
dependencies {
    implementation(project(":aura-core"))
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.core:core-ktx:1.15.0")
}
