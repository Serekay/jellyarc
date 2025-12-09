plugins {
	id("com.android.library")
}

android {
	namespace = "com.tailscale.embedded"
	compileSdk = libs.versions.android.compileSdk.get().toInt()

	defaultConfig {
		minSdk = libs.versions.android.minSdk.get().toInt()
		consumerProguardFiles("consumer-rules.pro")
	}

	buildFeatures {
		buildConfig = false
	}

	compileOptions {
		sourceCompatibility = JavaVersion.VERSION_1_8
		targetCompatibility = JavaVersion.VERSION_1_8
	}
}

dependencies {
	// libtailscale AAR - compileOnly here, actual dependency provided by app module
	compileOnly(files("../third_party/tailscale-android/android/libs/libtailscale.aar"))
}
