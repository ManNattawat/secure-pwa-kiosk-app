import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-kapt")
}

tasks.register("scanJarTimestamps") {
    doLast {
        val minYear = 1980
        val maxYear = 2107
        var badCount = 0
        val processed = mutableSetOf<File>()

        fun recordIssue(source: String, entryName: String, millis: Long) {
            if (millis <= 0) {
                println("Suspicious timestamp ($millis ms): $source -> $entryName")
                badCount++
            } else {
                val year = ZonedDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault()).year
                if (year < minYear || year > maxYear) {
                    println("Bad timestamp: $source -> $entryName @ $year")
                    badCount++
                }
            }
        }

        fun scanNestedJar(source: String, inputStream: java.io.InputStream) {
            JarInputStream(inputStream).use { jis ->
                var nestedEntry: JarEntry? = jis.nextJarEntry
                while (nestedEntry != null) {
                    val currentEntry = nestedEntry
                    val millis = runCatching { currentEntry.time }.getOrDefault(-1L)
                    recordIssue(source, currentEntry.name, millis)
                    nestedEntry = jis.nextJarEntry
                }
            }
        }

        fun scanZipFile(fileObj: File) {
            ZipFile(fileObj).use { zip: ZipFile ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry: ZipEntry = entries.nextElement()
                    val millis = runCatching { entry.lastModifiedTime?.toMillis() ?: entry.time }.getOrDefault(entry.time)
                    recordIssue(fileObj.absolutePath, entry.name, millis)

                    if (!entry.isDirectory && entry.name.endsWith(".jar")) {
                        zip.getInputStream(entry).use { scanNestedJar("${fileObj.absolutePath}::${entry.name}", it) }
                    }
                }
            }
        }

        val configurationsToScan = listOf("debugRuntimeClasspath", "releaseRuntimeClasspath", "compileClasspath")

        configurationsToScan
            .mapNotNull { configName ->
                runCatching { project.configurations.getByName(configName).resolve() }.getOrNull()
            }
            .flatten()
            .filter { it.isFile }
            .filter { it.extension in setOf("jar", "aar") }
            .forEach { fileObj: File ->
                if (processed.add(fileObj)) {
                    if (fileObj.extension == "jar") {
                        JarFile(fileObj).use { jar ->
                            jar.entries().asSequence().forEach { entry ->
                                val millis = runCatching { entry.lastModifiedTime?.toMillis() ?: entry.time }.getOrDefault(entry.time)
                                recordIssue(fileObj.absolutePath, entry.name, millis)
                            }
                        }
                    } else {
                        scanZipFile(fileObj)
                    }
                }
            }

        if (badCount == 0) {
            println("No invalid jar timestamps found 🎉")
        }
    }
}

tasks.register("scanMetaInfTimestamps") {
    doLast {
        val minYear = 1980
        val maxYear = 2107
        var badCount = 0

        val configurationsToScan = listOf("debugRuntimeClasspath", "releaseRuntimeClasspath", "compileClasspath")

        configurationsToScan
            .mapNotNull { configName ->
                runCatching { project.configurations.getByName(configName).resolve() }.getOrNull()
            }
            .flatten()
            .forEach { fileObj: File ->
            if (fileObj.name.endsWith(".jar")) {
                JarFile(fileObj).use { jar: JarFile ->
                    jar.entries().asSequence()
                        .filter { entry: JarEntry -> entry.name.startsWith("META-INF/") }
                        .forEach { entry: JarEntry ->
                            val millis = try {
                                entry.lastModifiedTime?.toMillis() ?: entry.time
                            } catch (_: Throwable) {
                                entry.time
                            }

                            if (millis > 0) {
                                val year = ZonedDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault()).year
                                if (year < minYear || year > maxYear) {
                                    println("Bad timestamp: ${fileObj.name} -> ${entry.name} @ $year")
                                    badCount++
                                }
                            } else {
                                println("Suspicious timestamp (0 ms): ${fileObj.name} -> ${entry.name}")
                                badCount++
                            }
                        }
                }
            }
        }

        if (badCount == 0) {
            println("No META-INF timestamp issues found 🎉")
        }
    }
}

