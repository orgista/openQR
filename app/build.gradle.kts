import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val versionProperties = Properties().apply {
    val versionFile = project.file("version.properties")
    versionFile.inputStream().use { load(it) }
}

val configuredVersionCode = providers.gradleProperty("appVersionCode").orNull?.toIntOrNull()
    ?: versionProperties.getProperty("VERSION_CODE").toInt()
val configuredVersionName = providers.gradleProperty("appVersionName").orNull
    ?: versionProperties.getProperty("VERSION_NAME")

val openQrKeystoreFile = providers.environmentVariable("OPENQR_KEYSTORE_FILE")
val openQrKeystorePass = providers.environmentVariable("OPENQR_KEYSTORE_PASS")
val openQrKeyAlias = providers.environmentVariable("OPENQR_KEY_ALIAS")
val openQrKeyPass = providers.environmentVariable("OPENQR_KEY_PASS")

android {
    namespace = "com.orgista.openqr"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.orgista.openqr"
        minSdk = 34
        versionCode = configuredVersionCode
        versionName = configuredVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    // One codebase, two stores with conflicting target requirements:
    // Meta's passthrough-camera guidance pins Quest builds at targetSdk 34,
    // while Google Play requires new mobile submissions to target 36+
    // (enforced since 2026-08-31). The flavor picks the ceiling per channel.
    flavorDimensions += "distribution"
    productFlavors {
        create("quest") {
            dimension = "distribution"
            targetSdk = 34
        }
        create("play") {
            dimension = "distribution"
            targetSdk = 36
        }
    }

    signingConfigs {
        create("release") {
            val keystorePath = openQrKeystoreFile.orNull
            if (!keystorePath.isNullOrBlank()) {
                storeFile = file(keystorePath)
                storePassword = openQrKeystorePass.orNull
                keyAlias = openQrKeyAlias.orNull
                keyPassword = openQrKeyPass.orNull
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        create("managed") {
            initWith(getByName("release"))
            applicationIdSuffix = ".debug"
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
        release {
            isMinifyEnabled = true
            signingConfig = signingConfigs.getByName("release")
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
        buildConfig = true
        viewBinding = true
    }

    lint {
        // These stay pinned intentionally until Meta's passthrough-camera guidance and
        // the project compileSdk move beyond API 34.
        disable += setOf("GradleDependency", "ObsoleteSdkInt", "OldTargetApi")
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("com.google.zxing:core:3.5.3")

    testImplementation("junit:junit:4.13.2")
}

val verifyOpenQrReleaseSigning by tasks.registering {
    group = "verification"
    description = "Fails release builds unless the OpenQR upload keystore inputs are configured."

    doLast {
        val requiredEnv = listOf(
            "OPENQR_KEYSTORE_FILE",
            "OPENQR_KEYSTORE_PASS",
            "OPENQR_KEY_ALIAS",
            "OPENQR_KEY_PASS",
        )
        val missing = requiredEnv.filter { providers.environmentVariable(it).orNull.isNullOrBlank() }
        if (missing.isNotEmpty()) {
            throw GradleException(
                "OpenQR release signing is not configured. Missing environment variables: " +
                    missing.joinToString(", "),
            )
        }

        val keystore = file(openQrKeystoreFile.get())
        if (!keystore.isFile) {
            throw GradleException(
                "OpenQR release keystore does not exist or is not a file: ${keystore.absolutePath}",
            )
        }
    }
}

tasks.configureEach {
    // Flavor-qualified variants (prePlayReleaseBuild, bundleQuestRelease, …)
    // must hit the same guard as the aggregate release tasks.
    if (name.matches(Regex("pre\\w*ReleaseBuild|assemble\\w*Release|bundle\\w*Release|package\\w*Release"))) {
        dependsOn(verifyOpenQrReleaseSigning)
    }
}
