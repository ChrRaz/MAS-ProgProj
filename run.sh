#!/bin/bash

# Example:
# ./run.sh levels/SACrunch.lvl -astar -g 100 -p

LEVEL="${1:-levels/SACrunch.lvl}"
MODE="${2:--astar}"
shift 2

/opt/openjdk-bin-11.0.4_p11/bin/java -Dsun.java2d.opengl=true -jar server.jar -l "$LEVEL" -c "java -cp build/ -Xmx5g -Xms5g searchclient.Main $MODE" "$@"
