import java.text.SimpleDateFormat

def jobName = env.JOB_NAME
def buildNum = env.BUILD_NUMBER
def builder = env.BUILDER
def ifTag = env.IFTAG.replace(" ", "")
def branch = env.BRANCH.replace(" ", "")
def version = env.VERSION.replace(" ", "")
def mailList = env.MAILTO.replace(" ", "")
// 构建内容：主应用：APP 数据库脚本：DB 全部构建：BOTH
def buildContent = env.BUILDCONTENT.replace(" ", "")

def tag
def errModuleName = ""
def errMessageList = []
def buildStatus = "unset"
def nowDateStr

def pythonDir = "/home/source/pythonScript/${jobName}"
def appSource
def appOut
def appLink
def dbSource
def dbOut
def dbLink

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
                appSource = "${version}/publish/"
                appOut = "/DataBk/build/star-erp/jarfile/"
                dbSource = "DIRDBSCRIPTS/publish/"
                dbOut = "/DataBk/build/star-erp/ErpIntfDbScripts/"
                appLink = "<a href=\"ftp://10.0.250.250/star-erp/jarfile/${version}/${tag}/\">ftp://10.0.250.250/star-erp/jarfile/${version}/${tag}/</a>"
                dbLink = "<a href=\"ftp://10.0.250.250/star-erp/ErpIntfDbScripts/${version}/${tag}/\">ftp://10.0.250.250/star-erp/ErpIntfDbScripts/${version}/${tag}/</a>"

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
                // 清空历史构建产物
                sh"""
                    rm -rf ./${version}/publish/*
                    rm -rf ./DIRDBSCRIPTS/publish/*
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
                // 更新程序代码
                if(buildContent.contentEquals("BOTH") || buildContent.contentEquals("APP")) {
                    sh """
                        export LANG=zh_CN
                        export CVSROOT=:pserver:scm:scisnotcs@192.168.32.11:/cvsroot
                        cvs -d :pserver:scm:scisnotcs@192.168.32.11:/cvsroot login
                        if [ ! -d "./${version}/CVS" ]; then
                            cvs co -d ${version} -r ${branch} boss/starsms-3.x/develop/4.implement/starerpintf
                        else
                            cvs update -d -r ${branch} ./${version}
                        fi
                    """
                }
                // 更新数据库脚本代码
                if(buildContent.contentEquals("BOTH") || buildContent.contentEquals("DB")) {
                    sh """
                        export LANG=zh_CN
                        export CVSROOT=:pserver:scm:scisnotcs@192.168.32.11:/cvsroot
                        cvs -d :pserver:scm:scisnotcs@192.168.32.11:/cvsroot login
                        if [ ! -d "./DIRDBSCRIPTS/CVS" ]; then
                            cvs co -r HEAD -d DIRDBSCRIPTS boss/starsms-3.x/develop/4.implement/dbscripts/ErpIntfDbScripts
                        else
                            cvs update -d -r HEAD ./DIRDBSCRIPTS
                        fi
                    """
                }
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
                    sh """
                        export LANG=zh_CN
                        export LC_ALL=zh_CN
                        export JAVA_HOME=/usr/java/jdk1.6.0_45
                        /usr/apache-ant-1.9.6/bin/ant -file ${workspace}/${version}/build.xml -Dbuilder=${builder} -Dbuild.dstamp=${nowDateStr} -Dbuild.number=${buildNum} -Dproject.ver=${version} -Dmodule.ver=${version} build.all
                    """
                }
                if(buildContent.contentEquals("BOTH") || buildContent.contentEquals("DB")) {
                    sh """
                        export LANG=zh_CN
                        export LC_ALL=zh_CN
                        export JAVA_HOME=/usr/java/jdk1.5.0_22
                        /usr/apache-ant-1.9.6/bin/ant -file ${workspace}/DIRDBSCRIPTS/build.xml -Dbuild.dstamp=${nowDateStr} -Dbuild.number=${buildNum} -Dbuilder=${builder} -Dproject.ver=${version} -Dmodule.ver=${version} build.all
                    """
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
                // cvs打tag：tag不能包括`$,.:;@/'
                if(buildContent.contentEquals("BOTH") || buildContent.contentEquals("APP")) {
                    sh """
                        export CVSROOT=:pserver:scm:scisnotcs@192.168.32.11:/cvsroot
                        cvs -d :pserver:scm:scisnotcs@192.168.32.11:/cvsroot login
                        cvs rtag -r ${branch} ${tag} boss/starsms-3.x/develop/4.implement/starerpintf
                    """
                }
                if(buildContent.contentEquals("BOTH") || buildContent.contentEquals("DB")) {
                    sh """
                        export CVSROOT=:pserver:scm:scisnotcs@192.168.32.11:/cvsroot
                        cvs -d :pserver:scm:scisnotcs@192.168.32.11:/cvsroot login
                        cvs rtag -r HEAD ${tag} boss/starsms-3.x/develop/4.implement/dbscripts/ErpIntfDbScripts
                    """
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
                        echo "BUILDER  : ${builder}" > ./${version}/publish/buildinfo.txt
                        echo "BRANCH   : ${branch}" >> ./${version}/publish/buildinfo.txt
                    """
                    if(ifTag.contentEquals("Y")) {
                        sh """
                            echo "SRCTAG   : ${tag}" >> ./${version}/publish/buildinfo.txt
                        """
                    }
                    op.uploadBuildingProductToFtp("${workspace}/${appSource}", "ERPAPP", version, tag, pythonDir, appOut, "ERPAPP", null)
                }
                if(buildContent.contentEquals("BOTH") || buildContent.contentEquals("DB")) {
                    sh """
                        echo "BUILDER  : ${builder}" > ./DIRDBSCRIPTS/publish/buildinfo.txt
                    """
                    if(ifTag.contentEquals("Y")) {
                        sh """
                            echo "SRCTAG   : ${tag}" >> ./DIRDBSCRIPTS/publish/buildinfo.txt
                        """
                    }
                    op.uploadBuildingProductToFtp("${workspace}/${dbSource}", "ERPDB", version, tag, pythonDir, dbOut, "ERPDB", null)
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
