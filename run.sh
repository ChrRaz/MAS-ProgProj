#!/bin/bash

# Example:
# ./run.sh levels/SACrunch.lvl -astar -g 100 -p

LEVEL="${1:-levels/SACrunch.lvl}"
MODE="${2:--astar}"
shift 2

java -Dsun.java2d.opengl=true -jar server.jar -l "$LEVEL" -c "java -cp build/ -Xmx5g -Xms5g searchclient.Main $MODE" "$@"
