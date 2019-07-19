# -*- coding: utf-8 -*-
# -*- encoding: gbk -*-
'''
Created on 2017年2月28日

@author: Simba
'''


import imaplib
import email
import smtplib
# from email.mime.text import MIMEText
import os
import logging


# 字符编码转换方法
def my_unicode(s, encoding):
    if encoding:
        return unicode(s, encoding)
    else:
        return unicode(s)


# 获得字符编码方法
def get_charset(message, default="ascii"):
    # Get the message charset
    return message.get_charset()
    return default


# 保存文件方法（都是保存在指定的根目录下）
def savefile(filename, data, path):
    try:
        filepath = path + filename
        print 'Saved as ' + filepath
        f = open(filepath, 'wb')
    except:
        print('filename error')
        f.close()
    f.write(data)
    f.close()


# 将邮件地址字符串变为邮件地址列表
def addressStrToAddressList(addressStr):
    addressTempList = addressStr.split(",")
    addressList = []
    for i in addressTempList:
        i = i.replace(' ', '')
        left = i.find('<')
        right = i.rfind('>')
        if right == -1:
            mailAddress = i[left + 1:]
        else:
            mailAddress = i[left + 1:right]
        addressList.append(mailAddress)
    return addressList


def parseHeader(message):
    """ 解析邮件首部 """
    mailHeadDic = {}
    subject = email.Header.decode_header(message["Subject"])
    sub = my_unicode(subject[0][0], subject[0][1])
    # 主题
    mailHeadDic['subject'] = sub
    # 发件人
    mailHeadDic['from'] = [email.utils.parseaddr(message.get('from'))[1]]
    # 收件人
    mailtoAddressStr = email.utils.parseaddr(message.get_all('to'))[1]
    mailtoAddressList = addressStrToAddressList(mailtoAddressStr)
    mailHeadDic['to'] = mailtoAddressList
    # 抄送人
    mailccAddressStr = email.utils.parseaddr(message.get_all('cc'))[1]
    mailHeadDic['cc'] = addressStrToAddressList(mailccAddressStr)
    return mailHeadDic


def parseBody(message):
    """ 解析邮件/信体 """
    mailContent = ''
    # 循环信件中的每一个mime的数据块
    for part in message.walk():
        # 这里要判断是否是multipart，是的话，里面的数据是一个message 列表
        if not part.is_multipart():
            # charset = part.get_charset()
            # print 'charset: ', charset
            contenttype = part.get_content_type()
            # print 'content-type', contenttype
            name = part.get_param("name")  # 如果是附件，这里就会取出附件的文件名
            if name:
                # 暂时不处理附件
                pass
                # 有附件
                # 下面的三行代码只是为了解码象=?gbk?Q?=CF=E0=C6=AC.rar?=这样的文件名
                # fh = email.Header.Header(name)
                # fdh = email.Header.decode_header(fh)
                # fname = fdh[0][0]
                # print '附件名:', fname
                # attach_data = par.get_payload(decode=True) #　解码出附件数据，然后存储到文件中

                # try:
                #     f = open(fname, 'wb') #注意一定要用wb来打开文件，因为附件一般都是二进制文件
                # except:
                #     print '附件名有非法字符，自动换一个'
                #     f = open('aaaa', 'wb')
                # f.write(attach_data)
                # f.close()
            else:
                # 不是附件，是文本内容
                charset = part.get_content_charset()
                contenttype = part.get_content_type()
                if contenttype in ['text/plain']:
                    if charset:
                        mailContent = part.get_payload(decode=True).decode(charset)
                    else:
                        mailContent = type(part.get_payload(decode=True))
                # print type(part.get_payload(decode=True))
                # print part.get_payload(decode=True).decode('gb2312')  # 解码出文本内容，直接输出来就可以了。
                # pass
            # print '+'*60 # 用来区别各个部分的输出
    return mailContent


# 获取邮件
def getMailInfo():
    # 邮件字典列表
    mailDicList = []
    connection = imaplib.IMAP4('imap.startimes.com.cn', 143)
    username = 'jenkins@startimes.com.cn'
    password = 'Startimes12'
    # password = 'CItest7777'
    # mypath = '/jenkins/'
    try:
        connection.login(username, password)
    except Exception as err:
        print('Login Error:', err)
    # connection.print_log()
    # typ, data = connection.list()
    connection.select('INBOX', False)
    mailResp, mailItems = connection.search(None, 'Unseen')
    for i in mailItems[0].split():
        # 邮件字典
        mailInfoDic = {}
        resp, mailData = connection.fetch(i, "(RFC822)")
        mailText = mailData[0][1]
        mail_message = email.message_from_string(mailText)
        mailInfoDic = parseHeader(mail_message)
        mailInfoDic['body'] = parseBody(mail_message)
        mailDicList.append(mailInfoDic)
    connection.close()
    connection.logout()
    return mailDicList


def sendEmail(addresseeList, attachmentPath, subject='autotest report', message="附件是自动化测试报告，请查阅！"):
    sender = 'jenkins@startimes.com.cn'
#    server = smtplib.SMTP("218.205.167.18")
    server = smtplib.SMTP('smtp.startimes.com.cn')
#    server.set_debuglevel(1)
    server.login("jenkins@startimes.com.cn", "Startimes12")  # 仅smtp服务器需要验证时

    # 构造MIMEMultipart对象做为根容器
    main_msg = email.MIMEMultipart.MIMEMultipart()

    # 构造MIMEText对象做为邮件显示内容并附加到根容器
    message = message.decode('utf-8')
    text_msg = email.MIMEText.MIMEText(message, _subtype='plain', _charset='utf-8')
    main_msg.attach(text_msg)

    # 构造MIMEBase对象做为文件附件内容并附加到根容器
    contype = 'application/octet-stream'
    maintype, subtype = contype.split('/', 1)

    if attachmentPath != '':
        # 读入文件内容并格式化
        data = open(attachmentPath, 'rb')
        file_msg = email.MIMEBase.MIMEBase(maintype, subtype)
        file_msg.set_payload(data.read())
        data.close()
        email.Encoders.encode_base64(file_msg)

        # 设置附件头
        basename = os.path.basename(attachmentPath)
        file_msg.add_header('Content-Disposition', 'attachment', filename=basename)
        main_msg.attach(file_msg)

    # 设置根容器属性
    main_msg['From'] = sender
    main_msg['To'] = ";".join(addresseeList)
    main_msg['Subject'] = subject
    main_msg['Date'] = email.Utils.formatdate()

    # 得到格式化后的完整文本
    fullText = main_msg.as_string()

    # 用smtp发送邮件
    try:
        logging.info('start sending email')
        server.sendmail(sender, addresseeList, fullText)
        server.close()
        logging.info('Send email successfully!')
    except Exception, e:
        logging.error(e)
        logging.error('Send email failed!')


# 始终发送邮件
def keepSendMail(addresseeList, subject, message):
    while True:
        try:
            sendEmail(addresseeList, '', subject, message)
        except:
            continue
        else:
            break
