import sys
import re
import argparse
import sqlite3
import gzip
from progressbar import ProgressBar, Counter, Timer
from lxml import etree

parser = argparse.ArgumentParser(prog='apntool', description="""Process Android's apn xml files and drop them into an
                                                             easily queryable SQLite db. Tested up to version 9 of
                                                             their APN file.""")
parser.add_argument('-v', '--version', action='version', version='%(prog)s v1.1')
parser.add_argument('-i', '--input', help='the xml file to parse', default='apns.xml', required=False)
parser.add_argument('-o', '--output', help='the sqlite db output file', default='apns.db', required=False)
parser.add_argument('--quiet', help='do not show progress or verbose instructions', action='store_true', required=False)
parser.add_argument('--no-gzip', help="do not gzip after creation", action='store_true', required=False)
args = parser.parse_args()


def normalized(target):
    o2_typo = re.compile(r"02\.co\.uk")
    port_typo = re.compile(r"(\d+\.\d+\.\d+\.\d+)\.(\d+)")
    leading_zeros = re.compile(r"(/|\.|^)0+(\d+)")
    subbed = o2_typo.sub(r'o2.co.uk', target)
    subbed = port_typo.sub(r'\1:\2', subbed)
    subbed = leading_zeros.sub(r'\1\2', subbed)
    return subbed

try:
    connection = sqlite3.connect(args.output)
    cursor = connection.cursor()
    cursor.execute('SELECT SQLITE_VERSION()')
    version = cursor.fetchone()
    if not args.quiet:
        print("SQLite version: %s" % version)
        print("Opening %s" % args.input)

    cursor.execute("PRAGMA legacy_file_format=ON")
    cursor.execute("PRAGMA journal_mode=DELETE")
    cursor.execute("PRAGMA page_size=32768")
    cursor.execute("VACUUM")
    cursor.execute("DROP TABLE IF EXISTS apns")
    cursor.execute("""CREATE TABLE apns(_id INTEGER PRIMARY KEY, mccmnc TEXT, mcc TEXT, mnc TEXT, carrier TEXT,
                apn TEXT, mmsc TEXT, port INTEGER, type TEXT, protocol TEXT, bearer TEXT, roaming_protocol TEXT,
                carrier_enabled INTEGER, mmsproxy TEXT, mmsport INTEGER, proxy TEXT,  mvno_match_data TEXT,
                mvno_type TEXT, authtype INTEGER, user TEXT, password TEXT, server TEXT)""")

    apns = etree.parse(args.input)
    root = apns.getroot()
    pbar = None
    if not args.quiet:
        pbar = ProgressBar(widgets=['Processed: ', Counter(), ' apns (', Timer(), ')'], maxval=len(list(root))).start()

    count = 0
    for apn in root.iter("apn"):
        if apn.get("mmsc") is None:
            continue
        sqlvars = ["?" for x in apn.attrib.keys()] + ["?"]
        mccmnc = "%s%s" % (apn.get("mcc"), apn.get("mnc"))
        normalized_mmsc = normalized(apn.get("mmsc"))
        if normalized_mmsc != apn.get("mmsc"):
            print("normalize MMSC: %s => %s" % (apn.get("mmsc"), normalized_mmsc))
            apn.set("mmsc", normalized_mmsc)

        if not apn.get("mmsproxy") is None:
            normalized_mmsproxy = normalized(apn.get("mmsproxy"))
            if normalized_mmsproxy != apn.get("mmsproxy"):
                print("normalize proxy: %s => %s" % (apn.get("mmsproxy"), normalized_mmsproxy))
                apn.set("mmsproxy", normalized_mmsproxy)

        values = [apn.get(attrib) for attrib in apn.attrib.keys()] + [mccmnc]
        keys = apn.attrib.keys() + ["mccmnc"]

        cursor.execute("SELECT 1 FROM apns WHERE mccmnc = ? AND apn = ?", [mccmnc, apn.get("apn")])
        if cursor.fetchone() is None:
            statement = "INSERT INTO apns (%s) VALUES (%s)" % (", ".join(keys), ", ".join(sqlvars))
            cursor.execute(statement, values)

        count += 1
        if not args.quiet:
            pbar.update(count)

    if not args.quiet:
        pbar.finish()
    connection.commit()
    print("Successfully written to %s" % args.output)

    if not args.no_gzip:
        gzipped_file = "%s.gz" % (args.output,)
        with open(args.output, 'rb') as orig:
            with gzip.open(gzipped_file, 'wb') as gzipped:
                gzipped.writelines(orig)
        print("Successfully gzipped to %s" % gzipped_file)

    if not args.quiet:
        print("\nTo include this in the distribution, copy it to the project's assets/databases/ directory.")
        print("If you support API 10 or lower, you must use the gzipped version to avoid corruption.")

except sqlite3.Error, e:
    if connection:
        connection.rollback()
    print("Error: %s" % e.args[0])
    sys.exit(1)
finally:
    if connection:
        connection.close()
