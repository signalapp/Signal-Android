#!/usr/bin/python3

import os
import re
import functools
import shlex, subprocess
import tempfile
import shutil
import sys

files = ["ic_video", "ic_audio"]
dirs = ["drawable-mdpi", "drawable-hdpi", "drawable-xhdpi", "drawable-xxhdpi"]
isize = 150
sizes = [isize, isize * 1.5, isize * 2, isize * 3]

# see http://www.google.com/design/spec/style/icons.html#icons-system-icons
# bottom-most section
suffixes = ["light", "dark"]
colors = ["#000000", "#ffffff"]
opacities = [0.54, 1.0]

dirPngs = "../res/"

def createImages(name):
    for s in range(len(sizes)):
        size = sizes[s]
        outputDir = dirPngs + dirs[s] + "/"
        svg = name + ".svg"

        if not os.path.isdir(outputDir):
            os.makedirs(outputDir, exist_ok=True)

        for t in range(len(suffixes)):
            suffix = suffixes[t]
            output = outputDir + name + "_" + suffix + ".png"
            print(svg + " -> " + output)

            tmpSvg = tempfile.mkstemp(".svg")[1]

            f = open(svg,'r')
            data = f.read()
            f.close()

            data = data.replace( \
                "fill=\"#000000\"", \
                "fill=\"" + colors[t] + "\"");
            data = data.replace( \
                "fill-opacity=\"1.0\"", \
                "fill-opacity=\"" + str(opacities[t]) + "\"");

            f = open(tmpSvg,'w')
            f.write(data)
            f.close()

            createPng(tmpSvg, output, size)

            os.remove(tmpSvg)

def createPng(svg, output, size):
        cmd = "inkscape -C -e " + output + " -h " + str(size) + " " + svg
        args = shlex.split(cmd)
        p = subprocess.call(args)

        tmpPng = tempfile.mkstemp(".png")[1]
        shutil.copyfile(output, tmpPng)

        cmd = "pngcrush " + tmpPng + " " + output
        args = shlex.split(cmd)
        print(args)
        p = subprocess.call(args)

        os.remove(tmpPng)

if __name__ == "__main__":

    for basename in files:
        print (basename)
        createImages(basename)
