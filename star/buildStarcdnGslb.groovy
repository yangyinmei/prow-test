import java.text.SimpleDateFormat

def jobName = env.JOB_NAME
def buildNum = env.BUILD_NUMBER
def ifTag = env.IFTAG.replace(" ", "")
def branch = env.BRANCH.replace(" ", "")
def version = env.VERSION.replace(" ", "")
def mailList = env.MAILTO.replace(" ", "")
def extParam = env.EXTPARAM.replace(" ", "")
def ifPublishImages = env.IFPUBLISHIMAGES.replace(" ", "")
def buildModuleList = extParam.tokenize(',')

def tag
def errModuleName = ""
def errMessageList = []
def buildStatus = "unset"
def tasks = [:]
def dockerUrl = []
def harborIp196 = ""
def harborUser196 = ""
def harborPassword196 = ""

def sourceCodeDir = "/home/source/${jobName}/sourceCode"
def pythonDir = "/home/source/pythonScript/${jobName}"
def uploadFtpDir = "/DataBk/build/${jobName}"
def sourceCodeGitUrl = "10.0.250.70:8088/starvideo-process/starcdn-gslb.git"

node('192.168.32.170') {
    try {
        stage('generate tag') {
            // 生成tag时需要的时间标签
            def nowdateFormat = new SimpleDateFormat("yyMMdd")
            def nowDateStr = nowdateFormat.format(new Date())
            tag = "${version}-${buildNum}-${nowDateStr}"
            println "tag : " + tag
        }
    } catch(err) {
        buildStatus = "FALSE"
        echo "${err}"
        errMessageList << "${err}!\n"
        errModuleName += "generate tag \n"
    }

    if(buildStatus.contentEquals("unset")) {
        try {
            stage('prepare environment') {
                checkout scm
                op = load ("./common/operation.groovy")
                props_comm = readProperties  file: './config/configComm.groovy'

                harborIp196 = props_comm.harborIp196
                harborUser196 = props_comm.harborUser196
                harborPassword196 = props_comm.harborPassword196
                // 更新python脚本（如果是新建的作业，没有此目录，需要创建）
                sh """
                    if [ ! -d ${pythonDir} ];then
                        mkdir ${pythonDir}
                    else
                        rm -rf  ${pythonDir}/* 
                    fi
                    cp -rf ./script/python/* ${pythonDir}/
                """
                // pdns模块在sourceCodeDir目录构建，先清空代码目录，再更新代码
                sh"""
                    rm -rf ${sourceCodeDir}
                """
                // 配置git信息
                sh """
                    echo "###Config git info###"
                    git config --global user.name 'jenkins'
                    git config --global user.email 'jenkins@startimes.com.cn'
                    git config --global http.postBuffer 524288000
                """
            }
        } catch(err) {
            buildStatus = "FALSE"
            echo "${err}"
            errMessageList << "${err}!\n"
            errModuleName += "prepare environment \n"
        }
    }

    if(buildStatus.contentEquals("unset")) {
        try {
            stage('update code') {
                // 构建时间
                def dateFormat = new SimpleDateFormat("yyyy-MM-dd-HH-mm")
                def getCodeDate = new Date()
                buildTime = dateFormat.format(getCodeDate)
                build job: 'UpdateSourceCode', parameters: [string(name: 'JOBNAME', value: jobName),
                                                            string(name: 'PROJECT', value: "sourceCode"),
                                                            string(name: 'BRANCH', value: branch),
                                                            string(name: 'GITURL', value: sourceCodeGitUrl)]
            }
        } catch(err) {
            buildStatus = "FALSE"
            echo "${err}"
            errMessageList << "${err}!\n更新代码失败，请检查您输入的分支是否正确！\n"
            errModuleName += "update code \n"
        }
    }

    // 环境要求：dos2unix + log4cpp + GeoIP + yaml-cpp
    // 构建pdns时，解决sudo: sorry, you must have a tty to run sudo
    // 编辑 /etc/sudoers：Defaults    requiretty，修改为 #Defaults    requiretty，表示不需要控制终端。
    buildModuleList.each { moduleName->
        // tasks[moduleName] = {
            node("192.168.32.170") {
                def codeDir = "${workspace}/${moduleName}"
                if(moduleName.contentEquals("pdns") || moduleName.contentEquals("http302")) {
                    codeDir = sourceCodeDir
                } else if(buildStatus.contentEquals("unset")) {
                    try {
                        stage('get code') {
                            op.getCode(sourceCodeDir, moduleName, branch)
                        }
                    } catch(err) {
                        buildStatus = "FALSE"
                        echo "${err}"
                        errMessageList << "${err}!\n"
                        errModuleName += moduleName + ": get code;"
                    }
                }
                if(buildStatus.contentEquals("unset")) {
                    try {
                        stage('build') {
                            dir(codeDir) {
                                if(moduleName.contentEquals("probe")) {
                                    sh"""
                                        cd probe
                                        export JAVA_HOME=/usr/java/jdk1.8.0_66
                                        export GRADLE_USER_HOME=/home/gradleCaches
                                        chmod 777 /usr/gradle-2.10/bin/gradle
                                        /usr/gradle-2.10/bin/gradle clean $moduleName
                                    """
                                }

                                if(moduleName.contentEquals("cdn-gslb-service")) {
                                    sh"""
                                        chmod 777 gradlew
                                        dos2unix gradlew
                                        export JAVA_HOME=/usr/java/jdk1.8.0_66
                                        ./gradlew clean $moduleName
                                    """
                                }
                                if(moduleName.contentEquals("pdns")) {
                                    sh"""
                                        rm -rf /usr/local/pdns/
                                        cd pdns
                                        chmod +x build.sh
                                        ./build.sh
                                    """
                                }
                                if(moduleName.contentEquals("fe")) {
                                    sh"""
                                        source /etc/profile
                                        cd fe
                                        chmod 777 build.sh
                                        ./build.sh
                                    """
                                }
                                if(moduleName.contentEquals("http302")) {
                                    sh"""
                                        cd http302
                                        chmod 777 build.sh
                                        ./build.sh
                                    """
                                }
                                if(moduleName.contentEquals("info-agg")) {
                                    sh"""
                                        cd InformationAggregation
                                        chmod 777 build.sh
                                        ./build.sh
                                    """
                                }
                            }//end dir
                        }//end stage:build
                    } catch(err) {
                        buildStatus = "FALSE"
                        echo "${err}"
                        errMessageList << "${err}！\n构建失败，请检查模块名是否正确，或者联系开发人员解决！\n"
                        errModuleName += ": build;"
                    }
                }
                if(buildStatus.contentEquals("unset")) {
                    try {
                        stage('build docker images') {
                            sh"""
                                docker login -u ${harborUser196} -p ${harborPassword196} ${harborIp196}
                            """
                            dir(codeDir) {
                                if(moduleName.contentEquals("cdn-gslb-service")) {
                                    sh"""
                                        find . -name "*.war" |xargs -i -exec cp {} ./docker_build/dockerfiles/
                                        unzip ./docker_build/dockerfiles/${moduleName}.war -d ./docker_build/dockerfiles/${moduleName}
                                    """
                                }
                                if(moduleName.contentEquals("pdns")) {
                                    sh"""
                                        cp ./pdns/run.sh ./docker_build/dockerfiles/
                                        cp -r ./pdns_rr_update ./docker_build/dockerfiles/rr_update
                                        mkdir -p ./docker_build/dockerfiles/pdns
                                        cp -r /usr/local/pdns/* ./docker_build/dockerfiles/pdns/
                                    """
                                }
                                if(moduleName.contentEquals("fe")) {
                                    sh"""
                                        cp -r ./fe/manage-portal/* ./docker_build/dockerfiles/
                                    """
                                }
                                if(moduleName.contentEquals("http302")) {
                                    sh"""
                                        cp ./http302/src/run.sh ./docker_build/dockerfiles/
                                        cp ./http302/src/http302 ./docker_build/dockerfiles/
                                        mkdir -p ./docker_build/dockerfiles/rr_update
                                        cp -r ./pdns_rr_update/* ./docker_build/dockerfiles/rr_update/
                                        mkdir -p ./docker_build/dockerfiles/openresty
                                        cp -r /usr/local/openresty/* ./docker_build/dockerfiles/openresty/
                                        mkdir -p ./docker_build/dockerfiles/pdns-recursor
                                        cp -r /usr/local/pdns-recursor/* ./docker_build/dockerfiles/pdns-recursor/
                                    """
                                }
                                if(moduleName.contentEquals("info-agg")) {
                                    sh """
                                        rm -rf ./docker_build/dockerfiles/tmp
                                        mkdir -p ./docker_build/dockerfiles/tmp
                                        cp ./InformationAggregation/src/run.sh ./docker_build/dockerfiles/tmp
                                        cp ./InformationAggregation/src/InformationAggregation ./docker_build/dockerfiles/tmp
                                        mkdir -p ./docker_build/dockerfiles/tmp/AggregationConfig
                                        cp -r ./InformationAggregation/src/python/AggregationConfig/* ./docker_build/dockerfiles/tmp/AggregationConfig/
                                        mkdir -p ./docker_build/dockerfiles/tmp/openresty
                                        cp -r /usr/local/openresty/* ./docker_build/dockerfiles/tmp/openresty/
                                    """
                                }
                                if(moduleName.contentEquals("probe")) {
                                    sh """
                                        find . -name "probe.zip" |xargs -i -exec cp {} ./docker_build/dockerfiles/
                                    """
                                }
                                if(!moduleName.contentEquals("probe")) {
                                    sh"""
                                        docker build -t ${harborIp196}/starcdn-gslb/${moduleName}:${tag} -f ./docker_build/dockerfiles/${moduleName}_Dockerfile ./docker_build/dockerfiles/
                                        docker push ${harborIp196}/starcdn-gslb/${moduleName}:${tag}
                                        docker rmi ${harborIp196}/starcdn-gslb/${moduleName}:${tag}
                                    """
                                    if(moduleName.contentEquals("fe")) {
                                        sh"""
                                           docker rmi ${harborIp196}/ottapplication/nginx:v1.10.2
                                        """
                                    }
                                    dockerUrl << '/starcdn-gslb/' + moduleName + ':' + tag
                                }
                            }//end dir
                        }//end stage : build docker images
                    } catch(err) {
                        buildStatus = "FALSE"
                        echo "${err}"
                        errMessageList << "${err}！\n构建镜像失败！\n"
                        errModuleName += ": build docker images;"
                    }
                }
                if(buildStatus.contentEquals("unset")) {
                    try {
                        stage('upload building product to ftp') {
                            dir(codeDir) {
                                sh"""
                                    echo "BUILDER  : $BUILDER" >> ./docker_build/dockerfiles/buildinfo.txt
                                """
                                if(ifTag.contentEquals("Y")) {
                                    sh"""
                                        echo "TAG      : $tag" >> ./docker_build/dockerfiles/buildinfo.txt
                                    """
                                }
                            }
                            op.uploadBuildingProductToFtp(codeDir, jobName, version, tag, pythonDir, uploadFtpDir, jobName, null)
                            
                        }
                    } catch(err) {
                        buildStatus = "FALSE"
                        echo "${err}"
                        errMessageList << "${err}!\n"
                        errModuleName += ": upload building product to ftp;"
                    }
                }
            }//end node
        // }//end task
    }//end buildModuleList.each
    //run task
    // if(buildStatus.contentEquals("unset")) {
    //     parallel tasks
    // }

    if(buildStatus.contentEquals("unset") && ifTag.contentEquals("Y")) {
        try {
            stage('tag code') {
                // tag local boss source code
                dir(sourceCodeDir) {
                    sh """
                        if [ `git tag --list ${tag}` ]
                        then
                            echo '${tag} exists.'
                        else
                            echo '${tag} not exist.'
                            git config --local remote.origin.url http://jenkins:startimes@${sourceCodeGitUrl}
                            git tag -a ${tag} -m 'BUILDER: '
                        fi
                    """
                }
                op.tagCode(tag, sourceCodeDir)
            }
        } catch(err) {
            buildStatus = "FALSE"
            echo "${err}"
            errMessageList << "${err}!\n"
            errModuleName += ": tag code;"
        }
    }
    // 发布镜像
    println "ifPublishImages ： " + ifPublishImages
    if(ifPublishImages && buildStatus.contentEquals("unset") && (ifPublishImages.toLowerCase()).contentEquals("TRUE".toLowerCase())) {
        try {
            stage('publish images') {
                build job: 'publishimages', parameters: [string(name: 'JOBNAME', value: jobName), string(name: 'TAG', value: tag), string(name: 'PYTHONDIR', value: pythonDir),string(name: 'MAILTO', value: mailList)], wait: false
            }
        } catch(err) {
            buildStatus = "FALSE"
            echo "${err}"
            errMessageList << "${err}!\n"
            errModuleName += "publish images"
        }
    }
    //构建信息记录到数据库
    stage('audit to database') {
        def Status
        if(buildStatus.contentEquals("unset")) {
            Status = "success"
        } else {
            Status = "failed"
        }
        build job: 'audit_database', parameters: [string(name: 'AuditType', value: "build"),
                                                  string(name: 'ProjectName', value: jobName),
                                                  string(name: 'Status', value: Status),
                                                  string(name: 'Branch', value: branch),
                                                  string(name: 'Version', value: version),
                                                  string(name: 'Tag', value: tag),
                                                  string(name: 'Environment', value:"192.168.32.173")
                                                 ]
    }
    stage('send email') {
        def dockerUrlStingParameter = []
        dockerUrl.each{ urlName->
            dockerUrlStingParameter << '<li>镜像地址ַ&nbsp;：10.0.251.196'+urlName+'</li>'
        }
        def onlineImageUrl = []
        if(ifPublishImages && (ifPublishImages.toLowerCase()).contentEquals("TRUE".toLowerCase())) {
            dockerUrl.each{ urlName->
                onlineImageUrl << '<li>镜像地址ַ&nbsp;：stagingreg.stariboss.com'+urlName+'</li>'
            }
        }
        def errMessage = errMessageList.join("<br>")
        println "失败模块及失败阶段：" + errModuleName
        println "失败原因：" + errMessage
        def bodyContent = """
            <li>新标签&nbsp;：${tag}</li>
            <li>构建时间&nbsp;：${buildTime}</li>
            <li>代码分支&nbsp;：${branch}</li>
            <li>构建编号&nbsp;：第${buildNum}次构建</li>
            <li>程序构建包&nbsp;：<a href="ftp://10.0.250.250/${jobName}/${version}/${tag}/">ftp://10.0.250.250/${jobName}/${version}/${tag}/</a></li>
            ${dockerUrlStingParameter}
            <li>云上镜像地址：</li>
            ${onlineImageUrl}
            <li>构建日志&nbsp;：&nbsp;<a href="${BUILD_URL}console">${BUILD_URL}console</a></li>
            <li>失败模块及失败阶段&nbsp;：${errModuleName}</li>
            <li>失败原因及错误日志&nbsp;：${errMessage}</li>
        """

        if(buildStatus.contentEquals("unset")) {
            mailSubjectStr = "Success! BuildJob: ${jobName} tag:${tag} 第${buildNum}次构建！"
        }else {
            mailSubjectStr = "Failed! BuildJob: ${jobName} tag:${tag} 第${buildNum}次构建！"
        }
        op.sendMail(jobName, buildStatus, mailList, bodyContent, mailSubjectStr)
    }

    //构建失败
    if(!(buildStatus.contentEquals("unset"))) {
        stage('build faild') {
            echo "Error stage: ${errModuleName}"
            error 'build faild'
        }
    }
}