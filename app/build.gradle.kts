plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Datos de Supabase: se leen de variables de entorno (secretos de GitHub) o de local.properties.
fun secret(name: String): String {
    val fromEnv = System.getenv(name)
    if (!fromEnv.isNullOrBlank()) return fromEnv
    val f = rootProject.file("local.properties")
    if (f.exists()) {
        val p = java.util.Properties()
        f.inputStream().use { p.load(it) }
        return p.getProperty(name, "")
    }
    return ""
}

android {
    namespace = "com.manuel.ayudapadres"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.manuel.ayudapadres"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "SUPABASE_URL", "\"${secret("SUPABASE_URL")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${secret("SUPABASE_ANON_KEY")}\"")
    }

    signingConfigs {
        create("familia") {
            storeFile = file("familia.keystore")
            storePassword = "ayudapadres"
            keyAlias = "familia"
            keyPassword = "ayudapadres"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("familia")
        }
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("familia")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}
