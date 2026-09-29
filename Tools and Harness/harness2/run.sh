#!/bin/bash
# The harness2 regression: migration, vault, round-trips, storage merge, pictures (and the design tests when present).
# Runs headless against the sandbox game data and a copy of the 4.23 test world.
J="/home/claude/fhp/target/Federation Home Planet.jar"
H=/home/claude/harness2
GAME=/home/claude/uitest/game
OLDSAVES=/tmp/saves.bak
OLDAPP=/home/claude/uitest/app
W=/tmp/h2work
cd $H && javac -cp "$J:." -d . *.java 2>&1 | grep -v "^Picked up"; echo "harness compiled (errors above, if any)"
run() { java -Djava.awt.headless=true -Dhomeplanet.noGameCheck=true -cp "$J:$H" "$@" 2>&1 | grep -v "^Picked up\|SLF4J\|^[0-9:.]* \[main\] \(INFO\|DEBUG\|WARN\)" ; }
# the converted world comes from the (separate) HW to FHP converter, run the way a user would after a first launch
CV=/home/claude/hw2fhp; C="$CV/dist/HW to FHP Converter.jar"
(cd $CV && ./build.sh >/dev/null 2>&1 && javac -cp "$J:$C:$H" -d test test/ConvT.java 2>&1 | grep -v "^Picked up")
echo "== ConvT"; java -Djava.awt.headless=true -Dhomeplanet.noGameCheck=true -cp "$J:$C:$H:$CV/test" ConvT $GAME $OLDSAVES $OLDAPP $W/mig afterFirstLaunch 2>&1 | grep -E "FAIL|ALL PASSED|FAILED|Exception|at hw2fhp|at homeplanet"
echo "== VaultT"; run VaultT $GAME $W/mig/saves $W/vault | grep -E "FAIL|ALL PASSED|FAILED|Exception|at org"
echo "== RoundT"; run RoundT $GAME $W/mig/saves | grep -E "FAIL|ALL PASSED|FAILED|Exception|at org|identical"
echo "== StoT"; run StoT $GAME $W/mig/saves $OLDSAVES/Homeworld.sav $OLDSAVES/HomeworldAE.sav | grep -E "FAIL|ALL PASSED|FAILED|Exception|at org"
echo "== PicT"; run PicT $GAME $W/mig/saves | grep -cE "bp=" | sed "s/^/ships drawn: /"
if [ -f $H/DesT.class ]; then echo "== DesT"; run DesT $GAME $W/mig/saves $W/des | grep -E "FAIL|ALL PASSED|FAILED|Exception|at org"; fi
echo "== CommT"; run CommT $GAME $W/mig/saves $W/comm | grep -E "FAIL|ALL PASSED|FAILED|Exception|at homeplanet"
