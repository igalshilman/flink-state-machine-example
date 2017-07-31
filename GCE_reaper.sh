#!/usr/bin/env bash

# Use this to randomly kill Flink processes on GCE nodes, you can change
# the pkill command below to also kill YARN JobManagers

NUM_WORKERS=5
CLUSTER_PREFIX="aljoscha-yarn"

# Seed random generator
RANDOM=$$$(date +%s)

while [ 1 ]
do
    index=$(($RANDOM % $NUM_WORKERS))
    selected_worker="$CLUSTER_PREFIX-w-$index"

    echo $selected_worker

    gcloud compute ssh $selected_worker --zone europe-west1-d --command "sudo pkill -f TaskManager" &

    # also kill JobManager, only use this if you have a HA setup
    gcloud compute ssh $selected_worker --zone europe-west1-d --command "sudo pkill -f YarnApplicationMasterRunner" &

    sleep 60
done