tasks.register("listMetaInfConflicts") {
    doLast {
        val seen = mutableSetOf<String>()
        val duplicates = mutableSetOf<String>()

        project.configurations.getByName("compileClasspath").resolve().forEach { file ->
            if (file.isFile && file.extension == "jar") {
                JarFile(file).use { jar ->
                    jar.entries().asSequence()
                        .filter { it.name.startsWith("META-INF/") }
                        .forEach { entry ->
                            if (!seen.add(entry.name)) {
                                duplicates.add(entry.name)
                            }
                        }
                }
            }
        }

        println("=== META-INF duplicates ===")
        if (duplicates.isEmpty()) {
            println("No duplicates found 🎉")
        } else {
            duplicates.forEach { println(it) }
        }
    }
}

android {
    namespace = "com.gse.securekiosk"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.gse.securekiosk"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }
        getByName("debug") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources.excludes += setOf(
            "META-INF/DEPENDENCIES",
            "META-INF/LICENSE",
            "META-INF/LICENSE.txt",
            "META-INF/LICENSE*",
            "META-INF/NOTICE",
            "META-INF/NOTICE.txt",
            "META-INF/NOTICE*",
            "META-INF/ASL2.0",
            "META-INF/AL2.0",
            "META-INF/LGPL2.1",
            "META-INF/*.kotlin_module",
            "META-INF/versions/9/previous-compilation-data.bin",
            "META-INF/androidx.print_print.version",
            "META-INF/androidx.localbroadcastmanager_localbroadcastmanager.version",
            "META-INF/com/android/build/gradle/aar-metadata.properties",
            "META-INF/com/android/build/gradle/**"
        )
    }
}

dependencies {
    val lifecycleVersion = "2.8.4"
    val workVersion = "2.8.1"
    val okhttpVersion = "4.12.0"
    val coroutinesVersion = "1.8.1"

    // implementation("androidx.core:core:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0") {
        exclude(group = "androidx.profileinstaller", module = "profileinstaller")
        exclude(group = "androidx.arch.core", module = "core-runtime")
        exclude(group = "androidx.lifecycle", module = "lifecycle-viewmodel-savedstate")
        exclude(group = "androidx.lifecycle", module = "lifecycle-livedata-core")
        exclude(group = "androidx.lifecycle", module = "lifecycle-runtime-ktx")
        exclude(group = "androidx.vectordrawable", module = "vectordrawable")
        exclude(group = "androidx.viewpager", module = "viewpager")
        exclude(group = "androidx.appcompat", module = "appcompat-resources")
        exclude(group = "androidx.savedstate", module = "savedstate")
        exclude(group = "androidx.collection", module = "collection")
    }
    // implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:$lifecycleVersion") {
        exclude(group = "androidx.profileinstaller", module = "profileinstaller")
        exclude(group = "androidx.arch.core", module = "core-runtime")
    }
    implementation("androidx.sqlite:sqlite:2.4.0")

    implementation("com.google.android.material:material:1.12.0") {
        exclude(group = "androidx.legacy", module = "legacy-support-core-utils")
        exclude(group = "androidx.cardview", module = "cardview")
        exclude(group = "androidx.interpolator", module = "interpolator")
        exclude(group = "androidx.dynamicanimation", module = "dynamicanimation")
        exclude(group = "androidx.arch.core", module = "core-runtime")
    }

    implementation("androidx.security:security-crypto:1.1.0-alpha06") {
        exclude(group = "androidx.profileinstaller", module = "profileinstaller")
    }
    implementation("com.squareup.okhttp3:okhttp:$okhttpVersion")
    implementation("com.squareup.okhttp3:logging-interceptor:$okhttpVersion")
    implementation("com.google.android.gms:play-services-location:21.0.1")
    implementation("com.google.android.gms:play-services-auth:21.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
    implementation("androidx.webkit:webkit:1.9.0")

    constraints {
    }

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
