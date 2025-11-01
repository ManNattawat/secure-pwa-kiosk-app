plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
}

allprojects {
    configurations.all {
        exclude(group = "androidx.profileinstaller", module = "profileinstaller")
        // Removed exclude for androidx.arch.core:core-runtime
        // because androidx.lifecycle requires it
    }
}
