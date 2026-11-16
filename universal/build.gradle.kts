plugins { id("com.android.application") }

android {
    namespace = "com.tvlink.ui"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tvlink.universal"
        minSdk = 24
        targetSdk = 34
        versionCode = providers.gradleProperty("universalVersionCode").map(String::toInt).getOrElse(1)
        versionName = "1.0.12"
    }

    sourceSets {
        named("main") {
            java.srcDirs("../mobile/src/main/java", "../tv/src/main/java")
            res.srcDir("../tv/src/main/res")
        }

        // Keep the two resource trees on different merge priorities.
        named("debug") { res.srcDir("../mobile/src/main/res") }
        named("release") { res.srcDir("../mobile/src/main/res") }
    }
}
