import java.net.URI
import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.google.services)
}

// =============================================================================
// PROMISE APP VERSIONING (Single Source of Truth)
// =============================================================================
// Release Versioning Convention for Firebase App Distribution:
// Every new release (patch, minor, or major) MUST increment versionCode by +1
// so Android & Firebase App Distribution recognize it as a newer upgrade.
//
// Versioning progression examples:
//   0.1.0 -> versionCode 1 (Current Initial Release)
//   0.1.1 -> versionCode 2 (Patch)
//   0.1.2 -> versionCode 3 (Patch)
//   0.2.0 -> versionCode 4 (Minor)
//   1.0.0 -> versionCode 5 (Major)
// =============================================================================
val appVersionName: String =
    (project.findProperty("promise.versionName") as String?)
        ?: "0.1.0"

val appVersionCode: Int =
    (project.findProperty("promise.versionCode") as String?)?.toIntOrNull()
        ?: 1

val localProperties =
    Properties().apply {
        val file = rootProject.file("local.properties")
        if (file.exists()) {
            file.inputStream().use { load(it) }
        }
    }

// Priority: -P / gradle.properties → local.properties (gitignored) → emulator default.
val debugApiBaseUrl: String =
    (project.findProperty("promise.apiBaseUrl") as String?)
        ?: localProperties.getProperty("promise.apiBaseUrl")
        ?: "http://10.0.2.2:8000/api/v1/"

val releaseApiBaseUrl: String =
    (project.findProperty("promise.prodApiBaseUrl") as String?)
        ?: (project.findProperty("promise.releaseApiBaseUrl") as String?)
        ?: System.getenv("PROMISE_PROD_API_BASE_URL")
        ?: localProperties.getProperty("promise.prodApiBaseUrl")
        ?: ""

val debugApiHost: String =
    try {
        URI(debugApiBaseUrl).host ?: ""
    } catch (_: Exception) {
        ""
    }

val syncDebugNetworkSecurityConfig =
    tasks.register("syncDebugNetworkSecurityConfig") {
        val configFile = file("src/debug/res/xml/network_security_config.xml")
        inputs.property("debugApiHost", debugApiHost)
        outputs.file(configFile)

        doLast {
            val domains = linkedSetOf("10.0.2.2", "localhost", "127.0.0.1")
            if (debugApiHost.isNotEmpty()) {
                domains.add(debugApiHost.lowercase())
            }
            val content = buildString {
                appendLine("<?xml version=\"1.0\" encoding=\"utf-8\"?>")
                appendLine("<network-security-config>")
                appendLine("    <!--")
                appendLine("      Debug-only cleartext allowlist for local development hosts.")
                appendLine("      Emulator: 10.0.2.2 / localhost / 127.0.0.1")
                appendLine("      Physical LAN: automatically synced with promise.apiBaseUrl ($debugApiBaseUrl).")
                appendLine("      Release builds do not use this config.")
                appendLine("    -->")
                appendLine("    <domain-config cleartextTrafficPermitted=\"true\">")
                domains.forEach { domain ->
                    appendLine("        <domain includeSubdomains=\"false\">$domain</domain>")
                }
                appendLine("    </domain-config>")
                appendLine("</network-security-config>")
            }
            if (!configFile.exists() || configFile.readText() != content) {
                configFile.parentFile.mkdirs()
                configFile.writeText(content)
            }
        }
    }

tasks.named("preBuild") {
    dependsOn(syncDebugNetworkSecurityConfig)
}

val validateReleaseApiConfig =
    tasks.register("validateReleaseApiConfig") {
        doLast {
            if (releaseApiBaseUrl.isBlank()) {
                throw GradleException(
                    "Missing release API base URL. Please set 'promise.prodApiBaseUrl' in gradle.properties/local.properties or via environment variable PROMISE_PROD_API_BASE_URL.",
                )
            }
            if (releaseApiBaseUrl.contains("10.0.2.2") || releaseApiBaseUrl.contains("localhost") || releaseApiBaseUrl.contains("127.0.0.1")) {
                throw GradleException(
                    "Release API base URL cannot point to localhost/emulator ($releaseApiBaseUrl).",
                )
            }
        }
    }

tasks.configureEach {
    if (name.contains("Release", ignoreCase = true) && !name.contains("UnitTest", ignoreCase = true) && !name.contains("AndroidTest", ignoreCase = true) && (name.startsWith("assemble") || name.startsWith("bundle") || name.startsWith("package"))) {
        dependsOn(validateReleaseApiConfig)
    }
}

android {
    namespace = "app.promise.android"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.promise.android"
        minSdk = 26
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val storeFileProp = (project.findProperty("promise.storeFile") as String?)
                ?: (project.findProperty("RELEASE_STORE_FILE") as String?)
                ?: localProperties.getProperty("promise.storeFile")
                ?: localProperties.getProperty("RELEASE_STORE_FILE")
                ?: System.getenv("PROMISE_STORE_FILE")
                ?: System.getenv("RELEASE_STORE_FILE")
                ?: "promise-release.jks"

            val storePasswordProp = (project.findProperty("promise.storePassword") as String?)
                ?: (project.findProperty("RELEASE_STORE_PASSWORD") as String?)
                ?: localProperties.getProperty("promise.storePassword")
                ?: localProperties.getProperty("RELEASE_STORE_PASSWORD")
                ?: System.getenv("PROMISE_STORE_PASSWORD")
                ?: System.getenv("RELEASE_STORE_PASSWORD")

            val keyAliasProp = (project.findProperty("promise.keyAlias") as String?)
                ?: (project.findProperty("RELEASE_KEY_ALIAS") as String?)
                ?: localProperties.getProperty("promise.keyAlias")
                ?: localProperties.getProperty("RELEASE_KEY_ALIAS")
                ?: System.getenv("PROMISE_KEY_ALIAS")
                ?: System.getenv("RELEASE_KEY_ALIAS")
                ?: "promise"

            val keyPasswordProp = (project.findProperty("promise.keyPassword") as String?)
                ?: (project.findProperty("RELEASE_KEY_PASSWORD") as String?)
                ?: localProperties.getProperty("promise.keyPassword")
                ?: localProperties.getProperty("RELEASE_KEY_PASSWORD")
                ?: System.getenv("PROMISE_KEY_PASSWORD")
                ?: System.getenv("RELEASE_KEY_PASSWORD")

            val keystoreInRoot = rootProject.file(storeFileProp)
            val keystoreInApp = file(storeFileProp)
            val keystoreInParent = file("../$storeFileProp")

            val resolvedStoreFile = when {
                file(storeFileProp).isAbsolute -> file(storeFileProp)
                keystoreInParent.canonicalFile.exists() -> keystoreInParent
                keystoreInRoot.canonicalFile.exists() -> keystoreInRoot
                keystoreInApp.canonicalFile.exists() -> keystoreInApp
                else -> keystoreInRoot
            }

            storeFile = resolvedStoreFile
            storePassword = storePasswordProp
            keyAlias = keyAliasProp
            keyPassword = keyPasswordProp
        }
    }

    buildTypes {
        debug {
            buildConfigField("String", "API_BASE_URL", "\"$debugApiBaseUrl\"")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            buildConfigField("String", "API_BASE_URL", "\"$releaseApiBaseUrl\"")
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.appdistribution)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
}
