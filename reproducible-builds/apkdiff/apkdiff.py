#! /usr/bin/env python3

import sys
import re
import logging
from xml.etree.ElementTree import Element
from zipfile import ZipFile
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from typing import Optional
from collections import defaultdict

from androguard.core import axml
from loguru import logger

from util import deep_compare, format_differences
from tqdm import tqdm

logging.getLogger("deepdiff").setLevel(logging.ERROR)

logger.disable("androguard")


@dataclass
class XmlDifference:
    """Represents a difference between two XML elements."""

    diff_type: str  # "tag", "attribute", "text", "child_count"
    path: str
    attribute_name: Optional[str] = None
    first_value: Optional[str] = None
    second_value: Optional[str] = None
    child_tag: Optional[str] = None


IGNORE_FILES = [
    # Related to app signing. Not expected to be present in unsigned builds. Doesn"t affect app code.
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

ALLOWED_ARSC_DIFF_PATHS = [".res1"]


def compare(apk1, apk2) -> bool:
    print(f"Comparing: \n\t{apk1}\n\t{apk2}\n")

    print("Unzipping...")
    zip1 = ZipFile(apk1, "r")
    zip2 = ZipFile(apk2, "r")

    return compare_entry_names(zip1, zip2) and compare_entry_contents(zip1, zip2) == True


def compare_entry_names(zip1: ZipFile, zip2: ZipFile) -> bool:
    print("Comparing zip entry names...")
    name_list_sorted_1 = sorted(zip1.namelist())
    name_list_sorted_2 = sorted(zip2.namelist())

    for ignoreFile in IGNORE_FILES:
        while ignoreFile in name_list_sorted_1:
            name_list_sorted_1.remove(ignoreFile)
        while ignoreFile in name_list_sorted_2:
            name_list_sorted_2.remove(ignoreFile)

    if len(name_list_sorted_1) != len(name_list_sorted_2):
        print("Manifest lengths differ!")

    for entry_name_1, entry_name_2 in zip(name_list_sorted_1, name_list_sorted_2):
        if entry_name_1 != entry_name_2:
            print("Sorted manifests don't match, %s vs %s" % (entry_name_1, entry_name_2))
            return False

    return True


def compare_entry_contents(zip1: ZipFile, zip2: ZipFile) -> bool:
    print("Comparing zip entry contents...")
    info_list_1 = list(filter(lambda info: info.filename not in IGNORE_FILES, zip1.infolist()))
    info_list_2 = list(filter(lambda info: info.filename not in IGNORE_FILES, zip2.infolist()))

    if len(info_list_1) != len(info_list_2):
        print("APK info lists of different length!")
        return False

    success = True
    for entry_info_1 in info_list_1:
        for entry_info_2 in list(info_list_2):
            if entry_info_1.filename == entry_info_2.filename:
                entry_bytes_1 = zip1.read(entry_info_1.filename)
                entry_bytes_2 = zip2.read(entry_info_2.filename)

                if entry_bytes_1 != entry_bytes_2 and not handle_special_cases(entry_info_1.filename, entry_bytes_1, entry_bytes_2):
                    zip1.extract(entry_info_1, "mismatches/first")
                    zip2.extract(entry_info_2, "mismatches/second")
                    print(f"APKs differ on file {entry_info_1.filename}! Files extracted to the mismatches/ directory.")
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
        return compare_android_xml(bytes1, bytes2)
    elif filename == "resources.arsc":
        print("Comparing resources.arsc (may take a while)...")
        return compare_resources_arsc(bytes1, bytes2)
    elif re.match("res/xml/splits[0-9]+\\.xml", filename):
        print(f"Comparing {filename}...")
        return compare_split_xml(bytes1, bytes2)

    return False


def compare_android_xml(bytes1: bytes, bytes2: bytes) -> bool:
    all_differences = compare_xml(bytes1, bytes2)
    bad_differences = []

    for diff in all_differences:
        is_split_attr = diff.diff_type == "attribute" and diff.path in ["manifest", "manifest/application"] and "split" in diff.attribute_name.lower()
        is_meta_attr = diff.diff_type == "attribute" and diff.path == "manifest/application/meta-data"
        is_meta_child_count = diff.diff_type == "child_count" and diff.child_tag == "meta-data"

        if not is_split_attr and not is_meta_attr and not is_meta_child_count and not is_meta_attr:
            bad_differences.append(diff)

    if bad_differences:
        print(bad_differences)
        return False

    return True


def compare_split_xml(bytes1: bytes, bytes2: bytes) -> bool:
    all_differences = compare_xml(bytes1, bytes2)
    bad_differences = []

    for diff in all_differences:
        is_language = diff.diff_type == "attribute" and diff.path == "splits/module/language/entry"

        if not is_language:
            bad_differences.append(diff)

    if bad_differences:
        print(bad_differences)
        return False

    return True


def compare_resources_arsc(first_entry_bytes: bytes, second_entry_bytes: bytes) -> bool:
    """
    Compares two resources.arsc files.
    Largely taken from https://github.com/TheTechZone/reproducible-tests/blob/d8c73772b87fbe337eb852e338238c95703d59d6/comparators/arsc_compare.py
    """
    first_arsc = axml.ARSCParser(first_entry_bytes)
    second_arsc = axml.ARSCParser(second_entry_bytes)

    all_package_names = sorted(set(first_arsc.packages.keys()) | set(second_arsc.packages.keys()))
    total_diffs = defaultdict(list)

    success = True

    for package_name in all_package_names:
        # Check if package exists in both files
        if package_name not in first_arsc.packages:
            print(f"Package only in source file: {package_name}")
            success = False
            continue

        if package_name not in second_arsc.packages:
            print(f"Package only in target file: {package_name}")
            success = False
            continue

        packages1 = first_arsc.packages[package_name]
        packages2 = second_arsc.packages[package_name]

        # Check package length
        if len(packages1) != len(packages2):
            print(f"Package length mismatch: {len(packages1)} vs {len(packages2)}")
            success = False
            continue

        # Compare each package element
        for i in tqdm(range(len(packages1))):
            pkg1 = packages1[i]
            pkg2 = packages2[i]

            if type(pkg1) != type(pkg2):
                print(f"Element type mismatch at index {i}: {type(pkg1).__name__} vs {type(pkg2).__name__}")
                success = False
                continue

            # Different comparison strategies based on type
            if isinstance(pkg1, axml.ARSCResTablePackage):
                diffs = deep_compare(pkg1, pkg2)
                if diffs:
                    print(f"Differences in ARSCResTablePackage at index {i}:")
                    total_diffs["ARSCResTablePackage"].append((i, diffs))
                    success = False

            elif isinstance(pkg1, axml.StringBlock):
                diffs = deep_compare(pkg1, pkg2)
                if diffs:
                    print(f"Differences in StringBlock at index {i}:")
                    total_diffs["StringBlock"].append((i, diffs))
                    success = False

            elif isinstance(pkg1, axml.ARSCHeader):
                diffs = deep_compare(pkg1, pkg2)
                if diffs:
                    print(f"Differences in ARSCHeader at index {i}:")
                    total_diffs["ARSCHeader"].append((i, diffs))
                    success = False

            elif isinstance(pkg1, axml.ARSCResTypeSpec):
                diffs = deep_compare(pkg1, pkg2)

                if diffs and not all(path in ALLOWED_ARSC_DIFF_PATHS for path in diffs.keys()):
                    print(f"Disallowed differences in ARSCResTypeSpec at index {i}:")
                    print(format_differences(diffs))
                    total_diffs["ARSCResTypeSpec"].append((i, diffs))
                    success = False

            elif isinstance(pkg1, axml.ARSCResTableEntry):
                # Use string representation for comparison
                if pkg1.__repr__() != pkg2.__repr__():
                    print(f"Differences in ARSCResTableEntry at index {i}")
                    print(f"Target: {pkg1.__repr__()}", 3)
                    print(f"Source: {pkg2.__repr__()}", 3)
                    total_diffs["ARSCResTableEntry"].append((i, {"representation": f"{pkg1.__repr__()} vs {pkg2.__repr__()}"}))
                    success = False

            elif isinstance(pkg1, list):
                if pkg1 != pkg2:
                    print(f"List difference at index {i}")
                    total_diffs["list"].append((i, {"diff": "Lists differ"}))
                    success = False

            elif isinstance(pkg1, axml.ARSCResType):
                diffs = deep_compare(pkg1, pkg2)
                if diffs:
                    print(f"Differences in ARSCResType at index {i}:")
                    total_diffs["ARSCResType"].append((i, diffs))
                    success = False
            else:
                # Other types
                print(f"Unhandled type: {type(pkg1).__name__} at index {i}")
                diffs = deep_compare(pkg1, pkg2)
                if diffs:
                    total_diffs[type(pkg1).__name__].append((i, diffs))
                    success = False

    for type_name, diffs in total_diffs.items():
        if diffs:
            print(f"  {type_name}: {len(diffs)}", 1)

    if not success:
        print("Files have differences beyond the allowed .res1 differences.")
    return True


def compare_xml(bytes1: bytes, bytes2: bytes) -> list[XmlDifference]:
    printer = axml.AXMLPrinter(bytes1)
    entry_text_1 = printer.get_xml().decode("utf-8")

    printer = axml.AXMLPrinter(bytes2)
    entry_text_2 = printer.get_xml().decode("utf-8")

    if entry_text_1 == entry_text_2:
        return True

    root1 = ET.fromstring(entry_text_1)
    root2 = ET.fromstring(entry_text_2)

    return compare_xml_elements(root1, root2)


def compare_xml_elements(elem1: Element, elem2: Element, path: str = "") -> list[XmlDifference]:
    """Recursively compare two XML elements and return list of XmlDifference objects."""
    differences: list[XmlDifference] = []

    # Build current path
    current_path = f"{path}/{elem1.tag}" if path else elem1.tag

    # Compare tags
    if elem1.tag != elem2.tag:
        differences.append(XmlDifference(diff_type="tag", path=path, first_value=elem1.tag, second_value=elem2.tag))
        return differences

    # Compare attributes
    attrs1 = elem1.attrib
    attrs2 = elem2.attrib

    all_keys = set(attrs1.keys()) | set(attrs2.keys())
    for key in sorted(all_keys):
        val1 = attrs1.get(key)
        val2 = attrs2.get(key)

        if val1 != val2:
            differences.append(XmlDifference(diff_type="attribute", path=current_path, attribute_name=key, first_value=val1, second_value=val2))

    # Compare text content
    text1 = (elem1.text or "").strip()
    text2 = (elem2.text or "").strip()
    if text1 != text2:
        differences.append(XmlDifference(diff_type="text", path=current_path, first_value=text1, second_value=text2))

    # Compare children
    children1 = list(elem1)
    children2 = list(elem2)

    # Try to match children by tag name for comparison
    children1_by_tag = {}
    for child in children1:
        children1_by_tag.setdefault(child.tag, []).append(child)

    children2_by_tag = {}
    for child in children2:
        children2_by_tag.setdefault(child.tag, []).append(child)

    # Compare children with matching tags
    all_child_tags = set(children1_by_tag.keys()) | set(children2_by_tag.keys())
    for tag in sorted(all_child_tags):
        list1 = children1_by_tag.get(tag, [])
        list2 = children2_by_tag.get(tag, [])

        if len(list1) != len(list2):
            differences.append(XmlDifference(diff_type="child_count", path=current_path, child_tag=tag, first_value=str(len(list1)), second_value=str(len(list2))))

        # Compare matching elements recursively
        for child1, child2 in zip(list1, list2):
            differences.extend(compare_xml_elements(child1, child2, current_path))

    return differences


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
