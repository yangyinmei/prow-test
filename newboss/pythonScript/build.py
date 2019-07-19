# -*- coding: utf-8 -*-
'''
Created on 2017年8月19日

@author: Simba
'''


import sys
import logmodule.logService as logService
import os
import tools.tool as TOOL
import exception.myException as myexception
import logging


# 在目录下递归查找指定名称的目录，返回完整路径
def getModuleDir(rootDir, moduleDirName):
    moduleDirNameList = []
    allDirList = []
    tupIterator = os.walk(rootDir)
    for i in tupIterator:
        for j in i[1]:
            allDirList.append(os.path.join(i[0], j))
    for m in allDirList:
        if os.path.isdir(m) and m.endswith("/" + moduleDirName):
            moduleDirNameList.append(m)
    if len(moduleDirNameList) != 1:
        raise myexception.MyException("Dir of module %s is not unique. " % moduleDirName)
    else:
        tempDirStr = moduleDirNameList[0].strip()
        relativeDirStr = tempDirStr.split(rootDir)[1]
    return relativeDirStr


# 将gradle任务写入代码目录下的gradleTaskList.config
def writeGradleTaskFile(codeDir, warGradleTaskStr):
    # 将任务写入代码目录下的文件gradleTaskList.config
    wholeFileName = os.path.join(codeDir, 'gradleTaskList.config')
    f = open(wholeFileName, 'w')
    f.write("#!/bin/bash\n")
    f.write("GRADLETASK=%s" % warGradleTaskStr)
    f.close


# 运行
def run():
    '''
    :param 脚本本身（非传入参数）
    :param codeDir:   代码目录
    :param moduleName: 模块名
    '''
    parameterList = sys.argv

    # 构建模块

    if parameterList[2] == "billing":
        warGradleTaskStr = ":billing:build"
        
    elif parameterList[2] == "jobserver":
        # warGradleTaskStr = "build"
        warGradleTaskStr = (
            ":platform:platform-cache-config:build\ "
            ":platform:platform-message:build\ "
            ":service:message-center:message-center-service:build\ "
            ":service:order:order-job:build\ "
            ":billing:build")
    else:
        # 构建war包
        # 组织war包的构建task
        relativeDirStr = getModuleDir(parameterList[1], parameterList[2])
    
        gradleTaskPrefixStr = relativeDirStr.replace("/", ":")
        print gradleTaskPrefixStr
        warGradleTaskStr = gradleTaskPrefixStr + ":build"
        
        # 将任务写入代码目录下的文件gradleTaskList.config
    writeGradleTaskFile(parameterList[1], warGradleTaskStr)


if __name__ == '__main__':
#    logService.initLogging()
    run()
#    logService.destoryLogging()
    