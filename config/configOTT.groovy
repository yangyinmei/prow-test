/***######***/
/***
环境相关
节点中的工作目录，与jenkins系统配置中的pod模板中的working directory一致
***/
//因为ott作业是在slave节点本地构建，所以工作空间目录与boss不同
workDirectory = "/home/jenkins"
/***
jdk version
***/
jdk_ver = "jdk1.8.0_66"

/***
git config
***/
yamlTemplateGitUrl = 10.0.250.70:8088/rd-platform/yaml-template.git
yamlGitUrl = 10.0.250.70:8088/rd-platform/yaml.git

ottapplicationSourcecodeGitUrl = 10.0.250.70:8088/starott/ottapplication.git
ottplatformSourcecodeGitUrl = 10.0.250.70:8088/starott/ottplatform.git
starosSourcecodeGitUrl = 10.0.250.70:8088/starvideo-process/staros.git
aqosSourceCodeGitUrl = 10.0.250.70:8088/AQoS/AQoS.git
starfsaappiosSourceCodeGitUrl = 10.0.250.70:8088/starfsa/starfsa-app-ios.git
starcdncacheSourceCodeGitUrl = 10.0.250.70:8088/starvideo-process/starcdn.git
starcdngslbSourceCodeGitUrl = 10.0.250.70:8088/starvideo-process/starcdn-gslb.git

yaml_kind_list = [\
    "deployment",\
    "service",\
    "istiorule",\
    "hpa",\
]

// ott这些模块需要生成多个模块，部署到不同的环境
ottplatform_multiple_yaml_modulelist = [\
    "commodity-service",\
    "information-service",\
    "lems-service",\
    "tacs-service",\
    "vams-service",\
    "vcms-service"\
]

ottplatform_multiple_kind = [\
    "",\
    "-b",\
    "-c",\
]

staros_yaml_modulelist = [\
    "os-bc",\
    "os-manage",\
    "os-processor",\
]

aws_product_modulelist = [\
    "aaa-service",\
    "auth-service",\
    "file-os-service",\
    "mcms-service",\
    "search-service",\
    "vams-service",\
    "vbms-service",\
    "vcms-service",\
    "program-proxy-service",\
    "imdb-proxy-service",\
    "media-analysis-service",\
]

zip_moduleList= [\
    "platform-config",\
    "vbsc-service",\
    "search-config",\
    "imdb-proxy-config",\
]

return this;