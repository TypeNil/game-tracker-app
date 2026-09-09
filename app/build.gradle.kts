import java.util.Properties
import java.net.URI
import java.net.InetAddress
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

// Resolution order: -PBFF_BASE_URL > env BFF_BASE_URL > local.properties (dev machine only).
val configuredLiveBaseUrl: Provider<String> = providers.gradleProperty("BFF_BASE_URL")
    .orElse(providers.environmentVariable("BFF_BASE_URL"))
    .orElse(providers.provider { localProperties.getProperty("bff.baseUrl") })

// Debug convenience fallback. liveRelease MUST NOT use it (fail-fast in androidComponents below).
val liveBaseUrl: String = configuredLiveBaseUrl.getOrElse("http://10.0.2.2:8080/")

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
        versionCode = 3
        versionName = "1.0.2"

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

    // Production (liveRelease) and portfolio (demoRelease) keys are independent.
    // Credentials come ONLY from env; missing env leaves that variant unsigned so
    // PR CI and local builds stay secret-free.
    val demoKeystorePath = System.getenv("DEMO_KEYSTORE_PATH")
    val releaseKeystorePath = System.getenv("RELEASE_KEYSTORE_PATH")
    require(
        demoKeystorePath.isNullOrBlank() ||
            releaseKeystorePath.isNullOrBlank() ||
            demoKeystorePath != releaseKeystorePath,
    ) {
        "DEMO_KEYSTORE_PATH must not reuse the production keystore"
    }

    signingConfigs {
        create("release") {
            if (!releaseKeystorePath.isNullOrBlank()) {
                storeFile = file(releaseKeystorePath)
                storePassword = System.getenv("RELEASE_STORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
            }
        }
        create("demoRelease") {
            if (!demoKeystorePath.isNullOrBlank()) {
                storeFile = file(demoKeystorePath)
                storePassword = System.getenv("DEMO_STORE_PASSWORD")
                keyAlias = System.getenv("DEMO_KEY_ALIAS")
                keyPassword = System.getenv("DEMO_KEY_PASSWORD")
            }
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
            isDebuggable = false
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

    lint {
        abortOnError = true

        // Promote accessibility checks that currently pass to error severity so
        // future accessibility regressions fail the build.
        error += listOf(
            "ContentDescription",
            "ClickableViewAccessibility",
        )
    }
}

// Per-variant signing so demoRelease cannot inherit the production keystore.
androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        val dslSigning = when (variant.flavorName) {
            "demo" -> android.signingConfigs.getByName("demoRelease")
            "live" -> android.signingConfigs.getByName("release")
            else -> null
        }
        if (dslSigning?.storeFile != null) {
            variant.signingConfig.setConfig(dslSigning)
        }
    }
}

// Execution-time guard: a liveRelease baked against an emulator loopback or plain http
// URL installs fine but is offline on real devices. Wired into preLiveReleaseBuild so it
// cannot be bypassed via aggregate/alias tasks (:app:build, :app:assemble, :app:bundle,
// Android Studio). Debug keeps the loopback fallback.
abstract class ValidateLiveReleaseBffUrlTask : DefaultTask() {
    @get:Input
    @get:Optional
    abstract val baseUrl: Property<String>

    @TaskAction
    fun validate() {
        val value = baseUrl.orNull
            ?: error("BFF_BASE_URL is required for liveRelease (gradle property, env var, or local.properties)")
        val uri = runCatching { URI(value) }.getOrElse {
            error("liveRelease BFF_BASE_URL is not a valid URI: $value")
        }
        // Retrofit requires a trailing slash; anything else crashes at Hilt init.
        val host = uri.host
            ?.removePrefix("[")
            ?.removeSuffix("]")
            ?.trimEnd('.')
            ?.lowercase()
            .orEmpty()
        // Covers 127.x.x.x, ::1 and other spellings InetAddress resolves as loopback.
        val isIpLiteral = host.contains(':') ||
            host.matches(Regex("""\d{1,3}(\.\d{1,3}){3}"""))
        val literalAddress = if (isIpLiteral) {
            runCatching { InetAddress.getByName(host) }.getOrNull()
        } else {
            null
        }
        require(uri.scheme.equals("https", ignoreCase = true) && host.isNotBlank() &&
            uri.userInfo == null && uri.rawQuery == null && uri.rawFragment == null &&
            uri.rawPath.endsWith("/") && host != "localhost" &&
            literalAddress?.isLoopbackAddress != true &&
            literalAddress?.isAnyLocalAddress != true) {
            "liveRelease requires a credential-free, non-loopback HTTPS base URL ending in '/': $value"
        }
    }
}

val validateLiveReleaseBffUrl = tasks.register<ValidateLiveReleaseBffUrlTask>("validateLiveReleaseBffUrl") {
    group = "verification"
    description = "Fails unless liveRelease is configured with a non-loopback HTTPS BFF URL."
    baseUrl.set(configuredLiveBaseUrl)
}

tasks.configureEach {
    if (name == "preLiveReleaseBuild") {
        dependsOn(validateLiveReleaseBffUrl)
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

    @get:Input
    abstract val flavors: ListProperty<String>

    @TaskAction
    fun verify() {
        flavors.get().forEach { flavor ->
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
                    // Signature is NOT verified here: PR CI asserts unsigned via apksigner, while
                    // the production release workflow pins the expected cert digest. APK Signature
                    // Scheme v2/v3 lives outside ZIP entries, so META-INF presence proves nothing.
                    val hasMetaInfSignature = zip.entries().asSequence().any {
                        it.name.startsWith("META-INF/") && (
                            it.name.endsWith(".RSA") || it.name.endsWith(".DSA") ||
                            it.name.endsWith(".EC") || it.name.endsWith(".SF")
                        )
                    }
                    println("META-INF signature entries present: $hasMetaInfSignature (verify signer with apksigner)")

                    val dexStrings = dexEntries.map { entry ->
                        zip.getInputStream(entry).use { input ->
                            String(input.readBytes(), Charsets.ISO_8859_1)
                        }
                    }
                    check(dexStrings.none { it.contains("io.github.typenil.gametracker.ACTION_TEST_NOTIFICATION") }) {
                        "Release APK ${apk.name} still contains ACTION_TEST_NOTIFICATION marker"
                    }
                    // Defense in depth behind the liveRelease fail-fast URL validation above:
                    // a live APK baked against the emulator loopback is offline on real devices.
                    if (flavor == "live") {
                        check(dexStrings.none { it.contains("10.0.2.2") }) {
                            "Release APK ${apk.name} contains emulator loopback URL (BFF_BASE_URL misconfigured)"
                        }
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
    flavors.set(listOf("demo", "live"))
    dependsOn("assembleDemoRelease", "assembleLiveRelease")
    buildDirectory.set(layout.buildDirectory)
}

// Production-only verifier: demoRelease must never be built or signed with the
// production identity. Portfolio signing uses DEMO_* in portfolio-release.yml.
tasks.register<VerifyReleaseArtifactsTask>("verifyLiveReleaseArtifacts") {
    group = "verification"
    description = "Inspects the liveRelease APK only (production workflow)."
    flavors.set(listOf("live"))
    dependsOn("assembleLiveRelease")
    buildDirectory.set(layout.buildDirectory)
}