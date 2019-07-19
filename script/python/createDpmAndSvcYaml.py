# -*- coding: utf-8 -*-
'''
Created on 2017年9月25日

@author: Simba
'''
import sys
import config.config as CONFIG
import os


def createDpmAndSvcYaml(sourceFilePath, desFilePath, tag, version, moduleName, tempName=''):
    if moduleName == "platform-activiti":
        moduleName = "activiti-rest"
    yamlModuleName = ''
    if tempName != '':
        yamlModuleName = tempName.replace('-','_')
    else:
        yamlModuleName = moduleName.replace('-','_')
    dataDic = {
        "modulename": moduleName,
        "version": version,
        "tag": tag,
        "percent": "%",
        "spec.template.spec.containers.image.repository": "{{ image_repository }}",
        "namespace": "{{ namespace }}",
        "mysql_ip": "{{ mysql_ip }}",
        "mysql_port": "{{ mysql_port }}",
        "mysql_user": "{{ mysql_user }}",
        "mysql_password": "{{ mysql_password }}",
        "redis_port": "{{ redis_port }}",
        "redis_ip": "{{ redis_ip }}",
        "db_index": "{{ db_index }}",
        "master_fdfs_ip": "{{ master_fdfs_ip }}",
        "kubernetes.fdfspod.ip": "{{ kubernetes.fdfspod.ip }}",
        "master_processor_ip": "{{ master_processor_ip }}",
        "registryIp": "{{ registryIp }}",
        "imagepullpolicy": "{{ imagepullpolicy }}",
        "zoneinfo": "{{ zoneinfo }}",
        "timezone": "{{ timezone }}",
        "alikey": "{{ alikey }}",
        "alikeysecret": "{{ alikeysecret }}",
        "nfs.ip": "{{ nfsip.stdout }}",
        "fdfs.racker.server": "{{ fastdfsip.stdout }}",
        "fdfs_domainName": "{{ masterip }}",
        "film_access_host": "{{ masterip }}",
        "dubbo.host.ip": "{{ dubbo.host.ip }}",
        "rabbit_connect_addresses": "{{ rabbit_connect_addresses }}",
        "imagePullPolicy": "{{ imagePullPolicy }}",
        "java_opts_size": "{{ %s_java_opts_size|default(java_opts_size) }}" % yamlModuleName,
        "scale_replicas": "{{ %s_scale_replicas|default(scale_replicas) }}" % yamlModuleName,
        "scale_limits_cpu": "{{ %s_scale_limits_cpu|default(scale_limits_cpu) }}" % yamlModuleName,
        "scale_limits_memory": "{{ %s_scale_limits_memory|default(scale_limits_memory) }}" % yamlModuleName,
        "scale_requests_cpu": "{{ %s_scale_requests_cpu|default(scale_requests_cpu) }}" % yamlModuleName,
        "scale_requests_memory": "{{ %s_scale_requests_memory|default(scale_requests_memory) }}" % yamlModuleName,
        "scale_minreplicas": "{{ %s_scale_minreplicas|default(scale_minreplicas) }}" % yamlModuleName,
        "scale_service_maxreplicas": "{{ %s_scale_service_maxreplica|default(scale_service_maxreplicas) }}" % yamlModuleName,
        "scale_cpu_threshold": "{{ %s_scale_cpu_threshold|default(scale_cpu_threshold) }}" % yamlModuleName,
        "scale_memory_threshold": "{{ %s_scale_memory_threshold|default(scale_memory_threshold) }}" % yamlModuleName,
    }
    if tempName != '':
        dataDic['pms-name'] = tempName
        if CONFIG.PMS_CONAX_YAML_DICT.get(tempName) is not None:
            dataDic['pms-key'] = CONFIG.PMS_CONAX_YAML_DICT.get(tempName)
        elif CONFIG.PMS_CONTEGO_YAML_DICT.get(tempName) is not None:
            dataDic['pms-key'] = CONFIG.PMS_CONTEGO_YAML_DICT.get(tempName)
    else:
        dataDic['pms-name'] = moduleName
        if CONFIG.PMS_FRONTED_YAML_DICT.get(moduleName) is not None:
            dataDic['pms-key'] = CONFIG.PMS_FRONTED_YAML_DICT.get(moduleName)

    if moduleName in CONFIG.ottplatform_multiple_yaml_modulelist_b:
        dataDic['log'] = "logb"
        dataDic['envconfig'] = "env-config-b"
    elif moduleName in CONFIG.ottplatform_multiple_yaml_modulelist_c:
        dataDic['log'] = "logc"
        dataDic['envconfig'] = "env-config-c"
    else:
        dataDic['log'] = "log"
        dataDic['envconfig'] = "env-config-a"

    fread = open(sourceFilePath, 'r')
    fStr = fread.read()
    fread.close()

    targetStr = fStr % dataDic

    fwrite = open(desFilePath, 'w')
    fwrite.write(targetStr)
    fwrite.close()


