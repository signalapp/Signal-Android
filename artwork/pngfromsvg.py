#!/usr/bin/python3

import os
import shlex, subprocess
import tempfile
import shutil

FILES = ["ic_video", "ic_audio"]
DIRS = ["drawable-mdpi", "drawable-hdpi", "drawable-xhdpi", "drawable-xxhdpi"]
ISIZE = 150
SIZES = [ISIZE, ISIZE * 1.5, ISIZE * 2, ISIZE * 3]

# see http://www.google.com/design/spec/style/icons.html#icons-system-icons
# bottom-most section
SUFFIXES = ["light", "dark"]
COLORS = ["#000000", "#ffffff"]
OPACITIES = [0.54, 1.0]

DIR_PNGS = "../res/"

def create_images(name):
    for s in range(len(SIZES)):
        size = SIZES[s]
        output_dir = DIR_PNGS + DIRS[s] + "/"
        svg = name + '.svg'

        if not os.path.isdir(output_dir):
            os.makedirs(output_dir, exist_ok=True)

        for t in range(len(SUFFIXES)):
            suffix = SUFFIXES[t]
            output = output_dir + name + "_" + suffix + ".png"
            print (svg + ' -> ' + output)

            tmp_svg = tempfile.mkstemp(".svg")[1]

            f = open(svg, 'r')
            data = f.read()
            f.close()

            data = data.replace(
                "fill=\"#000000\"",
                "fill=\"" + COLORS[t] + "\"")
            data = data.replace( \
                "fill-opacity=\"1.0\"",
                "fill-opacity=\"" + str(OPACITIES[t]) + "\"")

            f = open(tmp_svg, 'w')
            f.write(data)
            f.close()

            create_png(tmp_svg, output, size)

            os.remove(tmp_svg)

def create_png(svg, output, size):
    cmd = "inkscape -C -e " + output + " -h " + str(size) + " " + svg
    args = shlex.split(cmd)
    subprocess.call(args)

    tmp_png = tempfile.mkstemp(".png")[1]
    shutil.copyfile(output, tmp_png)

    cmd = "pngcrush " + tmp_png + " " + output
    args = shlex.split(cmd)
    print (args)
    subprocess.call(args)

    os.remove(tmp_png)

if __name__ == "__main__":

    for basename in FILES:
        print (basename)
        create_images(basename)
