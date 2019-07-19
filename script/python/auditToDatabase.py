#!/usr/bin/python
# -*- coding: UTF-8 -*-
import sys
import MySQLdb
import config.config as CONFIG


def auditToDatabase(AuditType, ProjectName, Status, Branch, Version, Tag, Environment):
    # 打开数据库连接
    db = MySQLdb.connect(CONFIG.DATABASE_IP, CONFIG.DATABASE_USER, CONFIG.DATABASE_PASSWORD, CONFIG.DATABASE, charset='utf8')

    # 使用cursor()方法获取操作游标
    cursor = db.cursor()

    # SQL 插入语句
    if AuditType == "build":
        sql = "INSERT INTO build_info(ProjectName, BuildStatus, Branch, Version, Tag, Environment) \
        VALUES ('%s', '%s', '%s', '%s', '%s', '%s')" % (ProjectName, Status, Branch, Version, Tag, Environment)
    elif AuditType == "deploy":
        sql = "INSERT INTO deploy_info(ProjectName, DeployStatus, Environment, Version) \
        VALUES ('%s', '%s', '%s', '%s')" % (ProjectName, Status, Environment, Version)
    elif AuditType == "test":
        sql = "INSERT INTO test_info(ProjectName, TestStatus, Environment, Version) \
        VALUES ('%s', '%s', '%s', '%s')" % (ProjectName, Status, Environment, Version)

    try:
        # 执行sql语句
        cursor.execute(sql)
        # 提交到数据库执行
        db.commit()
    except:
        # 发生错误时回滚
        db.rollback()

    # 关闭数据库连接
    db.close()


def run():
    # 传入参数
    AuditType = sys.argv[1]
    ProjectName = sys.argv[2]
    Status = sys.argv[3]
    Branch = sys.argv[4]
    Version = sys.argv[5]
    Tag = sys.argv[6]
    Environment = sys.argv[7]
    auditToDatabase(AuditType, ProjectName, Status, Branch, Version, Tag, Environment)


if __name__ == "__main__":
    run()
