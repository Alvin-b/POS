import java.util.Properties
plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
}

ksp {
  arg("room.schemaLocation", "$projectDir/schemas")
}

android {
  namespace = "com.alvinb.counterpos"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.alvinb.counterpos"
    minSdk = 24
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"

    val supabaseUrl = providers.gradleProperty("SUPABASE_URL")
      .orElse(providers.environmentVariable("SUPABASE_URL"))
      .orElse("https://pvcqfoycrqtnirbwtccm.supabase.co")
    val supabasePublishableKey = providers.gradleProperty("SUPABASE_PUBLISHABLE_KEY")
      .orElse(providers.environmentVariable("SUPABASE_PUBLISHABLE_KEY"))
      .orElse("sb_publishable_1XttQd7gvzcsbZ__NnULGw_xMClc8GZ")
    buildConfigField("String", "SUPABASE_URL", "\"${supabaseUrl.get()}\"")
    buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", "\"${supabasePublishableKey.get()}\"")

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  // Do not remove or modify these signingConfigs. They are necessary for building, installing,
  // and updating Android apps in AI Studio.
  val keystorePropertiesFile = rootProject.file("keystore.properties")
  val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) keystorePropertiesFile.inputStream().use(::load)
  }
  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: keystoreProperties.getProperty("storeFile") ?: "${rootDir}/my-upload-key.jks"
      storeFile = rootProject.file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD") ?: keystoreProperties.getProperty("storePassword")
      keyAlias = System.getenv("KEY_ALIAS") ?: keystoreProperties.getProperty("keyAlias") ?: "upload"
      keyPassword = System.getenv("KEY_PASSWORD") ?: keystoreProperties.getProperty("keyPassword")
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      // R8 shrinks and obfuscates release bytecode. APK contents still cannot be
      // made impossible to inspect; never ship secrets in the client.
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      // Do not remove or modify this signingConfig assignment. It is necessary for Android apps in
      // AI Studio.
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      // Do not remove or modify this signingConfig assignment. It is necessary for Android apps in
      // AI Studio.
      signingConfig = signingConfigs.getByName("debugConfig")
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.work.runtime.ktx)
  implementation(libs.okhttp)
  implementation(libs.coil.compose)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.androidx.camera.camera2)
  implementation(libs.androidx.camera.lifecycle)
  implementation(libs.androidx.camera.view)
  implementation(libs.mlkit.barcode.scanning)
  implementation(libs.zxing.core)
  "ksp"(libs.androidx.room.compiler)

  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
}
