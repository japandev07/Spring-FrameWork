#!/bin/bash
set -e

case "$1" in
	java8)
		 echo "https://github.com/adoptium/temurin8-binaries/releases/download/jdk8u322-b06/OpenJDK8U-jdk_x64_linux_hotspot_8u322b06.tar.gz"
	;;
	java11)
		 echo "https://github.com/adoptium/temurin11-binaries/releases/download/jdk-11.0.14.1%2B1/OpenJDK11U-jdk_x64_linux_hotspot_11.0.14.1_1.tar.gz"
	;;
  java17)
		 echo "https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.2%2B8/OpenJDK17U-jdk_x64_linux_hotspot_17.0.2_8.tar.gz"
  ;;
  java18)
		 echo "https://github.com/adoptium/temurin18-binaries/releases/download/jdk18-2022-02-12-08-06-beta/OpenJDK18-jdk_x64_linux_hotspot_2022-02-12-08-06.tar.gz"
  ;;
  *)
		echo $"Unknown java version"
		exit 1
esac
