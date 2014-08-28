#!/bin/sh
VERSION=0.6.0-SNAPSHOT

echo "Compiling binary distribution..."
lein clean && lein check && lein bin
rc=$?
if [[ $rc != 0 ]] ; then
    exit $rc
fi

mkdir target/atv-${VERSION}
cp atv.cmd target/atv-${VERSION}
cp target/atv target/atv-${VERSION}

if [ -f atv-${VERSION}.zip ] ; then
    rm atv-${VERSION}.zip
fi

cd target
zip -r9 ../atv-${VERSION}.zip atv-${VERSION}
cd ..

echo "Deploying as a library to clojars..."
lein deploy clojars
