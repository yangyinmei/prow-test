import java.text.SimpleDateFormat

/*
 * 全局变量定义
 */
//传入的构建过程变量
def branch = env.BRANCH
def group = env.GROUP
def oldTag = env.OLDTAG
def newTag = env.NEWTAG
def version = env.VERSION
def jobName = env.JOB_NAME
def buildnum = env.BUILD_NUMBER
def buildType = env.BUILDTYPE
def ifTransferImages = env.TRANSFER
def yamlBranch = 'master'
def yamlTemplateBranch
def mailList = env.MAILTO
def tagRecord = "${branch}${group}LASTTAG.txt"

//标签变量是否是本次构建增加到git中的
def tagIsNew

//有BUG，WORKSPACE变量在pipline中用不了
//def innerWorkspace = env.WORKSPACE


//环境相关
//节点中的工作目录，与jenkins系统配置中的pod模板中的working directory一致
def workDirectory = "/home/jenkins"

//定义新标签
def nowdateFormat = new SimpleDateFormat("yyMMdd")
def nowDateStr = nowdateFormat.format(new Date())

//状态
def buildStatus = "unset"
//异常模块名称
def errModuleName

//如果传入的新标签为空，则此处生成新标签，如果不为空，则使用传入的新标签

def tag

node('jnlp') {
	try {
		stage('establish tag'){
			if(newTag) {
				tag = "${newTag}"
			}else {
				if(branch.contentEquals("hotfix")) {
					//获得版本号去掉最后一段的前缀
					def versionPrefix = version.substring(0, version.lastIndexOf("."))

					//获得上次成功构建的标签，去掉最后一段的前缀
					//readFile方法只能在node中使用

					def lastTag = readFile("/home/transfer/${tagRecord}").replace(" ", "").replace("\n", "")
					def lastTagPrefix = lastTag.substring(0, lastTag.lastIndexOf("."))

					//如果上次标签与本次版本号前缀相同，则本次标签为上次标签+1，如果不相同，则本次标签为本次输入的版本号
					if(lastTagPrefix.contentEquals(versionPrefix)) {
						def lastNumber = (lastTag.substring(lastTag.lastIndexOf(".") + 1)).toInteger()
						def newNumberStr = (lastNumber + 1).toString()
						tag = lastTagPrefix + "." + newNumberStr
					}else {
						tag = version
					}

				}else {
					//非hotfix分支
					tag = "${version}-${buildnum}-${nowDateStr}"
				}
			}
			echo "tag is: ${tag}"
		}
	}catch(err) {
		buildStatus = "FALSE"
		echo "${err}"
		errModuleName = "establish tag"
	}



	/*
	 * 确定yaml文件模板的分支：如果有输入参数VERSION对应的模板分支，则取这个分支的模板文件，如果没有，则取master分支的模板文件
	 */
	try {
		stage('establish yaml template branch'){
			def tempYamlTemplateBranch = env.VERSION
			sh """
				git clone  http://jenkins:startimes@10.0.250.70:8088/rd-platform/yaml-template.git
				cd yaml-template
				yamltembranch=`git branch --all --list "origin/"${tempYamlTemplateBranch}`
				if [ -n "\$yamltembranch" ]
				then
					echo "${tempYamlTemplateBranch}" > ./yamlTemplateBranch.txt
				else
					echo "master" > ./yamlTemplateBranch.txt
				fi
			"""
			yamlTemplateBranch = readFile("/home/jenkins/workspace/both-worker/yaml-template/yamlTemplateBranch.txt").replace(" ", "").replace("\n", "")
			echo "yamlTemplateBranch is: ${yamlTemplateBranch}"
		}
	}catch(err) {
		buildStatus = "FALSE"
		echo "${err}"
		errModuleName = "establish yaml template branch"
	}
}


//接收增量构建变更模块的列表
def currentModuleList = []

//需要构建的模块列表
def buildModuleList

//并行任务集合
def tasks = [:]

