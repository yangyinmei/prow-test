# -*- coding: utf-8 -*-
'''
Created on 2017年8月19日

@author: Simba
'''


import logging
import os
import time
import sys
import config.config as CONFIG
sys.path.append("..")


global CONSOLE


def loggingDemo():
    """
    Just demo basic usage of logging module
    """
    logging.info("You should see this info both in log file and cmd window")
    logging.warning("You should see this warning both in log file and cmd window")
    logging.error("You should see this error both in log file and cmd window")
    logging.debug("You should ONLY see this debug in log file")
    return


def initLogging(logFilePath=None):
    '''
    Init for logging
    '''
    global CONSOLE
    if logFilePath is None:
        if os.path.isdir(CONFIG.LOG_HOME) is False:
            os.makedirs(CONFIG.LOG_HOME)
        '''time format %Y-%m-%d-%H-%M-%S'''
        logFile = os.path.join(CONFIG.LOG_HOME, str(time.strftime('%Y-%m-%d-%H-%M-%S', time.localtime())) + "-run.log")
    else:
        logFile = logFilePath + '/' + str(time.strftime('%Y-%m-%d-%H-%M-%S', time.localtime())) + "-run.log"
    if CONFIG.LOG_THREAD_PROCESS == 0:
        logFileFormat = '[%(asctime)s] LINE %(lineno)-4d: %(levelname)-8s %(message)s'
    elif CONFIG.LOG_THREAD_PROCESS == 1:
        logFileFormat = '[%(asctime)s] LINE %(lineno)-4d Thread %(thread)d: %(levelname)-8s %(message)s'
    elif CONFIG.LOG_THREAD_PROCESS == 2:
        logFileFormat = '[%(asctime)s] LINE %(lineno)-4d %(process)d: %(levelname)-8s %(message)s'
    else:
        logFileFormat = '[%(asctime)s] LINE %(lineno)-4d %(process)d Thread %(thread)d: %(levelname)-8s %(message)s'
    logging.basicConfig(
        level=logging.DEBUG,
        format=logFileFormat,
        datefmt='%m-%d %H:%M',
        filename=logFile,
        filemode='a'
    )
    
    # define a Handler which writes INFO messages or higher to the sys.stderr
    CONSOLE = logging.StreamHandler()
    CONSOLE.setLevel(logging.INFO)
    # set a format which is simpler for console use
    if CONFIG.LOG_THREAD_PROCESS == 0:
        formatter = logging.Formatter('[%(asctime)s] LINE %(lineno)-4d: %(levelname)-8s %(message)s')
    elif CONFIG.LOG_THREAD_PROCESS == 1:
        formatter = logging.Formatter('[%(asctime)s] LINE %(lineno)-4d Thread %(thread)d: %(levelname)-8s %(message)s')
    elif CONFIG.LOG_THREAD_PROCESS == 2:
        formatter = logging.Formatter('[%(asctime)s] LINE %(lineno)-4d %(process)d: %(levelname)-8s %(message)s')
    else:
        formatter = logging.Formatter('[%(asctime)s] LINE %(lineno)-4d %(process)d Thread %(thread)d: %(levelname)-8s %(message)s')
    # tell the handler to use this format
    CONSOLE.setFormatter(formatter)
    logging.getLogger().addHandler(CONSOLE)


def destoryLogging():
    global CONSOLE
    logging.getLogger().removeHandler(CONSOLE)
 

if __name__ == "__main__":
    logFilename = "run1.log"
    initLogging()
    loggingDemo()
    destoryLogging()
    