/***######***/
/***
环境相关
节点中的工作目录，与jenkins系统配置中的pod模板中的working directory一致
***/
workDirectory = "/home/jenkins"

/***
jdk version
***/
jdk_ver = "jdk1.8.0_66"

/***
基础镜像当中的第三方公共jar包目录
***/
thirdJarsDirInBaseImage = "/public-boss-jarfiles"

/***
构建服务器中的第三方公共jar包目录（遍历，获得列表）
***/
thirdJarsDirInBuildImage = "/home/source/stariboss/public-boss-jarfiles"

/***
git config
***/
gitUser = "jenkins"
gitPassword = "startimes"
staribossSourcecodeGitUrl = 10.0.250.70:8088/boss/stariboss.git
staribossPublicjarGitUrl = 10.0.250.70:8088/boss/publicjar.git
staribossYamlTemplateGitUrl = 10.0.250.70:8088/rd-platform/yaml-template.git
staribossChartsGitUrl = 10.0.250.70:8088/rd-platform/bossenv-config.git
staribossBillingGitUrl = 10.0.250.70:8088/boss/stariboss-billing.git


allList_boss = [\
    "account-billing",\
    "account-center-service", \
    "account-service", \
    "admin-billing-ui", \
    "admin-crm-ui", \
    "admin-public-ui", \
    "admin-product-ui", \
    "admin-oss-ui", \
    "agent-web", \
    "api-dealer-app", \
    "api-gateway-service", \
    "api-mboss-service", \
    "api-payment-service", \
    "area-service", \
    "boss-status-monitor", \
    "callcenter-proxy", \
    "card-center-service", \
    "card-service", \
    "channel-service", \
    "check-service", \
    "collection-center-service", \
    "collection-service", \
    "customer-center-service", \
    "customer-service", \
    "customer-ui", \
    "finance-ui", \
    "haiwai-proxy", \
    "iom-center-service", \
    "iom-service", \
    "job-service", \
    "knowledge-service", \
    "knowledge-ui", \
    "message-center-service", \
    "note-center-service", \
    "note-service", \
    "operator-ui", \
    "order-center-service", \
    "order-service", \
    "partner-service", \
    "partner-ui", \
    "platform-activiti", \
    "platform-cache-config", \
    "platform-config-server", \
    "pms-center-service", \
    "pms-frontend-conax-service", \
    "pms-frontend-ott-service",\
    "pms-frontend-pisys-service",\
    "pms-partition-service", \
    "portal-ui", \
    "problem-center-service", \
    "problem-service", \
    "product-service", \
    "resource-center-service", \
    "resource-service", \
    "resource-ui", \
    "request-graduate-service",\
    "system-query-service", \
    "system-service", \
    "worker-ui", \
]

// 生成产物为zip的模块名
zipList_boss = [\
    "platform-cache-config", \
    "platform-config-server", \
]

yaml_kind_list = [\
    "deployment",\
    "service",\
    "istiorule",\
    "hpa",\
]
// 不生成hpa yaml的模块列表
no_hpa_modulelist = [\
    "boss-status-monitor", \
    "platform-activiti", \
    "platform-cache-config", \
    "platform-config-server", \
    "pms-frontend-conax-service", \
    "pms-frontend-contego-service", \
]

// 前置机所有的子模块列表
pms_frontend_moduleList = [\
    "pms-frontend-conax-mg-service", \
    "pms-frontend-conax-s-service", \
    "pms-frontend-conax-at-service", \
    "pms-frontend-conax-ng-service", \
    "pms-frontend-conax-mz-service", \
    "pms-frontend-contego-bi-service", \
    "pms-frontend-contego-tz-service", \
    "pms-frontend-contego-za-service", \
    "pms-frontend-contego-ke-service", \
    "pms-frontend-contego-mb-service",\
]

return this;