#!/bin/sh
# Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
#
# Waits until the emulator has finished booting: its shared storage is mounted and the boot
# broadcasts are handled. sys.boot_completed turns 1 up to a minute earlier, and the work still
# running then made the first instrumented tests of a shard time out (#393). Ends by logging the
# memory available, to tell a test process killed for memory from one that crashed.
#
# Usage: settle-emulator.sh [time limit in seconds, default 600]. Every adb call stops at the limit
# too, so a stuck emulator fails here instead of running out the job's time. Uses the adb on the
# PATH, and the device ANDROID_SERIAL names if more than one is connected.
limit=${1:-600}
start=$(date +%s)

# Seconds left before the limit, at least 1 so `timeout` still runs the command once
left() { echo $((limit - ($(date +%s) - start) > 0 ? limit - ($(date +%s) - start) : 1)); }

# `sm` answers "emulated;0 checking" until the volume is mounted, and nothing while it is busy
volume() { timeout "$(left)" adb shell sm list-volumes emulated 2>/dev/null | tr -d '\r'; }

echo "Shared storage at the start: $(volume || true)"
until volume | grep -q " mounted"; do
  if [ $(($(date +%s) - start)) -ge "$limit" ]; then
    echo "The emulator's shared storage is still not mounted after ${limit}s."
    exit 1
  fi
  sleep 5
done
if ! timeout "$(left)" adb shell am wait-for-broadcast-idle > /dev/null; then
  echo "The emulator's boot broadcasts are still not handled after ${limit}s."
  exit 1
fi
echo "The emulator finished booting $(($(date +%s) - start))s after this check started."
adb shell grep MemAvailable /proc/meminfo
