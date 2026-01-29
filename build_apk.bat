@echo off
cd /d "C:\Users\lopez_3etit4k\.gemini\antigravity\playground\blazing-gemini\gf"
gradlew.bat assembleRelease --stacktrace > build_full_log.txt 2>&1
type build_full_log.txt
