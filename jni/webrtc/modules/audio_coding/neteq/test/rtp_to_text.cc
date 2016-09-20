/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

/*
 * Parses an rtpdump file and outputs a text table parsable by parseLog.m.
 * The output file will have .txt appended to the specified base name.
 * $ rtp_to_text [-d] <input_rtp_file> <output_base_name>
 *
 * -d   RTP headers only
 *
 */

#include "webrtc/system_wrappers/include/data_log.h"
#include "NETEQTEST_DummyRTPpacket.h"
#include "NETEQTEST_RTPpacket.h"

#include <stdio.h>
#include <string.h>

#include <iostream>
#include <string>
#include <vector>

/*********************/
/* Misc. definitions */
/*********************/

#define FIRSTLINELEN 40

using ::webrtc::DataLog;

int main(int argc, char* argv[])
{
    int arg_count = 1;
    NETEQTEST_RTPpacket* packet;

    if (argc < 3)
    {
      printf("Usage: %s [-d] <input_rtp_file> <output_base_name>\n", argv[0]);
      return -1;
    }

    // Parse dummy option
    if (argc >= 3 && strcmp(argv[arg_count], "-d") == 0)
    {
        packet = new NETEQTEST_DummyRTPpacket;
        ++arg_count;
    }
    else
    {
        packet = new NETEQTEST_RTPpacket;
    }

    std::string input_filename = argv[arg_count++];
    std::string table_name = argv[arg_count];

    std::cout << "Input file: " << input_filename << std::endl;
    std::cout << "Output file: " << table_name << ".txt" << std::endl;

    FILE *inFile=fopen(input_filename.c_str(),"rb");
    if (!inFile)
    {
        std::cout << "Cannot open input file " << input_filename << std::endl;
        return -1;
    }

    // Set up the DataLog and define the table
    DataLog::CreateLog();
    if (DataLog::AddTable(table_name) < 0)
    {
        std::cout << "Error adding table " << table_name << ".txt" << std::endl;
        return -1;
    }

    DataLog::AddColumn(table_name, "seq", 1);
    DataLog::AddColumn(table_name, "ssrc", 1);
    DataLog::AddColumn(table_name, "payload type", 1);
    DataLog::AddColumn(table_name, "length", 1);
    DataLog::AddColumn(table_name, "timestamp", 1);
    DataLog::AddColumn(table_name, "marker bit", 1);
    DataLog::AddColumn(table_name, "arrival", 1);

    // read file header
    char firstline[FIRSTLINELEN];
    if (fgets(firstline, FIRSTLINELEN, inFile) == NULL)
    {
        std::cout << "Error reading file " << input_filename << std::endl;
        return -1;
    }

    // start_sec + start_usec + source + port + padding
    if (fread(firstline, 4+4+4+2+2, 1, inFile) != 1)
    {
        std::cout << "Error reading file " << input_filename << std::endl;
        return -1;
    }

    while (packet->readFromFile(inFile) >= 0)
    {
        // write packet headers to
        DataLog::InsertCell(table_name, "seq", packet->sequenceNumber());
        DataLog::InsertCell(table_name, "ssrc", packet->SSRC());
        DataLog::InsertCell(table_name, "payload type", packet->payloadType());
        DataLog::InsertCell(table_name, "length", packet->dataLen());
        DataLog::InsertCell(table_name, "timestamp", packet->timeStamp());
        DataLog::InsertCell(table_name, "marker bit", packet->markerBit());
        DataLog::InsertCell(table_name, "arrival", packet->time());
        DataLog::NextRow(table_name);
        return -1;
    }

    DataLog::ReturnLog();

    fclose(inFile);

    return 0;
}
