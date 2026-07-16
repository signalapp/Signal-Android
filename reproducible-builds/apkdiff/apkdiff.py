#! /usr/bin/env python3

import difflib
import subprocess
import sys
import logging
from zipfile import ZipFile

from loguru import logger  # ty:ignore[unresolved-import]
from lxml import etree  # ty:ignore[unresolved-import]
from androguard.core.axml import AXMLPrinter  # ty:ignore[unresolved-import]

logging.getLogger("apkdiff").setLevel(logging.ERROR)
logger.disable("androguard")


def android_attr(name: str) -> str:
    return f"{{http://schemas.android.com/apk/res/android}}{name}"


# Related to app signing. Not expected to be present in unsigned builds. Doesn't affect app code.
IGNORE_FILES = [
    "META-INF/MANIFEST.MF",
    "META-INF/CERTIFIC.SF",
    "META-INF/CERTIFIC.RSA",
    "META-INF/TEXTSECU.SF",
    "META-INF/TEXTSECU.RSA",
    "META-INF/BNDLTOOL.SF",
    "META-INF/BNDLTOOL.RSA",
    "META-INF/code_transparency_signed.jwt",
    "stamp-cert-sha256",
]


# Play Store may add or modify attributes as part of the bundle process. Doesn't affect app code.
MANIFEST_IGNORE_ATTRIBUTES: dict[str, set[str]] = {
    "uses-sdk": {
        android_attr("minSdkVersion"),
    }
}

# Play Store may add or modify metadata as part of the bundle process. Doesn't affect app code.
MANIFEST_IGNORE_METADATA: list[str] = [
    "com.android.stamp.source",
    "com.android.stamp.type",
    "com.android.vending.derived.apk.id",
]


def compare(apk1, apk2) -> bool:
    print(f"Comparing: \n\t{apk1}\n\t{apk2}\n")

    print("Unzipping...")
    zip1 = ZipFile(apk1, "r")
    zip2 = ZipFile(apk2, "r")

    entry_names = compare_entry_names(zip1, zip2)
    entry_contents = compare_entry_contents(zip1, zip2)

    # Some splits (e.g. ABI config splits) contain no resource table. Compare when both APKs have one, treat both
    # missing as a match, and fail if only one of them has it.
    has_arsc_1 = "resources.arsc" in zip1.namelist()
    has_arsc_2 = "resources.arsc" in zip2.namelist()

    if has_arsc_1 and has_arsc_2:
        resources = compare_resources_arsc(apk1, apk2)
    elif has_arsc_1 != has_arsc_2:
        print("resources.arsc is present in only one of the APKs!")
        resources = False
    else:
        resources = True

    return entry_names and entry_contents and resources


def compare_entry_names(zip1: ZipFile, zip2: ZipFile) -> bool:
    print("Comparing zip entry names...")
    name_list_sorted_1 = sorted(zip1.namelist())
    name_list_sorted_2 = sorted(zip2.namelist())

    for ignoreFile in IGNORE_FILES:
        while ignoreFile in name_list_sorted_1:
            name_list_sorted_1.remove(ignoreFile)
        while ignoreFile in name_list_sorted_2:
            name_list_sorted_2.remove(ignoreFile)

    success = True
    if len(name_list_sorted_1) != len(name_list_sorted_2):
        print(
            f"Manifest lengths differ! {len(name_list_sorted_1)} vs {len(name_list_sorted_2)}"
        )
        success = False

    only_in_first = sorted(list(set(name_list_sorted_1) - set(name_list_sorted_2)))
    only_in_second = sorted(list(set(name_list_sorted_2) - set(name_list_sorted_1)))

    if only_in_first:
        print(f"Files present only in {zip1.filename}:")
        for name in only_in_first:
            print(f"  - {name}")
        success = False

    if only_in_second:
        print(f"Files present only in {zip2.filename}:")
        for name in only_in_second:
            print(f"  - {name}")
        success = False

    # If sets are identical but ordering differs, still report ordering mismatches
    if success:
        for entry_name_1, entry_name_2 in zip(name_list_sorted_1, name_list_sorted_2):
            if entry_name_1 != entry_name_2:
                print(f"Sorted manifests don't match: {entry_name_1} vs {entry_name_2}")
                success = False

    return success


