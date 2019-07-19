# -*- coding: utf-8 -*-
'''
Created on 2017年10月20日

@author: Simba
'''


import tools.ftpManager as ftpManager
import sys
import os


def run():
    '''
    :param 脚本本身（非传入参数）
    :param version: 版本号
    :param newTag: 标签
    '''
    version = sys.argv[1]
    tag = sys.argv[2]
    uploadFtpDir = sys.argv[3]
    ftpManager.recursiveCreateFtpdir(ftpManager.getSubsectionDir(os.path.join(uploadFtpDir, version, tag)))


if __name__ == "__main__":
    run()
