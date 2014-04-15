#!/bin/sh
VERSION=0.2.0-SNAPSHOT

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
