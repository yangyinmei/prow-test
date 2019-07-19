# -*- coding: utf-8 -*-
'''
@author: sunyh
'''
import urllib2
import urllib
import cookielib
import json
import sys
import subprocess

argList = sys.argv
srcURL = argList[1]
desURL = argList[2]
srcUserName = argList[3]
srcPassword = argList[4]
desUserName = argList[5]
desPassword = argList[6]
projectNames = argList[7]
tag = argList[8]


class UpdateBossException(Exception):

    def __init__(self, value):
        self.value = value

    def __str__(self):
        return repr(self.value)


def commandRun(command):
    try:
        p = subprocess.Popen(command, stdin=subprocess.PIPE,stdout=subprocess.PIPE, stderr=subprocess.PIPE, shell=True)
        rsList = p.stdout.readlines()
        print(rsList)
        if len(rsList) != 0:
            msg = ",".join(list(rsList)).replace('\n', '')
            # print msg
            return msg
        else:
            errStrList = p.stderr.readlines()
            if len(errStrList) != 0:
                raise UpdateBossException(
                    ",".join(list(errStrList)).replace('\n', ''))
    except Exception as e:
        raise Exception(e)


def login(url, username, password):
    values = {"principal": username, "password": password}
    data = urllib.urlencode(values)
    url = url + "/login"
    cj = cookielib.CookieJar()
    opener = urllib2.build_opener(urllib2.HTTPCookieProcessor(cj))
    urllib2.install_opener(opener)
    req = urllib2.Request(url, data)
    response = urllib2.urlopen(req)
    return cj


def findProjectByName(url, token, projectName):
    urlOpener = urllib2.build_opener(urllib2.HTTPCookieProcessor(token))
    request = urllib2.Request(url + "/api/projects")
    urlResult = urlOpener.open(request)
    projects_json = urlResult.read()
    projects_dic = json.loads(projects_json)
    for project_dic in projects_dic:
        if project_dic["name"] == projectName:
            projectId = project_dic["project_id"]
    return projectId


def getImagesWithTag(srcURL, token, projectId, tag):
    # get special repository by projectId
    urlOpener = urllib2.build_opener(urllib2.HTTPCookieProcessor(token))
    request = urllib2.Request("%s/api/repositories?project_id=%s" % (srcURL, projectId))
    urlResult = urlOpener.open(request)
    repositories_list = json.loads(urlResult.read())
    repositories_listnew = []
    for imageName in repositories_list:
        print(imageName['name'])
        print("%s/api/repositories/%s/tags?detail=0" % (srcURL, imageName['name']))
        request = urllib2.Request("%s/api/repositories/%s/tags" % (srcURL, imageName['name']))
        urlResult = urlOpener.open(request)
        imageTags_list = json.loads(urlResult.read())
        for imageTag in imageTags_list:
            if tag == imageTag['name']:
                repositories_listnew.append(imageName)
                break
    return repositories_listnew


def pullImagesTolocal(allImageNameFromProjects, srcURL, tag):
    for image in allImageNameFromProjects:
        imageName = image['name']
        commandRun("docker pull %s/%s:%s" % (srcURL.split("//")[-1], imageName, tag))
        # print "docker tag %s/%s:%s %s/%s:%s " % (srcURL.split("//")[-1],
        # imageName, tag, desURL.split("//")[-1], imageName, tag)
        commandRun("docker tag %s/%s:%s %s/%s:%s " % (srcURL.split("//")[-1], imageName, tag, desURL.split("//")[-1], imageName, tag))


def pushImagesToDes(allImageNameFromProjects, srcURL, desURL, tag):
    for image in allImageNameFromProjects:
        imageName = image['name']
        print "docker push %s/%s:%s" % (desURL.split("//")[-1], imageName, tag)
        commandRun("docker push %s/%s:%s" % (desURL.split("//")[-1], imageName, tag))
        print "docker rmi %s/%s:%s" % (desURL.split("//")[-1], imageName, tag)
        commandRun("docker rmi %s/%s:%s" %(srcURL.split("//")[-1], imageName, tag))
        commandRun("docker rmi %s/%s:%s" %(desURL.split("//")[-1], imageName, tag))


if __name__ == '__main__':
    token = login(srcURL, srcUserName, srcPassword)
    projectNames = projectNames.split(',')
    allImageNameFromProjects = []
    for i in range(len(projectNames)):
        projectId = findProjectByName(srcURL, token, projectNames[i].strip())
        # 获取含有指定标签的全部镜像
        result_list = getImagesWithTag(srcURL, token, projectId, tag)
        allImageNameFromProjects.extend(result_list)
    # 更改本地docker配置文件将--insecure-registry设置为srcURL
    # commandRun("sed -i \"/OPTIONS=/c OPTIONS='--selinux-enabled --log-driver=journald --signature-verification=false --insecure-registry %s'\" /etc/sysconfig/docker" %(srcURL.split("//")[-1]))
    # commandRun("systemctl restart  docker.service")
    # 拉取镜像到本地并更改标签
    pullImagesTolocal(allImageNameFromProjects, srcURL, tag)
    # 更改本地docker配置文件将--insecure-registry设置为desURL
    # commandRun("sed -i \" /OPTIONS=/c OPTIONS='--selinux-enabled --log-driver=journald --signature-verification=false --insecure-registry %s' \" /etc/sysconfig/docker" %(desURL.split("//")[-1]))
    # commandRun("systemctl restart  docker.service")
    # print "docker login -u %s -p %s %s" % (desUserName, desPassword, desURL.split("//")[-1])
    commandRun("docker login -u %s -p %s %s" % (desUserName, desPassword, desURL.split("//")[-1]))
    # push镜像到指定harbor并删除本地镜像
    pushImagesToDes(allImageNameFromProjects, srcURL, desURL, tag)
    commandRun("docker logout %s" % (desURL.split("//")[-1]))
