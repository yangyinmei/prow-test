import java.text.SimpleDateFormat

def branch = env.BRANCH
def version = env.VERSION
def jobName = env.JOB_NAME
def buildnum = env.BUILD_NUMBER
def frontlist = env.FRONTLIST
def backlist = env.BACKLIST
def mailList = env.MAILTO

def mailSubjectStr
def tag
def buildStatus = "unset"
def buildTime
def errModuleName
def errMessageList = []
def frontBuildModuleList
def backBuildModuleList
def buildArguments=""
def needPlatformAsr="false"
def tasks = [:]
def dockerUrl = []

def ltpSourceCodeDir = "/home/source/starltp/sourceCode"
def ltpGitUrl = "10.0.250.70:8088/starott/starltp.git"

node('192.168.32.170') {
    try {
        stage('generate tag') {
            checkout scm
            op = load ("./common/operation.groovy")
            props_comm = readProperties  file: './config/configComm.groovy'

            def nowdateFormat = new SimpleDateFormat("yyMMdd")
            def nowDateStr = nowdateFormat.format(new Date())
            tag = "${version}-${buildnum}-${nowDateStr}"
            println "tag"+tag
        }
    } catch(err) {
        buildStatus = "FALSE"
        echo "${err}"
        errMessageList << "${err}!\n"
        errModuleName += "generate tag"
    }

    if(buildStatus.contentEquals("unset")) {
        try {
            stage('update code') {
                def dateFormat = new SimpleDateFormat("yyyy-MM-dd-HH-mm")
                def getCodeDate = new Date()
                buildTime = dateFormat.format(getCodeDate)
                build job: 'UpdateSourceCode', parameters: [string(name: 'JOBNAME', value: jobName),
                                                            string(name: 'PROJECT', value: "sourceCode"),
                                                            string(name: 'BRANCH', value: branch),
                                                            string(name: 'GITURL', value: "${ltpGitUrl}")]
            }
        } catch(err) {
            buildStatus = "FALSE"
            echo "${err}"
            errMessageList << "${err}!\n更新代码失败，请检查您输入的分支是否正确！\n"
            errModuleName += "update code"
        }
    }

    if(buildStatus.contentEquals("unset")) {
        try {
            stage('get buildModuleList') {
                if(frontlist) {
                    frontBuildModuleList = frontlist.tokenize(',')
                }
                if(backlist) {
                    backBuildModuleList = backlist.tokenize(',')
                    backBuildModuleList.each { moduleName->
                        buildArguments = buildArguments + " :service:" + moduleName + ":" + moduleName + "-service:war"
                        if(moduleName.contentEquals("scriptsTranslation")) {
                            needPlatformAsr="true"
                        }
                    }
                    buildArguments = buildArguments + " :platform:platform-config:distZip"
                    if(needPlatformAsr.contentEquals("true")) {
                        buildArguments = buildArguments + " :platform:platform-asr:distZip"
                    }
                }
                println "frontBuildModuleList:"+frontBuildModuleList
                println "buildArguments"+buildArguments
            }
        } catch(err) {
            buildStatus = "FALSE"
            echo "${err}"
            errMessageList << "${err}!\n"
            errModuleName += "get buildModuleList"
        }
    }
    
    if(buildStatus.contentEquals("unset")) {
        if(!frontBuildModuleList && !buildArguments) {
            echo "There has been no changes in any module since last successful building."
        } else {
            try {
                stage('get code'){
                    // 清除上次构建
                    sh"""
                        rm -rf ltpCodeDir
                        mkdir -p ltpCodeDir
                        cp -rf ${ltpSourceCodeDir}/* ltpCodeDir/
                    """
                }
            } catch(err) {
                buildStatus = "FALSE"
                echo "${err}"
                errMessageList << "${err}!\n"
                errModuleName += "get ccode"
            }
            //前端门户
            if(frontlist) {
                frontBuildModuleList.each{ moduleName->
                    tasks[moduleName] = {
                        try {
                            stage('build front') {
                                dir("./ltpCodeDir/fe"){
                                    if(moduleName.contentEquals("service")) {
                                        sh """
                                            chmod +x build-service.sh
                                            ./build-service.sh
                                        """
                                    } else if(moduleName.contentEquals("portal")){
                                        sh """
                                            chmod +x build-portal.sh
                                            ./build-portal.sh
                                        """
                                    } else if(moduleName.contentEquals("translator-portal")){
                                        sh """
                                            chmod +x build-translator-portal.sh
                                            ./build-translator-portal.sh
                                        """
                                    } else if(moduleName.contentEquals("mobile-portal")){
                                        sh """
                                            chmod +x build-mobile-portal.sh
                                            ./build-mobile-portal.sh
                                        """
                                    }
                                }
                            }
                        } catch(err) {
                            buildStatus = "FALSE"
                            echo "${err}"
                            errMessageList << "${err}!\n build front failed!"
                            errModuleName += moduleName
                        }

                        try {
                            stage('build front images') {
                                if(moduleName.contentEquals("translator-portal")) {
                                    moduleName = "translatorportal"
                                }
                                sh """
                                    docker login -u admin -p asdf1234 10.0.251.196
                                    docker build -t 10.0.251.196/starott-ltp/${moduleName}:${tag} -f ./ltpCodeDir/docker_build/dockerfiles/${moduleName}.Dockerfile .
                                    docker push 10.0.251.196/starott-ltp/${moduleName}:${tag}
                                    docker rmi 10.0.251.196/starott-ltp/${moduleName}:${tag}
                                """
                                dockerUrl << '10.0.251.196/starott-ltp/'+moduleName+':'+tag
                            }
                        }catch(err) {
                            buildStatus = "FALSE"
                            echo "${err}"
                            errMessageList << "${err}!\n build fromt images failed"
                            errModuleName += moduleName
                        }
                    }
                }  
            }

            //后台微服务
            if(backlist) {
                try {
                    stage('build back') {
                        sh """
                            cd ltpCodeDir
                            export JAVA_HOME=/usr/java/jdk1.8.0_66
                            chmod 777 gradlew
                            ./gradlew clean
                            ./gradlew ${buildArguments}
                        """
                    }
                } catch(err) {
                    buildStatus = "FALSE"
                    echo "${err}"
                    errMessageList << "${err}!\n build back failed"
                    errModuleName += "build back"
                }

                backBuildModuleList.each { moduleName->
                    tasks[moduleName] = {
                        try{
                            stage('build back images') {
                                def imageModuleName = moduleName.toLowerCase()
                                sh """
                                    docker login -u admin -p asdf1234 10.0.251.196
                                    docker build -t 10.0.251.196/starott-ltp/${imageModuleName}:${tag} -f ./ltpCodeDir/docker_build/dockerfiles/${imageModuleName}.Dockerfile .
                                    docker push 10.0.251.196/starott-ltp/${imageModuleName}:${tag}
                                    docker rmi 10.0.251.196/starott-ltp/${imageModuleName}:${tag}
                                """
                                dockerUrl << '10.0.251.196/starott-ltp/'+imageModuleName+':'+tag
                            }
                        } catch(err) {
                            buildStatus = "FALSE"
                            echo "${err}"
                            errMessageList << "${err}!\n build back images failed"
                            errModuleName += moduleName
                        }
                    }
                }
            }
        }
    }

    //run task
    if(buildStatus.contentEquals("unset")) {
        parallel tasks
    }
    // 打标签
    if(buildStatus.contentEquals("unset")) {
        try {
            if((iftag.toLowerCase()).contentEquals('Y'.toLowerCase())) {
                stage('tag code') {
                    op.tagCode(tag, ltpSourceCodeDir)
                }
            }
        } catch(err) {
            buildStatus = "FALSE"
            echo "${err}"
            errMessageList << "${err}!\n"
            errModuleName += "tag code"
        }
    } else {
        stage('delete local tag') {
            op.deleteLocalTag(ltpSourceCodeDir, tag)
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
        def dockerUrlStingParameter = ""
        dockerUrl.each{ urlName->
            dockerUrlStingParameter += '<li>镜像地址ַ&nbsp;：'+urlName+'</li>'
        }
        def errMessage = errMessageList.join("<br>")
        println "失败模块及失败阶段：" + errModuleName
        println "失败原因：" + errMessage
        def bodyContent = """
            <li>构建时间&nbsp;：${buildTime}</li>
            <li>代码分支&nbsp;：${branch}</li>
            ${dockerUrlStingParameter}
            <li>构建日志&nbsp;：&nbsp;<a href="${BUILD_URL}console">${BUILD_URL}console</a></li>
            <li>失败模块及失败阶段&nbsp：${errModuleName}</li>
            <li>失败原因及错误日志&nbsp;：${errMessage}</li>
        """
        if(buildStatus.contentEquals("unset")) {
            mailSubjectStr = "Success! BuildJob: ${jobName} tag:${tag} originalsubject:${originalsubject}"
        }else {
            mailSubjectStr = "Failed! BuildJob: ${jobName} tag:${tag} originalsubject:${originalsubject}"
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