//全量列表
def allList = [
	"admin-billing-ui",
	"admin-crm-ui",
	"admin-oss-ui",
	"admin-product-ui",
	"admin-public-ui",
	"account-center-service",
	"account-service",
	"agent-web",
	"api-gateway-service",
	"api-payment-service",
// 	"api-mboss-service",
	"area-service",
//	"billing",
	"callcenter-proxy",
	"card-center-service",
	"card-service",
	"channel-service",
	"check-service",
	"collection-center-service",
	"collection-service",
	"customer-center-service",
	"customer-service",
	"customer-ui",
	"haiwai-proxy",
	"iom-center-service",
	"iom-service",
	"job-service",
	"jobserver",
	"knowledge-service",
	"knowledge-ui",
	"message-center-service",
	"message-receive-worker",
	"note-center-service",
	"note-service",
//	"operator-service",
	"operator-ui",
	"order-center-service",
//	"order-job",
	"order-service",
	"partner-service",
	"partner-ui",
	"platform-cache-config",
	"platform-config",
	"pms-center-service",
	"pms-frontend-conax-service",
//	"pms-frontend-ott-service",
	"pms-partition-service",
	"portal-ui",
	"problem-center-service",
	"problem-service",
	"product-service",
	"resource-center-service",
	"resource-service",
	"resource-ui",
	"starDA-web",
	"system-service",
	"worker-ui",
	]


def jiraTaskUrl

def buildTime

//增量构建的变更模块
def changedModules

//邮件标题
def mailSubjectStr


/*
 * 如果是增量构建获取代码,打标签，并执行:deploy:tools:version-review:run任务，获得变更模块
 * 如果是全量构建，则只获取代码，打标签
 */
