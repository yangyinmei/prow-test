import java.text.SimpleDateFormat

def jobName = env.JOB_NAME
def buildNum = env.BUILD_NUMBER
def builder = env.BUILDER
def ifTag = env.IFTAG.replace(" ", "")
def branch = env.BRANCH.replace(" ", "")
def dbBranch = env.DBBRANCH.replace(" ", "")
def version = env.VERSION.replace(" ", "")
def mailList = env.MAILTO.replace(" ", "")
// 构建内容：程序包：APP 数据库脚本：DB 全部构建：BOTH
def buildContent = env.BUILDCONTENT.replace(" ", "")

def tag
def errModuleName = ""
def errMessageList = []
def buildStatus = "unset"

def pythonDir = "/home/source/pythonScript/${jobName}"
def nowDateStr
def appSource
def appOut
def appLink
def dbSource
def dbOut
def dbLink
def starECMSGitUrl = "10.0.250.70:8088/StarECMS/StarECMS.git"
def dbScriptsGitUrl = "10.0.250.70:8088/StarECMS/DbScripts.git"
def toolGitUrl = "10.0.250.70:8088/StarECMS/tool.git"
def starECMSCodeDir = "/home/source/StarECMS/StarECMS"
def dbScriptsCodeDir = "/home/source/StarECMS/DbScripts"
def toolCodeDir = "/home/source/StarECMS/tool"

