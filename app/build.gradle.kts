plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val updateGithubOwner = providers.gradleProperty("UPDATE_GITHUB_OWNER").orElse("").get()
val updateGithubRepo = providers.gradleProperty("UPDATE_GITHUB_REPO").orElse("").get()

android {
    namespace = "com.pandian.tobacco"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.pandian.tobacco"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "1.0.4"
        buildConfigField("String", "UPDATE_GITHUB_OWNER", "\"$updateGithubOwner\"")
        buildConfigField("String", "UPDATE_GITHUB_REPO", "\"$updateGithubRepo\"")
    }

    buildFeatures { compose = true; buildConfig = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
