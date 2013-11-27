#!/bin/sh

PX=36
inkscape $1 -C --export-png=./res/drawable-ldpi/ic_launcher.png -y 0 --export-width=$PX --export-height=$PX

PX=48
inkscape $1 -C --export-png=./res/drawable-mdpi/ic_launcher.png -y 0 --export-width=$PX --export-height=$PX

PX=72
inkscape $1 -C --export-png=./res/drawable-hdpi/ic_launcher.png -y 0 --export-width=$PX --export-height=$PX

PX=96
inkscape $1 -C --export-png=./res/drawable-xhdpi/ic_launcher.png -y 0 --export-width=$PX --export-height=$PX

#PX=512
#inkscape '$1' --export-png=app_512.png -y 0 --export-width=$PX --export-height=$PX
