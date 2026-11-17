#!/bin/sh
# Opens The Home Planet Station on Linux (the Steam Deck's Desktop Mode included), as the Interface .bat does on Windows.
# The Java, the first found: java/ beside the jar (a release's own, below), the JDK the Construction Yard gathered (one
# folder up, in tools/), JAVA_HOME, then the PATH. With none, it offers to get one: unpacked into java/ beside the station,
# since SteamOS keeps its system files locked and has no Java of its own (7.13).
# A zip made on Windows doesn't mark this file as runnable: the first time, run it with  sh "Federation Home Planet Interface.sh"
# The station's own messages go in its log files (logs/).

HERE=$(cd "$(dirname "$0")" && pwd)
cd "$HERE" || exit 1

case "$(uname -m)" in
	x86_64|amd64) ARCH=x64 ;;
	aarch64|arm64) ARCH=aarch64 ;;
	*) ARCH="" ;;
esac
JRE_URL="https://api.adoptium.net/v3/binary/latest/17/ga/linux/$ARCH/jre/hotspot/normal/eclipse?project=jdk"

# ---- findjava: sets JAVA to the first java found, or leaves it empty ----
findjava() {
	JAVA=""
	for j in "$HERE/java/bin/java" "$HERE/../tools/jdk/bin/java" "${JAVA_HOME:+$JAVA_HOME/bin/java}"; do
		if [ -n "$j" ] && [ -x "$j" ]; then JAVA="$j"; return 0; fi
	done
	JAVA=$(command -v java 2>/dev/null)
}

# ---- open: the station on its own, this script done (nothing waits on a terminal) ----
open() {
	nohup "$JAVA" -jar "$HERE/Federation Home Planet.jar" "$@" >/dev/null 2>&1 &
	exit 0
}

findjava
if [ -n "$JAVA" ]; then open "$@"; fi

# ---- no Java: the question needs a terminal; opened from a file manager there is none, so open one ----
if [ ! -t 0 ] && [ -z "$HP_IN_TERMINAL" ]; then
	export HP_IN_TERMINAL=1
	for t in konsole gnome-terminal xfce4-terminal x-terminal-emulator xterm; do
		if command -v "$t" >/dev/null 2>&1; then
			case "$t" in
				gnome-terminal) exec "$t" -- sh "$HERE/$(basename "$0")" "$@" ;;
				*) exec "$t" -e sh "$HERE/$(basename "$0")" "$@" ;;
			esac
		fi
	done
	msg="The Home Planet Station could not find Java. Run \"Federation Home Planet Interface.sh\" from a terminal to get one."
	if command -v kdialog >/dev/null 2>&1; then kdialog --error "$msg"
	elif command -v zenity >/dev/null 2>&1; then zenity --error --text="$msg"
	fi
	exit 1
fi

failed() { echo; printf "   Press Enter to close. "; read -r _; exit 1; }

clear 2>/dev/null
echo "=========================================================="
echo "   THE FEDERATION HOME PLANET STATION"
echo "   The connection could not be established: no Java was found."
echo "=========================================================="
echo
echo "   The station runs on Java 8 or newer. It can get one for you:"
echo
echo "   0: Cancel"
echo "   1: Download Java just for the station (about 45 MB)"
echo "      Unpacked into the \"java\" folder beside the station: no installer, no admin rights,"
echo "      nothing changed outside this folder. Delete the folder to remove it."
echo
printf "   Input -> "
read -r choice
[ "$choice" = "1" ] || exit 1

if [ -z "$ARCH" ]; then
	echo "Java for this computer's processor ($(uname -m)) isn't one the station can fetch. Install Java 8 or newer, then run this again."
	failed
fi

echo
echo "Downloading Java ..."
TGZ="$HERE/java-download.tar.gz"
if command -v curl >/dev/null 2>&1; then
	curl -L --fail --retry 5 --retry-delay 3 -C - -o "$TGZ" "$JRE_URL"   # resumes a cut-off download on the next run
elif command -v wget >/dev/null 2>&1; then
	wget -c -O "$TGZ" "$JRE_URL"
else
	echo "Neither curl nor wget is on this computer, so Java can't be downloaded. Install Java 8 or newer, then run this again."
	failed
fi
if [ ! -f "$TGZ" ]; then echo "The Java download failed: nothing was saved. Check the connection and run this again."; failed; fi
got=$(wc -c < "$TGZ")
if [ "$got" -lt 30000000 ]; then
	echo "The Java download stopped early ($got bytes). Run this again: it carries on where it left off."
	failed
fi

echo "Unpacking Java into \"$HERE/java\" ..."
rm -rf "$HERE/java" "$HERE/java-download.tmp" && mkdir "$HERE/java-download.tmp" \
	&& tar -xzf "$TGZ" -C "$HERE/java-download.tmp" \
	&& mv "$HERE/java-download.tmp"/*/ "$HERE/java" \
	&& rm -rf "$HERE/java-download.tmp" "$TGZ"
if [ ! -x "$HERE/java/bin/java" ]; then
	echo "Java could not be unpacked. Delete \"java-download.tar.gz\" and the \"java\" folder if they're there, then run this again."
	failed
fi
echo "Java is ready in the \"java\" folder. Establishing the connection ..."
JAVA="$HERE/java/bin/java"
open "$@"
