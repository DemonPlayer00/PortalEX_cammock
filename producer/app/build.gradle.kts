plugins { id("com.android.application") }
android {
    namespace = "moe.fuqiuluo.cammockproducer"
    compileSdk = 36
    defaultConfig {
        applicationId = "moe.fuqiuluo.cammockproducer"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }
    buildTypes { release { isMinifyEnabled = false } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
