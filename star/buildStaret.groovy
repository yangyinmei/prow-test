import java.text.SimpleDateFormat

def jobName = env.JOB_NAME
def buildNum = env.BUILD_NUMBER
def builder = env.BUILDER
def ifTag = env.IFTAG.replace(" ", "")
def branch = env.BRANCH.replace(" ", "")
def version = env.VERSION.replace(" ", "")
def mailList = env.MAILTO.replace(" ", "")
def extParam = env.EXTPARAM.replace(" ", "")
def cpuParam = env.CPUPARAM.replace(" ", "")
def ifImage = env.IFIMAGE.replace(" ", "")
def ifPublishImages = env.IFPUBLISHIMAGES.replace(" ", "")

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
def sourceCodeGitUrl = "10.0.250.70:8088/starvideo-process/staret.git"

// 环境要求：/usr/java/jdk1.7.0_51 + 免密登录10.0.224.21 + 编译库（libevent-2.0.22-stable.tar.gz，libzmq-master.zip，log4cpp-1.1.1.tar.gz）
node("192.168.32.170") {
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

    if(buildStatus.contentEquals("unset")) {
        if(cpuParam.contentEquals("E5")) {
            try {
                stage('build codeC on E5') {
                    // 清空构建产物
                    sh """
                        rm -rf ${sourceCodeDir}/ETFE/manage-portal/dist
                        mkdir -p ${sourceCodeDir}/ETFE/manage-portal/dist/
                    """
                    sh"""
                        ssh -p 8029 root@10.0.224.21<< remotessh
                        source /root/.bash_profile
                        rm -rf /simba/
                        rm -rf  /StarETCodec_publish-E5
                        mkdir -p /StarETCodec_publish-E5
                        git clone http://jenkins:startimes@10.0.250.70:8088/starvideo-process/starcodec.git --branch develop-2.6 /simba/
                        cd /simba/StarETCodec/ffmpeg-2.6-loft
                        chmod +x version.sh configure
                        export PKG_CONFIG_PATH=/usr/local/lib/pkgconfig
                        ./configure --enable-libfreetype --enable-libfontconfig --enable-libfribidi --enable-libzmq --enable-libopencore-amrnb --enable-libvo-amrwbenc --enable-libfdk-aac --enable-libfaac --enable-libmp3lame --enable-libass --enable-libx264 --enable-libx265 --enable-nonfree --enable-gpl --disable-ffplay --disable-ffserver --enable-version3 --extra-ldflags='-L/opt/intel/mediasdk/lib/lin_x64' --extra-libs='-levent -lstdc++' --extra-cflags='-I/opt/intel/mediasdk/include -D_GNU_SOURCE=1 -D_REENTRANT'
                        make -j 8
                        cp /simba/StarETCodec/ffmpeg-2.6-loft/ffmpeg /StarETCodec_publish-E5
                        cp /simba/StarETCodec/ffmpeg-2.6-loft/ffprobe /StarETCodec_publish-E5
                        exit
                        remotessh
                    """
                }
            } catch(err) {
                buildStatus = "FALSE"
                echo "${err}"
                errMessageList << "${err}!\n"
                errModuleName += ": build codeC on E5;"
            }
        } else if(cpuParam.contentEquals("I7")) {
            try {
                stage('build codeC on I7') {
                    sh"""
                        ssh -p 22 root@10.0.224.21<< remotessh
                        source /root/.bash_profile
                        rm -rf /simba/
                        rm -rf  /StarETCodec_publish-I7
                        mkdir -p /StarETCodec_publish-I7
                        git clone http://jenkins:startimes@10.0.250.70:8088/starvideo-process/starcodec.git --branch develop-2.6 /simba/
                        cd /simba/StarETCodec/ffmpeg-2.6-loft
                        chmod +x version.sh configure
                        export PKG_CONFIG_PATH=/usr/local/lib/pkgconfig
                        ./configure --enable-libfreetype --enable-libfontconfig --enable-libfribidi --enable-libzmq --enable-libopencore-amrnb --enable-libvo-amrwbenc --enable-libfdk-aac --enable-libfaac --enable-libmp3lame --enable-libass --enable-libqsv --enable-nonfree --enable-gpl --disable-ffplay --disable-ffserver --enable-version3 --extra-ldflags='-L/opt/intel/mediasdk/lib/lin_x64' --extra-libs='-levent -lstdc++' --extra-cflags='-I/opt/intel/mediasdk/include -D_GNU_SOURCE=1 -D_REENTRANT'
                        make -j 8
                        cp /simba/StarETCodec/ffmpeg-2.6-loft/ffmpeg /StarETCodec_publish-I7
                        cp /simba/StarETCodec/ffmpeg-2.6-loft/ffprobe /StarETCodec_publish-I7
                        exit
                        remotessh
                    """
                }
            } catch(err) {
                buildStatus = "FALSE"
                echo "${err}"
                errMessageList << "${err}!\n"
                errModuleName += ": build codeC on I7;"
            }
        }
    }
    if(buildStatus.contentEquals("unset")) {
        try {
            stage('move remote outputs to tools') {
                dir(sourceCodeDir) {
                    sh"""
                        rm -rf ./tools/StarETCodec_publish-E3V5
                        rm -rf ./tools/StarETCodec_publish-I7
                        rm -rf ./tools/StarETCodec_publish-E5
                        if [[ ${cpuParam} =~ "I7" ]]; then
                            scp -r root@10.0.224.21:/StarETCodec_publish-I7 ./tools/
                        fi
                        if [[ $CPUPARAM =~ "E5" ]]; then
                            scp -r -P 8029 root@10.0.224.21:/StarETCodec_publish-E5 ./tools/
                        fi
                    """
                }
            }
        } catch(err) {
            buildStatus = "FALSE"
            echo "${err}"
            errMessageList << "${err}！\n"
            errModuleName += ": move remote outputs to tools;"
        }
    }
    if(buildStatus.contentEquals("unset")) {
        try {
            stage('build LiveDistribute and Switch') {
                sh"""
                    cd ${sourceCodeDir}/Switch/Debug/
                    make clean
                    make
                    rm -f ${sourceCodeDir}/tools/Switch.exe
                    cp Switch.exe ${sourceCodeDir}/tools/

                    cd ${sourceCodeDir}/LiveDistribute/Debug
                    make clean
                    make
                    rm -f ${sourceCodeDir}/tools/LiveDistribute.exe
                    cp LiveDistribute.exe ${sourceCodeDir}/tools/
                """
            }
        } catch(err) {
            buildStatus = "FALSE"
            echo "${err}"
            errMessageList << "${err}！\n"
            errModuleName += ": build LiveDistribute and Switch;"
        }
    }
    if(buildStatus.contentEquals("unset")) {
        try {
            stage('build') {
                sh"""
                    export JAVA_HOME=/usr/java/jdk1.7.0_51
                    /usr/apache-ant-1.9.6/bin/ant -file ${sourceCodeDir}/build.xml -Dbuild.dstamp=${nowDateStr} -Dbuild.number=${buildNum} -Dbuilder=${builder} -Dproject.ver=${version} -Dmodule.ver=${version} -Dbuild.extparam=${extParam} -Dbuild.type.lowercase=r build.all
                """
            }
        } catch(err) {
            buildStatus = "FALSE"
            echo "${err}"
            errMessageList << "${err}！\n"
            errModuleName += ": build;"
        }
    }
    if(buildStatus.contentEquals("unset") && extParam.contains("OTUI")) {
        try {
            stage('build OTUI') {
                dir(sourceCodeDir) {
                    sh"""
                        cd ./ETFE
                        chmod 777 build.sh
                        ./build.sh
                        cd ../ETFE/manage-portal/dist/
                        mv manage-portal.tar.gz manage-portal-${tag}.tar.gz
                    """
                }
            }
        } catch(err) {
            buildStatus = "FALSE"
            echo "${err}"
            errMessageList << "${err}！\n"
            errModuleName += ": build OTUI;"
        }
    }
    if(buildStatus.contentEquals("unset") && ifImage.contentEquals("Y")) {
        try {
            stage('build images') {
                dir(sourceCodeDir) {
                    sh"""
                        chmod 777 build.sh
                        ./build.sh ${harborIp196}/starott-et ${tag} ${sourceCodeDir}
                        cp -rf ${sourceCodeDir}/imageInfo.txt ${sourceCodeDir}/ETFE/manage-portal/dist/
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
                dir(sourceCodeDir) {
                    sh"""
                        echo "CPUPARAM : ${cpuParam}" > ./ETFE/manage-portal/dist/buildinfo.txt
                        echo "BUILDER : ${builder}" >> ./ETFE/manage-portal/dist/buildinfo.txt
                        cp -rf ./publish/* ./ETFE/manage-portal/dist/
                    """
                    if(ifTag.contentEquals("Y")) {
                        sh"""
                            echo "staret SRCTAG : $tag" >> ./ETFE/manage-portal/dist/buildinfo.txt
                            echo "starcodec SRCTAG : $tag" >> ./ETFE/manage-portal/dist/buildinfo.txt
                        """
                    }
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
        def errMessage = errMessageList.join("<br>")
        println "失败模块及失败阶段：" + errModuleName
        println "失败原因：" + errMessage
        def onlineImageUrl = []
        if(ifPublishImages && (ifPublishImages.toLowerCase()).contentEquals("TRUE".toLowerCase())) {
            onlineImageUrl << "<li>centos_manager_let镜像地址ַ&nbsp;：stagingreg.stariboss.com/starott-et/centos_manager_let:${tag}</li>"
            onlineImageUrl << "<li>centos_manager_ot镜像地址ַ&nbsp;：stagingreg.stariboss.com/starott-et/centos_manager_ot:${tag}</li>"
            onlineImageUrl << "<li>centos_processor镜像地址ַ&nbsp;：stagingreg.stariboss.com/starott-et/centos_processor:${tag}</li>"
        }
        def bodyContent = """
            <li>新标签&nbsp;：${tag}</li>
            <li>构建时间&nbsp;：${buildTime}</li>
            <li>代码分支&nbsp;：${branch}</li>
            <li>构建编号&nbsp;：第${buildNum}次构建</li>
            <li>程序构建包&nbsp;：<a href="ftp://10.0.250.250/${jobName}/${version}/${tag}/">ftp://10.0.250.250/${jobName}/${version}/${tag}/</a></li>
            <li>centos_manager_let镜像地址ַ&nbsp;：10.0.251.196/starott-et/centos_manager_let:${tag}</li>
            <li>centos_manager_ot镜像地址ַ&nbsp;：10.0.251.196/starott-et/centos_manager_ot:${tag}</li>
            <li>centos_processor镜像地址ַ&nbsp;：10.0.251.196/starott-et/centos_processor:${tag}</li>
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