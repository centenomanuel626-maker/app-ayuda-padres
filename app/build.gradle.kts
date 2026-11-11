plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Datos de Supabase del proyecto "Ayuda Padres". La clave anon es pública por diseño
// (va dentro del APK); la seguridad la dan las reglas RLS y el código de familia.
// Se pueden cambiar con variables de entorno (secretos de GitHub) o en local.properties.
val defaults = mapOf(
    "SUPABASE_URL" to "https://shbhfmkwphfofnijhxgd.supabase.co",
    "SUPABASE_ANON_KEY" to "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InNoYmhmbWt3cGhmb2ZuaWpoeGdkIiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTQ0MjcxNDYsImV4cCI6MjExMDAwMzE0Nn0.EYeEioGuLQKcxJJBs4pZ13M2XBv43o9Up1QkdPtjMks"
)

fun secret(name: String): String {
    val fromEnv = System.getenv(name)
    if (!fromEnv.isNullOrBlank()) return fromEnv
    val f = rootProject.file("local.properties")
    if (f.exists()) {
        val p = java.util.Properties()
        f.inputStream().use { p.load(it) }
        val v = p.getProperty(name, "")
        if (v.isNotBlank()) return v
    }
    return defaults[name] ?: ""
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
            storePassword = secret("SIGNING_PASSWORD")
            keyAlias = "familia"
            keyPassword = secret("SIGNING_PASSWORD")
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
