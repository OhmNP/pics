Write-Host "Java Version:"
java -version
Write-Host "JAVA_HOME: $env:JAVA_HOME"
Write-Host "ANDROID_HOME: $env:ANDROID_HOME"

if (Test-Path "gradlew.bat") {
    Write-Host "gradlew.bat found. Attempting to run with --info..."
    .\gradlew.bat testDebugUnitTest --info
}
else {
    Write-Host "gradlew.bat NOT found!"
}
