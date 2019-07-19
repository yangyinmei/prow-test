import java.text.SimpleDateFormat

def jobName = env.JOB_NAME
def buildNum = env.BUILD_NUMBER
def branch = env.BRANCH.replace(" ", "")
def group = env.GROUP.replace(" ", "")
def oldTag = env.OLDTAG.replace(" ", "")
def newTag = env.NEWTAG.replace(" ", "")
def version = env.VERSION.replace(" ", "")
def buildType = env.BUILDTYPE.replace(" ", "")
def deploypatterns = env.DEPLOYPATTERNS.replace(" ", "")
def ifTransferImages = env.TRANSFER.replace(" ", "")
def mailList = env.MAILTO.replace(" ", "")
def moduleStr = env.MODULESTR.replace(" ", "")
def ccMailList = env.CCMAILTO.replace(" ", "")

def yamlBranch = 'master'
def yamlTemplateBranch = 'stable'
def billingBranch = 'master'
def ccGroup = "jira-platform"

def tag = ""
def buildStatus = "unset"
def ifUploadToFtp = "false"
def createJiraBug = "false"
def buildTime
def jiraTaskUrl
def errModuleName = ""
def errCompileModuleName = ""
def errMessageList = []
def mailSubjectStr
def changedModules
def buildModuleList = []
def currentModuleList = []
def tasks = [:]
// 此列表用于记录编译失败信息，第一个值是bugLogMessage，第二个值是errJavaFile，第三个值是developers
def bugInfoList = []

def bossSourceCodeDir = "/home/source/${jobName}/sourceCode"
def billingSourceCodeDir = "/home/source/${jobName}/billingSourceCode"
def transferDir = "/home/source/transfer"
def uploadFtpDir = "/DataBk/build/stariboss-10.X"
def pythonDir = "/home/source/pythonScript/${jobName}"
def chartsDir = "/home/source/${jobName}/charts/charts"
def podName
def allList_boss = []

