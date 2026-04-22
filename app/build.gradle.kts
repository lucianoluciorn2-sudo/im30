plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "br.com.jetinopay"
    compileSdk = 35

    defaultConfig {
        applicationId = "br.com.jetinopay"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        // IP e porta da JetinoPay API — altere conforme seu ambiente
        buildConfigField("String", "JETINOPAY_BASE_URL", "\"http://192.168.1.50:3000/\"")
        // Código da empresa no servidor SiTef (fornecido pela Software Express)
        buildConfigField("String", "SITEF_EMPRESA_ID", "\"00000000\"")
        // IP do servidor SiTef na rede local
        buildConfigField("String", "SITEF_IP", "\"192.168.1.10\"")
        // CNPJ do estabelecimento
        buildConfigField("String", "ESTABELECIMENTO_CNPJ", "\"00000000000000\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
        }
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)

    // Rede
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp.core)
    implementation(libs.okhttp.logging)
    implementation(libs.gson)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Lifecycle / ViewModel
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.lifecycle.runtime.ktx)

    // ─────────────────────────────────────────────────────────
    // SDKs fornecidos pela Software Express e PAX:
    //   Coloque os arquivos .aar na pasta app/libs/ e
    //   descomente as linhas abaixo após receber o pacote.
    // ─────────────────────────────────────────────────────────
    // implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar", "*.jar"))))
}