def sourceCode = "sourceCode"
node('jnlp'){


	/*
	 * 创建存放构建镜像的日志目录，原本放在各模块构建镜像的python脚本中，由于并发，会引起创建目录出错
	 * 所以放在此处，单进程创建。
	 */
	try {
		stage('create dir for imagelog'){
			dir('/home/ci-python'){
				sh """
					mkdir -p logfile/${tag}"_buildimages_log"
				"""
			}
		}
	}catch(err) {
		buildStatus = "FALSE"
		echo "${err}"
		errModuleName = "create dir for imagelog"
	}


	/*
	 * 创建ftp中存放构建产物的父目录，如果放导各节点当中创建，会产生并发问题（各模块的目录仍然在各节点中上传时创建）
	 */
	if(buildStatus.contentEquals("unset")) {
		try {
			stage('create ftp dir'){
				sh """
					chmod 777 /home/ci-python/createFtpDir.py
					python /home/ci-python/createFtpDir.py ${version} ${tag}
				"""
			}
		}catch(err){
			buildStatus = "FALSE"
			echo "${err}"
			errModuleName = "create ftp dir"
		}
	}


	/*
	 * 获取新的代码，作为构建依据
	 */
	if(buildStatus.contentEquals("unset")) {
		try {
			stage('get code as sourceCode'){
				//如果job的BRANCH变量，输入tag，则此处获取tag对应的代码
				def dateFormat = new SimpleDateFormat("yyyy-MM-dd-HH-mm")
				def getCodeDate = new Date()
				buildTime = dateFormat.format(getCodeDate)
				sh """
					echo 'nameserver 192.168.5.225' > /etc/resolv.conf
					git clone -b ${branch} http://jenkins:startimes@10.0.250.70:8088/boss/stariboss.git ${sourceCode}
				"""
			}
		}catch(err) {
			buildStatus = "FALSE"
			echo "${err}"
			errModuleName = "get code as sourceCode"
		}
	}


	/*
	 * 获得对应版本的yaml模板文件
	 */
	if(buildStatus.contentEquals("unset")) {
		try {
			stage('get yaml template'){
				dir('/home/yamldir/'){
					sh """
						if [ ! -d '/home/yamldir/yaml-template/.git' ]
						then
							rm -rf /home/yamldir/yaml-template/
							git clone -b ${yamlTemplateBranch} http://jenkins:startimes@10.0.250.70:8088/rd-platform/yaml-template.git
						else
							cd yaml-template
							git config --global user.name 'jenkins'
							git config --global user.email 'jenkins@startimes.com.cn'
							git checkout ${yamlTemplateBranch}
							git pull origin ${yamlTemplateBranch}
						fi
					"""
				}
				echo "get yaml template: ${yamlTemplateBranch}"
			}
		}catch(err) {
			buildStatus = "FALSE"
			echo "${err}"
			errModuleName = "get yaml template"
		}
	}


	/*
	 * 获取或更新git上的yaml
	 */
	if(buildStatus.contentEquals("unset")) {
		try {
			stage('get yaml from git'){
				dir('/home/yamldir/'){
					sh """
						if [ ! -d '/home/yamldir/yaml/.git' ]
						then
							rm -rf /home/yamldir/yaml/
							git clone -b ${yamlBranch} http://jenkins:startimes@10.0.250.70:8088/rd-platform/yaml.git
						else
							cd yaml
							git fetch --all
							git reset --hard origin/${yamlBranch}
							#git checkout ${yamlBranch}
							#git pull origin ${yamlBranch}
						fi
						# 创建本次构建存放yaml文件的目录
						mkdir -p /home/yamldir/yaml/boss_yaml/${version}/${tag}/changed_module
						mkdir -p /home/yamldir/yaml/boss_yaml/${version}/${tag}/nochanging_module
					"""
				}
				echo "get yamlBranch: ${yamlBranch}"

			}
		}catch(err) {
			buildStatus = "FALSE"
			echo "${err}"
			errModuleName = "get yaml from git"
		}
	}


	/*
	 * 为代码打标签
	 */
	if(buildStatus.contentEquals("unset")) {
		try {
			stage('tag code'){
				dir(sourceCode){
					sh """
						if [ `git tag --list ${tag}` ]
						then
							echo '${tag} exists.'
							echo 'NO' > tagIsNew.CONF
						else
							echo '${tag} not exist.'
							git config --global user.name 'jenkins'
							git config --global user.email 'jenkins@startimes.com.cn'
							git config --local remote.origin.url http://jenkins:startimes@10.0.250.70:8088/boss/stariboss.git
							git tag -a ${tag} -m 'BUILDER: '
							git push origin ${tag}
							echo 'YES' > tagIsNew.CONF
						fi
					"""
					tagIsNew = readFile("tagIsNew.CONF").replace(" ", "").replace("\n", "")
				}
			}
		}catch(err) {
			buildStatus = "FALSE"
			echo "${err}"
			errModuleName = "tag code"
		}
	}



	/*
	 * 如果是增量构建，还需执行:deploy:tools:version-review:run任务，获得变更模块
	 */
	if(buildType.contentEquals('incremental')) {

		//如果增量构建未输入旧标签，则获取当前分支对应的上次构建生成的标签作为旧标签
		if(!oldTag) {

			if(buildStatus.contentEquals("unset")) {
				try {
					stage('oldTag is null'){
						oldTag = readFile("/home/transfer/${tagRecord}").replace(" ", "").replace("\n", "")
						echo "oldTag is null, get oldTag from ${tagRecord}: ${oldTag}"
					}
				}catch(err) {
					buildStatus = "FALSE"
					echo "${err}"
					errModuleName = "oldTag is null"
				}
			}

		}


		//执行获取新旧标签差别任务
		if(buildStatus.contentEquals("unset")) {
			try {
				stage('get moduleDependence.txt'){
					dir(sourceCode){
						sh """
							chmod 777 gradlew
							export JAVA_HOME=/usr/java/jdk1.8.0_66/
							export version1=${oldTag}
							export version2=${tag}
							export working_directory=${workDirectory}/workspace/${jobName}/${sourceCode}
							./gradlew clean :deploy:tools:version-review:run
						"""
					}
				}
			}catch(err){
				buildStatus = "FALSE"
				echo "${err}"
				errModuleName = "get moduleDependence.txt"
			}
		}


		//获得变更模块列表
		if(buildStatus.contentEquals("unset")) {
			try {
				stage('get changedModuleList'){
					dir(sourceCode){
						moduleStr = readFile('moduleDependence.txt').replace(" ", "")
						println moduleStr
						println oldTag
						println tag
						moduleList = moduleStr.tokenize(',')

						//排除当前未发布的模块（不在allList中的模块）
						//currentModuleList = moduleList.clone()

						moduleList.each{module->
							def existFlag = (module  in allList)
							if(existFlag) {
								currentModuleList << module
							}
						}
					}
				}
			}catch(err) {
				buildStatus = "FALSE"
				echo "${err}"
				errModuleName = "get changedModuleList"
			}
		}


	}

}


