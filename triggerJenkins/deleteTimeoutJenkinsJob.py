# -*- coding: utf-8 -*-
# -*- encoding: gbk -*-
'''
Created on 2019年7月12日

@author: Yangym
'''

import jenkins
import time
import os
import config.config as CONFIG


def run():
    # jenkins配置信息
    jenkins_server_url = CONFIG.JENKINS_URL_173
    jenkins_server_miniurl = '192.168.32.173:8081/jenkins'
    user_id = CONFIG.USERID_AND_APITOKEN_173[0]
    api_token = CONFIG.USERID_AND_APITOKEN_173[1]
    user_pwd = CONFIG.USERID_AND_APITOKEN_173[2]
    server = jenkins.Jenkins(
        jenkins_server_url, username=user_id, password=api_token)

    # 遍历所有作业，判断正在构建的作业，到当前时间，一共构建了多长时间，如果超过2个小时，停止该作业
    jobList = CONFIG.JOBNAME_TRIGGERSTR.keys()
    for job in jobList:
        last_build_num = server.get_job_info(job)['lastBuild']['number']
        last_build_building = \
            server.get_build_info(job, last_build_num)['building']
        if last_build_building is True:
            start_build_timestamp = \
                server.get_build_info(job, last_build_num)['timestamp']
            now_timestamp = time.time()
            buiding_totaltime = \
                (now_timestamp - (start_build_timestamp / 1000)) / 60
            print("*****")
            print(buiding_totaltime)
            if buiding_totaltime > 120:
                stop_shell = "curl -X POST http://%s:%s@%s/job/%s/%s/stop" % (
                    user_id, user_pwd, jenkins_server_miniurl,
                    job, last_build_num)
                print(stop_shell)
                os.system(stop_shell)


if __name__ == '__main__':
    run()
