#!/bin/bash
set -e

case "$1" in
	java8)
		 echo "https://github.com/adoptium/temurin8-binaries/releases/download/jdk8u322-b06/OpenJDK8U-jdk_x64_linux_hotspot_8u322b06.tar.gz"
	;;
	java11)
		 echo "https://github.com/adoptium/temurin11-binaries/releases/download/jdk-11.0.14%2B9/OpenJDK11U-jdk_x64_linux_hotspot_11.0.14_9.tar.gz"
	;;
  java16)
		 echo "https://github.com/adoptium/temurin16-binaries/releases/download/jdk-16.0.2%2B7/OpenJDK16U-jdk_x64_linux_hotspot_16.0.2_7.tar.gz"
  ;;
  *)
		echo $"Unknown java version"
		exit 1
esac
