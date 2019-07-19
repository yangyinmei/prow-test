def jobName = env.JOB_NAME
def buildNum = env.BUILD_NUMBER
def mailList = env.MAILTO
def buildStatus = "unset"
def errModuleName = ""
def errMessageList = []

def harborIp196 = ""
def harborUser196 = ""
def harborPassword196 = ""
def harborIp190 = ""
def harborUser190 = ""
def harborPassword190 = ""

node('jnlp') {
    try {
        stage('prepare environment') {
            checkout scm
            op = load ("./common/operation.groovy")
            props_comm = readProperties  file: './config/configComm.groovy'

            harborIp196 = props_comm.harborIp196
            harborUser196 = props_comm.harborUser196
            harborPassword196 = props_comm.harborPassword196
            harborIp190 = props_comm.harborIp190
            harborUser190 = props_comm.harborUser190
            harborPassword190 = props_comm.harborPassword190
        }
    } catch(err) {
        buildStatus = "FALSE"
        echo "${err}"
        errMessageList << "${err}!\n"
        errModuleName += "prepare environment \n"
    }

    if(buildStatus.contentEquals("unset")) {
        try {
            stage('delete old images') {
                build job: 'deleteImagesForStariboss', parameters: [string(name: 'IMAGENAME', value: "${harborIp196}/comm/tomcat:9.0.1")]
                build job: 'deleteImagesForStariboss', parameters: [string(name: 'IMAGENAME', value: "${harborIp196}/comm/tomcat9:openjdk11")]
                build job: 'deleteImagesForStariboss', parameters: [string(name: 'IMAGENAME', value: "${harborIp196}/comm/base:openjdk11")]
                build job: 'deleteImagesForStariboss', parameters: [string(name: 'IMAGENAME', value: "${harborIp190}/comm/base:openjdk11")]
            }
        } catch(err) {
            buildStatus = "FALSE"
            echo "${err}"
            errMessageList << "${err}!\n"
            errModuleName += "delete old images;"
        }
    }

    if(buildStatus.contentEquals("unset")) {
        try {
            stage('update code') {
                build job: 'UpdateSourceCode', parameters: [string(name: 'JOBNAME', value: jobName),
                                                            string(name: 'PROJECT', value: "dockerfile"),
                                                            string(name: 'BRANCH', value: "master"),
                                                            string(name: 'GITURL', value: "10.0.250.70:8088/rd-platform/dockerfile.git")]
                build job: 'UpdateSourceCode', parameters: [string(name: 'JOBNAME', value: jobName),
                                                            string(name: 'PROJECT', value: "public-boss-jarfiles"),
                                                            string(name: 'BRANCH', value: "master"),
                                                            string(name: 'GITURL', value: "10.0.250.70:8088/boss/publicjar.git")]
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
            stage('get openjdk11 tomcat9 code') {
                sh """
                    cp -rf /home/source/${jobName}/dockerfile/tomcat9_openjdk11 ./
                    mkdir -p ./tomcat9_openjdk11/public-boss-jarfiles || exit 0
                    cp -rf /home/source/${jobName}/public-boss-jarfiles/* ./tomcat9_openjdk11/public-boss-jarfiles/
                    rm -rf ./tomcat9_openjdk11/public-boss-jarfiles/.git
                """
            }
        } catch(err) {
            buildStatus = "FALSE"
            echo "${err}"
            errMessageList << "${err}!\n"
            errModuleName += "get openjdk11 tomcat9 code;"
        }
    }

    if(buildStatus.contentEquals("unset")) {
        try {
            stage('build openjdk11 tomcat9 images') {
                sh """
                    docker build -t ${harborIp196}/comm/tomcat9:openjdk11 -f ./tomcat9_openjdk11/addJar.Dockerfile ./tomcat9_openjdk11/
                    docker login -u ${harborUser196} -p ${harborPassword196} ${harborIp196}
                    docker push ${harborIp196}/comm/tomcat9:openjdk11
                """
            }
        } catch(err) {
            buildStatus = "FALSE"
            echo "${err}"
            errMessageList << "${err}!\n"
            errModuleName += "build openjdk11 tomcat9 images;"
        }
    }

    if(buildStatus.contentEquals("unset")) {
        try {
            stage('get openjdk11 base code') {
                sh """
                    cp -rf /home/source/${jobName}/dockerfile/base_openjdk11 ./
                    mkdir -p ./base_openjdk11/public-boss-jarfiles || exit 0
                    cp -rf /home/source/${jobName}/public-boss-jarfiles/* ./base_openjdk11/public-boss-jarfiles/
                    rm -rf ./base_openjdk11/public-boss-jarfiles/.git
                """
            }
        } catch(err) {
            buildStatus = "FALSE"
            echo "${err}"
            errMessageList << "${err}!\n"
            errModuleName += "get openjdk11 base code;"
        }
    }

    if(buildStatus.contentEquals("unset")) {
        try {
            stage('build openjdk11 base images') {
                sh """
                    docker build -t ${harborIp196}/comm/base:openjdk11 -f ./base_openjdk11/addJar.Dockerfile ./base_openjdk11/
                    docker login -u ${harborUser196} -p ${harborPassword196} ${harborIp196}
                    docker push ${harborIp196}/comm/base:openjdk11
                    docker tag ${harborIp196}/comm/base:openjdk11 ${harborIp190}/comm/base:openjdk11
                    docker login -u ${harborUser190} -p ${harborPassword190} ${harborIp190}
                    docker push ${harborIp190}/comm/base:openjdk11
                """
            }
        } catch(err) {
            buildStatus = "FALSE"
            echo "${err}"
            errMessageList << "${err}!\n"
            errModuleName += "build openjdk11 base images;"
        }
    }

    if(buildStatus.contentEquals("unset")) {
        try {
            stage('delete new images') {
                build job: 'deleteImagesForStariboss', parameters: [string(name: 'IMAGENAME', value: "${harborIp196}/comm/tomcat:9.0.1")]
                build job: 'deleteImagesForStariboss', parameters: [string(name: 'IMAGENAME', value: "${harborIp196}/comm/tomcat9:openjdk11")]
                build job: 'deleteImagesForStariboss', parameters: [string(name: 'IMAGENAME', value: "${harborIp196}/comm/base:openjdk11")]
                build job: 'deleteImagesForStariboss', parameters: [string(name: 'IMAGENAME', value: "${harborIp190}/comm/base:openjdk11")]
            }
        } catch(err) {
            buildStatus = "FALSE"
            echo "${err}"
            errMessageList << "${err}!\n"
            errModuleName += "delete new images;"
        }
    }

    stage('send email') {
        def errMessage = errMessageList.join("<br>")
        println "失败模块及失败阶段：" + errModuleName
        println "失败原因：" + errMessage
        def bodyContent = """
            <li>失败模块及失败阶段：${errModuleName}</li>
            <li>构建日志&nbsp;：&nbsp;<a href="${BUILD_URL}console">${BUILD_URL}console</a></li>
            <li>失败原因及错误日志&nbsp;：${errMessage}</li>
        """

        if(buildStatus.contentEquals("unset")) {
            mailSubjectStr = "Success! BuildJob: ${jobName} 第${buildNum}次构建！"
        }else {
            mailSubjectStr = "Failed! BuildJob: ${jobName} 第${buildNum}次构建！"
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