if(!(buildType.toLowerCase().contentEquals("buildall") || buildType.toLowerCase().contentEquals("incremental"))) {
    buildType = ""
}
if(moduleStr.toLowerCase().contentEquals("none")) {
    moduleStr = ""
}
node('jnlp-jdk11') {
    try {
        stage('generate tag') {
            checkout scm
            op = load ("./common/operation.groovy")
            props_comm = readProperties  file: './config/configComm.groovy'
            props_boss = readProperties  file: './config/configBOSS.groovy'
            // 生成tag时需要的时间标签
            def nowdateFormat = new SimpleDateFormat("yyMMdd")
            def nowDateStr = nowdateFormat.format(new Date())
            tag = op.generateTag(nowDateStr, moduleStr, newTag, branch, version, buildNum, transferDir, group)
            //如果是hotfix分支，输入的版本号小于上次构建的版本号，不构建
            if(tag.contentEquals("-1")) {
                buildStatus = "FALSE"
                errModuleName += "generate tag \n"
                errMessageList << "生成tag失败，请检查您输入的版本号是否正确！\n"
            }
        }
    } catch(err) {
        buildStatus = "FALSE"
        echo "${err}"
        errMessageList << "${err}!\n"
        errModuleName += "generate tag \n"
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
                                                            string(name: 'GITURL', value: "${props_boss.staribossSourcecodeGitUrl}")]
                build job: 'UpdateSourceCode', parameters: [string(name: 'JOBNAME', value: jobName),
                                                            string(name: 'PROJECT', value: "public-boss-jarfiles"),
                                                            string(name: 'BRANCH', value: "master"),
                                                            string(name: 'GITURL', value: "${props_boss.staribossPublicjarGitUrl}")]
                build job: 'UpdateSourceCode', parameters: [string(name: 'JOBNAME', value: jobName),
                                                            string(name: 'PROJECT', value: "yamlTemplate"),
                                                            string(name: 'BRANCH', value: yamlTemplateBranch),
                                                            string(name: 'GITURL', value: "${props_boss.staribossYamlTemplateGitUrl}")]
                build job: 'UpdateSourceCode', parameters: [string(name: 'JOBNAME', value: jobName),
                                                            string(name: 'PROJECT', value: "charts"),
                                                            string(name: 'BRANCH', value: yamlBranch),
                                                            string(name: 'GITURL', value: "${props_boss.staribossChartsGitUrl}")]
                build job: 'UpdateSourceCode', parameters: [string(name: 'JOBNAME', value: jobName),
                                                            string(name: 'PROJECT', value: "billingSourceCode"),
                                                            string(name: 'BRANCH', value: billingBranch),
                                                            string(name: 'GITURL', value: "${props_boss.staribossBillingGitUrl}")]
            }
        } catch(err) {
            buildStatus = "FALSE"
            echo "${err}"
            errMessageList << "${err}!\n更新代码失败，请检查您输入的分支是否正确！\n"
            errModuleName += "update code \n"
        }
    }

    if(buildStatus.contentEquals("unset")) {
        try {
            stage('prepare environment') {
                // 更新python脚本（如果是新建的作业，没有此目录，需要创建）
                sh """
                    if [ ! -d ${pythonDir} ];then
                        mkdir -p ${pythonDir}
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
                // 创建存放打镜像日志的目录
                sh """
                    mkdir -p /home/source/logfile/${tag}'_buildimages_log'
                """
                // 生产记录文件installflag.txt
                sh """
                    mkdir -p ${chartsDir}/${version}/${tag}
                    cd ${chartsDir}/${version}/${tag}
                    touch warinstallEnable.txt
                    touch change.txt
                """
                if(deploypatterns.contentEquals("war")) {
                    sh """ echo 'true' > ${chartsDir}/${version}/${tag}/warinstallEnable.txt """
                } else if(deploypatterns.contentEquals("imaging")) {
                    sh """ echo 'false' > ${chartsDir}/${version}/${tag}/warinstallEnable.txt """
                }
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
            stage('tag local code') {
                // tag local source code
                dir(bossSourceCodeDir) {
                    sh """
                        if [ `git tag --list ${tag}` ]
                        then
                            echo '${tag} exists.'
                        else
                            echo '${tag} not exist.'
                            git config --local remote.origin.url http://${props_comm.gitUser}:${props_comm.gitPassword}@${props_boss.staribossSourcecodeGitUrl}
                            git tag -a ${tag} -m 'BUILDER: '
                        fi
                    """
                }
                dir(billingSourceCodeDir) {
                    sh """
                        if [ `git tag --list ${tag}` ]
                        then
                            echo '${tag} exists.'
                        else
                            echo '${tag} not exist.'
                            git config --local remote.origin.url http://${props_comm.gitUser}:${props_comm.gitPassword}@${props_boss.staribossBillingGitUrl}
                            git tag -a ${tag} -m 'BUILDER: '
                        fi
                    """
                }
            }
        } catch(err) {
            buildStatus = "FALSE"
            echo "${err}"
            errMessageList << "${err}!\n"
            errModuleName += "tag local code \n"
        }
    }

    if(buildStatus.contentEquals("unset")) {
        try {
            stage('get buildModuleList') {
                allList_boss = Eval.me(props_boss.allList_boss)
                if(buildType) {
                    if(buildType.contentEquals('incremental')) {
                        if(!oldTag) {
                            sh """ 
                                touch ${transferDir}/${branch}${group}LASTTAG.txt
                            """
                            if(branch.contentEquals("hotfix_release") && group.contentEquals("hotfix")) {
                                def lastTagStr = readFile("${transferDir}/${branch}${group}LASTTAGBak.txt").replace(" ", "").replace("\n", ",")
                                def lastTagList = lastTagStr.tokenize(',')
                                for(lastTag in lastTagList) {
                                    if(lastTag.toString().split("\\.").length == 4) {
                                        oldTag = lastTag
                                    }
                                }
                            } else {
                                oldTag = readFile("${transferDir}/${branch}${group}LASTTAG.txt").replace(" ", "").replace("\n", "")
                            }
                            println "oldTag:"+oldTag
                        }
                        // oldTag的值为空，会获取构建列表失败，输出错误提示信息
                        if(oldTag) {
                            dir(bossSourceCodeDir) {
                                sh """
                                    chmod 777 gradlew
                                    export version1=${oldTag}
                                    export version2=${tag}
                                    export working_directory=${bossSourceCodeDir}
                                    ./gradlew clean :deploy:tools:version-review:run
                                """
                            }
                            moduleList = readFile("${bossSourceCodeDir}/moduleDependence.txt").replace(" ", "").tokenize(',')
                            println "changeModuleList:" + moduleList
                            moduleList.each { module->
                                if(module in Eval.me(props_boss.allList_boss)) {
                                    currentModuleList << module
                                }
                            }
                            buildModuleList = currentModuleList
                        } else {
                            buildStatus = "FALSE"
                            errModuleName += "get buildModuleList in cremental situation\n"
                            errMessageList << "oldTag为空, 获取增量构建列表失败! 请检查您是否输入了oldTag(如果构建新分支，必须输入oldTag)!\n"
                        }
                    } else {
                        buildModuleList = allList_boss
                    }
                } else {
                    buildModuleList = moduleStr.tokenize(',')
                }

                if(moduleStr) {
                    buildModuleList.each{ module->
                        if(!(module in allList_boss)) {
                            allList_boss << module
                        }
                    }
                }

                if(branch.contentEquals("feature_3") && ("platform-config" in buildModuleList)) {
                    buildModuleList = buildModuleList - "platform-config"
                    allList_boss = allList_boss -"platform-config"
                }
                println "buildModuleList:"+buildModuleList
            }
        } catch(err) {
            buildStatus = "FALSE"
            echo "${err}"
            errMessageList << "${err}!\n"
            errModuleName += "get buildModuleList 获取增量构建列表失败，请检查输入的oldTag是否正确！\n"
        }
    }
    
    if(buildStatus.contentEquals("unset")) {
        if(!buildModuleList) {
            echo "There has been no changes in any module since last successful building."
            changedModules = "No changings between ${oldTag} and ${tag}."
            stage('delete local tag') {
                op.deleteLocalTag(bossSourceCodeDir, tag)
            }
        } else {
            allList_boss.each { moduleName->
                def codeDir = "${props_boss.workDirectory}/workspace/${jobName}/${moduleName}"
                tasks[moduleName] = {
                    if(moduleName in buildModuleList) {
                        retry (3) {
                            if((!buildStatus.contentEquals("unset") && op.channelClosedErr(errMessageList)) || buildStatus.contentEquals("unset")) {
                                if(moduleName.contentEquals("account-billing")) {
                                    podName = "jnlp"
                                } else{
                                    podName = "jnlp-jdk11"
                                }
                                node(podName) {
                                    // 清空错误日志
                                    sh"""
                                        rm -rf /tmp/log.txt
                                        touch /tmp/log.txt
                                    """
                                    if(buildStatus.contentEquals("unset")) {
                                        try {
                                            stage('get code') {
                                                if(moduleName.contentEquals("account-billing")) {
                                                    op.getCode(billingSourceCodeDir, moduleName, branch)
                                                } else {
                                                    op.getCode(bossSourceCodeDir, moduleName, branch)
                                                }
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
                                                op.build(codeDir, moduleName, pythonDir, jobName)
                                            }
                                        } catch(err) {
                                            buildStatus = "FALSE"
                                            echo "${err}"
                                            if(readFile("/tmp/log.txt").contains("FAILURE: Build failed with an exception.")) {
                                                errMessageList << "${err}！\n${moduleName}模块编译失败，请联系开发人员解决！\n"
                                                errModuleName += moduleName + ": compile;"
                                            } else {
                                                errMessageList << "${err}！\n${moduleName}模块构建失败，请检查模块名是否正确！\n"
                                                errModuleName += moduleName + ": build;"
                                            }
                                            errCompileModuleName = moduleName
                                        }
                                    }
                                    if(buildStatus.contentEquals("unset")) {
                                        try {
                                            stage('move building output to dir: outputFileFolder') {
                                                op.moveBuildingOutputToDir(codeDir, moduleName, pythonDir)
                                            }
                                        } catch(err) {
                                            buildStatus = "FALSE"
                                            echo "${err}"
                                            errMessageList << "${err}!\n"
                                            errModuleName += moduleName + ": move building output to dir: outputFileFolder;"
                                        }
                                    }
                                    if(buildStatus.contentEquals("unset")) {
                                        try {
                                            stage('refactor output') {
                                                op.refactorOutput(codeDir, moduleName, pythonDir)
                                            }
                                        } catch(err) {
                                            buildStatus = "FALSE"
                                            echo "${err}"
                                            errMessageList << "${err}!\n"
                                            errModuleName += moduleName + ": refactor output;"
                                        }
                                    }
                                    if(buildStatus.contentEquals("unset") && deploypatterns.contentEquals("imaging")) {
                                        try {
                                            stage('modify third jars and build images') {
                                                op.modifyThirdJarsAndBuildImages(codeDir, moduleName, tag, pythonDir)
                                            }
                                            stage('delete image') {
                                                if (moduleName.contentEquals("account-billing")) {
                                                    op.deleteImage(moduleName, tag, "billing")
                                                } else {
                                                    op.deleteImage(moduleName, tag, "boss")
                                                }
                                            }
                                        } catch(err) {
                                            buildStatus = "FALSE"
                                            echo "${err}"
                                            errMessageList << "${err}!\n"
                                            errModuleName += moduleName + ": build images;"
                                        }
                                    }
                                    if(buildStatus.contentEquals("unset") && deploypatterns.contentEquals("war")) {
                                        try {
                                            stage('upload building product to ftp') {
                                                op.uploadBuildingProductToFtp(codeDir, moduleName, version, tag, pythonDir, uploadFtpDir, jobName, Eval.me(props_boss.zipList_boss))
                                                ifUploadToFtp = "true"
                                            }
                                        } catch(err) {
                                            buildStatus = "FALSE"
                                            echo "${err}"
                                            errMessageList << "${err}!\n"
                                            errModuleName += moduleName + ": upload building product to ftp;"
                                        }
                                    }
                                    // 从python的构建错误信息文件中读取错误信息
                                    if(!buildStatus.contentEquals("unset")) {
                                        errMessageList << readFile("/tmp/log.txt").replace("\n", "\n<br>")
                                    }
                                    // 如果编译失败，定位失败文件，及最近一次修改此文件的开发人员
                                    if(!buildStatus.contentEquals("unset") && errModuleName.contains(": compile;") && createJiraBug.contentEquals("false")) {
                                        try {
                                            bugInfoList = op.getInfoForCompileFailed(errMessageList)
                                            // 如果编译失败，在jira上创建bug，方便bug跟踪管理
                                            def summaryStr = "[代码提交构建失败] ${errCompileModuleName}此模块编译失败，开发人员为${bugInfoList[2]}，编译失败的文件是${bugInfoList[1]}，请相关开发人员关注并尽快解决！"
                                            sh """
                                                chmod 777 ${pythonDir}/recordChanggingsToJira.py
                                                python ${pythonDir}/recordChanggingsToJira.py "${summaryStr}" "${bugInfoList[0]}" ${jobName} ${buildStatus} "${bugInfoList[2]}" "${ccGroup}"
                                            """
                                            createJiraBug = "true"
                                        } catch(err) {
                                            echo "${err}"
                                            errModuleName += moduleName + ": create jira bug;"
                                        }
                                    }
                                }// end node('jnlp')
                            }
                        }
                    } else if(buildType && deploypatterns.contentEquals("imaging") && buildStatus.contentEquals("unset")) {
                        node('jnlp') {
                            try {
                                stage('retag and repush image') {
                                    def imageName = moduleName.toLowerCase()
                                    retry (3) {
                                        sh """ 
                                            docker login -u ${props_comm.harborUser}  -p ${props_comm.harborPassword} ${props_comm.harborIp}
                                            docker pull ${props_comm.harborIp}/boss/${imageName}:${oldTag}
                                            docker tag ${props_comm.harborIp}/boss/${imageName}:${oldTag} ${props_comm.harborIp}/boss/${imageName}:${tag}
                                            docker push ${props_comm.harborIp}/boss/${imageName}:${tag}
                                        """
                                        // 重试休眠
                                        sleep 3
                                    }
                                    sh """
                                        docker rmi ${props_comm.harborIp}/boss/${imageName}:${oldTag}
                                        docker rmi ${props_comm.harborIp}/boss/${imageName}:${tag}
                                    """
                                }
                            } catch(err) {
                                buildStatus = "FALSE"
                                echo "${err}"
                                errMessageList << "${err}!\n拉取未变更模块的镜像失败，请确认oldTag的值，或者上次构建是否为war包！\n"
                                errModuleName += moduleName + ": retag and repush image;"
                            }
                        }// end node
                    }//end if buildType
                }//end tasks
            }//end props_boss.allList_boss.each
        
            //run task
            if(buildStatus.contentEquals("unset")) {
                parallel tasks
            }

            // 记录此次构建模块列表到change.txt
            if(buildStatus.contentEquals("unset")) {
                try {
                    stage('record buildModuleList into txt') {
                        op.recordBuildModuleListIntoTxt(buildModuleList, allList_boss, Eval.me(props_boss.pms_frontend_moduleList), buildType, chartsDir, version, tag)
                    }
                } catch(err) {
                    buildStatus = "FALSE"
                    echo "${err}"
                    errMessageList << "${err}!\n"
                    errModuleName += "record buildModuleList into txt \n"
                }
            }
            
            // 构建成功 打tag；构建失败则删除本地tag
            if(buildStatus.contentEquals("unset")) {
                try {
                    stage('tag code') {
                        op.tagCode(tag, bossSourceCodeDir)
                        op.tagCode(tag, billingSourceCodeDir)
                    }
                } catch(err) {
                    buildStatus = "FALSE"
                    echo "${err}"
                    errMessageList << "${err}!\n"
                    errModuleName += "tag code \n"
                }
            } else {
                stage('delete local tag') {
                    op.deleteLocalTag(bossSourceCodeDir, tag)
                    op.deleteLocalTag(billingSourceCodeDir, tag)
                }
            }
    
            if(buildType) {
                if(buildStatus.contentEquals("unset")) {
                    try {
                        if(group.contentEquals("autotest")) {
                            stage('record current version and tag') {
                                op.recordCurrentVersionAndTag(version, tag, chartsDir)
                            }
                        }
                    } catch(err) {
                        buildStatus = "FALSE"
                        echo "${err}"
                        errMessageList << "${err}!\n"
                        errModuleName += "record current version and tag \n"
                    }
                }

                if(buildStatus.contentEquals("unset")) {
                   try {
                       stage('record changings to jira') {
                            dir(bossSourceCodeDir) {
                                def summaryStr = "Commit infomations of ${tag} in branch ${branch}, build type: ${buildType}, build job: ${jobName}."
                                if(buildType.contentEquals('incremental')) {
                                    sh """
                                        commitMessage=`git log --oneline ${oldTag}..${tag}|grep -E 'STARIBOSS|STARBA'|awk '{\$1="";print \$0}'`
                                        echo "\$commitMessage" > /tmp/commitMessage.txt
                                        if [ \${#commitMessage} -gt 30000 ]; then
                                            commitMessage=`git log --oneline --grep STARIBOSS ${oldTag}..${tag} --max-count=100 | awk '{\$1="";print \$0}'`
                                        fi
                                        commitMessage="----------详细提交信息见附件----------\n\$commitMessage"
                                        chmod 777 ${pythonDir}/recordChanggingsToJira.py
                                        python ${pythonDir}/recordChanggingsToJira.py "${summaryStr}" "\$commitMessage" ${jobName} ${buildStatus} null ${ccMailList}
                                    """
                                } else {
                                    sh """
                                        commitMessage=`git log --oneline --max-count=100|grep -E 'STARIBOSS|STARBA'|awk '{\$1="";print \$0}'`
                                        echo "\$commitMessage" > /tmp/commitMessage.txt
                                        commitMessage="----------详细提交信息见附件----------\n\$commitMessage"
                                        chmod 777 ${pythonDir}/recordChanggingsToJira.py
                                        python ${pythonDir}/recordChanggingsToJira.py "${summaryStr}" "\$commitMessage" ${jobName} ${buildStatus} null ${ccMailList}
                                    """
                                }
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
    
                if(buildStatus.contentEquals("unset")) {
                    try {
                        stage('record tag as oldTag') {
                            if(!(branch.contentEquals("hotfix_release") && group.contentEquals("hotfix"))) {
                                op.recordTagAsOldTag(tag, branch, transferDir, group)
                            }
                            // 如果是hotfix_release分支hotfix分组，而且输入了newTag，则需要将newTag记录为oldTag
                            else if(newTag) {
                                def lastTagStr = readFile("${transferDir}/${branch}${group}LASTTAG.txt").replace(" ", "").replace("\n", ",")
                                def lastTagList = lastTagStr.tokenize(',')
                                sh"""
                                    rm -rf ${transferDir}/${branch}${group}LASTTAG.txt
                                    touch ${transferDir}/${branch}${group}LASTTAG.txt
                                """
                                for(lastTag in lastTagList) {
                                    if(lastTag.toString().split("\\.").length == 4) {
                                        sh """
                                            echo ${newTag} >> ${transferDir}/${branch}${group}LASTTAG.txt
                                        """
                                    } else {
                                        sh """
                                            echo ${lastTag} >> ${transferDir}/${branch}${group}LASTTAG.txt
                                        """
                                    }
                                }
                            }
                        }
                    } catch(err) {
                       buildStatus = "FALSE"
                       echo "${err}"
                       errMessageList << "${err}!\n"
                       errModuleName += "record tag as oldTag \n"
                    }
                }
            }

            if(buildStatus.contentEquals("unset")) {
                try {
                    stage('push yaml to git') {
                        op.pushYamlToGit(version, tag, yamlBranch, chartsDir)
                    }
                } catch(err) {
                    stage('delete local yaml') {
                        dir(chartsDir) {
                            sh"""
                                rm -rf ./${version}/${tag}
                            """
                        }
                    }
                    buildStatus = "FALSE"
                    echo "${err}"
                    errMessageList << "${err}!\n"
                    errModuleName += "push yaml to git \n"
                }
            }
            
            if(ifTransferImages && buildStatus.contentEquals("unset") && (ifTransferImages.toLowerCase()).contentEquals("TRUE".toLowerCase())) {
                try {
                    if(deploypatterns && deploypatterns.contentEquals("imaging")) {
                        stage('trigger transferImages') {
                            build job: 'transferImages', parameters: [string(name: 'TAG', value: tag), string(name: 'MAILTO', value: mailList), string(name: 'MODULES', value: moduleStr)], wait: false
                        }
                    } else if(deploypatterns && deploypatterns.contentEquals("war")) {
                        stage('publish to aws s3') {
                            build job: 'publishToAWSs3', parameters: [string(name: 'TAG', value: tag), string(name: 'VERSION', value: version), string(name: 'MAILTO', value: mailList)], wait: false
                        }
                    }
                } catch(err) {
                    buildStatus = "FALSE"
                    echo "${err}"
                    errMessageList << "${err}!\n上传镜像或war包失败，可能是网络问题，请稍后重试，或联系管理员！\n"
                    errModuleName += "trigger transferImages \n"
                }
            }
        }
    }

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
    if((!buildStatus.contentEquals("unset") || !buildModuleList) && !newTag && branch.contentEquals("hotfix_release") && group.contentEquals("hotfix")) {
        try {
            stage('recover record tag') {
                sh"""
                    rm -rf ${transferDir}/${branch}${group}LASTTAG.txt
                    mv ${transferDir}/${branch}${group}LASTTAGBak.txt ${transferDir}/${branch}${group}LASTTAG.txt
                """
            }
        } catch(err) {
            echo "${err}"
            errMessageList << "${err}!\n"
            errModuleName += moduleName + ": recover record tag;"
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
                                                  string(name: 'ProjectName', value: "NewBOSS"),
                                                  string(name: 'Status', value: Status),
                                                  string(name: 'Branch', value: branch),
                                                  string(name: 'Version', value: version),
                                                  string(name: 'Tag', value: tag),
                                                  string(name: 'Environment', value:"192.168.32.173")
                                                 ]
    }
    
    stage('send email') {
        if(changedModules) {
            changedModules = changedModules
        } else if(buildType.contentEquals("incremental")) {
            changedModules = currentModuleList.join("<br>")
        }
        def errMessage = errMessageList.join("<br>")
        println "失败日志："
        println errMessage
        def bodyContent = """
            <li>失败模块及失败阶段：${errModuleName}</li>
            <li>构建类型&nbsp;：&nbsp;${buildType}</li>
            <li>旧标签&nbsp;：${oldTag}</li>
            <li>新标签&nbsp;：${tag}</li>
            <li>incremental build changings&nbsp;：<a href="${jiraTaskUrl}">${jiraTaskUrl}</a></li>
            <li>构建时间&nbsp;：${buildTime}</li>
            <li>代码分支&nbsp;：${branch}</li>
            <li>YAML的git地址ַ&nbsp;：<a href="http://10.0.250.70:8088/rd-platform/yaml.git">http://10.0.250.70:8088/rd-platform/yaml.git</a></li>
            <li>git上的YAML目录&nbsp;：&nbsp;.../k8s/application/boss/boss_yaml/${version}/${tag}/</li>
            <li>变更模块&nbsp;：&nbsp;<br>${changedModules}</li>
            <li>单独构建模块&nbsp;：&nbsp;<br>${moduleStr}</li>
            <li>构建日志&nbsp;：&nbsp;<a href="${BUILD_URL}console">${BUILD_URL}console</a></li>
            <li>失败原因及错误日志&nbsp;：${errMessage}</li>
        """

        if(buildStatus.contentEquals("unset")) {
            mailSubjectStr = "Success! BuildJob: ${jobName} tag:${tag} 第${buildNum}次构建！"
        }else {
            mailSubjectStr = "Failed! BuildJob: ${jobName} tag:${tag} 第${buildNum}次构建！"
        }
        op.sendMail(jobName, buildStatus, mailList, bodyContent, mailSubjectStr)
    }

    if(!(buildStatus.contentEquals("unset")) && errModuleName.contains(": compile;")) {
        stage('send email for compile fail') {
            mailList = "${mailList}, ${ccMailList}"
            if(changedModules) {
                changedModules = changedModules
            } else if(buildType.contentEquals("incremental")) {
                changedModules = currentModuleList.join("<br>")
            }
            def errMessage = errMessageList.join("<br>")
            def bodyContent = """
                <li>失败模块及失败阶段：${errModuleName}</li>
                <li><b>此次构建涉及代码变更的开发人员：${bugInfoList[2]}</b></li>
                <li>构建类型&nbsp;：&nbsp;${buildType}</li>
                <li>旧标签&nbsp;：${oldTag}</li>
                <li>新标签&nbsp;：${tag}</li>
                <li>incremental build changings&nbsp;：<a href="${jiraTaskUrl}">${jiraTaskUrl}</a></li>
                <li>构建时间&nbsp;：${buildTime}</li>
                <li>代码分支&nbsp;：${branch}</li>
                <li>变更模块&nbsp;：&nbsp;<br>${changedModules}</li>
                <li>单独构建模块&nbsp;：&nbsp;<br>${moduleStr}</li>
                <li>构建日志&nbsp;：&nbsp;<a href="${BUILD_URL}console">${BUILD_URL}console</a></li>
                <li>失败原因及错误日志&nbsp;：${errMessage}</li>
            """
            mailSubjectStr = "${errCompileModuleName}此模块编译失败，开发人员为${bugInfoList[2]}，编译失败的文件是${bugInfoList[1]}，请相关开发人员关注并尽快解决！"
            op.sendMail(jobName, buildStatus, mailList, bodyContent, mailSubjectStr)
        }
    }

    //构建失败
    if(!(buildStatus.contentEquals("unset"))) {
        stage('build faild') {
            echo "Error stage: ${errModuleName}"
            error 'build faild'
        }
    }
}