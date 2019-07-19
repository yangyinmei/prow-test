# -*- coding: utf-8 -*-
'''
Created on 2017年8月21日

@author: Simba
'''
import sys
import tools.ftpManager as ftpManager
import os
import config.config as CONFIG


# 获取构建产物
def getProduct(codeDir, moduleName, jobName):
    if CONFIG.OUTPUTDIR_DIC.get(jobName) is not None:
        outputDir = os.path.join(codeDir, CONFIG.OUTPUTDIR_DIC.get(jobName))
        print "######### outputDir #########"
        print outputDir
    elif jobName == "ottapplication":
        outputDir = os.path.join(codeDir, "manage-portal/dist")
    else:
        outputDir = os.path.join(codeDir, "outputFileFolder")
    outputList = []
    for root, dirs, files in os.walk(outputDir):
        for name in files:
            if (name.endswith(".zip") or name.endswith(".war") or name.endswith(".tar.gz") or name.endswith(".tar") or name.endswith("buildinfo.txt") or name.endswith("imageInfo.txt") or name.endswith(".apk")):
                print(os.path.join(root, name))
                outputList.append(os.path.join(root, name))
    return outputList


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
    uploadFtpDir = sys.argv[5]
    jobName = sys.argv[6]

    outputList = getProduct(codeDir, moduleName, jobName)
    for sourcePath in outputList:
        fileName = sourcePath.split("/")[-1]
        ftpManager.recursiveCreateFtpdir(ftpManager.getSubsectionDir(os.path.join(uploadFtpDir, version, newTag, moduleName)))
        desPath = os.path.join(uploadFtpDir, version, newTag, moduleName, fileName)
        ftpUploadFile(sourcePath, desPath)


if __name__ == "__main__":
    run()
