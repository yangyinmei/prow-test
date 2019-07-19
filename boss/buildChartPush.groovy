def jobName = env.JOB_NAME
def buildNum = env.BUILD_NUMBER
def branch = env.BRANCH.replace(" ", "")
def gitUrl = env.GITURL.replace(" ", "")
def mailList = env.MAILTO.replace(" ", "")

def buildStatus = "unset"
def errModuleName
def errMessageList = []

def charSourceCodeDir = "/home/source/${jobName}/sourceCode"
def allList_chart= []

node('192.168.32.170') {
    try {
        stage('update code') {
        	build job: 'UpdateSourceCode', parameters: [string(name: 'JOBNAME', value: jobName),
                                                        string(name: 'PROJECT', value: "sourceCode"),
                                                        string(name: 'BRANCH', value: branch),
                                                        string(name: 'GITURL', value: gitUrl)]
            sh """
            	set -x
                ssh -p 22 root@10.0.228.23<< remotessh
                cd ${charSourceCodeDir}
                git fetch || exit 1
                git checkout ${branch} || exit 1
                git reset --hard origin/${branch} || exit 1
                git pull || exit 1
            """
        }
    } catch(err) {
        buildStatus = "FALSE"
        echo "${err}"
        errMessageList << "${err}!\n更新代码失败，请检查您输入的分支是否正确！\n"
        errModuleName += "update code \n"
    }
    if(buildStatus.contentEquals("unset")) {
    	try {
	        stage('prepare environment') {
	            checkout scm
	            op = load ("./common/operation.groovy")
	            props_boss = readProperties  file: './config/configBOSS.groovy'
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
	        stage('get change moduleNames and push chartmuseum') {
	        	dir(charSourceCodeDir) {
	        		sh """
	                    commitMessage=`git log --oneline --max-count=1|awk '{\$1="";print \$0}'`
	                    echo "\$commitMessage" > /tmp/commitMessage.txt
	                """
	        	}
                def commitMessage = readFile("/tmp/commitMessage.txt").replace("\n", "")
                println "commitMessage: " + commitMessage
                // push的模块列表allList_chart从commit信息中获取（如果提交信息为all，表示push的是boss的所有模块，这里通过遍历目录获取列表）
                if(commitMessage.contains("alllist")) {
                    dir(charSourceCodeDir) {
                        sh"""
                            stariboss_allList=`ls -l ./stariboss/|awk '/^d/ {print \$NF}'`
                            echo "\$stariboss_allList" > /tmp/stariboss_allList.txt
                        """
                    }
                    allList_chart = readFile("/tmp/stariboss_allList.txt").replace("\n", ",").tokenize(',')
                } else if(!commitMessage.toLowerCase().contains("merge")) {
                    allList_chart = commitMessage.tokenize(',')
                }
                println "allList_chart:" + allList_chart
                if(allList_chart) {
                    allList_chart.each { moduleName->
                        // 获取上传模块的相对路径
                        dir(charSourceCodeDir) {
                            sh """
                                targetDir=`find -type d -name ${moduleName}`
                                echo "\$targetDir" > /tmp/targetDir.txt
                            """
                        }
                        def targetDir = readFile("/tmp/targetDir.txt").replace("\n", "")
                        sh"""
                            set -x
                            ssh -p 22 root@10.0.228.23<< remotessh
                            cd /home/source/${jobName}/sourceCode/${targetDir}/..
                            helm push ${moduleName} chartmuseum || exit 0
                        """
                    }
                }
	        }
	    } catch(err) {
	        buildStatus = "FALSE"
	        echo "${err}"
	        errMessageList << "${err}!\n"
	        errModuleName += "get change moduleNames and push chartmuseum. \n"
	    }
    }
    stage('send email') {
        def errMessage = errMessageList.join("<br>")
        println "失败模块及失败阶段：" + errModuleName
        println "失败原因：" + errMessage
        def bodyContent = """
            <li>代码分支&nbsp;：${branch}</li>
            <li>构建编号&nbsp;：第${buildNum}次构建</li>
            <li>构建日志&nbsp;：&nbsp;<a href="${BUILD_URL}console">${BUILD_URL}console</a></li>
            <li>失败模块及失败阶段&nbsp;：${errModuleName}</li>
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