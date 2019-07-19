# -*- coding: utf-8 -*-
'''
Created on 2017年8月18日

@author: vivis
'''
import os

'''
脚本存放根目录
'''
ROOT_HOME = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))

'''
脚本执行日志存放根目录
'''
LOG_HOME = "/home/source/logfile/"

'''
日志是否打印线程号和进程号
0:不打印
1:只打印线程号
2:只打印进程号
3：打印线程号，并且打印进程号
'''
LOG_THREAD_PROCESS = 0

# 镜像地址
IMAGE_HOST = '10.0.251.196'
# FTP地址
FTP_HOST = '10.0.250.250'
# FTP用户名、密码
FTP_USER_PWD = ('buildftp', 'buildftp')

# spark-jobserver镜像构建所需jar包列表
JAR_FORJOBSERVER_RELATIVEPATH_DIC = {
    "stariboss-platform-common.jar": "platform/platform-common/build/libs/stariboss-platform-common.jar",
    "stariboss-platform-cache-data.jar": "platform/platform-cache-data/build/libs/stariboss-platform-cache-data.jar",
    "stariboss-platform-redis.jar": "platform/platform-redis/build/libs/stariboss-platform-redis.jar",
    "stariboss-platform-zookeeper.jar": "platform/platform-zookeeper/build/libs/stariboss-platform-zookeeper.jar",
    "stariboss-platform-message.jar": "platform/platform-message/build/libs/stariboss-platform-message.jar",
    "stariboss-message-center-api.jar": "service/message-center/message-center-api/build/libs/stariboss-message-center-api.jar",
    "stariboss-system-api.jar": "service/system/system-api/build/libs/stariboss-system-api.jar",
    "stariboss-order-job.jar": "service/order/order-job/build/libs/stariboss-order-job.jar",
    "stariboss-billing.jar": "billing/build/libs/stariboss-billing.jar"}

# 包名与模块名不同的war包字典，模块名与包名的map
# EXTRA_WAR_DIC = {
#     "starDA-web": "starDA.war",
#     "api-gateway-service": "api.war",
#     "haiwai-proxy": "stariboss-haiwai_proxy.war",
#     "callcenter-proxy": "stariboss-callcenter_proxy.war"}

# zip包列表
MV_ZIP_LIST = [
    "platform-cache-config",
    "platform-config-server",
]

# 镜像相关的信息
DOCKER_INFO = {"registryaddress": "10.0.251.196",
               "registoryusername": "admin",
               "registorypassword": "asdf1234",
               "bossimagerdir": "boss",
               "billingimagerdir": "billing",
               "platformimagerdir": "platform",
               "thirdjarsLocalDir": "/home/nfs/stariboss/public-boss-jarfiles",
               "thirdjarsDirInBuildImage": "/home/source/stariboss/public-boss-jarfiles",
               "thirdjarsDirInWarImage": "/public-boss-jarfiles"
               }

# 记录构建状态的数据库信息
DATABASE_IP = '10.0.250.185'
DATABASE_USER = 'jenkins'
DATABASE_PASSWORD = '123456'
DATABASE = 'jenkins'

# istio部署service特殊的几个模块，这几个模块的service或deployment模板与其他的不一样，包括stariboss和ott
SPECIAL_DEPLOYMENT_DICT = {
    "boss-status-monitor": ["boss-status-monitor-deployment", "service", "istiorule", "hpa"],
    "haiwai-proxy": ["haiwai-proxy-deployment", "haiwai-proxy-service", "istiorule", "hpa"],
    "job-service": ["job-service-deployment", "service", "istiorule", "hpa"],
    "platform-activiti": ["platform-activiti-deployment", "service", "istiorule", "hpa"],
    "platform-cache-config": ["platform-cache-config-deployment", "platform-cache-config-service", "istiorule", "hpa"],
    "platform-config-server": ["platform-config-server-deployment", "platform-config-server-service", "istiorule", "hpa"],
    "pms-frontend-ott-service": ["pms-frontend-service-deployment", "service", "istiorule", "hpa"],
    "pms-frontend-pisys-service": ["pms-frontend-service-deployment", "service", "istiorule", "hpa"],
    "nginx-manageportal": ["nginx-manageportal-deployment", "nginx-manageportal-service", "istiorule", "hpa"],
    "nginx-stb-manageportal": ["nginx-stb-manageportal-deployment", "nginx-stb-manageportal-service", "istiorule", "hpa"],
    "os-bc": ["os-bc-deployment", "os-bc-service", "istiorule", "hpa"],
    "os-manage": ["os-manage-deployment", "os-manage-service", "istiorule", "hpa"],
    "os-processor": ["os-processor-deployment", "os-processor-service", "istiorule", "hpa"],
}

PMS_CONAX_YAML_DICT = {
    "pms-frontend-conax-mg-service": "ca.conax_mg_equipmentid",
    "pms-frontend-conax-s-service": "ca.conax_s_equipmentid",
    "pms-frontend-conax-at-service": "ca.conax_at_equipmentid",
    "pms-frontend-conax-ng-service": "ca.conax_ng_equipmentid",
    "pms-frontend-conax-mz-service": "ca.conax_mz_equipmentid",
}
PMS_CONTEGO_YAML_DICT = {
    "pms-frontend-contego-bi-service": "ca.contego_bi_equipmentid",
    "pms-frontend-contego-tz-service": "ca.contego_tz_equipmentid",
    "pms-frontend-contego-za-service": "ca.contego_za_equipmentid",
    "pms-frontend-contego-ke-service": "ca.contego_ke_equipmentid",
    "pms-frontend-contego-mb-service": "ca.contego_mb_equipmentid",
}
PMS_FRONTED_YAML_DICT = {
    "pms-frontend-ott-service": "ca.ott_equipmentid",
    "pms-frontend-pisys-service": "ca.pisys_equipmentid",
}