//增量构建或全量构建
if(buildType.contentEquals('incremental')) {
	buildModuleList = currentModuleList.clone()
}else {
	buildModuleList = allList.clone()
}


// 如果列表为空，则不需要构建
if(!buildModuleList) {
	echo "There has been no changes in any module since last successful building."
	changedModules = "No changings between ${oldTag} and ${tag}. ${tag} has been deleted in code repository."

	//如果没有需要构建的
	node('jnlp'){
		try {
			stage('delete tag'){
				sh """
					git clone -b ${tag} http://bizh:startimes@10.0.250.70:8088/boss/stariboss.git
					cd stariboss
					git tag -d ${tag}
					git push origin :refs/tags/${tag}
				"""
			}
		}catch(err) {
			buildStatus = "FALSE"
			echo "${err}"
			errModuleName = "delete tag"
		}
	}

}else {
	allList.each{moduleName->
	//buildModuleList.each{moduleName->
		def codeDir = "${workDirectory}/workspace/${jobName}/${moduleName}"

		tasks[moduleName] = {
			node('jnlp'){
				if(moduleName in buildModuleList) {

					//如果变更模块中有agent-web，单独处理agent-web
					if(moduleName.contentEquals("agent-web")) {
						if(buildStatus.contentEquals("unset")) {
							try {
								build job: 'agent-web', parameters: [string(name: 'TAG', value: tag), string(name: 'IMAGETAG', value: tag), string(name: 'VERSION', value: version)]
							}catch(err) {
								buildStatus = "FALSE"
								echo "${err}"
								errModuleName = moduleName
							}
						}
					}else {
						/*
						 * ==============
						 * 为各模块获取代码
						 * ==============
						 */
						if(buildStatus.contentEquals("unset")) {
							try {
								stage('get code'){
									//各模块代码，通过clone获取,代码目录不挂载到nfs
									sh """
										hostname
										export npm_config_registry=https://registry.npm.taobao.org
										echo "nameserver 192.168.5.225" > /etc/resolv.conf
										git config --global http.postBuffer 524288000
										git clone -b ${tag} http://jenkins:startimes@10.0.250.70:8088/boss/stariboss.git ${moduleName}
									"""
								}
							}catch(err) {
								buildStatus = "FALSE"
								echo "${err}"
								errModuleName = moduleName
							}
						}


						/*
						 * ==============
						 * 节点当中构建对应模块
						 * ==============
						 */
						if(buildStatus.contentEquals("unset")) {
							try {
								stage('build'){
									sh """
										chmod 777 /home/ci-python/build.py
										python /home/ci-python/build.py ${codeDir} ${moduleName}
										cd ./${moduleName}
										. /etc/profile
										. ./gradleTaskList.config
										./gradlew clean \$GRADLETASK
									"""
								}
							}catch(err) {
								buildStatus = "FALSE"
								echo "${err}"
								errModuleName = moduleName
							}
						}


						/*
						 * ==============
						 * 将构建产物和模块对应的dockerfile移动到代码根目录下的outputFileFolder文件夹中
						 * ==============
						 */
						if(buildStatus.contentEquals("unset")) {
							try {
								stage('move building output to dir: outputFileFolder'){
									sh """
										chmod 777 /home/ci-python/mvFileToOutputFileFolder.py
										python /home/ci-python/mvFileToOutputFileFolder.py ${codeDir} ${moduleName}
									"""
								}
							}catch(err) {
								buildStatus = "FALSE"
								echo "${err}"
								errModuleName = moduleName
							}
						}


						/*
						 * ==============
						 * 删除公共jar包，并构建镜像，上传镜像
						 * ==============
						 */
						if(buildStatus.contentEquals("unset")) {
							try {
								stage('modify third jars and build images'){
									sh """
										chmod 777 /home/ci-python/buildDockerImage.py
										python /home/ci-python/buildDockerImage.py ${codeDir} ${moduleName} ${tag}
									"""
								}
							}catch(err) {
								buildStatus = "FALSE"
								echo "${err}"
								errModuleName = moduleName
							}
						}


						/*
						 * ==============
						 * 删除生成的镜像
						 * ==============
						 */
						if(buildStatus.contentEquals("unset")) {
							try {
								stage('delete image'){
									def imageName = moduleName.toLowerCase()
									if(imageName in ["billing", "order-job", "jobserver"]) {
										if(imageName == "jobserver") {
											sh """
												docker rmi 10.0.251.196/billing/spark-jobserver:${tag}
												docker rmi 10.0.251.196/billing/spark-jobserver-yarn:${tag}
												docker rmi 10.0.251.196/billing/uploadjarfile:${tag}
											"""
										}
										else {
											sh """
												docker rmi 10.0.251.196/billing/${imageName}:${tag}
											"""
										}
									}
									else {
										sh """
											docker rmi 10.0.251.196/boss/${imageName}:${tag}
										"""
									}
								}
							}catch(err) {
								buildStatus = "FALSE"
								echo "${err}"
								errModuleName = moduleName
							}
						}


						/*
						 * ==============
						 * 将构建产物上传ftp中
						 * ==============
						 */
						if(buildStatus.contentEquals("unset")) {
							try {
								stage('upload building product to ftp'){
									sh """
										chmod 777 /home/ci-python/uploadProductToFtp.py
										python /home/ci-python/uploadProductToFtp.py ${codeDir} ${moduleName} ${version} ${tag}
									"""
								}
							}catch(err) {
								buildStatus = "FALSE"
								echo "${err}"
								errModuleName = moduleName
							}
						}


						/*
						 * ==============
						 * 生成yaml文件
						 * ==============
						 */
						if(buildStatus.contentEquals("unset")) {
							try {
								stage('create and upload yaml files'){
									sh """
										chmod 777 /home/ci-python/createDpmAndSvcYaml.py
										python /home/ci-python/createDpmAndSvcYaml.py ${codeDir} ${moduleName} ${tag} ${version} changed_module /home/yamldir/yaml-template/boss_yaml /home/yamldir/yaml/boss_yaml
									"""
								}
							}catch(err) {
								buildStatus = "FALSE"
								echo "${err}"
								errModuleName = moduleName
							}
						}
					}

				}else {
					// 未变更模块的处理：
					if(buildStatus.contentEquals("unset")) {
						try {
							stage('retag and repush image'){
								sh """
									docker login -u admin  -p asdf1234 10.0.251.196
								"""
								println oldTag
								println tag
								def imageName = moduleName.toLowerCase()
								if(imageName in ["billing", "order-job", "jobserver"]) {
									if(imageName == "jobserver") {
										sh """
											echo ${oldTag}
											echo ${tag}
			                                docker pull 10.0.251.196/billing/spark-jobserver:${oldTag}
			                                docker pull 10.0.251.196/billing/spark-jobserver-yarn:${oldTag}
			                                docker pull 10.0.251.196/billing/uploadjarfile:${oldTag}

			                                docker tag 10.0.251.196/billing/spark-jobserver:${oldTag} 10.0.251.196/billing/spark-jobserver:${tag}
			                                docker tag 10.0.251.196/billing/spark-jobserver-yarn:${oldTag} 10.0.251.196/billing/spark-jobserver-yarn:${tag}
			                                docker tag 10.0.251.196/billing/uploadjarfile:${oldTag} 10.0.251.196/billing/uploadjarfile:${tag}

			                                docker push 10.0.251.196/billing/spark-jobserver:${tag}
			                                docker push 10.0.251.196/billing/spark-jobserver-yarn:${tag}
			                                docker push 10.0.251.196/billing/uploadjarfile:${tag}

			                                docker rmi 10.0.251.196/billing/spark-jobserver:${tag}
			                                docker rmi 10.0.251.196/billing/spark-jobserver:${oldTag}
			                                docker rmi 10.0.251.196/billing/spark-jobserver-yarn:${tag}
			                                docker rmi 10.0.251.196/billing/spark-jobserver-yarn:${oldTag}
			                                docker rmi 10.0.251.196/billing/uploadjarfile:${tag}
			                                docker rmi 10.0.251.196/billing/uploadjarfile:${oldTag}
										"""
									}else {
										sh """
				                            echo ${oldTag}
											echo ${tag}
				                            docker pull 10.0.251.196/billing/${imageName}:${oldTag}
				                            docker tag 10.0.251.196/billing/${imageName}:${oldTag} 10.0.251.196/billing/${moduleName}:${tag}
				                            docker push 10.0.251.196/billing/${imageName}:${tag}
				                            docker rmi 10.0.251.196/billing/${imageName}:${tag}
				                            docker rmi 10.0.251.196/billing/${imageName}:${oldTag}
									 	"""
									}
								}else {
									sh """
										echo ${oldTag}
										echo ${tag}
										echo "docker tag 10.0.251.196/boss/${imageName}:${oldTag} 10.0.251.196/boss/${imageName}:${tag}"
			                            docker pull 10.0.251.196/boss/${imageName}:${oldTag}
			                            docker tag 10.0.251.196/boss/${imageName}:${oldTag} 10.0.251.196/boss/${imageName}:${tag}
			                            docker push 10.0.251.196/boss/${imageName}:${tag}
			                            docker rmi 10.0.251.196/boss/${imageName}:${tag}
			                            docker rmi 10.0.251.196/boss/${imageName}:${oldTag}
									"""
								}
							}
						}catch(err) {
							buildStatus = "FALSE"
							echo "${err}"
							errModuleName = moduleName
						}
					}


					/*
					 * ==============
					 * 生成未变更模块的新标签对应的yaml文件
					 * ==============
					 */
					if(buildStatus.contentEquals("unset")) {
						try {
							stage('create and upload yaml files for nochanged'){
								sh """
		                            chmod 777 /home/ci-python/createDpmAndSvcYaml.py
		                            python /home/ci-python/createDpmAndSvcYaml.py ${codeDir} ${moduleName} ${tag} ${version} nochanging_module /home/yamldir/yaml-template/boss_yaml /home/yamldir/yaml/boss_yaml
								"""
							}
						}catch(err) {
							buildStatus = "FALSE"
							echo "${err}"
							errModuleName = moduleName
						}
					}


				}
			}
		}
	}


	//并行执行各节点任务
	if(buildStatus.contentEquals("unset")) {
		parallel tasks
	}


	node('jnlp') {


		/*
		 * 记录版本标签信息到文件/home/yamldir/yaml/version.txt（提交git）
		 */
		if(buildStatus.contentEquals("unset")) {
			try {
				stage('record current version and tag'){
					sh """
						echo '${version}/${tag}' > /home/yamldir/yaml/version.txt
					"""
				}
			}catch(err) {
				buildStatus = "FALSE"
				echo "${err}"
				errModuleName = "record current version and tag"
			}
		}


		/*
		 * 推送本次构建生成的yaml到git
		 */
		if(buildStatus.contentEquals("unset")) {
			try {
				stage('push yaml to git'){
					dir('/home/yamldir/yaml/'){
						sh """
							echo 'nameserver 192.168.5.225' > /etc/resolv.conf
							git config --global user.name 'jenkins'
							git config --global user.email 'bizh@startimes.com.cn'

							if [ `git tag --list ${tag}` ]
							then
								echo '${tag} exists.'
							else
								git checkout ${yamlBranch}
								git pull origin ${yamlBranch}
								git add boss_yaml
								git commit -a -m ${tag}
								git push origin ${yamlBranch}
								git tag -a ${tag} -m '${tag}'
								git push origin ${tag}
							fi
						"""
					}
				}
			}catch(err) {
				buildStatus = "FALSE"
				echo "${err}"
				errModuleName = "push yaml to git"
			}
		}


		/*
		 * 记录提交历史到jira
		 */
		if(buildStatus.contentEquals("unset")) {
			try {
				stage('record changings to jira'){
					if(buildType.contentEquals('incremental')) {
						sh """
							git clone -b ${tag} http://jenkins:startimes@10.0.250.70:8088/boss/stariboss.git
							cd stariboss
							commitMessage=`git log --oneline --grep STARIBOSS ${oldTag}..${tag} | awk '{\$1="";print \$0}'`
							chmod 777 /home/ci-python/recordChanggingsToJira.py
							python /home/ci-python/recordChanggingsToJira.py ${oldTag} ${tag} "\$commitMessage" ${branch} ${buildType}
						"""
					}else {
						sh """
							git clone -b ${tag} http://jenkins:startimes@10.0.250.70:8088/boss/stariboss.git
							cd stariboss
							commitMessage=`git log --oneline --max-count=100 --grep STARIBOSS | awk '{\$1="";print \$0}'`
							chmod 777 /home/ci-python/recordChanggingsToJira.py
							python /home/ci-python/recordChanggingsToJira.py "no old tag" ${tag} "\$commitMessage" ${branch} ${buildType}
						"""
					}
				}
			}catch(err) {
				buildStatus = "FALSE"
				echo "${err}"
				errModuleName = "record changings to jira"
			}
		}


		/*
		 * 获得新提交的issue链接
		 */
		if(buildStatus.contentEquals("unset")) {
			try {
				stage('get issue url'){
					jiraTaskUrl = readFile("/home/transfer/issueUrlFile").replace(" ", "").replace("\n", "")
					println jiraTaskUrl
				}
			}catch(err) {
				buildStatus = "FALSE"
				echo "${err}"
				errModuleName = "get issue url"
			}
		}


		/*
		 * 将新标签作为旧标签记录，如果下次增量构建未提供旧标签，则使用该记录
		 */
		if(buildStatus.contentEquals("unset")) {
			try {
				stage('record tag as oldTag'){
					sh """
						echo ${tag} > /home/transfer/${tagRecord}
					"""
					echo "record the tag: echo ${tag} > /home/transfer/${tagRecord}"
				}
			}catch(err) {
				buildStatus = "FALSE"
				echo "${err}"
				errModuleName = "record tag as oldTag"
			}
		}


		/*
		 * 如果有需要构建的模块，但是构建失败，并且新标签为本次刚创建的标签，且标签已推送到远程，从远程git删除标签
		 */
		if(!(buildStatus.contentEquals("unset"))) {
			if((tagIsNew.contentEquals("YES"))) {
				stage('delete tag'){
					sh """
						git clone -b hotfix http://bizh:startimes@10.0.250.70:8088/boss/stariboss.git
						cd stariboss
						existTag=\$(git tag|grep ^${tag}\$)

						if [ -n "\$existTag" ]
						then
							git push origin --delete ${tag}
							echo "tag ${tag} has been delete on remote."
						else
							echo "tag \$existTag not exist, need not delete."
						fi
					"""
				}
			}
		}

	}
}


