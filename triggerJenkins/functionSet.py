# -*- coding: utf-8 -*-
'''
Created on 2017年6月21日

@author: Simba
'''
import config.config as CONFIG
import chardet


# 获得需要触发的jobname
def getJobNameToTrigger(mailSubjectStr):
    triggerstrDicKeyList = CONFIG.JOBNAME_TRIGGERSTR.keys()
    matchCount = 0
    jobName = ''
    for key in triggerstrDicKeyList:
        if key in mailSubjectStr:
            matchCount += 1
            jobName = CONFIG.JOBNAME_TRIGGERSTR[key]
        else:
            continue
    return matchCount, jobName


# 根据jobname从邮件内容中获取相应参数以及参数值
def getParameterDic(mailBody):
    initialMailBodyList = mailBody.split("\n")
    paramStrTempList = []
    paramStrList = []
    errorStr = ''
    parameterDic = {}
    for i in initialMailBodyList:
        if (len(i) != 0) and ('=' in i):
            paramStrTempList.append(i)
    for j in paramStrTempList:
        j = j.replace(' ', '')
        j = j.strip()
        if j not in paramStrList:
            paramStrList.append(j)
        else:
            errorStr = 'repeated parameter'
    if errorStr == 'repeated parameter':
        return errorStr, parameterDic
    else:
        newparamStrList = []
        for line in paramStrList:
            if u'构建传递参数' in line:
                line = line.replace(u'构建传递参数', '')
            newparamStrList.append(line)
        errorStr = 'ok'
        for parameStr in newparamStrList:
            index = parameStr.find('=')
            keyStr = parameStr[0:index]
            valueStr = parameStr[index + 1:]
            parameterDic[keyStr] = valueStr
        return errorStr, parameterDic


# 列表中的所有元素，在字典的键组成的列表中是否都存在
def ifListInDicKey(theList, theDic):
    for i in theList:
        if i not in theDic.keys():
            return False
    return True


# 传入参数不足的邮件内容
def getMessageForParameterNotEnough(theList, theDic):
    jobParametersList = theList[:]
    inputList = theDic.keys()[:]

    # 排除邮件自动带入的参数
    for jobParameter in theList:
        if 'mailto' == jobParameter:
            jobParametersList.remove('mailto')
        if 'originalsubject' == jobParameter:
            jobParametersList.remove('originalsubject')
        if 'BUILDER' == jobParameter:
            jobParametersList.remove('BUILDER')

    # 排除邮件自动带入的参数
    for inputParameter in theDic.keys()[:]:
        if 'mailto' == inputParameter:
            inputList.remove('mailto')
        if 'originalsubject' == inputParameter:
            inputList.remove('originalsubject')
        if 'BUILDER' == inputParameter:
            inputList.remove('BUILDER')

    # 将列表排序
    jobParametersList.sort()
    inputList.sort()

    # 由列表组织邮件内容字符串
    expectStr = CONFIG.EXCEPTION_DIC['parameter not enough']
    needStr = 'Expect parameters:\n'
    for i in jobParametersList:
        needStr = needStr + i + '\n'

    actualStr = 'Actual parameters:\n'
    for j in inputList:
        actualStr = actualStr + j + '\n'

    wholeStr = expectStr + needStr + actualStr
    return wholeStr


# 确定字符串编码格式
def getCharsetOfStr(theStr):
    strEncoding = chardet.detect(theStr)['encoding']
    return strEncoding


# 解码为unicode
def getUnicodeStr(theStr):
    newStr = theStr.decode(getCharsetOfStr(theStr))
    return newStr
