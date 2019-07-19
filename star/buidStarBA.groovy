import java.text.SimpleDateFormat

def jobName = env.JOB_NAME
def buildNum = env.BUILD_NUMBER
def builder = env.BUILDER
def ifTag = env.IFTAG.replace(" ", "")
def branch = env.BRANCH.replace(" ", "")
def version = env.VERSION.replace(" ", "")
def build_enviroment = env.BUILD_ENVIRONMENT.replace(" ", "")
def is_variants_apk = env.IS_VARIANTS_APK.replace(" ", "")
def task = env.TASK.replace(" ", "")
def mailList = env.MAILTO.replace(" ", "")

def tag
def oldTag
def errModuleName = ""
def errMessageList = []
def buildStatus = "unset"
def nowDateStr

def sourceCodeDir = "/home/source/${jobName}/sourceCode"
def transferDir = "/home/source/transfer"
def pythonDir = "/home/source/pythonScript/${jobName}"
def uploadFtpDir = "/DataBk/build/${jobName}"
def sourceCodeGitUrl = "10.0.250.70:8088/android-boss/starda_combine.git"

// 环境要求：外网下载gradle 3.3及相关jar包等 + Android SDK Build-tools, revision 25
node("192.168.32.170") {
    try {
        stage('generate tag') {
            // 生成tag时需要的时间标签
            def nowdateFormat = new SimpleDateFormat("yyMMdd")
            nowDateStr = nowdateFormat.format(new Date())
            tag = "${version}-${buildNum}-${nowDateStr}"
            println "tag : " + tag
            oldTag = readFile("${transferDir}/starBA-AutobuildLASTTAG.txt").replace(" ", "").replace("\n", "")
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
                // 清空代码
                sh """
                    rm -rf ${sourceCodeDir}
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

    if(buildStatus.contentEquals("unset")) {
        try {
            stage('build') {
                dir(sourceCodeDir) {
                    sh"""
                        #为gradle传递签名
                        echo systemProp.REPO_keyAlias=StarDA > gradle.properties
                        echo systemProp.REPO_keyPassword=www.startimes.com.cn >> gradle.properties
                        echo systemProp.REPO_storePassword=www.startimes.com.cn >> gradle.properties

                        . /etc/profile
                        
                        cp -rf countly/src/main/assets/google-services-${build_enviroment}.json starda-common/google-services.json
                        cp -rf countly/src/main/assets/app_url_${build_enviroment}.xml countly/src/main/res/values/app_url.xml
                        
                        newVersion=`git rev-list ${branch} --count`

                        echo "build.git.number=\$newVersion" > app/version.properties
                        echo "build.git.branch=${branch}" >> app/version.properties
                        echo "build.target.env=${build_environment}" >> app/version.properties
                        echo "build.variants.apk=${is_variants_apk}" >> app/version.properties
                        chmod 777 gradlew
                        ./gradlew clean ${task}
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
                    echo "BUILDER  : ${builder}" > ${sourceCodeDir}/app/build/outputs/apk/release/buildinfo.txt
                """
                if(ifTag.contentEquals("Y")) {
                    sh"""
                        echo "SRCTAG : ${tag}" >> ${sourceCodeDir}/app/build/outputs/apk/buildinfo.txt
                    """
                }
                op.uploadBuildingProductToFtp(sourceCodeDir, jobName, version, tag, pythonDir, uploadFtpDir, jobName, null)  
            }
        } catch(err) {
            buildStatus = "FALSE"
            echo "${err}"
            errMessageList << "${err}!\n"
            errModuleName += ": upload building product to ftp;"
        }
    }

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

    if(buildStatus.contentEquals("unset") && jobName.contentEquals("starBA-Autobuild")) {
        try {
            stage('record tag as oldTag') {
                sh """ echo ${tag} > ${transferDir}/starBA-AutobuildLASTTAG.txt """
            }
        } catch(err) {
           buildStatus = "FALSE"
           echo "${err}"
           errMessageList << "${err}!\n"
           errModuleName += "record tag as oldTag \n"
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
        if(jobName.contentEquals("starBA-Autobuild")) {
            // 获取代码git提交记录
            dir(sourceCodeDir) {
                sh """
                    commitMessage=`git log --oneline ${oldTag}..${tag}|awk '{\$1="";print \$0}'`
                    echo "\$commitMessage" > /tmp/commitMessage.txt
                """
            }
        }
        def gitCommitInfo = readFile("/tmp/commitMessage.txt").replace("\n", "\n<br>")
        def bodyContent = """
            <li>新标签&nbsp;：${tag}</li>
            <li>构建时间&nbsp;：${buildTime}</li>
            <li>代码分支&nbsp;：${branch}</li>
            <li>构建编号&nbsp;：第${buildNum}次构建</li>
            <li>程序构建包&nbsp;：<a href="ftp://10.0.250.250/${jobName}/${version}/${tag}/">ftp://10.0.250.250/${jobName}/${version}/${tag}/</a></li>
            <li>构建日志&nbsp;：&nbsp;<a href="${BUILD_URL}console">${BUILD_URL}console</a></li>
            <li>git提交信息&nbsp;：${gitCommitInfo}</li>
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