YAML_KIND_LIST = [
    "deployment",
    "service",
    "istiorule",
    "hpa",
]

ottplatform_multiple_yaml_modulelist_b = [
    "commodity-service-b",
    "information-service-b",
    "lems-service-b",
    "tacs-service-b",
    "vams-service-b",
    "vcms-service-b"
]
ottplatform_multiple_yaml_modulelist_c = [
    "commodity-service-c",
    "information-service-c",
    "lems-service-c",
    "tacs-service-c",
    "vams-service-c",
    "vcms-service-c"
]

# 构建产物路径
OUTPUTDIR_DIC = {'AQoS': 'publish/dist',
                 'starapp-android': 'startvhelper/build/outputs/apk',
                 'starfsaapp-ios': 'StarfsaAppHTML/dist/',
                 'StarProXiaoYi': 'app/build/outputs/apk/',
                 'StarProLauncher': 'app/build/outputs/apk/',
                 'StarProSetting': 'app/build/outputs/apk/',
                 'OriginalStaribossAPP': '',
                 'OriginalStaribossSIM': '',
                 'OriginalStaribossDB': '',
                 'StarECMSAPP': '',
                 'StarECMSDB': '',
                 'starcdn-gslb': 'docker_build/dockerfiles',
                 'staret': 'ETFE/manage-portal/dist',
                 'staros': 'publish',
                 'starcdn-cache': 'streamer/publish',
                 'starott-gradle': 'build/publish',
                 'starott-stat-sadb': '',
                 'starvideo-process-starcdn': 'outputs',
                 'starapp-win8': 'starbox_win8ui/build/outputs/apk',
                 'ip-dispatch': 'ip-dispatch/build/libs',
                 'starDA-app': 'app/build/outputs/apk',
                 'starDA-web': 'build/libs',
                 'starda_newboss': 'app/build/outputs/apk',
                 'starEA-App': 'app/build/outputs/apk',
                 'starfsa-app': 'app/build/outputs/apk',
                 'starfsa-middle-layer': 'starFSA_appIntf/build/libs',
                 'WEBSELFAPP': '',
                 'WEBSELFSIM': '',
                 'WEBSELFDB': '',
                 'starbi': 'publish',
                 'stardss-service': 'target',
                 'stardss-app': 'app/build/outputs/apk',
                 'ERPAPP': '',
                 'ERPDB': '',
                 'starmboss_app': 'app/build/outputs/apk',
                 'hotel_huangshi': 'starbox_win8ui/build/outputs/apk',
                 'starda_combine': 'app/build/outputs/apk/release',
                 'starBA': 'app/build/outputs/apk/release',
                 'starBA-Autobuild': 'app/build/outputs/apk/release',
                 }

STARIBOSS_JAVA_OPTS_SIZE_DICT = {
    "account-service": "Xmx1G",
    "customer-center-service": "Xmx1G",
    "customer-service": "Xmx1G",
    "job-service": "Xmx1G",
    "order-service": "Xmx1G",
    "partner-service": "Xmx1G",
    "platform-cache-config": "Xmx2G",
    "platform-config-server": "Xmx1G",
    "pms-center-service": "Xmx1G",
    "pms-frontend-conax-service": "Xmx1G",
    "pms-frontend-contego-service": "Xmx1G",
    "pms-frontend-ott-service": "Xmx1G",
    "pms-frontend-pisys-service": "Xmx1G",
    "pms-partition-service": "Xmx1G",
    "product-service": "Xmx1G",
    "resource-service": "Xmx1G",
}

STARIBOSS_SERVICE_MODULELIST = [
    "account-center-service",
    "account-service",
    "agent-web",
    "api-dealer-app",
    "api-gateway-service",
    "api-mboss-service",
    "api-payment-service",
    "area-service",
    "boss-status-monitor",
    "callcenter-proxy",
    "card-center-service",
    "card-service",
    "channel-service",
    "check-service",
    "collection-center-service",
    "collection-service",
    "customer-center-service",
    "customer-service",
    "haiwai-proxy",
    "iom-center-service",
    "iom-service",
    "job-service",
    "knowledge-service",
    "message-center-service",
    "note-center-service",
    "note-service",
    "order-center-service",
    "order-service",
    "partner-service",
    "platform-activiti",
    "pms-center-service",
    "pms-frontend-conax-service",
    "pms-frontend-ott-service",
    "pms-frontend-pisys-service",
    "pms-partition-service",
    "problem-center-service",
    "problem-service",
    "product-service",
    "resource-center-service",
    "resource-service",
    "request-graduate-service",
    "system-query-service",
    "system-service",
]

STARIBOSS_UI_MODULELIST = [
    "admin-billing-ui",
    "admin-crm-ui",
    "admin-public-ui",
    "admin-product-ui",
    "admin-oss-ui",
    "customer-ui",
    "finance-ui",
    "knowledge-ui",
    "operator-ui",
    "partner-ui",
    "portal-ui",
    "resource-ui",
    "worker-ui",
]
