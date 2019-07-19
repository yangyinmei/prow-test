import java.text.SimpleDateFormat

def jobName = env.JOB_NAME
def buildNum = env.BUILD_NUMBER
def builder = env.BUILDER
def ifTag = env.IFTAG.replace(" ", "")
def branch = env.BRANCH.replace(" ", "")
def version = env.VERSION.replace(" ", "")
def ifPublishImages = env.IFPUBLISHIMAGES.replace(" ", "")
def mailList = env.MAILTO.replace(" ", "")

def tag
def errModuleName = ""
def errMessageList = []
def buildStatus = "unset"
def nowDateStr
def yamlBranch = 'master'
def yamlTemplateBranch='stable'
def harborIp196 = ""
def harborUser196 = ""
def harborPassword196 = ""

def sourceCodeDir = "/home/source/${jobName}/sourceCode"
def yamlTemplateDir = "/home/source/${jobName}/yamlTemplate/ott/os_yaml"
def yamlDir = "/home/source/${jobName}/yaml"
def masterYamlDir = "/home/nfs/${jobName}/yaml"
def pythonDir = "/home/source/pythonScript/${jobName}"
def uploadFtpDir = "/DataBk/build/${jobName}"

// 环境要求：/usr/java/jdk1.7.0_51 + dos2unix + 免密登录10.0.224.21
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
                props_ott = readProperties  file: './config/configOTT.groovy'
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
                // 清空构建产物
                sh """
                    rm -rf ${sourceCodeDir}/publish
                    mkdir -p ${sourceCodeDir}/publish
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
                                                            string(name: 'GITURL', value: "${props_ott.starosSourcecodeGitUrl}")]
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
            errModuleName += "update code \n"
        }
    }

    if(buildStatus.contentEquals("unset")) {
        try {
            stage('build on E5') {
                // sh"""
                //     set -x
                //     ssh -p 8029 root@10.0.224.21<< remotessh
                //     source /root/.bash_profile
                //     rm -rf /simba/
                //     rm -rf  /StarETCodec_publish-E5
                //     mkdir -p /StarETCodec_publish-E5
                //     git clone http://jenkins:startimes@10.0.250.70:8088/starvideo-process/starcodec.git --branch develop-2.6 /simba/
                //     cd /simba/StarETCodec/ffmpeg-2.6-loft
                //     chmod +x version.sh configure
                //     export PKG_CONFIG_PATH=/usr/local/lib/pkgconfig
                //     ./configure --enable-static --disable-shared --enable-libzmq --enable-gpl --extra-libs='-levent -lstdc++'
                //     make -j 8
                //     cp /simba/StarETCodec/ffmpeg-2.6-loft/ffmpeg /StarETCodec_publish-E5
                //     cp /simba/StarETCodec/ffmpeg-2.6-loft/ffprobe /StarETCodec_publish-E5
                //     exit
                //     remotessh
                // """
                sh"""
                    set -x
                    ssh -p 8029 root@10.0.224.21<< remotessh
                    source /root/.bash_profile
                    rm -rf /simba/
                    rm -rf  /StarETCodec_publish-E5
                    mkdir -p /StarETCodec_publish-E5
                    git clone http://jenkins:startimes@10.0.250.70:8088/starvideo-process/ffmpeg.git --branch release /simba/
                    cd /simba
                    export PKG_CONFIG_PATH=/usr/local/lib/pkgconfig
                    ./configure --enable-libzmq --enable-gpl --enable-libevent --extra-libs=-levent
                    make -j 8
                    cp /simba/ffmpeg /StarETCodec_publish-E5
                    cp /simba/ffprobe /StarETCodec_publish-E5
                    exit
                    remotessh
                """
            }
        } catch(err) {
            buildStatus = "FALSE"
            echo "${err}"
            errMessageList << "${err}!\n"
            errModuleName += "build on E5;"
        }
    }
    if(buildStatus.contentEquals("unset")) {
        try {
            stage('move remote outputs to tools') {
                dir(sourceCodeDir) {
                    sh"""
                        sudo scp -r -P 8029 root@10.0.224.21:/StarETCodec_publish-E5 ./tools/
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
            stage('build') {
                dir(sourceCodeDir) {
                    sh"""
                        touch ./publish/buildinfo.txt
                        dos2unix build.sh
                        chmod +x build.sh
                        ./build.sh ${sourceCodeDir} ${builder} ${nowDateStr} ${buildNum} ${version} ${nowDateStr} ${ifTag}
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
                        rm -rf ./buildImage
                        mkdir buildImage
                        cp ./publish/*.zip ./buildImage

                        cd buildImage
                        unzip star-nginx-source*.zip -d star-nginx-source
                        cd star-nginx-source
                        cp -r ../../tools/StarETCodec_publish-E5 ./
                        unzip starott_cdn_inject_processor*.zip -d processor
                        cp ${sourceCodeDir}/dockerfile/StarOS_processor_Dockerfile ./
                        docker build -t ${harborIp196}/starott/staros_processor:${tag} -f ./StarOS_processor_Dockerfile .
                        docker push ${harborIp196}/starott/staros_processor:${tag}
                        docker rmi ${harborIp196}/starott/staros_processor:${tag}

                        unzip starott_cdn_inject_taskmanager*.zip -d manager
                        cp ${sourceCodeDir}/dockerfile/StarOS_manager_Dockerfile ./
                        docker build -t ${harborIp196}/starott/staros_manager:${tag} -f ./StarOS_manager_Dockerfile .
                        docker push ${harborIp196}/starott/staros_manager:${tag}
                        docker rmi ${harborIp196}/starott/staros_manager:${tag}

                        #cd ${sourceCodeDir}/buildImage
                        #unzip starott_router_manage_service*.zip -d router
                        unzip ${sourceCodeDir}/buildImage/starott_router_manage_service*.zip -d router
                        cp ${sourceCodeDir}/dockerfile/StarOS_router_Dockerfile ./
                        docker build -t ${harborIp196}/starott/staros_router:${tag} -f ./StarOS_router_Dockerfile .
                        docker push ${harborIp196}/starott/staros_router:${tag}
                        docker rmi ${harborIp196}/starott/staros_router:${tag}
                        docker rmi ${harborIp196}/starott/centos_os:v1_190307
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
                op.uploadBuildingProductToFtp(sourceCodeDir, jobName, version, tag, pythonDir, uploadFtpDir, jobName, null)  
            }
        } catch(err) {
            buildStatus = "FALSE"
            echo "${err}"
            errMessageList << "${err}!\n"
            errModuleName += ": upload building product to ftp;"
        }
    }
    if(buildStatus.contentEquals("unset")) {
        try {
            stage('create and upload yaml files') {
                yamlDir = "/home/source/${jobName}/yaml/k8s/application/ott/os_yaml"
                Eval.me(props_ott.staros_yaml_modulelist).each { moduleName->
                    op.createAndUploadYamlFiles(version, tag, moduleName, pythonDir, yamlDir, yamlTemplateDir, Eval.me(props_ott.yaml_kind_list))
                }
            }
        } catch(err) {
            buildStatus = "FALSE"
            echo "${err}"
            errMessageList << "${err}!\n"
            errModuleName += "create and upload yaml files"
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
                            git config --local remote.origin.url http://${props_comm.gitUser}:${props_comm.gitPassword}@${props_ott.starosSourcecodeGitUrl}
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
            onlineImageUrl << "<li>manager镜像地址ַ&nbsp;：stagingreg.stariboss.com/starott/staros_manager:${tag}</li>"
            onlineImageUrl << "<li>processor镜像地址ַ&nbsp;：stagingreg.stariboss.com/starott/staros_processor:${tag}</li>"
            onlineImageUrl << "<li>router镜像地址ַ&nbsp;：stagingreg.stariboss.com/starott/staros_router:${tag}</li>"
        }
        def bodyContent = """
            <li>新标签&nbsp;：${tag}</li>
            <li>构建时间&nbsp;：${buildTime}</li>
            <li>代码分支&nbsp;：${branch}</li>
            <li>构建编号&nbsp;：第${buildNum}次构建</li>
            <li>程序构建包&nbsp;：<a href="ftp://10.0.250.250/${jobName}/${version}/${tag}/">ftp://10.0.250.250/${jobName}/${version}/${tag}/</a></li>
            <li>manager镜像地址ַ&nbsp;：10.0.251.196/starott/staros_manager:${tag}</li>
            <li>processor镜像地址ַ&nbsp;：10.0.251.196/starott/staros_processor:${tag}</li>
            <li>router镜像地址ַ&nbsp;：10.0.251.196/starott/staros_router:${tag}</li>
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