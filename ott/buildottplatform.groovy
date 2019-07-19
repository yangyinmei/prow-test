import java.text.SimpleDateFormat

def branch = env.BRANCH
def version = env.VERSION
def iftag = env.IFTAG
def mailList = env.MAILTO
def extparam = env.EXTPARAM
def ifPublishImages = env.IFPUBLISHIMAGES
def jobName = env.JOB_NAME
def buildnum = env.BUILD_NUMBER
def originalsubject = env.originalsubject

// 在生成tag时，判断用
def moduleStr = ""
def newTag = ""
def group = ""
def tag
def buildStatus = "unset"
def ifUploadToFtp = "false"
def buildTime
def jiraTaskUrl
def errModuleName
def errMessageList = []
def mailSubjectStr
def buildModuleList
def tasks = [:]
def dockerUrl = []
def yamlBranch = 'master'
def yamlTemplateBranch='stable'
def harborIp196 = ""
def harborUser196 = ""
def harborPassword196 = ""

def ottplatformSourceCodeDir = "/home/source/${jobName}/sourceCode"
def yamlTemplateDir = "/home/source/${jobName}/yamlTemplate/ott/ott_yaml"
def yamlDir = "/home/source/${jobName}/yaml"
def masterYamlDir = "/home/nfs/${jobName}/yaml"
def transferDir = "/home/source/transfer"
def pythonDir = "/home/source/pythonScript/${jobName}"
def uploadFtpDir = "/DataBk/build/${jobName}"

