import java.text.SimpleDateFormat

def jobName = env.JOB_NAME
def buildNum = env.BUILD_NUMBER
def builder = env.BUILDER
def ifTag = env.IFTAG.replace(" ", "")
def branch = env.BRANCH.replace(" ", "")
def version = env.VERSION.replace(" ", "")
def extParam = env.EXTPARAM.replace(" ", "")
def mailList = env.MAILTO.replace(" ", "")
def buildModuleList = extParam.tokenize(',')

def tag
def errModuleName = ""
def errMessageList = []
def buildStatus = "unset"
def nowDateStr
def tasks = [:]
def harborIp196 = ""
def harborUser196 = ""
def harborPassword196 = ""

def sourceCodeDir = "/home/source/${jobName}/sourceCode"
def pythonDir = "/home/source/pythonScript/${jobName}"
def uploadFtpDir = "/DataBk/build/${jobName}"
def sourceCodeGitUrl = "10.0.250.70:8088/starott/starott.git"

// 环境要求：/usr/gradle-2.10 + docker + sonar
node("base-slave") {
    try {
        stage('generate tag') {
            // 生成tag时需要的时间标签
            def nowdateFormat = new SimpleDateFormat("yyMMdd")
            nowDateStr = nowdateFormat.format(new Date())
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
    // 每个extParam参数，不可以并行构建
    buildModuleList.each { moduleName->
        def codeDir = "${workspace}/${moduleName}"
        if(buildStatus.contentEquals("unset")) {
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
                        sh"""
                            export LANG=en_US.UTF-8
                            /usr/gradle-2.10/bin/gradle clean
                            /usr/gradle-2.10/bin/gradle ${moduleName}

                            chmod 777 ./docker_build/build.sh
                            ./docker_build/build.sh ${harborIp196}/starott ${tag} ${codeDir}
                        """
                    }
                }
            } catch(err) {
                buildStatus = "FALSE"
                echo "${err}"
                errMessageList << "${err}！\n"
                errModuleName += ": build;"
            }
        }
        if(buildStatus.contentEquals("unset")) {
            try {
                stage('upload building product to ftp') {
                    sh"""
                        echo "BUILDER  : ${builder}" > ${codeDir}/build/publish/buildinfo.txt
                        mv ${codeDir}/docker_build/*.txt ${codeDir}/build/publish/
                    """
                    if(ifTag.contentEquals("Y")) {
                        sh"""
                            echo "SRCTAG : ${tag}" >> ${codeDir}/build/publish/buildinfo.txt
                        """
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
    }//end buildModuleList.each

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
    if(buildStatus.contentEquals("unset")) {
        node("192.168.32.170") {
            stage('SonarQube analysis') {
                dir(sourceCodeDir) {
                    sh"""
                        if [ ! -d  sonar-project.properties ]; then
                            echo "sonar.projectKey=${jobName}" > ./sonar-project.properties
                            echo "sonar.projectName=${jobName}" >> ./sonar-project.properties
                            echo "sonar.projectVersion=${version}" >> ./sonar-project.properties
                            echo "sonar.sources=." >> ./sonar-project.properties
                            echo "sonar.java.source=1.8" >> ./sonar-project.properties
                            echo "sonar.java.target=1.8" >> ./sonar-project.properties
                            echo "sonar.sourceEncoding=UTF-8" >> ./sonar-project.properties
                            echo "sonar.exclusions=**/common.properties,**/module.properties,**/*.properties,**/*.xml,**/*.js,**/*.jsp,**/*.sql,**/*.css" >> ./sonar-project.properties
                        fi
                        source /etc/profile
                        sonar-scanner
                    """
                }
            }
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
        def errMessage = errMessageList.join("<br>")
        println "失败模块及失败阶段：" + errModuleName
        println "失败原因：" + errMessage
        def bodyContent = """
            <li>新标签&nbsp;：${tag}</li>
            <li>构建时间&nbsp;：${buildTime}</li>
            <li>代码分支&nbsp;：${branch}</li>
            <li>构建编号&nbsp;：第${buildNum}次构建</li>
            <li>程序构建包&nbsp;：<a href="ftp://10.0.250.250/${jobName}/${version}/${tag}/">ftp://10.0.250.250/${jobName}/${version}/${tag}/</a></li>
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