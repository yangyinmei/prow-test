# -*- coding: utf-8 -*-
'''
@author: sunyh
'''
import urllib2
import urllib
import cookielib
import json
import time

import sys

# argList = sys.argv
# url = argList[1]
# username = argList[2]
# password = argList[3]
# projectName_str = argList[4]
# version = argList[5]


# url = "http://10.0.251.190"
# username = "admin"
# password = "asdf1234"
# projectName_str = "boss,billing"
# version = "development-test-1222"
# desRepositoryStr = "harborrelease"






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


def getCopyStatusByName(url, token, projectName):
    sleepTime = 30  # second
    timeout = 36000  # second

    urlOpener = urllib2.build_opener(urllib2.HTTPCookieProcessor(token))
    request = urllib2.Request(url + "/api/policies/replication")
    urlResult = urlOpener.open(request)
    polices_json = urlResult.read()
    polices_dic = json.loads(polices_json)

    result_dic = {}
    status = ["error", "retrying", "stopped",
              "canceled", "running"]
    # get policeId in specified projectName
    for police_dic in polices_dic:

        if police_dic["project_name"] == projectName and int(police_dic["enabled"]) == 1:
            result_list = []
            policeId = police_dic["id"]
            # get copy status by policeId
            startTime = time.time()
            runningflag = 1
            dataofStatusRunning = None
            dataofStatusRetrying = None
            while (runningflag and time.time() - startTime < timeout):
                dataofStatusRunning = getDataofRepositories(
                    urlOpener, url, policeId, "running")
                dataofStatusRetrying = getDataofRepositories(
                    urlOpener, url, policeId, "retrying")
                    
                if (len(dataofStatusRunning) == 0) and (len(dataofStatusRetrying) == 0):
                    runningflag = 0
                    break
                print "running, please wait......."
                time.sleep(sleepTime)

            # get data of specified status
            for i in range(len(status)):
                nameofData = "dataofStatus" + status[i]
                dataofStatus = getDataofRepositories(
                    urlOpener, url, policeId, status[i])
                if len(dataofStatus) != 0:
                    result_list.append(
                        {nameofData: [len(dataofStatus), nameofRepository(dataofStatus)]})
            if len(result_list) != 0:
                result_dic[police_dic["target_name"]] = result_list
            else:
                result_dic[police_dic["target_name"]] = "SUCCESS"
    return result_dic


def nameofRepository(dataofSpecifiedStatus):
    resultofRepository = []
    if len(dataofSpecifiedStatus) != 0:
        for temp in dataofSpecifiedStatus:
            resultofRepository.append(temp["repository"])
    return resultofRepository
# get data of Repositories in Specified state


def getDataofRepositories(urlOpener, url, policeId, status):
    page_size = 1000
    request = urllib2.Request(
        "%s/api/jobs/replication?policy_id=%s&status=%s&page_size=%s" % (url, policeId, status, page_size))
    urlResult = urlOpener.open(request)
    polices_json = urlResult.read()
    if "running" == status:
        return json.loads(polices_json)
    else:
        dataofSpecifiedStatus_list = json.loads(polices_json)
        for i in range(len(dataofSpecifiedStatus_list) - 1, -1, -1):
            dataofSpecifiedStatus = dataofSpecifiedStatus_list[i]
            # print type(dataofSpecifiedStatus["tags"])
            if (dataofSpecifiedStatus["tags"] is None) or (version not in dataofSpecifiedStatus["tags"]):
                dataofSpecifiedStatus_list.remove(dataofSpecifiedStatus)
        return dataofSpecifiedStatus_list


def format2txt(datas):
    file = open("./resultOfHarborCopy.txt", "wb")
    for data in datas:
        file.write("registry: " + data.keys()
                   [0] + "\ttag: " + version + "\n")
        for values in data.values():
            for target, value in values.items():
                if isinstance(value, str):
                    str1 = "\t" + "rule: " + target + ": " + value + "\n"
                    file.write(str1)
                else:
                    for resultOfstatus in value:
                        for key, value in resultOfstatus.items():
                            for i in range(len(value)):
                                if i == 0:
                                    file.write("\t" + "rule: " + target + "\n")
                                    file.write("\t\t" + key +
                                               ":   total: " + str(value[i]))
                                else:
                                    file.write(
                                        "\n\t\t\t\t\t" + "\n\t\t\t\t\t".join(value[i]) + "\n")
        file.write("\n")

    file.close()


if __name__ == '__main__':


    argList = sys.argv
    url = argList[1]
    username = argList[2]
    password = argList[3]
    projectName_str = argList[4]
    version = argList[5]
    desRepositoryStr = argList[6]


    projectName = projectName_str.split(",")
    token = login(url, username, password)
    resultofCopyStatus = []

    resList = []

    # 获得同步结果
    for i in range(len(projectName)):
        resTup = ()
        result = getCopyStatusByName(url, token, projectName[i].strip())

        for desRepository in result.keys():
            if len(result.values()) != 0:
                resTup = (projectName[i], desRepository, result[desRepository])
                resList.append(resTup)
    print resList


    # 比较同步结果
    statusFlag = True

    desRepositoryList = desRepositoryStr.split(',')
    print desRepositoryList
    for projectNameStr in projectName:
        for desRepository in desRepositoryList:
            if (projectNameStr, desRepository, 'SUCCESS') not in resList:
                statusFlag = False

    if statusFlag is False:
        sys.exit(1)
    else:
        sys.exit(0)