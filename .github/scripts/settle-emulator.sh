#!/bin/sh
# Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
#
# Gets the emulator ready for the tests (#393):
# - Waits until it has finished booting: its shared storage is mounted and the boot broadcasts
#   are handled. sys.boot_completed turns 1 up to a minute earlier, and the work still running
#   then made the first instrumented tests of a shard time out.
# - Then stops the cached apps those broadcasts started. They fill most of the emulator's memory,
#   and installing the app on top of them had Android kill the test process before any test ran.
#
# Usage: settle-emulator.sh [timeout in seconds, default 600]. Uses the adb on the PATH, and the
# device ANDROID_SERIAL names if more than one is connected.
timeout=${1:-600}
start=$(date +%s)

# `sm` answers "emulated;0 checking" until the volume is mounted, and nothing while it is busy
volume() { adb shell sm list-volumes emulated 2>/dev/null | tr -d '\r'; }
available() { adb shell grep MemAvailable /proc/meminfo | tr -s ' ' | cut -d' ' -f2; }

echo "Shared storage at the start: $(volume || true)"
until volume | grep -q " mounted"; do
  if [ $(($(date +%s) - start)) -ge "$timeout" ]; then
    echo "The emulator's shared storage is still not mounted after ${timeout}s."
    exit 1
  fi
  sleep 5
done
adb shell am wait-for-broadcast-idle > /dev/null
echo "The emulator finished booting $(($(date +%s) - start))s after this check started."

before=$(available)
adb shell am kill-all
echo "Memory available: $((before / 1024)) MB, $(($(available) / 1024)) MB once the cached apps stopped."
