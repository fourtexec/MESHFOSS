buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        // AGP 9 включает встроенную поддержку Kotlin. Явный KGP нужен, чтобы
        // использовать актуальный стабильный компилятор, а не версию по умолчанию.
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.21")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
