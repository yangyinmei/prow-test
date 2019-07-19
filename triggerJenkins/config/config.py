# -*- coding: utf-8 -*-
'''
Created on 2017年2月28日

@author: Simba
'''
import os


# 脚本存放根目录
ROOT_HOME = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))


# 脚本日志存放根目录
LOG_HOME = ROOT_HOME + os.sep + 'logs/'


# 访问jenkins的user_id和api_token
USERID_AND_APITOKEN_173 = ['jenkins',
                           '1183cd81dd1b9b4df9ab7bd097dd07979b',
                           'jenkins'
                           ]

# jenkins地址
JENKINS_URL = 'http://10.0.250.72:8080/jenkins/'
JENKINS_URL_173 = 'http://192.168.32.173:8081/jenkins'

# 邮件标题关键字符串与jenkins的jobname的关系: key：邮件标题中的关键字字符串；value: jobname
JOBNAME_TRIGGERSTR = {'aqos': 'AQoS',
                      'ottapplication': 'ottapplication',
                      'ottplatform': 'ottplatform',
                      'starapp-android': 'starapp-android',
                      'originalboss': 'OriginalBOSS',
                      'starecms': 'StarECMS',
                      'starfsaapp-ios': 'starfsaapp-ios',
                      'starcdn-cache': 'starcdn-cache',
                      'starcdn-gslb': 'starcdn-gslb',
                      'staret': 'staret',
                      'staros': 'staros',
                      'starott-gradle': 'starott-gradle',
                      'starott-stat-sadb': 'starott-stat-sadb',
                      'starvideo-process-starcdn': 'starvideo-process-starcdn',
                      'starprolauncher': 'StarProLauncher',
                      'starprosetting': 'StarProSetting',
                      'starproxiaoyi': 'StarProXiaoYi',
                      'starapp-win8': 'starapp-win8',
                      'star-selfservice-web': 'star-selfservice-web',
                      'ip-dispatch': 'ip-dispatch',
                      'starda-app': 'starDA-app',
                      'starda-web': 'starDA-web',
                      'starda_newboss': 'starda_newboss',
                      'Starea-app': 'StarEA-App',
                      'startimes-erp': 'startimes-erp',
                      'starfsa-app': 'starfsa-app',
                      'starfsa-middle-layer': 'starfsa-middle-layer',
                      'starbi': 'starbi',
                      'stardss-app': 'stardss-app',
                      'stardss-service': 'stardss-service',
                      'starmboss_app': 'starmboss_app',
                      'hotel_huangshi': 'hotel_huangshi',
                      'starda_combine': 'starda_combine',
                      'starba': 'starBA',
                      'starltp': 'starltp',
                      'stariboss-test': 'stariboss-test',
                      'stariboss-helm': 'stariboss-helm',
                      }
# 在192.168.32.173的pod中构建的作业列表
SLAVELIST_173 = {
  "AQoS",
  "starapp-android",
  "OriginalBOSS",
  "StarECMS",
  "starfsaapp-ios",
  "starcdn-cache",
  "starcdn-gslb",
  "staret",
  "staros",
  "starott-gradle",
  "starott-stat-sadb",
  "starvideo-process-starcdn",
  "StarProLauncher",
  "StarProSetting",
  "StarProXiaoYi",
  "starapp-win8",
  "star-selfservice-web",
  "ip-dispatch",
  "starDA-app",
  "starDA-web",
  "starda_newboss",
  "StarEA-App",
  "startimes-erp",
  "starfsa-app",
  "starfsa-middle-layer",
  "starbi",
  "stardss-app",
  "stardss-service",
  "starmboss_app",
  "hotel_huangshi",
}

# 异常字典
EXCEPTION_DIC = {'no_new_mail': 'There is no new email need to deal with.',
                 'no_match_keystr': ('There is no keyStr found in keyStrList or the keyStr was found more than once '
                                     'in the keyStrList.  Please check the subject of email and contact the administrator.'
                                     ),
                 'repeated parameter': 'Please make sure that the parameter is unique in the email.',
#                 'no parameter': 'There is no parameter in the email, but the job wanted to be triggered need some parameters.',
#                 'parameter count wrong': 'The number of parameter the email given, is not equal to the number of the job needs.',
                 'parameter not enough': 'Parameters in the email are not enough.\n'
                 }
