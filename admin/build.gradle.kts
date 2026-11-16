plugins { id("com.android.application") }

val vName = (project.findProperty("vName") as String?) ?: "1.0.0"
val vCode = (project.findProperty("vCode") as String?)?.toIntOrNull() ?: 1000000
val ks = file("release.jks")

android {
    namespace = "com.cinedor.console"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.cinedor.console"
        minSdk = 24
        targetSdk = 34
        versionCode = vCode
        versionName = vName
    }

    sourceSets {
        getByName("main") {
            manifest.srcFile("AndroidManifest.xml")
            java.setSrcDirs(listOf("java"))
            res.setSrcDirs(listOf("res"))
        }
    }

    signingConfigs {
        create("release") {
            if (ks.exists()) {
                storeFile = ks
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = "cinedor"
                keyPassword = System.getenv("KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (ks.exists()) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint { abortOnError = false; checkReleaseBuilds = false }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
}
