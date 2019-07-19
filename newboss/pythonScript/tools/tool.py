# -*- coding: utf-8 -*-
'''
Created on 2017年8月16日

@author: vivis
'''


import subprocess


# 本机执行命令，返回shell执行结果
def runShell(cmd):
    returnDic = {}
    p = subprocess.Popen(cmd, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE, shell=True)
    p.wait()
    outStrList = p.stdout.readlines()
    errStrList = p.stderr.readlines()
    returnDic['outStrList'] = outStrList
    returnDic['errStrList'] = errStrList
    return returnDic
    
    