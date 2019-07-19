#!/bin/bash

ps -ef|grep "python run.py" |grep -v grep
if [ $? -ne 0 ]
then
	echo "Jenkins trigger is not running, start it now."
	cd /Jenkins/triggerJenkins
	python run.py &
else
	echo "Jenkins trigger is still running."
fi