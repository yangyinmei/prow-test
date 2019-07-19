# -*- coding: utf-8 -*-
'''
Created on 2018年11月05日

@author: Yangym
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

    ftpManager.ftpDeleteDir(os.path.join(uploadFtpDir, version, tag), tag)
    print "Delete producet from ftp success!"


if __name__ == "__main__":
    run()
