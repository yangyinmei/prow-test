# -*- coding: utf-8 -*-
'''
Created on 2017年10月19日

@author: Simba
'''

import sys
from jira import JIRA
import config.jiraConfig as jiraConfig
import os


def createTaskOfJira(summaryStr, descriptionStr, jobName):
    if jobName == "stariboss-helm":
        issue_dict = jiraConfig.boss_issue_dict
    else:
        issue_dict = jiraConfig.ott_issue_dict
    issue_dict['summary'] = summaryStr
    issue_dict['description'] = descriptionStr
    jira = JIRA(jiraConfig.JIRA_URL, basic_auth=jiraConfig.JIRA_AUTH)
    jira.DEFAULT_OPTIONS['headers']['Content-Type'] = 'application/json'
    issueKey = jira.create_issue(fields=issue_dict)
    jira.add_attachment(issue=issueKey, attachment='/tmp/commitMessage.txt')
    return issueKey


def createBugOfJira(summaryStr, descriptionStr, developer, ccGroup):
    issue_dict = jiraConfig.boss_bug_issue_dict
    issue_dict['summary'] = summaryStr
    issue_dict['description'] = descriptionStr
    issue_dict['assignee']['name'] = developer
    issue_dict['customfield_12103'] = [{'name': ccGroup}]
    jira = JIRA(jiraConfig.JIRA_URL, basic_auth=jiraConfig.JIRA_AUTH)
    jira.DEFAULT_OPTIONS['headers']['Content-Type'] = 'application/json'
    issueKey = jira.create_issue(fields=issue_dict)
    jira.add_attachment(issue=issueKey, attachment='/tmp/log.txt')
    return issueKey


def run():
    '''
    :param 脚本本身（非传入参数）
    :param newTag: 新标签
    :param descriptionStr: issue描述
    '''
    summaryStr = sys.argv[1]
    descriptionStr = sys.argv[2]
    jobName = sys.argv[3]
    buildStatus = sys.argv[4]
    developer = sys.argv[5]
    ccGroup = sys.argv[6]
    print("*********************")
    print(summaryStr)
    print(descriptionStr)
    print(jobName)
    print(buildStatus)
    print(developer)
    print(ccGroup)
    print("*********************")
    if buildStatus == "unset":
        issueKey = createTaskOfJira(summaryStr, descriptionStr, jobName)
        issueLink = issueKey.permalink()
        print("================")
        print(issueKey.permalink())
        print(type(issueKey))
        print("================")

        issueUrlFile = os.path.join('/home/source/transfer', 'issueUrlFile')
        f = open(issueUrlFile, 'w')
        f.write(issueLink)
        f.close
    else:
        issueKey = createBugOfJira(summaryStr, descriptionStr, developer, ccGroup)


if __name__ == '__main__':
    run()
