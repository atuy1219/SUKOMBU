import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

val buildVersionCode = providers.environmentVariable("SUKOMBU_VERSION_CODE")
    .map { it.toIntOrNull() ?: 1 }
    .orElse(1)
val buildVersionName = providers.environmentVariable("SUKOMBU_VERSION_NAME")
    .orElse("1.0.0-dev")

android {
    namespace = "com.atuy.scomb"
    compileSdk = 37

    signingConfigs {
        create("release") {
            val keystorePropertiesFile = rootProject.file("local.properties")
            val keystoreProperties = Properties()
            if (keystorePropertiesFile.exists()) {
                FileInputStream(keystorePropertiesFile).use(keystoreProperties::load)
            }

            val keyStorePath = providers.environmentVariable("KEYSTORE_PATH").orNull
                ?: keystoreProperties.getProperty("key.store")
            val keyStorePwd = providers.environmentVariable("KEY_STORE_PASSWORD").orNull
                ?: keystoreProperties.getProperty("key.store.password")
            val keyAliasVal = providers.environmentVariable("ALIAS").orNull
                ?: keystoreProperties.getProperty("key.alias")
            val keyPwd = providers.environmentVariable("KEY_PASSWORD").orNull
                ?: keystoreProperties.getProperty("key.password")

            if (keyStorePath != null && keyStorePwd != null && keyAliasVal != null && keyPwd != null) {
                storeFile = file(keyStorePath)
                storePassword = keyStorePwd
                keyAlias = keyAliasVal
                keyPassword = keyPwd
            }
        }
    }

    defaultConfig {
        applicationId = "com.atuy.scomb"
        minSdk = 35
        targetSdk = 37

        versionCode = buildVersionCode.get()
        versionName = buildVersionName.get()

        ndk {
            abiFilters.add("arm64-v8a")
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
        freeCompilerArgs.add("-Xannotation-default-target=param-property")
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16.1")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.security.crypto)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.moshi)
    implementation(libs.logging.interceptor)
    implementation(libs.moshi)
    ksp(libs.moshi.codegen)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.room.compiler)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    implementation(libs.error.prone.annotations)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.common)
}
