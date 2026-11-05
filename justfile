# Same checks CI runs. JAVA_HOME falls back to the Gradle-provisioned JDK 21.
export JAVA_HOME := env("JAVA_HOME", home_directory() / ".gradle/jdks/temurin-21")

check:
    ./gradlew testReleaseUnitTest lintRelease assembleRelease -q
