plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val jniLibsDir = file("src/main/jniLibs")
val hevtunLib = "libhev-socks5-tunnel.so"
val abis = listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
fun hasHevtunLib(): Boolean = abis.any { File(jniLibsDir, "$it/$hevtunLib").exists() }

val assetsDir = file("src/main/assets")
val paqetAbis = listOf("arm64-v8a", "armeabi-v7a")
fun hasPaqetBinaries(): Boolean =
    paqetAbis.any { File(assetsDir, "$it/paqet").exists() } || File(assetsDir, "paqet").exists()

val rootDir = rootProject.projectDir
val isWindows = org.gradle.internal.os.OperatingSystem.current().isWindows

// Init hev-socks5-tunnel and its nested submodules (yaml, lwip, hev-task-system, src/core) so NDK build finds them
val initHevtunSubmodules by tasks.register<Exec>("initHevtunSubmodules") {
    onlyIf { rootDir.resolve("hev-socks5-tunnel").isDirectory }
    workingDir = rootDir
    commandLine("git", "submodule", "update", "--init", "--recursive", "hev-socks5-tunnel")
    isIgnoreExitValue = false
}

// Fix Git symlinks on Windows (hev-socks5-tunnel submodules store symlinks as path-only files)
val fixHevtunSymlinks by tasks.register<Exec>("fixHevtunSymlinks") {
    onlyIf { isWindows && rootDir.resolve("hev-socks5-tunnel").isDirectory }
    workingDir = rootDir
    commandLine(
        "powershell", "-ExecutionPolicy", "Bypass", "-File",
        rootDir.resolve("fix-hevtun-symlinks.ps1").absolutePath
    )
    isIgnoreExitValue = false
    dependsOn(initHevtunSubmodules)
}

// Resolve Android NDK path (env > local.properties ndk.dir > sdk.dir/ndk/<version>)
fun findNdkPath(): File? {
    fun toDir(path: String): File? {
        val f = File(path).absoluteFile
        return if (f.isDirectory) f else null
    }
    System.getenv("ANDROID_NDK_HOME")?.let { toDir(it) }?.let { return it }
    System.getenv("NDK_HOME")?.let { toDir(it) }?.let { return it }
    val localProp = rootProject.file("local.properties")
    if (!localProp.exists()) return null
    // Parse local.properties (key=value, Java property-style escaping: \\ -> \, \: -> : for Windows)
    fun getProp(key: String): String? = localProp.readLines()
        .firstOrNull { it.trimStart().startsWith("$key=") }
        ?.substringAfter("=")?.trim()
        ?.replace("\\\\", "\\")
        ?.replace("\\:", ":")
    getProp("ndk.dir")?.let { path: String ->
        val f = File(path).absoluteFile
        if (f.isDirectory) {
            // If this is the NDK root (has ndk-build), use it
            if (File(f, "ndk-build").exists() || File(f, "ndk-build.cmd").exists()) return f
            // Else treat as sdk/ndk folder containing version dirs (e.g. 29.0.14206865)
            val versionDirs = f.listFiles()?.filter { it.isDirectory && it.name.matches(Regex("^\\d+(\\.\\d+)+$")) }.orEmpty()
            versionDirs.maxByOrNull { it.name }?.let { return it }
        }
    }
    val sdkPath = getProp("sdk.dir") ?: return null
    val sdkDir = File(sdkPath).absoluteFile
    if (!sdkDir.isDirectory) return null
    val ndkRoot = File(sdkDir, "ndk")
    if (!ndkRoot.isDirectory) return null
    val versionDirs = ndkRoot.listFiles()?.filter { it.isDirectory && it.name.matches(Regex("^\\d+(\\.\\d+)+$")) }.orEmpty()
    return versionDirs.maxByOrNull { it.name }
}

// Build hev-socks5-tunnel native lib (same as v2rayNG); runs when lib is missing and NDK is available
val buildHevtun by tasks.register<Exec>("buildHevtun") {
    onlyIf {
        if (hasHevtunLib()) return@onlyIf false
        val ndk = findNdkPath()
        if (ndk == null || !ndk.isDirectory) {
            logger.warn("Android NDK not found. Skipping hev-socks5-tunnel build. App will build but TUN routing requires the native lib. Set ANDROID_NDK_HOME or install NDK via SDK Manager, then rebuild.")
            return@onlyIf false
        }
        true
    }
    dependsOn(fixHevtunSymlinks)
    workingDir = rootDir
    commandLine("bash", rootDir.resolve("compile-hevtun.sh").absolutePath)
    isIgnoreExitValue = false
    doFirst {
        if (!rootDir.resolve("hev-socks5-tunnel").isDirectory) {
            throw GradleException("hev-socks5-tunnel not found. Run: git submodule update --init --recursive")
        }
        val ndk = findNdkPath()!!
        environment("NDK_HOME", ndk.absolutePath)
    }
}

// Build paqet (Go) for Android. Fails the build if binaries are missing (no skip).
val buildPaqet by tasks.register<Exec>("buildPaqet") {
    onlyIf { !hasPaqetBinaries() }
    workingDir = rootDir
    commandLine("bash", rootDir.resolve("compile-paqet.sh").absolutePath)
    isIgnoreExitValue = false
    doFirst {
        if (!rootDir.resolve("paqet").isDirectory) {
            throw GradleException("paqet submodule not found. Run: git submodule update --init paqet")
        }
        val ndk = findNdkPath()
        if (ndk == null || !ndk.isDirectory) {
            throw GradleException("Android NDK not found. Set ANDROID_NDK_HOME or ndk.dir in local.properties.")
        }
        environment("ANDROID_NDK_HOME", ndk.absolutePath)
    }
}

// Ensure lib/asset builds run: hevtun before mergeNativeLibs, paqet before mergeAssets
tasks.whenTaskAdded {
    if (name == "mergeDebugNativeLibs" || name == "mergeReleaseNativeLibs") {
        dependsOn(buildHevtun)
    }
    if (name == "mergeDebugAssets" || name == "mergeReleaseAssets") {
        dependsOn(buildPaqet)
    }
}

// Run buildPaqet at start of every build (fails if paqet binaries missing)
afterEvaluate {
    tasks.named("preBuild").configure { dependsOn(buildPaqet) }
}

android {
    namespace = "com.alirezabeigy.paqetng"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.alirezabeigy.paqetng"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Release signing: set KEYSTORE_FILE (or KEYSTORE_PATH), KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD
    // or use local signing/release.keystore + env KEYSTORE_PASSWORD (KEY_ALIAS defaults to "paqetng")
    signingConfigs {
        val keystorePath = System.getenv("KEYSTORE_FILE") ?: System.getenv("KEYSTORE_PATH")
            ?: rootProject.file("signing/release.keystore").takeIf { it.exists() }?.absolutePath
        val storePass = System.getenv("KEYSTORE_PASSWORD")
        if (keystorePath != null && storePass != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = storePass
                keyAlias = System.getenv("KEY_ALIAS") ?: "paqetng"
                keyPassword = System.getenv("KEY_PASSWORD") ?: storePass
            }
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
            isUniversalApk = true
        }
    }

    buildTypes {
        release {
            signingConfigs.findByName("release")?.let { signingConfig = it }
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        viewBinding = true
        compose = true
    }
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.preference.ktx)
    implementation(libs.gson)
    implementation(libs.zxing.core)
    implementation(libs.zxing.android.embedded)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    debugImplementation(libs.androidx.ui.tooling)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}