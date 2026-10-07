#!/bin/bash
# The harness2 regression: a fresh 4B test world built from the game data alone, then the vault, round trips,
# pictures, design and commissioning tests on copies of it. Runs headless.
#
#   run.sh GAMEDIR [OLDSAVES OLDAPP]
#     GAMEDIR           the folder with FTL's ftl.dat
#     (VICLOG=folder    optional, in the environment: a save logger's folder ending in a final victory, for VicT to replay)
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
echo "== SafeT"; run SafeT "$GAME" "$WORLD" "$W/safe" | grep -E "$PICK|^PASS"
echo "== BlueT"; run BlueT "$GAME" "$WORLD" "$W/blue" | grep -E "$PICK|^PASS"
echo "== HistT"; run HistT "$GAME" "$WORLD" "$W/hist" | grep -E "$PICK|^PASS"
echo "== PriceT"; run PriceT "$GAME" "$WORLD" "$W/price" | grep -E "$PICK|^PASS|^Kestrel|^Relief"
echo "== RuleT"; run RuleT "$GAME" "$WORLD" "$W/rule" | grep -E "$PICK|^PASS"
echo "== FleetT"; run FleetT "$GAME" "$WORLD" "$W/fleet" | grep -E "$PICK|^PASS"
echo "== TransT"; run TransT "$GAME" "$WORLD" "$W/trans" | grep -E "$PICK|^PASS"
echo "== ChainT"; run ChainT "$GAME" "$WORLD" "$W/chain" | grep -E "$PICK|^PASS"
for P in yes no early off; do echo "== ThirdT $P"; run ThirdT "$GAME" "$WORLD" "$W/third-$P" "$P" | grep -E "$PICK|^PASS"; done
echo "== DerT"; run DerT "$GAME" "$WORLD" "$W/der" | grep -E "$PICK|^PASS|^derelicts|^locked|^rebuild"
echo "== PatchT"; run PatchT "$GAME" "$WORLD" "$W/patch" | grep -E "$PICK|^PASS"
echo "== PartT"; run PartT "$GAME" "$WORLD" "$W/part" | grep -E "$PICK|^PASS"
echo "== ExpT"; run ExpT "$GAME" "$WORLD" "$W/exp" | grep -E "$PICK|^PASS|^expeditions"
echo "== AsgT"; run AsgT "$GAME" "$WORLD" "$W/asg" | grep -E "$PICK|^PASS|^three"
echo "== RestT"; run RestT "$GAME" "$WORLD" "$W/rest" | grep -E "$PICK|^PASS"
echo "== OverT"; run OverT "$GAME" "$WORLD" "$W/over" | grep -E "$PICK|^PASS"
echo "== LogT"; run LogT "$GAME" "$WORLD" "$W/log" | grep -E "$PICK|^PASS"
echo "== CrewT"; run CrewT "$GAME" "$WORLD" "$W/crew" | grep -E "$PICK|^PASS"
echo "== StrT"; run StrT "$GAME" "$WORLD" "$W/str" | grep -E "$PICK|^PASS"
echo "== RankT"; run RankT "$GAME" "$WORLD" "$W/rank" | grep -E "$PICK|^PASS"
echo "== AccT"; run AccT "$GAME" "$WORLD" "$W/acc" | grep -E "$PICK|^PASS"
echo "== CallOffT"; run CallOffT "$GAME" "$WORLD" "$W/calloff" | grep -E "$PICK|^PASS"
echo "== LockT"; run LockT "$GAME" "$WORLD" "$W/lock" | grep -E "$PICK|^PASS"
echo "== ConT"; run ConT "$GAME" "$WORLD" "$W/con" | grep -E "$PICK|^PASS"
echo "== DockT"; run DockT "$GAME" "$WORLD" "$W/dock" | grep -E "$PICK|^PASS"
echo "== RepT"; run RepT "$GAME" "$WORLD" "$W/rep" | grep -E "$PICK|^PASS"
echo "== StatT"; run StatT "$GAME" "$WORLD" "$W/stat" | grep -E "$PICK|^PASS"
echo "== RepuT"; run RepuT "$GAME" "$WORLD" "$W/repu" | grep -E "$PICK|^PASS"
echo "== UpdT"; run UpdT "$W/upd" | grep -E "$PICK|^PASS"
echo "== StoreT"; run StoreT "$W/store" | grep -E "$PICK|^PASS"
echo "== HomeT"; run HomeT "$GAME" "$WORLD" "$W/home" | grep -E "$PICK|^PASS"
echo "== ShipStoreT"; run ShipStoreT "$GAME" "$WORLD" "$W/shipstore" | grep -E "$PICK|^PASS"
echo "== VicT"; run VicT "$GAME" "$WORLD" "$W/vic" $VICLOG | grep -E "$PICK|^PASS|^replay"
echo "== LinkT"; run LinkT "$GAME" "$WORLD" "$W/link" | grep -E "$PICK|^PASS"
echo "== EventT"; run EventT "$GAME" "$WORLD" "$W/event" | grep -E "$PICK|^PASS|event logs"
# the windows themselves, driven as a player would: needs a display, so a virtual one
echo "== GuiT"
if command -v xvfb-run >/dev/null; then
	(cd "$W" && xvfb-run -a java -Dhomeplanet.noGameCheck=true -cp "$CP" GuiT "$GAME" "$WORLD" "$W/gui" 2>&1) | grep -E "$PICK|^PASS|^auction"
else echo "(GuiT skipped: no xvfb-run)"; fi

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
