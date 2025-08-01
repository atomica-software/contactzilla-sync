/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)

    alias(libs.plugins.mikepenz.aboutLibraries)
    alias(libs.plugins.cyclonedx)
}

tasks.cyclonedxBom {
    projectType = "application"
    outputName = "sbom-android-app"
    outputFormat = "json"
    includeLicenseText = false
    componentName = "ContactzillaSyncClient"
    componentVersion = android.defaultConfig.versionName
}

// Android configuration
android {
    compileSdk = 35

    defaultConfig {
        applicationId = "com.atomicasoftware.contactzillasync"

        //noinspection HighAppVersionCode
        versionCode = 2070040508
        versionName = "4.5.8"

        setProperty("archivesBaseName", "ContactzillaSyncClient-$versionName")

        minSdk = 24        // Android 7.0
        targetSdk = 35     // Android 15

        testInstrumentationRunner = "com.atomicasoftware.contactzillasync.HiltTestRunner"
    }

    java {
        toolchain {
            languageVersion = JavaLanguageVersion.of(21)
        }
    }

    compileOptions {
        // required for
        // - dnsjava 3.x: java.nio.file.Path
        // - ical4android: time API
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    // Java namespace for our classes (not to be confused with Android package ID)
    namespace = "com.atomicasoftware.contactzillasync"

    flavorDimensions += "distribution"
    productFlavors {
        create("ose") {
            dimension = "distribution"
            versionNameSuffix = "-cz"
        }
    }

    sourceSets {
        getByName("androidTest") {
            assets.srcDir("$projectDir/schemas")
        }
    }

    signingConfigs {
        create("bitfire") {
            storeFile = file(System.getenv("ANDROID_KEYSTORE") ?: "/dev/null")
            storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("ANDROID_KEY_ALIAS")
            keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules-release.pro")

            isShrinkResources = true

            signingConfig = signingConfigs.findByName("bitfire")
        }
    }

    lint {
        disable += arrayOf("GoogleAppIndexingWarning", "ImpliedQuantity", "MissingQuantity", "MissingTranslation", "ExtraTranslation", "RtlEnabled", "RtlHardcoded", "Typos")
    }

    androidResources {
        generateLocaleConfig = true
    }

    packaging {
        resources {
            // multiple (test) dependencies have LICENSE files at same location
            merges += arrayOf("META-INF/LICENSE*")
        }
    }

    @Suppress("UnstableApiUsage")
    testOptions {
        managedDevices {
            localDevices {
                create("virtual") {
                    device = "Pixel 3"
                    apiLevel = 34
                    systemImageSource = "aosp-atd"
                }
            }
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

aboutLibraries {
    // exclude timestamps for reproducible builds [https://github.com/bitfireAT/davx5-ose/issues/994]
    excludeFields = arrayOf("generated")
    // path to our custom library definitions
    configPath = "config"
}

dependencies {
    // core
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlinx.coroutines)
    coreLibraryDesugaring(libs.android.desugaring)

    // Hilt
    implementation(libs.hilt.android.base)
    ksp(libs.androidx.hilt.compiler)
    ksp(libs.hilt.android.compiler)

    // support libs
    implementation(libs.androidx.activityCompose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.core)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.base)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.paging)
    implementation(libs.androidx.paging.compose)
    implementation(libs.androidx.preference)
    implementation(libs.androidx.security)
    implementation(libs.androidx.work.base)

    // Jetpack Compose
    implementation(libs.compose.accompanist.permissions)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.materialIconsExtended)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.compose.ui.toolingPreview)
    implementation(libs.compose.ui.text.google.fonts)

    // Glance Widgets
    implementation(libs.glance.base)
    implementation(libs.glance.material)

    // Jetpack Room
    implementation(libs.room.runtime)
    implementation(libs.room.base)
    implementation(libs.room.paging)
    ksp(libs.room.compiler)

    // own libraries
    implementation(libs.bitfire.cert4android)
    implementation(libs.bitfire.dav4jvm) {
        exclude(group="junit")
        exclude(group="org.ogce", module="xpp3")    // Android has its own XmlPullParser implementation
    }
    implementation(libs.bitfire.vcard4android)

    // third-party libs
    @Suppress("RedundantSuppression")
    implementation(libs.dnsjava)
    implementation(libs.guava)
    implementation(libs.mikepenz.aboutLibraries)
    implementation(libs.nsk90.kstatemachine)
    implementation(libs.okhttp.base)
    implementation(libs.okhttp.brotli)
    implementation(libs.okhttp.logging)
    implementation(libs.openid.appauth)
    implementation(libs.zxing.android.embedded)

    // for tests
    androidTestImplementation(libs.androidx.arch.core.testing)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.work.testing)
    androidTestImplementation(libs.hilt.android.testing)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.mockk.android)
    androidTestImplementation(libs.okhttp.mockwebserver)
    androidTestImplementation(libs.room.testing)

    testImplementation(libs.bitfire.dav4jvm)
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.okhttp.mockwebserver)
}
