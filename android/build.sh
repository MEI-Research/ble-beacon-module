#!/usr/bin/env bash

TI_SDK_VERSION="12.1.2.GA"
GITINFO=$( git describe --long --abbrev=6 --dirty=+ )

expand-template ()
{
    file=$1;
    shift;
    exprs="";
    for var in $@;
    do
        exprs="$exprs -e's/\${$var}/${!var}/'";
    done;
    CMD="sed <$file $exprs";
    eval "$CMD"
}

expand-template ./manifest.template >manifest \
  GITINFO TI_SDK_VERSION

echo '[mei] Building Android Plot Projects module via Titanium SDK'


#NODE_VERSION=14

#echo '[mei] selecting node version via `n`'
#n $NODE_VERSION

rm -rf dist build

echo `date` '[mei] building via Ti sdk'
ti build  -p android -c --build-only --sdk $TI_SDK_VERSION

echo ''
echo `date` '[mei] build complete'
