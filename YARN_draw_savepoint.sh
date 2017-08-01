#!/usr/bin/env /bin/bash

# Usage: YARN_draw_savepoint.sh StateMachineJob gs://path/to/savepoint

APPLICATION_CLASS=$1
SAVEPOINT_URL=$2

YARN_ID=$(yarn application -list 2>&1 |grep "$APPLICATION_CLASS" |sed "s/\(\w*\).*$APPLICATION_CLASS.*/\1/")

echo "YARN Application ID: $YARN_ID"

JOB_ID=$(bin/flink list -yid $YARN_ID 2>&1|grep "RUNNING" |sed 's/.*[ ]:[ ]\(.*\)[ ]:[ ].*/\1/')

echo "Job ID: $JOB_ID"

bin/flink savepoint -yid $YARN_ID $JOB_ID $SAVEPOINT_URL
