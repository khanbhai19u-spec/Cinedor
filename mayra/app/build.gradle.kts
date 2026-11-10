plugins { id("com.android.application") }

val vName = (project.findProperty("vName") as String?) ?: "1.0.0"
val vCode = (project.findProperty("vCode") as String?)?.toIntOrNull() ?: 1
val ks = file("release.jks")

android {
    namespace = "com.mayra.assistant"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.mayra.assistant"
        minSdk = 26          // java.time (Claude SDK) needs Android 8.0+
        targetSdk = 34
        versionCode = vCode
        versionName = vName
    }

    signingConfigs {
        create("release") {
            if (ks.exists()) {
                storeFile = ks
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS") ?: "cinedor"
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

    packaging {
        resources {
            excludes += setOf(
                "META-INF/versions/**",
                "META-INF/DEPENDENCIES",
                "META-INF/INDEX.LIST",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "META-INF/AL2.0",
                "META-INF/LGPL2.1",
                "META-INF/*.kotlin_module",
                "META-INF/*.version",
                "META-INF/maven/**",
                "META-INF/proguard/**"
            )
        }
    }

    lint { abortOnError = false; checkReleaseBuilds = false }
}

dependencies {
    // Official Anthropic Java SDK (Claude API)
    implementation("com.anthropic:anthropic-java:3.7.0")
}
