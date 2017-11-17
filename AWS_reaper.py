import boto3
import botocore
import sys
import time
import os
import random
import json


if __name__ == "__main__":
    job_flow_json = json.load(open('/mnt/var/lib/info/job-flow.json'))
    cluster_id = job_flow_json['jobFlowId']
    print("Cluster ID: {0}".format(cluster_id))

    client = boto3.client('emr')

    instances = client.list_instances(
        ClusterId=cluster_id,
    )

    while True:
        instance = random.choice(instances['Instances'])
        print("Killing TaskManagers on {0}".format(instance['PublicDnsName']))
        os.system('ssh {0} sudo pkill -f taskmanager'.format(instance['PublicDnsName']))
        time.sleep(30)