def getSrcFilePath(yamlTemplateDir, moduleName):
    srcFilePath = yamlTemplateDir
    for parent, dirnames, filenames in os.walk(yamlTemplateDir):
        for filename in filenames:
            if filename == moduleName + ".yaml":
                srcFilePath = os.path.join(parent, filename)
    return srcFilePath


def createYaml(yamlTemplateDir, yamlDir, moduleName, srcFile, desFile, tag, version, isChanged, yamlKind, tempName=''):
    srcFilePath = os.path.join(yamlTemplateDir, srcFile)
    desFilePath = os.path.join(yamlDir, version, tag, isChanged, moduleName, yamlKind, desFile)
    createDpmAndSvcYaml(srcFilePath, desFilePath, tag, version, moduleName, tempName)


def run():
    '''
    :param 脚本本身（非传入参数）
    :param codeDir:   代码目录
    :param moduleName: 模块名
    :param tag: 标签
    :param version: 版本
    :param isChanged: 是否变更
    :param yamlTemplateDir: yaml模板路径
    :param yamlDir: 生成的yaml文件的根目录
    '''

    moduleName = sys.argv[1]
    tag = sys.argv[2]
    version = sys.argv[3]
    isChanged = sys.argv[4]
    yamlTemplateDir = sys.argv[5]
    yamlDir = sys.argv[6]

    # 利用模板生成对应的yaml文件
    if moduleName == "account-billing":
        createYaml(yamlTemplateDir, yamlDir, moduleName, moduleName + ".yaml", moduleName + ".yaml", tag, version, isChanged, "job")
    elif moduleName == "pms-frontend-conax-service":
        for yamlKind in CONFIG.YAML_KIND_LIST:
            for tempName in CONFIG.PMS_CONAX_YAML_DICT:
                createYaml(yamlTemplateDir, yamlDir, moduleName, moduleName +"-" + yamlKind + ".yaml", tempName + "-" + yamlKind + ".yaml", tag, version, isChanged, yamlKind, tempName)
    elif moduleName == "pms-frontend-contego-service":
        for yamlKind in CONFIG.YAML_KIND_LIST:
            for tempName in CONFIG.PMS_CONTEGO_YAML_DICT:
                createYaml(yamlTemplateDir, yamlDir, moduleName, "pms-frontend-conax-service-" + yamlKind + ".yaml", tempName + "-" + yamlKind + ".yaml", tag, version, isChanged, yamlKind, tempName)
    elif CONFIG.SPECIAL_DEPLOYMENT_DICT.get(moduleName) is not None:
        i = 0
        for yamlKind in CONFIG.YAML_KIND_LIST:
            createYaml(yamlTemplateDir, yamlDir, moduleName, CONFIG.SPECIAL_DEPLOYMENT_DICT.get(moduleName)[i] + ".yaml", moduleName + "-" + yamlKind + ".yaml", tag, version, isChanged, yamlKind)
            i = i + 1
    else:
        for yamlKind in CONFIG.YAML_KIND_LIST:
            createYaml(yamlTemplateDir, yamlDir, moduleName, yamlKind + ".yaml", moduleName + "-" + yamlKind + ".yaml", tag, version, isChanged, yamlKind)


if __name__ == "__main__":
    run()
