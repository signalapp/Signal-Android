#! /usr/bin/env python3

import sys
from zipfile import ZipFile


class ApkDiff:
    IGNORE_FILES = [
        # Related to app signing. Not expected to be present in unsigned builds. Doesn't affect app code.
        "META-INF/MANIFEST.MF",
        "META-INF/CERTIFIC.SF",
        "META-INF/CERTIFIC.RSA",
        "META-INF/TEXTSECU.RSA",
        "META-INF/TEXTSECU.SF"
    ]

    def compare(self, firstApk, secondApk):
        firstZip = ZipFile(firstApk, 'r')
        secondZip = ZipFile(secondApk, 'r')

        return self.compareEntryNames(firstZip, secondZip) and self.compareEntryContents(firstZip, secondZip) == True

    def compareEntryNames(self, firstZip, secondZip):
        firstNameListSorted = sorted(firstZip.namelist())
        secondNameListSorted = sorted(secondZip.namelist())

        for ignoreFile in self.IGNORE_FILES:
            while ignoreFile in firstNameListSorted:
                firstNameListSorted.remove(ignoreFile)
            while ignoreFile in secondNameListSorted:
                secondNameListSorted.remove(ignoreFile)

        if len(firstNameListSorted) != len(secondNameListSorted):
            print("Manifest lengths differ!")

        for (firstEntryName, secondEntryName) in zip(firstNameListSorted, secondNameListSorted):
            if firstEntryName != secondEntryName:
                print("Sorted manifests don't match, %s vs %s" % (firstEntryName, secondEntryName))
                return False

        return True

    def compareEntryContents(self, firstZip, secondZip):
        firstInfoList = list(filter(lambda info: info.filename not in self.IGNORE_FILES, firstZip.infolist()))
        secondInfoList = list(filter(lambda info: info.filename not in self.IGNORE_FILES, secondZip.infolist()))

        if len(firstInfoList) != len(secondInfoList):
            print("APK info lists of different length!")
            return False

        success = True
        for firstEntryInfo in firstInfoList:
            for secondEntryInfo in list(secondInfoList):
                if firstEntryInfo.filename == secondEntryInfo.filename:
                    firstEntryBytes = firstZip.read(firstEntryInfo.filename)
                    secondEntryBytes = secondZip.read(secondEntryInfo.filename)

                    if firstEntryBytes != secondEntryBytes:
                        firstZip.extract(firstEntryInfo, "mismatches/first")
                        secondZip.extract(secondEntryInfo, "mismatches/second")
                        print("APKs differ on file %s! Files extracted to the mismatches/ directory." % (firstEntryInfo.filename))
                        success = False

                    secondInfoList.remove(secondEntryInfo)
                    break

        return success


if __name__ == '__main__':
    if len(sys.argv) != 3:
        print("Usage: apkdiff <pathToFirstApk> <pathToSecondApk>")
        sys.exit(1)

    if ApkDiff().compare(sys.argv[1], sys.argv[2]):
        print("APKs match!")
        sys.exit(0)
    else:
        print("APKs don't match!")
        sys.exit(1)
