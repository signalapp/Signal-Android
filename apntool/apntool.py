import sys
import argparse
import sqlite3
import gzip
from progressbar import ProgressBar, Counter, Timer
from lxml import etree

parser = argparse.ArgumentParser(prog='apntool', description="""Process Android's apn xml files and drop them into an easily
                                                             queryable SQLite db. Tested up to version 9 of their APN file.""")
parser.add_argument('-v', '--version', action='version', version='%(prog)s v1.0')
parser.add_argument('-i', '--input', help='the xml file to parse', default='apns.xml', required=False)
parser.add_argument('-o', '--output', help='the sqlite db output file', default='apns.db', required=False)
parser.add_argument('--quiet', help='do not show progress or verbose instructions', action='store_true', required=False)
parser.add_argument('--no-gzip', help="do not gzip after creation", action='store_true', required=False)
args = parser.parse_args()

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
    cursor.execute("""CREATE TABLE apns(_id INTEGER PRIMARY KEY, mccmnc TEXT, mcc TEXT, mnc TEXT, carrier TEXT, apn TEXT,
                mmsc TEXT, port INTEGER, type TEXT, protocol TEXT, bearer TEXT, roaming_protocol TEXT,
                carrier_enabled INTEGER, mmsproxy TEXT, mmsport INTEGER, proxy TEXT,  mvno_match_data TEXT,
                mvno_type TEXT, authtype INTEGER, user TEXT, password TEXT, server TEXT)""")

    apns = etree.parse(args.input)
    root = apns.getroot()
    pbar = ProgressBar(widgets=['Processed: ', Counter(), ' apns (', Timer(), ')'], maxval=len(list(root))).start() if not args.quiet else None

    count = 0
    for apn in root.iter("apn"):
        sqlvars = ["?" for x in apn.attrib.keys()] + ["?"]
        values = [apn.get(attrib) for attrib in apn.attrib.keys()] + ["%s%s" % (apn.get("mcc"), apn.get("mnc"))]
        keys = apn.attrib.keys() + ["mccmnc"]

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
