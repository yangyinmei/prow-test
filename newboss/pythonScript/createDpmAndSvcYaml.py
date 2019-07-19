# -*- coding: utf-8 -*-
'''
Created on 2017年9月25日

@author: Simba
'''
import sys
import config.config as CONFIG
import os
import tools.ftpManager as ftpManager


def createDpmAndSvcYaml(sourceFilePath, desFilePath, tag):
    dataDic = {
        "spec.template.spec.containers.image.repository": "{{ image_repository }}",
        "spec.template.spec.containers.image.tag": tag}

    fread = open(sourceFilePath, 'r')
    fStr = fread.read()
    fread.close()

    targetStr = fStr % dataDic

    fwrite = open(desFilePath, 'w')
    fwrite.write(targetStr)
    fwrite.close()


#  逐级检查ftp上存放yaml的目录是否存在，如果不存在则创建
def checkFtpYamlDir(ftpDir):
    ftpManager.recursiveCreateFtpdir(ftpManager.getSubsectionDir(ftpDir))


def uploadYamlFile(codeDir, moduleName, version, tag, isChanged):
    if moduleName == "pms-frontend-conax-service":
        srcFilePath1 = os.path.join(codeDir, "outputFileFolder", "pms-frontend-conax-service.yaml")
        srcFilePath2 = os.path.join(codeDir, "outputFileFolder", "pms-frontend-conax-s-service.yaml")

        desFtpPath1 = os.path.join("/DataBk/build/stariboss-10.X/yaml", version, tag, isChanged, "pms-frontend-conax-service.yaml")
        desFtpPath2 = os.path.join("/DataBk/build/stariboss-10.X/yaml", version, tag, isChanged, "pms-frontend-conax-s-service.yaml")

        ftpManager.ftpUploadFile(CONFIG.FTP_HOST, CONFIG.FTP_USER_PWD[0], CONFIG.FTP_USER_PWD[1], srcFilePath1, desFtpPath1)
        ftpManager.ftpUploadFile(CONFIG.FTP_HOST, CONFIG.FTP_USER_PWD[0], CONFIG.FTP_USER_PWD[1], srcFilePath2, desFtpPath2)
    else:
        srcFilePath = os.path.join(codeDir, "outputFileFolder", "%s.yaml" % moduleName)
        desFtpPath = os.path.join("/DataBk/build/stariboss-10.X/yaml", version, tag, isChanged, "%s.yaml" % moduleName)
        ftpManager.ftpUploadFile(CONFIG.FTP_HOST, CONFIG.FTP_USER_PWD[0], CONFIG.FTP_USER_PWD[1], srcFilePath, desFtpPath)


def run():
    '''
    :param 脚本本身（非传入参数）
    :param codeDir:   代码目录
    :param moduleName: 模块名
    :param tag: 标签
    :param version: 版本
    :param isChanged: 是否变更
    :param yamlTemplateDir: yaml模板路径
    :param yamlDir: 生成的yaml文件的根目录
    '''

    codeDir = sys.argv[1]
    moduleName = sys.argv[2]
    tag = sys.argv[3]
    version = sys.argv[4]
    isChanged = sys.argv[5]
    yamlTemplateDir = sys.argv[6]
    yamlDir = sys.argv[7]

    # 利用模板生成对应的yaml文件
    if moduleName == "pms-frontend-conax-service":
        srcFilePath1 = os.path.join(yamlTemplateDir, "pms-frontend-conax-service.yaml")
        srcFilePath2 = os.path.join(yamlTemplateDir, "pms-frontend-conax-s-service.yaml")
        srcFilePath3 = os.path.join(yamlTemplateDir, "pms-frontend-conax-mg-service.yaml")

#         desFilePath1 = os.path.join(codeDir, "outputFileFolder", "pms-frontend-conax-service.yaml")
#         desFilePath2 = os.path.join(codeDir, "outputFileFolder", "pms-frontend-conax-s-service.yaml")

        desFilePath1 = os.path.join(yamlDir, version, tag, isChanged, "pms-frontend-conax-service.yaml")
        desFilePath2 = os.path.join(yamlDir, version, tag, isChanged, "pms-frontend-conax-s-service.yaml")
        desFilePath3 = os.path.join(yamlDir, version, tag, isChanged, "pms-frontend-conax-mg-service.yaml")

        createDpmAndSvcYaml(srcFilePath1, desFilePath1, tag)
        createDpmAndSvcYaml(srcFilePath2, desFilePath2, tag)
        createDpmAndSvcYaml(srcFilePath3, desFilePath3, tag)
    elif moduleName == "jobserver":
        srcFilePath1 = os.path.join(yamlTemplateDir, "jobserver-aws.yaml")
        srcFilePath2 = os.path.join(yamlTemplateDir, "uploadjarfile.yaml")
        srcFilePath3 = os.path.join(yamlTemplateDir, "jobserver-local.yaml")

#         desFilePath1 = os.path.join(codeDir, "outputFileFolder", "jobserver.yaml")
#         desFilePath2 = os.path.join(codeDir, "outputFileFolder", "uploadjarfile.yaml")

        desFilePath1 = os.path.join(yamlDir, version, tag, isChanged, "jobserver-aws.yaml")
        desFilePath2 = os.path.join(yamlDir, version, tag, isChanged, "uploadjarfile.yaml")
        desFilePath3 = os.path.join(yamlDir, version, tag, isChanged, "jobserver-local.yaml")

        createDpmAndSvcYaml(srcFilePath1, desFilePath1, tag)
        createDpmAndSvcYaml(srcFilePath2, desFilePath2, tag)
        createDpmAndSvcYaml(srcFilePath3, desFilePath3, tag)
    else:
        srcFilePath = os.path.join(yamlTemplateDir, "%s.yaml" % moduleName.lower())
        desFilePath = os.path.join(yamlDir, version, tag, isChanged, "%s.yaml" % moduleName.lower())
        createDpmAndSvcYaml(srcFilePath, desFilePath, tag)

    # 将生成的YAML文件，上传ftp
#     yamlFtpDir = os.path.join("/DataBk/build/stariboss-10.X/yaml", version, tag, isChanged)
#     checkFtpYamlDir(yamlFtpDir)
#
#     uploadYamlFile(codeDir, moduleName, version, tag, isChanged)


if __name__ == "__main__":
    run()







