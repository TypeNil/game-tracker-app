import java.util.Properties
import java.util.zip.ZipFile

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.detekt)
}

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { load(it) }
    }
}

val liveBaseUrl: String = localProperties.getProperty("bff.baseUrl")
    ?: (project.findProperty("BFF_BASE_URL") as? String)
    ?: "http://10.0.2.2:8080/"

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

android {
    namespace = "io.github.typenil.gametracker"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.typenil.gametracker"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }
    flavorDimensions += "environment"
    productFlavors {
        create("demo") {
            dimension = "environment"
            applicationIdSuffix = ".demo"
            versionNameSuffix = "-demo"
            buildConfigField("String", "BFF_BASE_URL", "\"http://10.0.2.2:8080/\"")
        }
        create("live") {
            dimension = "environment"
            buildConfigField("String", "BFF_BASE_URL", "\"$liveBaseUrl\"")
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
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

    testOptions {
        unitTests.all {
            it.systemProperty("demoAssetsDir", file("src/demo/assets").absolutePath)
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    sourceSets {
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // AndroidX Core & Architecture Components
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    // Jetpack Compose & Material 3
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    // Hilt Dependency Injection
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // Room Database (SSOT)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.room.paging)
    ksp(libs.room.compiler)

    // Paging 3
    implementation(libs.androidx.paging.runtime.ktx)
    implementation(libs.androidx.paging.compose)

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)

    // Networking & Serialization
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.okhttp.core)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)

    // Coroutines & Flow
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // Image Loading (Coil 3)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // Detekt Plugins (KtLint Formatting & Compose Rules)
    detektPlugins(libs.detekt.formatting)
    detektPlugins(libs.detekt.compose)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.androidx.paging.testing)
    testImplementation(libs.okhttp.mockwebserver)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.work.testing)


    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
abstract class VerifyReleaseArtifactsTask : DefaultTask() {
    @get:Internal
    abstract val buildDirectory: DirectoryProperty

    @TaskAction
    fun verify() {
        listOf("demo", "live").forEach { flavor ->
            val variantName = "${flavor}Release"
            val mappingFile = buildDirectory
                .file("outputs/mapping/$variantName/mapping.txt")
                .get()
                .asFile

            check(mappingFile.isFile && mappingFile.length() > 0L) {
                "Missing R8 mapping output for $variantName at ${mappingFile.path}"
            }

            val releaseDir = buildDirectory.dir("outputs/apk/$flavor/release").get().asFile
            val apks = releaseDir.listFiles { _, name -> name.endsWith(".apk") }
                ?: emptyArray()

            check(apks.isNotEmpty()) {
                "No release APK found in ${releaseDir.path}"
            }

            apks.forEach { apk ->
                ZipFile(apk).use { zip ->
                    val dexEntries = zip.entries().asSequence()
                        .filter { it.name.startsWith("classes") && it.name.endsWith(".dex") }
                        .toList()

                    check(dexEntries.isNotEmpty()) {
                        "Release APK ${apk.name} does not contain DEX"
                    }
                    val hasSignature = zip.entries().asSequence().any {
                        it.name.startsWith("META-INF/") && (
                            it.name.endsWith(".RSA") || it.name.endsWith(".DSA") ||
                            it.name.endsWith(".EC") || it.name.endsWith(".SF")
                        )
                    }
                    check(!hasSignature) {
                        "Release APK ${apk.name} must be unsigned, but signature entries were found in META-INF"
                    }

                    val containsTestAction = dexEntries.any { entry ->
                        zip.getInputStream(entry).use { input ->
                            val dexString = String(input.readBytes(), Charsets.ISO_8859_1)
                            dexString.contains("io.github.typenil.gametracker.ACTION_TEST_NOTIFICATION")
                        }
                    }
                    check(!containsTestAction) {
                        "Release APK ${apk.name} still contains ACTION_TEST_NOTIFICATION marker"
                    }
                    check(zip.getEntry("AndroidManifest.xml") != null) {
                        "Release APK ${apk.name} is missing AndroidManifest.xml"
                    }
                }
                println("Verified release artifact: ${apk.name} (${apk.length()} bytes, R8 mapping: ${mappingFile.length()} bytes)")
            }
        }
    }
}

tasks.register<VerifyReleaseArtifactsTask>("verifyReleaseArtifacts") {
    group = "verification"
    description = "Inspects demoRelease and liveRelease APKs to verify R8 minification and artifact integrity."
    dependsOn("assembleDemoRelease", "assembleLiveRelease")
    buildDirectory.set(layout.buildDirectory)
}