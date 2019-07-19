# -*- coding: utf-8 -*-
'''
Created on 2017年9月1日

@author: jink
'''


import config.config as CONFIG
import tools.tool as TOOL
import os
import sys
import time
import random
import subprocess
import exception.myException as myexception


# 构建镜像
def createDockerImage(fileDir, dockerfile, newTag, buildLogAbsolutePath):
    moduleName = (os.path.splitext(dockerfile)[0]).lower()
    if moduleName == "account-billing":
        imageName = os.path.join(CONFIG.DOCKER_INFO["registryaddress"], CONFIG.DOCKER_INFO["billingimagerdir"], moduleName + ":" + newTag)
    else:
        imageName = os.path.join(CONFIG.DOCKER_INFO["registryaddress"], CONFIG.DOCKER_INFO["bossimagerdir"], moduleName + ":" + newTag)

    actualStr = "docker build -t %s -f %s %s 2>&1 |grep -v 'Sending build context to Docker daemon' > %s/build_%s_image.log" % \
                (imageName, dockerfile, '.', buildLogAbsolutePath, moduleName)
    print("dockerBuildActualStr:" + actualStr)

    try:
        p = subprocess.Popen(actualStr, shell=True)
        p.wait()
        f = open((buildLogAbsolutePath + ('/build_%s_image.log' % moduleName)), 'r')
        lineList = f.readlines()
        print(lineList)
        f.close()
        boolFlag = False
        for line in lineList:
            if 'Successfully built' in line:
                boolFlag = True
        if boolFlag is False:
            msg = "build docker image failed!"
            TOOL.exception_write("createDockerImage", msg)
            raise Exception(msg)
    except Exception as e:
        print(e)
        raise Exception(e)


# 登录镜像仓库
def logInImageRegistory():
    rightStr = 'docker login -u %s  -p %s %s' % (CONFIG.DOCKER_INFO['registoryusername'],
                                                 CONFIG.DOCKER_INFO['registorypassword'],
                                                 CONFIG.DOCKER_INFO['registryaddress'])
    p = subprocess.Popen(rightStr, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE, shell=True)
    resList = p.stdout.readlines()
    if len(resList) != 0:
        pass
    else:
        errStrList = p.stderr.readlines()
        errStrStripList = []
        for j in errStrList:
            errStrStripList.append(j.strip())
        errStr = ','.join(errStrStripList)
        TOOL.exception_write("logInImageRegistory", errStr)
        raise myexception.MyException("%s" % errStr)


# 推送镜像到仓库
def pushImageToRepository(warImageName, tagName, repositoryName):
    imageName = os.path.join(repositoryName, warImageName)
    command = "docker push %s:%s" % (imageName, tagName)
    print("pushImageCommand:" + command)
    p = subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.PIPE, shell=True)
    resTup = p.communicate()
    errStr = resTup[1]
    if len(errStr) == 0:
        print("%s pushed successfully." % (imageName + ':' + tagName))
        return True
    else:
        print(errStr)
        msg = "%s pushed failed." % (imageName + ':' + tagName)
        TOOL.exception_write("pushImageToRepository", msg)
        raise Exception(msg)


# 删除镜像
def deleteImage(warImageName, tagName, repositoryName):
    imageName = os.path.join(repositoryName, warImageName)
    command = "docker rmi %s:%s" % (imageName, tagName)
    p = subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.PIPE, shell=True)
    resTup = p.communicate()
    errStr = resTup[1]
    if len(errStr) == 0:
        print("%s deleted successfully." % (imageName + ':' + tagName))
        return True
    else:
        msg = "%s deleted failed." % (imageName + ':' + tagName)
        TOOL.exception_write("deleteImage", msg)
        raise Exception(msg)


def run():
    '''
    :param 脚本本身（非传入参数）
    :param codeDir:   代码目录
    :param moduleName: 模块名
    :param newTag: 标签
    '''
    codeDir = sys.argv[1]
    moduleName = sys.argv[2]
    newTag = sys.argv[3]

    # 改变当前工作目录到指定的路径
    os.chdir(os.path.join(codeDir, 'outputFileFolder'))

    # 构建镜像
    baseDir = os.path.join(codeDir, 'outputFileFolder')
    buildLogAbsolutePath = os.path.join(CONFIG.LOG_HOME, newTag + "_buildimages_log")

    dockerfile = moduleName + ".Dockerfile"
    attempts = 0
    success = False
    while attempts < 3 and not success:
        try:
            createDockerImage(baseDir, dockerfile, newTag, buildLogAbsolutePath)
            success = True
        except Exception as e:
            time.sleep(random.randint(3, 8))
            attempts += 1
            if attempts == 3:
                raise Exception(e)

    # 登录镜像仓库
    logInImageRegistory()

    warImageNameList = []
    # 推送并删除镜像
    if moduleName == "account-billing":
        repositoryName = os.path.join(CONFIG.DOCKER_INFO["registryaddress"], CONFIG.DOCKER_INFO["billingimagerdir"])
    else:
        repositoryName = os.path.join(CONFIG.DOCKER_INFO["registryaddress"], CONFIG.DOCKER_INFO["bossimagerdir"])
    warImageNameList.append(moduleName.lower())

    for wl in warImageNameList:
        pushImageToRepository(wl, newTag, repositoryName)


if __name__ == '__main__':
    run()
