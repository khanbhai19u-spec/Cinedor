plugins { id("com.android.application") }

val vName = (project.findProperty("vName") as String?) ?: "2.0.0"
val vCode = (project.findProperty("vCode") as String?)?.toIntOrNull() ?: 20000
val ks = file("release.jks")
// TMDB key comes from the GitHub secret TMDB_API_KEY (or -PtmdbKey=... locally). Never commit it.
val tmdbKey = (System.getenv("TMDB_API_KEY") ?: (project.findProperty("tmdbKey") as String?) ?: "").trim()

android {
    namespace = "com.cinedor.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.cinedor.player"
        minSdk = 24
        targetSdk = 34
        versionCode = vCode
        versionName = vName
        buildConfigField("String", "TMDB_API_KEY", "\"" + tmdbKey.replace("\"", "") + "\"")
    }

    buildFeatures { buildConfig = true }

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
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.viewpager2:viewpager2:1.1.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
    implementation("com.github.bumptech.glide:glide:4.16.0")
}
