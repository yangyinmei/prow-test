# -*- coding: utf-8 -*-
'''
Created on 2017Äê6ÔÂ21ÈÕ

@author: Simba
'''
import logging
import os
import time
import config.config as CONFIG

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


def getFileList(filePath):
    list_dirs = os.walk(filePath)
    for root, _, files in list_dirs:
        for f in files:
            if "-run.log" in f:
                os.remove(os.path.join(root, f))


def initLogging(logFilePath=None):
    '''
    Init for logging
    '''
    global CONSOLE
    if logFilePath is None:
        getFileList(CONFIG.LOG_HOME)
        if os.path.isdir(CONFIG.LOG_HOME) is False:
            os.makedirs(CONFIG.LOG_HOME)
        '''time format %Y-%m-%d-%H-%M-%S'''
        logFile = CONFIG.LOG_HOME + str(time.strftime('%Y-%m-%d-%H-%M-%S', time.localtime())) + "-run.log"
    else:
        getFileList(logFilePath)
        logFile = logFilePath + '/' + str(time.strftime('%Y-%m-%d-%H-%M-%S', time.localtime())) + "-run.log"

    logging.basicConfig(
        level=logging.DEBUG,
        format='[%(asctime)s] LINE %(lineno)-4d : %(levelname)-8s %(message)s',
        datefmt='%m-%d %H:%M',
        filename=logFile,
        filemode='a'
    )

    # define a Handler which writes INFO messages or higher to the sys.stderr
    CONSOLE = logging.StreamHandler()
    CONSOLE.setLevel(logging.INFO)
    # set a format which is simpler for console use
    formatter = logging.Formatter('[%(asctime)s] LINE %(lineno)-4d : %(levelname)-8s %(message)s')
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
