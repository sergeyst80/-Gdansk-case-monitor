#!/bin/sh
# ARM64 build host: execute the official x86-64 SDK binary through QEMU.
exec /usr/bin/qemu-x86_64 -L /usr/x86_64-linux-gnu /opt/android-sdk-linux/build-tools/36.0.0/aapt2 "$@"
