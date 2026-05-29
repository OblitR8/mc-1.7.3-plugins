#!/bin/bash
# ============================================================
#  DiamondShop Build Script  (Linux / macOS)
#  For Beta 1.7.3 / CraftBukkit b1060 (Project Poseidon)
#
#  Place craftbukkit-b1060.jar in the same folder as this
#  script, then run:  chmod +x build.sh && ./build.sh
# ============================================================

CB="craftbukkit-b1060.jar"
SRC="src/main/java"
OUT="build/classes"
RES="src/main/resources"

if [ ! -f "$CB" ]; then
    echo "ERROR: $CB not found!"
    echo "Place the CraftBukkit b1060 jar next to this script."
    exit 1
fi

echo "Cleaning build directory..."
rm -rf build
mkdir -p "$OUT"

echo "Compiling sources..."
javac -source 1.6 -target 1.6 -cp "$CB" -d "$OUT" \
    "$SRC/com/shop/plugin/ShopItem.java" \
    "$SRC/com/shop/plugin/ShopManager.java" \
    "$SRC/com/shop/plugin/ShopPlugin.java"

if [ $? -ne 0 ]; then
    echo "Compilation FAILED."
    exit 1
fi

echo "Copying resources..."
cp "$RES/plugin.yml" "$OUT/plugin.yml"

echo "Packaging JAR..."
cd build/classes
jar cvf ../../DiamondShop.jar .
cd ../..

echo ""
echo "============================================================"
echo " SUCCESS! DiamondShop.jar is ready."
echo " Copy it into your server's /plugins folder and restart."
echo "============================================================"
