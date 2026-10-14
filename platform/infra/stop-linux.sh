#!/bin/sh
set -eu
task_pid="$1"
test -p /opt/create-server/minecraft/console.fifo
printf 'stop\n' > /opt/create-server/minecraft/console.fifo
# systemd must wait for the save to finish before considering ExecStop complete.
while kill -0 "$task_pid" 2>/dev/null; do
    sleep 1
done
