def tag = env.TAG
def moduleStr = env.MODULES
def mailList = env.MAILTO
def jobName = env.JOBNAME
def buildNum = env.BUILD_NUMBER

def buildStatus = "unset"
def errModuleName
def errMessage
def mailSubjectStr
def tasks = [:]
def allList
def moduleList
def transferList = []
def resRepository = '10.0.251.196'
def desRepository = '10.0.251.190'
def checkSyncRepository = 'http://10.0.251.190'
def checkSyncUser = "admin"
def checkSyncPASSWORD = "asdf1234"
def checkSyncprojectName = 'boss,billing'
def checkSyncdesRepositoryStr = 'harborrelease'

node('jnlp') {
	try {
		stage('prepare environment') {
			checkout scm
            op = load ("./common/operation.groovy")
			props_boss = readProperties  file: './config/configBOSS.groovy'
			allList = Eval.me(props_boss.allList_boss)
		}
	} catch(err) {
		buildStatus = "FALSE"
		echo "${err}"
		errMessage = "${err}\n"
		errModuleName = "prepare environment"
	}

	// 获取上传模块列表
	if(moduleStr) {
		moduleList = (moduleStr.replace(" ", "")).tokenize(',')
		moduleList.each { moduleName->
				transferList << moduleName
			}
	} else {
		transferList = allList
	}
	// 剔除account-billing模块
	// if("account-billing" in transferList) {
	// 	transferList = transferList - ["account-billing"]
	// }
	println "transferList: " + transferList

	transferList.each { moduleName->
		moduleName = moduleName.toLowerCase()
		tasks[moduleName] = {
			node('jnlp') {
				def imageProject
				if(moduleName.contentEquals("account-billing")) {
					imageProject = "billing"
				} else {
					imageProject = "boss"
				}
				//从196获取镜像
				if(buildStatus.contentEquals("unset")) {
					try {
						stage('get image from 196') {
							sh """
								docker pull ${resRepository}/${imageProject}/${moduleName}:${tag}
							"""
						}
					} catch(err) {
						buildStatus = "FALSE"
						echo "${err}"
						errMessage = "${err}\n"
						errModuleName = moduleName
					}
				}
				
				//登录190仓库
				if(buildStatus.contentEquals("unset")) {
					try {
						stage('login 190') {
							retry (3) {
								sh """
									docker login -u admin  -p asdf1234 10.0.251.190
								"""
							}
						}
					} catch(err) {
						buildStatus = "FALSE"
						echo "${err}"
						errMessage = "${err}\n"
						errModuleName = moduleName
					}
				}
				
				//重命名镜像
				if(buildStatus.contentEquals("unset")) {
					try {
						stage('rename image') {
							sh """
								docker tag ${resRepository}/${imageProject}/${moduleName}:${tag} ${desRepository}/${imageProject}/${moduleName}:${tag}
							"""
						}
					} catch(err) {
						buildStatus = "FALSE"
						echo "${err}"
						errMessage = "${err}\n"
						errModuleName = moduleName
					}
				}
				
				//推送镜像到190
				if(buildStatus.contentEquals("unset")) {
					try {
						stage('push image') {
							retry (3) {
								sh """
									docker push ${desRepository}/${imageProject}/${moduleName}:${tag}
								"""
							}
						}
					} catch(err) {
						buildStatus = "FALSE"
						echo "${err}"
						errMessage = "${err}\n"
						errModuleName = moduleName
					}
				}
				
				//删除镜像
				if(buildStatus.contentEquals("unset")) {
					try {
						stage('delete image') {
							sh """
								docker rmi ${desRepository}/${imageProject}/${moduleName}:${tag}
								docker rmi ${resRepository}/${imageProject}/${moduleName}:${tag}
							"""
						}
					} catch(err) {
						buildStatus = "FALSE"
						echo "${err}"
						errMessage = "${err}\n"
						errModuleName = moduleName
					}
				}	
			}
		}
	}

	parallel tasks
	
	/**
	* 检查镜像同步状态
	*/
	if(buildStatus.contentEquals("unset")) {
		try {
			stage('check image sync') {
				retry (3) {
					build job: 'check-harbor-sync', parameters: [string(name: 'url', value: checkSyncRepository),
																 string(name: 'USERNAME', value: checkSyncUser),
																 string(name: 'PASSWORD', value: checkSyncPASSWORD),
																 string(name: 'projectName', value: checkSyncprojectName),
																 string(name: 'TAG', value: tag),
																 string(name: 'desRepositoryStr', value: checkSyncdesRepositoryStr),
																 string(name: 'MAILTO', value: mailList)]
				}
			}
		} catch(err) {
			buildStatus = "FALSE"
			echo "${err}"
			errMessage = "${err}\n"
			errModuleName = "check image sync"
		}
	}
	/*
	 * 发送邮件
	 */
	stage('send mail') {
		def bodyContent = """
			<li>Failed stage &nbsp;：${errModuleName}</li>
			<li>本次推送镜像标签&nbsp;：${tag}</li>
			<li>本次推送模块&nbsp;：${transferList}</li>
			<li>错误日志&nbsp;：${errMessage}</li>
		"""
		if(buildStatus.contentEquals("unset")) {
			mailSubjectStr = "Success! transferImages-${buildNum} Images of tag:${tag} have been pushed to ${desRepository}"
		} else {
			mailSubjectStr = "Failed! transferImages-${buildNum} transferImages for ${tag} failed!"
		}
		op.sendMail(jobName, buildStatus, mailList, bodyContent, mailSubjectStr)
	}
	
	//构建失败
	if(!(buildStatus.contentEquals("unset"))) {
		stage('build faild'){
			echo "Error stage: ${errModuleName}"
			error 'build faild'
		}
	}
}