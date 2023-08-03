#!/usr/bin/env bash

TI_SDK_VERSION=12.1.2.GA
JAVA_VERSION=17.0.7-amzn
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

echo '[mei] Building Android Plot Projects module via Titanium SDK'

. ~/.sdkman/bin/sdkman-init.sh
sdk use java $JAVA_VERSION

expand-template ./manifest.template >manifest \
  GITINFO TI_SDK_VERSION

#NODE_VERSION=14

#echo '[mei] selecting node version via `n`'
#n $NODE_VERSION

rm -rf dist build

echo `date` '[mei] building via Ti sdk'
ti build  -p android -c --build-only --sdk $TI_SDK_VERSION

echo ''
echo `date` '[mei] build complete'