// 环境要求:CVS + ant + /usr/java/jdk1.5.0_22 + /usr/java/jdk1.6.0_45
node('base-slave') {
    try {
        stage('generate tag') {
            // 生成tag时需要的时间标签
            def nowdateFormat = new SimpleDateFormat("yyMMdd")
            nowDateStr = nowdateFormat.format(new Date())
            tag = "${version}-${buildNum}-${nowDateStr}"
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
                // 声明变量
                appSource = "StarECMS/publish/"
                appOut = "/DataBk/build/StarECMS/StarECMS/"
                dbSource = "DbScripts/publish/"
                dbOut = "/DataBk/build/StarECMS/DbScripts/"
                appLink = "<a href=\"ftp://10.0.250.250/StarECMS/StarECMS/${version}/${tag}/\">ftp://10.0.250.250/StarECMS/StarECMS/${version}/${tag}/</a>"
                dbLink = "<a href=\"ftp://10.0.250.250/StarECMS/DbScripts/${version}/${tag}/\">ftp://10.0.250.250/StarECMS/DbScripts/${version}/${tag}/</a>"

                checkout scm
                op = load ("./common/operation.groovy")
                // 更新python脚本（如果是新建的作业，没有此目录，需要创建）
                sh """
                    if [ ! -d ${pythonDir} ];then
                        mkdir ${pythonDir}
                    else
                        rm -rf  ${pythonDir}/* 
                    fi
                    cp -rf ./script/python/* ${pythonDir}/
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
                                                            string(name: 'PROJECT', value: "StarECMS"),
                                                            string(name: 'BRANCH', value: branch),
                                                            string(name: 'GITURL', value: starECMSGitUrl)]
                build job: 'UpdateSourceCode', parameters: [string(name: 'JOBNAME', value: jobName),
                                                            string(name: 'PROJECT', value: "DbScripts"),
                                                            string(name: 'BRANCH', value: dbBranch),
                                                            string(name: 'GITURL', value: dbScriptsGitUrl)]
                build job: 'UpdateSourceCode', parameters: [string(name: 'JOBNAME', value: jobName),
                                                            string(name: 'PROJECT', value: "tool"),
                                                            string(name: 'BRANCH', value: "master"),
                                                            string(name: 'GITURL', value: toolGitUrl)]
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
            stage('build') {
                if(buildContent.contentEquals("BOTH") || buildContent.contentEquals("APP")) {
                    dir(starECMSCodeDir) {
                        sh """
                            export LANG=en_US.UTF-8
                            rm -rf ./publish/
                            export JAVA_HOME=/usr/java/jdk1.6.0_45
                            /usr/apache-ant-1.9.6/bin/ant -file ./build.xml -Dbuilder=${builder} -Dbuild.dstamp=${nowDateStr} -Dbuild.number=${buildNum} -Dproject.ver=${version} -Dmodule.ver=${version} -Dbuild.extparam=all build.all || exit 1
                        """
                    }
                }
                if(buildContent.contentEquals("BOTH") || buildContent.contentEquals("DB")) {
                    dir(dbScriptsCodeDir) {
                        sh"""
                            export LANG=en_US.UTF-8
                            export JAVA_HOME=/usr/java/jdk1.5.0_22
                            /usr/apache-ant-1.9.6/bin/ant -file ./build.xml -Dbuild.dstamp=${nowDateStr} -Dbuild.number=${buildNum} -Dbuilder=${builder} -Dproject.ver=${version} -Dmodule.ver=${version} build.all || exit 1
                        """
                    }
                }
            }
        } catch(err) {
            buildStatus = "FALSE"
            echo "${err}"
            errMessageList << "${err}！\n构建失败，请检查模块名是否正确，或者联系开发人员解决！\n"
            errModuleName += ": build;"
        }
    }
    if(buildStatus.contentEquals("unset") && ifTag.contentEquals("Y")) {
        try {
            stage('tag code') {
                if(buildContent.contentEquals("BOTH") || buildContent.contentEquals("APP")) {
                    dir(starECMSCodeDir) {
                        sh """
                            if [ `git tag --list ${tag}` ]
                            then
                                echo '${tag} exists.'
                            else
                                echo '${tag} not exist.'
                                git config --local remote.origin.url http://jenkins:startimes@${starECMSGitUrl}
                                git tag -a ${tag} -m 'BUILDER: '
                            fi
                        """
                    }
                    op.tagCode(tag, starECMSCodeDir)
                }
                if(buildContent.contentEquals("BOTH") || buildContent.contentEquals("DB")) {
                    dir(dbScriptsCodeDir) {
                        sh """
                            if [ `git tag --list ${tag}` ]
                            then
                                echo '${tag} exists.'
                            else
                                echo '${tag} not exist.'
                                git config --local remote.origin.url http://jenkins:startimes@${dbScriptsGitUrl}
                                git tag -a ${tag} -m 'BUILDER: '
                            fi
                        """
                    }
                    op.tagCode(tag, dbScriptsCodeDir)
                }
            }
        } catch(err) {
            buildStatus = "FALSE"
            echo "${err}"
            errMessageList << "${err}!\n"
            errModuleName += ": tag code;"
        }
    }
    if(buildStatus.contentEquals("unset")) {
        try {
            stage('upload building product to ftp') {
                if(buildContent.contentEquals("BOTH") || buildContent.contentEquals("APP")) {
                    sh """
                        echo "BUILDER  : ${builder}" > ${starECMSCodeDir}/publish/buildinfo.txt
                    """
                    if(ifTag.contentEquals("Y")) {
                        sh """
                            echo "BRANCH   : ${branch}" >> ${starECMSCodeDir}/publish/buildinfo.txt
                            echo "SRCTAG   : ${tag}" >> ${starECMSCodeDir}/publish/buildinfo.txt
                        """
                    }
                    op.uploadBuildingProductToFtp("${starECMSCodeDir}/publish", "StarECMSAPP", version, tag, pythonDir, appOut, "StarECMSAPP", null)
                }
                if(buildContent.contentEquals("BOTH") || buildContent.contentEquals("DB")) {
                    sh """
                        echo "BUILDER  : ${builder}" > ${dbScriptsCodeDir}/publish/buildinfo.txt
                    """
                    if(ifTag.contentEquals("Y")) {
                        sh """
                            echo "DBBRANCH   : ${dbBranch}" >> ${dbScriptsCodeDir}/publish/buildinfo.txt
                            echo "SRCTAG   : ${tag}" >> ${dbScriptsCodeDir}/publish/buildinfo.txt
                        """
                    }
                    op.uploadBuildingProductToFtp("${dbScriptsCodeDir}/publish", "StarECMSDB", version, tag, pythonDir, dbOut, "StarECMSDB", null)
                }
            }
        } catch(err) {
            buildStatus = "FALSE"
            echo "${err}"
            errMessageList << "${err}!\n"
            errModuleName += ": upload building product to ftp;"
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
            <li>程序构建包&nbsp;：${appLink}
            <li>数据库脚本&nbsp;：${dbLink}
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