#!/bin/bash
# Builds "HW to FHP Converter.jar" against the Home Planet jar (which it borrows classes from at run time).
set -e
cd "$(dirname "$0")"
J="/home/claude/fhp/target/Federation Home Planet.jar"
rm -rf classes && mkdir -p classes dist
javac -source 8 -target 8 -cp "$J" -d classes src/hw2fhp/*.java 2>&1 | grep -v "^Picked up\|warning: \[options\]" || true
printf 'Manifest-Version: 1.0\nMain-Class: hw2fhp.Converter\nClass-Path: Federation%%20Home%%20Planet.jar\n' > classes/MANIFEST.MF
jar cfm "dist/HW to FHP Converter.jar" classes/MANIFEST.MF -C classes hw2fhp
printf '@echo off\r\ncd /d "%%~dp0"\r\njava -jar "HW to FHP Converter.jar"\r\nif errorlevel 1 pause\r\n' > "dist/HW to FHP Converter.bat"
ls -la dist
