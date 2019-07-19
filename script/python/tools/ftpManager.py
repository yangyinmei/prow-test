# -*- coding: utf-8 -*-
'''
Created on 2017年9月19日

@author: vivis
'''
import ftplib
import logging
import os
import config.config as CONFIG


class myFtp:
    ftp = ftplib.FTP()
    bIsDir = False
    path = ""

    def connetc(self, host='', port=21, timeout=-999):
        self.ftp.connect(self, host, port, timeout)

    def getwelcome(self):
        self.ftp.getwelcome()

    def cwd(self, dirname):
        res = self.ftp.cwd(dirname)
        return res

    def dir(self, *args):
        self.ftp.dir(*args)

    def __init__(self, host, port='21'):
        # self.ftp.set_debuglevel(2) # 打开调试级别2，显示详细信息
        # self.ftp.set_pasv(0)      # 0主动模式 1 #被动模式
        self.ftp.connect(host, port)

    def Login(self, user, passwd):
        self.ftp.login(user, passwd)
        logging.info(self.ftp.welcome)

    def DownLoadFile(self, LocalFile, RemoteFile):
        file_handler = open(LocalFile, 'wb')
        self.ftp.retrbinary("RETR %s" % (RemoteFile), file_handler.write)
        file_handler.close()
        return True

    def UpLoadFile(self, LocalFile, RemoteFile):
        if os.path.isfile(LocalFile) is False:
            return False
        file_handler = open(LocalFile, "rb")
        self.ftp.storbinary('STOR %s' % RemoteFile, file_handler, 4096)
        file_handler.close()
        return True

    def UpLoadFileTree(self, LocalDir, RemoteDir):
        if os.path.isdir(LocalDir) is False:
            return False
        logging.info("LocalDir: %s" % LocalDir)
        LocalNames = os.listdir(LocalDir)
        print "list:", LocalNames

        print RemoteDir
        self.ftp.cwd(RemoteDir)
        for Local in LocalNames:
            src = os.path.join(LocalDir, Local)
            if os.path.isdir(src):
                self.UpLoadFileTree(src, Local)
            else:
                self.UpLoadFile(src, Local)
        self.ftp.cwd("..")
        return

    def DownLoadFileTree(self, LocalDir, RemoteDir):
        print "remoteDir:", RemoteDir
        if os.path.isdir(LocalDir) is False:
            os.makedirs(LocalDir)
        self.ftp.cwd(RemoteDir)
        RemoteNames = self.ftp.nlst()
        print "RemoteNames", RemoteNames
        print self.ftp.nlst("/del1")
        for filee in RemoteNames:
            Local = os.path.join(LocalDir, filee)
            if self.isDir(filee):
                self.DownLoadFileTree(Local, filee)
            else:
                self.DownLoadFile(Local, filee)
        self.ftp.cwd("..")
        return

    def show(self, listt):
        result = listt.lower().split(" ")
        if self.path in result and "<dir>" in result:
            self.bIsDir = True

    def isDir(self, path):
        self.bIsDir = False
        self.path = path
        # this ues callback function ,that will change bIsDir value
        self.ftp.retrlines('LIST', self.show)
        return self.bIsDir

    def retrlines(self, cmd, callback=None):
        res = self.ftp.retrlines(cmd, callback)
        return res

    def mkd(self, dirname):
        resStr = self.ftp.mkd(dirname)
        return resStr

    def rmd(self, dirname):
        resStr = self.ftp.rmd(dirname)
        return resStr

    def delete(self, filename):
        resStr = self.ftp.delete(filename)
        return resStr

    def pwd(self):
        resStr = self.ftp.pwd()
        return resStr

    def nlst(self):
        resStr = self.ftp.nlst()
        return resStr

    def close(self):
        self.ftp.quit()


# 获得指定目录的，各级父目录列表
def getSubsectionDir(dirs):
    dirNameList = dirs.split("/")
    subsectionDirList = []
    for i in range(len(dirNameList)):
        if i == 0:
            subsectionDirList.append("/")
        else:
            newDirNameList = dirNameList[0:i + 1]
            dirStr = "/".join(newDirNameList)
            subsectionDirList.append(dirStr)
    return subsectionDirList


# 递归创建ftp目录
def recursiveCreateFtpdir(dirList):
    ftp = myFtp(CONFIG.FTP_HOST)
    ftp.Login(CONFIG.FTP_USER_PWD[0], CONFIG.FTP_USER_PWD[1])
    for dirStr in dirList:
        try:
            ftp.cwd(dirStr)
        except ftplib.error_perm:
            ftp.mkd(dirStr)
    ftp.close()


# ftp下载目录
def ftpDownloadDir(ftpHost, ftpuser, ftpPwd, localDir, remoteDir):
    ftp = myFtp(ftpHost)
    ftp.Login(ftpuser, ftpPwd)
    ftp.DownLoadFileTree(localDir, remoteDir)
    ftp.close()


# ftp上传目录, 目前不支持目录下还有目录的情况。
def ftpUploadDir(ftpHost, ftpuser, ftpPwd, localDir, remoteDir):
    ftp = myFtp(ftpHost)
    ftp.Login(ftpuser, ftpPwd)
    ftp.UpLoadFileTree(localDir, remoteDir)
    ftp.close()


# ftp下载文件
def ftpDownloadFile(ftpHost, ftpuser, ftpPwd, localFilepath, remoteFilepath):
    ftp = myFtp(ftpHost)
    ftp.Login(ftpuser, ftpPwd)
    ftp.DownLoadFile(localFilepath, remoteFilepath)
    ftp.close()


# ftp上传文件

def ftpUploadFile(ftpHost, ftpuser, ftpPwd, localFilepath, remoteFilepath):
    ftp = myFtp(ftpHost)
    ftp.Login(ftpuser, ftpPwd)
    ftp.UpLoadFile(localFilepath, remoteFilepath)
    ftp.close()


# ftp删除目录
def ftpDeleteDir(remoteDir, delDir):
    ftp = myFtp(CONFIG.FTP_HOST)
    ftp.Login(CONFIG.FTP_USER_PWD[0], CONFIG.FTP_USER_PWD[1])
    print remoteDir
    ftp.cwd(remoteDir)
    list = ftp.nlst()
    for modulename in list:
        ftp.cwd(modulename)
        productlist = ftp.nlst()
        for product in productlist:
            ftp.delete(product)
        ftp.cwd("..")
        ftp.rmd(modulename)
    ftp.cwd("..")
    ftp.rmd(delDir)
    ftp.close()
