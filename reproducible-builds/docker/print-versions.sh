#!/bin/bash

while read dep; do
  dep_name=$(echo $dep | cut -f1 -d '=')
  version=$(dpkg -s $dep_name | grep 'Version: ' | cut -f2 -d ' ')
  echo "$dep_name=$version"
done < $1
