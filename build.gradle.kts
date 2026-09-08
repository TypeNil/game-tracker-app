import io.gitlab.arturbosch.detekt.Detekt

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.detekt)
}

val detektConfigFile = files("$rootDir/config/detekt/detekt.yml")

detekt {
    toolVersion = libs.versions.detekt.get()
    buildUponDefaultConfig = false
    allRules = false
    autoCorrect = false
    config.setFrom(detektConfigFile)
}

subprojects {
    pluginManager.withPlugin("io.gitlab.arturbosch.detekt") {
        extensions.configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
            toolVersion = libs.versions.detekt.get()
            buildUponDefaultConfig = false
            allRules = false
            autoCorrect = false
            config.setFrom(detektConfigFile)
        }
    }
}

allprojects {
    tasks.withType<Detekt>().configureEach {
        jvmTarget = "17"
        autoCorrect = false
        buildUponDefaultConfig = false
        config.setFrom(detektConfigFile)
        reports {
            html.required.set(true)
            xml.required.set(true)
            txt.required.set(false)
            sarif.required.set(false)
        }
    }
}