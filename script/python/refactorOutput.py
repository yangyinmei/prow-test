# -*- coding: utf-8 -*-
'''
Created on 2018年9月7日

@author: yangym
'''

import config.config as CONFIG
import os
import zipfile
import sys


def unzipFile(zipFilePath, unzipFilePath=None):
    '''
    :param zipFilePath:被解压缩文件的绝对路径
    :param unzipFilePath:解压后存放的绝对路径
    '''
    if zipfile.is_zipfile(zipFilePath):
        print("is zipfile")
        fz = zipfile.ZipFile(zipFilePath, 'r')
        for f in fz.namelist():
            fz.extract(f, unzipFilePath)
        return True
    else:
        msg = 'This file is not zip file'
        print(msg)
        return False


def getFileList(filePath):
    fileList = []
    list_dirs = os.walk(filePath)
    for root, _, files in list_dirs:
        for f in files:
            fileList.append(f)
    return fileList


# 去除公共jar包,并重写context.xml
def modifyContext(codeDir, moduleName):
    warPath = os.path.join(codeDir, "outputFileFolder", moduleName + ".war")
    warDirPath = os.path.join(codeDir, "outputFileFolder", moduleName)
    if unzipFile(warPath, warDirPath):
        libPath = warDirPath + "/WEB-INF/lib/"
        # if "agent-web" == moduleName:
        #     repetLibList = removeRepetitionLibs(libPath, CONFIG.DOCKER_INFO["thirdjarsLocalDir"])
        # else:
        repetLibList = removeRepetitionLibs(libPath, CONFIG.DOCKER_INFO["thirdjarsDirInBuildImage"])
        XML_PATH = os.path.join(codeDir, "outputFileFolder", "context.xml")
        modifyWebXML(moduleName, XML_PATH, repetLibList, CONFIG.DOCKER_INFO["thirdjarsDirInWarImage"])


def removeRepetitionLibs(moduleLibPath, commonLibPath):
    moduleLibList = getFileList(moduleLibPath)
    commonLibList = getFileList(commonLibPath)
    repetList = list((set(moduleLibList).union(set(commonLibList))) ^ (set(moduleLibList) ^ set(commonLibList)))
    # 删除moduleLibPath中重复的libs
    for name in repetList:
        libPath = moduleLibPath + name
        os.remove(libPath)
        print("os.remove(%s)" % (libPath))
    return repetList


def modifyWebXML(moduleName, xmlPath, libList, commonLibPath):
    old = open(xmlPath)
    texts = old.readlines()
    new_texts = []

    if moduleName in CONFIG.STARIBOSS_UI_MODULELIST:
        s = "  <JarScanner>\n"
        s += '''    <JarScanFilter pluggabilitySkip="aws-java*,brave-,websocket-,groovy-,jedis-,poi-,jetty-,zipkin-,common-,hibernate-,http,org.eclipse*,pfl*,terracotta*,spring-,reactor,netty*,jackson-*" />\n'''
        s += "  </JarScanner>\n"
    elif moduleName in CONFIG.STARIBOSS_SERVICE_MODULELIST:
        s = "  <JarScanner>\n"
        s += '''    <JarScanFilter pluggabilitySkip="aws-java*,brave-,websocket-,groovy-,jedis-,poi-,jetty-,zipkin-,common-,hibernate-,http,org.eclipse*,pfl*,terracotta*,spring-,reactor,netty*,jackson-" tldSkip="*"/>\n'''
        s += "  </JarScanner>\n"
    s += "  <Resources>\n"
    s += '''<PreResources className="org.apache.catalina.webresources.DirResourceSet" base="/patch/" webAppMount="/WEB-INF/lib" />\n'''
    for lib in libList:
        sourcePath = os.path.join(commonLibPath, lib)
        s += '''    <PostResources className="org.apache.catalina.webresources.FileResourceSet" base="%s" webAppMount="/WEB-INF/lib/%s"/>\n''' % (sourcePath, lib)
    s += "  </Resources>\n"

    flag = False
    for line in texts:
        if "<Context>" in line and flag is False:
            line += "\n" + s
            flag = True
        new_texts.append(line)

    if flag is False:
        line += "\n" + s
        new_texts.append(line)
    new = open(xmlPath, 'w')
    new.writelines(new_texts)
    new.close()
    old.close()


def modifyDockerfile(codeDir, moduleName):
    dataDic = {
        "image.repository": CONFIG.IMAGE_HOST
    }
    sourceFilePath = os.path.join(codeDir, "outputFileFolder", "%s.Dockerfile" % moduleName)
    desFilePath = os.path.join(codeDir, "outputFileFolder", "%s.Dockerfile" % moduleName)

    fread = open(sourceFilePath, 'r')
    fStr = fread.read()
    fread.close()

    targetStr = fStr % dataDic

    fwrite = open(desFilePath, 'w')
    fwrite.write(targetStr)
    fwrite.close()


def run():
    '''
    :param 脚本本身（非传入参数）
    :param codeDir:   代码目录
    :param moduleName: 模块名
    '''
    codeDir = sys.argv[1]
    moduleName = sys.argv[2]

    if moduleName not in CONFIG.MV_ZIP_LIST and moduleName != "account-billing":
        modifyContext(codeDir, moduleName)

    if moduleName in CONFIG.MV_ZIP_LIST:
        zipFilePath = os.path.join(codeDir, 'outputFileFolder', moduleName + ".zip")
        unzipFileDir = os.path.join(codeDir, 'outputFileFolder')
        unzipFile(zipFilePath, unzipFileDir)

    # 修改Dockerfile中的镜像参数
    print "modifyDockerfile"
    modifyDockerfile(codeDir,moduleName)


if __name__ == '__main__':
    run()
