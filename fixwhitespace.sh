#!/bin/sh

if [ $# -lt 1 ]; then
    echo "Usage: ./fixwhitespace.sh <folder name>"
    exit 1
fi

# Remove trailing whitespace and replace tab indents by spaces
find $1 -name \*.java -type f -exec sed -i -e 's/[ \t]*$//g' -e 's/\t/    /g' '{}' \;
