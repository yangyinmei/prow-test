# -*- coding: utf-8 -*-
'''
Created on 2017年8月31日

@author: Simba
'''


import logmodule.logService as logService
import os
import exception.myException as myexception
import shutil
import config.config as CONFIG
import sys


# 从给定目录里，获得指定后缀的文件的绝对路径
def getSuffixFileFromDir(theDir, suffix):
    suffixFileList = []
    fileAndDirList = os.listdir(theDir)
    if len(fileAndDirList) == 0:
        raise myexception.MyException("The dir : %s is empty." % theDir)
    else:
        for i in fileAndDirList:
            # 如果文件以suffix结尾，返回文件的绝对路径
            if os.path.isfile(os.path.join(theDir, i)) and i.endswith(suffix):
                suffixFileList.append(os.path.join(theDir, i))
    return suffixFileList


# 在指定目录递归查找指定文件，返回唯一绝对路径
def findFile(rootDir, fileName):
    print fileName
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
        raise myexception.MyException("Find too many files for %s" % fileName)
    elif len(wantFileAbsolutePathList) < 1:
        raise myexception.MyException("Didn't find file for %s." % fileName)
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
        raise myexception.MyException("Dir of module %s is not unique. " % moduleDirName)
    else:
        relativeDirStr = moduleDirNameList[0].strip()
#         relativeDirStr = tempDirStr.split(rootDir)[1]
    return relativeDirStr


# 移动构建镜像所需文件（处理除jobserver模块以外的模块）
def mvOrCpFileExcludeJobserver(codeDir, moduleName):
    if moduleName == "billing":
        moduleDir = os.path.join(codeDir, "billing")
    else:
        moduleDir = getModuleDir(codeDir, moduleName)
        
#     extraWarList = CONFIG.EXTRA_WAR_DIC.keys()
    
    # ==移动构建生成物==
#     if moduleName in extraWarList:
#         # ==移动war包（war包与模块名称不一致）
#         warSrcPath = findFile(moduleDir, CONFIG.EXTRA_WAR_DIC[moduleName])
#         warDesPath = os.path.join(codeDir, "outputFileFolder", CONFIG.EXTRA_WAR_DIC[moduleName])
#         shutil.copy(warSrcPath, warDesPath)
        
    if moduleName in CONFIG.MV_ZIP_LIST:
        # ==移动zip包==

        zipSrcPath = findFile(moduleDir, moduleName + ".zip")
        zipDesPath = os.path.join(codeDir, "outputFileFolder", moduleName + ".zip")
        shutil.copy(zipSrcPath, zipDesPath)

    else:
        # ==移动war包（war包与模块名称一致）
        warSrcPath = findFile(moduleDir, moduleName + ".war")
        warDesPath = os.path.join(codeDir, "outputFileFolder", moduleName + ".war")
        shutil.copy(warSrcPath, warDesPath)
    
    # ==移动dockerfile==
    dockerfileSrcPath = os.path.join(moduleDir, moduleName + ".Dockerfile")
    dockerfileDesPath = os.path.join(codeDir, "outputFileFolder", moduleName + ".Dockerfile")
    shutil.copy(dockerfileSrcPath, dockerfileDesPath)
    
    if moduleName in CONFIG.MV_ZIP_LIST:
        # ==移动run.sh==
        shellSrcPath = os.path.join(moduleDir, "run.sh")
        shellDesPath = os.path.join(codeDir, "outputFileFolder", "run.sh")
        shutil.copy(shellSrcPath, shellDesPath)
    
#    if moduleName not in ["order-job", "billing", "platform-config"]:
    # ==移动td-agent.conf==
#     tdconfSrcPath = os.path.join(moduleDir, moduleName + ".conf")
#     tdconfDesPath = os.path.join(codeDir, "outputFileFolder", moduleName + ".conf")
#     shutil.move(tdconfSrcPath, tdconfDesPath)
    
    # ==复制context文件==
    if moduleName not in CONFIG.MV_ZIP_LIST:
        contextSrcPath = os.path.join(CONFIG.ROOT_HOME, "context", "context.xml")
        contextDesPath = os.path.join(codeDir, "outputFileFolder", "context.xml")
        shutil.copy(contextSrcPath, contextDesPath)


# 移动或拷贝文件，为构建镜像准备
def mvOrCpFile(codeDir, moduleName):

    if moduleName == "jobserver":
        # 移动jar包到：代码目录/outputFileFolder/Jarfilefolder下
        wholeJarfilefolder = os.path.join(codeDir, "outputFileFolder", "Jarfilefolder")
        os.makedirs(wholeJarfilefolder)
        for jarKey in CONFIG.JAR_FORJOBSERVER_RELATIVEPATH_DIC.keys():

            jarSrcPath = os.path.join(codeDir, CONFIG.JAR_FORJOBSERVER_RELATIVEPATH_DIC[jarKey])
            jarDesPath = os.path.join(wholeJarfilefolder, jarKey)
            shutil.copy(jarSrcPath, jarDesPath)
            
        # 移动jobserver的dockerfile（yarn和非yarn的）
        yarnDockerfileSrcPath = findFile(codeDir, "spark-jobserver-yarn.Dockerfile")
        yarnDockerfileDesPath = os.path.join(codeDir, "outputFileFolder", "spark-jobserver-yarn.Dockerfile")
        shutil.copy(yarnDockerfileSrcPath, yarnDockerfileDesPath)
        
        jobserverFileSrcPath = findFile(codeDir, "spark-jobserver.Dockerfile")
        jobserverFileDesPath = os.path.join(codeDir, "outputFileFolder", "spark-jobserver.Dockerfile")
        shutil.copy(jobserverFileSrcPath, jobserverFileDesPath)
        
        # 移动uploadJarFile的dockerfile
        uploadJarFileSrcPath = findFile(codeDir, "uploadjarfile.Dockerfile")
        uploadJarFileDesPath = os.path.join(codeDir, "outputFileFolder", "uploadjarfile.Dockerfile")
        shutil.copy(uploadJarFileSrcPath, uploadJarFileDesPath)
        
        # 移动uploadJarFile的shell文件
        uploadShellSrcPath = findFile(codeDir, "uploadJarFile.sh")
        uploadShellDesPath = os.path.join(codeDir, "outputFileFolder", "uploadJarFile.sh")
        shutil.copy(uploadShellSrcPath, uploadShellDesPath)
        
    else:
        mvOrCpFileExcludeJobserver(codeDir, moduleName)
        

def run():
    '''
    :param 脚本本身（非传入参数）
    :param codeDir:   代码目录
    :param moduleName: 模块名
    '''
    parameterList = sys.argv
    
    # 在代码目录创建outputFileFolder文件夹
    wholeOutputFileFolderDir = os.path.join(parameterList[1], "outputFileFolder")
    if not os.path.exists(wholeOutputFileFolderDir):
        os.makedirs(wholeOutputFileFolderDir)
    
    mvOrCpFile(parameterList[1], parameterList[2])


if __name__ == '__main__':
#    logService.initLogging()
    run()
#    logService.destoryLogging()
    