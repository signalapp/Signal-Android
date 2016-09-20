function apmtest(task, testname, filepath, casenumber, legacy)
%APMTEST is a tool to process APM file sets and easily display the output.
%   APMTEST(TASK, TESTNAME, CASENUMBER) performs one of several TASKs:
%     'test'  Processes the files to produce test output.
%     'list'  Prints a list of cases in the test set, preceded by their
%             CASENUMBERs.
%     'show'  Uses spclab to show the test case specified by the
%             CASENUMBER parameter.
%
%   using a set of test files determined by TESTNAME:
%     'all'   All tests.
%     'apm'   The standard APM test set (default).
%     'apmm'  The mobile APM test set.
%     'aec'   The AEC test set.
%     'aecm'  The AECM test set.
%     'agc'   The AGC test set.
%     'ns'    The NS test set.
%     'vad'   The VAD test set.
%
%   FILEPATH specifies the path to the test data files.
%
%   CASENUMBER can be used to select a single test case. Omit CASENUMBER,
%   or set to zero, to use all test cases.
%

if nargin < 5 || isempty(legacy)
  % Set to true to run old VQE recordings.
  legacy = false;
end

if nargin < 4 || isempty(casenumber)
  casenumber = 0;
end

if nargin < 3 || isempty(filepath)
  filepath = 'data/';
end

if nargin < 2 || isempty(testname)
  testname = 'all';
end

if nargin < 1 || isempty(task)
  task = 'test';
end

if ~strcmp(task, 'test') && ~strcmp(task, 'list') && ~strcmp(task, 'show')
  error(['TASK ' task ' is not recognized']);
end

if casenumber == 0 && strcmp(task, 'show')
  error(['CASENUMBER must be specified for TASK ' task]);
end

inpath = [filepath 'input/'];
outpath = [filepath 'output/'];
refpath = [filepath 'reference/'];

if strcmp(testname, 'all')
  tests = {'apm','apmm','aec','aecm','agc','ns','vad'};
else
  tests = {testname};
end

if legacy
  progname = './test';
else
  progname = './process_test';
end

global farFile;
global nearFile;
global eventFile;
global delayFile;
global driftFile;

if legacy
  farFile = 'vqeFar.pcm';
  nearFile = 'vqeNear.pcm';
  eventFile = 'vqeEvent.dat';
  delayFile = 'vqeBuf.dat';
  driftFile = 'vqeDrift.dat';
else
  farFile = 'apm_far.pcm';
  nearFile = 'apm_near.pcm';
  eventFile = 'apm_event.dat';
  delayFile = 'apm_delay.dat';
  driftFile = 'apm_drift.dat';
end

simulateMode = false;
nErr = 0;
nCases = 0;
for i=1:length(tests)
  simulateMode = false;

  if strcmp(tests{i}, 'apm')
    testdir = ['apm/'];
    outfile = ['out'];
    if legacy
      opt = ['-ec 1 -agc 2 -nc 2 -vad 3'];
    else
      opt = ['--no_progress -hpf' ...
          ' -aec --drift_compensation -agc --fixed_digital' ...
          ' -ns --ns_moderate -vad'];
    end

  elseif strcmp(tests{i}, 'apm-swb')
    simulateMode = true;
    testdir = ['apm-swb/'];
    outfile = ['out'];
    if legacy
      opt = ['-fs 32000 -ec 1 -agc 2 -nc 2'];
    else
      opt = ['--no_progress -fs 32000 -hpf' ...
          ' -aec --drift_compensation -agc --adaptive_digital' ...
          ' -ns --ns_moderate -vad'];
    end
  elseif strcmp(tests{i}, 'apmm')
    testdir = ['apmm/'];
    outfile = ['out'];
    opt = ['-aec --drift_compensation -agc --fixed_digital -hpf -ns ' ...
        '--ns_moderate'];

  else
    error(['TESTNAME ' tests{i} ' is not recognized']);
  end

  inpathtest = [inpath testdir];
  outpathtest = [outpath testdir];
  refpathtest = [refpath testdir];

  if ~exist(inpathtest,'dir')
    error(['Input directory ' inpathtest ' does not exist']);
  end

  if ~exist(refpathtest,'dir')
    warning(['Reference directory ' refpathtest ' does not exist']);
  end

  [status, errMsg] = mkdir(outpathtest);
  if (status == 0)
    error(errMsg);
  end

  [nErr, nCases] = recurseDir(inpathtest, outpathtest, refpathtest, outfile, ...
      progname, opt, simulateMode, nErr, nCases, task, casenumber, legacy);

  if strcmp(task, 'test') || strcmp(task, 'show')
    system(['rm ' farFile]);
    system(['rm ' nearFile]);
    if simulateMode == false
      system(['rm ' eventFile]);
      system(['rm ' delayFile]);
      system(['rm ' driftFile]);
    end
  end
end

if ~strcmp(task, 'list')
  if nErr == 0
    fprintf(1, '\nAll files are bit-exact to reference\n', nErr);
  else
    fprintf(1, '\n%d files are NOT bit-exact to reference\n', nErr);
  end
end


function [nErrOut, nCases] = recurseDir(inpath, outpath, refpath, ...
    outfile, progname, opt, simulateMode, nErr, nCases, task, casenumber, ...
    legacy)

global farFile;
global nearFile;
global eventFile;
global delayFile;
global driftFile;

dirs = dir(inpath);
nDirs = 0;
nErrOut = nErr;
for i=3:length(dirs) % skip . and ..
  nDirs = nDirs + dirs(i).isdir;
