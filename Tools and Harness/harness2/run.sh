#!/bin/bash
# The harness2 regression: a fresh 4B test world built from the game data alone, then the vault, round trips,
# pictures, design and commissioning tests on copies of it. Runs headless.
#
#   run.sh GAMEDIR [OLDSAVES OLDAPP]
#     GAMEDIR           the folder with FTL's ftl.dat
#     OLDSAVES OLDAPP   optional: an old FTL Homeworld saves folder and program folder, to also test the
#                       HW to FHP converter (ConvT, StoT)
#
# Build the jar first (mvn package in the repo root). Scratch files go in harness2/work.
H="$(cd "$(dirname "$0")" && pwd)"
REPO="$(cd "$H/../.." && pwd)"
J="$REPO/target/Federation Home Planet.jar"
GAME="${1:?usage: run.sh GAMEDIR [OLDSAVES OLDAPP]}"
GAME="$(cd "$GAME" && pwd)"
W="$H/work"
[ -f "$J" ] || { echo "No jar: build it first (mvn package in $REPO)"; exit 1; }
rm -rf "$W" && mkdir -p "$W/classes"
javac -cp "$J" -d "$W/classes" "$H"/*.java 2>&1 | grep -v "^Picked up"; echo "harness compiled (errors above, if any)"
CP="$J:$W/classes"
run() { (cd "$W" && java -Djava.awt.headless=true -Dhomeplanet.noGameCheck=true -cp "$CP" "$@" 2>&1) | grep -v "^Picked up\|SLF4J\|^[0-9:.]* \[main\] \(INFO\|DEBUG\|WARN\)" ; }
PICK="FAIL|ALL PASSED|FAILED|Exception|at org|at homeplanet|unreadable"

echo "== WorldT"; run WorldT "$GAME" "$W/world" | grep -E "$PICK"
WORLD="$W/world/saves"
echo "== VaultT"; run VaultT "$GAME" "$WORLD" "$W/vault" | grep -E "$PICK"
echo "== RoundT"; run RoundT "$GAME" "$WORLD" | grep -E "$PICK|identical|DIFF"
echo "== PicT"; run PicT "$GAME" "$WORLD" | grep -cE "img=[0-9]" | sed "s/^/ships drawn: /"
echo "== DesT"; run DesT "$GAME" "$WORLD" "$W/des" | grep -E "$PICK"
echo "== CommT"; run CommT "$GAME" "$WORLD" "$W/comm" | grep -E "$PICK"

# the converter, only with old Homeworld data to convert
if [ $# -ge 3 ]; then
	OLDSAVES="$(cd "$2" && pwd)"; OLDAPP="$(cd "$3" && pwd)"
	CV="$H/../hw2fhp-converter"; C="$CV/dist/HW to FHP Converter.jar"
	"$CV/build.sh" "$J" >/dev/null 2>&1 || echo "converter build failed"
	javac -cp "$J:$C:$W/classes" -d "$W/classes" "$CV"/test/*.java 2>&1 | grep -v "^Picked up"
	CP="$J:$C:$W/classes"
	echo "== ConvT"; run ConvT "$GAME" "$OLDSAVES" "$OLDAPP" "$W/conv" afterFirstLaunch | grep -E "$PICK|at hw2fhp"
	echo "== StoT"; run StoT "$GAME" "$W/conv/saves" "$OLDSAVES/Homeworld.sav" "$OLDSAVES/HomeworldAE.sav" | grep -E "$PICK"
else
	echo "(converter tests skipped: no old Homeworld data given)"
fi