def compare_entry_contents(zip1: ZipFile, zip2: ZipFile) -> bool:
    print("Comparing zip entry contents...")
    info_list_1 = list(
        filter(lambda info: info.filename not in IGNORE_FILES, zip1.infolist())
    )
    info_list_2 = list(
        filter(lambda info: info.filename not in IGNORE_FILES, zip2.infolist())
    )

    success = True
    if len(info_list_1) != len(info_list_2):
        print(
            f"APK info lists of different length! {len(info_list_1)} vs {len(info_list_2)}"
        )
        success = False

    for entry_info_1 in info_list_1:
        for entry_info_2 in list(info_list_2):
            if entry_info_1.filename == entry_info_2.filename:
                entry_bytes_1 = zip1.read(entry_info_1.filename)
                entry_bytes_2 = zip2.read(entry_info_2.filename)

                if entry_bytes_1 != entry_bytes_2 and not handle_special_cases(
                    entry_info_1.filename, entry_bytes_1, entry_bytes_2
                ):
                    zip1.extract(entry_info_1, "mismatches/first")
                    zip2.extract(entry_info_2, "mismatches/second")
                    print(
                        f"APKs differ on file {entry_info_1.filename}! Files extracted to the mismatches/ directory."
                    )
                    success = False

                info_list_2.remove(entry_info_2)
                break

    return success


def handle_special_cases(filename: str, bytes1: bytes, bytes2: bytes):
    """
    There are some specific files that expect will not be byte-for-byte identical. We want to ensure that the files
    are matching except these expected differences. The differences are all related to extra XML attributes that the
    Play Store may add as part of the bundle process. These differences do not affect the behavior of the app and are
    unfortunately unavoidable given the modern realities of the Play Store.
    """
    if filename == "AndroidManifest.xml":
        print("Comparing AndroidManifest.xml...")
        return axml_diff(filename, bytes1, bytes2)
    elif filename == "resources.arsc":
        # we will compare resources.arsc separately with aapt2, so we can ignore any differences here
        return True

    return False


def compare_resources_arsc(apk1: str, apk2: str) -> bool:
    print("Comparing resources.arsc...")

    resources1 = dump_resources(apk1)
    resources2 = dump_resources(apk2)

    if resources1 == resources2:
        return True
    else:
        print("resources.arsc files differ!")
        diff = difflib.unified_diff(
            resources1,
            resources2,
            fromfile=apk1,
            tofile=apk2,
            lineterm="",
        )
        for line in diff:
            print(line)
        return False


def dump_resources(apk):
    try:
        with subprocess.Popen(
            ["aapt2", "dump", "resources", apk],
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
        ) as process:
            stdout, stderr = process.communicate()
            if process.returncode != 0:
                raise RuntimeError(f"aapt2 failed with error: {stderr.strip()}")
    except FileNotFoundError:
        raise RuntimeError("aapt2 is not installed or not in the PATH.")

    return stdout.strip().splitlines()


def filter_tree(root: etree._Element) -> etree._Element:
    for element in root.findall(".//meta-data"):
        name = element.get(android_attr("name"), "")
        if any(name.startswith(prefix) for prefix in MANIFEST_IGNORE_METADATA):
            element.getparent().remove(element)

    for element in root.iter():
        for attr in MANIFEST_IGNORE_ATTRIBUTES.get(element.tag, ()):
            element.attrib.pop(attr, None)

    return root


def axml_diff(filename: str, bytes1: bytes, bytes2: bytes) -> bool:
    root_a = AXMLPrinter(bytes1).get_xml_obj()
    root_b = AXMLPrinter(bytes2).get_xml_obj()

    filtered_a = filter_tree(root_a)
    filtered_b = filter_tree(root_b)

    pretty_a = etree.tostring(filtered_a, pretty_print=True, encoding="unicode")
    pretty_b = etree.tostring(filtered_b, pretty_print=True, encoding="unicode")

    if pretty_a == pretty_b:
        return True
    else:
        print(f"{filename} files differ!")
        diff = difflib.unified_diff(
            a=pretty_a.splitlines(keepends=True),
            b=pretty_b.splitlines(keepends=True),
            fromfile="a/" + filename,
            tofile="b/" + filename,
        )
        sys.stdout.writelines(diff)
        return False


if __name__ == "__main__":
    if len(sys.argv) != 3:
        print("Usage: apkdiff <pathToFirstApk> <pathToSecondApk>")
        sys.exit(1)

    if compare(sys.argv[1], sys.argv[2]):
        print("APKs match!")
        sys.exit(0)
    else:
        print("APKs don't match!")
        sys.exit(1)
