#!/usr/bin/env bash

# 1 = host , 2 = zookeeper host 3 = input file 4 = jobId

ssh $1 "cd ~/ece496root/bin && nohup ./worker.sh $2 $3 $4 >> ~/worker.$1.log &"
# ssh $1 "cd ~/Desktop/ece496/ece496root/bin && nohup ./worker.sh $2 $3 $4 >> ~/worker.$1.log &"
