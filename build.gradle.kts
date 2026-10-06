// Top-level build file where you can add configuration options common to all sub-projects/modules.

// Lock the plugins' own dependencies too (buildscript-gradle.lockfile); see app/build.gradle.kts.
buildscript { configurations.classpath { resolutionStrategy.activateDependencyLocking() } }

plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.jetbrainsKotlinAndroid) apply false
    alias(libs.plugins.kotlinCompose) apply false
    alias(libs.plugins.gms) apply false
}
