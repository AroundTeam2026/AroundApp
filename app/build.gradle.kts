import org.gradle.kotlin.dsl.DependencyHandlerScope
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  alias(libs.plugins.androidApplication)
  alias(libs.plugins.kotlinCompose)
  alias(libs.plugins.ktfmt)
  alias(libs.plugins.sonar)
  alias(libs.plugins.gms)
  id("jacoco")
}

android {
  namespace = "com.github.aroundteam2026.aroundapp"
  compileSdk = 37

  defaultConfig {
    applicationId = "com.github.aroundteam2026.aroundapp"
    minSdk = 28
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    vectorDrawables { useSupportLibrary = true }
  }

  buildTypes {
    release {
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(
          getDefaultProguardFile("proguard-android-optimize.txt"),
          "proguard-rules.pro",
      )
    }

    debug {
      enableUnitTestCoverage = true
      enableAndroidTestCoverage = true
    }
  }

  testCoverage { jacocoVersion = "0.8.11" }

  buildFeatures { compose = true }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }

  testOptions {
    unitTests {
      isIncludeAndroidResources = true
      isReturnDefaultValues = true
    }
  }
}

kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }

sonar {
  properties {
    property("sonar.projectKey", "AroundTeam2026_AroundApp")
    property("sonar.projectName", "AroundApp")
    property("sonar.organization", "aroundteam2026")
    property("sonar.host.url", "https://sonarcloud.io")
    // Comma-separated paths to the various directories containing the *.xml JUnit report files.
    // Each path may be absolute or relative to the project base directory.
    property(
        "sonar.junit.reportPaths",
        "${project.layout.buildDirectory.get()}/test-results/testDebugUnitTest/",
    )
    // Paths to xml files with Android Lint issues. If the main flavor is changed, this file will
    // have to be changed too.
    property(
        "sonar.androidLint.reportPaths",
        "${project.layout.buildDirectory.get()}/reports/lint-results-debug.xml",
    )
    // Paths to JaCoCo XML coverage report files.
    property(
        "sonar.coverage.jacoco.xmlReportPaths",
        "${project.layout.buildDirectory.get()}/reports/jacoco/jacocoTestReport/jacocoTestReport.xml",
    )
  }
}

// When a library is used both by robolectric and connected tests, use this function
fun DependencyHandlerScope.globalTestImplementation(dep: Any) {
  androidTestImplementation(dep)
  testImplementation(dep)
}

dependencies {
  val composeBom = platform(libs.compose.bom)
  val firebaseBom = platform(libs.firebase.bom)

  implementation(composeBom)
  implementation(firebaseBom)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.appcompat)
  implementation(libs.material)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.firebase.firestore)
  implementation(libs.firebase.auth)
  implementation(libs.compose.ui)
  implementation(libs.compose.ui.graphics)
  implementation(libs.compose.material3)
  implementation(libs.compose.activity)
  implementation(libs.compose.viewmodel)
  implementation(libs.compose.preview)

  debugImplementation(libs.compose.tooling)
  debugImplementation(libs.compose.test.manifest)

  testImplementation(libs.junit)
  testImplementation(libs.robolectric)

  androidTestImplementation(firebaseBom)

  // Shared by Robolectric (test/) and instrumented (androidTest/) tests
  globalTestImplementation(composeBom)
  globalTestImplementation(libs.androidx.junit)
  globalTestImplementation(libs.androidx.espresso.core)
  globalTestImplementation(libs.compose.test.junit)
  globalTestImplementation(libs.kaspresso)
  globalTestImplementation(libs.kaspresso.compose)
}

tasks.withType<Test> {
  // Configure Jacoco for each tests
  configure<JacocoTaskExtension> {
    isIncludeNoLocationClasses = true
    excludes = listOf("jdk.internal.*")
  }
}

tasks.register("jacocoTestReport", JacocoReport::class) {
  group = "verification"
  description = "Generates the JaCoCo coverage report from unit and instrumented tests."
  mustRunAfter("testDebugUnitTest", "connectedDebugAndroidTest")

  reports {
    xml.required = true
    html.required = true
  }

  val fileFilter =
      listOf(
          "**/R.class",
          "**/R$*.class",
          "**/BuildConfig.*",
          "**/Manifest*.*",
          "**/*Test*.*",
          "android/**/*.*",
      )

  val debugTree =
      fileTree(project.layout.buildDirectory) {
        include(
            // AGP 9 compiles Kotlin with its built-in compiler, which writes here
            "intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes/**",
            // Fallback for the AGP 8 layout
            "tmp/kotlin-classes/debug/**",
        )
        exclude(fileFilter)
      }

  val mainSrc = "${project.layout.projectDirectory}/src/main/java"
  sourceDirectories.setFrom(files(mainSrc))
  classDirectories.setFrom(files(debugTree))
  executionData.setFrom(
      fileTree(project.layout.buildDirectory.get()) {
        include("outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec")
        include("outputs/code_coverage/debugAndroidTest/connected/*/coverage.ec")
      }
  )
}
