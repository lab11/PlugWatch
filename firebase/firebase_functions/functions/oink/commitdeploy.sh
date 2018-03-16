#!/bin/bash
FIRST_ARG="$1"
SECOND_ARG="$2"
THIRD_ARG="$3"

git add "$FIRST_ARG"
git commit -m "$THIRD_ARG on $(date)"
git push

firebase deploy --only functions:"$SECOND_ARG"
