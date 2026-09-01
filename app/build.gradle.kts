plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.services)
}

import java.util.Properties

android {
    namespace = "com.vivero.pickingve"
    compileSdk = 34

    val secrets = Properties().apply {
        val file = rootProject.file("secrets.properties")
        if (file.exists()) {
            val contenido = file.readText().trimStart('\uFEFF')
            load(contenido.toByteArray().inputStream())
        }
    }

    defaultConfig {
        applicationId = "com.vivero.pickingve"
        minSdk = 26
        targetSdk = 34
versionCode = 46

        versionName = "2.3.6"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        buildConfigField(
            "String",
            "DEFAULT_TELEGRAM_BOT_TOKEN",
            "\"${secrets.getProperty("TELEGRAM_BOT_TOKEN", "")}\""
        )
        buildConfigField(
            "String",
            "DEFAULT_TELEGRAM_CHAT_ID",
            "\"${secrets.getProperty("TELEGRAM_CHAT_ID", "")}\""
        )
        buildConfigField(
            "String",
            "API_KEY",
            "\"${secrets.getProperty("API_KEY", "")}\""
        )
        buildConfigField(
            "String",
            "DEFAULT_LABELS_BOT_TOKEN",
            "\"${secrets.getProperty("LABELS_BOT_TOKEN", "")}\""
        )
        buildConfigField(
            "String",
            "DEFAULT_LABELS_CHAT_ID",
            "\"${secrets.getProperty("LABELS_CHAT_ID", "")}\""
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

val apkVersionName = android.defaultConfig.versionName

tasks.matching { it.name == "packageDebug" }.configureEach {
    doLast {
        val apk = layout.buildDirectory.file("outputs/apk/debug/app-debug.apk").get().asFile
        val destino = rootProject.layout.projectDirectory
            .file("apks/PickingVE-debug-$apkVersionName.apk").asFile
        if (apk.exists()) {
            destino.parentFile?.mkdirs()
            apk.copyTo(destino, overwrite = true)
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    // Room DB
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // CameraX
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)

    // ML Kit Vision
    implementation(libs.mlkit.barcode)
    implementation(libs.mlkit.text)

    // Ktor Networking
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    // Kotlinx Coroutines
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)

    // Firebase (push notifications)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.coil.compose)
    implementation(libs.androidx.security.crypto)

    debugImplementation(libs.androidx.ui.tooling)

    // Unit Testing
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
