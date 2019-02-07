#! /usr/bin/env python3

import sys
from zipfile import ZipFile

class ApkDiff:

    # "resources.arsc" is on the list due to a bug. https://issuetracker.google.com/issues/110237303
    IGNORE_FILES = ["resources.arsc", "META-INF/MANIFEST.MF", "META-INF/SIGNAL_S.RSA", "META-INF/SIGNAL_S.SF"]

    def compare(self, sourceApk, destinationApk):
        sourceZip      = ZipFile(sourceApk, 'r')
        destinationZip = ZipFile(destinationApk, 'r')

        if self.compareManifests(sourceZip, destinationZip) and self.compareEntries(sourceZip, destinationZip) == True:
            print("APKs match!")
        else:
            print("APKs don't match!")

    def compareManifests(self, sourceZip, destinationZip):
        sourceEntrySortedList      = sorted(sourceZip.namelist())
        destinationEntrySortedList = sorted(destinationZip.namelist())

        for ignoreFile in self.IGNORE_FILES:
            while ignoreFile in sourceEntrySortedList: sourceEntrySortedList.remove(ignoreFile)
            while ignoreFile in destinationEntrySortedList: destinationEntrySortedList.remove(ignoreFile)

        if len(sourceEntrySortedList) != len(destinationEntrySortedList):
            print("Manifest lengths differ!")

        for (sourceEntryName, destinationEntryName) in zip(sourceEntrySortedList, destinationEntrySortedList):
            if sourceEntryName != destinationEntryName:
                print("Sorted manifests don't match, %s vs %s" % (sourceEntryName, destinationEntryName))
                return False

        return True

    def compareEntries(self, sourceZip, destinationZip):
        sourceInfoList = sourceZip.infolist()
        destinationInfoList = destinationZip.infolist()

        for ignoreFile in self.IGNORE_FILES:
            for sourceEntryInfo in sourceInfoList:
                if sourceEntryInfo.filename == ignoreFile:
                    sourceInfoList.remove(sourceEntryInfo)
            for destinationEntryInfo in destinationInfoList:
                if destinationEntryInfo.filename == ignoreFile:
                    destinationInfoList.remove(destinationEntryInfo)

        if len(sourceInfoList) != len(destinationInfoList):
            print("APK info lists of different length!")
            return False

        for sourceEntryInfo in sourceInfoList:
            for destinationEntryInfo in list(destinationInfoList):
                if sourceEntryInfo.filename == destinationEntryInfo.filename:
                    sourceEntry      = sourceZip.open(sourceEntryInfo, 'r')
                    destinationEntry = destinationZip.open(destinationEntryInfo, 'r')

                    if self.compareFiles(sourceEntry, destinationEntry) != True:
                        print("APK entry %s does not match %s!" % (sourceEntryInfo.filename, destinationEntryInfo.filename))
                        return False

                    destinationInfoList.remove(destinationEntryInfo)
                    break

        return True

    def compareFiles(self, sourceFile, destinationFile):
        sourceChunk      = sourceFile.read(1024)
        destinationChunk = destinationFile.read(1024)

        while len(sourceChunk) != 0 or len(destinationChunk) != 0:
            if sourceChunk != destinationChunk:
                return False

            sourceChunk      = sourceFile.read(1024)
            destinationChunk = destinationFile.read(1024)

        return True

if __name__ == '__main__':
    if len(sys.argv) != 3:
        print("Usage: apkdiff <pathToFirstApk> <pathToSecondApk>")
        sys.exit(1)

    ApkDiff().compare(sys.argv[1], sys.argv[2])
