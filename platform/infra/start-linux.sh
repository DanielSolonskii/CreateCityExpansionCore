#!/bin/sh
set -eu
cd /opt/create-server/minecraft
if [ ! -e console.fifo ]; then
    mkfifo -m 600 console.fifo
fi
test -p console.fifo
# Keep the FIFO open in both directions, so console input does not receive EOF.
exec 3<>console.fifo
exec /usr/lib/jvm/java-21-openjdk-amd64/bin/java @user_jvm_args.txt @libraries/net/neoforged/neoforge/21.1.251/unix_args.txt nogui <&3
