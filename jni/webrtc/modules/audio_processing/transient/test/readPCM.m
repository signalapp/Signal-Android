function [x, t] = readPCM(file, fs)
%[x, t] = readPCM(file, fs)
%
%Reads a signal from a PCM file.
%
%x: The read signal after normalization.
%t: The respective time vector.
%
%file: The PCM file where the signal is stored in int16 format.
%fs: The signal sample rate in Hertz.
fid = fopen(file);
x = fread(fid, inf, 'int16');
fclose(fid);
x = x - mean(x);
x = x / max(abs(x));
t = 0:(1 / fs):((length(x) - 1) / fs);
