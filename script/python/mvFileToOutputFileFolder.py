# -*- coding: utf-8 -*-
'''
Created on 2017年8月31日

@author: Simba
'''

import os
import exception.myException as myexception
import shutil
import config.config as CONFIG
import tools.tool as TOOL
import sys


# 在指定目录递归查找指定文件，返回唯一绝对路径
def findFile(rootDir, fileName):
    allFileAbsolutePathList = []
    wantFileAbsolutePathList = []
    tupIterator = os.walk(rootDir)
    for i in tupIterator:
        for j in i[2]:
            allFileAbsolutePathList.append(os.path.join(i[0], j))
    # 获得指定文件的绝对路径列表
    for m in allFileAbsolutePathList:
        if m.endswith(fileName) and os.path.isfile(m):
            wantFileAbsolutePathList.append(m)
    if len(wantFileAbsolutePathList) > 1:
        msg = "Find too many files for %s" % fileName
        TOOL.exception_write("findFile", msg)
        raise myexception.MyException(msg)
    elif len(wantFileAbsolutePathList) < 1:
        msg = "Didn't find file for %s." % fileName
        TOOL.exception_write("findFile", msg)
        raise myexception.MyException(msg)
    else:
        return wantFileAbsolutePathList[0]


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
        msg = "Dir of module %s is not unique. " % moduleDirName
        TOOL.exception_write("getModuleDir", msg)
        raise myexception.MyException(msg)
    else:
        relativeDirStr = moduleDirNameList[0].strip()
    return relativeDirStr


# 移动构建镜像所需文件
def mvOrCpFile(codeDir, moduleName):
    moduleDir = getModuleDir(codeDir, moduleName)

    # ==移动zip包、rar包、war包==
    if moduleName in CONFIG.MV_ZIP_LIST:
        zipSrcPath = findFile(moduleDir, moduleName + ".zip")
        zipDesPath = os.path.join(codeDir, "outputFileFolder", moduleName + ".zip")
        shutil.copy(zipSrcPath, zipDesPath)
    elif moduleName == "platform-activiti":
        rarSrcPath = findFile(moduleDir, "activiti-rest.rar")
        rarDesPath = os.path.join(codeDir, "outputFileFolder", "activiti-rest.rar")
        shutil.copy(rarSrcPath, rarDesPath)
    elif moduleName != "account-billing":
        warSrcPath = findFile(moduleDir, moduleName + ".war")
        warDesPath = os.path.join(codeDir, "outputFileFolder", moduleName + ".war")
        shutil.copy(warSrcPath, warDesPath)

    # ==移动dockerfile==
    if moduleName == "account-billing":
        dockerfileSrcPath = os.path.join(moduleDir, "uploadjarfileimage", moduleName + ".Dockerfile")
        dockerfileDesPath = os.path.join(codeDir, "outputFileFolder", moduleName + ".Dockerfile")
        shutil.copy(dockerfileSrcPath, dockerfileDesPath)
    else:
        dockerfileSrcPath = os.path.join(moduleDir, moduleName + ".Dockerfile")
        dockerfileDesPath = os.path.join(codeDir, "outputFileFolder", moduleName + ".Dockerfile")
        shutil.copy(dockerfileSrcPath, dockerfileDesPath)

    # ==移动run.sh==
    if moduleName in CONFIG.MV_ZIP_LIST or moduleName == "boss-status-monitor" or moduleName =="platform-activiti":  
        shellSrcPath = os.path.join(moduleDir, "run.sh")
        shellDesPath = os.path.join(codeDir, "outputFileFolder", "run.sh")
        shutil.copy(shellSrcPath, shellDesPath)

    # ==account-billing移动uploadJarFile.sh==
    if moduleName == "account-billing":  
        shellSrcPath = os.path.join(moduleDir, "uploadjarfileimage", "uploadJarFile.sh")
        shellDesPath = os.path.join(codeDir, "outputFileFolder", "uploadJarFile.sh")
        shutil.copy(shellSrcPath, shellDesPath)

    # ==复制context文件==
    if moduleName not in CONFIG.MV_ZIP_LIST and moduleName != "platform-activiti" and moduleName != "account-billing":
        contextSrcPath = os.path.join(CONFIG.ROOT_HOME, "context", "context.xml")
        print("contextSrcPath:" + contextSrcPath)
        contextDesPath = os.path.join(codeDir, "outputFileFolder", "context.xml")
        print("contextDesPath" + contextDesPath)
        shutil.copy(contextSrcPath, contextDesPath)


def run():
    '''
    :param 脚本本身（非传入参数）
    :param codeDir:   代码目录
    :param moduleName: 模块名
    '''
    codeDir = sys.argv[1]
    moduleName = sys.argv[2]

    # 在代码目录创建outputFileFolder文件夹
    wholeOutputFileFolderDir = os.path.join(codeDir, "outputFileFolder")
    if not os.path.exists(wholeOutputFileFolderDir):
        os.makedirs(wholeOutputFileFolderDir)

    mvOrCpFile(codeDir, moduleName)


if __name__ == '__main__':
    run()