node('jnlp'){

	stage('nameserver'){
		sh """
			echo "nameserver 192.168.5.225" > /etc/resolv.conf
		"""
	}
	/*
	 * ==============
	 * 发送邮件
	 * ==============
	 */
	stage('send mail'){

		// body
		if(changedModules) {
			changedModules = changedModules
		}else {
			changedModules = currentModuleList.join("<br>")
		}

		def bodyStr = """
						<!DOCTYPE html>
						<html>
						<head>
						<meta charset="UTF-8">
						<title>\${ENV, var="JOB_NAME"}</title>
						</head>

						<body leftmargin="8" marginwidth="0" topmargin="8" marginheight="4"
						    offset="0">
						    <table width="95%" cellpadding="0" cellspacing="0"
						        style="font-size: 11pt; font-family: Tahoma, Arial, Helvetica, sans-serif">
						        <tr>
						            <td>(本邮件是程序自动下发的，请勿回复！)</td>
						        </tr>
						        <tr>
						            <td>
						                <ul>
											<li>Failed stage &nbsp;：${errModuleName}</li>
											<li>构建类型&nbsp;：&nbsp;${buildType}</li>
			                                <li>旧标签&nbsp;：${oldTag}</li>
			                                <li>新标签&nbsp;：${tag}</li>
											<li>incremental build changings&nbsp;：<a href="${jiraTaskUrl}">${jiraTaskUrl}</a></li>
											<li>构建时间&nbsp;：${buildTime}</li>
			                                <li>代码分支&nbsp;：${branch}</li>
								            <li>git项目地址ַ&nbsp;：<a href="http://10.0.250.70:8088/rd-platform/yaml.git">http://10.0.250.70:8088/rd-platform/yaml.git</a></li>
											<li>git上的YAML目录&nbsp;：&nbsp;.../boss_yaml/${version}/${tag}/</li>
						                    <li>变更模块&nbsp;：&nbsp;<br>${changedModules}</li>
						                </ul>
						            </td>
						        </tr>
						    </table>
						</body>
						</html>
					"""


		// subject

//					if(hudson.model.Result.SUCCESS.equals(currentBuild.rawBuild.getPreviousBuild()?.getResult())) {
//						buildStatus = "Success"
//					}else {
//						buildStatus = "Failed"
//					}

		if(buildStatus.contentEquals("unset")) {
			mailSubjectStr = "Success! buildtype: ${buildType} tag:${tag}"
		}else {
			mailSubjectStr = "Failed! buildtype: ${buildType} tag:${tag}"
		}

		emailext body: "${bodyStr}",
		recipientProviders: [
			[$class: 'DevelopersRecipientProvider'],
			[$class: 'RequesterRecipientProvider']
			],
		subject: "${mailSubjectStr}",
		mimeType: 'text/html',
		//to: 'bizh@startimes.com.cn'
		//to: 'yinzy@startimes.com.cn,bizh@startimes.com.cn,jink@startimes.com.cn,sunt@startimes.com.cn'
		to: mailList
	}


	//构建失败
	if(!(buildStatus.contentEquals("unset"))) {
		stage('build faild'){
			echo "Error stage: ${errModuleName}"
			error 'build faild'
		}
	}


	/*
	 * 参数确定是否触发推送镜像到190的任务
	 */
	if(buildModuleList) {
		if(buildStatus.contentEquals("unset")) {
			try {
				if((ifTransferImages.toLowerCase()).contentEquals('TRUE'.toLowerCase())) {
					stage('trigger transferImages'){
						//build job: 'transferImages', parameters: [string(name: 'TAG', value: tag), string(name: 'MAILTO', value: mailList)], wait: false
						build job: 'transferImages', parameters: [string(name: 'TAG', value: tag), string(name: 'MAILTO', value: mailList)]
					}
				}
			}catch(err) {
				buildStatus = "FALSE"
				echo "${err}"
				errModuleName = "trigger transferImages"
			}
		}
	}

}


