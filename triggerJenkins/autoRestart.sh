#!/bin/sh
ps -ef|grep python|grep run.py
if [ $? -ne 0 ]
then
  echo "start /Jenkins/triggerJenkins/run.py"
