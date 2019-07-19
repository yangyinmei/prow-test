# -*- coding: utf-8 -*-
'''
Created on 2017年6月21日

@author: Simba
'''
import jenkins
import config.config as CONFIG


def triggerJenkinsWithparameter(jobName, parameterDic):
    server = jenkins.Jenkins(
        CONFIG.JENKINS_URL_173,
        CONFIG.USERID_AND_APITOKEN_173[0],
        CONFIG.USERID_AND_APITOKEN_173[1]
    )
    server.build_job(jobName, parameterDic)


def triggerJenkinsWithoutparameter(jobName):
    server = jenkins.Jenkins(
        CONFIG.JENKINS_URL_173,
        CONFIG.USERID_AND_APITOKEN_173[0],
        CONFIG.USERID_AND_APITOKEN_173[1]
    )
    server.build_job(jobName)


# 获得job所需的参数列表
def getParametersOfJob(jobName):
    parameterList = []
    server = jenkins.Jenkins(
        CONFIG.JENKINS_URL_173,
        CONFIG.USERID_AND_APITOKEN_173[0],
        CONFIG.USERID_AND_APITOKEN_173[1]
    )
    if jobName in CONFIG.SLAVELIST_173:
        # print server.get_job_info(jobName)['property'][4]['parameterDefinitions']
        parameterDefinitionsList = server.get_job_info(jobName)['property'][4]['parameterDefinitions']
    else:
        # print server.get_job_info(jobName)['property'][5]['parameterDefinitions']
        parameterDefinitionsList = server.get_job_info(jobName)['property'][5]['parameterDefinitions']

    for i in parameterDefinitionsList:
        parameterList.append(i['name'])

    return parameterList