end


if nDirs == 0
  nCases = nCases + 1;

  if casenumber == nCases || casenumber == 0

    if strcmp(task, 'list')
      fprintf([num2str(nCases) '. ' outfile '\n'])
    else
      vadoutfile = ['vad_' outfile '.dat'];
      outfile = [outfile '.pcm'];

      % Check for VAD test
      vadTest = 0;
      if ~isempty(findstr(opt, '-vad'))
        vadTest = 1;
        if legacy
          opt = [opt ' ' outpath vadoutfile];
        else
          opt = [opt ' --vad_out_file ' outpath vadoutfile];
        end
      end

      if exist([inpath 'vqeFar.pcm'])
        system(['ln -s -f ' inpath 'vqeFar.pcm ' farFile]);
      elseif exist([inpath 'apm_far.pcm'])
        system(['ln -s -f ' inpath 'apm_far.pcm ' farFile]);
      end

      if exist([inpath 'vqeNear.pcm'])
        system(['ln -s -f ' inpath 'vqeNear.pcm ' nearFile]);
      elseif exist([inpath 'apm_near.pcm'])
        system(['ln -s -f ' inpath 'apm_near.pcm ' nearFile]);
      end

      if exist([inpath 'vqeEvent.dat'])
        system(['ln -s -f ' inpath 'vqeEvent.dat ' eventFile]);
      elseif exist([inpath 'apm_event.dat'])
        system(['ln -s -f ' inpath 'apm_event.dat ' eventFile]);
      end

      if exist([inpath 'vqeBuf.dat'])
        system(['ln -s -f ' inpath 'vqeBuf.dat ' delayFile]);
      elseif exist([inpath 'apm_delay.dat'])
        system(['ln -s -f ' inpath 'apm_delay.dat ' delayFile]);
      end

      if exist([inpath 'vqeSkew.dat'])
        system(['ln -s -f ' inpath 'vqeSkew.dat ' driftFile]);
      elseif exist([inpath 'vqeDrift.dat'])
        system(['ln -s -f ' inpath 'vqeDrift.dat ' driftFile]);
      elseif exist([inpath 'apm_drift.dat'])
        system(['ln -s -f ' inpath 'apm_drift.dat ' driftFile]);
      end

      if simulateMode == false
        command = [progname ' -o ' outpath outfile ' ' opt];
      else
        if legacy
          inputCmd = [' -in ' nearFile];
        else
          inputCmd = [' -i ' nearFile];
        end

        if exist([farFile])
          if legacy
            inputCmd = [' -if ' farFile inputCmd];
          else
            inputCmd = [' -ir ' farFile inputCmd];
          end
        end
        command = [progname inputCmd ' -o ' outpath outfile ' ' opt];
      end
      % This prevents MATLAB from using its own C libraries.
      shellcmd = ['bash -c "unset LD_LIBRARY_PATH;'];
      fprintf([command '\n']);
      [status, result] = system([shellcmd command '"']);
      fprintf(result);

      fprintf(['Reference file: ' refpath outfile '\n']);

      if vadTest == 1
        equal_to_ref = are_files_equal([outpath vadoutfile], ...
                                       [refpath vadoutfile], ...
                                       'int8');
        if ~equal_to_ref
          nErr = nErr + 1;
        end
      end

      [equal_to_ref, diffvector] = are_files_equal([outpath outfile], ...
                                                   [refpath outfile], ...
                                                   'int16');
      if ~equal_to_ref
        nErr = nErr + 1;
      end

      if strcmp(task, 'show')
        % Assume the last init gives the sample rate of interest.
        str_idx = strfind(result, 'Sample rate:');
        fs = str2num(result(str_idx(end) + 13:str_idx(end) + 17));
        fprintf('Using %d Hz\n', fs);

        if exist([farFile])
          spclab(fs, farFile, nearFile, [refpath outfile], ...
              [outpath outfile], diffvector);
          %spclab(fs, diffvector);
        else
          spclab(fs, nearFile, [refpath outfile], [outpath outfile], ...
              diffvector);
          %spclab(fs, diffvector);
        end
      end
    end
  end
else

  for i=3:length(dirs)
    if dirs(i).isdir
      [nErr, nCases] = recurseDir([inpath dirs(i).name '/'], outpath, ...
          refpath,[outfile '_' dirs(i).name], progname, opt, ...
          simulateMode, nErr, nCases, task, casenumber, legacy);
    end
  end
end
nErrOut = nErr;

function [are_equal, diffvector] = ...
    are_files_equal(newfile, reffile, precision, diffvector)

are_equal = false;
diffvector = 0;
if ~exist(newfile,'file')
  warning(['Output file ' newfile ' does not exist']);  
  return
end

if ~exist(reffile,'file')
  warning(['Reference file ' reffile ' does not exist']);  
  return
end

fid = fopen(newfile,'rb');
new = fread(fid,inf,precision);
fclose(fid);

fid = fopen(reffile,'rb');
ref = fread(fid,inf,precision);
fclose(fid);

if length(new) ~= length(ref)
  warning('Reference is not the same length as output');
  minlength = min(length(new), length(ref));
  new = new(1:minlength);
  ref = ref(1:minlength);
end
diffvector = new - ref;

if isequal(new, ref)
  fprintf([newfile ' is bit-exact to reference\n']);
  are_equal = true;
else
  if isempty(new)
    warning([newfile ' is empty']);
    return
  end
  snr = snrseg(new,ref,80);
  fprintf('\n');
  are_equal = false;
end
