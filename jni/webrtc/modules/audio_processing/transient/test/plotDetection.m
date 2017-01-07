function [] = plotDetection(PCMfile, DATfile, fs, chunkSize)
%[] = plotDetection(PCMfile, DATfile, fs, chunkSize)
%
%Plots the signal alongside the detection values.
%
%PCMfile: The file of the input signal in PCM format.
%DATfile: The file containing the detection values in binary float format.
%fs: The sample rate of the signal in Hertz.
%chunkSize: The chunk size used to compute the detection values in seconds.
[x, tx] = readPCM(PCMfile, fs);
[d, td] = readDetection(DATfile, fs, chunkSize);
plot(tx, x, td, d);
