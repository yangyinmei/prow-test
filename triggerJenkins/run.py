# -*- coding: utf-8 -*-
'''
Created on 2017年2月28日

@author: Simba
'''
import config.config as CONFIG
import logService.logService as logService
import logging
import receiveAndSendMail
import functionSet
import jenkinsManager
import time
import sys


def start():
    reload(sys)
    # print sys.getdefaultencoding()
    sys.setdefaultencoding('utf-8')
    mailDicList = receiveAndSendMail.getMailInfo()
    logging.info("Receive mails complete!")
    if len(mailDicList) == 0:
        logging.info(CONFIG.EXCEPTION_DIC['no_new_mail'])
    else:
        for mailInfoDic in mailDicList:
            # 原标题
            mailSubject = mailInfoDic['subject']
            logging.info("mailSubject:" + mailSubject)

            # 原邮件正文
            mailBody = mailInfoDic['body']
            mailfrom = mailInfoDic['from'][0]
            mailtoTempList = mailInfoDic['cc'] + mailInfoDic['to'] + mailInfoDic['from']

            # 获得回复邮件列表
            mailtoList = []
            for mailAddress in mailtoTempList:
                if mailAddress:
                    mailtoList.append(mailAddress)
            if 'yangym@startimes.com.cn' not in mailtoList:
                mailtoList.append('yangym@startimes.com.cn')
            if 'jenkins@startimes.com.cn' in mailtoList:
                mailtoList.remove('jenkins@startimes.com.cn')
            # 由新收件人列表获得收件人字符串
            mailtoStr = ','.join(mailtoList)

            # 获得回复标题
            newSubject = 'reply: ' + mailSubject

            startStr = 'Now start to build, Please wait patiently for a reply from jenkins@startimes.com.cn unless time is too long.'

            # 由邮件标题关键字获得对应的jobname，邮件标题先全部转为小写
            mailSubjectForMatch = mailSubject.lower()
            matchCount, jobName = functionSet.getJobNameToTrigger(mailSubjectForMatch)
            logging.info("matchCount: " + str(matchCount) + ", jobName:" + jobName)

            # 邮件标题关键字在config.py中是否存在且唯一匹配
            if matchCount != 1:
                logging.error("Keystr of job %s error." % jobName)
                logging.error(CONFIG.EXCEPTION_DIC['no_match_keystr'])
                receiveAndSendMail.keepSendMail(mailtoList, 'Failed ' + newSubject, CONFIG.EXCEPTION_DIC['no_match_keystr'])
            else:
                # 获得job所需的参数列表
                parameterOfJobList = jenkinsManager.getParametersOfJob(jobName)
                print("parameterOfJobList:")
                print(parameterOfJobList)

                # 从邮件中获得参数字典
                errorStr, param_dict = functionSet.getParameterDic(mailBody)
                # 为获得的参数字典增加收件人，提供给job构建完成后发送邮件使用
                param_dict['mailto'] = mailtoStr
                param_dict['originalsubject'] = mailSubject
                param_dict['BUILDER'] = mailfrom
                print("param_dict:")
                print(param_dict)

                # 确定邮件给出的字典中是否提供了job所需的所有参数
                boolFlag = functionSet.ifListInDicKey(parameterOfJobList, param_dict)
                # 所需的参数，邮件中没有给全
                if not boolFlag:
                    logging.error("Some parameters needed were not given in the email.")
                    lackOfParameterMessageStr = functionSet.getMessageForParameterNotEnough(parameterOfJobList, param_dict)
                    receiveAndSendMail.keepSendMail(mailtoList, "Failed! Can't start to build! " + newSubject, lackOfParameterMessageStr)
                else:
                    # 所需参数邮件中已给全,且邮件中没有重复参数，例如，存在多次回复的内容
                    if errorStr == 'ok':
                        # job不需要传入参数
                        if len(parameterOfJobList) == 0:
                            logging.info("Start to trigger the job %s." % jobName)
                            jenkinsManager.triggerJenkinsWithoutparameter(jobName)
                            receiveAndSendMail.keepSendMail(mailtoList, "Start building %s " % jobName + newSubject, startStr)
                        else:
                            logging.info("Start to trigger the job %s with parameters." % jobName)
                            jenkinsManager.triggerJenkinsWithparameter(jobName, param_dict)
                            receiveAndSendMail.keepSendMail(mailtoList, "Start building %s " % jobName + newSubject, startStr)
                    # 邮件中参数重复（通常是存在回复的邮件，即邮件正文中存在多个模板表格）
                    else:
                        logging.error(CONFIG.EXCEPTION_DIC['repeated parameter'])
                        receiveAndSendMail.keepSendMail(mailtoList, "Faild! repeated parameter! " + newSubject, CONFIG.EXCEPTION_DIC[errorStr])


def run():
    while True:
        start()
        logging.info("Sleep 300s......")
        logging.info(" ")
        time.sleep(300)


if __name__ == '__main__':
    logService.initLogging()
    '''
    :param 脚本本身
    '''
    start()
    logService.destoryLogging()
