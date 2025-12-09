plugins {
	id("com.android.library")
}

android {
	namespace = "com.tailscale.libtailscale"
	compileSdk = libs.versions.android.compileSdk.get().toInt()

	defaultConfig {
		minSdk = 26
	}
}

dependencies {
	api(files("../third_party/tailscale-android/android/libs/libtailscale.aar"))
}
