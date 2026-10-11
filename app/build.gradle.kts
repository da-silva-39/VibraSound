plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "mz.ac.ucm.feng.ti.vibrasound"

    // compileSdk 37 - SDK presente no ambiente. Sem dependencias AndroidX.
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "mz.ac.ucm.feng.ti.vibrasound"
        // minSdk 26 (Android 8.0): necessario para AudioFocusRequest e NotificationChannel.
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

// Apenas JUnit para testes unitarios simples. Nenhuma biblioteca AndroidX ou externa.
dependencies {
    testImplementation(libs.junit)
}
