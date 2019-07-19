# -*- coding: utf-8 -*-
'''
Created on 2017年10月19日

@author: Simba
'''


from jira import JIRA
import config.jiraConfig as jiraConfig
import sys
import os


# shell
# curl -u simba:aivivi --header "Content-Type: application/json" -X POST --data '{"fields":{"project":{"key":"CIB"},"summary":"Test ChargenNr","description":"some description","issuetype":{"name":"Task"}}}' http://10.0.250.73:8080/rest/api/latest/issue/


def createTaskOfJira(branch, newTag, descriptionStr, buildType):
    # summaryStr = "Commit infomations between %s and %s." % (oldTag, newTag)
    summaryStr = "Commit infomations of %s in branch %s, build type: %s." % (newTag, branch, buildType)
    issue_dict = jiraConfig.issue_dict
    issue_dict['summary'] = summaryStr
    issue_dict['description'] = descriptionStr
    jira = JIRA(jiraConfig.JIRA_URL, basic_auth=jiraConfig.JIRA_AUTH)
    jira.DEFAULT_OPTIONS['headers']['Content-Type'] = 'application/json'
    issueKey = jira.create_issue(fields=issue_dict)
    return issueKey
 
 
def run():
    '''
    :param 脚本本身（非传入参数）
    :param oldTag: 旧标签
    :param newTag: 新标签
    :param descriptionStr: issue描述
    :param branch: 代码所属分支
    :param buildType: 全量构建或增量构建
    '''
    oldTag = sys.argv[1]
    newTag = sys.argv[2]
    descriptionStr = sys.argv[3]
    branch = sys.argv[4]
    buildType = sys.argv[5]
    issueKey = createTaskOfJira(branch, newTag, descriptionStr, buildType)
    issueLink = issueKey.permalink()
    print "================"
    print issueKey.permalink()
    print type(issueKey)
    print "================"
     
    issueUrlFile = os.path.join('/home/transfer', 'issueUrlFile')
    f = open(issueUrlFile, 'w')
    f.write(issueLink)
    f.close
 
 
#
if __name__ == '__main__':
    run()