node('slave') {
    try {
        stage('prepare environment') {
            checkout scm
            op = load ("./common/operation.groovy")
            props_comm = readProperties  file: './config/configComm.groovy'
            props_ott = readProperties  file: './config/configOTT.groovy'
            
            harborIp196 = props_comm.harborIp196
            harborUser196 = props_comm.harborUser196
            harborPassword196 = props_comm.harborPassword196
            // 更新python脚本
            sh """
                rm -rf  ${pythonDir}/*
                mkdir -p ${pythonDir}
                cp -rf ./script/python/* ${pythonDir}/
            """
            // 生成tag
            def nowdateFormat = new SimpleDateFormat("yyMMdd")
            def nowDateStr = nowdateFormat.format(new Date())
            tag = op.generateTag(nowDateStr, moduleStr, newTag, branch, version, buildnum, transferDir, group)
            // 配置git信息
            sh"""
                echo "###Config git info###"
                git config --global user.name 'jenkins'
                git config --global user.email 'jenkins@startimes.com.cn'
                git config --global http.postBuffer 524288000
            """
            // 清空记录文件
            sh """
                rm -rf ${transferDir}/${jobName}CommitMessage
                touch ${transferDir}/${jobName}CommitMessage
            """
        }
    } catch(err) {
        buildStatus = "FALSE"
        echo "${err}"
        errMessageList << "${err}!\n"
        errModuleName += "prepare environment"
    }
    
    if(buildStatus.contentEquals("unset")) {
        try {
            stage('update code') {
                // 开始构建时间
                def dateFormat = new SimpleDateFormat("yyyy-MM-dd-HH-mm")
                def getCodeDate = new Date()
                buildTime = dateFormat.format(getCodeDate)

                build job: 'UpdateSourceCode', parameters: [string(name: 'JOBNAME', value: jobName),
                                                            string(name: 'PROJECT', value: "sourceCode"),
                                                            string(name: 'BRANCH', value: branch),
                                                            string(name: 'GITURL', value: "${props_ott.ottplatformSourcecodeGitUrl}")]
                build job: 'UpdateSourceCode', parameters: [string(name: 'JOBNAME', value: jobName),
                                                            string(name: 'PROJECT', value: "yamlTemplate"),
                                                            string(name: 'BRANCH', value: yamlTemplateBranch),
                                                            string(name: 'GITURL', value: "${props_ott.yamlTemplateGitUrl}")]
                build job: 'UpdateSourceCode', parameters: [string(name: 'JOBNAME', value: jobName),
                                                            string(name: 'PROJECT', value: "yaml"),
                                                            string(name: 'BRANCH', value: yamlBranch),
                                                            string(name: 'GITURL', value: "${props_ott.yamlGitUrl}")]
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
            stage('tag local code') {
                // tag local source code
                dir(ottplatformSourceCodeDir) {
                    sh """
                        if [ `git tag --list ${tag}` ]
                        then
                            echo '${tag} exists.'
                        else
                            echo '${tag} not exist.'
                            git config --local remote.origin.url http://${props_comm.gitUser}:${props_comm.gitPassword}@${props_ott.ottplatformSourcecodeGitUrl}
                            git tag -a ${tag} -m 'BUILDER: '
                        fi
                    """
                }
            }
        } catch(err) {
            buildStatus = "FALSE"
            echo "${err}"
            errMessageList << "${err}!\n"
            errModuleName += "tag local code"
        }
    }

    if(buildStatus.contentEquals("unset")) {
        try {
            stage('get buildModuleList') {
                if(extparam) {
                    buildModuleList = extparam.tokenize(',')
                }
                println "buildModuleList:"+buildModuleList
            }
        } catch(err) {
            buildStatus = "FALSE"
            echo "${err}"
            errMessageList << "${err}!\n"
            errModuleName += "get buildModuleList"
        }
    }
    
    if(buildStatus.contentEquals("unset")) {
        if(!buildModuleList) {
            echo "There has been no changes in any module since last successful building."
            stage('delete local tag') {
                op.deleteLocalTag(ottplatformSourceCodeDir, tag)
            }
        } else {
            buildModuleList.each { moduleName->
                tasks[moduleName] = {
                    node('slave') {
                        def workspace = env.WORKSPACE
                        def codeDir = "${workspace}/${moduleName}"
                        println "codeDir : " + codeDir
                        // 清空错误日志
                        sh """
                            rm -rf /home/logfile/${jobName}log.txt
                            touch /home/logfile/${jobName}log.txt
                        """
                        if(buildStatus.contentEquals("unset")) {
                            try {
                                stage('get code') {
                                    op.getCode(ottplatformSourceCodeDir, moduleName, branch)
                                }
                            }catch(err) {
                                buildStatus = "FALSE"
                                echo "${err}"
                                errMessageList << "${err}!\n"
                                errModuleName += moduleName + "： get code"
                            }
                        }

                        if(buildStatus.contentEquals("unset")) {
                            try {
                                stage('build') {
                                    dir(moduleName) {
                                        sh """
                                            export JAVA_HOME=/usr/java/jdk1.8.0_66
                                            chmod 777 gradlew
                                            ./gradlew clean
                                            ./gradlew ${moduleName} 2> /home/logfile/${jobName}log.txt
                                        """
                                    }
                                }
                            } catch(err) {
                                buildStatus = "FALSE"
                                echo "${err}"
                                errMessageList << "${err}!\n构建失败，请检查模块名是否正确，或者联系开发人员解决！\n"
                                errModuleName += moduleName + "： build"
                            }
                        }

                        if(buildStatus.contentEquals("unset")) {
                            try {
                                stage('move building output to outputFileFolder') {
                                    dir(moduleName) {
                                        sh """
                                            rm -rf ./outputFileFolder
                                            mkdir outputFileFolder

                                            if [ ${moduleName} == "platform-config" ]
                                            then
                                                mv ./platform/platform-config/build/distributions/platform-config.zip ./outputFileFolder/
                                            elif [ ${moduleName} == "search-config" ]
                                            then
                                                mv ./service/search/search-config/build/distributions/search-config.zip ./outputFileFolder/
                                            elif [ ${moduleName} == "vbsc-service" ]
                                            then
                                                mv ./service/vbms/vbsc-service/build/distributions/vbsc.zip ./outputFileFolder/
                                            elif [ ${moduleName} == "imdb-proxy-config" ]
                                            then
                                                mv ./service/imdb-proxy/imdb-proxy-config/build/distributions/imdb-proxy-config.zip ./outputFileFolder/
                                            else
                                                find . -name ${moduleName}.war |xargs -i -exec cp {} ./outputFileFolder/
                                            fi
                                        """
                                    }
                                }
                            } catch(err) {
                                buildStatus = "FALSE"
                                echo "${err}"
                                errMessageList << "${err}!\n"
                                errModuleName += moduleName + "： move building output to outputFileFolder"
                            }
                        }

                        if(buildStatus.contentEquals("unset")) {
                            try {
                                stage('build images') {
                                    if(!(moduleName in Eval.me(props_ott.zip_moduleList))) {
                                        dir(moduleName) {
                                            sh """
                                                rm -f ./docker_build/dockerfiles/*.war
                                                cp ./outputFileFolder/${moduleName}.war ./docker_build/dockerfiles/
                                                cd ./docker_build/dockerfiles/
                                                unzip ${moduleName}.war -d ${moduleName}
                                                
                                                docker login -u ${harborUser196} -p ${harborPassword196} ${harborIp196}
                                                docker build -t ${harborIp196}/starott-platform/${moduleName}:${tag} -f ./${moduleName}_Dockerfile .
                                                docker push ${harborIp196}/starott-platform/${moduleName}:${tag}
                                                rm -rf ./${moduleName}
                                            """
                                        }
                                        dockerUrl << '/starott-platform/'+moduleName+':'+tag
                                    }
                                }
                            } catch(err) {
                                buildStatus = "FALSE"
                                echo "${err}"
                                errMessageList << "${err}!\n"
                                errModuleName += moduleName + "： build images"
                            }
                        }

                        if(buildStatus.contentEquals("unset")) {
                            try {
                                stage('upload building product to ftp') {
                                    if(ifPublishImages && (ifPublishImages.toLowerCase()).contentEquals("TRUE".toLowerCase())) {
                                        if(!(moduleName in Eval.me(props_ott.zip_moduleList))) {
                                            retry (3) {
                                                sh """
                                                    cd ${codeDir}/outputFileFolder
                                                    docker save ${harborIp196}/starott-platform/${moduleName}:${tag} > ${moduleName}-${tag}.tar
                                                """
                                            }
                                        }
                                    }
                                    op.uploadBuildingProductToFtp(codeDir, moduleName, version, tag, pythonDir, uploadFtpDir, jobName, null)
                                    if(ifPublishImages && (ifPublishImages.toLowerCase()).contentEquals("TRUE".toLowerCase())) {
                                        if(moduleName in Eval.me(props_ott.aws_product_modulelist)) {
                                            sh"""
                                                cd ${codeDir}/outputFileFolder
                                                mkdir ${moduleName}
                                                unzip ${moduleName}.war -d ${moduleName}
                                                cp -r /home/ottplatform-aws/${moduleName}-tomcat ./
                                                cp -r ./${moduleName} ./${moduleName}-tomcat/webapps/
                                                sed -i 's/zookeeper-service:2181/172.31.35.217:2181,172.31.42.204:2181,172.31.40.73:2181/g' ./${moduleName}-tomcat/webapps/${moduleName}/WEB-INF/classes/config.properties
                                                sed -i 's/CATALINA_OUT="\$CATALINA_BASE"\\/logs\\/catalina.out/CATALINA_OUT=\\/dev\\/null/g' ./${moduleName}-tomcat/bin/catalina.sh
                                                cp -r /home/source/${jobName}/yaml/k8s/application/ott/platform-config/platform-config ./
                                                cp -r /home/source/${jobName}/yaml/k8s/application/ott/platform-config/aws-production/configScripts ./platform-config/
                                                chmod +x ./${moduleName}-tomcat/bin/*.sh
                                                chmod +x ./platform-config/bin/*
                                                zip -r ${moduleName}-aws-${tag}.zip ./${moduleName}-tomcat ./platform-config
                                                rm -rf ${moduleName}.war
                                                rm -rf ${moduleName}-${tag}.tar
                                                rm -rf ${moduleName}-tomcat
                                            """
                                        }
                                        def awsUploadFtpDir = "/DataBk/build/starott-platform-aws"
                                        op.uploadBuildingProductToFtp(codeDir, moduleName, version, tag, pythonDir, awsUploadFtpDir, jobName, null)
                                    }
                                    ifUploadToFtp = "true"
                                }
                            } catch(err) {
                                buildStatus = "FALSE"
                                echo "${err}"
                                errMessageList << "${err}!\n"
                                errModuleName += moduleName + "： upload building product to ftp"
                            }
                        }

                        try {
                            stage('delete images') {
                                if(!(moduleName in Eval.me(props_ott.zip_moduleList))) {
                                    sh """
                                        docker rmi ${harborIp196}/starott-platform/${moduleName}:${tag}
                                    """
                                }
                            }
                        } catch(err) {
                            buildStatus = "FALSE"
                            echo "${err}"
                            errMessageList << "${err}!\n"
                            errModuleName += moduleName + "： delete images"
                        }

                        if(buildStatus.contentEquals("unset")) {
                            try {
                                stage('create and upload yaml files') {
                                    if(!(moduleName in Eval.me(props_ott.zip_moduleList))){
                                        yamlDir = "/home/source/${jobName}/yaml/k8s/application/ott/ott_yaml"
                                        if(moduleName in Eval.me(props_ott.ottplatform_multiple_yaml_modulelist)) {
                                            Eval.me(props_ott.ottplatform_multiple_kind).each { multipleKind->
                                                op.createAndUploadYamlFiles(version, tag, moduleName + multipleKind, pythonDir, yamlDir, yamlTemplateDir, Eval.me(props_ott.yaml_kind_list))
                                            }
                                        } else {
                                            op.createAndUploadYamlFiles(version, tag, moduleName, pythonDir, yamlDir, yamlTemplateDir, Eval.me(props_ott.yaml_kind_list))
                                        }
                                    }
                                }
                            } catch(err) {
                                buildStatus = "FALSE"
                                echo "${err}"
                                errMessageList << "${err}!\n"
                                errModuleName += moduleName + "： create and upload yaml files"
                            }
                        }
                        // 从构建错误日志文件中读取错误信息
                        if(!buildStatus.contentEquals("unset")) {
                            errMessageList << readFile("/home/logfile/${jobName}log.txt").replace("\n", "\n<br>")
                        }
                    }//end node
                }//end task
            }//end buildModuleList.each

            //run task
            if(buildStatus.contentEquals("unset")) {
                parallel tasks
            }
            
            // 打标签
            if(buildStatus.contentEquals("unset")) {
                try {
                    if((iftag.toLowerCase()).contentEquals('Y'.toLowerCase())) {
                        stage('tag code') {
                            op.tagCode(tag, ottplatformSourceCodeDir)
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
                    op.deleteLocalTag(ottplatformSourceCodeDir, tag)
                }
            }

            if(buildStatus.contentEquals("unset")) {
                try {
                    stage('push yaml to git') {
                        node('master') {
                            op.pushYamlToGit(version,tag,yamlBranch,masterYamlDir,jobName)
                        }
                    }
                } catch(err) {
                    buildStatus = "FALSE"
                    echo "${err}"
                    errMessageList << "${err}!\n"
                    errModuleName += "push yaml to git"
                }
            }

            if(buildStatus.contentEquals("unset")) {
               try {
                   stage('record changings to jira') {
                        dir(ottplatformSourceCodeDir) {
                            sh """
                                commitMessage=`git log --oneline --max-count=100 | awk '{\$1="";print \$0}'`
                                echo "\$commitMessage" >> ${transferDir}/${jobName}CommitMessage
                                echo "\$commitMessage" > /tmp/commitMessage.txt
                                commitMessage="----------详细提交信息见附件----------\n\$commitMessage"
                                chmod 777 ${pythonDir}/recordChanggingsToJira.py
                                python ${pythonDir}/recordChanggingsToJira.py "no old tag" ${tag} "\$commitMessage" ${branch} null ${jobName}
                            """
                        }
                        jiraTaskUrl = readFile("${transferDir}/issueUrlFile").replace(" ", "").replace("\n", "")
                    }
                } catch(err) {
                    buildStatus = "FALSE"
                    echo "${err}"
                    errMessageList << "${err}!\n"
                    errModuleName += "record changings to jira \n"
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
        }//end else [if(!buildModuleList)]
    }//end buildStatus

    if(!buildStatus.contentEquals("unset") && ifUploadToFtp.contentEquals("true")) {
        try {
            stage('delete product from ftp') {
                op.deleteProductFromFtp(version, tag, uploadFtpDir, pythonDir)
            }
        } catch(err) {
            echo "${err}"
            errMessageList << "${err}!\n"
            errModuleName += moduleName + ": delete product from ftp;"
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
        def commitMessage =  readFile("${transferDir}/${jobName}CommitMessage").replace("\n", "<br>")
        def errMessage = errMessageList.join("<br>")
        println "失败模块及失败阶段：" + errModuleName
        println "失败原因：" + errMessage
        def bodyContent = """
            <li>构建时间&nbsp;：${buildTime}</li>
            <li>代码分支&nbsp;：${branch}</li>
            <li>ftp地址ַ&nbsp;：<a href="ftp://10.0.250.250/${jobName}/${version}/${tag}">ftp://10.0.250.250/${jobName}/${version}/${tag}</a></li>
            <li>ftp发布地址ַ&nbsp;：<a href="ftp://10.0.250.250/starott-platform-aws/${version}/${tag}">ftp://10.0.250.250/starott-platform-aws/${version}/${tag}</a></li>
            ${dockerUrlStingParameter}
            <li>云上镜像地址：</li>
            ${onlineImageUrl}
            <li>build changings&nbsp;：<a href="${jiraTaskUrl}">${jiraTaskUrl}</a></li>
            <li>构建日志&nbsp;：&nbsp;<a href="${BUILD_URL}console">${BUILD_URL}console</a></li>
            <li>失败模块及失败阶段&nbsp：${errModuleName}</li>
            <li>失败原因及错误日志&nbsp;：${errMessage}</li>
            <li>变更记录&nbsp：${commitMessage}</li>
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