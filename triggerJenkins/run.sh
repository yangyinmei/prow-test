#!/bin/bash

#ps -ef|grep "python run.py" |grep -v grep
ps -ef|grep "python run.py" |grep -v grep |awk '{print $2}'
if [ $? -ne 0 ]
then
    echo "`date "+%Y-%m-%d %H:%M:%S"` Jenkins trigger is not running, start it now."
    rm -rf /Jenkins/jenkinsci
    git clone http://jenkins:startimes@10.0.250.70:8088/rd-platform/jenkinsci.git /Jenkins/jenkinsci
    cd /Jenkins/jenkinsci
    git checkout master
    git pull
    \cp -rf /Jenkins/jenkinsci/triggerJenkins/* /Jenkins/triggerJenkins/
    cd /Jenkins/triggerJenkins/
    python run.py

else
    echo "`date "+%Y-%m-%d %H:%M:%S"` Jenkins trigger is running, restart it now."
    triggerPid=`ps -ef|grep "python run.py" |grep -v grep |awk '{print $2}'`
    kill -9 $triggerPid
    rm -rf /Jenkins/jenkinsci
    git clone http://jenkins:startimes@10.0.250.70:8088/rd-platform/jenkinsci.git /Jenkins/jenkinsci
    cd /Jenkins/jenkinsci
    git checkout master
    git pull
    \cp -rf /Jenkins/jenkinsci/triggerJenkins/* /Jenkins/triggerJenkins/
    cd /Jenkins/triggerJenkins/
    python run.py
fi