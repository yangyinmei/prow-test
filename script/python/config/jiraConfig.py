# -*- coding: utf-8 -*-
'''
Created on 2017年10月23日

@author: Simba
'''

boss_issue_dict = {
    'project': "CIB",
    'summary': '',
    'description': '',
    'issuetype': {'name': 'Task'},
    # 'components': [{'name': None}],
}

ott_issue_dict = {
    'project': "OTTCI",
    'summary': '',
    'description': '',
    'issuetype': {'name': 'Task'},
    # 'components': [{'name': None}],
}

boss_bug_issue_dict = {
    'project': "STARIBOSS",
    'summary': '',  # 问题主题
    'description': '',  # 问题描述
    'issuetype': {'name': 'Bug'},  # 问题类型
    'priority': {'id': '1'},  # 优先级
    'assignee': {'name': ''},  # 经办人
    'customfield_12103': [],  # 抄送通知
}

# JIRA_URL
JIRA_URL = 'http://10.0.251.127:8080/'


# JIRA_AUTH
JIRA_AUTH = ('autotest', '123456')
