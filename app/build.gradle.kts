plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
}

android {
    namespace = "com.yshalsager.mafza"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.yshalsager.mafza"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            manifestPlaceholders["app_label"] = "Mafza Debug"
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            manifestPlaceholders["app_label"] = "Mafza"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        jvmToolchain(17)
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

}

fun register_apk_rename(variant_name: String) {
    val task_suffix = variant_name.replaceFirstChar { char ->
        if (char.isLowerCase()) char.titlecase() else char.toString()
    }

    val rename_task = tasks.register("rename${task_suffix}ApkOutput") {
        doLast {
            val apk_dir = layout.buildDirectory.dir("outputs/apk/$variant_name").get().asFile
            val source_apk = apk_dir.resolve("app-$variant_name.apk")
            if (!source_apk.exists()) return@doLast

            val version_name = android.defaultConfig.versionName ?: "0.0.0"
            val target_name = "${rootProject.name.lowercase()}-${version_name}-${variant_name}.apk"
            val target_apk = apk_dir.resolve(target_name)

            source_apk.copyTo(target_apk, overwrite = true)
        }
    }

    tasks.matching { it.name == "assemble$task_suffix" }.configureEach {
        finalizedBy(rename_task)
    }
}

register_apk_rename("debug")
register_apk_rename("release")

dependencies {
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
