# -*- coding: utf-8 -*-
'''
Created on 2017年8月18日

@author: vivis
'''


import os


'''
脚本存放根目录
'''
ROOT_HOME = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))


'''
脚本执行日志存放根目录
'''
LOG_HOME = os.path.join(ROOT_HOME, "logfile")


'''
日志是否打印线程号和进程号
0:不打印
1:只打印线程号
2:只打印进程号
3：打印线程号，并且打印进程号
'''
LOG_THREAD_PROCESS = 0


# FTP地址
FTP_HOST = '10.0.250.250'


# FTP用户名、密码
FTP_USER_PWD = ('buildftp', 'buildftp')


# spark-jobserver镜像构建所需jar包列表
JAR_FORJOBSERVER_RELATIVEPATH_DIC = {
    "stariboss-platform-common.jar": "platform/platform-common/build/libs/stariboss-platform-common.jar",
    "stariboss-platform-cache-data.jar": "platform/platform-cache-data/build/libs/stariboss-platform-cache-data.jar",
    "stariboss-platform-redis.jar": "platform/platform-redis/build/libs/stariboss-platform-redis.jar",
    "stariboss-platform-zookeeper.jar": "platform/platform-zookeeper/build/libs/stariboss-platform-zookeeper.jar",
    "stariboss-platform-message.jar": "platform/platform-message/build/libs/stariboss-platform-message.jar",
    "stariboss-message-center-api.jar": "service/message-center/message-center-api/build/libs/stariboss-message-center-api.jar",
    "stariboss-system-api.jar": "service/system/system-api/build/libs/stariboss-system-api.jar",
    "stariboss-order-job.jar": "service/order/order-job/build/libs/stariboss-order-job.jar",
    "stariboss-billing.jar": "billing/build/libs/stariboss-billing.jar"}


# 包名与模块名不同的war包字典，模块名与包名的map
# EXTRA_WAR_DIC = {
#     "starDA-web": "starDA.war",
#     "api-gateway-service": "api.war",
#     "haiwai-proxy": "stariboss-haiwai_proxy.war",
#     "callcenter-proxy": "stariboss-callcenter_proxy.war"}


# zip包列表
MV_ZIP_LIST = [
    "platform-config",
    "platform-cache-config",
    "billing",
    "order-job"]


# 镜像相关的信息
DOCKER_INFO = {"registryaddress": "10.0.251.196",
               "registoryusername": "admin",
               "registorypassword": "asdf1234",
               "bossimagerdir": "boss",
               "platformimagerdir": "platform",
               "billingimagedir": "billing",
               "thirdjarsDirInBuildImage": "/home/public-boss-jarfiles",
               "thirdjarsDirInWarImage": "/public-boss-jarfiles"
               }


# yaml文件模板在构建容器中的目录
#GIT_YAML_TEMPLATE_DIR = "/home/yamldir/yaml-template/boss_yaml"
# yaml文件存放根目录
#GIT_YAML_ROOT_DIR = "/home/yamldir/yaml/boss_yaml"







