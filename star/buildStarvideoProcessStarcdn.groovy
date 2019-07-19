import java.text.SimpleDateFormat

def jobName = env.JOB_NAME
def buildNum = env.BUILD_NUMBER
def builder = env.BUILDER
def ifTag = env.IFTAG.replace(" ", "")
def branch = env.BRANCH.replace(" ", "")
def version = env.VERSION.replace(" ", "")
def mailList = env.MAILTO.replace(" ", "")

def tag
def errModuleName = ""
def errMessageList = []
def buildStatus = "unset"
def nowDateStr
def harborIp196 = ""
def harborUser196 = ""
def harborPassword196 = ""

def sourceCodeDir = "/home/source/${jobName}/sourceCode"
def pythonDir = "/home/source/pythonScript/${jobName}"
def uploadFtpDir = "/DataBk/build/${jobName}"
def sourceCodeGitUrl = "10.0.250.70:8088/starvideo-process/starcdn.git"

// 环境要求：/usr/java/jdk1.7.0_51 + /usr/apache-ant-1.9.6
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
                // 清空工作空间
                sh"""
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
                        export JAVA_HOME=/usr/java/jdk1.7.0_51
                        /usr/apache-ant-1.9.6/bin/ant -file ${sourceCodeDir}/starcdn_cacheManager/build.xml -Dbuilder=${builder} -Dbuild.dstamp=${nowDateStr} -Dbuild.number=${buildNum} -Dproject.ver=${version} -Dmodule.ver=${version} build.all
                        /usr/apache-ant-1.9.6/bin/ant -file ${sourceCodeDir}/starcdn_scheduleManager/build.xml -Dbuilder=${builder} -Dbuild.dstamp=${nowDateStr} -Dbuild.number=${buildNum} -Dproject.ver=${version} -Dmodule.ver=${version} build.all
                        /usr/apache-ant-1.9.6/bin/ant -file ${sourceCodeDir}/starcdn_ui/build.xml -Dbuilder=${builder} -Dbuild.dstamp=${nowDateStr} -Dbuild.number=${buildNum} -Dproject.ver=${version} -Dmodule.ver=${version} build.all
                        # 修改数据库包的名称
                        mv ${sourceCodeDir}/starcdn_cacheManager/publish/DB-${version}-dbscript-r-${buildNum}-${nowDateStr}.zip ${sourceCodeDir}/starcdn_cacheManager/publish/starcdn_cacheManager_DB-${version}-dbscript-r-${buildNum}-${nowDateStr}.zip
                        mv ${sourceCodeDir}/starcdn_scheduleManager/publish/DB-${version}-dbscript-r-${buildNum}-${nowDateStr}.zip ${sourceCodeDir}/starcdn_scheduleManager/publish/starcdn_scheduleManager_DB-${version}-dbscript-r-${buildNum}-${nowDateStr}.zip
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
            stage('build images') {
                dir(sourceCodeDir) {
                    sh"""
                        cp ./starcdn_ui/publish/starcdn_node_client-${version}-r-${buildNum}-${nowDateStr}.zip ./docker_build/
                        cp ./starcdn_ui/publish/starott_manageportal_client-${version}-r-${buildNum}-${nowDateStr}.zip ./docker_build/
                        cd ./docker_build/
                        unzip starcdn_node_client-${version}-r-${buildNum}-${nowDateStr}.zip -d starcdn_node_client
                        unzip starott_manageportal_client-${version}-r-${buildNum}-${nowDateStr}.zip -d starott_manageportal_client

                        cp -rf starott_manageportal_client/starott_manageportal_client.war/* starcdn_node_client/starcdn_node_client.war/

                        docker login -u ${harborUser196} -p ${harborPassword196} ${harborIp196}
                        docker build -t ${harborIp196}/starott-cdn/nodeui:${tag} -f ./nodeui.dockerfile .
                        docker push ${harborIp196}/starott-cdn/nodeui:${tag}
                        docker rmi ${harborIp196}/starott-cdn/nodeui:${tag}
                        rm -rf starcdn_node_client
                        rm -f starcdn_node_client-${version}-r-${buildNum}-${nowDateStr}.zip
                        rm -rf starott_manageportal_client
                        rm -f starott_manageportal_client-${version}-r-${buildNum}-${nowDateStr}.zip

                        rm -rf subSystems
                        cd ${sourceCodeDir}
                        cp ./starcdn_cacheManager/publish/starott_cache_manage_service-${version}-r-${buildNum}-${nowDateStr}.zip ./docker_build/
                        cd ./docker_build/
                        mkdir -p subSystems/cacheManager
                        unzip starott_cache_manage_service-${version}-r-${buildNum}-${nowDateStr}.zip -d subSystems/cacheManager > /dev/null

                        cd ${sourceCodeDir}
                        cp ./starcdn_scheduleManager/publish/starcdn_schedule_distribute_manager-${version}-r-${buildNum}-${nowDateStr}.zip ./docker_build/
                        cd ./docker_build/
                        mkdir -p subSystems/scheduleManager
                        unzip starcdn_schedule_distribute_manager-${version}-r-${buildNum}-${nowDateStr}.zip -d subSystems/scheduleManager > /dev/null
                        
                        cd ${sourceCodeDir}
                        cp ./starcdn_scheduleManager/publish/tools_dm.zip ./docker_build/
                        cd ./docker_build/
                        unzip tools_dm.zip

                        docker build -t ${harborIp196}/starott-cdn/subsystems:${tag} -f subsystems.dockerfile .
                        docker push ${harborIp196}/starott-cdn/subsystems:${tag}
                        docker rmi ${harborIp196}/starott-cdn/subsystems:${tag}

                        rm -rf subSystems
                        rm -rf tools
                        rm -f *.zip

                        cd ${sourceCodeDir}
                        mkdir -p ${sourceCodeDir}/outputs
                        echo "images list:" >> ./outputs/buildinfo.txt
                        echo "${harborIp196}/starott-cdn/nodeui:${tag}" >>  ./outputs/buildinfo.txt
                        echo "${harborIp196}/starott-cdn/subsystems:${tag}" >>  ./outputs/buildinfo.txt

                        docker rmi ${harborIp196}/starott-cdn/centos_cdn:base
                        docker rmi ${harborIp196}/starott-cdn/centos_abu_truck:base
                    """
                }
            }
        } catch(err) {
            buildStatus = "FALSE"
            echo "${err}"
            errMessageList << "${err}！\n"
            errModuleName += ": build images;"
        }
    }
    if(buildStatus.contentEquals("unset")) {
        try {
            stage('upload building product to ftp') {
                sh"""
                    cp -rf ${sourceCodeDir}/starcdn_cacheManager/publish/* ${sourceCodeDir}/outputs/
                    cp -rf ${sourceCodeDir}/starcdn_scheduleManager/publish/* ${sourceCodeDir}/outputs/
                    cp -rf ${sourceCodeDir}/starcdn_ui/publish/* ${sourceCodeDir}/outputs/
                    echo "BUILDER  : ${builder}" >> ${sourceCodeDir}/outputs/buildinfo.txt
                """
                if(ifTag.contentEquals("Y")) {
                    sh"""
                        echo "SRCTAG : ${tag}" >> ${sourceCodeDir}/outputs/buildinfo.txt
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