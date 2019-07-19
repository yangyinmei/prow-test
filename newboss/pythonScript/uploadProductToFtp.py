# -*- coding: utf-8 -*-
'''
Created on 2017年8月21日

@author: Simba
'''


import sys
import logmodule.logService as logService
import tools.ftpManager as ftpManager
import os
import config.config as CONFIG


# 获取构建产物
def getProduct(codeDir, moduleName):
    outputDir = os.path.join(codeDir, "outputFileFolder")
    outputList = os.listdir(outputDir)
    outputDic = {}
    for output in outputList:
        if (os.path.isfile(os.path.join(outputDir, output)) and (output.endswith(".zip") or output.endswith(".war"))):
            outputDic[moduleName] = os.path.join(outputDir, output)
    return outputDic


# ftp上传文件夹
def ftpUploadDir(localDir, remoteDir):
    ftpManager.ftpUploadDir(CONFIG.FTP_HOST, CONFIG.FTP_USER_PWD[0], CONFIG.FTP_USER_PWD[1], localDir, remoteDir)
    

# ftp上传文件
def ftpUploadFile(localFilepath, remoteFilepath):
    ftpManager.ftpUploadFile(CONFIG.FTP_HOST, CONFIG.FTP_USER_PWD[0], CONFIG.FTP_USER_PWD[1], localFilepath, remoteFilepath)


def run():
    '''
    :param 脚本本身（非传入参数）
    :param codeDir:   代码目录
    :param moduleName: 模块名
    :param version: 版本号
    :param newTag: 标签
    '''
    codeDir = sys.argv[1]
    moduleName = sys.argv[2]
    version = sys.argv[3]
    newTag = sys.argv[4]
    
    # 处理jobserver的构建物
    if moduleName == "jobserver":
        sourceDir = os.path.join(codeDir, "outputFileFolder", "Jarfilefolder")
        desDir = os.path.join("/DataBk/build/stariboss-10.X", version, newTag, moduleName, "Jarfilefolder")
        ftpManager.recursiveCreateFtpdir(ftpManager.getSubsectionDir(desDir))
        ftpUploadDir(sourceDir, desDir)
    else:
        outputDic = getProduct(codeDir, moduleName)
        sourcePath = outputDic[moduleName]
        fileName = sourcePath.split("/")[-1]
        ftpManager.recursiveCreateFtpdir(ftpManager.getSubsectionDir(os.path.join("/DataBk/build/stariboss-10.X", version, newTag, moduleName)))
        desPath = os.path.join("/DataBk/build/stariboss-10.X", version, newTag, moduleName, fileName)
        ftpUploadFile(sourcePath, desPath)


if __name__ == "__main__":
#    logService.initLogging()
    run()
#    logService.destoryLogging()


